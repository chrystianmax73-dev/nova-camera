package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShutterLogTest {
    @Test fun sensorDelayAcceptsOnlyPlausibleWindow() {
        val tap = 10_000_000_000L
        assertEquals(120L, ShutterLog.sensorDelayMs(tap + 120_000_000L, tap))
        // ZSL: quadro anterior ao toque.
        assertEquals(-40L, ShutterLog.sensorDelayMs(tap - 40_000_000L, tap))
        // Outra base de tempo (ex.: desde o boot sem sono profundo) não é comparável.
        assertNull(ShutterLog.sensorDelayMs(tap + 3_600_000_000_000L, tap))
        assertNull(ShutterLog.sensorDelayMs(0L, tap))
    }

    @Test fun reportGroupsByModeWithMedian() {
        ShutterLog.clear()
        ShutterLog.configured = "qualidade máxima"
        listOf(300L, 500L, 400L).forEach { ShutterLog.add(ShutterLog.Entry("qualidade máxima", it, it - 50, it + 200)) }
        ShutterLog.add(ShutterLog.Entry("sem atraso (ZSL)", 30, -20, 180))
        val r = ShutterLog.report()
        assertTrue(r, r.contains("qualidade máxima (3 fotos, mediana): toque→captura 400 ms, toque→exposição do sensor 350 ms, toque→imagem 600 ms"))
        assertTrue(r, r.contains("sem atraso (ZSL) (1 fotos, mediana): toque→captura 30 ms, toque→exposição do sensor -20 ms"))
        ShutterLog.clear()
    }
}

class CostLogTest {
    @Test fun reportsMedianAndHeatPerKind() {
        CostLog.clear()
        fun heat(t: Float, s: Int) = DeviceHeat(s, t, false, false, 80)
        CostLog.add(CostLog.Entry("Noturno", 4000, heat(30f, 0), heat(31f, 1)))
        CostLog.add(CostLog.Entry("Noturno", 6000, heat(31f, 1), heat(33f, 2)))
        CostLog.add(CostLog.Entry("Noturno", 5000, heat(32f, 1), heat(33f, 1)))
        val r = CostLog.report()
        assertTrue(r, r.contains("Noturno (3): mediana 5.0 s, máx. 6.0 s, bateria +1.3 °C em média, estado térmico máx. 2"))
        CostLog.clear()
    }
}
