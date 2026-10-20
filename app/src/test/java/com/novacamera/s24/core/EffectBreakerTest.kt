package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectBreakerTest {
    @Test fun umaFalhaSoPausaPoucosSegundos() {
        val b = EffectBreaker()
        assertEquals(EffectBreaker.FIRST_MS, b.fail(1_000))
        assertTrue(b.blocked(2_000))
        assertFalse(b.blocked(1_000 + EffectBreaker.FIRST_MS)) // controles voltam sozinhos
    }

    @Test fun falhasSeguidasPausamMaisSemDesligarDeVez() {
        val b = EffectBreaker()
        b.fail(0); assertEquals(EffectBreaker.SECOND_MS, b.fail(10_000)); assertEquals(EffectBreaker.LONG_MS, b.fail(40_000))
        assertFalse(b.blocked(40_000 + EffectBreaker.LONG_MS))
    }

    @Test fun falhaAntigaEsquecida() {
        val b = EffectBreaker()
        b.fail(0); b.fail(10_000)
        assertEquals(EffectBreaker.FIRST_MS, b.fail(10_000 + EffectBreaker.WINDOW_MS + 1))
    }

    @Test fun voltarAoAppZera() {
        val b = EffectBreaker(); b.fail(0); b.reset(); assertFalse(b.blocked(1))
    }
}
