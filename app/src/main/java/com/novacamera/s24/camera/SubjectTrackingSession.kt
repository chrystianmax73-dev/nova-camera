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
import com.novacamera.s24.core.SubjectTracker
import com.novacamera.s24.core.TrackingBudget
import com.novacamera.s24.core.TrackingLog
import java.util.concurrent.Executors

/** Uma leitura em voo, sem novo stream CameraX/EGL. Snapshot público já recortado/espelhado. */
class SubjectTrackingSession(
    private val preview: PreviewView,
    private val valid: () -> Boolean,
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
        selection = (x / preview.width.coerceAtLeast(1)).coerceIn(0f, 1f) to
            (y / preview.height.coerceAtLeast(1)).coerceIn(0f, 1f)
        lastFrameAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick); handler.post(tick)
    }

    fun stop(reason: String = "") {
        val notify = active
        active = false; generation++; selection = null
        handler.removeCallbacks(tick)
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
                    if (pick != null) { tracker = SubjectTracker(); result = tracker.select(gray, w, h, pick.first, pick.second, frameAt) }
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

    override fun close() {
        if (closed) return
        stop(); closed = true; reader.close()
        if (lazyWorker.isInitialized()) {
            lazyWorker.value.execute { small?.recycle(); small = null; pixels = IntArray(0); gray = ByteArray(0) }
            lazyWorker.value.shutdown()
        }
    }
}
