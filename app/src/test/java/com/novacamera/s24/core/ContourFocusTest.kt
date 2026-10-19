package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class ContourFocusTest {
    private val w = 200
    private val h = 150
    /** Caixa do objeto em x 120..179, y 90..139 (como a caixa na prateleira de baixo). */
    private fun mask() = FloatArray(w * h) { i -> val x = i % w; val y = i / w; if (x in 120..179 && y in 90..139) 1f else 0f }

    @Test fun regiaoDeFocoFicaDentroDoObjetoTocado() {
        val b = ContourMask.focusBox(mask(), w, h, 150f / (w - 1), 115f / (h - 1))!!
        assertTrue(b[0] >= 120f / w - 1e-3f && b[2] <= 180f / w + 1e-3f)
        assertTrue(b[1] >= 90f / h - 1e-3f && b[3] <= 140f / h + 1e-3f)
        // Cresce até o limite (20% do menor lado = 30 px), não fica no ponto do dedo.
        assertEquals(30f / w, b[2] - b[0], 1.5f / w)
    }

    @Test fun dedoNaBordaUsaOObjetoAoLadoEFundoNaoGeraRegiao() {
        assertNotNull(ContourMask.focusBox(mask(), w, h, 118f / (w - 1), 115f / (h - 1)))
        assertNull(ContourMask.focusBox(mask(), w, h, 30f / (w - 1), 30f / (h - 1)))
    }

    @Test fun objetoFinoDemaisNaoViraRegiao() {
        val thin = FloatArray(w * h) { i -> if (i % w == 100) 1f else 0f }
        assertNull(ContourMask.focusBox(thin, w, h, 100f / (w - 1), .5f))
    }
}
