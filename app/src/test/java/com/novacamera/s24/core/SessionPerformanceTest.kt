package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class SessionPerformanceTest {
    private fun s(t: Long, cpu: Long, name: String = "controle", frames: Int = 0) =
        SessionPerformance.Sample(t, cpu, 100_000, name, frames, 0, 1.0)
    @Test fun comparaApenasIntervalosContinuosDaMesmaAtividade() {
        val c = SessionPerformance.Counter()
        assertNull(c.add(s(0, 0)))
        val d = c.add(s(10_000, 15_000, frames = 600))!!
        assertEquals(15_000, d.cpuMs)
        assertEquals(600, d.frames)
        assertNull(c.add(s(20_000, 20_000, "rastreando")))
        assertNull(c.add(s(60_000, 21_000, "rastreando")))
        c.reset()
        assertNull(c.add(s(70_000, 22_000, "rastreando")))
    }
    @Test fun contadoresReiniciadosNaoProduzemMelhoriaArtificial() {
        val c = SessionPerformance.Counter()
        c.add(s(1, 100, frames = 200))
        assertNull(c.add(s(10_001, 0, frames = 0)))
    }
}
