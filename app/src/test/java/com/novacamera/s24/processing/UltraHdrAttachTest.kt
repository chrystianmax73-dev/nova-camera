package com.novacamera.s24.processing

import android.graphics.Bitmap
import com.novacamera.s24.core.imaging.Plane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UltraHdrAttachTest {
    @Test fun attachesGainmapOnlyWhereSceneExceedsSdrWhite() {
        val session = Session(Files.createTempDirectory("nova-hdr").toFile())
        // Cena 40×30: metade direita 3× o branco SDR; foto final 400×300 com a direita quase branca.
        session.saveHdr(Plane(40, 30, FloatArray(40 * 30) { if (it % 40 >= 20) 3f else 0.2f }))
        val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        for (y in 0 until 300) for (x in 0 until 400) bmp.setPixel(x, y, if (x >= 200) 0xFFF0F0F0.toInt() else 0xFF7C7C7C.toInt())
        UltraHdr.attach(session, bmp)
        assertTrue(bmp.hasGainmap())
        val gm = bmp.gainmap!!
        assertTrue("faixa ${gm.ratioMax.toList()}", gm.ratioMax[0] in 2.5f..3.6f)
        val g = gm.gainmapContents
        assertEquals(40, g.width)
        fun alpha(x: Int, y: Int) = (g.getPixel(x, y) ushr 24) and 0xFF
        assertEquals("meio-tom sem ganho", 0, alpha(5, 15))
        assertTrue("realce com ganho: ${alpha(35, 15)}", alpha(35, 15) > 200)
        session.dir.deleteRecursively()
    }

    @Test fun noGainmapWithoutHdrScene() {
        val session = Session(Files.createTempDirectory("nova-sdr").toFile())
        val bmp = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        UltraHdr.attach(session, bmp)
        assertFalse(bmp.hasGainmap())
        session.dir.deleteRecursively()
    }
}
