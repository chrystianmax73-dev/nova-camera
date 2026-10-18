package com.novacamera.s24.core

import com.novacamera.s24.core.imaging.Px
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BacklightMeterTest {
    private fun scene(window: Int, subject: Int, w: Int = 100, h: Int = 100) = IntArray(w * h) { i ->
        val x = i % w
        // Janela estourada na faixa da direita (20%), assunto escuro no resto.
        if (x >= 80) Px.argb(window, window, window) else Px.argb(subject, subject, subject)
    }

    @Test fun contraluzClareiaOCentroEmPassos() {
        val r = BacklightMeter.measure(scene(255, 25), 100, 100)
        assertTrue(r.clipped > 0.15f)
        val t = BacklightMeter.target(0f, r)
        assertEquals(1.5f, t, 0.01f) // centro em ~0,1 pede ~+1,6 EV, limitado a +1,5
        val s = BacklightMeter.step(0f, t)
        assertEquals(0.34f, s, 1e-4f)
    }

    @Test fun cenaSemEstouroNaoCompensa() {
        val r = BacklightMeter.measure(scene(200, 40), 100, 100)
        assertEquals(0f, r.clipped, 0f)
        assertEquals(0f, BacklightMeter.target(0f, r), 0f)
        // Já compensando e a janela saiu do quadro: volta a zero aos poucos.
        assertEquals(0.66f, BacklightMeter.step(1f, BacklightMeter.target(1f, r)), 1e-4f)
    }

    @Test fun estavelQuandoOCentroJaEstaNoAlvo() {
        val r = BacklightMeter.measure(scene(255, 77), 100, 100) // centro ≈ 0,30
        val t = BacklightMeter.target(0.8f, r)
        assertEquals(0.8f, BacklightMeter.step(0.8f, t), 1e-4f)
    }
}
