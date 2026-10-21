package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class LensSmudgeTest {
    @Test fun veuEmVariasCenasAvisaUmaVez() {
        val l = LensSmudge(); var warned = 0
        for (i in 0 until 30) if (l.add(0.2f, 0.3f, i / 5, i * 500L)) warned++
        assertEquals(1, warned)
        for (i in 30 until 60) if (l.add(0.2f, 0.3f, i / 5, i * 500L)) warned++
        assertEquals("uma vez por 30 min", 1, warned)
    }

    @Test fun neblinaOuParedeNumaCenaSoNaoAvisa() {
        val l = LensSmudge()
        for (i in 0 until 60) assertFalse(l.add(0.25f, 0.2f, 7, i * 500L))
    }

    @Test fun lenteLimpaNaoAvisaEMedidaFunciona() {
        val l = LensSmudge()
        for (i in 0 until 60) assertFalse(l.add(0.02f, 0.8f, i / 5, i * 500L))
        val px = IntArray(100) { if (it < 50) (255 shl 24) else (255 shl 24) or 0xFFFFFF }
        val (black, contrast) = LensSmudge.measure(px)
        assertEquals(0f, black, 0.01f); assertEquals(1f, contrast, 0.01f)
        val veil = IntArray(100) { if (it < 50) (255 shl 24) or 0x505050 else (255 shl 24) or 0xB0B0B0 }
        val (b2, c2) = LensSmudge.measure(veil)
        assertTrue(b2 > LensSmudge.VEIL_BLACK && c2 < LensSmudge.VEIL_CONTRAST)
    }
}
