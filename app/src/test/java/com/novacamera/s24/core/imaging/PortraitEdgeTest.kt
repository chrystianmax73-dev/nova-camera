package com.novacamera.s24.core.imaging

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.max

/**
 * Borda do Retrato contra fundo de mesmo brilho (mão sobre tapete bege, como na foto do usuário).
 * A máscara da segmentação chega grosseira (modelo de 256 px); a refinada precisa seguir a borda
 * real: nem fundo nítido em volta da pessoa (franja), nem pessoa desfocada na borda.
 */
class PortraitEdgeTest {
    private val w = 600; private val h = 800

    private fun truth(x: Int, y: Int): Boolean {
        val dx = (x - 300) / 140.0; val dy = (y - 520) / 220.0
        val arm = dx * dx + dy * dy <= 1.0
        val remote = x in 330..400 && y in 120..560
        return arm || remote
    }

    private fun scene(): IntArray {
        val rnd = Random(7)
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val n = rnd.nextGaussian()
            val (r, g, b) = when {
                x in 330..400 && y in 120..560 -> Triple(30.0, 30.0, 34.0)       // controle (escuro)
                truth(x, y) -> Triple(205.0, 150.0, 128.0)                       // pele: luma ≈ 160
                else -> { val t = 12 * kotlin.math.sin(x / 5.0) * kotlin.math.sin(y / 7.0); Triple(176.0 + t, 162.0 + t, 146.0 + t) } // tapete: luma ≈ 163
            }
            Px.argb((r + 4 * n).toInt(), (g + 4 * n).toInt(), (b + 4 * n).toInt())
        }
    }

    /** Máscara como a do modelo: 1/16 da resolução e borrada. */
    private fun coarse(): Plane {
        val f = 16
        val small = Plane(w / f, h / f)
        for (y in 0 until small.h) for (x in 0 until small.w) {
            var s = 0; for (yy in 0 until f) for (xx in 0 until f) if (truth(x * f + xx, y * f + yy)) s++
            small[x, y] = s / (f * f).toFloat()
        }
        return Planes.boxBlur(small, 1)
    }

    private class Score(val halo: Float, val lost: Float)

    private fun score(alpha: Plane): Score {
        val t = Plane(w, h, FloatArray(w * h) { if (truth(it % w, it / w)) 1f else 0f })
        val near = Planes.boxBlur(t, 40); val touch = Planes.boxBlur(t, 4)
        var out = 0; var outBad = 0; var inn = 0; var inBad = 0
        for (i in 0 until w * h) {
            val a = alpha.sample((i % w).toFloat() * alpha.w / w, (i / w).toFloat() * alpha.h / h)
            if (t.data[i] == 0f && near.data[i] > 0f && touch.data[i] == 0f) { out++; if (a > 0.5f) outBad++ }
            if (t.data[i] == 1f && touch.data[i] == 1f && near.data[i] < 1f) { inn++; if (a < 0.5f) inBad++ }
        }
        return Score(outBad / max(1, out).toFloat(), inBad / max(1, inn).toFloat())
    }

    @Test fun colorRefinedEdgeHasNoBackgroundFringeOnSameBrightnessBackground() {
        val px = scene()
        val full = Planes.downsampleRgb(px, w, h, 1)
        val low = Planes.downsampleRgb(px, w, h, 2)
        val c = coarse()
        // Antes: guiado só pela luminância + transição puxada para a pessoa em todo o contorno.
        val luma = Planes.downsampleLuma(px, w, h, 1)
        val oldRefined = Portrait.refineMatte(c, luma)
        val old = Plane(w, h, FloatArray(w * h) { val t = ((oldRefined.data[it] - 0.08f) / 0.47f).coerceIn(0f, 1f); t * t * (3 - 2 * t) })
        val new = Portrait.refineMatteColor(c, low, full, null)
        val so = score(old); val sn = score(new)
        println("PORTRAIT franja antes=${so.halo} depois=${sn.halo} · pessoa perdida antes=${so.lost} depois=${sn.lost}")
        assertTrue("franja de fundo nítido: ${sn.halo} (antes ${so.halo})", sn.halo < 0.03f && sn.halo < so.halo / 3)
        assertTrue("borda da pessoa desfocada: ${sn.lost}", sn.lost < 0.03f)
    }
}

class PortraitGrainTest {
    @Test fun noiseEstimateIgnoresTextureAndMatchesSensorNoise() {
        val w = 512; val h = 512; val rnd = Random(3)
        val px = IntArray(w * h) { i ->
            val x = i % w
            val tex = if (x > 300) 40 * kotlin.math.sin(i / 3.0) else 0.0 // metade com textura forte
            val v = (120 + tex + 3.0 * rnd.nextGaussian()).toInt()
            Px.argb(v, v, v)
        }
        val s = Portrait.noiseSigma(px, w, h)
        assertTrue("σ estimado $s (real 3)", s in 2.2f..3.8f)
    }

    @Test fun gaussHashHasUnitSpread() {
        var sum = 0.0; var sq = 0.0; val n = 200_000
        for (i in 0 until n) { val v = Portrait.gaussHash(i % 1000, i / 1000).toDouble(); sum += v; sq += v * v }
        val mean = sum / n; val sd = kotlin.math.sqrt(sq / n - mean * mean)
        assertTrue("média $mean", kotlin.math.abs(mean) < 0.02)
        assertTrue("desvio $sd", sd in 0.95..1.05)
    }
}
