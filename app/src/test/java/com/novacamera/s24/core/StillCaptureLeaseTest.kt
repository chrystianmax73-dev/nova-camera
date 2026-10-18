package com.novacamera.s24.core
import org.junit.Assert.*
import org.junit.Test
class StillCaptureLeaseTest {
    @Test fun oitoJPEGsNaoDisparamRebindPorPausaDaPrevia() {
        val a=StillCaptureLease();a.begin(100)
        repeat(8){i->a.request(100+i*1100L);assertTrue(a.defersRecovery(100+i*1100L+1000))}
        a.end();assertFalse(a.defersRecovery(10_000))
    }
    @Test fun sensorSemEntregaNaoFicaProtegidoIndefinidamente() {
        val a=StillCaptureLease();a.begin(0)
        assertTrue(a.defersRecovery(9999));assertFalse(a.defersRecovery(10000))
        repeat(6){a.request(it*5000L)}
        assertFalse(a.defersRecovery(30000))
    }
    @Test fun exposicaoLongaTemLimiteTotalETerminaAoDesvincular() {
        val a=StillCaptureLease();a.begin(0,15000,8)
        assertTrue(a.defersRecovery(16000));assertFalse(a.defersRecovery(18000))
        a.request(17900);assertTrue(a.defersRecovery(18000));a.end();assertFalse(a.defersRecovery(18001))
    }
}
