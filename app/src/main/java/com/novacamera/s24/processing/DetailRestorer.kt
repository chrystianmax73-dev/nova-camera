package com.novacamera.s24.processing

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.novacamera.s24.core.DenoisePolicy
import com.novacamera.s24.core.imaging.DetailGate
import kotlin.math.min

/**
 * IA de detalhe: Real-ESRGAN compacto ("realesr-general-x4v3", BSD-3-Clause, convertido no CI por
 * tools/realesr/export_ncnn.py) no mesmo ncnn do NAFNet. A foto ampliada digitalmente é reduzida à
 * metade (escala que o sensor viu de fato), a rede devolve o dobro e cada bloco só entra se, reduzido de
 * novo, bater com a entrada ([DetailGate]); onde não bate (letra deformada, textura inventada) fica o
 * original. Quando e onde roda: [com.novacamera.s24.core.DetailPolicy]. Sem o modelo na build, [available]
 * é falso e nada muda.
 */
object DetailRestorer {
    private const val TAG = "NovaDetail"
    private const val PARAM = "detail/detail.param"
    private const val BIN = "detail/detail.bin"
    /** Bloco útil na metade (múltiplo de [DetailGate.BLOCK]) e margem de contexto. */
    private const val TILE = 192
    private const val PAD = 12
    private const val BAND = 64

    private val libLoaded = runCatching { System.loadLibrary("novadenoise") }.onFailure { Log.w(TAG, "biblioteca nativa ausente", it) }.isSuccess
    @Volatile private var handle = 0L
    private var lastUsedMs = 0L
    @Volatile var report = "IA de detalhe ainda não executada"
        private set

    @JvmStatic private external fun nativeLoad(assets: AssetManager, param: String, bin: String, useGpu: Boolean): Long
    @JvmStatic private external fun nativeUsesGpu(handle: Long): Boolean
    @JvmStatic private external fun nativeRun(handle: Long, input: Bitmap, output: Bitmap): Boolean
    @JvmStatic private external fun nativeRelease(handle: Long)

    fun available(context: Context): Boolean =
        libLoaded && runCatching { context.assets.list("detail")?.toSet()?.containsAll(listOf("detail.param", "detail.bin")) == true }.getOrDefault(false)

    @Synchronized
    private fun ensureLoaded(context: Context): Boolean {
        if (handle != 0L) return true
        if (!available(context)) return false
        handle = nativeLoad(context.assets, PARAM, BIN, true)
        return handle != 0L
    }

    @Synchronized
    fun release() { if (handle != 0L) { nativeRelease(handle); handle = 0L } }

    @Synchronized
    fun releaseIfIdle(nowMs: Long) { if (handle != 0L && nowMs - lastUsedMs >= DenoisePolicy.IDLE_MS) release() }

    /** [accepted]: fração da foto em que a IA entrou (0 quando não aplicada). */
    class Result(val applied: Boolean, val accepted: Float, val text: String)

    /** Aplica no lugar em [bmp] (ARGB mutável). Cancelamento lança [CancelledException]; o resto devolve o motivo. */
    @Synchronized
    fun restore(context: Context, bmp: Bitmap, cancel: CancelToken, budget: DenoisePolicy.Budget, progress: (Float) -> Unit): Result {
        val started = SystemClock.elapsedRealtime()
        fun done(r: Result) = r.also { report = it.text; Log.i(TAG, it.text) }
        val w = bmp.width; val h = bmp.height
        val hw = w / 2; val hh = h / 2
        val size = TILE + 2 * PAD
        if (hw < size || hh < size) return done(Result(false, 0f, "IA de detalhe dispensada: foto pequena"))
        if (!ensureLoaded(context)) return done(Result(false, 0f, "IA de detalhe: modelo indisponível"))
        val gpu = nativeUsesGpu(handle)
        lastUsedMs = SystemClock.elapsedRealtime()
        // Metade da foto (média 2×2), lida em faixas para não duplicar a foto inteira na memória Java.
        val half = IntArray(hw * hh)
        run {
            val buf = IntArray(w * 2 * BAND)
            var y = 0
            while (y < hh) {
                val rows = min(BAND, hh - y)
                bmp.getPixels(buf, 0, w, 0, 2 * y, w, 2 * rows)
                System.arraycopy(DetailGate.halve(buf, w, 2 * rows), 0, half, y * hw, hw * rows)
                y += rows
            }
        }
        val ow = hw * 2; val oh = hh * 2
        var out: Bitmap? = null
        val inTile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val outTile = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
        try {
            out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            val gbw = (hw + DetailGate.BLOCK - 1) / DetailGate.BLOCK; val gbh = (hh + DetailGate.BLOCK - 1) / DetailGate.BLOCK
            val err = FloatArray(gbw * gbh)
            val src = IntArray(size * size); val den = IntArray(size * size * 4)
            val nx = (hw + TILE - 1) / TILE; val ny = (hh + TILE - 1) / TILE
            val inferenceStarted = SystemClock.elapsedRealtime()
            var runMs = 0L
            for (ty in 0 until ny) for (tx in 0 until nx) {
                if (cancel.cancelled) throw CancelledException()
                if (!DenoisePolicy.allowed(ProcessingQueue.heat) ||
                    DenoisePolicy.expired(SystemClock.elapsedRealtime() - started, SystemClock.elapsedRealtime() - inferenceStarted, budget))
                    return done(Result(false, 0f, "IA de detalhe interrompida por custo/temperatura (${tx + ty * nx}/${nx * ny} blocos)"))
                val x0 = tx * TILE; val y0 = ty * TILE
                val sx = (x0 - PAD).coerceIn(0, hw - size); val sy = (y0 - PAD).coerceIn(0, hh - size)
                for (y in 0 until size) System.arraycopy(half, (sy + y) * hw + sx, src, y * size, size)
                inTile.setPixels(src, 0, size, 0, 0, size, size)
                val t = SystemClock.elapsedRealtime()
                if (!com.novacamera.s24.core.NativeGate.run("detalhe") { nativeRun(handle, inTile, outTile) }) {
                    release()
                    return done(Result(false, 0f, "IA de detalhe: inferência falhou"))
                }
                runMs += SystemClock.elapsedRealtime() - t
                outTile.getPixels(den, 0, size * 2, 0, 0, size * 2, size * 2)
                val tw = min(TILE, hw - x0); val th = min(TILE, hh - y0)
                val centerOut = IntArray(4 * tw * th)
                for (y in 0 until 2 * th) System.arraycopy(den, (2 * (y0 - sy) + y) * size * 2 + 2 * (x0 - sx), centerOut, y * 2 * tw, 2 * tw)
                val centerHalf = IntArray(tw * th)
                for (y in 0 until th) System.arraycopy(half, (y0 + y) * hw + x0, centerHalf, y * tw, tw)
                val local = DetailGate.backError(centerOut, centerHalf, tw, th)
                val bx0 = x0 / DetailGate.BLOCK; val by0 = y0 / DetailGate.BLOCK
                for (by in 0 until local.bh) for (bx in 0 until local.bw) err[(by0 + by) * gbw + bx0 + bx] = local.values[by * local.bw + bx]
                out.setPixels(centerOut, 0, 2 * tw, 2 * x0, 2 * y0, 2 * tw, 2 * th)
                progress(0.9f * (ty * nx + tx + 1) / (nx * ny))
            }
            val weights = DetailGate.weights(DetailGate.Grid(gbw, gbh, err))
            val frac = DetailGate.acceptedFraction(weights)
            val sorted = err.sorted()
            val median = sorted[sorted.size / 2]
            val stats = "erro de volta mediano ${"%.4f".format(java.util.Locale.US, median)}, aceita em ${(frac * 100).toInt()}% da foto, " +
                "backend=${if (gpu) "Vulkan" else "CPU"}, ${nx * ny} blocos em ${runMs} ms"
            if (frac < DetailGate.MIN_ACCEPTED) return done(Result(false, frac, "IA de detalhe recusada pela checagem ($stats)"))
            val origBuf = IntArray(w * BAND); val outBuf = IntArray(ow * BAND)
            var y = 0
            while (y < oh) {
                if (cancel.cancelled) throw CancelledException()
                val rows = min(BAND, oh - y)
                bmp.getPixels(origBuf, 0, w, 0, y, w, rows)
                out.getPixels(outBuf, 0, ow, 0, y, ow, rows)
                DetailGate.blendRows(origBuf, w, y, rows, outBuf, ow, weights)
                bmp.setPixels(origBuf, 0, w, 0, y, w, rows)
                y += rows
            }
            progress(1f)
            return done(Result(true, frac, "IA de detalhe aplicada ($stats)"))
        } finally {
            out?.recycle(); inTile.recycle(); outTile.recycle()
            lastUsedMs = SystemClock.elapsedRealtime()
        }
    }
}
