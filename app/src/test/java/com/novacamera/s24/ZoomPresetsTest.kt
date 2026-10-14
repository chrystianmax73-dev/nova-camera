package com.novacamera.s24

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomPresetsTest {
    @Test
    fun presetsOutsideReportedRangeAreDisabled() {
        assertFalse(ZoomPresets.isSupported(0.6f, 1f, 10f))
        assertFalse(ZoomPresets.isSupported(20f, 1f, 10f))
        assertTrue(ZoomPresets.isSupported(10f, 1f, 10f))
    }

    @Test
    fun floatingPointRangeEdgesStillCount() {
        assertTrue(ZoomPresets.isSupported(0.6f, 0.6000001f, 100f))
        assertTrue(ZoomPresets.isSupported(30f, 0.6f, 29.999998f))
    }

    @Test
    fun activePresetToleratesClampedRatio() {
        assertTrue(ZoomPresets.isActive(10f, 9.99f))
        assertFalse(ZoomPresets.isActive(10f, 9.5f))
    }

    @Test
    fun labels() {
        assertEquals("1×", ZoomPresets.label(1f))
        assertEquals("10×", ZoomPresets.label(10f))
        assertEquals("0.6×", ZoomPresets.label(0.6f))
        assertEquals("2.4×", ZoomPresets.label(2.37f))
    }
}
