package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraMathTest {
    @Test
    fun orientationSnapsWithHysteresis() {
        assertEquals(90, CameraMath.snapOrientation(88, 0))
        // 50° fica perto da diagonal: mantém o estado atual.
        assertEquals(0, CameraMath.snapOrientation(50, 0))
        assertEquals(0, CameraMath.snapOrientation(355, 270))
        assertEquals(270, CameraMath.snapOrientation(-1, 270))
    }

    @Test
    fun surfaceRotationMatchesSensorConvention() {
        assertEquals(0, CameraMath.surfaceRotationFor(0))
        assertEquals(3, CameraMath.surfaceRotationFor(90))
        assertEquals(1, CameraMath.surfaceRotationFor(270))
    }

    @Test
    fun exposureDragFollowsUserHorizontal() {
        assertEquals(10f, CameraMath.userHorizontalDelta(10f, 0f, 0), 0f)
        // Aparelho girado 90° no sentido horário: arrastar para a "direita" do usuário é subir na tela.
        assertEquals(10f, CameraMath.userHorizontalDelta(0f, -10f, 90), 0f)
        assertEquals(10f, CameraMath.userHorizontalDelta(0f, 10f, 270), 0f)
    }

    @Test
    fun evConversionRespectsStepAndRange() {
        assertEquals(4, CameraMath.indexForEv(0.66, 1, 6, -24, 24))
        assertEquals(24, CameraMath.indexForEv(9.0, 1, 6, -24, 24))
        assertEquals(-1.0, CameraMath.evForIndex(-3, 1, 3), 1e-9)
        assertEquals("+0,7", CameraMath.formatEv(0.6667))
        assertEquals("−1,3", CameraMath.formatEv(-1.3333))
        assertEquals("±0", CameraMath.formatEv(0.01))
    }

    @Test
    fun zoomStepConvergesLogarithmically() {
        var z = 1f
        repeat(60) { z = CameraMath.zoomStep(z, 10f, 0.2f) }
        assertEquals(10f, z, 0f)
        val half = CameraMath.zoomStep(1f, 4f, 0.5f)
        assertEquals(2f, half, 0.001f)
    }

    @Test
    fun zoomBarMappingIsPiecewiseLog() {
        val stops = listOf(0.6f, 1f, 3f, 5f, 10f)
        assertEquals(0.6f, CameraMath.zoomForFraction(stops, 0f), 0.001f)
        assertEquals(1f, CameraMath.zoomForFraction(stops, 0.25f), 0.001f)
        assertEquals(10f, CameraMath.zoomForFraction(stops, 1f), 0.001f)
        val f = CameraMath.fractionForZoom(stops, 2f)
        assertEquals(2f, CameraMath.zoomForFraction(stops, f), 0.001f)
    }

    @Test
    fun proLabels() {
        assertEquals("1/125", CameraMath.shutterLabel(1_000_000_000L / 125))
        assertEquals("2 s", CameraMath.shutterLabel(2_000_000_000L))
        assertEquals("∞", CameraMath.focusLabel(0f))
        assertEquals("50 cm", CameraMath.focusLabel(2f))
        assertEquals(listOf(100, 125, 160, 200), CameraMath.isoStops(100, 200))
        val shutters = CameraMath.shutterStops(1_000_000L, 100_000_000L)
        assertEquals(1_000_000_000L / 1000, shutters.first())
        assertEquals(1_000_000_000L / 10, shutters.last())
        assertEquals("01:05", CameraMath.formatDuration(65))
    }
}

class LensSelectionTest {
    private val logical = LensOption("0", 1f, 0.6f, 10f)
    private val wide = LensOption("2", 0.6f, 1f, 2f)
    private val tele = LensOption("3", 3f, 1f, 3.4f)

    @Test
    fun prefersCurrentLogicalCameraWhenItCovers() {
        val c = LensSelection.choose(listOf(logical, wide, tele), "0", "0", 0.6f)!!
        assertEquals("0", c.cameraId)
        assertEquals(0.6f, c.zoomOnLens, 0.001f)
    }

    @Test
    fun switchesToUltraWideWhenMainCannotGoBelowOne() {
        val main = LensOption("0", 1f, 1f, 10f)
        val c = LensSelection.choose(listOf(main, wide), "0", "0", 0.6f)!!
        assertEquals("2", c.cameraId)
        assertEquals(1f, c.zoomOnLens, 0.001f)
    }

    @Test
    fun picksNativeTelephotoOverDigitalWhenSwitching() {
        val main = LensOption("0", 1f, 1f, 2f)
        val c = LensSelection.choose(listOf(main, tele), "0", "0", 3f)!!
        assertEquals("3", c.cameraId)
    }

    @Test
    fun unreachableStopsAreDropped() {
        val stops = LensSelection.reachableStops(listOf(logical), listOf(0.6f, 1f, 10f, 20f, 30f))
        assertEquals(listOf(0.6f, 1f, 10f), stops)
    }
}

class ModeResolverTest {
    @Test
    fun portraitWithoutOemIsHonestlyUnavailable() {
        val s = ModeResolver.resolve(CaptureMode.RETRATO, DeviceModeFacts(false, false, null))
        assertFalse(s.canCapture)
    }

    @Test
    fun nightFallsBackToExtendedExposureOnlyWhenSensorAllows() {
        val basic = ModeResolver.resolve(CaptureMode.NOITE, DeviceModeFacts(false, false, 10))
        assertEquals(ModeEngine.NOVA_BASIC, basic.engine)
        val none = ModeResolver.resolve(CaptureMode.NOITE, DeviceModeFacts(false, false, null))
        assertEquals(ModeEngine.UNAVAILABLE, none.engine)
        val oem = ModeResolver.resolve(CaptureMode.NOITE, DeviceModeFacts(false, true, 10))
        assertEquals(ModeEngine.OEM_EXTENSION, oem.engine)
    }

    @Test
    fun extendedExposureRangeSelection() {
        assertEquals(8, ModeResolver.extendedExposureMinFps(listOf(15 to 15, 8 to 30, 30 to 30, 10 to 24)))
        assertNull(ModeResolver.extendedExposureMinFps(listOf(15 to 30, 30 to 30, 7 to 60)))
    }
}

class ThermalPolicyTest {
    private fun heat(status: Int, charging: Boolean = false, powerSave: Boolean = false) =
        DeviceHeat(status, 38f, charging, powerSave, 50)

    @Test
    fun moderateHeatIsSilent() {
        assertFalse(ThermalPolicy.showBadge(heat(ThermalPolicy.MODERATE, charging = true)))
        assertFalse(ThermalPolicy.limitVideo(heat(ThermalPolicy.MODERATE)))
    }

    @Test
    fun severeHeatShowsChargingContext() {
        val h = heat(ThermalPolicy.SEVERE, charging = true)
        assertTrue(ThermalPolicy.showBadge(h))
        assertEquals("Aparelho quente · carregando", ThermalPolicy.badgeText(h))
        assertTrue(ThermalPolicy.limitVideo(h))
        assertFalse(ThermalPolicy.stopRecording(h))
        assertTrue(ThermalPolicy.stopRecording(heat(ThermalPolicy.CRITICAL)))
    }

    @Test
    fun powerSaveLimitsVideoWithoutAlarm() {
        val h = heat(ThermalPolicy.NONE, powerSave = true)
        assertTrue(ThermalPolicy.limitVideo(h))
        assertFalse(ThermalPolicy.showBadge(h))
    }
}

class ProMeterTest {
    @Test
    fun rulerOpensAtCurrentAutoValue() {
        val isos = CameraMath.isoStops(50, 3200).map { it.toDouble() }
        assertEquals(1600.0, isos[CameraMath.nearestLogIndex(isos, 1500.0)], 0.0)
    }

    @Test
    fun meterExplainsBlackPreview() {
        // O que acontecia na 0.4: auto em ISO 1600, régua saltava para ISO 50 com o mesmo tempo.
        assertEquals(-5.0, CameraMath.meterEv(50, 33_333_333L, 1600, 33_333_333L), 0.01)
        assertEquals(1.0, CameraMath.meterEv(100, 20_000_000L, 100, 10_000_000L), 0.01)
    }
}
