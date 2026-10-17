package com.novacamera.s24.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.util.Range
import android.view.View
import com.novacamera.s24.MainActivity
import com.novacamera.s24.camera.CameraCapabilities
import com.novacamera.s24.camera.CameraState
import com.novacamera.s24.camera.RecordingPhase
import com.novacamera.s24.core.CaptureMode
import com.novacamera.s24.core.ModeEngine
import com.novacamera.s24.core.ModeSupport
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Teste de fumaça da tela principal: cria a Activity real, aplica o estado de cada modo
 * (sem câmera física) e desenha a interface. Salva PNGs em build/ui-renders para revisão visual.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MainScreenRenderTest {
    @Test
    fun seletorPermiteEscolherATaxaUnicaQuandoAtualFoiRecusada() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val settings = com.novacamera.s24.settings.NovaSettings(app)
        settings.videoQuality = com.novacamera.s24.settings.VideoQualityOption.UHD
        settings.videoFps = 60
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        activity.onCameraState(state(CaptureMode.VIDEO).copy(videoFps = 60,
            videoFpsOptions = listOf(30), videoCaptureProblem = "60 fps recusado"))
        MainActivity::class.java.getDeclaredMethod("cycleFps").apply { isAccessible = true }.invoke(activity)
        org.junit.Assert.assertEquals(30, settings.videoFps)
        org.junit.Assert.assertEquals(com.novacamera.s24.settings.VideoQualityOption.UHD, settings.videoQuality)
        controller.pause().stop().destroy()
    }

    @Test
    fun alternativaDeVideoSoMudaFpsComEscolhaExplicitaEPreservaUhd() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val settings = com.novacamera.s24.settings.NovaSettings(app)
        settings.videoQuality = com.novacamera.s24.settings.VideoQualityOption.UHD
        settings.videoFps = 60
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        activity.onCameraState(state(CaptureMode.VIDEO).copy(videoFps = 60,
            videoCaptureProblem = "4K/60 fps não aceito pela sessão", videoSuggestedFps = 30))
        val card = MainActivity::class.java.getDeclaredField("infoCard").apply { isAccessible = true }
            .get(activity) as android.widget.TextView
        val shutter = MainActivity::class.java.getDeclaredField("shutter").apply { isAccessible = true }
            .get(activity) as View
        org.junit.Assert.assertEquals(View.VISIBLE, card.visibility)
        org.junit.Assert.assertFalse(shutter.isEnabled)
        org.junit.Assert.assertEquals(60, settings.videoFps)
        card.performClick()
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            .getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        org.junit.Assert.assertEquals(60, settings.videoFps)
        card.performClick()
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            .getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        org.junit.Assert.assertEquals(30, settings.videoFps)
        org.junit.Assert.assertEquals(com.novacamera.s24.settings.VideoQualityOption.UHD, settings.videoQuality)
        controller.pause().stop().destroy()
    }

    private val caps = CameraCapabilities(
        cameraId = "0", isFront = false, manualSensor = true,
        isoRange = Range(50, 3200), exposureRangeNs = Range(100_000L, 500_000_000L),
        minFocusDistance = 10f, awbModes = listOf(1, 2, 3, 5, 6), facePriorityScene = true,
        aeLockAvailable = true, awbLockAvailable = true, aeFpsRanges = emptyList(), physicalCameraIds = setOf("2", "3", "4"),
    )

    private fun state(mode: CaptureMode, engine: ModeEngine = ModeEngine.STANDARD, note: String = "") = CameraState(
        mode = mode, support = ModeSupport(mode, engine, note), isFront = false, caps = caps,
        zoomStops = listOf(0.6f, 1f, 3f, 5f, 10f), zoomMin = 0.6f, zoomMax = 10f,
        evMin = -12, evMax = 12, evStepNum = 1, evStepDen = 6, evIndex = 0,
        hasFlash = true, oemHdrAvailable = false, oemHdrActive = false, extensionActive = false,
        canLockExposure = true, videoQualityLabel = if (mode.isVideo) "4K" else null, videoFps = 30,
        stabilization = true, screenFlash = false,
        photoResolution = if (mode.isVideo) null else android.util.Size(4000, 3000),
        photoSizeOptions = if (mode.isVideo || engine == ModeEngine.NOVA) emptyList() else listOf(
            com.novacamera.s24.core.SizeOption(8160, 6120, true), com.novacamera.s24.core.SizeOption(4000, 3000, false)),
        photoSize = if (mode.isVideo) null else com.novacamera.s24.core.SizeOption(4000, 3000, false),
        processing = when {
            engine != ModeEngine.NOVA -> null
            mode == CaptureMode.NOITE -> com.novacamera.s24.processing.JobKind.NIGHT
            mode == CaptureMode.RETRATO -> com.novacamera.s24.processing.JobKind.PORTRAIT
            else -> com.novacamera.s24.processing.JobKind.FOOD
        },
        rawAvailable = mode == CaptureMode.PRO, rawActive = false,
        videoQualityOptions = if (mode.isVideo) listOf(com.novacamera.s24.settings.VideoQualityOption.AUTO,
            com.novacamera.s24.settings.VideoQualityOption.UHD, com.novacamera.s24.settings.VideoQualityOption.FHD) else emptyList(),
        videoFpsOptions = if (mode.isVideo) listOf(24, 30, 60) else emptyList(),
    )

    @Test
    fun rendersEveryModeWithoutCrashing() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val out = File("build/ui-renders").apply { mkdirs() }

        val cases = listOf(
            "foto" to state(CaptureMode.FOTO),
            "video" to state(CaptureMode.VIDEO),
            "pro" to state(CaptureMode.PRO),
            "video-pro" to state(CaptureMode.VIDEO_PRO),
            "mais" to state(CaptureMode.FOTO),
            "retrato-nova" to state(CaptureMode.RETRATO, ModeEngine.NOVA,
                com.novacamera.s24.core.ModeResolver.resolve(CaptureMode.RETRATO, com.novacamera.s24.core.DeviceModeFacts(true, true, 10)).note),
            "noturno-processando" to state(CaptureMode.NOITE, ModeEngine.NOVA),
            "panoramica-indisponivel" to state(CaptureMode.PANORAMA, ModeEngine.UNAVAILABLE,
                com.novacamera.s24.core.ModeResolver.resolve(CaptureMode.PANORAMA, com.novacamera.s24.core.DeviceModeFacts(true, true, 10)).note),
        )
        for ((name, s) in cases) {
            activity.onCameraState(s)
            activity.onZoom(if (s.mode == CaptureMode.VIDEO) 2.4f else 1f)
            if (name == "video") activity.onRecording(RecordingPhase.RECORDING, 83)
            if (name == "video-pro") activity.onActiveLens("Tele 5×")
            if (name == "mais") MainActivity::class.java.getDeclaredMethod("showGrid").apply { isAccessible = true }.invoke(activity)
            if (name == "noturno-processando") MainActivity::class.java
                .getDeclaredMethod("onProcessingProgress", com.novacamera.s24.processing.JobProgress::class.java)
                .apply { isAccessible = true }
                .invoke(activity, com.novacamera.s24.processing.JobProgress(1L, com.novacamera.s24.processing.JobKind.NIGHT,
                    0.46f, "Reduzindo ruído", 1))
            shadowOf(Looper.getMainLooper()).idle()
            val root = activity.window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1080, 2340)
            val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (name == "video") activity.onRecording(RecordingPhase.IDLE, 0)
            if (name == "mais") MainActivity::class.java.getDeclaredMethod("hideGrid").apply { isAccessible = true }.invoke(activity)
        }
        assertTrue(File(out, "foto.png").length() > 0)
    }
}

