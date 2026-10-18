package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadCpuTest {
    @Test fun leNomeComEspacoEParentesesETicks() {
        // Campos reais de /proc/<tid>/stat: utime=14, stime=15.
        val line = "4242 (Camera (x) 2) S 1 2 3 4 5 6 7 8 9 10 120 30 0 0 20 0 1 0 500 0 0"
        assertEquals("Camera (x) 2" to 150L, ThreadCpu.parse(line))
        assertEquals(null, ThreadCpu.parse("lixo"))
    }

    @Test fun agrupaPoolsENumeros() {
        assertEquals("nova-proc-#", ThreadCpu.group("nova-proc-3"))
        assertEquals("Binder:#_#", ThreadCpu.group("Binder:1234_5"))
    }

    @Test fun somaPorAtividadeEDescartaTrocaDeAtividade() {
        ThreadCpu.resetForTest()
        ThreadCpu.add("RETRATO", 0, mapOf(1 to ("RenderThread" to 100L), 2 to ("CameraX-core" to 50L)))
        // 10 s depois: RenderThread +500 ticks (5 s de CPU = 50%), CameraX +100 (10%).
        ThreadCpu.add("RETRATO", 10_000, mapOf(1 to ("RenderThread" to 600L), 2 to ("CameraX-core" to 150L)))
        ThreadCpu.add("FOTO", 20_000, mapOf(1 to ("RenderThread" to 5_000L))) // mudou de atividade: não conta
        val r = ThreadCpu.report()
        assertTrue(r, r.contains("RETRATO (10 s): total 60.0%"))
        assertTrue(r, r.contains("RenderThread") && r.contains("50.0%"))
        assertTrue(r, !r.contains("FOTO"))
    }
}
