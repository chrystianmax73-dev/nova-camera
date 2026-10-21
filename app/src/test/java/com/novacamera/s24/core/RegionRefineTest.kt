package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class RegionRefineTest {
    @Test fun recorteFicaNoCentroDoVisivelEDentroDoLimite() {
        val b = RegionRefine.crop(.25f, .25f, .75f, .75f, 4000, 3000)!! // 2000×1500 visíveis
        assertEquals(RegionRefine.MAX_SIDE, b.width); assertEquals(RegionRefine.MAX_SIDE, b.height)
        assertEquals(2000, (b.left + b.right) / 2); assertEquals(1500, (b.top + b.bottom) / 2)
        val small = RegionRefine.crop(.5f, .5f, .52f, .52f, 4000, 3000)!! // menor que o bloco da rede
        assertEquals(RegionRefine.MIN_SIDE, small.width)
        val edge = RegionRefine.crop(.95f, .95f, 1f, 1f, 4000, 3000)!!
        assertEquals(4000, edge.right); assertEquals(3000, edge.bottom)
        assertNull(RegionRefine.crop(.1f, .1f, .1f, .2f, 4000, 3000))
    }

    /** Ponto do JPEG gravado → exibido, com a mesma rotação horária de Engine.orient. */
    private fun orientPoint(x: Int, y: Int, r: Int, uw: Int, uh: Int) = when (r) {
        90 -> (uh - y) to x
        180 -> (uw - x) to (uh - y)
        270 -> y to (uw - x)
        else -> x to y
    }

    @Test fun caixaVoltaParaACoordenadaDoJpegEmTodaRotacao() {
        val uw = 4000; val uh = 3000
        val src = RegionRefine.Box(100, 200, 900, 700)
        for (r in listOf(0, 90, 180, 270)) {
            val (ow, oh) = if (r % 180 == 0) uw to uh else uh to uw
            val a = orientPoint(src.left, src.top, r, uw, uh); val c = orientPoint(src.right, src.bottom, r, uw, uh)
            val shown = RegionRefine.Box(minOf(a.first, c.first), minOf(a.second, c.second), maxOf(a.first, c.first), maxOf(a.second, c.second))
            assertEquals("rotação $r", src, RegionRefine.unrotated(shown, r, ow, oh))
        }
    }
}
