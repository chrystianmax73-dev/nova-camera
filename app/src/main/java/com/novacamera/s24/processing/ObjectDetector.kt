package com.novacamera.s24.processing

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.novacamera.s24.core.Detections
import com.novacamera.s24.core.NativeGate

/**
 * Toque inteligente: PP-PicoDet-S 416 (PaddleDetection, Apache-2.0; convertido no CI por
 * tools/picodet/export_ncnn.py) diz o que é o objeto tocado (80 classes do COCO) e onde ele está. Uma
 * inferência por toque, na CPU (~1,2 GFLOP). Sem o modelo na build, [available] é falso e o toque continua
 * só com o contorno.
 */
object ObjectDetector {
    private const val TAG = "NovaDetect"
    private val libLoaded = runCatching { System.loadLibrary("novadenoise") }.isSuccess
    private var handle = 0L
    @Volatile var report = "Detector ainda não usado"
        private set

    @JvmStatic private external fun nativeLoad(assets: AssetManager, param: String, bin: String): Long
    @JvmStatic private external fun nativeRun(handle: Long, input: Bitmap): FloatArray?
    @JvmStatic private external fun nativeRelease(handle: Long)

    fun available(context: Context): Boolean = libLoaded &&
        runCatching { context.assets.list("detect")?.toSet()?.containsAll(listOf("detect.param", "detect.bin")) == true }.getOrDefault(false)

    @Synchronized
    fun release() { if (handle != 0L) { val h = handle; handle = 0L; NativeGate.run("detector: liberação") { nativeRelease(h) } } }

    /** Objetos em [frame] (coordenadas do próprio frame). Lista vazia sem modelo ou em falha. */
    @Synchronized
    fun detect(context: Context, frame: Bitmap): List<Detections.Det> {
        if (!available(context)) return emptyList()
        if (handle == 0L) handle = NativeGate.run("detector: carga") { nativeLoad(context.assets, "detect/detect.param", "detect/detect.bin") }
        if (handle == 0L) return emptyList()
        val t0 = SystemClock.elapsedRealtime()
        val input = Bitmap.createScaledBitmap(frame, Detections.SIZE, Detections.SIZE, true)
        val raw = try { NativeGate.run("detector") { nativeRun(handle, input) } } finally { if (input !== frame) input.recycle() } ?: return emptyList()
        val outs = unpack(raw)
        val dets = Detections.decode(outs, frame.width, frame.height)
        report = "Detector (PP-PicoDet-S): ${dets.size} objetos em ${SystemClock.elapsedRealtime() - t0} ms"
        Log.i(TAG, report)
        return dets
    }

    private fun unpack(raw: FloatArray): List<FloatArray> {
        val n = raw[0].toInt()
        var at = 1 + 2 * n
        return (0 until n).map { k ->
            val size = raw[1 + 2 * k].toInt() * raw[2 + 2 * k].toInt()
            raw.copyOfRange(at, at + size).also { at += size }
        }
    }
}
