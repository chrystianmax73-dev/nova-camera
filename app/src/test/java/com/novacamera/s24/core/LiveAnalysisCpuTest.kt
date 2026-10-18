package com.novacamera.s24.core

import org.junit.Assert.assertTrue
import org.junit.Test

class LiveAnalysisCpuTest {
    @Test fun relatorioMostraPorcentagemPorEtapa() {
        LiveAnalysisCpu.resetForTest()
        LiveAnalysisCpu.frame(0)
        LiveAnalysisCpu.frame(10_000)
        // 10 s de análise: profundidade gastou 3 s de CPU (30%) em 10 execuções.
        repeat(10) { LiveAnalysisCpu.add(LiveAnalysisCpu.Stage.DEPTH, 300, 650) }
        LiveAnalysisCpu.add(LiveAnalysisCpu.Stage.COPY, 500, 600)
        val r = LiveAnalysisCpu.report()
        assertTrue(r, r.contains("profundidade") && r.contains("30.0% de um núcleo") && r.contains("10 execuções"))
        assertTrue(r, r.contains("cópia/escala/giro") && r.contains("5.0%"))
    }
}
