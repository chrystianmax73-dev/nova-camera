package com.novacamera.s24.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class HdrGainTest {
    private fun enc(lin: Float) = if (lin >= 1f) 1f else lin.toDouble().pow(1 / 2.4).toFloat().let { (1.055f * it - 0.055f).coerceIn(0f, 1f) }
    private val w = 80; private val h = 60
    /** Cena linear: meios-tons variados e uma "janela" de 2,5× o branco SDR em x ≥ 60. */
    private fun scene(x: Int, y: Int) = if (x >= 60) 2.5f else 0.05f + 0.6f * x / 60f * (0.5f + 0.5f * y / h)
    private fun shot(gain: Float, patch: Boolean = false) = Plane(w, h, FloatArray(w * h) { i ->
        val x = i % w; val y = i / w
        val s = if (patch && x in 62..70 && y in 10..30) 0.05f else scene(x, y) // objeto escuro que se moveu
        enc(s * gain)
    })

    @Test fun measuresExposureGainInMidtones() {
        assertEquals(4f, HdrGain.exposureGain(shot(1f), shot(0.25f))!!, 0.2f)
    }

    @Test fun sceneRecoversHighlightsFromDarkFrameAndKeepsMidtones() {
        val s = HdrGain.scene(listOf(shot(1f), shot(0.25f)))!!
        assertEquals(2.5f, s[70, 40], 0.3f)
        assertEquals(HdrGain.lin(shot(1f)[20, 20]), s[20, 20], 1e-4f)
    }

    @Test fun movedDarkObjectNeverDarkensTheScene() {
        val s = HdrGain.scene(listOf(shot(1f), shot(0.25f, patch = true)))!!
        assertTrue(s[65, 20] >= HdrGain.lin(1f) - 1e-4f)
    }

    @Test fun noGainmapWithoutExtraRange() {
        assertNull("mesma exposição", HdrGain.scene(listOf(shot(1f), shot(1f))))
        val dim = Plane(w, h, FloatArray(w * h) { enc(0.3f * (it % w) / w + 0.05f) })
        assertNull("nada estourado", HdrGain.scene(listOf(dim, Plane(w, h, FloatArray(w * h) { enc(HdrGain.lin(dim.data[it]) * 0.25f) }))))
    }

    @Test fun ratioBoostsOnlyBeyondSdrWhite() {
        val s = HdrGain.scene(listOf(shot(1f), shot(0.25f)))!!
        val r = HdrGain.ratio(s, shot(1f))
        assertEquals(1f, r[20, 20], 1e-4f)
        assertEquals(2.5f, r[70, 40], 0.3f)
        assertTrue(HdrGain.peak(r) <= HdrGain.MAX_RATIO)
        val bytes = HdrGain.encode(r, HdrGain.peak(r))
        assertEquals(0, bytes[20 * w + 20].toInt() and 0xFF)
        assertTrue((bytes[40 * w + 70].toInt() and 0xFF) > 240)
    }

    @Test fun orientationMatchesBitmapRotation() {
        val p = Plane(4, 2).apply { this[0, 0] = 1f }
        val r = p.oriented(90, false)
        assertEquals(2, r.w); assertEquals(4, r.h); assertEquals(1f, r[1, 0], 0f)
        assertEquals(1f, r.oriented(0, true)[0, 0], 0f)
        assertEquals(1f, p.oriented(270, false)[0, 3], 0f)
        assertEquals(1f, p.oriented(180, false)[3, 1], 0f)
    }
}
