package com.novacamera.s24.processing

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.novacamera.s24.core.imaging.Plane

/**
 * Profundidade relativa para o Retrato: Depth Anything V2 **Small** (Apache-2.0; as versões maiores
 * são de uso não comercial e não entram) convertido para ncnn no CI (tools/depth/export_ncnn.py).
 * Uma inferência por foto, numa cópia reduzida e quadrada; o modelo é liberado em seguida.
 * Sem o modelo na build, [available] é falso e o Retrato usa só a segmentação.
 */
object DepthEstimator {
    private const val TAG = "NovaDepth"
    private const val PARAM = "depth/depth.param"
    private const val BIN = "depth/depth.bin"
    /** Lado da entrada fixado na exportação (múltiplo de 14). */
    const val SIZE = 392

    private val libLoaded = runCatching { System.loadLibrary("novadenoise") }.onFailure { Log.w(TAG, "biblioteca nativa ausente", it) }.isSuccess
    @Volatile var report = "Profundidade: ainda não executada"
        private set

    @JvmStatic private external fun nativeLoad(assets: AssetManager, param: String, bin: String, useGpu: Boolean): Long
    @JvmStatic private external fun nativeUsesGpu(handle: Long): Boolean
    @JvmStatic private external fun nativeRun(handle: Long, input: Bitmap, output: FloatArray): Boolean
    @JvmStatic private external fun nativeRelease(handle: Long)

    fun available(context: Context): Boolean =
        libLoaded && runCatching { context.assets.list("depth")?.toSet()?.containsAll(listOf("depth.param", "depth.bin")) == true }.getOrDefault(false)

    /**
     * Profundidade inversa relativa (maior = mais perto) da foto inteira, num plano [SIZE]×[SIZE]
     * esticado sobre a foto (o chamador redimensiona para a proporção dela). Null se indisponível.
     */
    @Synchronized
    fun estimate(context: Context, photo: Bitmap): Plane? {
        if (!available(context)) { report = "Profundidade: modelo não incluído nesta build"; return null }
        val t0 = SystemClock.elapsedRealtime()
        val handle = nativeLoad(context.assets, PARAM, BIN, true)
        if (handle == 0L) { report = "Profundidade: falha ao carregar o modelo"; return null }
        val tLoad = SystemClock.elapsedRealtime() - t0
        val input = Bitmap.createScaledBitmap(photo, SIZE, SIZE, true)
        try {
            val out = FloatArray(SIZE * SIZE)
            val t1 = SystemClock.elapsedRealtime()
            val ok = nativeRun(handle, input, out)
            val tRun = SystemClock.elapsedRealtime() - t1
            report = "Profundidade (Depth Anything V2 Small): ${if (ok) "ok" else "falhou"} · backend=" +
                "${if (nativeUsesGpu(handle)) "Vulkan" else "CPU"}, carga=${tLoad}ms, inferência=${tRun}ms"
            Log.i(TAG, report)
            return if (ok && out.any { it.isFinite() && it != out[0] }) Plane(SIZE, SIZE, out) else null
        } finally {
            if (input !== photo) input.recycle()
            nativeRelease(handle)
        }
    }
}
