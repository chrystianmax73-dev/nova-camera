package com.novacamera.s24.core

import com.novacamera.s24.core.imaging.SceneStats
import com.novacamera.s24.core.imaging.ToneIntent
import com.novacamera.s24.core.imaging.ToneParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightExposureTest {
    private val handheld = NightExposure.maxFrameNs(0.02f, 1f, ois = true)

    @Test fun quartoEscuro_trocaIsoAltoPorTempoLongo() {
        // Caso real: 1/24 s, ISO 6539 na principal com OIS, na mão.
        val p = NightExposure.plan(6539, 41_666_666L, 50, 12800, 30_000_000_000L, handheld, 5)!!
        assertTrue("tempo maior que o automático", p.exposureNs > 41_666_666L * 3)
        assertTrue("ISO bem menor: ${p.iso}", p.iso < 3000)
        // A luz total sobe só o reforço (+0,5 EV).
        val ratio = p.iso.toDouble() * p.exposureNs / (6539.0 * 41_666_666L)
        assertEquals(Math.pow(2.0, 0.5), ratio, 0.05)
        assertTrue(p.frames in 3..5)
    }

    @Test fun apoiado_aceitaQuadroMaisLongo() {
        val still = NightExposure.maxFrameNs(0.002f, 1f, ois = true)
        assertTrue(still > handheld)
        assertTrue(still > NightExposure.MAX_FRAME_NS)
        assertTrue(still <= NightExposure.STEADY_MAX_FRAME_NS)
        assertTrue(NightExposure.steady(0.002f))
        // Na mão, o teto continua 0,5 s.
        assertTrue(NightExposure.maxFrameNs(0.013f, 1f, ois = true) <= NightExposure.MAX_FRAME_NS)
        // Apoiado: mesma luz em quadros mais longos e tempo total maior (mais luz somada, menos ruído).
        val p = NightExposure.plan(6539, 41_666_666L, 50, 12800, 30_000_000_000L, still, 8, steady = true)!!
        assertTrue(p.exposureNs > NightExposure.MAX_FRAME_NS)
        assertTrue(p.frames.toLong() * p.exposureNs <= 4_000_000_000L + p.exposureNs)
    }

    @Test fun teleESemOis_encurtamOLimite() {
        assertTrue(NightExposure.maxFrameNs(0.02f, 5f, ois = true) < handheld)
        assertTrue(NightExposure.maxFrameNs(0.02f, 1f, ois = false) < handheld)
    }

    @Test fun cenaClaraOuSemGanho_mantemAutomatico() {
        assertNull(NightExposure.plan(200, 10_000_000L, 50, 12800, 1_000_000_000L, handheld, 5))
        // Automático já usa um tempo perto do limite de tremido.
        assertNull(NightExposure.plan(3200, handheld, 50, 12800, 1_000_000_000L, handheld, 5))
    }

    @Test fun respeitaLimitesDoSensor() {
        val p = NightExposure.plan(6400, 30_000_000L, 100, 3200, 100_000_000L, NightExposure.MAX_FRAME_NS, 5)!!
        assertTrue(p.exposureNs <= 100_000_000L)
        assertTrue(p.iso in 100..3200)
    }

    @Test fun tomNoturno_clareiaMeiosTonsEFirmaOPreto() {
        val dark = SceneStats(mean = 0.29f, p01 = 0.077f, p05 = 0.095f, p50 = 0.27f, p95 = 0.55f, p99 = 0.86f, shadowFraction = 0.09f, darkChannel = 0.063f)
        val night = ToneParams.plan(dark, ToneIntent.NIGHT)
        assertTrue(night.midLiftEv > 0.4f)
        assertTrue(night.lumaBlack > 0.02f)
        assertEquals(0f, ToneParams.plan(dark, ToneIntent.LIGHT).midLiftEv, 0f)
    }

    @Test fun tomNoturno_contraALuz_naoEsbranquica() {
        // Armário escuro na frente de parede/janela clara: p95 alto.
        val backlit = SceneStats(mean = 0.35f, p01 = 0.06f, p05 = 0.07f, p50 = 0.30f, p95 = 0.82f, p99 = 0.87f, shadowFraction = 0.2f, darkChannel = 0.05f)
        val night = ToneParams.plan(backlit, ToneIntent.NIGHT)
        assertTrue("$night", night.midLiftEv < 0.15f)
    }

    @Test fun pretoFirme_naoSaturaSombraColorida() {
        val w = 8; val h = 8
        val px = IntArray(w * h) { com.novacamera.s24.core.imaging.Px.argb(45, 32, 44) }
        val base = com.novacamera.s24.core.imaging.Plane(w, h, FloatArray(w * h) { kotlin.math.ln(36f / 255f) })
        com.novacamera.s24.core.imaging.ToneMap(base, ToneParams(0f, 0f, 0f, 0f, 0f, 0.05f), w, h).applyTile(px, w, h, 0, 0)
        val c = px[0]
        val r = com.novacamera.s24.core.imaging.Px.r(c); val g = com.novacamera.s24.core.imaging.Px.g(c)
        // Mesma proporção R/G de antes (sem virar magenta): 45/32 ≈ 1,41.
        assertEquals(45f / 32f, r.toFloat() / g, 0.08f)
    }
}
