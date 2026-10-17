package com.novacamera.s24.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastProfileTest {
    private fun gray(v: Int) = Px.argb(v, v, v)

    @Test fun pretoLavadoFicaFirmeSemEstourar() {
        // Cena "lavada": o mais escuro em 20/255 (como o Nova no dossiê), textura média e um claro.
        val w = 160; val h = 120
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val v = when { x < 20 -> 20; x > 140 -> 235; else -> 90 + ((x / 8 + y / 8) % 2) * 40 }
            gray(v)
        }
        val dark = Adaptive.darkChannel(px)
        val params = ToneParams.contrastProfile(dark, 0.45f)
        assertTrue(params.lumaBlack > 0.04f)
        val luma = Planes.downsampleLuma(px, w, h, 1)
        val out = px.copyOf()
        ToneMap(ToneMap.baseFrom(luma), params, w, h, ToneMap.midFrom(luma)).applyTile(out, w, h, 0, 0)
        assertTrue("preto deveria descer: ${Px.r(out[60 * w + 5])}", Px.r(out[60 * w + 5]) < 12)
        val before = Adaptive.quality(px, w, h); val after = Adaptive.quality(out, w, h)
        assertTrue(after.contrast > before.contrast)
        assertTrue(after.clipped <= before.clipped + 0.006f)
    }

    @Test fun cenaComPretoRealNaoMuda() {
        assertEquals(0f, ToneParams.contrastProfile(0.01f).lumaBlack, 0f)
        assertEquals(0.06f, ToneParams.contrastProfile(0.3f).lumaBlack, 1e-6f)
        // Cena escura (quarto): o preto firme não entra, para não escurecer o ambiente.
        assertEquals(0f, ToneParams.contrastProfile(0.3f, medianLuma = 0.10f).lumaBlack, 0f)
    }
}

class LowLightLiftTest {
    @org.junit.Test fun clareiaSoComIsoAltoECenaEscura() {
        // Frasco 5×: ISO 2056, mediana 0,18, p95 0,26 → ~+0,6 EV.
        val p = ToneParams.lowLightLift(2056, 0.184f, 0.258f)!!
        org.junit.Assert.assertEquals(0.6f, p.midLiftEv, 0.05f)
        // Luz boa (ISO baixo) ou cena já clara: nada.
        org.junit.Assert.assertNull(ToneParams.lowLightLift(400, 0.18f, 0.26f))
        org.junit.Assert.assertNull(ToneParams.lowLightLift(2056, 0.30f, 0.6f))
        // Altas luzes perto do limite: o clareamento não estoura (folga das altas luzes).
        org.junit.Assert.assertTrue((ToneParams.lowLightLift(3200, 0.10f, 0.85f)?.midLiftEv ?: 0f) < 0.1f)
    }

    @org.junit.Test fun hdrForcadoComPoucaLuzViraQuadrosIguais() {
        org.junit.Assert.assertEquals(4, com.novacamera.s24.core.AutoPhotoPolicy.hdrAsMerge(2056, 2))
        org.junit.Assert.assertEquals(4, com.novacamera.s24.core.AutoPhotoPolicy.hdrAsMerge(800, 1))
        org.junit.Assert.assertEquals(0, com.novacamera.s24.core.AutoPhotoPolicy.hdrAsMerge(400, 3))
        org.junit.Assert.assertEquals(0, com.novacamera.s24.core.AutoPhotoPolicy.hdrAsMerge(100, 1))
    }
}
