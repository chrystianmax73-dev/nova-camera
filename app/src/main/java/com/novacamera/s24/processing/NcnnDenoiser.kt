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
    private val overBudget = com.novacamera.s24.core.DenoiseSkip()
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

    /** Acima disto (Alta resolução), a rede trabalha em meia resolução ([HalfResidual]). */
    const val HALF_FROM = 16_500_000L

    /**
     * Foto grande: reduz 2× (média 2×2, em faixas), limpa a reduzida e devolve à foto cheia só o ruído
     * retirado, ampliado. ¼ dos blocos (49 MP ≈ custo de 12 MP). Mesmo contrato de [denoiseInPlace].
     */
    fun denoiseHalf(
        context: Context, bmp: Bitmap, strength: Float, cancel: CancelToken,
        budget: DenoisePolicy.Budget, skipClean: Boolean, progress: (Float) -> Unit,
    ): Boolean {
        val w = bmp.width - bmp.width % 2; val h = bmp.height - bmp.height % 2
        val sw = w / 2; val sh = h / 2
        val orig = IntArray(sw * sh)
        val rows = 256
        val buf = IntArray(bmp.width * rows)
        var y = 0
        while (y < h) {
            if (cancel.cancelled) throw CancelledException()
            val n = minOf(rows, h - y)
            bmp.getPixels(buf, 0, bmp.width, 0, y, bmp.width, n)
            if (bmp.width != w) for (r in 0 until n) System.arraycopy(buf, r * bmp.width, buf, r * w, w)
            com.novacamera.s24.core.imaging.HalfResidual.box2(buf, w, n, orig, y / 2)
            y += n
        }
        val small = Bitmap.createBitmap(orig, sw, sh, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
        try {
            if (!denoiseInPlace(context, small, strength, cancel, budget, skipClean) { progress(0.9f * it) }) return false
            report = "meia resolução ${sw}×${sh} para ${bmp.width}×${bmp.height} · " + report
            val den = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
            y = 0
            while (y < bmp.height) {
                if (cancel.cancelled) throw CancelledException()
                val n = minOf(rows, bmp.height - y)
                bmp.getPixels(buf, 0, bmp.width, 0, y, bmp.width, n)
                com.novacamera.s24.core.imaging.HalfResidual.addResidual(buf, bmp.width, n, y, orig, den, sw, sh)
                bmp.setPixels(buf, 0, bmp.width, 0, y, bmp.width, n)
                y += n
            }
            progress(1f)
            return true
        } finally { small.recycle() }
    }

    /** Escolhe resolução cheia ou meia ([HALF_FROM]). */
    fun denoiseAuto(
        context: Context, bmp: Bitmap, strength: Float, cancel: CancelToken,
        budget: DenoisePolicy.Budget, skipClean: Boolean = true, half: Boolean = bmp.width.toLong() * bmp.height > HALF_FROM,
        progress: (Float) -> Unit,
    ): Boolean = if (half) denoiseHalf(context, bmp, strength, cancel, budget, skipClean, progress)
        else denoiseInPlace(context, bmp, strength, cancel, budget, skipClean, progress)

    @Synchronized
    fun denoiseInPlace(
        context: Context, bmp: Bitmap, strength: Float, cancel: CancelToken,
        budget: DenoisePolicy.Budget = DenoisePolicy.FOREGROUND,
        /** Bloco já limpo ([com.novacamera.s24.core.imaging.TileNoise]) fica como está, sem inferência. */
        skipClean: Boolean = false, progress: (Float) -> Unit,
    ): Boolean {
        val started = SystemClock.elapsedRealtime()
        if (strength <= 0f || started < retryAfterMs) return false
        // Mesmo tamanho e orçamento que acabaram de não caber: não carrega o modelo para desistir de novo.
        overBudget.skippedSince(bmp.width.toLong() * bmp.height, budget, started)?.let { ago ->
            report = "IA dispensada sem carregar: a última projeção para ${bmp.width}×${bmp.height} excedeu o orçamento " +
                "(medida há ${ago / 60_000} min; mede de novo após ${com.novacamera.s24.core.DenoiseSkip.HOLD_MS / 60_000} min)"
            return false
        }
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
        if (!DenoisePolicy.canRun(pixels, gpu, headroom, w)) {
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
        var inferred = 0
        var skipped = 0
        var status = "interrompida"
        // Resultados por faixa ([DenoiseBands]); a faixa volta ao bitmap quando ninguém mais lê as
        // linhas dela. Cancelamento/fallback pode deixar bmp em parte refinado: quem chama descarta.
        val pending = ArrayDeque<Pair<Int, IntArray>>()
        val free = ArrayDeque<IntArray>()
        fun flush(before: Int?) {
            while (pending.isNotEmpty()) {
                val (band, buf) = pending.first()
                if (before != null && !com.novacamera.s24.core.DenoiseBands.writable(band, before, h, TILE, PAD)) break
                pending.removeFirst()
                val y0 = band * TILE
                bmp.setPixels(buf, 0, w, 0, y0, w, com.novacamera.s24.core.DenoiseBands.outputEnd(band, h, TILE) - y0)
                free.addLast(buf)
            }
        }
        try {
            outTile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (ty in 0 until ny) {
                flush(ty)
                val result = free.removeFirstOrNull() ?: IntArray(TILE * w)
                pending.addLast(ty to result)
                for (tx in 0 until nx) {
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
                    val tw = min(TILE, w - x0); val th = min(TILE, h - y0)
                    if (skipClean && com.novacamera.s24.core.imaging.TileNoise.clean(src, size, size)) {
                        for (y in 0 until th) System.arraycopy(src, (y0 + y - sy) * size + (x0 - sx), result, y * w + x0, tw)
                        completed++; skipped++
                        progress(completed.toFloat() / (nx * ny))
                        continue
                    }
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
                    completed++; inferred++
                    if (inferred == 1) firstTileMs = elapsed
                    outTile.getPixels(den, 0, size, 0, 0, size, size)
                    for (y in 0 until th) for (x in 0 until tw) {
                        val i = (y0 + y - sy) * size + (x0 + x - sx)
                        val o = src[i]; val d = den[i]
                        result[y * w + x0 + x] = Px.argb(
                            (Px.r(o) + (Px.r(d) - Px.r(o)) * s + 0.5f).toInt(),
                            (Px.g(o) + (Px.g(d) - Px.g(o)) * s + 0.5f).toInt(),
                            (Px.b(o) + (Px.b(d) - Px.b(o)) * s + 0.5f).toInt())
                    }
                    // A primeira chamada Vulkan pode compilar pipelines. Medir dois blocos
                    // seguintes, incluindo cópias/mistura, antes de projetar a foto inteira.
                    // Todos os blocos continuam sujeitos aos mesmos limites de tempo/térmica.
                    if (inferred == 2 || inferred == 3) {
                        steadyTileMs = maxOf(steadyTileMs, SystemClock.elapsedRealtime() - tileStarted)
                    }
                    // Projeção pelos blocos que de fato passam pela rede (os limpos pulados até aqui).
                    val remainingInferred = ((nx * ny - completed).toLong() * inferred + completed - 1) / completed
                    if (inferred >= 3 && !DenoisePolicy.remainingFits(
                            SystemClock.elapsedRealtime() - started,
                            SystemClock.elapsedRealtime() - inferenceStarted,
                            steadyTileMs, remainingInferred.toInt(), budget)) {
                        status = "fallback: custo sustentado projetado excede orçamento"
                        overBudget.recordOverBudget(pixels, budget, SystemClock.elapsedRealtime())
                        return false
                    }
                    progress(completed.toFloat() / (nx * ny))
                }
            }
            if (cancel.cancelled) throw CancelledException()
            if (!DenoisePolicy.allowed(ProcessingQueue.heat) ||
                DenoisePolicy.expired(SystemClock.elapsedRealtime() - started, SystemClock.elapsedRealtime() - inferenceStarted, budget)) {
                status = "fallback por custo/temperatura"
                return false
            }
            flush(null)
            status = "concluída"
            return true
        } finally {
            lastUsedMs = SystemClock.elapsedRealtime()
            report = "NAFNet $status · backend=${if (gpu) "Vulkan" else "CPU"}, reutilizado=$reused, " +
                "carga=${loadMs}ms, primeira inferência=${firstTileMs}ms, bloco sustentado=${steadyTileMs}ms, inferência=${runMs}ms, " +
                "total=${lastUsedMs - started}ms, blocos=$completed/${nx * ny} (limpos pulados=$skipped), $w×$h, " +
                "PSS início/fim=${pssBeforeKb}/${android.os.Debug.getPss()} KiB (não pico), " +
                "térmico=${heatBefore?.thermalStatus}/${ProcessingQueue.heat?.thermalStatus}, " +
                "bateria °C=${heatBefore?.batteryTempC}/${ProcessingQueue.heat?.batteryTempC}"
            Log.i(TAG, report)
            inTile.recycle(); outTile?.recycle()
        }
    }
}
