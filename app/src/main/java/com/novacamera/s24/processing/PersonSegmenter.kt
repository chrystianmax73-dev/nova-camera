package com.novacamera.s24.processing

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.novacamera.s24.core.imaging.Plane
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Segmentação de pessoa (ML Kit Selfie Segmentation, modelo embutido, offline).
 * Recebe a foto já na orientação real; devolve a confiança 0..1 de "é pessoa" por ponto.
 *
 * Limites conhecidos (do próprio modelo): feito para pessoas a até ~2–3 m; não separa objetos,
 * animais ou comida; cabelo muito fino e objetos transparentes ficam aproximados — o refinamento
 * guiado ([Engine.refineMatte]) corrige parte disso nas bordas reais da foto.
 */
object PersonSegmenter {
    /** Lado maior da imagem enviada ao modelo (o modelo trabalha em baixa resolução de qualquer forma). */
    private const val INPUT = 768

    class Result(val mask: Plane, val coverage: Float)

    fun segment(photo: Bitmap): Result {
        val scale = INPUT.toFloat() / max(photo.width, photo.height)
        val small = if (scale < 1f)
            Bitmap.createScaledBitmap(photo, max(1, (photo.width * scale).toInt()), max(1, (photo.height * scale).toInt()), true)
        else photo
        val segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build()
        )
        try {
            val result = Tasks.await(segmenter.process(InputImage.fromBitmap(small, 0)), 20, TimeUnit.SECONDS)
            val w = result.width; val h = result.height
            val buf = result.buffer
            buf.rewind()
            val plane = Plane(w, h)
            var person = 0
            for (i in 0 until w * h) {
                val v = buf.float.coerceIn(0f, 1f)
                plane.data[i] = v
                if (v > 0.5f) person++
            }
            return Result(plane, person.toFloat() / (w * h))
        } finally {
            segmenter.close()
            if (small !== photo) small.recycle()
        }
    }
}
