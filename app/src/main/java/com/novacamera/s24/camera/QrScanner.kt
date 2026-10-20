package com.novacamera.s24.camera

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.view.PreviewView
import com.novacamera.s24.core.QrContent
import com.novacamera.s24.core.QrDedupe
import java.util.concurrent.Executors

/**
 * QR e códigos de barras sempre ligados na FOTO (pedido do dono, 20/10), como a câmera da Samsung. Lê a
 * própria prévia (snapshot reduzido a 960 px, sem stream de câmera extra) ~2,5×/s; com calor ou economia,
 * 1×/s; pausa enquanto a foto é capturada. zxing-cpp (Apache-2.0). Nunca abre nada sozinho.
 */
class QrScanner(
    private val preview: PreviewView,
    private val enabled: () -> Boolean,
    private val slow: () -> Boolean,
    private val onFound: (QrContent) -> Unit,
    private val onGone: () -> Unit,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val reader = PreviewFrameReader(preview, 960f)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "nova-qr").apply { priority = Thread.MIN_PRIORITY } }
    private val zxing by lazy { zxingcpp.BarcodeReader() }
    private val dedupe = QrDedupe()
    private var busy = false
    private var running = false
    private var closed = false
    @Volatile var lastReadMs = 0.0
        private set

    fun start() { if (closed || running) return; running = true; handler.post(tick) }
    fun stop() { running = false; handler.removeCallbacks(tick); dedupe.reset(); onGone() }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!running || closed) return
            val next = if (slow()) 1000L else 400L
            if (!enabled() || busy || preview.previewStreamState.value != PreviewView.StreamState.STREAMING) {
                handler.postDelayed(this, next); return
            }
            busy = true
            val accepted = reader.read { bmp, _ ->
                if (bmp == null || closed) { busy = false; bmp?.recycle(); handler.postDelayed(this, next); return@read }
                worker.execute {
                    val t = SystemClock.elapsedRealtimeNanos()
                    val text = runCatching { zxing.read(bmp, android.graphics.Rect(0, 0, bmp.width, bmp.height), 0) }
                        .getOrNull()?.firstOrNull { !it.text.isNullOrBlank() }?.text
                    lastReadMs = (SystemClock.elapsedRealtimeNanos() - t) / 1e6
                    bmp.recycle()
                    handler.post {
                        busy = false
                        if (!running || closed) return@post
                        val now = SystemClock.elapsedRealtime()
                        if (text != null) { if (dedupe.fresh(text, now)) onFound(QrContent.classify(text)) }
                        else if (dedupe.gone(now)) { dedupe.reset(); onGone() }
                        handler.postDelayed(this, next)
                    }
                }
            }
            if (!accepted) { busy = false; handler.postDelayed(this, next) }
        }
    }

    override fun close() {
        if (closed) return
        closed = true; running = false; handler.removeCallbacks(tick)
        reader.close(); worker.shutdown()
    }

}
