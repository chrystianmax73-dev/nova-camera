package com.novacamera.s24.core
import org.junit.Assert.*
import org.junit.Test
class LatestPendingTest {
    @Test fun atrasoNaFilaNaoRenovaIdadeDaAnalise() {
        val q=LatestPending<Long>();q.offer(1000L)
        assertFalse(CinematicPolicy.fresh(1800,q.take()!!))
        q.offer(1800L);assertTrue(CinematicPolicy.fresh(2000,q.take()!!))
    }

    @Test fun consumidorBloqueadoNaoRecebeUmaFilaDeMascarasAntigas() {
        val q=LatestPending<Int>();var posts=0
        repeat(1000){if(q.offer(it))posts++}
        assertEquals(1,posts);assertEquals(999,q.take());assertNull(q.take())
        assertTrue(q.offer(1000));assertEquals(1000,q.take())
    }
    @Test fun retirarEfeitoSubstituiMascaraQueAindaNaoFoiConsumida() {
        val q=LatestPending<String>();assertTrue(q.offer("máscara"))
        assertFalse(q.offer("limpar"));assertEquals("limpar",q.take())
    }
}
