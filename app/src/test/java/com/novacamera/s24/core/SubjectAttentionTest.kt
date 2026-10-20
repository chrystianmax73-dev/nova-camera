package com.novacamera.s24.core

import com.novacamera.s24.core.imaging.SubjectAttention
import java.util.Random
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

class SubjectAttentionTest {
    private val w = 320; private val h = 240
    private fun gray(v: Int) = (255 shl 24) or (v shl 16) or (v shl 8) or v
    private fun g(p: Int) = p and 255

    /** Cena da foto 19/10: parede clara em cima, prateleira escura embaixo com uma caixa de letras escura. */
    private fun scene(text: Boolean, noise: Double = 3.0): IntArray {
        val rnd = Random(4)
        return IntArray(w * h) { k ->
            val x = k % w; val y = k / w
            var v = if (y < 120) 150.0 else 22.0
            if (text && y in 160..215 && x in 170..280) v = if ((x / 4 + y / 6) % 3 == 0) 15.0 else 45.0 // letras na sombra
            gray((v + rnd.nextGaussian() * noise).roundToInt().coerceIn(0, 255))
        }
    }

    @Test fun achaAsLetrasNaSombraESoClareiaAli() {
        val px = scene(text = true)
        val maps = SubjectAttention.map(px, w, h, null)
        assertTrue("caixa achada", maps.weight[225, 188] > 0.5f)
        assertTrue("parede clara fica", maps.weight[60, 40] < 0.05f)
        assertTrue("sombra lisa sem informação fica", maps.weight[40, 200] < 0.2f)
        assertTrue(SubjectAttention.accept(px, w, h, maps))
        val out = px.copyOf(); SubjectAttention.applyTile(out, w, h, 0, 0, w, h, maps)
        assertTrue("caixa mais clara", g(out[188 * w + 225]) > g(px[188 * w + 225]))
        assertEquals("parede intocada", px[40 * w + 60], out[40 * w + 60])
    }

    @Test fun semAssuntoNaoInventaAtencao() {
        val px = scene(text = false)
        val maps = SubjectAttention.map(px, w, h, null)
        assertFalse(SubjectAttention.accept(px, w, h, maps))
    }

    @Test fun toqueNaSombraViraAssunto() {
        val px = scene(text = false)
        val maps = SubjectAttention.map(px, w, h, 0.2f to 0.8f)
        assertTrue(maps.weight[64, 192] > 0.5f)
        assertTrue(SubjectAttention.accept(px, w, h, maps))
    }

    @Test fun recorteDaFotoGrandeUsaAsMesmasCoordenadas() {
        val px = scene(text = true); val maps = SubjectAttention.map(px, w, h, null)
        val whole = px.copyOf(); SubjectAttention.applyTile(whole, w, h, 0, 0, w, h, maps)
        val part = IntArray(60 * 40) { px[(170 + it / 60) * w + 200 + it % 60] }
        SubjectAttention.applyTile(part, 60, 40, 200, 170, w, h, maps)
        for (k in part.indices) assertEquals(whole[(170 + k / 60) * w + 200 + k % 60], part[k])
    }
}
