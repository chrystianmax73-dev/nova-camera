package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ShutterZoomTest {
    private val stops = listOf(0.6f, 1f, 3f, 5f, 10f)
    private fun at(start: Float, steps: Float) = ShutterZoom.target(start, steps * ShutterZoom.STEP_DP, stops, 0.6f, 30f)

    @Test fun andaEmNumerosInteirosNaoEmDecimos() {
        assertEquals(4f, at(3f, 1f), 0f)
        assertEquals(5f, at(3f, 2f), 0f)
        assertEquals(10f, at(3f, 7f), 0f)
        assertEquals(2f, at(3f, -1f), 0f)
        assertEquals(1f, at(3f, -2f), 0f)
        assertEquals(0.6f, at(3f, -3f), 0f)
    }

    @Test fun menosDeUmDegrauNaoMexe() {
        assertEquals(3f, at(3f, 0.9f), 0f)
        assertEquals(3f, at(3f, -0.9f), 0f)
    }

    @Test fun zoomForaDaEscadaVaiParaOVizinho() {
        assertEquals(3f, at(2.4f, 1f), 0f)
        assertEquals(2f, at(2.4f, -1f), 0f)
    }

    @Test fun acimaDeDezEmPassosLargosERespeitaLimites() {
        assertEquals(15f, at(10f, 1f), 0f)
        assertEquals(30f, at(10f, 50f), 0f)
        assertEquals(0.6f, at(1f, -50f), 0f)
        assertEquals(listOf(0.6f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 15f, 20f, 30f), ShutterZoom.ladder(stops, 0.6f, 30f))
    }

    @Test fun paradaQuaseInteiraNaoDuplica() {
        // Tele de 5,1×: o 5× inteiro sai (degrau de 2% não serviria); 2,7× e 3× ficam (11%).
        val l = ShutterZoom.ladder(listOf(0.6f, 1f, 2.7f, 5.1f), 0.6f, 10f)
        assertEquals(listOf(0.6f, 1f, 2f, 2.7f, 3f, 4f, 5.1f, 6f, 7f, 8f, 9f, 10f), l)
    }
}
