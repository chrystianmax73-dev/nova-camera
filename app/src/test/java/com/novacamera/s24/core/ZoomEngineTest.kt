package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomCurveTest {
    @Test
    fun photoTapReachesTenXWithinBudget() {
        val c = ZoomCurve(photo = true)
        val d = c.durationFor(1f, 10f)
        assertTrue("1→10× deve durar ≤ 320 ms, foi $d", d <= 320)
        c.animateTo(1f, 10f, 0)
        assertEquals(10f, c.valueAt(d), 0.001f)
        assertTrue(c.finished(d))
    }

    @Test
    fun videoIsSmootherButBounded() {
        val video = ZoomCurve(photo = false)
        val photo = ZoomCurve(photo = true)
        assertTrue(video.durationFor(1f, 5f) > photo.durationFor(1f, 5f))
        assertTrue(video.durationFor(0.6f, 30f) <= 900)
    }

    @Test
    fun curveRespondsImmediatelyAndIsMonotonic() {
        val c = ZoomCurve(photo = true)
        c.animateTo(1f, 5f, 0)
        val d = c.durationFor(1f, 5f)
        // Ease-out: no primeiro quarto do tempo já percorreu mais da metade do caminho (em log).
        val quarter = c.valueAt(d / 4)
        assertTrue(kotlin.math.ln(quarter) / kotlin.math.ln(5f) > 0.5f)
        var last = 1f
        for (t in 0..d step 8) { val v = c.valueAt(t); assertTrue(v >= last - 1e-4f); last = v }
    }

    @Test
    fun interruptionContinuesFromCurrentValue() {
        val c = ZoomCurve(photo = true)
        c.animateTo(1f, 10f, 0)
        val mid = c.valueAt(60)
        c.animateTo(mid, 3f, 60)
        assertEquals(mid, c.valueAt(60), 0.001f)
        assertEquals(3f, c.valueAt(60 + c.durationFor(mid, 3f)), 0.001f)
    }
}

class ZoomRequestGateTest {
    @Test
    fun neverStacksRequests() {
        val g = ZoomRequestGate()
        assertEquals(1.2f, g.offer(1.2f))
        assertNull(g.offer(1.5f))   // em voo: guarda
        assertNull(g.offer(2.0f))   // substitui o pendente, não empilha
        assertEquals(2.0f, g.completed())
        assertNull(g.completed())
    }

    @Test
    fun ignoresImperceptibleChanges() {
        val g = ZoomRequestGate()
        g.offer(3f); g.completed()
        assertNull(g.offer(3.001f))
        assertEquals(3.1f, g.offer(3.1f))
    }
}

class ZoomTelemetryTest {
    @Test
    fun measuresLatencyLensChangesAndDrops() {
        val t = ZoomTelemetry()
        t.setExpectedFps(30)
        t.command(5f, 1000)
        assertFalse(t.frame(0, 1f, "2", 1010))
        assertFalse(t.frame(33_333_333, 3f, "2", 1040))
        // Lacuna de ~3 quadros + troca para a tele.
        assertTrue(t.frame(133_333_333, 5f, "4", 1140))
        assertEquals(140L, t.lastLatencyMs)
        assertEquals("4", t.lastPhysicalId)
        assertTrue(t.droppedFrames >= 2)
        assertTrue(t.report().contains("lente física 2 → 4"))
    }
}
