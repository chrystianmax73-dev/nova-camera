package com.novacamera.s24.core

import com.novacamera.s24.core.imaging.Plane
import com.novacamera.s24.core.imaging.oriented
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sin

class PreviewMaskCalibrationTest {
    private val w = PreviewMaskCalibration.GW; private val h = PreviewMaskCalibration.GH
    private fun scene() = FloatArray(w * h) { i -> val x = i % w; val y = i / w; (0.5 + 0.3 * sin(x / 3.0) + 0.02 * y * (x % 5)).toFloat() }
    private fun flipped(a: FloatArray, fx: Boolean, fy: Boolean) = FloatArray(w * h) { i ->
        val x = i % w; val y = i / w; a[(if (fy) h - 1 - y else y) * w + (if (fx) w - 1 - x else x)]
    }

    @Test fun findsTheFlipBetweenTextureAndAnalysis() {
        val analysis = scene()
        for (fx in listOf(false, true)) for (fy in listOf(false, true)) {
            // Leitura da textura = análise vista com esse espelhamento.
            assertEquals(fx to fy, PreviewMaskCalibration.bestFlip(flipped(analysis, fx, fy), analysis))
        }
    }

    @Test fun flatSceneIsNotTrusted() {
        assertNull(PreviewMaskCalibration.bestFlip(FloatArray(w * h) { 0.5f }, scene()))
    }

    @Test fun gridAveragesCells() {
        val g = PreviewMaskCalibration.grid(FloatArray(64 * 48) { if (it / 64 < 24) 1f else 0f }, 64, 48)
        assertEquals(1f, g[0], 1e-6f); assertEquals(0f, g[(h - 1) * w], 1e-6f)
    }

    /** A máscara volta da orientação de pé para a do buffer: ida e volta tem de ser identidade. */
    @Test fun maskRotationRoundTrip() {
        val p = Plane(5, 3, FloatArray(15) { it.toFloat() })
        for (rot in listOf(0, 90, 180, 270)) {
            val back = p.oriented(rot, false).oriented((360 - rot) % 360, false)
            assertEquals(5, back.w); assertArrayEquals(p.data, back.data, 0f)
        }
    }
}
