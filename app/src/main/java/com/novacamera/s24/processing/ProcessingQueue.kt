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
    private val listeners = mutableListOf<(JobProgress) -> Unit>()
    private val queued = ArrayList<Job>()
    @Volatile private var current: Job? = null
    @Volatile var heat: DeviceHeat? = null
    @Volatile var keepOriginals = false
    /** Redução de ruído por IA (NAFNet/ncnn), experimental. */
    @Volatile var aiDenoise = false

    val pending: Int get() = synchronized(queued) { queued.size } + (if (current != null) 1 else 0)
    fun canAccept() = pending < MAX_PENDING

    fun addListener(l: (JobProgress) -> Unit) { listeners += l }
    fun removeListener(l: (JobProgress) -> Unit) { listeners -= l }

    private fun post(p: JobProgress) = main.post { listeners.toList().forEach { it(p) } }

    fun submit(context: Context, job: Job) {
        val app = context.applicationContext
        synchronized(queued) { queued += job }
        post(JobProgress(job.id, job.kind, 0f, "Na fila", pending))
        executor.execute {
            synchronized(queued) { queued -= job }
            current = job
            try { run(app, job) } finally { current = null }
        }
    }

    /** Descarta só o cache do modelo, fora da UI e depois da tarefa atual. */
    fun trimModelMemory() { executor.execute { NcnnDenoiser.release() } }

    /** Cancela a tarefa em andamento e as que estão na fila (os originais já salvos ficam). */
    fun cancelAll() {
        current?.cancel?.cancelled = true
        synchronized(queued) { queued.forEach { it.cancel.cancelled = true } }
    }

    fun sessionsDir(context: Context) = File(context.filesDir, "review")

    fun session(context: Context, id: String): Session? =
        File(sessionsDir(context), id).takeIf { it.isDirectory }?.let(::Session)

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    private fun run(context: Context, job: Job) {
        val resolver = context.contentResolver
        val name = stamp()
        val session = Session(File(sessionsDir(context), "${job.id}"))
        session.dir.mkdirs()
        session.kind = job.kind
        // Noturno: referência de melhor nitidez medida, apenas com exposição/geometria iguais.
        // HDR conserva a exposição normal; não compara nitidez de exposições diferentes.
        val orderedFrames = if (job.kind == JobKind.NIGHT && job.frames.size > 1) runCatching {
            val first = job.frames[0]
            if (job.frames.any { it.evIndex != first.evIndex || it.rotationDegrees != first.rotationDegrees || it.crop != first.crop }) job.frames
            else {
                val scores = job.frames.map { frame ->
                    val src = JpegSource(frame)
                    try {
                        com.novacamera.s24.core.imaging.FrameQuality.score(src.luma(kotlin.math.max(1, kotlin.math.max(src.width, src.height) / 768)))
                    } finally { src.close() }
                }
                val index = com.novacamera.s24.core.imaging.FrameQuality.best(scores)
                session.meta.setProperty("referenceIndex", index.toString())
                listOf(job.frames[index]) + job.frames.filterIndexed { i, _ -> i != index }
            }
        }.getOrDefault(job.frames) else job.frames
        val frame0 = orderedFrames[0]
        session.exif = MediaWriter.exifOf(frame0.jpeg)
        var originalUri: Uri? = null
        fun progress(f: Float, stage: String) = post(JobProgress(job.id, job.kind, f.coerceIn(0f, 1f), stage, pending))

        session.zoom = job.zoom
        session.signature = job.signature
        try {
            if (job.kind == JobKind.AUTO) { runAuto(context, job, session, name); return }
            // 1. Original (quadro de referência, sem processamento) — salvo primeiro, sempre.
            progress(0.02f, "Salvando original")
            val orig = Engine.orient(JpegSource(frame0).let { s -> s.decodeFull().also { s.close() } }, frame0.rotationDegrees, job.mirror)
            MediaWriter.writeFile(session.original, orig, 95)
            // Original byte a byte (o JPEG da câmera, sem recompressão) em "Originais".
            if (keepOriginals) originalUri = MediaWriter.insertBytes(resolver, frame0.jpeg, "NOVA_${name}_original.jpg", MediaWriter.ORIGINALS)
            session.originalUri = originalUri
            val h = heat
            val critical = h != null && h.thermalStatus >= ThermalPolicy.CRITICAL
            if (critical) {
                if (originalUri == null && job.kind != JobKind.AUTO)
                    originalUri = MediaWriter.insert(resolver, orig, "NOVA_$name.jpg", MediaWriter.GALLERY, session.exif, null)
                orig.recycle()
                finish(job, session, null, originalUri, "Aparelho muito quente: salvo só o original, sem processamento")
                return
            }
            // Com aquecimento severo, a fusão multiquadro é pulada (um quadro só, processamento leve).
            val frames = if (h != null && h.thermalStatus >= ThermalPolicy.SEVERE) orderedFrames.take(1) else orderedFrames

            // 2. Fusão (orientação do sensor), lendo os quadros por faixas.
            var base: Bitmap
            var note = ""
            if (frames.size > 1 && (job.kind == JobKind.NIGHT || job.kind == JobKind.HDR)) {
                orig.recycle()
                val sources = frames.map(::JpegSource)
                try {
                    progress(0.05f, "Alinhando ${frames.size} quadros")
                    val a = Engine.align(sources, job.cancel) { progress(0.05f + 0.15f * it, "Alinhando ${frames.size} quadros") }
                    base = Bitmap.createBitmap(sources[0].width, sources[0].height, Bitmap.Config.ARGB_8888)
                    if (job.kind == JobKind.NIGHT) {
                        val rejected = Engine.mergeInto(sources, base, a, job.cancel) { progress(0.2f + 0.45f * it, "Reduzindo ruído") }
                        val n = a.usedFrames
                        note = if (n < 2) "Movimento ou alinhamento incerto: usado o quadro de referência, sem fusão"
                               else "$n de ${frames.size} quadros fundidos" + if (rejected > 0.15f) " · movimento detectado em parte da cena" else ""
                    } else {
                        val n = Engine.fuseInto(sources, base, a, job.cancel) { progress(0.2f + 0.45f * it, "Combinando exposições") }
                        note = if (n < 2) "Movimento ou alinhamento incerto: usado o quadro de referência, sem fusão"
                               else "$n de ${frames.size} exposições combinadas (só onde coincidem)"
                    }
                    session.meta.setProperty("unsafe", a.unsafe.joinToString { "%.2f".format(java.util.Locale.US, it) })
                } finally { sources.forEach { it.close() } }
                base = Engine.orient(base, frame0.rotationDegrees, job.mirror)
            } else {
                base = orig
                if (job.kind == JobKind.NIGHT || job.kind == JobKind.HDR)
                    note = job.captureNote ?: "Um quadro (multiquadro indisponível nesta captura)"
            }
            if (job.cancel.cancelled) throw CancelledException()
            // Redução de ruído por IA (experimental, desligada por padrão): Noturno e Retrato.
            if (aiDenoise && com.novacamera.s24.core.DenoisePolicy.allowed(heat) &&
                (job.kind == JobKind.NIGHT || job.kind == JobKind.PORTRAIT) && NcnnDenoiser.available(context)) {
                val t0 = android.os.SystemClock.elapsedRealtime()
                val ok = runCatching {
                    NcnnDenoiser.denoiseInPlace(context, base, 0.75f, job.cancel) { progress(0.5f + 0.15f * it, "Reduzindo ruído (IA)") }
                }.onFailure { if (it is CancelledException) throw it; Log.w(TAG, "NAFNet falhou", it) }.getOrDefault(false)
                if (ok) note = (if (note.isEmpty()) "" else "$note · ") +
                    "ruído reduzido por IA em ${(android.os.SystemClock.elapsedRealtime() - t0) / 100 / 10.0} s"
            }
            MediaWriter.writeFile(session.base, base, 95)

            // 3. Retrato: segmentação + máscara refinada (guardada para reedição).
            if (job.kind == JobKind.PORTRAIT) {
                progress(0.68f, "Separando pessoa e fundo")
                val seg = runCatching { PersonSegmenter.segment(context, base) }.onFailure { Log.w(TAG, "segmentação falhou", it) }.getOrNull()
                if (seg != null && seg.coverage >= 0.03f) {
                    session.saveMatte(Engine.portraitMatte(base, seg.person, seg.protect))
                    session.hasPerson = true
                } else {
                    note = if (seg == null) "Segmentação indisponível: foto salva sem desfoque"
                           else "Nenhuma pessoa detectada: foto salva sem desfoque (o Retrato separa pessoas)"
                }
            }
            session.note = note
            session.save()

            // 4. Acabamento (tom, desfoque) e gravação da versão aprimorada.
            render(session, base, session.tone, session.blur, job.cancel) { progress(0.7f + 0.25f * it, "Ajustando tons") }
            job.signature?.let { Signature.draw(base, it) }
            progress(0.96f, "Salvando")
            val label = if (job.kind == JobKind.AUTO) "aprimorada" else job.kind.name.lowercase(Locale.US)
            val enhanced = MediaWriter.insert(resolver, base, "NOVA_${name}_$label.jpg", MediaWriter.GALLERY, session.exif,
                "Nova ${job.kind.label}" + if (note.isNotEmpty()) " · $note" else "")
            base.recycle()
            session.enhancedUri = enhanced
            session.save()
            finish(job, session, enhanced, originalUri, note.ifEmpty { null })
        } catch (e: CancelledException) {
            finish(job, session, null, originalUri ?: saveOriginalFallback(context, session, name, job), "Cancelado · original salvo")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "sem memória", e)
            finish(job, session, null, originalUri ?: saveOriginalFallback(context, session, name, job), "Memória insuficiente: salvo só o original")
        } catch (e: Exception) {
            Log.e(TAG, "falha no processamento", e)
            finish(job, session, null, originalUri ?: saveOriginalFallback(context, session, name, job), "Falha no processamento: salvo só o original")
        } finally {
            prune(context)
            // Reutilização curta entre fotos; descarte fora da UI e sem liberar durante inferência.
            if ((heat?.thermalStatus ?: 0) >= ThermalPolicy.MODERATE || !aiDenoise) NcnnDenoiser.release()
            else main.postDelayed({ executor.execute {
                if (pending == 0) NcnnDenoiser.releaseIfIdle(android.os.SystemClock.elapsedRealtime())
            } }, com.novacamera.s24.core.DenoisePolicy.IDLE_MS)
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
        val bmp = Engine.orient(JpegSource(frame).let { s -> s.decodeFull().also { s.close() } }, frame.rotationDegrees, false)
        val exif = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(frame.jpeg))
        val facts = job.imagingFacts.copy(
            iso = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 },
            exposureNs = exif.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                .takeIf { it.isFinite() && it > 0 && it < 60 }?.let { (it * 1_000_000_000).toLong() })
        facts.toMeta().forEach { (k, v) -> session.meta.setProperty(k, v) }
        session.meta.setProperty("nai.enabled", "true")
        val plan = Engine.planAdaptive(bmp, job.zoom, facts)
        val tone = job.autoTone && !plan.skip
        if (!tone && job.signature == null) {
            bmp.recycle()
            session.dir.deleteRecursively()
            return finish(job, session, null, null, null)
        }
        if (job.cancel.cancelled) throw CancelledException()
        MediaWriter.writeFile(session.original, bmp, 95)
        session.base.delete(); session.original.copyTo(session.base)
        val originalUri = if (keepOriginals && tone)
            MediaWriter.insertBytes(resolver, frame.jpeg, "NOVA_${name}_original.jpg", MediaWriter.ORIGINALS) else null
        session.originalUri = originalUri
        session.note = if (tone) plan.reason else "Só assinatura (ajuste automático desligado)"
        if (!tone) session.tone = 0f
        session.save()
        if (tone) Engine.toneInPlace(bmp, plan, 1f, false, job.cancel) { post(JobProgress(job.id, job.kind, 0.2f + 0.7f * it, "Ajustando", pending)) }
        job.signature?.let { Signature.draw(bmp, it) }
        if (job.cancel.cancelled) throw CancelledException()
        MediaWriter.write(resolver, target, bmp, session.exif, "Nova · ${session.note}")
        bmp.recycle()
        session.enhancedUri = target
        session.save()
        finish(job, session, target, originalUri, null)
    }

    /** Se o usuário desativou "manter originais" e o processamento não terminou, o original vai para a galeria. */
    private fun saveOriginalFallback(context: Context, session: Session, name: String, job: Job): Uri? {
        if (job.kind == JobKind.AUTO || !session.original.exists()) return null
        return runCatching {
            val bmp = BitmapFactory.decodeFile(session.original.path) ?: return null
            MediaWriter.insert(context.contentResolver, bmp, "NOVA_$name.jpg", MediaWriter.GALLERY, session.exif, null).also { bmp.recycle() }
        }.getOrNull()
    }

    private fun finish(job: Job, session: Session, enhanced: Uri?, original: Uri?, message: String?) {
        post(JobProgress(job.id, job.kind, 1f, "Pronto", pending - 1, finished = true,
            enhancedUri = enhanced, originalUri = original, sessionId = if (enhanced != null) session.id else null, message = message))
    }

    /**
     * Acabamento sobre a "base" (orientação real), no lugar. Também usado pela revisão para
     * reaplicar com outra intensidade — sempre a partir da base, nunca acumulando.
     */
    fun render(session: Session, bmp: Bitmap, tone: Float, blur: Float, cancel: CancelToken, progress: (Float) -> Unit) {
        val kind = session.kind
        val plan = if (kind == JobKind.AUTO && session.meta.getProperty("nai.enabled") == "true")
            Engine.planAdaptive(bmp, session.zoom, com.novacamera.s24.core.imaging.ImagingFacts.fromMeta(
                session.meta.stringPropertyNames().associateWith { session.meta.getProperty(it) }))
        else Engine.planTone(bmp, kind.tone, session.zoom)
        Engine.toneInPlace(bmp, plan, tone, kind == JobKind.FOOD, cancel) { progress(0.5f * it) }
        if (kind == JobKind.PORTRAIT && session.hasPerson) {
            val matte = session.loadMatte()
            if (matte != null) Engine.portraitInPlace(bmp, matte, blur, cancel) { progress(0.5f + 0.5f * it) }
        }
        progress(1f)
    }

    /** Lê a base/original de uma sessão em tamanho reduzido para a tela de revisão. */
    fun decodeForScreen(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample; inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    /** Regrava a versão aprimorada em resolução cheia com novas intensidades (na fila, em segundo plano). */
    fun reRender(context: Context, session: Session, tone: Float, blur: Float, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val ok = runCatching {
                val bmp = BitmapFactory.decodeFile(session.base.path, BitmapFactory.Options().apply { inMutable = true })
                    ?: error("base ausente")
                session.tone = tone; session.blur = blur
                render(session, bmp, tone, blur, CancelToken()) {}
                session.signature?.let { Signature.draw(bmp, it) }
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
        dirs.drop(KEEP_SESSIONS).forEach { it.deleteRecursively() }
    }
}

