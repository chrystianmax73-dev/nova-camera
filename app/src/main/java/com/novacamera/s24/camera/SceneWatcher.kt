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
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.novacamera.s24.core.SceneDetect
import java.util.concurrent.Executors

/**
 * Cena da prévia ([SceneDetect]): ~1×/s (com calor ou economia, a cada 2 s) lê um quadro de 320 px da própria
 * prévia (sem stream extra), classifica (EfficientNet-Lite0 int8, ~5 MB, CPU, poucos ms) e mede a folhagem.
 * Pula a leitura quando outra IA nativa está ocupada. [faceArea] vem dos rostos que a câmera já detecta.
 */
class SceneWatcher(
    private val context: Context,
    private val preview: PreviewView,
    private val enabled: () -> Boolean,
    private val slow: () -> Boolean,
    private val faceArea: () -> Float,
    private val onScene: (SceneDetect.Scene) -> Unit,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val reader = PreviewFrameReader(preview, 320f)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "nova-cena").apply { priority = Thread.MIN_PRIORITY } }
    private val tracker = SceneDetect.Tracker()
    private var classifier: ImageClassifier? = null
    private var failed = false
    private var busy = false
    private var running = false
    private var closed = false

    val scene get() = tracker.scene

    fun start() { if (closed || running || failed) return; running = true; handler.post(tick) }
    fun stop() {
        running = false; handler.removeCallbacks(tick)
        if (tracker.scene != SceneDetect.Scene.NONE) { tracker.reset(); onScene(SceneDetect.Scene.NONE) }
    }

    private fun model(): ImageClassifier? = classifier ?: runCatching {
        ImageClassifier.createFromOptions(context.applicationContext, ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.IMAGE).setMaxResults(25).build())
    }.onFailure { Log.w(TAG, "classificador de cena indisponível", it); failed = true }.getOrNull()?.also { classifier = it }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!running || closed) return
            val next = if (slow()) 2000L else 1000L
            if (!enabled() || busy || preview.previewStreamState.value != PreviewView.StreamState.STREAMING) {
                if (!enabled()) stopShowing()
                handler.postDelayed(this, next); return
            }
            busy = true
            val face = faceArea()
            val accepted = reader.read { bmp, _ ->
                if (bmp == null || closed) { busy = false; bmp?.recycle(); handler.postDelayed(this, next); return@read }
                worker.execute {
                    val result = com.novacamera.s24.core.NativeGate.tryRun("cena ao vivo") {
                        val scores = model()?.classify(BitmapImageBuilder(bmp).build())?.classificationResult()
                            ?.classifications()?.firstOrNull()?.categories().orEmpty().map { it.index() to it.score() }
                        val small = android.graphics.Bitmap.createScaledBitmap(bmp, 96, 128, true)
                        val px = IntArray(96 * 128).also { small.getPixels(it, 0, 96, 0, 0, 96, 128) }
                        if (small !== bmp) small.recycle()
                        SceneDetect.candidate(SceneDetect.groups(scores), SceneDetect.foliage(px), face)
                    }
                    bmp.recycle()
                    handler.post {
                        busy = false
                        if (!running || closed) return@post
                        if (result != null && tracker.update(result.first, result.second, SystemClock.elapsedRealtime())) onScene(tracker.scene)
                        if (failed) { running = false; return@post }
                        handler.postDelayed(this, next)
                    }
                }
            }
            if (!accepted) { busy = false; handler.postDelayed(this, next) }
        }
    }

    private fun stopShowing() {
        if (tracker.scene != SceneDetect.Scene.NONE) { tracker.reset(); onScene(SceneDetect.Scene.NONE) }
    }

    override fun close() {
        if (closed) return
        closed = true; running = false; handler.removeCallbacks(tick)
        reader.close()
        worker.execute { classifier?.close(); classifier = null }
        worker.shutdown()
    }

    companion object {
        private const val TAG = "NovaScene"
        const val MODEL = "scene/efficientnet_lite0.tflite"
    }
}
