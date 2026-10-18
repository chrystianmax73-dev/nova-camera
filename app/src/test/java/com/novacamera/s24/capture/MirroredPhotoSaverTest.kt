package com.novacamera.s24.capture

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Confere em pixels reais que a selfie fica "como no visor": o lado direito continua à direita. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MirroredPhotoSaverTest {
    /** Imagem 4×2 do sensor: metade esquerda vermelha, direita azul. */
    private fun sensorImage(): Bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until 4) for (y in 0 until 2) setPixel(x, y, if (x < 2) Color.RED else Color.BLUE)
    }

    private fun apply(rotation: Int): Bitmap {
        val src = sensorImage()
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, MirroredPhotoSaver.transform(rotation), true)
    }

    @Test
    fun noRotationSwapsLeftAndRight() {
        val out = apply(0)
        assertEquals(Color.BLUE, out.getPixel(0, 0))
        assertEquals(Color.RED, out.getPixel(3, 0))
    }

    @Test
    fun rotationHappensBeforeMirror() {
        // 270° (sensor frontal típico em retrato): a imagem vira 2×4; o espelho atua no eixo horizontal final.
        val out = apply(270)
        assertEquals(2, out.width)
        assertEquals(4, out.height)
        // Após girar 270°, o vermelho fica embaixo; o espelho horizontal não troca cima/baixo.
        assertEquals(Color.BLUE, out.getPixel(0, 0))
        assertEquals(Color.RED, out.getPixel(0, 3))
        assertEquals(out.getPixel(0, 0), out.getPixel(1, 0))
    }
}
