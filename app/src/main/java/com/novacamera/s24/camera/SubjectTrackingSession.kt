package com.novacamera.s24.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.view.PreviewView
import com.novacamera.s24.core.FaceLock
import com.novacamera.s24.core.SubjectTracker
import com.novacamera.s24.core.TrackingBudget
import com.novacamera.s24.core.TrackingLog
import java.util.concurrent.Executors

/**
 * Uma leitura em voo, sem novo stream CameraX/EGL. Snapshot público já recortado/espelhado.
 * Toque num rosto que o sensor detectou: segue o rosto do próprio sensor (sem ler a prévia); rosto sumido
 * por [FaceLock.HANDOFF_MS] (virou de costas, mão na frente) passa ao rastreio por aparência na última
 * posição, e volta ao rosto quando ele reaparece ali. Objetos e pessoas sem rosto: só aparência.
 */
class SubjectTrackingSession(
    private val preview: PreviewView,
    private val valid: () -> Boolean,
    /** Rostos do sensor em coordenadas da prévia (vazio quando velhos ou indisponíveis). */
    private val faces: () -> List<FaceLock.Box>,
    private val onResult: (SubjectTracker.Result) -> Unit,
    private val onStopped: (String) -> Unit,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val lazyWorker = lazy { Executors.newSingleThreadExecutor { job ->
        Thread(job, "nova-subject-tracker").apply { priority = Thread.NORM_PRIORITY - 1 }
    } }
    private var generation = 0L
    private var busy = false
    private var closed = false
    var active = false; private set
    var thermal = 0
    var powerSave = false
    private var selection: Pair<Float, Float>? = null
    private var selectionSide = 0f
    private var face: FaceLock.Box? = null
    private var faceSeenAt = 0L
    private var lastFrameAt = 0L
    private val reader = PreviewFrameReader(preview)
    // Buffers e tracker usados apenas pela fila única de análise.
    private var tracker = SubjectTracker()
    private var small: Bitmap? = null
    private var pixels = IntArray(0)
    private var gray = ByteArray(0)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = Rect()

    fun select(x: Float, y: Float) {
        if (closed) return
        generation++; active = true
        handler.removeCallbacks(tick); handler.removeCallbacks(faceTick)
        val tapped = FaceLock.pick(faces(), x, y)
        if (tapped != null) {
            face = tapped; faceSeenAt = SystemClock.elapsedRealtime(); selection = null
            handler.post(faceTick); return
        }
        face = null
        appearance(x, y, 0f)
    }

    private fun appearance(x: Float, y: Float, side: Float) {
        selection = (x / preview.width.coerceAtLeast(1)).coerceIn(0f, 1f) to
            (y / preview.height.coerceAtLeast(1)).coerceIn(0f, 1f)
        selectionSide = side
        lastFrameAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick); handler.post(tick)
    }

    private fun faceResult(f: FaceLock.Box, moving: Boolean) {
        val w = preview.width.coerceAtLeast(1).toFloat(); val h = preview.height.coerceAtLeast(1).toFloat()
        onResult(SubjectTracker.Result(SubjectTracker.Phase.TRACKING, (f.cx / w).coerceIn(0f, 1f),
            (f.cy / h).coerceIn(0f, 1f), f.size / minOf(w, h), 1f, moving, FACE))
    }

    /** Rosto do sensor: custo de CPU é só comparar caixas; a cadência do AF continua limitada pela câmera. */
    private val faceTick: Runnable = object : Runnable {
        override fun run() {
            val last = face ?: return
            if (!active || closed) return
            if (!valid()) return stop("Enquadramento ou controle de foco mudou")
            if (TrackingBudget.suspended(thermal)) return stop("Rastreamento pausado pelo estado térmico")
            val now = SystemClock.elapsedRealtime()
            val next = FaceLock.follow(faces(), last)
            if (next != null) {
                val moved = kotlin.math.hypot(next.cx - last.cx, next.cy - last.cy) > last.size * 0.04f
                face = next; faceSeenAt = now
                faceResult(next, moved)
            } else if (now - faceSeenAt > FaceLock.HANDOFF_MS) {
                face = null
                TrackingLog.stopped("rosto sumiu; seguindo pela aparência")
                return appearance(last.cx, last.cy, last.size / minOf(preview.width, preview.height).coerceAtLeast(1))
            }
            if (active) handler.postDelayed(this, if (powerSave || thermal >= 2) 250L else FACE_INTERVAL_MS)
        }
    }

    /** No rastreio por aparência, o rosto que reaparece no alvo devolve a trava ao sensor. */
    private fun regainFace(r: SubjectTracker.Result): Boolean {
        val w = preview.width.toFloat(); val h = preview.height.toFloat()
        if (w <= 0f || h <= 0f) return false
        val s = r.side * minOf(w, h)
        val at = FaceLock.Box(r.x * w - s / 2, r.y * h - s / 2, r.x * w + s / 2, r.y * h + s / 2)
        val found = FaceLock.follow(faces(), at) ?: return false
        face = found; faceSeenAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick); handler.post(faceTick)
        return true
    }

    fun stop(reason: String = "") {
        val notify = active
        active = false; generation++; selection = null; face = null
        handler.removeCallbacks(tick); handler.removeCallbacks(faceTick)
        if (notify) { TrackingLog.stopped(reason.ifBlank { "automático" }); onStopped(reason) }
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!active || closed) return
            if (!valid()) return stop("Enquadramento ou controle de foco mudou")
            if (TrackingBudget.suspended(thermal)) return stop("Rastreamento pausado pelo estado térmico")
            if (!preview.isAttachedToWindow || preview.previewStreamState.value != PreviewView.StreamState.STREAMING) {
                if (SystemClock.elapsedRealtime() - lastFrameAt > 1200) return stop("Prévia interrompida")
                handler.postDelayed(this, 200); return
            }
            if (busy) { handler.postDelayed(this, 100); return }
            busy = true
            val token = generation
            val deadline = Runnable { if (active && generation == token && busy) stop("Leitura da prévia sem resposta") }
            handler.postDelayed(deadline,1500)
            val accepted = reader.read { source, copyMs ->
                handler.removeCallbacks(deadline)
                if (closed || !active || token != generation) {
                    busy = false; source?.recycle(); return@read
                }
                if (source == null) {
                    busy = false
                    if (SystemClock.elapsedRealtime() - lastFrameAt > 1200) stop("Leitura da prévia indisponível")
                    else handler.postDelayed(tick,200)
                    return@read
                }
                val pick = selection; selection = null
                val pickSide = selectionSide
                val frameAt = SystemClock.elapsedRealtime() - copyMs.toLong()
                lastFrameAt = SystemClock.elapsedRealtime()
            lazyWorker.value.execute {
                val begin = SystemClock.elapsedRealtimeNanos()
                val cpuBegin = Debug.threadCpuTimeNanos()
                var result: SubjectTracker.Result? = null
                var failure: String? = null
                var w = 0; var h = 0
                try {
                    val scale = minOf(1f, 320f / maxOf(source.width, source.height))
                    w = (source.width * scale).toInt().coerceAtLeast(48)
                    h = (source.height * scale).toInt().coerceAtLeast(48)
                    if (small?.width != w || small?.height != h) {
                        small?.recycle(); small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        pixels = IntArray(w * h); gray = ByteArray(w * h)
                    }
                    rect.set(0, 0, w, h)
                    Canvas(small!!).drawBitmap(source, null, rect, paint)
                    small!!.getPixels(pixels, 0, w, 0, 0, w, h)
                    for (i in gray.indices) {
                        val p = pixels[i]
                        gray[i] = (((p shr 16 and 255) * 77 + (p shr 8 and 255) * 150 + (p and 255) * 29) shr 8).toByte()
                    }
                    if (pick != null) { tracker = SubjectTracker(); result = tracker.select(gray, w, h, pick.first, pick.second, frameAt, pickSide) }
                    else result = tracker.update(gray, w, h, frameAt)
                } catch (_: RuntimeException) { failure = "Análise do rastreamento indisponível" }
                catch (_: OutOfMemoryError) { failure = "Memória insuficiente para rastrear" }
                finally { source.recycle() }
                val wallMs = (SystemClock.elapsedRealtimeNanos() - begin) / 1_000_000.0
                val cpuMs = (Debug.threadCpuTimeNanos() - cpuBegin).coerceAtLeast(0) / 1_000_000.0
                handler.post {
                    busy = false
                    if (closed || !active || token != generation) return@post
                    if (!valid()) { stop("Enquadramento ou controle de foco mudou"); return@post }
                    val r = result
                    if (failure != null || r == null) { stop(failure ?: "Análise indisponível"); return@post }
                    TrackingLog.frame(copyMs, cpuMs, wallMs, w, h, r)
                    if (SystemClock.elapsedRealtime() - frameAt > 500) { stop("Análise atrasada; automático"); return@post }
                    if (r.phase == SubjectTracker.Phase.LOST) { stop(r.reason); return@post }
                    if (r.phase == SubjectTracker.Phase.TRACKING && regainFace(r)) return@post
                    onResult(r)
                    if (active && token == generation) {
                        handler.removeCallbacks(tick)
                        handler.postDelayed(tick, TrackingBudget.intervalMs(r.moving, thermal, powerSave, copyMs))
                    }
                }
            }
            }
            if (!accepted) {
                handler.removeCallbacks(deadline)
                busy = false
                if (SystemClock.elapsedRealtime() - lastFrameAt > 1200) stop("Leitura da prévia indisponível")
                else handler.postDelayed(tick,200)
            }
        }
    }

    companion object {
        const val FACE = "rosto"
        private const val FACE_INTERVAL_MS = 100L
    }

    override fun close() {
        if (closed) return
        stop(); closed = true; reader.close()
        if (lazyWorker.isInitialized()) {
            lazyWorker.value.execute { small?.recycle(); small = null; pixels = IntArray(0); gray = ByteArray(0) }
            lazyWorker.value.shutdown()
        }
    }
}
