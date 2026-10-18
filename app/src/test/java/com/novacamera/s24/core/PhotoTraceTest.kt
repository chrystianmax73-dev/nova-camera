package com.novacamera.s24.core
import org.junit.Assert.*
import org.junit.Test
class PhotoTraceTest {
    @Test fun correlacionaRajadaFilaEProcessamentoSemMisturarDisparos() {
        var t=0L
        val a=PhotoTrace("Retrato8",{t});a.mark("AF")
        t=100000000;a.mark("quadro1");t=800000000;a.mark("quadro8");a.publish("capturado")
        val b=PhotoTrace("outro",{t});b.publish("capturado")
        t=1200000000;a.mark("processado");a.publish("pronto")
        val r=PhotoTrace.report()
        assertTrue(r.contains("#${a.id} Retrato8"));assertTrue(r.contains("quadro8@800.0ms(+700.0)"))
        assertTrue(r.contains("total=1200.0ms"));assertTrue(r.contains("#${b.id} outro"))
        assertFalse(r.contains("Retrato8 · capturado"))
    }
    @Test fun erroTambemRegistraDuracaoEMemoriaDoHistoricoELimitada() {
        var t=0L;val a=PhotoTrace("falha",{t})
        try { a.measure("decode") {t=3000000;throw IllegalStateException()} }catch(_:IllegalStateException){}
        a.publish("erro");assertTrue(PhotoTrace.report().contains("decode=3.0ms"))
        repeat(20){PhotoTrace("novo").publish("fim")}
        assertFalse(PhotoTrace.report().contains("#${a.id} falha"))
    }
}
