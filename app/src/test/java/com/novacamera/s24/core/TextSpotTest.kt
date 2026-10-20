package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSpotTest {
    private fun b(l: Float, t: Float, r: Float, bt: Float) = OcrPost.Box(l, t, r, bt, 0.9f)

    @Test fun duasLinhasSaoTexto() = assertTrue(TextSpot.hasText(listOf(b(10f, 10f, 80f, 22f), b(10f, 30f, 90f, 42f)), 480, 360))
    @Test fun umaLinhaGrandeBasta() = assertTrue(TextSpot.hasText(listOf(b(10f, 10f, 200f, 40f)), 480, 360))
    @Test fun manchaQuadradaNaoEhTexto() = assertFalse(TextSpot.hasText(listOf(b(10f, 10f, 60f, 60f), b(100f, 100f, 130f, 130f)), 480, 360))
    @Test fun linhasEmPeTambem() = assertTrue(TextSpot.hasText(listOf(b(10f, 10f, 22f, 90f), b(30f, 10f, 42f, 80f)), 480, 360))
    @Test fun umaLinhaPequenaNao() = assertFalse(TextSpot.hasText(listOf(b(10f, 10f, 40f, 18f)), 480, 360))

    @Test fun botaoNaoPisca() {
        val h = TextSpot.Hysteresis()
        assertFalse(h.add(true)); assertTrue(h.add(true)); assertTrue(h.visible)
        assertFalse(h.add(false)); assertTrue(h.visible)
        assertFalse(h.add(true)); assertFalse(h.add(false)); assertTrue(h.add(false)); assertEquals(false, h.visible)
    }
}
