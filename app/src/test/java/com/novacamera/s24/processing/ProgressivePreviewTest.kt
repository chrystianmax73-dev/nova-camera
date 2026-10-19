package com.novacamera.s24.processing

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ProgressivePreviewTest {
    private fun jpeg(w: Int, h: Int) = ByteArrayOutputStream().also {
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it)
    }.toByteArray()

    @Test fun miniaturaReduzidaEGiradaComoAFoto() {
        val b = ProcessingQueue.previewOf(CapturedFrame(jpeg(4080, 3060), 90, null), mirror = false)!!
        assertTrue("reduzida: ${b.width}x${b.height}", maxOf(b.width, b.height) in 256..511)
        assertTrue("girada para retrato", b.height > b.width)
    }

    @Test fun semGiroMantemProporcao() {
        val b = ProcessingQueue.previewOf(CapturedFrame(jpeg(4000, 3000), 0, null), mirror = false)!!
        assertEquals(4f / 3f, b.width.toFloat() / b.height, 0.02f)
    }
}
