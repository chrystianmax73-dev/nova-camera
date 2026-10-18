package com.novacamera.s24.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPhotoTest {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    // SOI, APP1 Exif mínimo, SOS falso, EOI
    private val jpeg = b(0xFF, 0xD8, 0xFF, 0xE1, 0x00, 0x08, 'E'.code, 'x'.code, 'i'.code, 'f'.code, 0, 0, 0xFF, 0xDA, 0x00, 0x02, 1, 2, 3, 0xFF, 0xD9)
    private val mp4 = ByteArray(1000) { (it % 251).toByte() }

    @Test fun mp4NoFimEXmpLogoAposOSoi() {
        val out = MotionPhoto.build(jpeg, mp4, 1_500_000)
        assertArrayEquals(mp4, out.copyOfRange(out.size - mp4.size, out.size))
        assertEquals(0xE1.toByte(), out[3])
        val text = String(out, Charsets.ISO_8859_1)
        assertTrue(text.contains("GCamera:MotionPhoto=\"1\""))
        assertTrue(text.contains("Item:Length=\"1000\""))
        assertTrue(text.contains("GCamera:MicroVideoOffset=\"1000\""))
        assertTrue(text.contains("MotionPhotoPresentationTimestampUs=\"1500000\""))
        // O JPEG original continua inteiro antes do vídeo, terminando em EOI.
        val jpegEnd = out.size - mp4.size
        assertEquals(0xD9.toByte(), out[jpegEnd - 1]); assertEquals(0xFF.toByte(), out[jpegEnd - 2])
        assertNull(MotionPhoto.refuse(jpeg))
    }

    @Test fun recusaUltraHdrEXmpExistente() {
        val mpf = b(0xFF, 0xD8, 0xFF, 0xE2, 0x00, 0x06, 'M'.code, 'P'.code, 'F'.code, 0, 0xFF, 0xD9)
        assertNotNull(MotionPhoto.refuse(mpf))
        val withXmp = MotionPhoto.build(jpeg, mp4, 0)
        assertNotNull(MotionPhoto.refuse(withXmp))
        assertNotNull(MotionPhoto.refuse(b(1, 2, 3, 4)))
    }
}
