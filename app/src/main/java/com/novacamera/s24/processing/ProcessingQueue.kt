package com.novacamera.s24.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.novacamera.s24.core.DeviceHeat
import com.novacamera.s24.core.ThermalPolicy
import com.novacamera.s24.core.imaging.Plane
import com.novacamera.s24.core.imaging.Planes
import com.novacamera.s24.core.imaging.oriented
import com.novacamera.s24.core.imaging.ToneIntent
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.concurrent.Executors
import kotlin.math.max

/** O que o processamento faz com os quadros capturados. */
enum class JobKind(val label: String, val tone: ToneIntent) {
    NIGHT("Noturno", ToneIntent.NIGHT),
    HDR("HDR", ToneIntent.HDR),
    PORTRAIT("Retrato", ToneIntent.PORTRAIT),
    FOOD("Food", ToneIntent.FOOD),
    /** FOTO: ajuste automático e adaptativo da foto comum (muitas vezes nenhum). */
    AUTO("Automático", ToneIntent.LIGHT),
    PHOTO("Foto", ToneIntent.LIGHT),
    /** FOTO na tele (≥ 4,5×) com pouca luz: quadros alinhados e somados, tom da Foto (não clareia). */
    ZOOM("Zoom", ToneIntent.LIGHT),
    ;
    /** Fusão de quadros iguais (redução de ruído sem suavizar): Noturno, tele e Foto com luz média. */
    val merges get() = this == NIGHT || this == ZOOM || this == PHOTO
}

class Job(
    val kind: JobKind,
    /** Índice 0 = referência (exposição normal). */
    val frames: List<CapturedFrame>,
    val mirror: Boolean,
    /** Ampliação equivalente da captura (contraste local só no zoom). */
    val zoom: Float = 1f,
    /** AUTO: a foto comum já gravada na galeria, que será substituída pela versão pronta. */
    val sourceUri: Uri? = null,
    /** Assinatura a desenhar na versão final (null = desligada). */
    val signature: SignatureStyle? = null,
    /** Por que a captura usou um quadro só (multiquadro dispensado), para a revisão. */
    val captureNote: String? = null,
    /** AUTO: ajuste de tom permitido (desligado = só a assinatura). */
    val autoTone: Boolean = true,
    val imagingFacts: com.novacamera.s24.core.imaging.ImagingFacts = com.novacamera.s24.core.imaging.ImagingFacts(),
    /** Retrato: intensidade escolhida antes da foto (a mesma da prévia). */
    val portraitBlur: Float = 0.6f,
    /** Retrato: iluminação "Fundo escuro" (fundo ~1,2 EV mais escuro, pessoa intacta). */
    val portraitDarkBackground: Boolean = false,
    /** Nova Live: clipe curto ao redor do disparo, anexado à foto final quando os dois estiverem prontos. */
    val liveClip: LiveClips.Clip? = null,
    /** Tom e temperatura escolhidos ao vivo (a mesma conta da prévia). */
    val style: com.novacamera.s24.core.imaging.PhotoStyle = com.novacamera.s24.core.imaging.PhotoStyle(),
    /** Retrato: ponto tocado (0..1 na foto final, já orientada) que define o assunto do desfoque. */
    val subjectPoint: Pair<Float, Float>? = null,
    /**
     * Zoom digital sobre a lente (> 1): os quadros vieram sem recorte e a foto final é o centro
     * 1/srFactor montado por super-resolução. 1 = foto comum.
     */
    val srFactor: Float = 1f,
    /** Alta resolução na tele: saída [outScale]× maior que o quadro (2 = ~48 MP), por super-resolução. */
    val outScale: Float = 1f,
    val trace: com.novacamera.s24.core.PhotoTrace? = null,
) {
    val id: Long = System.currentTimeMillis()
    internal val cancel = CancelToken()
}

/** Estado publicado para a interface (sempre na thread principal). */
data class JobProgress(
    val jobId: Long, val kind: JobKind, val fraction: Float, val stage: String,
    val pending: Int, val finished: Boolean = false,
    val enhancedUri: Uri? = null, val originalUri: Uri? = null, val sessionId: String? = null,
    val message: String? = null,
    /** Miniatura do quadro da câmera (antes do processamento), enviada uma vez logo após a captura. */
    val preview: android.graphics.Bitmap? = null,
)

/**
 * Sessão de revisão (arquivos privados do app): original e "base" (resultado da fusão, antes do
 * tom/desfoque) na orientação real, mais a máscara do retrato. Permite reeditar a intensidade
 * depois da captura sem recapturar. Só as últimas [ProcessingQueue.KEEP_SESSIONS] são mantidas.
 */
class Session(val dir: File) {
    val id: String get() = dir.name
    val original get() = File(dir, "original.jpg")
    val base get() = File(dir, "base.jpg")
    val matteFile get() = File(dir, "matte.bin")
    /** Luminância da cena acima do branco SDR (HDR Nova), já na orientação da foto. */
    val hdrFile get() = File(dir, "hdr.bin")
    private val metaFile get() = File(dir, "meta.properties")
    val meta = Properties().also { p -> if (metaFile.exists()) metaFile.inputStream().use(p::load) }

    var kind: JobKind
        get() = JobKind.valueOf(meta.getProperty("kind", JobKind.AUTO.name))
        set(v) { meta.setProperty("kind", v.name) }
    var tone: Float
        get() = meta.getProperty("tone", "1").toFloat()
        set(v) { meta.setProperty("tone", v.toString()) }
    var blur: Float
        get() = meta.getProperty("blur", "0.6").toFloat()
        set(v) { meta.setProperty("blur", v.toString()) }
    var enhancedUri: Uri?
        get() = meta.getProperty("enhanced")?.let(Uri::parse)
        set(v) { if (v == null) meta.remove("enhanced") else meta.setProperty("enhanced", v.toString()) }
    var originalUri: Uri?
        get() = meta.getProperty("originalUri")?.let(Uri::parse)
        set(v) { if (v == null) meta.remove("originalUri") else meta.setProperty("originalUri", v.toString()) }
    var hasPerson: Boolean
        get() = meta.getProperty("person", "false").toBoolean()
        set(v) { meta.setProperty("person", v.toString()) }
    var note: String
        get() = meta.getProperty("note", "")
        set(v) { meta.setProperty("note", v) }
    var style: com.novacamera.s24.core.imaging.PhotoStyle
        get() = com.novacamera.s24.core.imaging.PhotoStyle.fromMeta(meta.getProperty("style"))
        set(v) { meta.setProperty("style", v.toMeta()) }
    var zoom: Float
        get() = meta.getProperty("zoom", "1").toFloat()
        set(v) { meta.setProperty("zoom", v.toString()) }
    var signature: SignatureStyle?
        get() = SignatureStyle.fromMeta(meta.getProperty("signature"))
        set(v) { if (v == null) meta.remove("signature") else meta.setProperty("signature", v.toMeta()) }
    var exif: Map<String, String>
        get() = meta.stringPropertyNames().filter { it.startsWith("exif.") }.associate { it.removePrefix("exif.") to meta.getProperty(it) }
        set(v) { v.forEach { (k, value) -> meta.setProperty("exif.$k", value) } }

    fun save() { metaFile.outputStream().use { meta.store(it, null) } }

    /** Desfoque pela profundidade estimada (0 = no foco), na orientação da foto. */
    val depthFile get() = File(dir, "depth_blur.bin")
    fun saveBlurMap(p: Plane) = savePlane(depthFile, p)
    fun loadBlurMap(): Plane? = loadPlane(depthFile)
    /** Atenção ao assunto ([com.novacamera.s24.core.imaging.SubjectAttention]): peso e luminância suave. */
    fun saveAttention(m: com.novacamera.s24.core.imaging.SubjectAttention.Maps) { savePlane(File(dir, "attention_w.bin"), m.weight); savePlane(File(dir, "attention_b.bin"), m.base) }
    fun loadAttention(): com.novacamera.s24.core.imaging.SubjectAttention.Maps? {
        val w = loadPlane(File(dir, "attention_w.bin")) ?: return null
        val b = loadPlane(File(dir, "attention_b.bin")) ?: return null
        return com.novacamera.s24.core.imaging.SubjectAttention.Maps(w, b)
    }
    fun saveHdr(p: Plane) = savePlane(hdrFile, p)
    fun loadHdr(): Plane? = loadPlane(hdrFile)
    private fun savePlane(f: File, p: Plane) = DataOutputStream(f.outputStream().buffered()).use { out ->
        out.writeInt(p.w); out.writeInt(p.h); p.data.forEach(out::writeFloat)
    }
    private fun loadPlane(f: File): Plane? = if (!f.exists()) null else runCatching {
        DataInputStream(f.inputStream().buffered()).use { inp ->
            val w = inp.readInt(); val h = inp.readInt()
            Plane(w, h, FloatArray(w * h) { inp.readFloat() })
        }
    }.getOrNull()

    fun saveMatte(p: Plane) = DataOutputStream(matteFile.outputStream().buffered()).use { out ->
        out.writeInt(p.w); out.writeInt(p.h); p.data.forEach(out::writeFloat)
    }

    fun loadMatte(): Plane? = if (!matteFile.exists()) null else DataInputStream(matteFile.inputStream().buffered()).use { inp ->
        val w = inp.readInt(); val h = inp.readInt()
        Plane(w, h, FloatArray(w * h) { inp.readFloat() })
    }
}

/**
 * Fila única de processamento em segundo plano (uma tarefa por vez, prioridade baixa de CPU).
 * O original é sempre gravado ANTES do processamento: cancelar, falta de memória ou
 * aquecimento nunca fazem a foto se perder.
 */
object ProcessingQueue {
    private const val TAG = "NovaProcessing"
    const val KEEP_SESSIONS = 4
    const val MAX_PENDING = 3

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "nova-processing")
    }
    private val main = Handler(Looper.getMainLooper())
    private val thumbs = Executors.newSingleThreadExecutor { r -> Thread(r, "nova-thumb") }

    /** ~1/16 da foto (12 MP → ~255×190): suficiente para a miniatura de 48 dp, ~30 ms. */
    internal fun previewOf(frame: CapturedFrame, mirror: Boolean): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(frame.jpeg, 0, frame.jpeg.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 256) sample *= 2
        val small = BitmapFactory.decodeByteArray(frame.jpeg, 0, frame.jpeg.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        if (frame.rotationDegrees % 360 == 0 && !mirror) return small
        val m = android.graphics.Matrix().apply { postRotate(frame.rotationDegrees.toFloat()); if (mirror) postScale(-1f, 1f) }
        return Bitmap.createBitmap(small, 0, 0, small.width, small.height, m, true).also { if (it !== small) small.recycle() }
    }
    private val listeners = mutableListOf<(JobProgress) -> Unit>()
    private val queued = ArrayList<Job>()
    @Volatile private var current: Job? = null
    /** Esfriou/voltou a carregar: o refino que esperava pela temperatura ou bateria recomeça sozinho. */
    @Volatile var heat: DeviceHeat? = null
        set(v) {
            field = v
            val c = refineContext ?: return
            if (refineCancel == null && pendingRefines > 0 && com.novacamera.s24.core.DenoisePolicy.allowed(v)) scheduleRefine(c)
        }
    @Volatile var keepOriginals = false
    /** Redução de ruído por IA (NAFNet/ncnn), experimental. */
    @Volatile var aiDenoise = false
    /** Perfil de tom "Contraste" (preto firme e contraste local); fica gravado em cada sessão. */
    @Volatile var contrastProfile = false

    val pending: Int get() = synchronized(queued) { queued.size } + (if (current != null) 1 else 0)
    fun canAccept() = pending < MAX_PENDING

    fun addListener(l: (JobProgress) -> Unit) { listeners += l }
    fun removeListener(l: (JobProgress) -> Unit) { listeners -= l }

    private fun post(p: JobProgress) = main.post { listeners.toList().forEach { it(p) } }

    /** Para anexar o Nova Live em [finish] (que não recebe contexto). */
    @Volatile private var resolver: android.content.ContentResolver? = null

    fun submit(context: Context, job: Job) {
        val app = context.applicationContext
        resolver = app.contentResolver
        MediaWriter.identify(app)
        synchronized(queued) { queued += job }
        // Processo protegido enquanto há foto na fila (abrir a galeria não deve perder a foto).
        ProcessingService.start(app)
        // A foto nova tem prioridade sobre a IA em segundo plano; o refino recomeça depois.
        refineCancel?.cancelled = true
        post(JobProgress(job.id, job.kind, 0f, "Na fila", pending))
        // Fotografia progressiva (fase 1): a miniatura mostra a foto da câmera em ~1 s, sem esperar o
        // processamento. Decodificada reduzida numa thread própria (não atrasa a fila); AUTO já está na galeria.
        if (job.kind != JobKind.AUTO) job.frames.firstOrNull()?.let { frame -> thumbs.execute {
            runCatching { previewOf(frame, job.mirror) }.getOrNull()?.let { bmp ->
                post(JobProgress(job.id, job.kind, 0f, "Na fila", pending, preview = bmp))
            }
        } }
        executor.execute {
            synchronized(queued) { queued -= job }
            current = job
            started[job.id] = android.os.SystemClock.elapsedRealtime() to heat
            try { job.trace?.mark("saída da fila");run(app, job) } finally {
                job.trace?.publish("processamento encerrado (consultar resultado)");current = null; started.remove(job.id)
                // Tarefa tratada (com ou sem sucesso): sem marca de interrompida.
                File(sessionsDir(app), "${job.id}/$INTERRUPTED_MARK").delete()
                if (pending == 0) ProcessingService.stop(app)
            }
        }
    }

    private const val INTERRUPTED_MARK = "interrupted"

    /**
     * Fotos cujo processamento foi interrompido porque o processo morreu (sistema encerrou o app,
     * travamento): o original já salvo na sessão vai para a galeria, sem processamento, uma vez.
     * Roda na fila de processamento, então nunca disputa com uma tarefa viva. AUTO não precisa:
     * a foto comum já estava na galeria antes do processamento.
     */
    fun recoverInterrupted(context: Context, done: (Int) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            var recovered = 0
            sessionsDir(app).listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val mark = File(dir, INTERRUPTED_MARK)
                if (!mark.exists()) return@forEach
                val kind = runCatching { mark.readText().trim() }.getOrDefault("")
                val session = Session(dir)
                if (kind != JobKind.AUTO.name && session.enhancedUri == null && session.original.exists()) {
                    runCatching {
                        val bmp = BitmapFactory.decodeFile(session.original.path) ?: error("original ilegível")
                        try {
                            MediaWriter.insert(app.contentResolver, bmp, "NOVA_${dir.name}_recuperada.jpg", MediaWriter.GALLERY,
                                session.exif, "Nova · processamento interrompido; original recuperado")
                        } finally { bmp.recycle() }
                        recovered++
                    }.onFailure { Log.w(TAG, "recuperação falhou: ${dir.name}", it) }
                }
                mark.delete()
            }
            if (recovered > 0) Log.i(TAG, "fotos recuperadas: $recovered")
            main.post { done(recovered) }
        }
    }

    /** Descarta só o cache do modelo, fora da UI e depois da tarefa atual. */
    fun trimModelMemory() { executor.execute { NcnnDenoiser.release() } }

    /** Pressão de memória real: libera também o modelo de profundidade mantido entre Retratos. */
    fun releaseDepthModel() { executor.execute { DepthEstimator.release() } }

    /** Modelo de profundidade fica carregado entre Retratos; liberado com calor ou depois de 1 min sem uso. */
    fun scheduleDepthRelease() {
        if ((heat?.thermalStatus ?: 0) >= ThermalPolicy.SEVERE) { DepthEstimator.release(); return }
        main.postDelayed({ executor.execute {
            if (pending == 0) DepthEstimator.releaseIfIdle(android.os.SystemClock.elapsedRealtime())
        } }, DepthEstimator.IDLE_MS + 1_000L)
    }

    /** Cancela a tarefa em andamento e as que estão na fila (os originais já salvos ficam). */
    fun cancelAll() {
        refineCancel?.cancelled = true
        current?.cancel?.cancelled = true
        synchronized(queued) { queued.forEach { it.cancel.cancelled = true } }
    }

    fun sessionsDir(context: Context) = File(context.filesDir, "review")

    fun session(context: Context, id: String): Session? =
        File(sessionsDir(context), id).takeIf { it.isDirectory }?.let(::Session)

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    private inline fun <T> timed(job:Job,name:String,block:()->T):T {
        val t=android.os.SystemClock.elapsedRealtimeNanos()
        memorySnapshot(job, "$name entrada")
        try { return block() } finally {
            job.trace?.mark("$name concluído (${(android.os.SystemClock.elapsedRealtimeNanos()-t)/1_000_000}ms)")
            memorySnapshot(job, "$name saída")
        }
    }

    private fun memorySnapshot(job:Job, label:String) {
        if(job.trace==null)return
        val rt=Runtime.getRuntime()
        job.trace.mark("$label heap=${(rt.totalMemory()-rt.freeMemory())/1048576}MiB/${rt.maxMemory()/1048576}MiB · nativo=${android.os.Debug.getNativeHeapAllocatedSize()/1048576}MiB (não PSS/pico)")
    }

    private fun run(context: Context, job: Job) {
        val resolver = context.contentResolver
        val name = stamp()
        val session = Session(File(sessionsDir(context), "${job.id}"))
        session.dir.mkdirs()
        // Marca apagada ao fim da tarefa (submit); se o processo morrer antes, a próxima abertura recupera.
        runCatching { File(session.dir, INTERRUPTED_MARK).writeText(job.kind.name) }
        session.kind = job.kind
        session.style = job.style
        if (contrastProfile) session.meta.setProperty("profile", "contrast")
        if (job.kind == JobKind.PORTRAIT) session.blur = job.portraitBlur
        if (job.kind == JobKind.PORTRAIT && job.portraitDarkBackground) session.meta.setProperty("portrait.light", "dark")
        // Noturno: referência de melhor nitidez medida, apenas com exposição/geometria iguais.
        // HDR conserva a exposição normal; não compara nitidez de exposições diferentes.
        val orderedFrames = if (job.kind.merges && job.frames.size > 1) runCatching {
            val first = job.frames[0]
            if (job.frames.any { it.evIndex != first.evIndex || it.rotationDegrees != first.rotationDegrees || it.crop != first.crop }) job.frames
            else {
                val referenceAt=android.os.SystemClock.elapsedRealtimeNanos()
                val scores = job.frames.map { frame ->
                    val src = JpegSource(frame)
                    try {
                        com.novacamera.s24.core.imaging.FrameQuality.score(src.luma(kotlin.math.max(1, kotlin.math.max(src.width, src.height) / 768)))
                    } finally { src.close() }
                }
                val index = com.novacamera.s24.core.imaging.FrameQuality.best(scores)
                job.trace?.mark("referência por energia/ruído: quadro${index+1}, custo=${(android.os.SystemClock.elapsedRealtimeNanos()-referenceAt)/1_000_000}ms"+
                    " · índices=${scores.map{it.detail}} · sigma=${scores.map{it.noise}}; não mede detalhe óptico")
                session.meta.setProperty("referenceIndex", index.toString())
                listOf(job.frames[index]) + job.frames.filterIndexed { i, _ -> i != index }
            }
        }.getOrDefault(job.frames) else job.frames
        val frame0 = orderedFrames[0]
        session.exif = MediaWriter.exifOf(frame0.jpeg)
        frame0.optics?.let{session.meta.setProperty("capture.optics",it.report())}
        job.frames.mapIndexedNotNull { i,f->f.optics?.let{"quadro${i+1}: ${it.report()}"} }.takeIf{it.isNotEmpty()}
            ?.let{session.meta.setProperty("capture.optics.frames",it.joinToString(" | "))}
        var originalUri: Uri? = null
        fun progress(f: Float, stage: String) = post(JobProgress(job.id, job.kind, f.coerceIn(0f, 1f), stage, pending))

        // A seleção por nitidez pode trocar a referência. Não usar ISO da primeira imagem para
        // ajustar outra; sem evidência nova conserva o comportamento das sessões antigas.
        val facts=frame0.optics?.let{audit->job.imagingFacts.copy(iso=audit.iso,exposureNs=audit.exposureNs)} ?: job.imagingFacts
        facts.toMeta().forEach { (k,v)->session.meta.setProperty(k,v) }
        session.zoom = job.zoom
        session.signature = job.signature
        runCatching { session.save() } // data/EXIF já gravados, para uma eventual recuperação
        try {
            // Núcleos para alinhamento e super-resolução nesta foto (um só com calor ou economia).
            Parallel.workers = heat.let { hh -> com.novacamera.s24.core.Workers.count(hh?.thermalStatus ?: 0, hh?.powerSave == true,
                Runtime.getRuntime().availableProcessors()) }
            job.trace?.mark("núcleos de processamento: ${Parallel.workers}")
            if (job.kind == JobKind.AUTO) { runAuto(context, job, session, name); return }
            // 1. Original (quadro de referência, sem processamento) — salvo primeiro, sempre.
            progress(0.02f, "Salvando original")
            var orig = timed(job,"decode/orientação original") { Engine.orient(JpegSource(frame0).let { s -> s.decodeFull().also { s.close() } }, frame0.rotationDegrees, job.mirror) }
            // Super-resolução: o "original" é o que o zoom digital de um quadro daria (centro ampliado).
            if (job.srFactor > 1.05f) orig = Engine.cropCenter(orig, job.srFactor)
            timed(job,"codificação/gravação original") { MediaWriter.writeFile(session.original, orig, 95) }
            // Original byte a byte (o JPEG da câmera, sem recompressão) em "Originais".
            if (keepOriginals) originalUri = MediaWriter.insertBytes(resolver, frame0.jpeg, "NOVA_${name}_original.jpg", MediaWriter.ORIGINALS)
            session.originalUri = originalUri
            val h = heat
            val critical = h != null && h.thermalStatus >= ThermalPolicy.CRITICAL
            val highQualityTooHot = h != null && h.thermalStatus >= ThermalPolicy.SEVERE && (job.outScale>1f || job.srFactor>1f)
            if (critical || highQualityTooHot) {
                if (originalUri == null && job.kind != JobKind.AUTO)
                    originalUri = MediaWriter.insert(resolver, orig, "NOVA_$name.jpg", MediaWriter.GALLERY, session.exif, null)
                orig.recycle()
                finish(job, session, null, originalUri, "Aparelho muito quente: salvo só o original, sem processamento nem reduzir a resolução escolhida")
                return
            }
            // Com aquecimento severo, a fusão multiquadro é pulada (um quadro só, processamento leve).
            val frames = if (h != null && h.thermalStatus >= ThermalPolicy.SEVERE) orderedFrames.take(1) else orderedFrames

            // 2. Fusão (orientação do sensor), lendo os quadros por faixas.
            var base: Bitmap
            var note = job.captureNote ?: ""
            var hdrScene: Plane? = null
            val temporalNoise = job.kind == JobKind.NIGHT || (job.kind == JobKind.ZOOM && (job.imagingFacts.iso ?: 0)>=800)
            if (job.srFactor > 1.05f || job.outScale > 1.05f) {
                orig.recycle()
                val sources = frames.map(::JpegSource)
                try {
                    val a = if (sources.size > 1) {
                        progress(0.05f, "Alinhando ${frames.size} quadros")
                        timed(job,"alinhamento ${frames.size} quadros") { Engine.align(sources, job.cancel, retainReliability = false) { progress(0.05f + 0.15f * it, "Alinhando ${frames.size} quadros") } }
                    } else null
                    // Não trocar a resolução selecionada por 1,5× conforme o lixo ainda não coletado.
                    val scale = if (a == null) 1f else safeOutScale(context, job.outScale, sources[0].width, sources[0].height)
                    val (out, n) = timed(job,"super-resolução") { Engine.superRes(sources, a, job.srFactor, scale, frame0.rotationDegrees, job.mirror, job.cancel) {
                        progress(0.2f + 0.45f * it, "Super-resolução")
                    } }
                    job.trace?.mark("SR $n/${frames.size} quadros · ${out.width}x${out.height}")
                    base = out
                    val z = String.format(java.util.Locale.US, "%.1f", job.srFactor).replace('.', ',')
                    val mp = (out.width.toLong() * out.height / 1_000_000L)
                    note = when {
                        n < 2 && job.srFactor > 1.05f -> "Zoom digital de ${z}× com um quadro (movimento ou alinhamento incerto)"
                        n < 2 -> "Alta resolução indisponível (movimento ou alinhamento incerto): um quadro"
                        job.srFactor > 1.05f -> "Super-resolução: $n de ${frames.size} quadros (zoom digital de ${z}× sobre a lente)"
                        else -> "Alta resolução: $n de ${frames.size} quadros, $mp MP"
                    } + if (scale < job.outScale - 0.01f) " · memória curta: resolução reduzida" else ""
                } finally { job.trace?.mark(sources.mapIndexed { i,s -> "JPEG${i+1}: ${s.cacheReport()}" }.joinToString("; ")); sources.forEach { it.close() } }
            } else if (frames.size > 1 && (job.kind.merges || job.kind == JobKind.HDR)) {
                orig.recycle()
                val sources = frames.map(::JpegSource)
                try {
                    progress(0.05f, "Alinhando ${frames.size} quadros")
                    val a = timed(job,"alinhamento ${frames.size} quadros") { Engine.align(sources, job.cancel, temporalNoise = temporalNoise) { progress(0.05f + 0.15f * it, "Alinhando ${frames.size} quadros") } }
                    base = Bitmap.createBitmap(sources[0].width, sources[0].height, Bitmap.Config.ARGB_8888)
                    if (job.kind.merges) {
                        val rejected = Engine.mergeInto(sources, base, a, job.cancel, temporalNoise = temporalNoise,
                            onNoise={job.trace?.mark("fusão: $it; ruído alinhamento=${a.noise} escala0..1 · por par=${a.pairNoise}")}) { progress(0.2f + 0.45f * it, "Reduzindo ruído") }
                        val n = a.usedFrames
                        job.trace?.mark("fusão temporal: usados=$n/${frames.size}, ruído alinhamento=${a.noise}, rejeição=$rejected, correlação=$temporalNoise, inseguro=${a.unsafe}")
                        note = if (n < 2) "Movimento ou alinhamento incerto: usado o quadro de referência, sem fusão"
                               else "$n de ${frames.size} quadros fundidos" + if (rejected > 0.15f) " · contribuições inconsistentes em parte da cena" else ""
                    } else {
                        val n = Engine.fuseInto(sources, base, a, job.cancel, onScene = { hdrScene = it }) {
                            progress(0.2f + 0.45f * it, "Combinando exposições")
                        }
                        note = if (n < 2) "Movimento ou alinhamento incerto: usado o quadro de referência, sem fusão"
                               else "$n de ${frames.size} exposições combinadas (só onde coincidem)"
                    }
                    session.meta.setProperty("unsafe", a.unsafe.joinToString { "%.2f".format(java.util.Locale.US, it) })
                } finally { job.trace?.mark(sources.mapIndexed { i,s -> "JPEG${i+1}: ${s.cacheReport()}" }.joinToString("; ")); sources.forEach { it.close() } }
                base = Engine.orient(base, frame0.rotationDegrees, job.mirror)
                hdrScene?.let { runCatching { session.saveHdr(it.oriented(frame0.rotationDegrees, job.mirror)) } }
            } else {
                base = orig
                // Foto de um quadro (luz boa) é o caminho normal, não uma falha do multiquadro.
                if ((job.kind.merges || job.kind == JobKind.HDR) && !(job.kind == JobKind.PHOTO && job.frames.size < 2))
                    note = job.captureNote ?: "Um quadro (multiquadro indisponível nesta captura)"
            }
            if (job.cancel.cancelled) throw CancelledException()
            // Atenção ao assunto: FOTO no escuro (ISO ≥ 800), não Noturno/Retrato/Food. Calculada e provada na
            // versão reduzida; aplicada no acabamento (render), também na revisão.
            val photoIso = job.imagingFacts.iso ?: jpegIso(frame0.jpeg)
            if ((job.kind == JobKind.PHOTO || job.kind == JobKind.ZOOM || job.kind == JobKind.HDR) && (photoIso ?: 0) >= ATTENTION_MIN_ISO)
                runCatching { timed(job, "atenção ao assunto") {
                    val (px, w, h) = Engine.preview(base, 512)
                    val maps = com.novacamera.s24.core.imaging.SubjectAttention.map(px, w, h, job.subjectPoint)
                    val ok = com.novacamera.s24.core.imaging.SubjectAttention.accept(px, w, h, maps)
                    job.trace?.mark("atenção: cobertura=${"%.1f".format(Locale.US, maps.coverage * 100)}% toque=${job.subjectPoint != null} aceita=$ok")
                    if (ok) {
                        session.saveAttention(maps)
                        note = listOf(note, "sombra do assunto clareada (${"%.0f".format(Locale.US, maps.coverage * 100)}% da cena)").filter { it.isNotEmpty() }.joinToString(" · ")
                    }
                } }.onFailure { Log.w(TAG, "atenção ao assunto falhou", it) }
            // Redução de ruído por IA em segundo plano, depois que a foto já está na galeria ([AiAutoPolicy]:
            // Noturno, Retrato, zoom e pouca luz; Alta resolução incluída, em faixas). A base sai com mais
            // qualidade para a rede.
            val aiLater = com.novacamera.s24.core.AiAutoPolicy.wanted(aiDenoise,
                job.kind == JobKind.NIGHT || job.kind == JobKind.PORTRAIT, job.kind == JobKind.FOOD, job.zoom,
                job.imagingFacts.iso ?: jpegIso(frame0.jpeg), base.width.toLong() * base.height) && NcnnDenoiser.available(context)
            timed(job,"codificação/gravação base") { MediaWriter.writeFile(session.base, base, if (aiLater) 98 else 95) }

            // 3. Retrato: segmentação + máscara refinada (guardada para reedição).
            if (job.kind == JobKind.PORTRAIT) {
                progress(0.68f, "Separando pessoa e fundo")
                val seg = runCatching { timed(job,"segmentação/carga") { PersonSegmenter.segment(context, base) } }.onFailure { Log.w(TAG, "segmentação falhou", it) }.getOrNull()
                val person = seg?.takeIf { it.coverage >= 0.03f }
                // Profundidade estimada: assunto inteiro (pessoa ou objeto do centro) nítido e desfoque
                // crescendo com a distância até ele.
                progress(0.69f, "Estimando profundidade")
                val depth = runCatching { timed(job,"profundidade/carga") { DepthEstimator.estimate(context, base) } }
                    .also { scheduleDepthRelease() }
                    .onFailure { Log.w(TAG, "profundidade falhou", it) }.getOrNull()
                val subject = runCatching { timed(job,"refino de máscara") { Engine.portraitSubject(base, person?.person, person?.hair, depth, job.subjectPoint,
                    report={job.trace?.mark(it)}) } }
                    .onFailure { Log.w(TAG, "assunto do retrato falhou", it) }.getOrNull()
                if (subject != null) {
                    session.saveMatte(subject.matte)
                    subject.blur?.let { runCatching { session.saveBlurMap(it) } }
                    session.hasPerson = true
                    // Soma à nota da captura (ex.: alta resolução), em vez de apagá-la.
                    subject.note?.let { note = if (note.isBlank()) it else "$note · $it" }
                } else {
                    val why = if (seg == null) "Segmentação indisponível: foto salva sem desfoque"
                              else "Nenhuma pessoa ou objeto destacado: foto salva sem desfoque"
                    note = if (note.isBlank()) why else "$note · $why"
                }
            }
            session.note = note
            session.save()

            // 4. Acabamento (tom, desfoque) e gravação da versão aprimorada.
            timed(job,"tom/detalhe/desfoque") { render(session, base, session.tone, session.blur, job.cancel, job.trace) { progress(0.7f + 0.25f * it, "Ajustando tons") } }
            job.signature?.let { Signature.draw(base, it.withExif(session.exif)) }
            UltraHdr.attach(session, base)
            progress(0.96f, "Salvando")
            val label = if (job.kind == JobKind.AUTO) "aprimorada" else job.kind.name.lowercase(Locale.US)
            val enhanced = timed(job,"codificação/gravação final") { MediaWriter.insert(resolver, base, "NOVA_${name}_$label.jpg", MediaWriter.GALLERY, session.exif,
                "Nova ${job.kind.label}" + (if (note.isNotEmpty()) " · $note" else "") +
                    (frame0.optics?.let{" · ${it.report()}"} ?: "")) }
            job.trace?.mark("resultado: $note")
            base.recycle()
            session.enhancedUri = enhanced
            if (aiLater) session.meta.setProperty(AI_KEY, AI_PENDING)
            session.save()
            finish(job, session, enhanced, originalUri, note.ifEmpty { null })
            if (aiLater) synchronized(refines) { refines.remove(session.id); refines.addLast(session.id) }
        } catch (e: CancelledException) {
            finish(job, session, null, originalUri ?: saveOriginalFallback(context, session, name, job), "Cancelado · original salvo")
        } catch (e: SelectedResolutionUnavailable) {
            finish(job, session, null, originalUri ?: saveOriginalFallback(context, session, name, job),
                "A resolução escolhida não coube na memória: somente original salvo, sem gerar uma versão reduzida")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "sem memória", e)
            finish(job, session, null, originalUri ?: saveOriginalFallback(context, session, name, job), "Memória insuficiente: salvo só o original")
        } catch (e: Exception) {
            Log.e(TAG, "falha no processamento", e)
            finish(job, session, null, originalUri ?: saveOriginalFallback(context, session, name, job), "Falha no processamento: salvo só o original")
        } finally {
            prune(context)
            releaseModelLater()
            scheduleRefine(context)
        }
    }

    /** Reutilização curta entre fotos; descarte fora da UI e sem liberar durante inferência. */
    private fun releaseModelLater() {
        if ((heat?.thermalStatus ?: 0) >= ThermalPolicy.MODERATE || !aiDenoise) NcnnDenoiser.release()
        else main.postDelayed({ executor.execute {
            if (pending == 0 && refineCancel == null) NcnnDenoiser.releaseIfIdle(android.os.SystemClock.elapsedRealtime())
        } }, com.novacamera.s24.core.DenoisePolicy.IDLE_MS)
    }

    private const val AI_KEY = "ai"
    private const val AI_PENDING = "pending"
    /** A rede já rodou e a base foi regravada; falta só o acabamento e a troca na galeria. */
    private const val AI_BASE = "base"
    private const val AI_DONE = "done"
    private const val AI_SKIPPED = "skipped"

    /** Sessões esperando a IA, na ordem das fotos. Só roda com a fila de fotos vazia. */
    private val refines = ArrayDeque<String>()
    @Volatile private var refineCancel: CancelToken? = null

    /** Refino pendente (para diagnóstico e testes). */
    val pendingRefines: Int get() = synchronized(refines) { refines.size }
    /** Pedidos feitos na revisão: rodam mesmo com a IA automática desligada, com orçamento de minutos. */
    private val manualRefines = HashSet<String>()
    @Volatile private var refineContext: Context? = null
    @Volatile private var refineRunning: String? = null
    @Volatile private var refineFraction = 0f

    enum class RefineState { NONE, WAITING, RUNNING, DONE, FAILED }
    data class RefineStatus(val state: RefineState, val fraction: Float = 0f, val detail: String = "")

    /** Estado do refino por IA de uma sessão (relê do disco: o refino grava numa cópia própria). */
    fun refineStatus(context: Context, id: String): RefineStatus {
        if (refineRunning == id) return RefineStatus(RefineState.RUNNING, refineFraction)
        if (synchronized(refines) { id in refines }) return RefineStatus(RefineState.WAITING)
        val meta = session(context, id)?.meta ?: return RefineStatus(RefineState.NONE)
        return when (meta.getProperty(AI_KEY)) {
            AI_DONE -> RefineStatus(RefineState.DONE, 1f, meta.getProperty("aiReport", ""))
            AI_SKIPPED, AI_BASE -> RefineStatus(RefineState.FAILED, 0f, meta.getProperty("aiReport", ""))
            else -> RefineStatus(RefineState.NONE)
        }
    }

    /** "Refinar foto inteira" da revisão. Devolve o motivo quando não dá para pedir. */
    fun requestRefine(context: Context, session: Session): String? {
        val app = context.applicationContext
        if (!NcnnDenoiser.available(app)) return "Modelo de IA ausente nesta versão"
        if (session.meta.getProperty("banded") == "true") return "Foto de alta resolução: a IA não roda nela"
        if (session.enhancedUri == null || !session.base.exists()) return "Foto sem versão Nova para substituir"
        if (session.meta.getProperty(AI_KEY) == AI_DONE) return "Esta foto já foi refinada por IA"
        refineContext = app
        synchronized(refines) { manualRefines += session.id; refines.remove(session.id); refines.addFirst(session.id) }
        scheduleRefine(app)
        return null
    }

    private fun scheduleRefine(context: Context) {
        if (synchronized(refines) { refines.isNotEmpty() }) executor.execute { runNextRefine(context.applicationContext) }
    }

    /**
     * IA em segundo plano: a foto Nova já está na galeria; aqui a base é limpa pela rede, recebe o
     * mesmo acabamento (tom, desfoque, assinatura) e substitui a mesma entrada da galeria — continua
     * uma foto só. Uma foto nova, aquecimento, economia de bateria ou o orçamento interrompem; a
     * foto da galeria fica como estava. Não sobrevive ao fechamento forçado do app.
     */
    private fun runNextRefine(context: Context) {
        if (pending > 0) return // a foto seguinte agenda de novo quando terminar
        refineContext = context
        if (!aiDenoise) synchronized(refines) { refines.retainAll(manualRefines) }
        // Espera a próxima foto ou a mudança de temperatura/bateria ([heat]).
        if (!com.novacamera.s24.core.DenoisePolicy.allowed(heat)) return
        val id = synchronized(refines) { refines.removeFirstOrNull() } ?: return
        val manual = synchronized(refines) { manualRefines.remove(id) }
        val session = session(context, id)
        val uri = session?.enhancedUri
        if (session == null || uri == null || !session.base.exists()) return scheduleRefine(context) // sessão já descartada
        val token = CancelToken()
        refineCancel = token
        refineRunning = id; refineFraction = 0f
        var bmp: Bitmap? = null
        try {
            bmp = BitmapFactory.decodeFile(session.base.path, BitmapFactory.Options().apply {
                inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: error("base ilegível")
            if (session.meta.getProperty(AI_KEY) != AI_BASE) {
                val t0 = android.os.SystemClock.elapsedRealtime()
                // Automático e pedido usam o mesmo teto: 12 MP leva ~1 min e 49 MP alguns minutos; temperatura,
                // economia e bateria baixa continuam interrompendo, e uma foto nova cancela e reenfileira.
                val budget = com.novacamera.s24.core.DenoisePolicy.MANUAL
                // Mede antes, age na medida, prova depois ([AiQuality]); recusada, a galeria fica como está.
                val before = aiStats(bmp)
                val strength = com.novacamera.s24.core.imaging.AiQuality.strengthFor(before.noise)
                fun skip(reason: String) {
                    session.meta.setProperty(AI_KEY, AI_SKIPPED); session.meta.setProperty("aiReport", reason); session.save()
                    NcnnDenoiser.noteDecision(reason)
                }
                if (strength <= 0f) return skip("IA dispensada: foto já limpa (ruído σ=${"%.1f".format(Locale.US, before.noise)})")
                val ok = NcnnDenoiser.denoiseAuto(context, bmp, strength, token, budget) { refineFraction = 0.9f * it }
                if (!ok) {
                    session.meta.setProperty(AI_KEY, AI_SKIPPED); session.meta.setProperty("aiReport", NcnnDenoiser.report); session.save()
                    return
                }
                val after = aiStats(bmp)
                val measured = "ruído σ ${"%.1f".format(Locale.US, before.noise)}→${"%.1f".format(Locale.US, after.noise)}, " +
                    "bordas ${"%.0f".format(Locale.US, before.edges)}→${"%.0f".format(Locale.US, after.edges)}, força ${(strength * 100).toInt()}%"
                if (!com.novacamera.s24.core.imaging.AiQuality.accept(before, after))
                    return skip("IA recusada pela validação ($measured); foto mantida · ${NcnnDenoiser.report}")
                NcnnDenoiser.noteDecision("IA aceita ($measured) · ${NcnnDenoiser.report}")
                MediaWriter.writeFile(session.base, bmp, 98)
                val secs = (android.os.SystemClock.elapsedRealtime() - t0) / 100 / 10.0
                session.note = (if (session.note.isEmpty()) "" else "${session.note} · ") + "ruído reduzido por IA em $secs s"
                session.meta.setProperty(AI_KEY, AI_BASE); session.meta.setProperty("aiReport", NcnnDenoiser.report); session.save()
            }
            render(session, bmp, session.tone, session.blur, token) {}
            if (token.cancelled) throw CancelledException()
            session.signature?.let { Signature.draw(bmp, it.withExif(session.exif)) }
            UltraHdr.attach(session, bmp)
            MediaWriter.write(context.contentResolver, uri, bmp, session.exif,
                "Nova ${session.kind.label}" + if (session.note.isNotEmpty()) " · ${session.note}" else "")
            session.meta.setProperty(AI_KEY, AI_DONE); session.save()
            Log.i(TAG, "IA em segundo plano concluída: $id")
        } catch (e: CancelledException) {
            synchronized(refines) { if (id !in refines) refines.addFirst(id); if (manual) manualRefines += id }
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "IA em segundo plano sem memória: $id", e)
        } catch (e: Exception) {
            Log.w(TAG, "IA em segundo plano falhou: $id", e) // ex.: foto apagada da galeria
        } finally {
            bmp?.recycle()
            refineCancel = null
            refineRunning = null
            releaseModelLater()
            scheduleRefine(context)
        }
    }

    /**
     * FOTO: a foto comum já está na galeria ([Job.sourceUri]). Se o plano adaptativo achar que
     * vale ajustar (ou se a assinatura estiver ligada), o original vai byte a byte para
     * "Originais" e a foto da galeria é substituída pela versão pronta — uma foto só, sem passos
     * extras. Se não valer, nada muda.
     */
    private fun runAuto(context: Context, job: Job, session: Session, name: String) {
        val resolver = context.contentResolver
        val frame = job.frames[0]
        val target = job.sourceUri ?: return finish(job, session, null, null, null)
        // Aquecimento severo/crítico: a foto comum fica como está.
        if ((heat?.thermalStatus ?: 0) >= ThermalPolicy.SEVERE) { session.dir.deleteRecursively(); return finish(job, session, null, null, null) }
        post(JobProgress(job.id, job.kind, 0.1f, "Analisando", pending))
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(frame.jpeg,0,frame.jpeg.size,bounds)
        if(bounds.outWidth.toLong()*bounds.outHeight>20_000_000L) { runHighResolutionAuto(context,job,session);return }
        val bmp = Engine.orient(JpegSource(frame).let { s -> s.decodeFull().also { s.close() } }, frame.rotationDegrees, false)
        val exif = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(frame.jpeg))
        val facts = job.imagingFacts.copy(
            iso = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 } ?: job.imagingFacts.iso,
            exposureNs = exif.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                .takeIf { it.isFinite() && it > 0 && it < 60 }?.let { (it * 1_000_000_000).toLong() } ?: job.imagingFacts.exposureNs)
        facts.toMeta().forEach { (k, v) -> session.meta.setProperty(k, v) }
        session.meta.setProperty("nai.enabled", "true")
        val plan = Engine.planAdaptive(bmp, job.zoom, facts)
        val tone = job.autoTone && !plan.skip
        val aiLater = com.novacamera.s24.core.AiAutoPolicy.wanted(aiDenoise, false, false, job.zoom, facts.iso,
            bmp.width.toLong() * bmp.height) && NcnnDenoiser.available(context)
        if (!tone && !aiLater && job.signature == null && job.style.neutral) {
            bmp.recycle()
            session.dir.deleteRecursively()
            return finish(job, session, null, null, null)
        }
        if (job.cancel.cancelled) throw CancelledException()
        MediaWriter.writeFile(session.original, bmp, if (aiLater) 98 else 95)
        session.base.delete(); session.original.copyTo(session.base)
        val originalUri = if (keepOriginals && (tone || aiLater))
            MediaWriter.insertBytes(resolver, frame.jpeg, "NOVA_${name}_original.jpg", MediaWriter.ORIGINALS) else null
        session.originalUri = originalUri
        session.note = listOfNotNull(if (tone) plan.reason else if (job.signature != null) "Só assinatura (ajuste automático desligado)" else null,
            job.style.label()).joinToString(" · ")
        if (!tone) session.tone = 0f
        session.save()
        if (tone) Engine.toneInPlace(bmp, plan, 1f, false, job.cancel) { post(JobProgress(job.id, job.kind, 0.2f + 0.7f * it, "Ajustando", pending)) }
        Engine.styleInPlace(bmp, job.style, job.cancel)
        job.signature?.let { Signature.draw(bmp, it.withExif(session.exif)) }
        if (job.cancel.cancelled) throw CancelledException()
        // Só a IA vai mexer (sem tom, assinatura nem estilo): a foto da galeria fica intacta até ela terminar.
        if (tone || job.signature != null || !job.style.neutral)
            MediaWriter.write(resolver, target, bmp, session.exif, "Nova · ${session.note}")
        bmp.recycle()
        session.enhancedUri = target
        if (aiLater) session.meta.setProperty(AI_KEY, AI_PENDING)
        session.save()
        finish(job, session, target, originalUri, null)
        if (aiLater) synchronized(refines) { refines.remove(session.id); refines.addLast(session.id) }
    }

    /** Ruído (mediana de 9 blocos em resolução cheia) e bordas (5% maiores gradientes na foto reduzida 4×). */
    private fun aiStats(bmp: Bitmap): com.novacamera.s24.core.imaging.AiQuality.Stats {
        val n = 288
        val tile = IntArray(n * n)
        val sigmas = ArrayList<Float>()
        if (bmp.width >= n && bmp.height >= n) for (gy in 1..3) for (gx in 1..3) {
            val x = ((bmp.width - n) * gx / 4); val y = ((bmp.height - n) * gy / 4)
            bmp.getPixels(tile, 0, n, x, y, n, n)
            sigmas += com.novacamera.s24.core.imaging.TileNoise.sigma(tile, n, n)
        }
        val w = bmp.width - bmp.width % 4; val h = bmp.height - bmp.height % 4
        val small = IntArray((w / 4) * (h / 4))
        val rows = 256
        val buf = IntArray(bmp.width * rows)
        var y = 0
        while (y < h) {
            val r = minOf(rows, h - y)
            bmp.getPixels(buf, 0, bmp.width, 0, y, bmp.width, r)
            if (bmp.width != w) for (k in 0 until r) System.arraycopy(buf, k * bmp.width, buf, k * w, w)
            com.novacamera.s24.core.imaging.AiQuality.box4Luma(buf, w, r, small, y / 4)
            y += r
        }
        return com.novacamera.s24.core.imaging.AiQuality.Stats(
            com.novacamera.s24.core.imaging.AiQuality.median(sigmas.toFloatArray()),
            com.novacamera.s24.core.imaging.AiQuality.edgeEnergy(small, w / 4, h / 4))
    }

    private const val ATTENTION_MIN_ISO = 800

    private fun jpegIso(jpeg: ByteArray): Int? = runCatching {
        androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(jpeg))
            .getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }
    }.getOrNull()

    /** Ajuste e assinatura em faixas para 50/200 MP; grava novamente a única URI final. */
    private fun runHighResolutionAuto(context: Context,job: Job,session: Session) {
        val target=job.sourceUri ?: return finish(job,session,null,null,null)
        session.original.writeBytes(job.frames[0].jpeg)
        session.original.copyTo(session.base,overwrite=true)
        session.meta.setProperty("banded","true")
        val originalUri=if(keepOriginals) MediaWriter.insertBytes(context.contentResolver,job.frames[0].jpeg,
            "NOVA_${session.id}_original.jpg",MediaWriter.ORIGINALS) else null
        session.originalUri=originalUri
        job.imagingFacts.toMeta().forEach { (k,v)->session.meta.setProperty(k,v) }
        session.meta.setProperty("nai.enabled","true")
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(session.base.path,bounds)
        val sample=BitmapFactory.decodeFile(session.base.path,BitmapFactory.Options().apply {
            inSampleSize=maxOf(1,maxOf(bounds.outWidth,bounds.outHeight)/1024)
        }) ?: error("Prévia indisponível")
        val plan=Engine.planAdaptive(sample,job.zoom,job.imagingFacts);sample.recycle()
        session.tone=if(job.autoTone)1f else 0f
        if((plan.skip||!job.autoTone)&&job.signature==null&&job.style.neutral){session.dir.deleteRecursively();finish(job,session,null,null,null);return}
        session.style=job.style
        val output=File(session.dir,"final.tmp.jpg")
        val ok=JpegBands.process(session.base,output,plan,session.tone,job.signature?.withExif(session.exif),job.cancel,session.exif,job.style) {
            post(JobProgress(job.id,job.kind,0.15f+0.8f*it,"Ajustando",pending))
        }
        if(ok) context.contentResolver.openOutputStream(target,"wt")?.use { dst -> output.inputStream().use { it.copyTo(dst) } }
            ?: error("Sem acesso à fotografia final")
        output.delete()
        session.note=if(ok)"Alta resolução processada em faixas" else "Mantido JPEG do sensor: processamento em faixas indisponível ou além do orçamento"
        session.enhancedUri=target;session.save();finish(job,session,target,originalUri,null)
    }

    /** Se o usuário desativou "manter originais" e o processamento não terminou, o original vai para a galeria. */
    private fun saveOriginalFallback(context: Context, session: Session, name: String, job: Job): Uri? {
        if (job.kind == JobKind.AUTO || !session.original.exists()) return null
        return runCatching {
            val bmp = BitmapFactory.decodeFile(session.original.path) ?: return null
            MediaWriter.insert(context.contentResolver, bmp, "NOVA_$name.jpg", MediaWriter.GALLERY, session.exif, null).also { bmp.recycle() }
        }.getOrNull()
    }

    private val started = java.util.concurrent.ConcurrentHashMap<Long, Pair<Long, DeviceHeat?>>()

    private fun finish(job: Job, session: Session, enhanced: Uri?, original: Uri?, message: String?) {
        val r = resolver
        if (enhanced != null && r != null) job.liveClip?.let { LiveClips.bind(r, enhanced, it) }
        else job.liveClip?.complete(null, 0)
        started.remove(job.id)?.let { (t0, h0) ->
            com.novacamera.s24.core.CostLog.add(com.novacamera.s24.core.CostLog.Entry(
                job.kind.label, android.os.SystemClock.elapsedRealtime() - t0, h0, heat))
        }
        post(JobProgress(job.id, job.kind, 1f, "Pronto", pending - 1, finished = true,
            enhancedUri = enhanced, originalUri = original, sessionId = if (enhanced != null) session.id else null, message = message))
    }

    /**
     * Acabamento sobre a "base" (orientação real), no lugar. Também usado pela revisão para
     * reaplicar com outra intensidade — sempre a partir da base, nunca acumulando.
     */
    /** Preserva a escala pedida. Recolhe temporários uma vez, só sob pressão, fora da UI. */
    /**
     * A foto de saída é um Bitmap: os pixels ficam na memória nativa (Android 8+), não no heap Java. Até a
     * 0.30.7 a conta usava o heap Java, que logo depois do alinhamento ainda guarda ~200 MB temporários:
     * 320 MB "necessários" × 310 livres → a Alta resolução desistia e salvava só o original (rajadas #3/#4
     * do diagnóstico de 19/10). Agora: RAM livre do aparelho para a saída, com folga, e o sistema sem
     * pressão de memória.
     */
    private fun safeOutScale(context: Context, wanted: Float, w: Int, h: Int): Float {
        if (wanted <= 1f) return wanted
        val required = (w * wanted).toLong() * (h * wanted).toLong() * 4L
        val info = android.app.ActivityManager.MemoryInfo()
        context.getSystemService(android.app.ActivityManager::class.java)?.getMemoryInfo(info)
        if (!com.novacamera.s24.core.OutputMemory.fits(required, info.availMem, info.threshold, info.lowMemory))
            throw SelectedResolutionUnavailable()
        return wanted
    }
    private class SelectedResolutionUnavailable : RuntimeException()

    /**
     * Planos tonais de um acabamento da foto inteira. Sem [region], [render] os registra; com [region],
     * [render] aplica os registrados num recorte em resolução cheia (prévia da IA na região ampliada),
     * para o recorte sair com o mesmo tom da foto inteira e não com o tom calculado só dele.
     */
    class RenderPlans {
        var tone: com.novacamera.s24.core.imaging.Adaptive.Plan? = null
        var lowLight: com.novacamera.s24.core.imaging.Adaptive.Plan? = null
        var profile: com.novacamera.s24.core.imaging.Adaptive.Plan? = null
        var region: Engine.Region? = null
        val recorded get() = tone != null
        fun at(region: Engine.Region) = RenderPlans().also { it.tone = tone; it.lowLight = lowLight; it.profile = profile; it.region = region }
    }

    fun render(session: Session, bmp: Bitmap, tone: Float, blur: Float, cancel: CancelToken, trace: com.novacamera.s24.core.PhotoTrace? = null,
               plans: RenderPlans? = null, progress: (Float) -> Unit) {
        fun <T> step(name:String, block:()->T):T = if(trace==null)block() else trace.measure(name,block)
        val kind = session.kind
        val region = plans?.region
        val replay = plans != null && region != null && plans.recorded
        val plan = if (replay) requireNotNull(plans?.tone) else step("acabamento/plano tonal") { if (kind == JobKind.PHOTO || kind == JobKind.ZOOM || (kind == JobKind.AUTO && session.meta.getProperty("nai.enabled") == "true"))
            Engine.planAdaptive(bmp, session.zoom, com.novacamera.s24.core.imaging.ImagingFacts.fromMeta(
                session.meta.stringPropertyNames().associateWith { session.meta.getProperty(it) }))
        else Engine.planTone(bmp, kind.tone, session.zoom) }.also { plans?.tone = it }
        // Tele (3×/5×/10×): detalhe fino antes do tom (o tom não realça o que foi recuperado). Também no
        // HDR e no Retrato (foto real: cacto 5× pelo HDR saía sem o realce). Noturno não (ruído).
        if (kind == JobKind.PHOTO || kind == JobKind.ZOOM || kind == JobKind.HDR || kind == JobKind.PORTRAIT)
            step("acabamento/detalhes") { Engine.detailInPlace(bmp, session.zoom, cancel) }
        step("acabamento/tons") { Engine.toneInPlace(bmp, plan, tone, kind == JobKind.FOOD, cancel, if (replay) region else null) { progress(0.5f * it) } }
        // Pouca luz na foto comum (o Noturno tem o próprio clareamento; Food fica como está).
        if (kind == JobKind.PHOTO || kind == JobKind.ZOOM || kind == JobKind.HDR || kind == JobKind.PORTRAIT)
            step("acabamento/pouca luz") {
                val lift = if (replay) plans?.lowLight else Engine.lowLightPlan(bmp, session.meta.getProperty("nai.iso")?.toIntOrNull()).also { plans?.lowLight = it }
                if (lift != null) Engine.toneInPlace(bmp, lift, 1f, false, cancel, if (replay) region else null) {}
            }
        // Atenção ao assunto (FOTO no escuro): sombra sobe só onde há informação ou toque; mapa gravado na sessão.
        if (kind == JobKind.PHOTO || kind == JobKind.ZOOM || kind == JobKind.HDR) session.loadAttention()?.let { maps ->
            step("acabamento/assunto") { Engine.attentionInPlace(bmp, maps, cancel, if (replay) region else null) }
        }
        // ISO alto: sombras sem manchas roxas/azuis de ruído de cor — antes do desfoque do retrato,
        // que transformaria essas manchas em "aquarela". Food fica com a cor que tem.
        if (kind != JobKind.FOOD) step("acabamento/crominância") { Engine.shadowChromaInPlace(bmp, session.meta.getProperty("nai.iso")?.toIntOrNull(), cancel) }
        // Perfil "Contraste": não no Noturno (tom do Noturno mantido por decisão do usuário) nem no Food.
        if (session.meta.getProperty("profile") == "contrast" && kind != JobKind.NIGHT && kind != JobKind.FOOD)
            step("acabamento/perfil") {
                val profile = (if (replay) plans?.profile else null) ?: Engine.profilePlan(bmp).also { plans?.profile = it }
                Engine.toneInPlace(bmp, profile, 1f, false, cancel, if (replay) region else null) {}
            }
        if (kind == JobKind.PORTRAIT && session.hasPerson && !replay) {
            val matte = session.loadMatte()
            if (matte != null) step("acabamento/desfoque") { Engine.portraitInPlace(bmp, matte, blur, cancel, session.loadBlurMap()) { progress(0.5f + 0.5f * it) } }
            if (matte != null && session.meta.getProperty("portrait.light") == "dark")
                step("acabamento/fundo escuro") { Engine.darkBackgroundInPlace(bmp, matte, cancel) }
        }
        // Estilo ao vivo por último: mesma conta e mesma ordem da prévia (sobre a imagem pronta).
        step("acabamento/estilo") { Engine.styleInPlace(bmp, session.style, cancel) }
        progress(1f)
    }

    /** Lê a base/original de uma sessão em tamanho reduzido para a tela de revisão. */
    fun decodeForScreen(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp=BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample; inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val rotation=runCatching { androidx.exifinterface.media.ExifInterface(file).rotationDegrees }.getOrDefault(0)
        return Engine.orient(bmp,rotation,false).also { if(it!==bmp)bmp.recycle() }
    }

    /** Regrava a versão aprimorada em resolução cheia com novas intensidades (na fila, em segundo plano). */
    fun reRender(context: Context, session: Session, tone: Float, blur: Float, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val ok = runCatching {
                if(session.meta.getProperty("banded")=="true") {
                    val sample=decodeForScreen(session.base,1024) ?: error("Prévia ausente")
                    // Plano em coordenadas do JPEG, para preservar a orientação EXIF na saída.
                    val exif=androidx.exifinterface.media.ExifInterface(session.base)
                    val unrotated=Engine.orient(sample,(360-exif.rotationDegrees)%360,false)
                    if(unrotated!==sample)sample.recycle()
                    val plan=Engine.planAdaptive(unrotated,session.zoom,com.novacamera.s24.core.imaging.ImagingFacts.fromMeta(
                        session.meta.stringPropertyNames().associateWith { session.meta.getProperty(it) }))
                    unrotated.recycle()
                    val output=File(session.dir,"reedit.tmp.jpg")
                    check(JpegBands.process(session.base,output,plan,tone,session.signature?.withExif(session.exif),CancelToken(),session.exif,session.style){})
                    synchronized(LiveClips.lock) {
                        app.contentResolver.openOutputStream(session.enhancedUri ?: error("Sem fotografia"),"wt")?.use { dst -> output.inputStream().use { it.copyTo(dst) } } ?: error("Sem saída")
                    }
                    session.enhancedUri?.let { LiveClips.reattach(app.contentResolver, it) }
                    output.delete();session.tone=tone;session.save()
                    return@runCatching
                }
                val bmp = BitmapFactory.decodeFile(session.base.path, BitmapFactory.Options().apply { inMutable = true })
                    ?: error("base ausente")
                session.tone = tone; session.blur = blur
                render(session, bmp, tone, blur, CancelToken()) {}
                session.signature?.let { Signature.draw(bmp, it.withExif(session.exif)) }
                UltraHdr.attach(session, bmp)
                val uri = session.enhancedUri ?: error("sem versão aprimorada")
                MediaWriter.write(app.contentResolver, uri, bmp, session.exif, "Nova ${session.kind.label}")
                bmp.recycle()
                session.save()
            }.onFailure { Log.e(TAG, "reedição falhou", it) }.isSuccess
            main.post { done(ok) }
        }
    }

    private fun prune(context: Context) {
        val dirs = sessionsDir(context).listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name.toLongOrNull() ?: 0L } ?: return
        // Sessões esperando a IA ficam além das quatro de revisão, com teto de espaço.
        val waiting = synchronized(refines) { refines.toSet() }
        val keep = dirs.take(KEEP_SESSIONS) + dirs.drop(KEEP_SESSIONS).filter { it.name in waiting }.take(KEEP_SESSIONS)
        dirs.filter { it !in keep }.forEach { dir ->
            dir.deleteRecursively()
            synchronized(refines) { refines.remove(dir.name) }
        }
    }
}

