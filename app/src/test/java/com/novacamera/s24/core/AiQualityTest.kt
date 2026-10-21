package com.novacamera.s24.core

import com.novacamera.s24.core.imaging.AiQuality
import com.novacamera.s24.core.imaging.TileNoise
import java.util.Random
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

class AiQualityTest {
    private val w = 288; private val h = 288
    /** Letras/contornos: blocos alternados escuros e claros como o texto da caixa. */
    private fun scene(x: Int, y: Int) = if ((x / 12 + y / 16) % 2 == 0) 70 else 150
    private fun argb(sigma: Double, blur: Boolean = false, seed: Long = 1): IntArray {
        val rnd = Random(seed)
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val base = if (!blur) scene(x, y).toDouble() else {
                var s = 0.0; for (dy in -4..4) for (dx in -4..4) s += scene((x + dx).coerceIn(0, w - 1), (y + dy).coerceIn(0, h - 1)); s / 81
            }
            val v = (base + rnd.nextGaussian() * sigma).roundToInt().coerceIn(0, 255)
            (255 shl 24) or (v shl 16) or (v shl 8) or v
        }
    }
    private fun stats(px: IntArray): AiQuality.Stats {
        val small = IntArray(w / 4 * (h / 4)); AiQuality.box4Luma(px, w, h, small, 0)
        return AiQuality.Stats(TileNoise.sigma(px, w, h), AiQuality.edgeEnergy(small, w / 4, h / 4))
    }

    @Test fun limpezaVerdadeiraEAceita() {
        assertTrue(AiQuality.accept(stats(argb(6.0)), stats(argb(1.0, seed = 2))))
    }

    @Test fun limpezaQueBorraOTextoERecusada() {
        assertFalse(AiQuality.accept(stats(argb(6.0)), stats(argb(1.0, blur = true))))
    }

    @Test fun semGanhoDeRuidoERecusada() {
        assertFalse(AiQuality.accept(stats(argb(6.0)), stats(argb(6.0, seed = 3))))
    }

    @Test fun forcaSegueORuidoMedido() {
        assertEquals(0f, AiQuality.strengthFor(0.8f)); assertEquals(0.55f, AiQuality.strengthFor(2f))
        assertEquals(0.75f, AiQuality.strengthFor(4f)); assertEquals(0.9f, AiQuality.strengthFor(9f))
    }
}
