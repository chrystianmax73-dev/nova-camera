package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFrameTest {
    private val square = floatArrayOf(100f, 100f, 200f, 100f, 200f, 200f, 100f, 200f)

    @Test fun cantosEmL() {
        val b = CodeFrame.brackets(square)
        assertEquals(32, b.size)
        // Canto superior esquerdo: um braço para a direita, outro para baixo, 24% do lado.
        assertEquals(100f, b[0], 0.01f); assertEquals(124f, b[2], 0.01f); assertEquals(100f, b[3], 0.01f)
        assertEquals(100f, b[6], 0.01f); assertEquals(124f, b[7], 0.01f)
    }

    @Test fun entradaAmpliadaEmVoltaDoCentro() {
        val b = CodeFrame.brackets(square, scale = 1.5f)
        assertEquals(75f, b[0], 0.01f); assertEquals(75f, b[1], 0.01f)
    }

    @Test fun acompanhaSemPular() {
        val now = square.copyOf(); val target = FloatArray(8) { square[it] + 40f }
        assertTrue(CodeFrame.follow(now, target, 0.3f))
        assertEquals(112f, now[0], 0.01f)
        repeat(40) { CodeFrame.follow(now, target, 0.3f) }
        assertFalse(CodeFrame.follow(now, target, 0.3f))
        assertEquals(140f, now[0], 0.01f)
    }

    @Test fun rotuloDoAviso() {
        assertEquals("Link · instagram.com", QrContent.classify("https://www.instagram.com/x").label())
        assertEquals("Link · cokeurl.com", QrContent.classify("https://cokeurl.com/q/BR-OOH-2000").label())
        assertTrue(QrContent.classify("WIFI:S:Casa;T:WPA;P:123;;").label().startsWith("Wi-Fi · Casa"))
    }
}
