package com.novacamera.s24.core

import com.novacamera.s24.core.QuickRecording.Action
import org.junit.Assert.*
import org.junit.Test

class QuickRecordingTest {
    @Test fun soltarEnquantoALenteEstaAbrindoCancelaSemVideoTardio() {
        val q = QuickRecording()
        assertTrue(q.hold())
        assertEquals(Action.RESTORE, q.release())
        assertEquals(Action.NONE, q.ready())
    }
    @Test fun segurarGravaESoltarParaUmaUnicaVez() {
        val q = QuickRecording()
        q.hold()
        assertEquals(Action.START, q.ready())
        assertEquals(Action.NONE, q.ready())
        assertEquals(Action.STOP, q.release())
        assertEquals(Action.NONE, q.release())
        assertFalse(q.hold()) // aguarda Finalize antes de aceitar outro gesto
        q.finished()
        assertTrue(q.hold())
    }
    @Test fun arrastarAntesDeAbrirBloqueiaESoltarMantemGravacao() {
        val q = QuickRecording()
        q.hold(); q.lock()
        assertTrue(q.locked)
        assertEquals(Action.START, q.ready())
        assertEquals(Action.NONE, q.release())
        assertEquals(Action.STOP, q.interrupt()) // home/chamada/detach também finalizam bloqueado
    }
    @Test fun interrupcaoDurantePreparacaoNaoIniciaAoRetornar() {
        val q = QuickRecording()
        q.hold(); q.lock()
        assertEquals(Action.RESTORE, q.interrupt())
        assertEquals(Action.NONE, q.ready())
    }
}
