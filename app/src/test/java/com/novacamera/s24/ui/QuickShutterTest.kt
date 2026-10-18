package com.novacamera.s24.ui

import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class QuickShutterTest {
    private class Calls : ShutterButton.QuickListener {
        var holds = 0; var locks = 0; var releases = 0; var cancels = 0
        val zooms=mutableListOf<Float>()
        override fun onZoom(deltaDp:Float) { zooms+=deltaDp }
        override fun onHold(): Boolean { holds++; return true }
        override fun onLock() { locks++ }
        override fun onRelease() { releases++ }
        override fun onCancel() { cancels++ }
    }
    private fun touch(b: ShutterButton, action: Int, x: Float = 40f, y:Float=40f) {
        val e = MotionEvent.obtain(0, 0, action, x, y, 0)
        b.onTouchEvent(e)
        e.recycle()
    }
    private fun hold() = shadowOf(Looper.getMainLooper()).idleFor(
        ViewConfiguration.getLongPressTimeout().toLong() + 1, TimeUnit.MILLISECONDS)

    @Test fun arrastarADireitaAntesDoTimeoutIniciaEBloqueiaUmaVezSemFotografar() {
        val b=ShutterButton(RuntimeEnvironment.getApplication()).apply { quickEnabled=true }
        val calls=Calls();b.quickListener=calls
        var photos=0;b.setOnClickListener { photos++ }
        touch(b,MotionEvent.ACTION_DOWN)
        touch(b,MotionEvent.ACTION_MOVE,1000f)
        touch(b,MotionEvent.ACTION_MOVE,1100f)
        touch(b,MotionEvent.ACTION_UP,1100f);hold()
        assertEquals(1,calls.holds);assertEquals(1,calls.locks);assertEquals(0,photos)
    }
    @Test fun subirEDescerControlamZoomSemBloquearPorArrasteDiagonal() {
        val b=ShutterButton(RuntimeEnvironment.getApplication()).apply { quickEnabled=true }
        val calls=Calls();b.quickListener=calls
        touch(b,MotionEvent.ACTION_DOWN);hold()
        touch(b,MotionEvent.ACTION_MOVE,40f,-1000f)
        touch(b,MotionEvent.ACTION_MOVE,40f,-800f)
        touch(b,MotionEvent.ACTION_MOVE,1000f,-700f)
        touch(b,MotionEvent.ACTION_UP,1000f,-700f)
        assertTrue(calls.zooms[0]>0);assertTrue(calls.zooms[1]<0)
        assertEquals(0,calls.locks);assertEquals(1,calls.releases)
    }
    @Test fun arrasteEsquerdoNaoDisparaNemBloqueiaEPressaoRecusadaNaoViraFoto() {
        val b=ShutterButton(RuntimeEnvironment.getApplication()).apply { quickEnabled=true }
        val calls=Calls();b.quickListener=calls
        touch(b,MotionEvent.ACTION_DOWN);touch(b,MotionEvent.ACTION_MOVE,-1000f);touch(b,MotionEvent.ACTION_UP,-1000f);hold()
        assertEquals(0,calls.holds);assertEquals(0,calls.locks)
        var photos=0;b.setOnClickListener { photos++ }
        b.quickListener=object:ShutterButton.QuickListener {
            override fun onHold()=false
            override fun onLock() { fail("não pode bloquear sem aceitar gesto") }
            override fun onRelease() {}
            override fun onCancel() {}
            override fun onZoom(deltaDp:Float) { fail("não pode aplicar zoom sem aceitar gesto") }
        }
        touch(b,MotionEvent.ACTION_DOWN);hold();touch(b,MotionEvent.ACTION_MOVE,1000f);touch(b,MotionEvent.ACTION_UP,1000f)
        assertEquals(0,photos)
    }
    @Test fun toqueSimplesContinuaFotografandoSemIniciarVideo() {
        val b = ShutterButton(RuntimeEnvironment.getApplication()).apply { quickEnabled = true }
        val calls = Calls(); b.quickListener = calls
        var photos = 0; b.setOnClickListener { photos++ }
        touch(b, MotionEvent.ACTION_DOWN)
        touch(b, MotionEvent.ACTION_UP)
        hold()
        assertEquals(1, photos)
        assertEquals(0, calls.holds)
    }
    @Test fun gestoContinuaMesmoQuandoOBindMudaOBotaoParaVideo() {
        val b = ShutterButton(RuntimeEnvironment.getApplication()).apply { quickEnabled = true }
        val calls = Calls(); b.quickListener = calls
        touch(b, MotionEvent.ACTION_DOWN); hold()
        b.quickEnabled = false
        // Distância em pixels suficiente também com a densidade do runner.
        touch(b, MotionEvent.ACTION_MOVE, 1000f)
        touch(b, MotionEvent.ACTION_MOVE, 1100f)
        touch(b, MotionEvent.ACTION_UP, 1100f)
        assertEquals(1, calls.holds)
        assertEquals(1, calls.locks)
        assertEquals(1, calls.releases)
    }
    @Test fun cancelamentoRemovePressaoPendenteESegundoDedoInterrompeVideo() {
        val b = ShutterButton(RuntimeEnvironment.getApplication()).apply { quickEnabled = true }
        val calls = Calls(); b.quickListener = calls
        touch(b, MotionEvent.ACTION_DOWN); touch(b, MotionEvent.ACTION_CANCEL); hold()
        assertEquals(0, calls.holds)
        touch(b, MotionEvent.ACTION_DOWN); hold()
        touch(b, MotionEvent.ACTION_POINTER_DOWN)
        assertEquals(1, calls.cancels)
    }
}
