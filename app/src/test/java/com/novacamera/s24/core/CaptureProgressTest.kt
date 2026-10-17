package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class CaptureProgressTest {
    @Test fun resultadoSemIsoOuExposicaoAindaMantemSessaoViva() {
        val p=CaptureProgress();p.begin(7)
        val before=p.snapshot()
        // O sinal é entregue na entrada do callback, antes da leitura de campos opcionais.
        repeat(90) { assertTrue(p.frame(7)) }
        assertEquals(90L,p.snapshot().frames)
        assertFalse(p.unchanged(before))
    }
    @Test fun naoReligaQuandoQuadrosVoltaramEnquantoAvisoAguardavaUi() {
        val p=CaptureProgress();p.begin(1);p.frame(1)
        val stalled=p.snapshot()
        assertTrue(p.unchanged(stalled))
        p.frame(1)
        assertFalse(p.unchanged(stalled))
        val resumed=p.snapshot();p.begin(2)
        assertFalse(p.frame(1))
        assertEquals(0L,p.snapshot().frames)
        assertFalse(p.unchanged(resumed))
        assertTrue(p.frame(2))
    }
    @Test fun previaIsoladaExtensaoSemCallbackEOrcamentoEsgotadoNaoDisparamVigia() {
        assertFalse(CaptureProgress.canWatch(false,false,false,false))
        assertFalse(CaptureProgress.canWatch(true,true,false,false))
        assertFalse(CaptureProgress.canWatch(true,true,true,true))
        assertTrue(CaptureProgress.canWatch(true,true,true,false))
        assertTrue(CaptureProgress.canWatch(true,false,false,false))
    }
}
