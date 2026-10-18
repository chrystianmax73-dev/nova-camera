package com.novacamera.s24.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthRefreshPolicyTest {
    private val cena = FloatArray(64) { (it % 8) / 8f }

    @Test fun primeiraVezCalcula() = assertTrue(DepthRefreshPolicy().needed(0, cena, null))

    @Test fun cenaParadaNaoRecalcula() {
        val p = DepthRefreshPolicy(); p.done(0, cena, null)
        val ruido = FloatArray(64) { cena[it] + if (it % 2 == 0) 0.01f else -0.01f }
        assertFalse(p.needed(5_000, ruido, null))
    }

    @Test fun enquadramentoNovoToqueNovoOuTempoMaximoRecalculam() {
        val p = DepthRefreshPolicy(); p.done(0, cena, 0.5f to 0.5f)
        val outra = FloatArray(64) { 1f - cena[it] }
        assertTrue(p.needed(1_000, outra, 0.5f to 0.5f))
        assertTrue(p.needed(1_000, cena, 0.7f to 0.5f))
        assertTrue(p.needed(1_000, cena, null))
        assertTrue(p.needed(DepthRefreshPolicy.MAX_AGE_MS, cena, 0.5f to 0.5f))
        assertFalse(p.needed(1_000, cena, 0.51f to 0.5f))
    }
}
