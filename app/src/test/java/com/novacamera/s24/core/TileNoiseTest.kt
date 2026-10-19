package com.novacamera.s24.core

import com.novacamera.s24.core.imaging.TileNoise
import java.util.Random
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class TileNoiseTest {
    private fun tile(sigma: Double, texture: Boolean, seed: Long = 7): IntArray {
        val rnd = Random(seed); val n = 288
        return IntArray(n * n) { i ->
            val x = i % n; val y = i / n
            val base = 110 + if (texture) 40 * sin(x * .9) * sin(y * .7) else 0.0
            val v = (base + rnd.nextGaussian() * sigma).roundToInt().coerceIn(0, 255)
            (255 shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    @Test fun estimaORuidoDoBloco() {
        assertEquals(4.0, TileNoise.sigma(tile(4.0, false), 288, 288).toDouble(), 0.6)
        assertEquals(8.0, TileNoise.sigma(tile(8.0, false), 288, 288).toDouble(), 1.0)
    }

    @Test fun pulaSoOBlocoLimpoNuncaOTexturizadoOuRuidoso() {
        assertTrue(TileNoise.clean(tile(0.4, false), 288, 288)) // céu/parede limpos
        assertFalse(TileNoise.clean(tile(3.0, false), 288, 288)) // ruído do Noturno
        assertFalse(TileNoise.clean(tile(0.4, true), 288, 288)) // detalhe fino: a rede ainda decide
    }
}
