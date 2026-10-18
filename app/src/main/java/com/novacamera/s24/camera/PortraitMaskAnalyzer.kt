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
    private val captureBusy: () -> Boolean = { false },
) : ImageAnalysis.Analyzer, java.io.Closeable {
    private val app = context.applicationContext
    private var segmenter: PersonSegmenter.Live? = null
    @Volatile private var depth: DepthEstimator.Session? = null
    /**
     * Cinematográfico (0.28.1): profundidade em thread própria. Antes ela rodava dentro do quadro (~1,4 s)
     * e esse quadro passava do limite de 650 ms → máscara apagada; enquanto isso a máscara anterior
     * vencia (750 ms). Sem pessoa na cena (só profundidade) o desfoque quase nunca ficava "pronto"
     * (dono 0.28.0: "A máscara ainda não está pronta ou perdeu validade"). Agora a pessoa/plano segue
     * a cada quadro e a profundidade nova entra quando termina; movimento descarta a que está em curso.
     */
    private val depthExecutor by lazy { java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "nova-cine-depth") } }
    @Volatile private var depthInFlight = false
    @Volatile private var depthDone: Plane? = null
    private val depthGeneration = java.util.concurrent.atomic.AtomicInteger()
    private var depthFailed = false
    private var wasPersonOnly = false
    private val createdAt=SystemClock.uptimeMillis()
    private var delivered=0L
    private var analyzed=0L
    private var lastSeg = 0L
    private var lastDepth = 0L
    private val depthBudget=com.novacamera.s24.core.DepthDutyBudget()
    /** Prévia da foto: profundidade só quando a cena mudou (a etapa mais cara da análise ao vivo). */
    private val depthRefresh=com.novacamera.s24.core.DepthRefreshPolicy()
    private val sceneGuard=com.novacamera.s24.core.CinematicSceneGuard()
    private var depthN: Plane? = null
    private var prevSubject: FloatArray? = null
    private var prevAmount: FloatArray? = null
    /** Quadros seguidos com a máscara praticamente igual (cena e aparelho parados). */
    private var stillFrames = 0
    /** Quadros restantes da transição de foco entre assuntos (ver [RACK_KEEP]). */
    private var rackFocusFrames = 0
    @Volatile private var closed = false
    private var lastState: String? = "init"
    private val metricsToken=metricsGeneration.incrementAndGet()
    init { report="Análise ao vivo do Retrato: preparando ${if(cinematic) "vídeo" else "foto"}" }

    override fun analyze(image: ImageProxy) {
        var cameraClosed=false
        val analysisStarted=SystemClock.uptimeMillis()
        val frameAt=SystemClock.elapsedRealtime()
        delivered++
        var sampled=false;var copyMs=0L;var segmentMs=0L;var transformMs=0L
        var ownedFull:Bitmap?=null;var ownedSmall:Bitmap?=null;var ownedUpright:Bitmap?=null
        try {
            if (closed) return
            // A rajada e o processamento da foto têm prioridade; sem rebind nem descarregar modelos.
            // Uma inferência nativa já em voo termina; a próxima não começa durante a aquisição.
            if(!cinematic && captureBusy()) {
                if(lastState!="Análise da prévia pausada: prioridade da fotografia") {
                    effect.clearMask();depthN=null;prevSubject=null;prevAmount=null;stillFrames=0
                }
                state("Análise da prévia pausada: prioridade da fotografia");return
            }
            if(cinematic && com.novacamera.s24.processing.VideoFinishJob.state.running) {
                if(lastState!="Desfoque pausado durante acabamento local")effect.clearMask()
                depth=null;depthN=null;prevSubject=null;prevAmount=null
                state("Desfoque pausado durante acabamento local");return
            }
            val now = SystemClock.uptimeMillis()
            val status = heat()?.thermalStatus ?: 0
            if (status >= ThermalPolicy.SEVERE) {
                if(lastState!="Prévia do desfoque pausada: aparelho quente") effect.clearMask()
                depth=null; DepthEstimator.release(); segmenter?.close();segmenter=null; depthN=null;prevSubject=null;prevAmount=null
                state("Prévia do desfoque pausada: aparelho quente"); return
            }
            // Cena parada: a máscara não muda, então 3 Hz bastam (economia de CPU/GPU); qualquer
            // movimento volta a 10 Hz no quadro seguinte.
            val still = stillFrames >= STILL_AFTER
            val lowPower = !cinematic && com.novacamera.s24.core.LivePortraitPolicy.lowPower(heat())
            if (now - lastSeg < if (status >= ThermalPolicy.MODERATE || still || lowPower) 300 else 100) return
            lastSeg = now

            val rot = image.imageInfo.rotationDegrees
            val cpu0=SystemClock.currentThreadTimeMillis(); val wall0=SystemClock.uptimeMillis()
            com.novacamera.s24.core.LiveAnalysisCpu.frame(wall0)
            val selectedTap=tapInImage(image,rot)
            cameraClosed=true
            sampled=true;analyzed++
            val copyStart=SystemClock.uptimeMillis()
            val full = com.novacamera.s24.core.FrameSnapshot.readAndClose(image) { image.toBitmap() }.also { ownedFull=it }
            copyMs=SystemClock.uptimeMillis()-copyStart
            // Reduz antes de girar: o modelo de pessoa vê 256 px e a máscara final tem 128 px.
            val transformAt=SystemClock.uptimeMillis()
            val s = 256f / max(full.width, full.height)
            val small = Bitmap.createScaledBitmap(full, max(1, (full.width * s).roundToInt()), max(1, (full.height * s).roundToInt()), true).also { ownedSmall=it }
            if (small !== full) full.recycle()
            if (effect.needsCalibration) effect.offerCalibration(lumaGrid(small))
            val upright = if (rot % 360 == 0) small else
                Bitmap.createBitmap(small, 0, 0, small.width, small.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
            ownedUpright=upright
            transformMs=SystemClock.uptimeMillis()-transformAt

            if (cinematic) {
                if(sceneGuard.moved(lumaGrid(upright),selectedTap)) {
                    if (sceneGuard.selectionOnly) {
                        // Troca de assunto com a cena parada (toque ou rosto novo): a profundidade da cena
                        // continua certa; só o plano de foco muda. Mantém a máscara anterior e deixa a
                        // suavização [RACK_KEEP] levar o foco até o novo assunto em ~1 s ("rack focus",
                        // como o Cinematográfico do iPhone), em vez de apagar o desfoque e recalcular.
                        rackFocusFrames = RACK_FRAMES
                    } else {
                        depthGeneration.incrementAndGet(); depthDone=null
                        depthN=null; prevSubject=null; prevAmount=null; effect.clearMask()
                    }
                }
            }
            // Prévia da foto: modelo leve (só mostra o desfoque; a foto usa o multiclasse). O vídeo
            // cinematográfico grava esta máscara, então continua com o multiclasse.
            com.novacamera.s24.core.LiveAnalysisCpu.add(com.novacamera.s24.core.LiveAnalysisCpu.Stage.COPY,
                SystemClock.currentThreadTimeMillis()-cpu0, SystemClock.uptimeMillis()-wall0)
            val seg = segmenter ?: PersonSegmenter.Live(app, light = !cinematic).also { segmenter = it }
            val segmentStart=SystemClock.uptimeMillis()
            val segCpu0=SystemClock.currentThreadTimeMillis()
            val segResult = runCatching { seg.segment(upright) }.getOrNull()
            val person = segResult?.takeIf { it.coverage >= 0.03f }?.person
            segmentMs=SystemClock.uptimeMillis()-segmentStart
            com.novacamera.s24.core.LiveAnalysisCpu.add(com.novacamera.s24.core.LiveAnalysisCpu.Stage.SEGMENT,
                SystemClock.currentThreadTimeMillis()-segCpu0, segmentMs)
            if(closed || (!cinematic && captureBusy()))return

            val mw = max(1, upright.width / 2); val mh = max(1, upright.height / 2)
            val depthEvery = when {
                status >= ThermalPolicy.MODERATE -> if(cinematic) 1500L else Long.MAX_VALUE
                lowPower -> if (still) 5000L else 2500L
                still -> 2500L
                else -> 900L
            }
            // Pessoa na cena e nenhum toque: a máscara de pessoa basta para a prévia (a foto calcula a
            // profundidade). Sem pessoa (planta/objeto) ou com toque, a profundidade volta.
            val personOnly = com.novacamera.s24.core.LivePortraitPolicy.personOnly(cinematic, selectedTap, segResult?.coverage ?: 0f, wasPersonOnly)
            wasPersonOnly = personOnly
            if (personOnly) {
                com.novacamera.s24.core.LiveAnalysisCpu.personOnlyFrame()
                if (depthN != null) { depthN = null; depthRefresh.reset() }
            }
            // Foto: com a cena parada a profundidade daria o mesmo resultado; só recalcula se o enquadramento
            // ou o toque mudou desde o último cálculo. O cinematográfico mantém a regra própria (grava a máscara).
            val sceneLuma = if (!cinematic && depthN != null) lumaGrid(upright) else null
            val sceneChanged = sceneLuma == null || depthRefresh.needed(now, sceneLuma, selectedTap,
                if (lowPower) com.novacamera.s24.core.LivePortraitPolicy.LOW_POWER_MAX_AGE_MS else com.novacamera.s24.core.DepthRefreshPolicy.MAX_AGE_MS)
            if (cinematic && !depthFailed && !personOnly && !depthInFlight && depthBudget.allowed(now,depthEvery,lastDepth) && ProcessingQueue.pending == 0) {
                lastDepth = now
                depthInFlight = true
                val input = upright.copy(Bitmap.Config.ARGB_8888, false)
                val gen = depthGeneration.get()
                depthExecutor.execute {
                    val started=SystemClock.uptimeMillis()
                    val depthCpu0=SystemClock.currentThreadTimeMillis()
                    try {
                        if (closed || gen != depthGeneration.get()) return@execute
                        val session = DepthEstimator.shared(app).also { if (it == null) depthFailed = true; depth = it }
                        val plane = session?.run(input)?.let { DepthPortrait.normalize(Planes.resize(it, mw, mh)) }
                        if (plane != null && !closed && gen == depthGeneration.get()) depthDone = plane
                    } catch (e: Throwable) { Log.w(TAG, "profundidade do cinematográfico falhou", e) }
                    finally {
                        input.recycle()
                        depthBudget.completed(started,SystemClock.uptimeMillis())
                        com.novacamera.s24.core.LiveAnalysisCpu.add(com.novacamera.s24.core.LiveAnalysisCpu.Stage.DEPTH,
                            SystemClock.currentThreadTimeMillis()-depthCpu0, SystemClock.uptimeMillis()-started)
                        depthInFlight = false
                    }
                }
            }
            if (cinematic) depthDone?.let { depthN = it; depthDone = null }
            if (!cinematic && !depthFailed && !personOnly && sceneChanged && depthBudget.allowed(now,depthEvery,lastDepth) && ProcessingQueue.pending == 0) {
                lastDepth = now
                if (!cinematic) depthRefresh.done(now, sceneLuma ?: lumaGrid(upright), selectedTap)
                val started=SystemClock.uptimeMillis()
                val depthCpu0=SystemClock.currentThreadTimeMillis()
                // Sessão compartilhada com a foto; buscada a cada uso porque a fila pode tê-la liberado.
                val session = DepthEstimator.shared(app).also { if (it == null) depthFailed = true; depth = it }
                try { session?.run(upright)?.let { depthN = DepthPortrait.normalize(Planes.resize(it, mw, mh)) } }
                finally {
                    depthBudget.completed(started,SystemClock.uptimeMillis())
                    com.novacamera.s24.core.LiveAnalysisCpu.add(com.novacamera.s24.core.LiveAnalysisCpu.Stage.DEPTH,
                        SystemClock.currentThreadTimeMillis()-depthCpu0, SystemClock.uptimeMillis()-started)
                }
            }
            val maskCpu0=SystemClock.currentThreadTimeMillis(); val maskWall0=SystemClock.uptimeMillis()
            if(closed || (!cinematic && captureBusy()))return
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
            val keep = if (rackFocusFrames > 0) { rackFocusFrames--; RACK_KEEP } else 0.4f
            val sub = smooth(subject.data, prevSubject, keep).also { prevSubject = it }
            val amt = smooth(amount.data, prevAmount, keep).also { prevAmount = it }
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
            effect.updateMask(bytes, bs.w, bs.h,frameAt)
            com.novacamera.s24.core.LiveAnalysisCpu.add(com.novacamera.s24.core.LiveAnalysisCpu.Stage.MASK,
                SystemClock.currentThreadTimeMillis()-maskCpu0, SystemClock.uptimeMillis()-maskWall0)
            state(if (effect.needsCalibration) "Ajustando a prévia do desfoque…" else if(cinematic) if(d!=null) "Cinematográfico experimental · profundidade estimada" else "Cinematográfico · máscara de pessoa, sem profundidade" else null)
        } catch (e: Throwable) {
            Log.w(TAG, "máscara da prévia falhou", e)
        } finally {
            // Aliases (sem giro/escala) são possíveis; isRecycled impede liberação duplicada.
            listOf(ownedUpright,ownedSmall,ownedFull).forEach { if(it!=null && !it.isRecycled)it.recycle() }
            if(!cameraClosed) image.close()
            if(sampled && metricsToken==metricsGeneration.get()) {
                report="Análise ao vivo do Retrato (${if(cinematic) "vídeo" else "foto"}): ${if(closed) "inativa" else "em sessão"}" +
                    " · entregues/analisados=$delivered/$analyzed em${SystemClock.uptimeMillis()-createdAt}ms (inclui pausas) · cópia/liberação=${copyMs}ms · escala/giro/calibração=${transformMs}ms · segmentação=${segmentMs}ms · total=${SystemClock.uptimeMillis()-analysisStarted}ms" +
                    " · pessoa CPU carga=${segmenter?.loadMs}ms, entrada/nativa/extração=${segmenter?.inputMs}/${segmenter?.nativeMs}/${segmenter?.extractionMs}ms" +
                    " · profundidade backend=${depth?.let { if(it.gpu) "Vulkan solicitado" else "CPU" }} carga=${depth?.loadMs}ms" +
                    " entrada/nativa-com-cópias/resultado=${depth?.prepareMs}/${depth?.nativeMs}/${depth?.resultMs}ms" +
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

    private fun smooth(now: FloatArray, prev: FloatArray?, keep: Float = 0.4f): FloatArray =
        if (prev == null || prev.size != now.size) now else FloatArray(now.size) { prev[it] * keep + now[it] * (1f - keep) }

    private fun lumaGrid(bmp: Bitmap): FloatArray {
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        val luma = FloatArray(px.size) { com.novacamera.s24.core.imaging.Px.luma(px[it]) }
        return PreviewMaskCalibration.grid(luma, bmp.width, bmp.height)
    }

    private fun state(s: String?) {
        if (!closed && s != lastState) {
            lastState=s
            if(metricsToken==metricsGeneration.get())report="Análise ao vivo do Retrato (${if(cinematic) "vídeo" else "foto"}): estado=${s ?: "ok"}" +
                " · última profundidade/carga=${depthBudget.lastCostMs}ms. Estado da análise; não mede GPU/autonomia ou pixels do MP4."
            onState(s)
        }
    }

    /** Cancela aceitação/publicação imediatamente; liberação de modelos fica na thread proprietária. */
    fun stopAcceptingFrames() { closed=true }

    /** Chamar na mesma thread da análise (depois de [ImageAnalysis.clearAnalyzer]). */
    override fun close() {
        closed = true
        depthGeneration.incrementAndGet(); depthDone = null
        if (cinematic) depthExecutor.shutdown()
        runCatching { segmenter?.close() }; segmenter = null
        // A sessão de profundidade é compartilhada com a foto: não fecha aqui, só agenda a liberação por ociosidade.
        if (depth != null) ProcessingQueue.scheduleDepthRelease()
        depth = null
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
        /** Rack focus: 70% do plano anterior por quadro analisado (~3/s) → ~90% do caminho em ~2 s, sem salto. */
        private const val RACK_KEEP = 0.7f
        private const val RACK_FRAMES = 8
    }
}
