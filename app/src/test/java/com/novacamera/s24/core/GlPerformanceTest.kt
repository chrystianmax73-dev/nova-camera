package com.novacamera.s24.core
import org.junit.Assert.*
import org.junit.Test
class GlPerformanceTest {
    @Test fun localizacaoPertenceAoProgramaInclusiveAusente() {
        var calls=0;val cache=GlLocationCache {p,n -> calls++;if(n=="ausente")-1 else p}
        repeat(60){assertEquals(5,cache.get(5,"uOn"));assertEquals(-1,cache.get(5,"ausente"))}
        assertEquals(2,calls);assertEquals(6,cache.get(6,"uOn"));assertEquals(3,calls)
    }
    @Test fun mascaraComMesmoTamanhoAtualizaSemRealocar() {
        val s=MaskTextureStorage();assertTrue(s.needsAllocation(128,96));s.allocated(128,96)
        repeat(100){assertFalse(s.needsAllocation(128,96))}
        assertTrue(s.needsAllocation(96,128));s.allocated(96,128);assertFalse(s.needsAllocation(96,128))
    }
    @Test fun temposContamAmostrasESessaoAntigaNaoSobrescreveAtual() {
        var t=0L;val a=GlPerformance{t};a.record(GlPerformance.SWAP,2000000);a.record(GlPerformance.SWAP,4000000)
        a.fboBuilds=1;t=1000000000;a.frame()
        assertTrue(GlPerformance.report.contains("n=2 · média/máx 3.000/4.000ms"))
        assertTrue(GlPerformance.report.contains("1 quadros/1.0s"))
        val b=GlPerformance{t};b.publish("novo");a.publish("velho")
        assertTrue(GlPerformance.report.contains("novo"));assertFalse(GlPerformance.report.contains("velho"))
    }
}
