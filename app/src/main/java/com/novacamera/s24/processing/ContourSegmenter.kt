package com.novacamera.s24.processing

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.novacamera.s24.core.ContourMask
import java.nio.ByteOrder

/**
 * Contorno ao toque: modelo MagicTouch (MediaPipe Interactive Segmenter, Apache-2.0, 6 MB, assets).
 * UM cálculo por toque, na prévia reduzida (512 px), em thread própria; nunca por quadro (bateria).
 * O modelo fica carregado enquanto o app está aberto e sai em [release] (onStop).
 */
object ContourSegmenter {
    private const val MODEL = "magic_touch.tflite"
    private const val INPUT = 512
    private var segmenter: InteractiveSegmenter? = null

    @Synchronized
    fun contour(context: Context, frame: Bitmap, nx: Float, ny: Float, color: Int): ContourMask.Result? {
        val seg = segmenter ?: InteractiveSegmenter.createFromOptions(context.applicationContext,
            InteractiveSegmenter.InteractiveSegmenterOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(Delegate.CPU).build())
                .setOutputConfidenceMasks(true)
                .setOutputCategoryMask(false)
                .build()).also { segmenter = it }
        val roi = InteractiveSegmenter.RegionOfInterest.create(NormalizedKeypoint.create(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f)))
        return PersonSegmenter.withOwnedInput(frame, INPUT) { image ->
            val masks = com.novacamera.s24.core.NativeGate.run("contorno ao toque") { seg.segment(image, roi) }.confidenceMasks().get()
            try {
                // Uma máscara (assunto) ou duas (fundo, assunto): a última é sempre o assunto.
                val m = masks.last()
                val buf = ByteBufferExtractor.extract(m).order(ByteOrder.nativeOrder()).asFloatBuffer()
                buf.rewind()
                val data = FloatArray(m.width * m.height) { buf.get() }
                ContourMask.edges(data, m.width, m.height, color)?.let { r ->
                    ContourMask.Result(r.w, r.h, r.edge, r.coverage, ContourMask.focusBox(data, m.width, m.height, nx, ny))
                }
            } finally { masks.forEach { it.close() } }
        }
    }

    @Synchronized
    fun release() { segmenter?.close(); segmenter = null }
}
