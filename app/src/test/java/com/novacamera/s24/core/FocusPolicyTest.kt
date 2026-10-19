package com.novacamera.s24.core

import com.novacamera.s24.core.FocusPolicy.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPolicyTest {
    private fun plan(
        night: Boolean = false, frames: Int = 1, zoom: Float = 1f, sr: Boolean = false,
        point: Boolean = false, atPoint: Boolean = false, locked: Boolean = false, manual: Boolean = false, ext: Boolean = false,
        settled: Boolean = false,
    ) = FocusPolicy.beforeBurst(night, frames, zoom, sr, point, atPoint, locked, manual, ext, settled)

    @Test fun semToqueComAfContinuoAssentadoMantemOAssuntoDaCameraNaoOCentro() {
        assertEquals(Target.HOLD, plan(frames = 8, zoom = 5f, sr = true, settled = true).target)
        assertEquals(0L, plan(frames = 8, zoom = 5f, settled = true).waitMs) // sem atraso no disparo
        assertEquals(Target.CENTER, plan(frames = 8, zoom = 5f, settled = false).target) // ainda procurando: centro
        assertEquals(Target.CENTER, plan(night = true, settled = true).target) // Noturno inalterado
        assertEquals(Target.POINT, plan(frames = 8, zoom = 5f, point = true, settled = true).target) // toque manda
        assertEquals(Target.NONE, plan(frames = 1, zoom = 1f, settled = true).target)
    }

    @Test fun pontoTocadoQueVoltouAoContinuoFocaDeNovoAntesDaFoto() {
        val p = plan(point = true, atPoint = false)
        assertEquals(Target.POINT, p.target)
        assertEquals(FocusPolicy.BURST_WAIT_MS, p.waitMs)
    }

    @Test fun pontoAindaFocadoNaoAtrasaODisparo() {
        assertEquals(Target.NONE, plan(point = true, atPoint = true, frames = 8, zoom = 10f, sr = true).target)
    }

    @Test fun bloqueioFocoManualEModoDoFabricanteNaoSaoMexidos() {
        assertEquals(Target.NONE, plan(night = true, locked = true).target)
        assertEquals(Target.NONE, plan(night = true, manual = true).target)
        assertEquals(Target.NONE, plan(sr = true, ext = true).target)
    }

    @Test fun noturnoSuperResolucaoETeleMultiquadroTravamNoCentro() {
        assertEquals(Target.CENTER, plan(night = true).target)
        assertEquals(FocusPolicy.NIGHT_WAIT_MS, plan(night = true).waitMs)
        assertEquals(Target.CENTER, plan(sr = true, frames = 8, zoom = 10f).target)
        assertEquals(Target.CENTER, plan(frames = 8, zoom = 5f).target)
        assertEquals(Target.CENTER, plan(frames = 4, zoom = 3f).target)
    }

    @Test fun fotoComumNa1xNaoGanhaAtraso() {
        assertEquals(Target.NONE, plan(frames = 1, zoom = 1f).target)
        assertEquals(Target.NONE, plan(frames = 4, zoom = 1f).target)
        assertEquals(Target.NONE, plan(frames = 1, zoom = 5f).target)
    }

    @Test fun zoomQueMudaOEnquadramentoDescartaOPonto() {
        assertFalse(FocusPolicy.zoomInvalidatesPoint(2f, 2.1f))
        assertTrue(FocusPolicy.zoomInvalidatesPoint(1f, 2f))
        assertTrue(FocusPolicy.zoomInvalidatesPoint(2f, 1.5f))
        assertFalse(FocusPolicy.zoomInvalidatesPoint(0f, 2f))
    }
}
