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
            "retrato-indisponivel" to state(CaptureMode.RETRATO, ModeEngine.UNAVAILABLE,
                "O fabricante não libera o Retrato para outros apps nesta câmera. O Retrato próprio ainda está em desenvolvimento."),
        )
        for ((name, s) in cases) {
            activity.onCameraState(s)
            activity.onZoom(if (s.mode == CaptureMode.VIDEO) 2.4f else 1f)
            if (name == "video") activity.onRecording(RecordingPhase.RECORDING, 83)
            shadowOf(Looper.getMainLooper()).idle()
            val root = activity.window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1080, 2340)
            val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (name == "video") activity.onRecording(RecordingPhase.IDLE, 0)
        }
        assertTrue(File(out, "foto.png").length() > 0)
    }
}
