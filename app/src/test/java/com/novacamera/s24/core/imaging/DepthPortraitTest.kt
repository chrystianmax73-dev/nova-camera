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

}

class DepthPortraitPlanTest {
    private val w = 120; private val h = 160
    private fun plant(x: Int, y: Int) = (x - 60) * (x - 60) / 900.0 + (y - 80) * (y - 80) / 1600.0 <= 1.0

    /** Planta perto (0,75..1), parede e chão no meio (0,45..0,65), portão longe (0..0,1). */
    private fun scene() = Plane(w, h, FloatArray(w * h) { i ->
        val x = i % w; val y = i / w
        when {
            plant(x, y) -> 0.75f + 0.25f * ((x * 7 + y * 3) % 10) / 10f   // folhas em várias distâncias
            y < 30 -> 0.05f                                                 // portão
            else -> 0.45f + 0.2f * y / h                                    // chão/parede
        }
    })

    @Test fun objectIsWholeAndFloorIsNotSubject() {
        val plan = DepthPortrait.plan(DepthPortrait.normalize(scene()), null)
        var inPlant = 0; var plantN = 0; var floorSubject = 0; var floorN = 0
        for (y in 0 until h) for (x in 0 until w) {
            val sub = plan.subject[x, y] >= 0.5f
            if (plant(x, y)) { plantN++; if (sub) inPlant++ } else if (y >= 30) { floorN++; if (sub) floorSubject++ }
        }
        assertTrue("planta inteira: $inPlant/$plantN", inPlant > plantN * 0.95)
        assertTrue("chão fora do assunto: $floorSubject/$floorN", floorSubject < floorN * 0.03)
        assertTrue("chão desfoca: ${plan.blur[10, 150]}", plan.blur[10, 150] > 0.25f)
        assertTrue("portão desfoca mais: ${plan.blur[10, 5]}", plan.blur[10, 5] > plan.blur[10, 150])
        assertEquals(0f, plan.blur[60, 80], 1e-4f)
    }

    @Test fun personKeepsHeldObjectAtSameDepth() {
        val depth = Plane(w, h, FloatArray(w * h) { i ->
            val x = i % w; val y = i / w
            if (x in 40..70 && y in 40..150) 0.8f else if (x in 71..80 && y in 30..60) 0.82f else 0.2f
        })
        val person = Plane(w, h, FloatArray(w * h) { i -> val x = i % w; val y = i / w; if (x in 40..70 && y in 40..150) 1f else 0f })
        val plan = DepthPortrait.plan(DepthPortrait.normalize(depth), person)
        assertTrue("objeto na mão entra no assunto: ${plan.subject[75, 45]}", plan.subject[75, 45] > 0.5f)
        assertTrue("fundo fora: ${plan.subject[100, 100]}", plan.subject[100, 100] < 0.5f)
    }
}
