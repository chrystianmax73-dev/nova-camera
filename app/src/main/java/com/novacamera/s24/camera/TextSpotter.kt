package com.novacamera.s24.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.camera.view.PreviewView
import com.novacamera.s24.core.TextSpot
import com.novacamera.s24.processing.TextReader
import java.util.concurrent.Executors

/**
 * Botão de texto automático na FOTO (pedido do dono, 20/10): a cada ~1,5 s (3,5 s com calor ou economia)
 * o detector de texto olha a prévia reduzida a 480 px; com texto em duas leituras seguidas o botão aparece,
 * sem texto em duas, some. Só o detector (o reconhecimento das letras fica para o toque). Na CPU.
 */
class TextSpotter(
    private val context: Context,
    private val preview: PreviewView,
    private val enabled: () -> Boolean,
    private val slow: () -> Boolean,
    private val onChange: (Boolean) -> Unit,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val reader = PreviewFrameReader(preview, 480f)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "nova-texto").apply { priority = Thread.MIN_PRIORITY } }
    private val state = TextSpot.Hysteresis()
    private var busy = false
    private var running = false
    private var closed = false

    fun start() { if (closed || running) return; running = true; handler.post(tick) }
    fun stop() { running = false; handler.removeCallbacks(tick); if (state.visible) { state.reset(); onChange(false) } else state.reset() }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!running || closed) return
            val next = if (slow()) 3500L else 1500L
            if (!enabled() || busy || preview.previewStreamState.value != PreviewView.StreamState.STREAMING) {
                if (state.visible && !enabled()) { state.reset(); onChange(false) }
                handler.postDelayed(this, next); return
            }
            busy = true
            val accepted = reader.read { bmp, _ ->
                if (bmp == null || closed) { busy = false; bmp?.recycle(); handler.postDelayed(this, next); return@read }
                worker.execute {
                    val has = runCatching { TextReader.spot(context, bmp) }.getOrNull()
                    bmp.recycle()
                    handler.post {
                        busy = false
                        if (!running || closed) return@post
                        if (has != null && state.add(has)) onChange(state.visible)
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
