package com.novacamera.s24.camera

import android.os.Looper
import androidx.camera.view.PreviewView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@LooperMode(LooperMode.Mode.PAUSED)
class SubjectTrackingSessionTest {
    @Test fun cancelarOuFecharDescartaTickPendenteENaoRetomaAnalise() {
        val preview=PreviewView(RuntimeEnvironment.getApplication())
        var results=0; var stopped=0
        val s=SubjectTrackingSession(preview,{true},{results++},{stopped++})
        s.select(30f,30f); s.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertFalse(s.active); assertEquals(0,results); assertEquals(1,stopped)
        s.select(40f,40f); s.close(); s.select(50f,50f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertFalse(s.active); assertEquals(0,results); assertEquals(2,stopped)
    }
    @Test fun sessaoInvalidadaNaoAplicaResultadoNaCameraNova() {
        val preview=PreviewView(RuntimeEnvironment.getApplication())
        var results=0; var reason=""
        val s=SubjectTrackingSession(preview,{false},{results++},{reason=it})
        s.select(30f,30f)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(s.active); assertEquals(0,results); assertTrue(reason.isNotBlank())
        s.close()
    }
}
