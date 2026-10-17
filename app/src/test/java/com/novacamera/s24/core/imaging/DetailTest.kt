package com.novacamera.s24.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DetailTest {
    private fun gray(v: Int) = Px.argb(v, v, v)

    @Test fun intensidadePorZoom() {
        assertEquals(0f, Detail.amountFor(1f), 0f)
        assertTrue(Detail.amountFor(3f) in 0.01f..Detail.amountFor(5f))
        assertTrue(Detail.amountFor(5f) > 0f)
        assertTrue(Detail.amountFor(10f) > Detail.amountFor(5f))
    }

    @Test fun bordaSuave_ficaMaisInclinada_semHalo() {
        // Degrau borrado (como a tele): 60 → 180 em ~4 px.
        val w = 40; val h = 12
        val ramp = intArrayOf(60, 70, 90, 120, 150, 170, 180)
        val px = IntArray(w * h) { i -> val x = i % w; gray(when { x < 17 -> 60; x > 23 -> 180; else -> ramp[x - 17] }) }
        val before = px.copyOf()
        Detail.applyBand(px, w, h, 0.6f, 1f)
        val r = 6 * w
        val slopeBefore = Px.r(before[r + 21]) - Px.r(before[r + 19])
        val slopeAfter = Px.r(px[r + 21]) - Px.r(px[r + 19])
        assertTrue("inclinação $slopeBefore → $slopeAfter", slopeAfter > slopeBefore)
        // Sem halo: nada abaixo do preto nem acima do branco do degrau.
        for (i in px.indices) assertTrue(Px.r(px[i]) in 60..180)
    }

    @Test fun pele_recebeMenosRealce() {
        // Mesmo degrau em tom de pele e em cinza: a pele muda menos.
        fun run(lo: Int, hi: Int, skin: Boolean): Int {
            val w = 40; val h = 12
            fun c(v: Int) = if (skin) Px.argb((v * 1.25f).toInt().coerceAtMost(255), v, (v * 0.85f).toInt()) else gray(v)
            val ramp = intArrayOf(0, 15, 40, 75, 100, 115, 120)
            val px = IntArray(w * h) { i -> val x = i % w; c(lo + when { x < 17 -> 0; x > 23 -> 120; else -> ramp[x - 17] }) }
            val before = px.copyOf()
            Detail.applyBand(px, w, h, 0.6f, 1f)
            return px.indices.sumOf { kotlin.math.abs(Px.g(px[it]) - Px.g(before[it])) }
        }
        assertTrue(run(110, 230, skin = true) < run(110, 230, skin = false))
    }

    @Test fun ruidoFraco_naoEhRealcado() {
        val w = 64; val h = 64; val rnd = Random(3)
        val px = IntArray(w * h) { gray(128 + rnd.nextInt(-2, 3)) }
        val sigma = Detail.noiseSigma(px, w, h)
        val changed = Detail.applyBand(px.copyOf(), w, h, 0.6f, sigma)
        assertTrue("alterados $changed de ${w * h}", changed < w * h / 20)
    }
}
