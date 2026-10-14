package com.novacamera.s24.processing

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SignatureTest {
    @Test
    fun deviceNamesFromRealModels() {
        assertEquals("Galaxy S24 Ultra", Signature.deviceName("SM-S928B", "samsung"))
        assertEquals("Galaxy S25 Ultra", Signature.deviceName("SM-S938B", "samsung"))
        assertEquals("Google Pixel 9 Pro", Signature.deviceName("Pixel 9 Pro", "Google"))
    }

    @Test
    fun metaRoundTrip() {
        val s = SignatureStyle(SignaturePosition.RIGHT, SignatureSize.LARGE, 0.65f, "Galaxy S24 Ultra")
        assertEquals(s, SignatureStyle.fromMeta(s.toMeta()))
    }

    @Test
    fun drawsOnlyInTheCornerArea() {
        val w = 1200; val h = 1600
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF406080.toInt()) }
        Signature.draw(bmp, SignatureStyle(SignaturePosition.LEFT, SignatureSize.MEDIUM, 0.85f, "Galaxy S24 Ultra"))
        var changedOutside = 0; var changedInside = 0
        for (y in 0 until h step 4) for (x in 0 until w step 4) {
            val changed = bmp.getPixel(x, y) != 0xFF406080.toInt()
            val corner = y > h * 0.88f && x < w * 0.6f
            if (changed) { if (corner) changedInside++ else changedOutside++ }
        }
        assertEquals("a assinatura não pode invadir a foto", 0, changedOutside)
        assertTrue(changedInside > 50)
        File("build/ui-renders").mkdirs()
        File("build/ui-renders/assinatura.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Vídeo gravado deitado no sensor e exibido em pé (rotação 90°): o texto precisa sair de pé. */
    @Test
    fun videoOverlayTransformMapsUprightCornersToBuffer() {
        val (m, w, h) = Signature.uprightToBuffer(android.graphics.Rect(0, 0, 1920, 1080), 90)
        assertEquals(1080, w); assertEquals(1920, h)
        fun map(x: Float, y: Float) = floatArrayOf(x, y).also { m.mapPoints(it) }.let { it[0].toInt() to it[1].toInt() }
        // Canto superior esquerdo de quem vê = canto inferior esquerdo do quadro bruto (girado 90° horário na exibição).
        assertEquals(0 to 1080, map(0f, 0f))
        // Canto inferior esquerdo de quem vê = canto inferior direito do quadro bruto.
        assertEquals(1920 to 1080, map(0f, 1920f))
        // Sem rotação e com recorte: só desloca para dentro do recorte.
        val (m0, w0, h0) = Signature.uprightToBuffer(android.graphics.Rect(10, 20, 110, 220), 0)
        assertEquals(100, w0); assertEquals(200, h0)
        assertEquals(10 to 20, floatArrayOf(0f, 0f).also { m0.mapPoints(it) }.let { it[0].toInt() to it[1].toInt() })
    }
}
