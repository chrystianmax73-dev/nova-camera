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
    ENHANCE("Aprimorar", ToneIntent.LIGHT),
}

class Job(
    val kind: JobKind,
    /** Índice 0 = referência (exposição normal). */
    val frames: List<CapturedFrame>,
    val mirror: Boolean,
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
        get() = JobKind.valueOf(meta.getProperty("kind", JobKind.ENHANCE.name))
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
    @Volatile var keepOriginals = true

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
        val frame0 = job.frames[0]
        session.exif = MediaWriter.exifOf(frame0.jpeg)
        var originalUri: Uri? = null
        fun progress(f: Float, stage: String) = post(JobProgress(job.id, job.kind, f.coerceIn(0f, 1f), stage, pending))

        try {
            // 1. Original (quadro de referência, sem processamento) — salvo primeiro, sempre.
            progress(0.02f, "Salvando original")
            val orig = Engine.orient(JpegSource(frame0).let { s -> s.decodeFull().also { s.close() } }, frame0.rotationDegrees, job.mirror)
            MediaWriter.writeFile(session.original, orig, 95)
            if (keepOriginals || job.kind == JobKind.ENHANCE) {
                // No modo Aprimorar o original já está na galeria (é a foto comum); nos demais ele vai para "Originais".
                if (job.kind != JobKind.ENHANCE)
                    originalUri = MediaWriter.insert(resolver, orig, "NOVA_${name}_original.jpg", MediaWriter.ORIGINALS,
                        session.exif, "Original (sem processamento Nova)")
            }
            session.originalUri = originalUri
            val h = heat
            val critical = h != null && h.thermalStatus >= ThermalPolicy.CRITICAL
            if (critical) {
                if (originalUri == null && job.kind != JobKind.ENHANCE)
                    originalUri = MediaWriter.insert(resolver, orig, "NOVA_$name.jpg", MediaWriter.GALLERY, session.exif, null)
                orig.recycle()
                finish(job, session, null, originalUri, "Aparelho muito quente: salvo só o original, sem processamento")
                return
            }
            // Com aquecimento severo, a fusão multiquadro é pulada (um quadro só, processamento leve).
            val frames = if (h != null && h.thermalStatus >= ThermalPolicy.SEVERE) job.frames.take(1) else job.frames

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
                        note = "${frames.size} quadros fundidos" + if (rejected > 0.15f) " · movimento detectado em parte da cena" else ""
                    } else {
                        Engine.fuseInto(sources, base, a, job.cancel) { progress(0.2f + 0.45f * it, "Combinando exposições") }
                        note = "${frames.size} exposições combinadas"
                    }
                } finally { sources.forEach { it.close() } }
                base = Engine.orient(base, frame0.rotationDegrees, job.mirror)
            } else {
                base = orig
                if (job.kind == JobKind.NIGHT || job.kind == JobKind.HDR) note = "Um quadro (multiquadro indisponível nesta captura)"
            }
            if (job.cancel.cancelled) throw CancelledException()
            MediaWriter.writeFile(session.base, base, 95)

            // 3. Retrato: segmentação + máscara refinada (guardada para reedição).
            if (job.kind == JobKind.PORTRAIT) {
                progress(0.68f, "Separando pessoa e fundo")
                val seg = runCatching { PersonSegmenter.segment(base) }.onFailure { Log.w(TAG, "segmentação falhou", it) }.getOrNull()
                if (seg != null && seg.coverage >= 0.03f) {
                    session.saveMatte(Engine.refineMatte(base, seg.mask))
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
            progress(0.96f, "Salvando")
            val label = if (job.kind == JobKind.ENHANCE) "aprimorada" else job.kind.name.lowercase(Locale.US)
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
        }
    }

    /** Se o usuário desativou "manter originais" e o processamento não terminou, o original vai para a galeria. */
    private fun saveOriginalFallback(context: Context, session: Session, name: String, job: Job): Uri? {
        if (job.kind == JobKind.ENHANCE || !session.original.exists()) return null
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
        val (params, baseLayer) = Engine.planTone(bmp, kind.tone)
        Engine.toneInPlace(bmp, params, baseLayer, tone, kind == JobKind.FOOD, cancel) { progress(0.5f * it) }
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
