package com.novacamera.s24.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Size
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VideoSignatureOverlayTest {
    private val style = SignatureStyle(SignaturePosition.LEFT, SignatureSize.LARGE, 0.85f, "Galaxy S24 Ultra")

    @Test fun textoFicaLegivelNoCantoAposRecorteRotacaoEEspelhoDoVideo() {
        for (rotation in listOf(0, 90, 180, 270)) for (mirror in listOf(false, true)) {
            val crop = Rect(20, 30, 820, 630)
            val buffer = Bitmap.createBitmap(840, 660, Bitmap.Config.ARGB_8888)
            val overlay = VideoSignatureOverlay(style)
            assertTrue(overlay.draw(crop, rotation, mirror) { Canvas(buffer) })
            // Reproduz a ordem documentada pelo CameraX sem usar a inversa sob teste:
            // primeiro recorta o buffer, depois gira e, por último, espelha na exibição.
            val cropped = Bitmap.createBitmap(buffer, crop.left, crop.top, crop.width(), crop.height())
            val forward = Matrix().apply { postRotate(rotation.toFloat()); if (mirror) postScale(-1f, 1f) }
            val displayed = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, forward, false)
            val expected = Bitmap.createBitmap(displayed.width, displayed.height, Bitmap.Config.ARGB_8888)
            Signature.draw(expected, style)
            var difference = 0L
            var visible = 0
            for (y in displayed.height * 4 / 5 until displayed.height) for (x in 0 until displayed.width) {
                difference += abs(Color.alpha(displayed.getPixel(x, y)) - Color.alpha(expected.getPixel(x, y)))
                if (Color.alpha(displayed.getPixel(x, y)) > 50) visible++
            }
            val area = displayed.width * (displayed.height - displayed.height * 4 / 5)
            assertTrue("assinatura ausente: $rotation/$mirror", visible > 100)
            assertTrue("texto/canto invertido: $rotation/$mirror", difference.toDouble() / area < 1.5)
            expected.recycle(); displayed.recycle()
            if (!cropped.isRecycled) cropped.recycle()
            if (!buffer.isRecycled) buffer.recycle()
        }
    }

    @Test fun sessentaQuadrosNaoRepintamTexturaEstaticaMasNovaGeometriaRedesenha() {
        val overlay = VideoSignatureOverlay(style)
        val crop = Rect(0, 0, 800, 600)
        val buffer = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        var locks = 0
        fun canvas(): Canvas { locks++; return Canvas(buffer) }
        repeat(120) { overlay.draw(crop, 0, false, canvas = ::canvas) }
        assertEquals(1, locks)
        assertEquals(1, overlay.drawings)
        overlay.draw(crop, 0, true, canvas = ::canvas)
        overlay.draw(crop, 90, true, canvas = ::canvas)
        overlay.draw(crop, 90, true, Size(1920, 1080), ::canvas)
        assertEquals(4, locks)
        buffer.recycle()
    }
}
