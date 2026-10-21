package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class OcrPostTest {
    @Test fun caixasDoMapaComExpansaoPelaAlturaDaLinha() {
        val w = 200; val h = 100
        val p = FloatArray(w * h)
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int, v: Float) { for (y in y0 until y1) for (x in x0 until x1) p[y * w + x] = v }
        fill(20, 20, 120, 30, 0.9f)  // linha de texto 100×10
        fill(30, 60, 90, 66, 0.4f)   // mancha fraca: abaixo do limiar da caixa
        fill(150, 70, 152, 71, 0.95f) // ponto minúsculo
        val b = OcrPost.boxes(p, w, h)
        assertEquals(1, b.size)
        assertEquals(19.5f, b[0].height, 0.01f) // 10 × 1,95
        assertEquals(100f + 9.5f, b[0].width, 0.01f)
        assertEquals(70f, (b[0].left + b[0].right) / 2, 0.01f)
    }

    @Test fun ctcJuntaRepeticoesPulaVazioEUsaEspaco() {
        val dict = listOf("o", "l", "á")
        // o o _ l á á (espaço=4) a
        val steps = floatArrayOf(1f, .9f, 1f, .9f, 0f, .9f, 2f, .8f, 3f, .7f, 3f, .7f, 4f, .9f, 1f, .9f)
        val (text, conf) = OcrPost.decode(steps, dict)
        assertEquals("olá o", text)
        assertTrue(conf > 0.7f)
    }

    @Test fun ordemDeLeituraPorLinha() {
        val a = OcrPost.Box(100f, 10f, 150f, 20f, 1f); val b = OcrPost.Box(10f, 12f, 90f, 22f, 1f)
        val c = OcrPost.Box(10f, 40f, 60f, 50f, 1f)
        assertEquals(listOf(b, a, c), OcrPost.readingOrder(listOf(c, a, b)) { it })
    }
}
