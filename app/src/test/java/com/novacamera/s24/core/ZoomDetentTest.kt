package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomDetentTest {
    private val stops = listOf(0.6f, 1f, 3f, 5f, 10f)

    @Test fun stepsGrowWithZoom() {
        assertEquals(0.1f, ZoomDetent.step(1.5f), 0f)
        assertEquals(0.5f, ZoomDetent.step(4f), 0f)
        assertEquals(1f, ZoomDetent.step(30f), 0f)
    }

    @Test fun opticalStopBothDirections() {
        assertEquals(ZoomDetent.Kind.STOP, ZoomDetent.crossed(0.95f, 1.0f, stops))
        assertEquals(ZoomDetent.Kind.STOP, ZoomDetent.crossed(1.05f, 1.0f, stops))
        assertEquals(ZoomDetent.Kind.STOP, ZoomDetent.crossed(2.8f, 3.2f, stops))
        // Sair da parada não repete o tique forte.
        assertEquals(ZoomDetent.Kind.NONE, ZoomDetent.crossed(1.0f, 1.02f, stops))
    }

    @Test fun stepTicksOnlyOnBoundary() {
        assertEquals(ZoomDetent.Kind.STEP, ZoomDetent.crossed(1.18f, 1.21f, stops))
        assertEquals(ZoomDetent.Kind.NONE, ZoomDetent.crossed(1.21f, 1.25f, stops))
        assertEquals(ZoomDetent.Kind.STEP, ZoomDetent.crossed(6.4f, 6.6f, stops))
        assertEquals(ZoomDetent.Kind.NONE, ZoomDetent.crossed(2f, 2f, stops))
    }
}
