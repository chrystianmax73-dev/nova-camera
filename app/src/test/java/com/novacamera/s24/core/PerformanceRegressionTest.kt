package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class PerformanceRegressionTest {
    @Test fun pausedSessionIsNotCountedAsThousandsOfDroppedFrames() {
        val t=ZoomTelemetry()
        t.frame(1_000_000_000L,1f,"5",1)
        t.breakStream()
        t.frame(1_601_000_000_000L,1f,"5",1_600_001)
        assertEquals(0,t.droppedFrames)
        t.frame(1_601_100_000_000L,1f,"5",1_600_101)
        assertTrue(t.droppedFrames>=1)
    }
    @Test fun lateZoomCallbackCannotApplyOldSessionTarget() {
        val gate = ZoomRequestGate()
        assertEquals(10f, gate.offer(10f)!!, 0f)
        val old = gate.generation
        gate.offer(5f)
        gate.reset()
        assertEquals(1f, gate.offer(1f)!!, 0f)
        gate.offer(3f)
        assertNull(gate.completed(old))
        // Callback antigo não desbloqueia nem consome a requisição da sessão atual.
        assertEquals(3f, gate.completed(gate.generation)!!, 0f)
    }

    @Test fun photoTeleSelectionUsesNativeWhileVideoKeepsLogicalPolicy() {
        val main = LensOption("main", 1f, 1f, 30f)
        val tele = LensOption("tele", 5f, 1f, 10f)
        val exposed = listOf(main, tele)
        assertEquals("tele", LensSelection.choose(exposed, "main", "main", 10f, true)!!.cameraId)
        assertEquals(2f, LensSelection.choose(exposed, "main", "main", 10f, true)!!.zoomOnLens, 0f)
        assertEquals("main", LensSelection.choose(exposed, "main", "main", 10f)!!.cameraId)
        assertEquals("tele", LensSelection.choose(listOf(main.copy(logical = true), tele), "main", "main", 10f, true)!!.cameraId)
        assertEquals("main", LensSelection.choose(exposed, "tele", "main", 1f, true)!!.cameraId)
    }

    @Test fun aiCostPolicyKeepsFullResolutionCpuAndHotDeviceOnFastFallback() {
        assertFalse(DenoisePolicy.canRun(12_000_000, false, 1_000_000_000))
        assertTrue(DenoisePolicy.canRun(12_000_000, true, 1_000_000_000))
        assertFalse(DenoisePolicy.canRun(12_000_000, true, 20_000_000))
        assertFalse(DenoisePolicy.allowed(DeviceHeat(ThermalPolicy.MODERATE, 40f, false, false, 80)))
        assertFalse(DenoisePolicy.allowed(DeviceHeat(ThermalPolicy.NONE, 30f, false, true, 80)))
        assertTrue(DenoisePolicy.allowed(DeviceHeat(ThermalPolicy.NONE, 30f, false, false, 80)))
        assertFalse(DenoisePolicy.projectedFits(100, 180, 300))
        assertTrue(DenoisePolicy.projectedFits(20, 180, 300))
        assertTrue(DenoisePolicy.expired(8_000, 5_000))
    }

    /**
     * 12 MP = 192 blocos de 256 px. Com 40–80 ms por bloco (estimativa, não medição no S24 Ultra)
     * a foto não cabe no limite de primeiro plano, mas cabe no de segundo plano.
     */
    @Test fun backgroundBudgetFitsFullPhotoThatForegroundCannot() {
        val tiles = 16 * 12
        for (tileMs in listOf(40L, 80L)) {
            assertFalse(DenoisePolicy.remainingFits(1_000, 200, tileMs, tiles - 3))
            assertTrue(DenoisePolicy.remainingFits(1_000, 200, tileMs, tiles - 3, DenoisePolicy.BACKGROUND))
        }
        // Mesmo em segundo plano há teto: 400 ms por bloco (CPU ou GPU estrangulada) não roda.
        assertFalse(DenoisePolicy.remainingFits(1_000, 200, 400, tiles - 3, DenoisePolicy.BACKGROUND))
        assertTrue(DenoisePolicy.expired(60_000, 1_000, DenoisePolicy.BACKGROUND))
        assertFalse(DenoisePolicy.expired(8_000, 6_000, DenoisePolicy.BACKGROUND))
    }

    @Test fun longNightExposureDoesNotQueueUnboundedBurst() {
        assertEquals(2, CaptureDecision.nightFrames(1600, 500_000_000, 6).first)
        assertEquals(1, CaptureDecision.nightFrames(1600, 2_000_000_000, 6).first)
        assertEquals(6, CaptureDecision.nightFrames(1600, 50_000_000, 6).first)
    }
}
