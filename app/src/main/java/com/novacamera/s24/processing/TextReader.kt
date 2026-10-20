package com.novacamera.s24.processing

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.novacamera.s24.core.NativeGate
import com.novacamera.s24.core.OcrPost
import kotlin.math.roundToInt

/**
 * Leitura de texto offline (PP-OCRv5 mobile via ncnn, pesos PaddleOCR Apache-2.0; ver docs/THIRD_PARTY.md).
 * Sob demanda (botão "Texto"); o modelo é liberado depois de [IDLE_MS] sem uso. Uma inferência nativa
 * por vez ([NativeGate]).
 */
object TextReader {
    private const val TAG = "NovaOcr"
    private const val DIR = "ocr"
    private const val DET_SIDE = 960
    private const val REC_HEIGHT = 48
    const val IDLE_MS = 60_000L

    class Line(val text: String, val box: RectF, val confidence: Float)

    private val libLoaded = runCatching { System.loadLibrary("novadenoise") }.isSuccess
    @Volatile private var handle = 0L
    private var dict: List<String> = emptyList()
    private var lastUsed = 0L
    @Volatile var report = "Leitura de texto ainda não usada"
        private set

    @JvmStatic private external fun nativeLoad(assets: AssetManager, detParam: String, detBin: String, recParam: String, recBin: String, useGpu: Boolean): Long
    @JvmStatic private external fun nativeDetect(handle: Long, input: Bitmap, out: FloatArray): Boolean
    @JvmStatic private external fun nativeRecognize(handle: Long, input: Bitmap): FloatArray?
    @JvmStatic private external fun nativeUsesGpu(handle: Long): Boolean
    @JvmStatic private external fun nativeRelease(handle: Long)

    private val FILES = listOf("PP_OCRv5_mobile_det.ncnn.param", "PP_OCRv5_mobile_det.ncnn.bin",
        "PP_OCRv5_mobile_rec.ncnn.param", "PP_OCRv5_mobile_rec.ncnn.bin", "dict.txt")

    fun available(context: Context): Boolean = libLoaded &&
        runCatching { context.assets.list(DIR)?.toSet()?.containsAll(FILES) == true }.getOrDefault(false)

    @Synchronized
    private fun ensure(context: Context): Boolean {
        if (handle != 0L) return true
        if (!available(context)) return false
        val a = context.assets
        dict = a.open("$DIR/dict.txt").bufferedReader(Charsets.UTF_8).readLines()
        handle = nativeLoad(a, "$DIR/${FILES[0]}", "$DIR/${FILES[1]}", "$DIR/${FILES[2]}", "$DIR/${FILES[3]}", true)
        return handle != 0L
    }

    @Synchronized fun release() { if (handle != 0L) { nativeRelease(handle); handle = 0L } }
    @Synchronized fun releaseIfIdle(nowMs: Long) { if (handle != 0L && nowMs - lastUsed >= IDLE_MS) release() }

    /** Lê as linhas de [photo] (orientada como na tela). Lista vazia se não houver texto ou modelo. */
    @Synchronized
    fun read(context: Context, photo: Bitmap): List<Line> {
        val t0 = SystemClock.elapsedRealtime()
        if (!ensure(context)) { report = "Leitura de texto indisponível nesta versão (modelo ausente)"; return emptyList() }
        lastUsed = t0
        val scale = minOf(1f, DET_SIDE.toFloat() / maxOf(photo.width, photo.height))
        val sw = (photo.width * scale).roundToInt().coerceAtLeast(1); val sh = (photo.height * scale).roundToInt().coerceAtLeast(1)
        val pw = (sw + 31) / 32 * 32; val ph = (sh + 31) / 32 * 32
        val det = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        try {
            Canvas(det).apply { drawColor(0xFF727272.toInt()); drawBitmap(photo, null, Rect(0, 0, sw, sh), paint) }
            val prob = FloatArray(pw * ph)
            if (!NativeGate.run("texto: detecção") { nativeDetect(handle, det, prob) }) { report = "Leitura de texto: detecção falhou"; return emptyList() }
            val tDet = SystemClock.elapsedRealtime() - t0
            val boxes = OcrPost.boxes(prob, pw, ph, 3f * scale)
            val lines = ArrayList<Line>()
            for (b in boxes) {
                val l = (b.left / scale).toInt().coerceIn(0, photo.width - 1); val t = (b.top / scale).toInt().coerceIn(0, photo.height - 1)
                val r = (b.right / scale).toInt().coerceIn(l + 1, photo.width); val bt = (b.bottom / scale).toInt().coerceIn(t + 1, photo.height)
                val bw = r - l; val bh = bt - t
                if (bh < 4 || bw < 4) continue
                val outW = (REC_HEIGHT * bw.toFloat() / bh).roundToInt().coerceIn(16, 1600)
                val crop = Bitmap.createBitmap(outW, REC_HEIGHT, Bitmap.Config.ARGB_8888)
                Canvas(crop).drawBitmap(photo, Rect(l, t, r, bt), Rect(0, 0, outW, REC_HEIGHT), paint)
                val steps = try { NativeGate.run("texto: reconhecimento") { nativeRecognize(handle, crop) } } finally { crop.recycle() } ?: continue
                val (text, conf) = OcrPost.decode(steps, dict)
                if (text.isNotBlank() && conf >= MIN_CONFIDENCE) lines += Line(text, RectF(l.toFloat(), t.toFloat(), r.toFloat(), bt.toFloat()), conf)
            }
            lastUsed = SystemClock.elapsedRealtime()
            report = "Texto (PP-OCRv5 mobile): ${lines.size} linhas de ${boxes.size} caixas · ${if (nativeUsesGpu(handle)) "Vulkan" else "CPU"} · " +
                "detecção ${tDet} ms · total ${lastUsed - t0} ms · ${photo.width}×${photo.height}"
            Log.i(TAG, report)
            return OcrPost.readingOrder(lines) { OcrPost.Box(it.box.left, it.box.top, it.box.right, it.box.bottom, it.confidence) }
        } finally { det.recycle() }
    }

    /** Linhas com confiança média abaixo disso são descartadas (ruído lido como letra). */
    private const val MIN_CONFIDENCE = 0.5f
}
