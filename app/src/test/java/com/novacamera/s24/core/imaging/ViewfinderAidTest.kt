package com.novacamera.s24.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewfinderAidTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test fun histogramaEEstouro() {
        val w = 40; val h = 30
        // Metade esquerda escura, direita estourada (janela).
        val px = IntArray(w * h) { if (it % w < 20) gray(30) else gray(255) }
        val r = ViewfinderAid.analyze(px, w, h, wantEdges = false)
        assertEquals(w * h, r.histogram.sum())
        assertEquals(w * h / 2, r.histogram[ViewfinderAid.BINS - 1])
        assertEquals(0.5f, r.clippedFraction, 0.001f)
        assertTrue(r.clipped[w - 1]); assertFalse(r.clipped[0])
    }

    @Test fun canalUnicoEstouradoConta() {
        // Vermelho saturado (luminância média) também perdeu detalhe.
        val r = ViewfinderAid.analyze(intArrayOf((0xFF shl 24) or (255 shl 16) or (40 shl 8) or 40), 1, 1, false)
        assertTrue(r.clipped[0])
    }

    @Test fun realceMarcaSoBordaForte() {
        val w = 60; val h = 40
        // Degrau nítido no meio; resto liso com ruído leve.
        val px = IntArray(w * h) { i -> gray(if (i % w < 30) 60 + (i * 7 % 5) else 200 + (i * 3 % 5)) }
        val r = ViewfinderAid.analyze(px, w, h, wantEdges = true)
        assertTrue(r.edges[20 * w + 30] || r.edges[20 * w + 29])
        assertFalse(r.edges[20 * w + 10]); assertFalse(r.edges[20 * w + 50])
        // Cena lisa (fora de foco): nada é marcado.
        val flat = ViewfinderAid.analyze(IntArray(w * h) { gray(120 + it % 3) }, w, h, true)
        assertFalse(flat.edges.any { it })
    }

    @Test fun nivel() {
        assertEquals(0f, ViewfinderAid.horizon(0f, 9.8f, 0f)!!, 0.01f)
        // Girado ~5° no sentido horário: o horizonte aparece girado 5° no sentido anti-horário.
        val a = ViewfinderAid.horizon(-9.8f * kotlin.math.sin(Math.toRadians(5.0)).toFloat(), 9.8f * kotlin.math.cos(Math.toRadians(5.0)).toFloat(), 0f)!!
        assertEquals(-5f, a, 0.05f)
        assertEquals(5f, ViewfinderAid.levelError(a), 0.05f)
        // Paisagem reta.
        assertEquals(0f, ViewfinderAid.levelError(ViewfinderAid.horizon(9.8f, 0f, 0f)!!), 0.01f)
        // Apontado para o chão: sem nível.
        assertNull(ViewfinderAid.horizon(0.5f, 0.5f, 9.7f))
    }
}
