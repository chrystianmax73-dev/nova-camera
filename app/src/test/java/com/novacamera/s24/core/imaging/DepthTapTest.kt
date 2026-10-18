package com.novacamera.s24.core.imaging

import org.junit.Assert.assertTrue
import org.junit.Test

class DepthTapTest {
    /** Fundo longe (0,1), objeto perto no centro (0,9) e objeto médio à esquerda (0,5). */
    private fun scene(): Plane {
        val w = 80; val h = 60
        return Plane(w, h, FloatArray(w * h) { i ->
            val x = i % w; val y = i / w
            when {
                x in 32..48 && y in 18..42 -> 0.9f
                x in 4..16 && y in 10..50 -> 0.5f
                else -> 0.1f
            }
        })
    }

    private fun sharp(p: DepthPortrait.Plan, x: Int, y: Int) = p.subject[x, y] >= 0.5f

    @Test fun semToque_assuntoEhOCentro() {
        val p = DepthPortrait.plan(scene(), null)
        assertTrue(sharp(p, 40, 30)); assertTrue(!sharp(p, 10, 30))
    }

    @Test fun toqueNoObjetoDaEsquerda_viraOAssunto() {
        val p = DepthPortrait.plan(scene(), null, 10f / 79f to 30f / 59f)
        assertTrue("objeto tocado nítido", sharp(p, 10, 30))
        assertTrue("objeto do centro desfocado", !sharp(p, 40, 30))
        assertTrue("fundo desfocado", !sharp(p, 70, 5))
    }

    @Test fun toqueNaPessoa_mantemAPessoa() {
        val s = scene()
        val person = Plane(s.w, s.h, FloatArray(s.w * s.h) { i -> val x = i % s.w; val y = i / s.w; if (x in 32..48 && y in 18..42) 1f else 0f })
        val p = DepthPortrait.plan(s, person, 40f / 79f to 30f / 59f)
        assertTrue(p.fromPerson); assertTrue(sharp(p, 40, 30))
    }
}

class DepthDiskFilterTest {
    @org.junit.Test fun discoNaoFazCantoReto() {
        val p = Plane(41, 41)
        p[20, 20] = 1f
        val q = DepthPortrait.maxFilterDisk(p, 10)
        org.junit.Assert.assertEquals(1f, q[20, 30], 0f)   // borda do disco no eixo
        org.junit.Assert.assertEquals(1f, q[27, 27], 0f)   // dentro (distância ≈ 9,9)
        org.junit.Assert.assertEquals(0f, q[29, 29], 0f)   // canto do quadrado: fora do disco
        org.junit.Assert.assertEquals(1f, DepthPortrait.maxFilter(p, 10)[29, 29], 0f) // o quadrado preenchia
    }
}
