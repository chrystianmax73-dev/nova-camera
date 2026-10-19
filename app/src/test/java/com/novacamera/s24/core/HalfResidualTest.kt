package com.novacamera.s24.core

import com.novacamera.s24.core.imaging.HalfResidual
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class HalfResidualTest {
    private fun gray(v: Int) = (255 shl 24) or (v shl 16) or (v shl 8) or v
    private fun g(p: Int) = p and 255

    @Test fun semMudancaNaRedeAFotoCheiaFicaIgual() {
        val w = 40; val h = 30
        val full = IntArray(w * h) { gray((it * 37) % 256) }
        val small = IntArray(w / 2 * (h / 2)); HalfResidual.box2(full, w, h, small, 0)
        val band = full.copyOf()
        HalfResidual.addResidual(band, w, h, 0, small, small.copyOf(), w / 2, h / 2)
        assertArrayEquals(full, band)
    }

    /**
     * Foto de Alta resolução: o ruído vem dos quadros de 12 MP ampliados 2× (grão de ~2 px). A meia
     * resolução o retira sem tocar no contorno nítido. Ruído de 1 pixel isolado cairia pouco (~10%):
     * limite conhecido, aceito porque a super-resolução já o atenua ao juntar os quadros.
     */
    @Test fun ruidoCaiEContornoFica() {
        val w = 200; val h = 120; val rnd = Random(3)
        val clean = IntArray(w * h) { i -> val x = i % w; if (x < 100) 60 else 180 } // contorno vertical
        val grain = DoubleArray(w / 2 * (h / 2)) { rnd.nextGaussian() * 6 }
        val noisy = IntArray(w * h) { i -> gray((clean[i] + grain[(i / w / 2) * (w / 2) + (i % w) / 2]).roundToInt().coerceIn(0, 255)) }
        val sw = w / 2; val sh = h / 2
        val small = IntArray(sw * sh); HalfResidual.box2(noisy, w, h, small, 0)
        // "Rede" ideal na meia resolução: devolve a cena limpa reduzida.
        val den = IntArray(sw * sh) { i -> gray(if (i % sw < 50) 60 else 180) }
        val out = noisy.copyOf(); HalfResidual.addResidual(out, w, h, 0, small, den, sw, sh)
        fun rms(a: IntArray, x0: Int, x1: Int): Double {
            var s = 0.0; var n = 0
            for (y in 0 until h) for (x in x0 until x1) { val e = g(a[y * w + x]) - clean[y * w + x]; s += e * e; n++ }
            return sqrt(s / n)
        }
        assertTrue("ruído ${rms(noisy, 10, 90)} → ${rms(out, 10, 90)}", rms(out, 10, 90) < rms(noisy, 10, 90) * 0.6)
        // Degrau continua de 1 pixel: a coluna 99 escura e a 100 clara.
        assertTrue(abs(g(out[60 * w + 99]) - 60) < 25 && abs(g(out[60 * w + 100]) - 180) < 25)
    }

    @Test fun mediaDoisPorDoisEmFaixas() {
        val w = 8; val rows = 4
        val src = IntArray(w * rows) { gray(10 * (it % 4)) }
        val out = IntArray(w / 2 * 4)
        HalfResidual.box2(src, w, rows, out, 2) // escreve nas linhas 2..3 da saída
        assertEquals(5, g(out[2 * 4])); assertEquals(25, g(out[2 * 4 + 1])); assertEquals(0, out[0])
    }
}
