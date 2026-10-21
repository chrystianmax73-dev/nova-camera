package com.novacamera.s24.core

import com.novacamera.s24.core.SceneDetect.Scene
import com.novacamera.s24.core.imaging.SceneTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneDetectTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun gruposPorIndice() {
        val g = SceneDetect.groups(listOf(151 to 0.4f, 282 to 0.3f, 963 to 0.1f, 985 to 0.05f, 958 to 0.5f))
        assertEquals(0.7f, g.pet, 1e-4f)
        assertEquals(0.1f, g.food, 1e-4f)   // feno (958) não é comida
        assertEquals(0.05f, g.plant, 1e-4f)
    }

    @Test fun folhagemVerdeViraPlanta() {
        val leaves = IntArray(100) { if (it < 30) argb(60, 130, 50) else argb(120, 110, 100) }
        assertEquals(0.3f, SceneDetect.foliage(leaves), 1e-4f)
        val (scene, p) = SceneDetect.candidate(SceneDetect.Groups(0f, 0f, 0f), 0.3f, 0f)
        assertEquals(Scene.PLANT, scene); assertTrue(p >= SceneDetect.ENTER)
        // Parede bege e pele não contam como folhagem.
        assertEquals(0f, SceneDetect.foliage(IntArray(10) { argb(200, 180, 150) }), 0f)
        assertEquals(0f, SceneDetect.foliage(IntArray(10) { argb(190, 140, 110) }), 0f)
    }

    @Test fun rostoTemPrioridade() {
        assertEquals(Scene.PERSON, SceneDetect.candidate(SceneDetect.Groups(0.9f, 0f, 0f), 0f, 0.05f).first)
    }

    @Test fun histereseSemPiscar() {
        val t = SceneDetect.Tracker()
        assertFalse(t.update(Scene.PET, 0.8f, 0))
        assertFalse(t.update(Scene.PET, 0.8f, 500))
        assertTrue(t.update(Scene.PET, 0.8f, 800))          // estável por 700 ms
        assertEquals(Scene.PET, t.scene)
        assertFalse(t.update(Scene.NONE, 0f, 1000))
        assertFalse(t.update(Scene.NONE, 0f, 2500))          // ainda dentro de 2 s da última troca
        assertTrue(t.update(Scene.NONE, 0f, 2900))           // saiu (baixo há ≥ 1 s e já passaram 2 s)
        assertEquals(Scene.NONE, t.scene)
    }

    @Test fun ajusteLeveSemTocarPele() {
        val skin = argb(200, 150, 120)
        val tile = intArrayOf(skin, argb(90, 140, 70))
        SceneTone.applyTile(tile, Scene.PLANT)
        assertEquals(skin, tile[0])
        val g = (tile[1] shr 8) and 255
        assertTrue("verde $g", g in 141..146)                 // vibrance +8% × (1 − saturação)
        val food = intArrayOf(skin); SceneTone.applyTile(food, Scene.FOOD)
        assertEquals(skin, food[0])
        val pet = intArrayOf(argb(90, 140, 70)); SceneTone.applyTile(pet, Scene.PET)
        assertEquals(argb(90, 140, 70), pet[0])
    }
}
