package com.novacamera.s24.processing

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.novacamera.s24.core.imaging.Px
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

    /** Libera a memória do modelo (chamado quando a fila esvazia). */
    @Synchronized
    fun release() {
        if (handle != 0L) { nativeRelease(handle); handle = 0L }
    }

    /**
     * Aplica no lugar, em blocos. [strength] 0..1 mistura com o original (1 = só a rede): manter
     * um pouco do grão evita o aspecto plástico. Devolve false se não houver modelo.
     */
    fun denoiseInPlace(context: Context, bmp: Bitmap, strength: Float, cancel: CancelToken, progress: (Float) -> Unit): Boolean {
        if (strength <= 0f || !ensureLoaded(context)) return false
        val w = bmp.width; val h = bmp.height
        val size = TILE + 2 * PAD
        if (w < size || h < size) return false
        val inTile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val outTile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val src = IntArray(size * size); val den = IntArray(size * size)
        val s = strength.coerceIn(0f, 1f)
        val nx = (w + TILE - 1) / TILE; val ny = (h + TILE - 1) / TILE
        val t0 = SystemClock.elapsedRealtime()
        // O resultado de cada bloco vai para um buffer separado: os blocos vizinhos precisam ler a imagem original.
        val result = IntArray(w * h)
        try {
            for (ty in 0 until ny) for (tx in 0 until nx) {
                if (cancel.cancelled) throw CancelledException()
                val x0 = tx * TILE; val y0 = ty * TILE
                val sx = (x0 - PAD).coerceIn(0, w - size); val sy = (y0 - PAD).coerceIn(0, h - size)
                bmp.getPixels(src, 0, size, sx, sy, size, size)
                inTile.setPixels(src, 0, size, 0, 0, size, size)
                if (!nativeRun(handle, inTile, outTile)) return false
                outTile.getPixels(den, 0, size, 0, 0, size, size)
                val tw = min(TILE, w - x0); val th = min(TILE, h - y0)
                for (y in 0 until th) for (x in 0 until tw) {
                    val i = (y0 + y - sy) * size + (x0 + x - sx)
                    val o = src[i]; val d = den[i]
                    result[(y0 + y) * w + x0 + x] = Px.argb(
                        (Px.r(o) + (Px.r(d) - Px.r(o)) * s + 0.5f).toInt(),
                        (Px.g(o) + (Px.g(d) - Px.g(o)) * s + 0.5f).toInt(),
                        (Px.b(o) + (Px.b(d) - Px.b(o)) * s + 0.5f).toInt())
                }
                progress((ty * nx + tx + 1).toFloat() / (nx * ny))
            }
            bmp.setPixels(result, 0, w, 0, 0, w, h)
            Log.i(TAG, "NAFNet ${w}×$h em ${SystemClock.elapsedRealtime() - t0} ms")
            return true
        } finally {
            inTile.recycle(); outTile.recycle()
        }
    }
}
