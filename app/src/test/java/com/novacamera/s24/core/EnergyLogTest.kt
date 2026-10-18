package com.novacamera.s24.core
import org.junit.Assert.*
import org.junit.Test
class EnergyLogTest {
    @Test fun s24ComEscalaIncertaNaoApresentaPotenciaComoValidada() {
        val s=EnergyLog.Sample(0,"Foto",-1442,4020,38f,70,false,0,.5f,
            averageRaw=-1510,chargeCounterRaw=3100000)
        assertNull(s.powerMw)
        assertEquals(5.79684,s.nominalPowerMw!!,.00001)
        val r=EnergyLog.report(listOf(s,s.copy(tMs=60000,chargeCounterRaw=3080000)))
        assertTrue(r.contains("NÃO CONFIRMADA"));assertTrue(r.contains("Δcarga=20000"))
        assertTrue(r.contains("média bruta=-1510"));assertFalse(r.contains("em média (máx."))
    }

    private fun s(raw:Long?,charging:Boolean=false)=EnergyLog.Sample(1,"controle",raw,4000,30f,90,charging,0,.3f,unitConfirmed=true)
    @Test fun correnteDaApiNuncaEReescaladaPelaMagnitude() {
        assertEquals(9999L,EnergyLog.normalizeUa(-9999))
        assertEquals(10000L,EnergyLog.normalizeUa(10000))
        assertEquals(.004,s(-1).powerMw!!,.000001)
        assertEquals(39.996,s(-9999).powerMw!!,.000001)
        // Valores altos anteriores não passam pela heurística: 3.5A/4V =14W.
        assertEquals(14000.0,s(-3500000).powerMw!!,.000001)
    }
    @Test fun indisponivelOuConectadoNaoViraConsumoZero() {
        for(raw in listOf(0L,Long.MIN_VALUE,Int.MIN_VALUE.toLong(),Long.MAX_VALUE))assertNull(s(raw).powerMw)
        assertNull(s(null).powerMw);assertNull(s(-1000000,true).powerMw)
        assertNull(s(-1000000).copy(voltageMv=0).powerMw)
    }
    @Test fun diagnosticoExplicaUnidadeBrutoSinalEDenominadorReal() {
        val text=EnergyLog.report(listOf(s(-3500000),s(-1000,true).copy(tMs=10001)))
        assertTrue(text.contains("n válido=1/2"));assertTrue(text.contains("bruto=-3500000"))
        assertTrue(text.contains("unidade=µA"));assertTrue(text.contains("conectado=true"))
    }
}
