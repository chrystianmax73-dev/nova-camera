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
    private val cinematic: Boolean = false,
    private val tapSensor: () -> Pair<Float,Float>? = { null },
) : ImageAnalysis.Analyzer, java.io.Closeable {
    private val app = context.applicationContext
    private var segmenter: PersonSegmenter.Live? = null
    private var depth: DepthEstimator.Session? = null
    private var depthFailed = false
    private var lastSeg = 0L
    private var lastDepth = 0L
    private val depthBudget=com.novacamera.s24.core.DepthDutyBudget()
    private val sceneGuard=com.novacamera.s24.core.CinematicSceneGuard()
    private var depthN: Plane? = null
    private var prevSubject: FloatArray? = null
    private var prevAmount: FloatArray? = null
    /** Quadros seguidos com a máscara praticamente igual (cena e aparelho parados). */
    private var stillFrames = 0
    @Volatile private var closed = false
    private var lastState: String? = "init"
    private val metricsToken=metricsGeneration.incrementAndGet()
    init { report="Análise ao vivo do Retrato: preparando ${if(cinematic) "vídeo" else "foto"}" }

    override fun analyze(image: ImageProxy) {
        var cameraClosed=false
        val analysisStarted=SystemClock.uptimeMillis()
        var sampled=false;var copyMs=0L;var segmentMs=0L
        var ownedFull:Bitmap?=null;var ownedSmall:Bitmap?=null;var ownedUpright:Bitmap?=null
        try {
            if (closed) return
            if(cinematic && com.novacamera.s24.processing.VideoFinishJob.state.running) {
                if(lastState!="Desfoque pausado durante acabamento local")effect.clearMask()
                depth?.close();depth=null;depthN=null;prevSubject=null;prevAmount=null
                state("Desfoque pausado durante acabamento local");return
            }
            val now = SystemClock.uptimeMillis()
            val status = heat()?.thermalStatus ?: 0
            if (status >= ThermalPolicy.SEVERE) {
                if(lastState!="Prévia do desfoque pausada: aparelho quente") effect.clearMask()
                depth?.close();depth=null; segmenter?.close();segmenter=null; depthN=null;prevSubject=null;prevAmount=null
                state("Prévia do desfoque pausada: aparelho quente"); return
            }
            // Cena parada: a máscara não muda, então 3 Hz bastam (economia de CPU/GPU); qualquer
            // movimento volta a 10 Hz no quadro seguinte.
            val still = stillFrames >= STILL_AFTER
            if (now - lastSeg < if (status >= ThermalPolicy.MODERATE || still) 300 else 100) return
            lastSeg = now

            val rot = image.imageInfo.rotationDegrees
            val selectedTap=tapInImage(image,rot)
            cameraClosed=true
            sampled=true
            val copyStart=SystemClock.uptimeMillis()
            val full = com.novacamera.s24.core.FrameSnapshot.readAndClose(image) { image.toBitmap() }.also { ownedFull=it }
            copyMs=SystemClock.uptimeMillis()-copyStart
            // Reduz antes de girar: o modelo de pessoa vê 256 px e a máscara final tem 128 px.
            val s = 256f / max(full.width, full.height)
            val small = Bitmap.createScaledBitmap(full, max(1, (full.width * s).roundToInt()), max(1, (full.height * s).roundToInt()), true).also { ownedSmall=it }
            if (small !== full) full.recycle()
            if (effect.needsCalibration) effect.offerCalibration(lumaGrid(small))
            val upright = if (rot % 360 == 0) small else
                Bitmap.createBitmap(small, 0, 0, small.width, small.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
            ownedUpright=upright

            if (cinematic) {
                if(sceneGuard.moved(lumaGrid(upright),selectedTap)) {
                    depthN=null; prevSubject=null; prevAmount=null; effect.clearMask()
                }
            }
            val seg = segmenter ?: PersonSegmenter.Live(app).also { segmenter = it }
            val segmentStart=SystemClock.uptimeMillis()
            val person = runCatching { seg.segment(upright) }.getOrNull()?.takeIf { it.coverage >= 0.03f }?.person
            segmentMs=SystemClock.uptimeMillis()-segmentStart
            if(closed)return

            val mw = max(1, upright.width / 2); val mh = max(1, upright.height / 2)
            val depthEvery = when {
                status >= ThermalPolicy.MODERATE -> if(cinematic) 1500L else Long.MAX_VALUE
                still -> 2500L
                else -> 900L
            }
            if (!depthFailed && depthBudget.allowed(now,depthEvery,lastDepth) && ProcessingQueue.pending == 0) {
                lastDepth = now
                val started=SystemClock.uptimeMillis()
                val session = depth ?: DepthEstimator.Session.open(app).also { if (it == null) depthFailed = true; depth = it }
                try { session?.run(upright)?.let { depthN = DepthPortrait.normalize(Planes.resize(it, mw, mh)) } }
                finally { depthBudget.completed(started,SystemClock.uptimeMillis()) }
            }
            if(closed)return
            if (upright !== small) upright.recycle()
            small.recycle()

            val d = depthN?.takeIf { it.w == mw && it.h == mh }
            val (subject, amount) = when {
                d != null -> DepthPortrait.plan(d, person, selectedTap).let { it.subject to it.blur }
                person != null -> Planes.resize(person, mw, mh).let { p -> p to Plane(mw, mh, FloatArray(mw * mh) { 1f }) }
                else -> { effect.clearMask(); state("Retrato: aproxime-se de uma pessoa ou objeto"); return }
            }
            prevSubject?.takeIf { it.size == subject.data.size }?.let { p ->
                var d = 0f
                for (i in p.indices) d += kotlin.math.abs(p[i] - subject.data[i])
                stillFrames = if (d / p.size < STILL_DIFF) stillFrames + 1 else 0
            } ?: run { stillFrames = 0 }
            // Suaviza no tempo: sem tremulação na borda entre quadros.
            val sub = smooth(subject.data, prevSubject).also { prevSubject = it }
            val amt = smooth(amount.data, prevAmount).also { prevAmount = it }
            if(cinematic && (SystemClock.uptimeMillis()-now>650 || (heat()?.thermalStatus ?: 0)>=ThermalPolicy.SEVERE)) {
                effect.clearMask(); state("Desfoque pausado: análise atrasada ou temperatura; vídeo comum mantido"); return
            }
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
            state(if (effect.needsCalibration) "Ajustando a prévia do desfoque…" else if(cinematic) if(d!=null) "Cinematográfico experimental · profundidade estimada" else "Cinematográfico · máscara de pessoa, sem profundidade" else null)
        } catch (e: Throwable) {
            Log.w(TAG, "máscara da prévia falhou", e)
        } finally {
            // Aliases (sem giro/escala) são possíveis; isRecycled impede liberação duplicada.
            listOf(ownedUpright,ownedSmall,ownedFull).forEach { if(it!=null && !it.isRecycled)it.recycle() }
            if(!cameraClosed) image.close()
            if(sampled && metricsToken==metricsGeneration.get()) {
                report="Análise ao vivo do Retrato (${if(cinematic) "vídeo" else "foto"}): ${if(closed) "inativa" else "em sessão"}" +
                    " · última cópia/liberação=${copyMs}ms · segmentação=${segmentMs}ms · total=${SystemClock.uptimeMillis()-analysisStarted}ms" +
                    " · última profundidade/carga=${depthBudget.lastCostMs}ms · máscara atual=${!closed && effect.maskReady}" +
                    " · estado=${lastState ?: "ok"}. Não mede GPU, autonomia ou pixels do MP4."
            }
        }
    }

    private fun tapInImage(image:ImageProxy,rot:Int):Pair<Float,Float>? = tapSensor()?.let { (x,y) ->
        val point=floatArrayOf(x,y); image.imageInfo.sensorToBufferTransformMatrix.mapPoints(point)
        val nx=(point[0]/image.width).coerceIn(0f,1f);val ny=(point[1]/image.height).coerceIn(0f,1f)
        when(rot%360) {90->1f-ny to nx;180->1f-nx to 1f-ny;270->ny to 1f-nx;else->nx to ny}
    }

    private fun smooth(now: FloatArray, prev: FloatArray?): FloatArray =
        if (prev == null || prev.size != now.size) now else FloatArray(now.size) { prev[it] * 0.4f + now[it] * 0.6f }

    private fun lumaGrid(bmp: Bitmap): FloatArray {
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        val luma = FloatArray(px.size) { com.novacamera.s24.core.imaging.Px.luma(px[it]) }
        return PreviewMaskCalibration.grid(luma, bmp.width, bmp.height)
    }

    private fun state(s: String?) { if (!closed && s != lastState) { lastState = s; onState(s) } }

    /** Cancela aceitação/publicação imediatamente; liberação de modelos fica na thread proprietária. */
    fun stopAcceptingFrames() { closed=true }

    /** Chamar na mesma thread da análise (depois de [ImageAnalysis.clearAnalyzer]). */
    override fun close() {
        closed = true
        runCatching { segmenter?.close() }; segmenter = null
        runCatching { depth?.close() }; depth = null
        depthN=null;prevSubject=null;prevAmount=null
        if(metricsToken==metricsGeneration.get())report="Análise ao vivo do Retrato: inativa · modelos liberados"
    }

    companion object {
        private val metricsGeneration=java.util.concurrent.atomic.AtomicLong()
        @Volatile var report="Análise ao vivo do Retrato: ainda não executada";private set
        private const val TAG = "NovaPortraitMask"
        /** Diferença média da máscara entre quadros abaixo disso = parado. */
        private const val STILL_DIFF = 0.006f
        private const val STILL_AFTER = 10
    }
}
