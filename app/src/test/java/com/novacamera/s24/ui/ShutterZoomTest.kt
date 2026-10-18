package com.novacamera.s24.ui

import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShutterZoomTest {
    private class Spy : ShutterButton.QuickListener {
        var zoom = 0f; var holds = 0; var clicks = 0
        override fun onHold(): Boolean { holds++; return true }
        override fun onLock() {}
        override fun onRelease() {}
        override fun onCancel() {}
        override fun onZoom(deltaDp: Float) { zoom += deltaDp }
    }

    private fun drag(quick: Boolean, dyPx: Float): Pair<Spy, Int> {
        val b = ShutterButton(RuntimeEnvironment.getApplication())
        b.layout(0, 0, 200, 200)
        val spy = Spy(); var clicks = 0
        b.quickListener = spy; b.quickEnabled = quick
        b.setOnClickListener { clicks++ }
        val t = SystemClock.uptimeMillis()
        b.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 100f, 0))
        for (i in 1..10) b.onTouchEvent(MotionEvent.obtain(t, t + i * 10L, MotionEvent.ACTION_MOVE, 100f, 100f + dyPx * i / 10, 0))
        b.onTouchEvent(MotionEvent.obtain(t, t + 120L, MotionEvent.ACTION_UP, 100f, 100f + dyPx, 0))
        return spy to clicks
    }

    @Test fun arrastarParaCimaAproximaSemFotografar() {
        for (quick in listOf(true, false)) {
            val (spy, clicks) = drag(quick, -300f)
            assertTrue("zoom positivo (quick=$quick)", spy.zoom > 0f)
            assertEquals("soltar depois de arrastar não fotografa (quick=$quick)", 0, clicks)
            assertEquals("não vira gravação (quick=$quick)", 0, spy.holds)
        }
    }

    @Test fun arrastarParaBaixoAfasta() {
        assertTrue(drag(true, 300f).first.zoom < 0f)
        assertTrue(drag(false, 300f).first.zoom < 0f)
    }

    @Test fun toqueSimplesContinuaFotografando() {
        val (spy, clicks) = drag(true, 0f)
        assertEquals(1, clicks); assertEquals(0f, spy.zoom)
    }
}
