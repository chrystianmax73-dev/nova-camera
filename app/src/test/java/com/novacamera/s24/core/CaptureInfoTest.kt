package com.novacamera.s24.core

import androidx.exifinterface.media.ExifInterface
import com.novacamera.s24.processing.SignatureStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureInfoTest {
    @Test fun keysMatchExifInterface() {
        assertEquals(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, CaptureInfo.FOCAL_35)
        assertEquals(ExifInterface.TAG_F_NUMBER, CaptureInfo.F_NUMBER)
        assertEquals(ExifInterface.TAG_EXPOSURE_TIME, CaptureInfo.EXPOSURE)
        assertEquals(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, CaptureInfo.ISO)
    }

    @Test fun formatsOnlyWhatTheCameraRecorded() {
        val full = mapOf(CaptureInfo.FOCAL_35 to "23", CaptureInfo.F_NUMBER to "1.7", CaptureInfo.EXPOSURE to "0.008333", CaptureInfo.ISO to "50")
        assertEquals("23 mm   f/1.7   1/120 s   ISO 50", CaptureInfo.line(full))
        assertEquals("f/3.4   1/60 s", CaptureInfo.line(mapOf(CaptureInfo.F_NUMBER to "34/10", CaptureInfo.EXPOSURE to "1/60")))
        assertEquals("2 s   ISO 3200", CaptureInfo.line(mapOf(CaptureInfo.ISO to "3200", CaptureInfo.EXPOSURE to "2")))
        assertNull(CaptureInfo.line(emptyMap()))
        assertEquals("0,5 s", CaptureInfo.shutter(0.5))
    }

    @Test fun signatureMetaKeepsOptionAndOldSessionsStillParse() {
        val s = SignatureStyle(captureInfo = true, device = "Galaxy S24 Ultra")
        assertEquals(true, SignatureStyle.fromMeta(s.toMeta())!!.captureInfo)
        assertEquals(false, SignatureStyle.fromMeta("LEFT;MEDIUM;0.85;Galaxy S24 Ultra")!!.captureInfo)
        assertNull("opção desligada não lê EXIF", SignatureStyle(captureInfo = false, device = "x").withExif(mapOf(CaptureInfo.ISO to "50")).details)
        assertEquals("ISO 50", SignatureStyle(captureInfo = true, device = "x").withExif(mapOf(CaptureInfo.ISO to "50")).details)
    }
}
