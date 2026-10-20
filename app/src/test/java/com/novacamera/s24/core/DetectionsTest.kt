package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectionsTest {
    /** Saídas vazias (notas 0) com uma célula forte no nível de passo 8. */
    private fun outs(cell: Int, cls: Int, score: Float, peak: Int): List<FloatArray> {
        val ns = intArrayOf(52 * 52, 26 * 26, 13 * 13, 7 * 7)
        val scores = ns.map { FloatArray(it * 80) }
        val regs = ns.map { FloatArray(it * 32) }
        scores[0][cell * 80 + cls] = score
        for (k in 0 until 4) regs[0][cell * 32 + k * 8 + peak] = 30f // softmax ≈ one-hot em "peak"
        return scores + regs
    }

    @Test fun decodificaCaixaPelaDistribuicao() {
        // Célula (x=10, y=5) no passo 8 → centro (80, 40); distância 2 × 8 = 16 para cada lado.
        val d = Detections.decode(outs(5 * 52 + 10, 39, 0.9f, 2), 416, 416)
        assertEquals(1, d.size)
        assertEquals("Garrafa", d[0].label)
        assertEquals(64f, d[0].l, 0.1f); assertEquals(24f, d[0].t, 0.1f); assertEquals(96f, d[0].r, 0.1f); assertEquals(56f, d[0].b, 0.1f)
    }

    @Test fun escalaParaAImagemDeOrigem() {
        val d = Detections.decode(outs(5 * 52 + 10, 58, 0.8f, 2), 832, 1248)
        assertEquals("Planta", d[0].label)
        assertEquals(128f, d[0].l, 0.2f); assertEquals(72f, d[0].t, 0.2f)
    }

    @Test fun notaBaixaNaoEntra() = assertEquals(0, Detections.decode(outs(100, 0, 0.3f, 2), 416, 416).size)

    @Test fun toquePegaAMenorCaixaQueContem() {
        val mesa = Detections.Det(60, 0.8f, 0f, 0f, 400f, 400f)
        val vaso = Detections.Det(58, 0.7f, 100f, 100f, 160f, 200f)
        assertEquals("Planta", Detections.pickAt(listOf(mesa, vaso), 120f, 150f, 416, 416)?.label)
        assertEquals("Mesa", Detections.pickAt(listOf(mesa, vaso), 300f, 300f, 416, 416)?.label)
        assertNull(Detections.pickAt(listOf(vaso), 400f, 400f, 416, 416))
    }

    @Test fun nmsTiraRepetida() {
        val a = Detections.Det(0, 0.9f, 0f, 0f, 100f, 100f); val b = Detections.Det(0, 0.8f, 5f, 5f, 100f, 100f)
        assertEquals(1, Detections.nms(listOf(a, b)).size)
    }
}
