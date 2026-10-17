package com.novacamera.s24.ui

import android.Manifest
import android.net.Uri
import com.novacamera.s24.MainActivity
import com.novacamera.s24.camera.RecordingPhase
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VideoLifecycleTest {
    @Test fun finalizeDepoisDeDestruirATelaNaoUsaSomNemExecutorDeMiniaturasEncerrados() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        controller.pause().stop().destroy()
        assertTrue(activity.isDestroyed)
        // Antes, a URI levava a io.execute depois de io.shutdown: RejectedExecutionException.
        activity.onRecording(RecordingPhase.IDLE, 0)
        activity.onRecordingFinished(Uri.parse("content://media/external/video/media/123"), null)
    }
}
