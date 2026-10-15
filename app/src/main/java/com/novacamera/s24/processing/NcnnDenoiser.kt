package com.novacamera.s24.processing

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.novacamera.s24.core.imaging.Px
import com.novacamera.s24.core.DenoisePolicy
import kotlin.math.min

/**
 * Redução de ruído por IA (NAFNet, MIT) executada com ncnn (BSD-3-Clause), na GPU via Vulkan
 * quando disponível. Experimental e desligada por padrão: só entra se o usuário ligar, e a
 * revisão permite comparar com o original.
 *
 * O modelo (nafnet.param/.bin em assets/nafnet) é gerado no CI a partir dos pesos oficiais
 * (tools/nafnet/export_ncnn.py). Se a build não tiver o modelo, [available] é falso e nada muda.
 */
object NcnnDenoiser {
    private const val TAG = "NovaDenoise"
    private const val PARAM = "nafnet/nafnet.param"
    private const val BIN = "nafnet/nafnet.bin"
    /** Bloco útil e margem de contexto (a rede vê 288×288; múltiplo de 16 exigido pelo NAFNet). */
    private const val TILE = 256
    private const val PAD = 16

    private val libLoaded = runCatching { System.loadLibrary("novadenoise") }.onFailure { Log.w(TAG, "biblioteca nativa ausente", it) }.isSuccess
    @Volatile private var handle = 0L
    private var lastUsedMs = 0L
    private var retryAfterMs = 0L
    @Volatile var report = "IA ainda não executada"
        private set

    @JvmStatic private external fun nativeLoad(assets: AssetManager, param: String, bin: String, useGpu: Boolean): Long
    @JvmStatic private external fun nativeUsesGpu(handle: Long): Boolean
    @JvmStatic private external fun nativeRun(handle: Long, input: Bitmap, output: Bitmap): Boolean
    @JvmStatic private external fun nativeRelease(handle: Long)

    /** O modelo veio nesta build e a biblioteca nativa carregou. */
    fun available(context: Context): Boolean =
        libLoaded && runCatching { context.assets.list("nafnet")?.toSet()?.containsAll(listOf("nafnet.param", "nafnet.bin")) == true }.getOrDefault(false)

    @Synchronized
    private fun ensureLoaded(context: Context): Boolean {
        if (handle != 0L) return true
        if (!available(context)) return false
        handle = nativeLoad(context.assets, PARAM, BIN, true)
        if (handle != 0L) Log.i(TAG, "NAFNet carregado (GPU=${nativeUsesGpu(handle)})")
        return handle != 0L
    }

    /** Libera sob pressão de memória/térmica; sincronizada com toda a inferência. */
    @Synchronized
    fun release() {
        if (handle != 0L) { nativeRelease(handle); handle = 0L }
    }

    /**
     * Aplica no lugar, em blocos. [strength] 0..1 mistura com o original (1 = só a rede): manter
     * um pouco do grão evita o aspecto plástico. Devolve false se não houver modelo.
     */
    @Synchronized
    fun releaseIfIdle(nowMs: Long) {
        if (handle != 0L && nowMs - lastUsedMs >= DenoisePolicy.IDLE_MS) release()
    }

    @Synchronized
    fun denoiseInPlace(
        context: Context, bmp: Bitmap, strength: Float, cancel: CancelToken,
        budget: DenoisePolicy.Budget = DenoisePolicy.FOREGROUND, progress: (Float) -> Unit,
    ): Boolean {
        val started = SystemClock.elapsedRealtime()
        if (strength <= 0f || started < retryAfterMs) return false
        val reused = handle != 0L
        val pssBeforeKb = android.os.Debug.getPss()
        val heatBefore = ProcessingQueue.heat
        progress(0f)
        if (!ensureLoaded(context)) {
            retryAfterMs = SystemClock.elapsedRealtime() + DenoisePolicy.IDLE_MS
            report = "Modelo offline indisponível ou falha na inicialização"
            return false
        }
        val loadMs = SystemClock.elapsedRealtime() - started
        lastUsedMs = SystemClock.elapsedRealtime()
        val gpu = nativeUsesGpu(handle)
        val w = bmp.width; val h = bmp.height
        val pixels = w.toLong() * h
        val runtime = Runtime.getRuntime()
        val headroom = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        if (!DenoisePolicy.canRun(pixels, gpu, headroom)) {
            report = "IA dispensada: backend=${if (gpu) "Vulkan" else "CPU"}, pixels=$pixels, memória Java disponível=$headroom, carga=${loadMs}ms"
            release()
            return false
        }
        val size = TILE + 2 * PAD
        if (w < size || h < size) return false
        val src = IntArray(size * size); val den = IntArray(size * size)
        val inTile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        var outTile: Bitmap? = null
        val s = strength.coerceIn(0f, 1f)
        val nx = (w + TILE - 1) / TILE; val ny = (h + TILE - 1) / TILE
        val inferenceStarted = SystemClock.elapsedRealtime()
        var firstTileMs = 0L
        var steadyTileMs = 0L
        var runMs = 0L
        var completed = 0
        var result: IntArray? = null
        var status = "interrompida"
        // Apenas o buffer separado recebe os resultados. Cancelamento/fallback deixa bmp intacto.
        try {
            outTile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (ty in 0 until ny) for (tx in 0 until nx) {
                val tileStarted = SystemClock.elapsedRealtime()
                if (cancel.cancelled) throw CancelledException()
                if (!DenoisePolicy.allowed(ProcessingQueue.heat) ||
                    DenoisePolicy.expired(SystemClock.elapsedRealtime() - started, SystemClock.elapsedRealtime() - inferenceStarted, budget)) {
                    status = "fallback por custo/temperatura"
                    return false
                }
                val x0 = tx * TILE; val y0 = ty * TILE
                val sx = (x0 - PAD).coerceIn(0, w - size); val sy = (y0 - PAD).coerceIn(0, h - size)
                bmp.getPixels(src, 0, size, sx, sy, size, size)
                inTile.setPixels(src, 0, size, 0, 0, size, size)
                val t = SystemClock.elapsedRealtime()
                if (!nativeRun(handle, inTile, outTile)) {
                    status = "fallback: inferência falhou"
                    retryAfterMs = SystemClock.elapsedRealtime() + DenoisePolicy.IDLE_MS
                    release()
                    return false
                }
                val elapsed = SystemClock.elapsedRealtime() - t
                runMs += elapsed
                completed++
                if (completed == 1) {
                    firstTileMs = elapsed
                    result = IntArray(w * h)
                }
                outTile.getPixels(den, 0, size, 0, 0, size, size)
                val tw = min(TILE, w - x0); val th = min(TILE, h - y0)
                for (y in 0 until th) for (x in 0 until tw) {
                    val i = (y0 + y - sy) * size + (x0 + x - sx)
                    val o = src[i]; val d = den[i]
                    result!![(y0 + y) * w + x0 + x] = Px.argb(
                        (Px.r(o) + (Px.r(d) - Px.r(o)) * s + 0.5f).toInt(),
                        (Px.g(o) + (Px.g(d) - Px.g(o)) * s + 0.5f).toInt(),
                        (Px.b(o) + (Px.b(d) - Px.b(o)) * s + 0.5f).toInt())
                }
                // A primeira chamada Vulkan pode compilar pipelines. Medir dois blocos
                // seguintes, incluindo cópias/mistura, antes de projetar a foto inteira.
                // Todos os blocos continuam sujeitos aos mesmos limites de tempo/térmica.
                if (completed == 2 || completed == 3) {
                    steadyTileMs = maxOf(steadyTileMs, SystemClock.elapsedRealtime() - tileStarted)
                }
                if (completed >= 3 && !DenoisePolicy.remainingFits(
                        SystemClock.elapsedRealtime() - started,
                        SystemClock.elapsedRealtime() - inferenceStarted,
                        steadyTileMs, nx * ny - completed, budget)) {
                    status = "fallback: custo sustentado projetado excede orçamento"
                    return false
                }
                progress(completed.toFloat() / (nx * ny))
            }
            if (cancel.cancelled) throw CancelledException()
            if (!DenoisePolicy.allowed(ProcessingQueue.heat) ||
                DenoisePolicy.expired(SystemClock.elapsedRealtime() - started, SystemClock.elapsedRealtime() - inferenceStarted, budget)) {
                status = "fallback por custo/temperatura"
                return false
            }
            bmp.setPixels(result!!, 0, w, 0, 0, w, h)
            status = "concluída"
            return true
        } finally {
            lastUsedMs = SystemClock.elapsedRealtime()
            report = "NAFNet $status · backend=${if (gpu) "Vulkan" else "CPU"}, reutilizado=$reused, " +
                "carga=${loadMs}ms, primeira inferência=${firstTileMs}ms, bloco sustentado=${steadyTileMs}ms, inferência=${runMs}ms, " +
                "total=${lastUsedMs - started}ms, blocos=$completed/${nx * ny}, $w×$h, " +
                "PSS início/fim=${pssBeforeKb}/${android.os.Debug.getPss()} KiB (não pico), " +
                "térmico=${heatBefore?.thermalStatus}/${ProcessingQueue.heat?.thermalStatus}, " +
                "bateria °C=${heatBefore?.batteryTempC}/${ProcessingQueue.heat?.batteryTempC}"
            Log.i(TAG, report)
            inTile.recycle(); outTile?.recycle()
        }
    }
}
