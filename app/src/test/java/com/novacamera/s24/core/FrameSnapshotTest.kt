package com.novacamera.s24.core
import org.junit.Assert.*
import org.junit.Test
class FrameSnapshotTest {
    @Test fun liberaCameraAntesDaInferenciaEMesmoSeCopiaFalhar() {
        val events=mutableListOf<String>()
        val snapshot=FrameSnapshot.readAndClose(AutoCloseable { events+="close" }) { events+="copy";byteArrayOf(1,2) }
        events+="infer"
        assertEquals(listOf("copy","close","infer"),events)
        assertEquals(2,snapshot.size)
        var closes=0
        try { FrameSnapshot.readAndClose(AutoCloseable { closes++ }) { error("cópia recusada") } } catch(_:IllegalStateException) {}
        assertEquals(1,closes)
    }
    @Test fun inferenciaLentaRecebeOrcamentoSemContornarIntervaloMinimo() {
        val b=DepthDutyBudget()
        assertTrue(b.allowed(1000,900,0))
        b.completed(1000,2000)
        assertFalse(b.allowed(4999,900,1000))
        assertTrue(b.allowed(5000,900,1000))
        b.completed(5000,5010)
        assertFalse(b.allowed(5040,900,5000))
        assertTrue(b.allowed(5900,900,5000))
    }
}
