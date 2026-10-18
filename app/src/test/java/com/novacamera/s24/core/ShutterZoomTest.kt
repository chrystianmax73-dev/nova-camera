package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class ShutterZoomTest {
    private val stops = listOf(0.6f, 1f, 3f, 5f, 10f)

    @Test fun de3Para5ComCercaDeUmCentimetro() {
        val dp = ln(5f / 3f) * ShutterZoom.DP_PER_E
        assertTrue("menos de 70 dp", dp < 70f)
        assertEquals(5f, ShutterZoom.target(3f, dp, stops, 0.6f, 100f), 0f)
    }

    @Test fun grudaNasParadasPerto() {
        assertEquals(5f, ShutterZoom.target(3f, ln(4.8f / 3f) * ShutterZoom.DP_PER_E, stops, 0.6f, 100f), 0f)
        assertEquals(3f, ShutterZoom.target(3f, 4f, stops, 0.6f, 100f), 0f)
        val meio = ShutterZoom.target(3f, ln(4f / 3f) * ShutterZoom.DP_PER_E, stops, 0.6f, 100f)
        assertEquals(4f, meio, 0.01f)
    }

    @Test fun respeitaLimites() {
        assertEquals(0.6f, ShutterZoom.target(1f, -1000f, stops, 0.6f, 100f), 0f)
        assertEquals(100f, ShutterZoom.target(10f, 1000f, stops, 0.6f, 100f), 0f)
    }
}
