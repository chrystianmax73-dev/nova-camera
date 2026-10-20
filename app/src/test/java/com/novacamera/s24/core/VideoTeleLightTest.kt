package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTeleLightTest {
    @Test fun escuroNa3xSeguraAntesDaTele() {
        val s = VideoTeleLight.State()
        // 3× a ISO 1500, 1/30 s: a 5× pediria ~3000 (≥ 90% de 3200).
        assertTrue(s.update(1500, 33_333_333L, 3200, onTele5 = false))
        assertEquals(4.9f, VideoTeleLight.zoom(5.0f, s.holding), 0f)
        assertEquals(4.9f, VideoTeleLight.zoom(5.8f, s.holding), 0f)
        assertEquals(10f, VideoTeleLight.zoom(10f, s.holding), 0f)   // 10× segue na 5×
        assertEquals(3f, VideoTeleLight.zoom(3f, s.holding), 0f)
    }

    @Test fun claroNaoMexe() {
        val s = VideoTeleLight.State()
        assertFalse(s.update(400, 16_000_000L, 3200, onTele5 = false))
        assertEquals(5f, VideoTeleLight.zoom(5f, s.holding), 0f)
        // ISO alto mas tempo curto: o automático ainda pode alongar a exposição.
        assertFalse(s.update(3000, 10_000_000L, 3200, onTele5 = true))
    }

    @Test fun teleNoTetoSegura() {
        val s = VideoTeleLight.State()
        assertTrue(s.update(3200, 41_000_000L, 3200, onTele5 = true))
        assertTrue(s.holding)
    }

    @Test fun histerese() {
        val s = VideoTeleLight.State()
        s.update(1500, 33_333_333L, 3200, onTele5 = false)
        assertFalse(s.update(1200, 33_333_333L, 3200, onTele5 = false))  // 2400: entre 55% e 90%, fica
        assertTrue(s.holding)
        assertTrue(s.update(800, 33_333_333L, 3200, onTele5 = false))    // 1600 ≤ 55%: solta
        assertFalse(s.holding)
    }
}
