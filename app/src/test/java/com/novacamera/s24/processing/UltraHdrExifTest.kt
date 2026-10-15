package com.novacamera.s24.processing

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * O JPEG Ultra HDR é a imagem SDR com metadados XMP (hdrgm) e um índice MPF que aponta, por
 * deslocamento relativo, para o gainmap gravado depois do fim da imagem principal. A gravação do
 * EXIF não pode descartar esses segmentos nem mudar a distância entre o índice e o gainmap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UltraHdrExifTest {
    private fun jpeg(w: Int, h: Int, c: Int) = ByteArrayOutputStream().also {
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(c) }.compress(Bitmap.CompressFormat.JPEG, 90, it)
    }.toByteArray()

    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val len = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (len shr 8).toByte(), len.toByte()) + payload
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..hay.size - needle.size) { for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer; return i }
        return -1
    }

    @Test fun exifWritePreservesGainmapStructure() {
        val primary = jpeg(64, 48, 0xFF808080.toInt())
        val gainmap = jpeg(16, 12, 0xFF404040.toInt())
        val xmp = segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta hdrgm:Version=\"1.0\"/>".toByteArray())
        val mpf = segment(0xE2, "MPF\u0000MM-index".toByteArray())
        val file = primary.copyOfRange(0, 2) + xmp + mpf + primary.copyOfRange(2, primary.size) + gainmap
        val mpfAt = indexOf(file, "MPF\u0000".toByteArray())
        val distance = file.size - gainmap.size - mpfAt

        val f = File.createTempFile("uhdr", ".jpg").apply { writeBytes(file) }
        ExifInterface(f).apply {
            setAttribute(ExifInterface.TAG_SOFTWARE, MediaWriter.SOFTWARE)
            setAttribute(ExifInterface.TAG_MAKE, "samsung")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:15 03:30:00")
            saveAttributes()
        }
        val out = f.readBytes()
        assertEquals("EXIF gravado", "samsung", ExifInterface(f).getAttribute(ExifInterface.TAG_MAKE))
        assertTrue("XMP hdrgm preservado", indexOf(out, "hdrgm:Version".toByteArray()) > 0)
        val mpfOut = indexOf(out, "MPF\u0000".toByteArray())
        assertTrue("MPF preservado", mpfOut > 0)
        assertArrayEquals("gainmap intacto no fim", gainmap, out.copyOfRange(out.size - gainmap.size, out.size))
        assertEquals("deslocamento MPF → gainmap", distance, out.size - gainmap.size - mpfOut)
        f.delete()
    }
}
