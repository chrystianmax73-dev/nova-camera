package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TextBlocksTest {
    private fun l(t: String, x0: Float, y0: Float, x1: Float, y1: Float) = TextBlocks.Line(t, TextBlocks.Rect(x0, y0, x1, y1))

    @Test fun paragrafoViraUmBloco() {
        val b = TextBlocks.group(listOf(l("SHAMPOO HIDRA", 100f, 500f, 300f, 530f), l("PURIFICANTE", 110f, 535f, 290f, 565f), l("400 ml", 160f, 700f, 230f, 725f)))
        assertEquals(2, b.size)
        assertEquals("SHAMPOO HIDRA\nPURIFICANTE", b[0].text)
    }

    @Test fun caixaRepetidaDentroDeOutraSai() {
        val b = TextBlocks.group(listOf(l("ELSEVE", 100f, 300f, 320f, 360f), l("LSE", 140f, 305f, 230f, 355f)))
        assertEquals(1, b.size); assertEquals("ELSEVE", b[0].text)
    }

    @Test fun colunasSeparadasNaoJuntam() {
        val b = TextBlocks.group(listOf(l("NOVO", 100f, 400f, 160f, 420f), l("SEM SILICONES", 300f, 400f, 420f, 420f)))
        assertEquals(2, b.size)
    }
}
