package com.novacamera.s24.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.view.PreviewView
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.novacamera.s24.core.PalmPose
import com.novacamera.s24.core.PalmTrigger
import java.util.concurrent.Executors

/**
 * Disparo com a palma (opção, desligada por padrão): mostre a palma aberta para a câmera por ~0,7 s e o
 * app inicia o contador de 3 s. Lê a própria prévia (snapshot de 480 px, sem stream extra) ~4×/s; com calor
 * ou economia, ~1,5×/s; não roda durante a captura nem com outra IA nativa ocupada (pula a leitura).
 * O modelo (detector de mão do MediaPipe, ~7,8 MB) só é carregado com a opção ligada.
 */
class PalmShutter(
    private val context: Context,
    private val preview: PreviewView,
    private val enabled: () -> Boolean,
    private val slow: () -> Boolean,
    private val onPalm: () -> Unit,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val reader = PreviewFrameReader(preview, 480f)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "nova-palma").apply { priority = Thread.MIN_PRIORITY } }
    private val trigger = PalmTrigger()
    private var landmarker: HandLandmarker? = null
    private var failed = false
    private var busy = false
    private var running = false
    private var closed = false
    @Volatile var lastReadMs = 0.0
        private set

    fun start() { if (closed || running || failed) return; running = true; handler.post(tick) }
    fun stop() { running = false; handler.removeCallbacks(tick); trigger.reset() }

    private fun model(): HandLandmarker? = landmarker ?: runCatching {
        HandLandmarker.createFromOptions(context.applicationContext, HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.IMAGE).setNumHands(2)
            .setMinHandDetectionConfidence(0.6f).setMinHandPresenceConfidence(0.6f).build())
    }.onFailure { Log.w(TAG, "detector de mão indisponível", it); failed = true }.getOrNull()?.also { landmarker = it }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!running || closed) return
            val next = if (slow()) 700L else 250L
            if (!enabled() || busy || preview.previewStreamState.value != PreviewView.StreamState.STREAMING) {
                trigger.reset(); handler.postDelayed(this, next); return
            }
            busy = true
            val accepted = reader.read { bmp, _ ->
                if (bmp == null || closed) { busy = false; bmp?.recycle(); handler.postDelayed(this, next); return@read }
                worker.execute {
                    val t = SystemClock.elapsedRealtimeNanos()
                    val aspect = bmp.width.toFloat() / bmp.height
                    // null = outra IA nativa ocupada: esta leitura fica de fora (não conta nem zera a palma).
                    val palm = com.novacamera.s24.core.NativeGate.tryRun("palma ao vivo") {
                        val hands = model()?.detect(BitmapImageBuilder(bmp).build())?.landmarks().orEmpty()
                        hands.any { pts ->
                            val xy = FloatArray(42); pts.take(21).forEachIndexed { i, p -> xy[2 * i] = p.x(); xy[2 * i + 1] = p.y() }
                            PalmPose.isOpenPalm(xy, aspect)
                        }
                    }
                    lastReadMs = (SystemClock.elapsedRealtimeNanos() - t) / 1e6
                    bmp.recycle()
                    handler.post {
                        busy = false
                        if (!running || closed) return@post
                        if (palm != null && trigger.add(palm, SystemClock.elapsedRealtime())) onPalm()
                        if (failed) { running = false; return@post }
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
        reader.close()
        worker.execute { landmarker?.close(); landmarker = null }
        worker.shutdown()
    }

    companion object {
        private const val TAG = "NovaPalm"
        const val MODEL = "hand/hand_landmarker.task"
    }
}
