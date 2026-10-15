package com.novacamera.s24.processing

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.novacamera.s24.core.imaging.Plane
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Segmentação do Retrato com o modelo MediaPipe "selfie_multiclass_256x256" (Apache-2.0),
 * embutido em assets e executado offline. Além de pessoa × fundo, separa **cabelo**, pele do rosto,
 * pele do corpo, roupa e acessórios/objetos na mão (óculos, controle): tudo o que não é fundo fica
 * nítido; só o cabelo recebe transição larga.
 *
 * Limites do modelo: entrada 256×256 (bordas finas são refinadas depois na foto em resolução
 * cheia por [Engine.refineMatte]); feito para pessoas próximas; não separa animais ou objetos.
 */
object PersonSegmenter {
    private const val MODEL = "selfie_multiclass_256x256.tflite"
    private const val INPUT = 768

    // Ordem das máscaras de confiança do modelo multiclasse.
    private const val BACKGROUND = 0
    private const val HAIR = 1
    private const val FACE_SKIN = 3

    class Result(
        /** Confiança de "é pessoa" (1 − fundo). */
        val person: Plane,
        /** Cabelo: única região com transição larga (fios finos). Acessórios e objetos já estão em [person]. */
        val hair: Plane,
        /** Fração da imagem ocupada pela pessoa. */
        val coverage: Float,
        /** Fração ocupada por pele do rosto (rosto detectado de fato). */
        val faceCoverage: Float,
    )

    private fun options() = ImageSegmenter.ImageSegmenterOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(Delegate.CPU).build())
        .setRunningMode(RunningMode.IMAGE)
        .setOutputConfidenceMasks(true)
        .setOutputCategoryMask(false)
        .build()

    fun segment(context: Context, photo: Bitmap): Result {
        val segmenter = ImageSegmenter.createFromOptions(context.applicationContext, options())
        try { return run(segmenter, photo, INPUT) } finally { segmenter.close() }
    }

    /** Segmentador mantido aberto para a prévia ao vivo do Retrato (uma chamada por quadro analisado). */
    class Live(context: Context) : java.io.Closeable {
        private val segmenter = ImageSegmenter.createFromOptions(context.applicationContext, options())
        fun segment(frame: Bitmap): Result = run(segmenter, frame, 256)
        override fun close() = segmenter.close()
    }

    private fun run(segmenter: ImageSegmenter, photo: Bitmap, input: Int): Result {
        val scale = input.toFloat() / max(photo.width, photo.height)
        val small = if (scale < 1f)
            Bitmap.createScaledBitmap(photo, max(1, (photo.width * scale).toInt()), max(1, (photo.height * scale).toInt()), true)
        else photo
        try {
            val masks = segmenter.segment(BitmapImageBuilder(small).build()).confidenceMasks().get()
            fun plane(i: Int): Plane {
                val img = masks[i]
                val buf = ByteBufferExtractor.extract(img).order(ByteOrder.nativeOrder()).asFloatBuffer()
                val p = Plane(img.width, img.height)
                buf.rewind()
                for (k in 0 until img.width * img.height) p.data[k] = buf.get().coerceIn(0f, 1f)
                return p
            }
            val bg = plane(BACKGROUND)
            val person = Plane(bg.w, bg.h, FloatArray(bg.data.size) { 1f - bg.data[it] })
            val hair = plane(HAIR)
            val face = plane(FACE_SKIN)
            val n = person.data.size.toFloat()
            return Result(person, hair, person.data.count { it > 0.5f } / n, face.data.count { it > 0.5f } / n)
        } finally {
            if (small !== photo) small.recycle()
        }
    }
}
