package com.novacamera.s24.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthPortraitTest {
    private val w = 120; private val h = 160
    /** Cena: fundo longe (0,1) com chão ficando perto embaixo; assunto em (40..80, 50..130) a 0,8. */
    private fun depth() = Plane(w, h, FloatArray(w * h) { i ->
        val x = i % w; val y = i / w
        if (x in 40..80 && y in 50..130) 0.8f else 0.1f + 0.3f * y / h
    })
    private fun person() = Plane(w, h, FloatArray(w * h) { i -> val x = i % w; val y = i / w; if (x in 40..80 && y in 50..130) 1f else 0f })

    @Test fun normalizeSpansSceneRange() {
        val n = DepthPortrait.normalize(depth())
        assertEquals(1f, n[60, 90], 1e-4f)
        assertTrue(n[5, 5] < 0.05f)
    }

    @Test fun focusFollowsPersonOrNearestCentralSubject() {
        val n = DepthPortrait.normalize(depth())
        assertEquals(n[60, 90], DepthPortrait.focus(n, person()), 1e-4f)
        assertEquals("sem pessoa: objeto mais perto no centro", n[60, 90], DepthPortrait.focus(n, null), 0.02f)
    }

    @Test fun blurGrowsWithDepthDistanceAndBackgroundNextToSubjectIsNotSharp() {
        val n = DepthPortrait.normalize(depth())
        val b = DepthPortrait.blurMap(n, DepthPortrait.focus(n, person()), dilate = 2)
        assertEquals("assunto no foco", 0f, b[60, 90], 1e-4f)
        assertEquals("fundo longe", 1f, b[5, 5], 1e-4f)
        // Fundo colado no assunto herda o desfoque do vizinho (nada de franja nítida).
        assertTrue("borda: ${b[38, 90]}", b[38, 90] >= b[30, 90] - 1e-4f && b[38, 90] > 0.9f)
    }

    @Test fun inFocusMaskSelectsSubjectDepth() {
        val n = DepthPortrait.normalize(depth())
        val m = DepthPortrait.inFocus(n, DepthPortrait.focus(n, null))
        assertTrue(m[60, 90] > 0.99f)
        assertTrue(m[5, 5] < 0.01f)
    }
}
