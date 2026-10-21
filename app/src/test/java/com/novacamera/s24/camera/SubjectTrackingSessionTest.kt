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
        val s=SubjectTrackingSession(preview,{true},{emptyList()},{results++},{stopped++})
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
        val s=SubjectTrackingSession(preview,{false},{emptyList()},{results++},{reason=it})
        s.select(30f,30f)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(s.active); assertEquals(0,results); assertTrue(reason.isNotBlank())
        s.close()
    }
    @Test fun toqueNoRostoSegueORostoDoSensorEPassaAAparenciaQuandoSome() {
        val preview=PreviewView(RuntimeEnvironment.getApplication())
        preview.layout(0,0,1000,1000)
        var faces=listOf(com.novacamera.s24.core.FaceLock.Box(400f,400f,500f,500f))
        val got=ArrayList<com.novacamera.s24.core.SubjectTracker.Result>(); var reason=""
        val s=SubjectTrackingSession(preview,{true},{faces},{got+=it},{reason=it})
        s.select(450f,520f) // queixo
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        assertEquals(SubjectTrackingSession.FACE,got.last().reason)
        assertEquals(.45,got.last().x.toDouble(),.001)
        faces=listOf(com.novacamera.s24.core.FaceLock.Box(440f,400f,540f,500f))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        assertEquals(.49,got.last().x.toDouble(),.001)
        val count=got.size
        faces=emptyList()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertTrue(s.active); assertEquals(count,got.size) // rosto sumiu há pouco: mantém a trava, sem inventar alvo
        // Sem prévia no teste, a aparência não lê quadros e encerra: prova que a passagem aconteceu.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertFalse(s.active); assertTrue(reason.isNotBlank())
        s.close()
    }
}
