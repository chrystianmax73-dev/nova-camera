package com.novacamera.s24.core

import com.novacamera.s24.core.imaging.DetailGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailGateTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    /** Foto 2× de uma metade conhecida: cada pixel da metade vira 2×2 com a mesma média. */
    private fun upscale(half: IntArray, hw: Int, hh: Int, jitter: (Int, Int) -> Int = { _, _ -> 0 }): IntArray {
        val w = hw * 2
        return IntArray(w * hh * 2) { i ->
            val x = i % w; val y = i / w
            val base = half[(y / 2) * hw + x / 2] and 255
            gray((base + jitter(x, y)).coerceIn(0, 255))
        }
    }

    private val hw = 128; private val hh = 128
    private val half = IntArray(hw * hh) { i -> gray(60 + (i % hw) + (i / hw) / 2) }

    @Test fun saidaConsistenteAceitaInteira() {
        // Detalhe novo que some na média 2×2 (xadrez ±20): é exatamente o que a checagem deixa passar.
        val out = upscale(half, hw, hh) { x, y -> if ((x + y) % 2 == 0) 20 else -20 }
        val w = DetailGate.weights(DetailGate.backError(out, half, hw, hh))
        assertEquals(1f, DetailGate.acceptedFraction(w), 1e-6f)
    }

    @Test fun regiaoInventadaRecusadaComMargem() {
        // Num quadrado de 32×32 (metade) a saída muda o brilho médio em 12 níveis (letra deformada).
        val out = upscale(half, hw, hh) { x, y -> if (x / 2 in 48 until 80 && y / 2 in 48 until 80) 12 else 0 }
        val w = DetailGate.weights(DetailGate.backError(out, half, hw, hh))
        val center = w.values[(64 / DetailGate.BLOCK) * w.bw + 64 / DetailGate.BLOCK]
        assertTrue("miolo recusado: $center", center < 0.15f)
        assertTrue(w.values[0] > 0.95f) // longe dali, aceito
        assertTrue(DetailGate.acceptedFraction(w) in 0.5f..0.9f)
    }

    @Test fun misturaRespeitaPeso() {
        val full = IntArray(256 * 256) { gray(100) }
        val out = IntArray(256 * 256) { gray(200) }
        val all = DetailGate.Grid(8, 8, FloatArray(64) { 1f })
        DetailGate.blend(full, 256, 256, out, all)
        assertEquals(200, full[1000] and 255)
        val none = DetailGate.Grid(8, 8, FloatArray(64))
        val keep = IntArray(256 * 256) { gray(100) }
        DetailGate.blend(keep, 256, 256, out, none)
        assertEquals(100, keep[1000] and 255)
    }

    @Test fun metadeEhMedia2x2() {
        val px = intArrayOf(gray(10), gray(20), gray(30), gray(40))
        assertEquals(25, DetailGate.halve(px, 2, 2)[0] and 255)
    }

    @Test fun politicaSoNaTeleComZoomDigital() {
        val tele5 = DetailPolicy.lensZoom(18f, 5.6f, 4.2f)!!   // ~111 mm equivalente
        val main = DetailPolicy.lensZoom(6.3f, 9.8f, 7.4f)!!   // ~22 mm
        assertTrue(tele5 > 4f && main < 1.2f)
        assertTrue(DetailPolicy.eligible(true, 10f, 1f, tele5, 12_000_000))
        assertFalse(DetailPolicy.eligible(true, 5f, 1f, tele5, 12_000_000))   // óptico puro
        assertFalse(DetailPolicy.eligible(true, 2f, 1f, main, 12_000_000))    // recorte do sensor de 200 MP
        assertFalse(DetailPolicy.eligible(false, 10f, 1f, tele5, 12_000_000)) // Noturno/Retrato
        assertFalse(DetailPolicy.eligible(true, 10f, 1f, tele5, 49_000_000))  // grande demais
        assertTrue(DetailPolicy.eligible(true, 10f, 2f, null, 12_000_000))    // super-resolução 2×
    }
}
