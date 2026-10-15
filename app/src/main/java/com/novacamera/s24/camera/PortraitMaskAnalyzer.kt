package com.novacamera.s24.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.novacamera.s24.core.DeviceHeat
import com.novacamera.s24.core.PreviewMaskCalibration
import com.novacamera.s24.core.ThermalPolicy
import com.novacamera.s24.core.imaging.DepthPortrait
import com.novacamera.s24.core.imaging.Plane
import com.novacamera.s24.core.imaging.Planes
import com.novacamera.s24.core.imaging.oriented
import com.novacamera.s24.processing.DepthEstimator
import com.novacamera.s24.processing.PersonSegmenter
import com.novacamera.s24.processing.ProcessingQueue
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Máscara da prévia do Retrato a partir dos quadros da análise: a mesma segmentação de pessoa e a
 * mesma regra de assunto pela profundidade da foto final ([DepthPortrait.plan]), em resolução
 * reduzida. Pessoa a até 10 Hz; profundidade ~1 Hz (o mapa de profundidade pode estar até ~1 s
 * atrás do quadro — a borda da pessoa vem sempre da segmentação recente). Com aquecimento, a taxa
 * cai; aquecimento severo pausa a prévia do desfoque e avisa.
 *
 * Saída em coordenadas do buffer da câmera (sem rotação), o mesmo espaço da textura da prévia.
 */
class PortraitMaskAnalyzer(
    context: Context,
    private val effect: PortraitPreviewEffect,
    private val heat: () -> DeviceHeat?,
    /** Texto curto para a interface (null = tudo certo). Chamado na thread da análise. */
    private val onState: (String?) -> Unit,
) : ImageAnalysis.Analyzer, java.io.Closeable {
    private val app = context.applicationContext
    private var segmenter: PersonSegmenter.Live? = null
    private var depth: DepthEstimator.Session? = null
    private var depthFailed = false
    private var lastSeg = 0L
    private var lastDepth = 0L
    private var depthN: Plane? = null
    private var prevSubject: FloatArray? = null
    private var prevAmount: FloatArray? = null
    @Volatile private var closed = false
    private var lastState: String? = "init"

    override fun analyze(image: ImageProxy) {
        try {
            if (closed) return
            val now = SystemClock.uptimeMillis()
            val status = heat()?.thermalStatus ?: 0
            if (status >= ThermalPolicy.SEVERE) {
                effect.clearMask(); state("Prévia do desfoque pausada: aparelho quente"); return
            }
            if (now - lastSeg < if (status >= ThermalPolicy.MODERATE) 300 else 100) return
            lastSeg = now

            val full = image.toBitmap()
            val rot = image.imageInfo.rotationDegrees
            // Reduz antes de girar: o modelo de pessoa vê 256 px e a máscara final tem 128 px.
            val s = 256f / max(full.width, full.height)
            val small = Bitmap.createScaledBitmap(full, max(1, (full.width * s).roundToInt()), max(1, (full.height * s).roundToInt()), true)
            if (small !== full) full.recycle()
            if (effect.needsCalibration) effect.offerCalibration(lumaGrid(small))
            val upright = if (rot % 360 == 0) small else
                Bitmap.createBitmap(small, 0, 0, small.width, small.height, Matrix().apply { postRotate(rot.toFloat()) }, true)

            val seg = segmenter ?: PersonSegmenter.Live(app).also { segmenter = it }
            val person = runCatching { seg.segment(upright) }.getOrNull()?.takeIf { it.coverage >= 0.03f }?.person

            val mw = max(1, upright.width / 2); val mh = max(1, upright.height / 2)
            val depthEvery = if (status >= ThermalPolicy.MODERATE) Long.MAX_VALUE else 900L
            if (!depthFailed && now - lastDepth >= depthEvery && ProcessingQueue.pending == 0) {
                lastDepth = now
                val session = depth ?: DepthEstimator.Session.open(app).also { if (it == null) depthFailed = true; depth = it }
                session?.run(upright)?.let { depthN = DepthPortrait.normalize(Planes.resize(it, mw, mh)) }
            }
            if (upright !== small) upright.recycle()
            small.recycle()

            val d = depthN?.takeIf { it.w == mw && it.h == mh }
            val (subject, amount) = when {
                d != null -> DepthPortrait.plan(d, person).let { it.subject to it.blur }
                person != null -> Planes.resize(person, mw, mh).let { p -> p to Plane(mw, mh, FloatArray(mw * mh) { 1f }) }
                else -> { effect.clearMask(); state("Retrato: aproxime-se de uma pessoa ou objeto"); return }
            }
            // Suaviza no tempo: sem tremulação na borda entre quadros.
            val sub = smooth(subject.data, prevSubject).also { prevSubject = it }
            val amt = smooth(amount.data, prevAmount).also { prevAmount = it }
            // De volta à orientação do buffer da câmera (inverso da rotação da análise).
            val back = (360 - rot % 360) % 360
            val bs = Plane(mw, mh, sub).oriented(back, false)
            val ba = Plane(mw, mh, amt).oriented(back, false)
            val bytes = ByteArray(bs.w * bs.h * 2)
            for (i in 0 until bs.w * bs.h) {
                bytes[2 * i] = (bs.data[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
                bytes[2 * i + 1] = (ba.data[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            }
            effect.updateMask(bytes, bs.w, bs.h)
            state(if (effect.needsCalibration) "Ajustando a prévia do desfoque…" else null)
        } catch (e: Throwable) {
            Log.w(TAG, "máscara da prévia falhou", e)
        } finally {
            image.close()
        }
    }

    private fun smooth(now: FloatArray, prev: FloatArray?): FloatArray =
        if (prev == null || prev.size != now.size) now else FloatArray(now.size) { prev[it] * 0.4f + now[it] * 0.6f }

    private fun lumaGrid(bmp: Bitmap): FloatArray {
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        val luma = FloatArray(px.size) { com.novacamera.s24.core.imaging.Px.luma(px[it]) }
        return PreviewMaskCalibration.grid(luma, bmp.width, bmp.height)
    }

    private fun state(s: String?) { if (s != lastState) { lastState = s; onState(s) } }

    /** Chamar na mesma thread da análise (depois de [ImageAnalysis.clearAnalyzer]). */
    override fun close() {
        closed = true
        runCatching { segmenter?.close() }; segmenter = null
        runCatching { depth?.close() }; depth = null
    }

    private companion object { const val TAG = "NovaPortraitMask" }
}
