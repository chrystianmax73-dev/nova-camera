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
    fun portraitDefaultsToNovaEngineEvenWithoutOem() {
        val s = ModeResolver.resolve(CaptureMode.RETRATO, DeviceModeFacts(false, false, null))
        assertEquals(ModeEngine.NOVA, s.engine)
        // Mesmo com a extensão disponível, o padrão é o motor próprio (o do fabricante suaviza a pele).
        assertEquals(ModeEngine.NOVA, ModeResolver.resolve(CaptureMode.RETRATO, DeviceModeFacts(true, false, null)).engine)
        assertEquals(ModeEngine.OEM_EXTENSION,
            ModeResolver.resolve(CaptureMode.RETRATO, DeviceModeFacts(true, false, null), EnginePrefs(oemPortrait = true)).engine)
        // Preferir o fabricante sem a extensão disponível não deixa o modo indisponível.
        assertEquals(ModeEngine.NOVA,
            ModeResolver.resolve(CaptureMode.RETRATO, DeviceModeFacts(false, false, null), EnginePrefs(oemPortrait = true)).engine)
    }

    @Test
    fun nightUsesNovaMultiFrameUnlessOemPreferred() {
        assertEquals(ModeEngine.NOVA, ModeResolver.resolve(CaptureMode.NOITE, DeviceModeFacts(false, false, 10)).engine)
        assertEquals(ModeEngine.NOVA, ModeResolver.resolve(CaptureMode.NOITE, DeviceModeFacts(false, false, null)).engine)
        assertEquals(ModeEngine.OEM_EXTENSION,
            ModeResolver.resolve(CaptureMode.NOITE, DeviceModeFacts(false, true, 10), EnginePrefs(oemNight = true)).engine)
    }

    @Test
    fun unimplementedModesDoNotCapture() {
        assertFalse(ModeResolver.resolve(CaptureMode.PANORAMA, DeviceModeFacts(true, true, 10)).canCapture)
    }

    @Test
    fun cinemaIsPrimaryAndUsesNovaDepthVideo() {
        assertTrue(CaptureMode.VIDEO_RETRATO.primary)
        assertEquals("CINEMA", CaptureMode.VIDEO_RETRATO.label)
        assertEquals(ModeEngine.NOVA, ModeResolver.resolve(CaptureMode.VIDEO_RETRATO, DeviceModeFacts(false, false, null)).engine)
    }

    @Test
    fun lowLightVideoFpsAlsoAt60() {
        val s24 = listOf(15 to 15, 24 to 24, 15 to 30, 30 to 30, 15 to 60, 60 to 60)
        assertEquals(15 to 30, ModeResolver.autoVideoFps(s24))
        assertEquals(15 to 60, ModeResolver.autoVideoFps(s24, 60))
        assertEquals(null, ModeResolver.autoVideoFps(s24, 24))
        assertEquals(null, ModeResolver.autoVideoFps(listOf(30 to 30, 60 to 60), 60))
    }

    @Test
    fun hdrBracketRespectsStepAndRange() {
        // Passo 1/3 EV, faixa ±12 (±4 EV): 0, −2 EV, +1 EV.
        assertEquals(listOf(0, -6, 3), ModeResolver.hdrBracket(1, 3, -12, 12))
        // Passo 1/2 EV, faixa só até +0,5 EV: o quadro claro ficaria pequeno demais e é descartado.
        assertEquals(listOf(0, -4), ModeResolver.hdrBracket(1, 2, -4, 1))
        // Sem compensação disponível: um quadro só.
        assertEquals(listOf(0), ModeResolver.hdrBracket(1, 3, 0, 0))
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

class PhotoSizesTest {
    // Tamanhos no estilo de um sensor de 200 MP com saída agrupada de 12 MP e 50 MP.
    private val regular = listOf(4000 to 3000, 4000 to 2252, 3000 to 3000, 1920 to 1080, 1440 to 1080, 640 to 480)
    private val highRes = listOf(8160 to 6120, 8160 to 4592)

    @Test
    fun listsOnlyStandardAndLargerPerAspect() {
        val o = PhotoSizes.options(regular, highRes, 4f / 3f)
        assertEquals(listOf("8160x6120", "4000x3000"), o.map { it.key })
        assertTrue(o[0].highResolution)
        assertEquals("50 MP", o[0].label)
        assertEquals("12 MP", o[1].label)
        val wide = PhotoSizes.options(regular, highRes, 16f / 9f)
        assertEquals(listOf("8160x4592", "4000x2252"), wide.map { it.key })
    }

    @Test
    fun frontCameraWithSingleSizeHasOneOption() {
        val o = PhotoSizes.options(listOf(3648 to 2736, 1920 to 1440), emptyList(), 4f / 3f)
        assertEquals(1, o.size)
        assertEquals("10 MP", o[0].label)
    }

    @Test
    fun pickKeepsMegapixelClassAcrossAspectRatios() {
        val four3 = PhotoSizes.options(regular, highRes, 4f / 3f)
        val wide = PhotoSizes.options(regular, highRes, 16f / 9f)
        val chosen = four3[0]
        assertEquals("8160x4592", PhotoSizes.pick(wide, chosen.pixels)!!.key)
        assertEquals("4000x3000", PhotoSizes.pick(four3, null)!!.key)
    }

    @Test
    fun maximumResolutionOnlySizesAreReportedAsBlocked() {
        val o = PhotoSizes.options(regular, emptyList(), 4f / 3f)
        val blocked = PhotoSizes.blocked(o, listOf(16320 to 12240, 8160 to 6120), 4f / 3f)
        assertEquals(listOf("16320x12240", "8160x6120"), blocked.map { it.key })
        assertEquals("200 MP", blocked[0].label)
    }
}

class TimelapsePlanTest {
    @Test
    fun framesAndSpeedup() {
        val p = TimelapsePlan(intervalMs = 1000, maxCaptureSeconds = 300)
        assertEquals(30, p.speedup)
        assertEquals(61, p.framesAfter(60_000))
        assertEquals(61f / 30f, p.videoSecondsAfter(60_000), 1e-4f)
        assertFalse(p.finished(299_000)); assertTrue(p.finished(300_000))
        assertFalse(TimelapsePlan(1000, 0).finished(10_000_000))
    }

    @Test
    fun dueOncePerInterval() {
        val p = TimelapsePlan(intervalMs = 500, maxCaptureSeconds = 0)
        assertTrue(p.due(0, null))
        assertFalse(p.due(300, 0))
        assertTrue(p.due(490, 0))
    }

    @Test
    fun outputSizeIsEncoderFriendly() {
        assertEquals(1920 to 1072, TimelapsePlan.outputSize(1920, 1080))
        assertEquals(1072 to 1920, TimelapsePlan.outputSize(1080, 1920))
        assertEquals(1440 to 1920, TimelapsePlan.outputSize(3000, 4000))
        assertEquals("0,5 s", TimelapsePlan.intervalLabel(500))
    }
}

class CaptureDecisionTest {
    @Test
    fun nightBurstOnlyInRealLowLight() {
        assertEquals(1, CaptureDecision.nightFrames(200, 10_000_000L, 5).first)
        assertEquals(5, CaptureDecision.nightFrames(1600, 66_000_000L, 5).first)
        assertEquals(5, CaptureDecision.nightFrames(null, null, 5).first)
        assertEquals(1, CaptureDecision.nightFrames(3200, 100_000_000L, 1).first)
    }

    @Test
    fun hdrBracketOnlyWhatTheSceneNeeds() {
        assertEquals(listOf(0), CaptureDecision.hdrSteps(listOf(0, -6, 3), 0.001f, 0.01f).first)
        assertEquals(listOf(0, -6), CaptureDecision.hdrSteps(listOf(0, -6, 3), 0.05f, 0.01f).first)
        assertEquals(listOf(0, -6, 3), CaptureDecision.hdrSteps(listOf(0, -6, 3), 0.05f, 0.08f).first)
    }
}
