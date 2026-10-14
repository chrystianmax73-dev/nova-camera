package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class PerformanceRegressionTest {
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

    @Test fun photoTeleSelectionPreservesLogicalCameraAndVideoPolicy() {
        val main = LensOption("main", 1f, 1f, 30f)
        val tele = LensOption("tele", 5f, 1f, 10f)
        val exposed = listOf(main, tele)
        assertEquals("tele", LensSelection.choose(exposed, "main", "main", 10f, true)!!.cameraId)
        assertEquals(2f, LensSelection.choose(exposed, "main", "main", 10f, true)!!.zoomOnLens, 0f)
        assertEquals("main", LensSelection.choose(exposed, "main", "main", 10f)!!.cameraId)
        assertEquals("main", LensSelection.choose(listOf(main.copy(logical = true), tele), "main", "main", 10f, true)!!.cameraId)
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

    @Test fun longNightExposureDoesNotQueueUnboundedBurst() {
        assertEquals(2, CaptureDecision.nightFrames(1600, 500_000_000, 6).first)
        assertEquals(1, CaptureDecision.nightFrames(1600, 2_000_000_000, 6).first)
        assertEquals(6, CaptureDecision.nightFrames(1600, 50_000_000, 6).first)
    }
}
