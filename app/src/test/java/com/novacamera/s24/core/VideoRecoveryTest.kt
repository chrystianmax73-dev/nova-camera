package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test
import com.novacamera.s24.core.VideoRecovery.Action

class VideoRecoveryTest {
    @Test fun bindRecusadoBloqueiaNovasRecuperacoesAteEscolhaDoUsuario() {
        val r = VideoRecovery()
        r.rejectBinding()
        assertTrue(r.exhausted)
        repeat(100) { assertEquals(Action.IGNORE, r.stalled(false)) }
        assertEquals(Action.IGNORE, r.finalized())
        r.reset()
        assertEquals(Action.REBIND, r.stalled(false))
    }

    @Test fun paradaComEisRetiraSomenteEstabilizacaoEAguardaFinalize() {
        val r = VideoRecovery()
        assertEquals(Action.FINALIZE, r.stalled(recording = true, stabilized = true))
        assertTrue(r.disableStabilization)
        repeat(10) { assertEquals(Action.IGNORE, r.stalled(true)) }
        assertEquals(Action.REBIND, r.finalized())
        assertEquals(Action.REBIND, r.stalled(recording = false, stabilized = false))
        assertEquals(Action.STOP, r.stalled(false))
        r.reset()
        assertFalse(r.disableStabilization)
    }

    @Test fun gravacaoSoReligaDepoisDeFinalizarEIgnoraInterrupcoesRepetidas() {
        val r = VideoRecovery()
        assertEquals(Action.FINALIZE, r.stalled(true))
        repeat(10) { assertEquals(Action.IGNORE, r.stalled(true)) }
        assertEquals(Action.REBIND, r.finalized())
        assertEquals(Action.IGNORE, r.finalized())
    }

    @Test fun religarNaoRenovaOrcamentoENaoCriaCicloDeTelaPreta() {
        val r = VideoRecovery()
        assertEquals(Action.REBIND, r.stalled(false))
        assertEquals(Action.REBIND, r.stalled(false))
        assertEquals(Action.STOP, r.stalled(false))
        repeat(100) { assertEquals(Action.IGNORE, r.stalled(false)) }
        assertTrue(r.exhausted)
        r.reset()
        assertEquals(Action.REBIND, r.stalled(false))
    }

    @Test fun mesmoNoLimiteAGravacaoPrecisaFinalizarSemNovaLigacao() {
        val r = VideoRecovery(0)
        assertEquals(Action.FINALIZE, r.stalled(true))
        assertEquals(Action.STOP, r.finalized())
        assertEquals(Action.IGNORE, r.stalled(false))
    }

    @Test fun efeitoAnteriorLiberaAntesDaInicializacaoDoNovoNaMesmaFilaGl() {
        val queue = ArrayDeque<() -> Unit>()
        var context = ""
        val slot = SerialEffectSlot<AutoCloseable>()
        fun effect(name: String): AutoCloseable {
            queue.addLast { context = name }
            return AutoCloseable { queue.addLast { context = "" } }
        }
        slot.replace { effect("4K30") }
        while (queue.isNotEmpty()) queue.removeFirst()()
        slot.replace { effect("FHD60") }
        while (queue.isNotEmpty()) queue.removeFirst()()
        // A ordem antiga (init novo → release antigo) deixava o contexto vazio.
        assertEquals("FHD60", context)
        slot.replace { null } // assinatura desligada
        while (queue.isNotEmpty()) queue.removeFirst()()
        assertEquals("", context)
        slot.replace { effect("frontal") }
        while (queue.isNotEmpty()) queue.removeFirst()()
        assertEquals("frontal", context)
    }
}

