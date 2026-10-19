package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class AutoNightPolicyTest {
    private fun w(iso: Int?, expMs: Long?, zoom: Float = 1f, on: Boolean = true, hot: Boolean = false) =
        AutoNightPolicy.wanted(on, iso, expMs?.times(1_000_000L), zoom, hot)

    @Test fun fotoNoEscuroDaTeleUsaONoturno() {
        assertTrue(w(2056, 41, zoom = 5f)) // foto real 19/10 20:22, 5×
        assertTrue(w(3196, 41, zoom = 3f)) // foto real 19/10, 3×
        assertTrue(w(1000, 33, zoom = 10f))
    }

    @Test fun luzDeInteriorEDiaFicamNaFotoComum() {
        assertFalse(w(1615, 33)) // geladeira 1×: ISO alto mas a principal aguenta
        assertFalse(w(2000, 16, zoom = 5f)) // tempo curto: ainda há luz (ou movimento)
        assertFalse(w(400, 33, zoom = 5f))
        assertTrue(w(2500, 33)) // principal só bem no escuro
    }

    @Test fun desligadoQuenteOuSemMedicaoNaoMuda() {
        assertFalse(w(3000, 60, zoom = 5f, on = false))
        assertFalse(w(3000, 60, zoom = 5f, hot = true))
        assertFalse(w(null, 60, zoom = 5f)); assertFalse(w(3000, null, zoom = 5f))
    }
}
