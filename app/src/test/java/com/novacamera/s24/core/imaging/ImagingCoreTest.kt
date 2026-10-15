package com.novacamera.s24.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Imagens sintéticas com propriedades conhecidas: cada teste mede um critério objetivo. */
private object Synth {
    /** Textura suave e não periódica (soma de senóides), 0..1. */
    fun texture(w: Int, h: Int, seed: Int = 1): FloatArray {
        val rnd = Random(seed.toLong())
        val waves = List(6) { floatArrayOf(rnd.nextFloat() * 0.25f + 0.02f, rnd.nextFloat() * 0.25f + 0.02f, rnd.nextFloat() * 6f) }
        return FloatArray(w * h) { i ->
            val x = i % w; val y = i / w
            var v = 0f
            for (wv in waves) v += sin(x * wv[0] + y * wv[1] + wv[2])
            0.5f + v / 12f
        }
    }

    fun gray(values: FloatArray): IntArray = IntArray(values.size) { val v = (values[it] * 255f).toInt(); Px.argb(v, v, v) }

    fun shifted(src: FloatArray, w: Int, h: Int, dx: Int, dy: Int): FloatArray =
        FloatArray(w * h) { i -> val x = i % w - dx; val y = i / w - dy; src[min(h - 1, max(0, y)) * w + min(w - 1, max(0, x))] }

    fun noisy(px: IntArray, sigma: Float, seed: Int): IntArray {
        val rnd = Random(seed.toLong())
        return IntArray(px.size) { i ->
            val n = (rnd.nextGaussian() * sigma).toFloat()
            val c = px[i]
            Px.argb((Px.r(c) + n).toInt(), (Px.g(c) + n).toInt(), (Px.b(c) + n).toInt())
        }
    }

    fun std(a: IntArray, b: IntArray): Float {
        var s = 0.0
        for (i in a.indices) { val d = (Px.g(a[i]) - Px.g(b[i])).toDouble(); s += d * d }
        return sqrt(s / a.size).toFloat()
    }

    fun lumaPlane(px: IntArray, w: Int, h: Int) = Plane(w, h, FloatArray(w * h) { Px.luma(px[it]) })
}

class AlignTest {
    @Test
    fun recoversKnownHandheldShift() {
        val w = 240; val h = 180
        val ref = Synth.texture(w, h)
        val mov = Synth.shifted(ref, w, h, 7, -4)
        val s = Align.globalShift(Plane(w, h, ref), Plane(w, h, mov), maxShift = 24)
        assertEquals(Shift(7, -4), s)
    }
}

class MergeTest {
    private val w = 160; private val h = 120
    private val clean = Synth.gray(Synth.texture(w, h, 3))

    @Test
    fun fourFramesHalveTheNoise() {
        val sigma = 8f
        val frames = (0 until 4).map { Synth.noisy(clean, sigma, 10 + it) }
        val est = Merge.estimateNoise(frames[0], w, h)
        val merged = Merge.mergeTile(frames[0], frames.drop(1), est).pixels
        val before = Synth.std(frames[0], clean)
        val after = Synth.std(merged, clean)
        assertTrue("ruído antes=$before depois=$after", after < before * 0.62f)
    }

    @Test
    fun movingObjectDoesNotCreateGhost() {
        val frames = (0 until 4).map { Synth.noisy(clean, 4f, 20 + it).copyOf() }
        // Um objeto claro aparece só no quadro 2 (alguém passou na frente).
        for (y in 40 until 80) for (x in 60 until 100) frames[2][y * w + x] = Px.argb(250, 250, 250)
        val merged = Merge.mergeTile(frames[0], frames.drop(1), Merge.estimateNoise(frames[0], w, h)).pixels
        var maxDiff = 0
        for (y in 40 until 80) for (x in 60 until 100) maxDiff = max(maxDiff, abs(Px.g(merged[y * w + x]) - Px.g(clean[y * w + x])))
        assertTrue("diferença máxima na área do objeto: $maxDiff", maxDiff < 20)
    }
}

class ToneTest {
    private fun scene(w: Int, h: Int, level: Float, green: Boolean): IntArray {
        val t = Synth.texture(w, h, 5)
        return IntArray(w * h) { i ->
            val v = (t[i] * level * 255f).coerceIn(0f, 255f)
            if (green && i % w < w / 2) Px.argb((v * 0.55f).toInt(), v.toInt(), (v * 0.45f).toInt()) else Px.argb(v.toInt(), v.toInt(), v.toInt())
        }
    }

    private fun apply(px: IntArray, w: Int, h: Int, intent: ToneIntent): Pair<IntArray, ToneParams> {
        val luma = Planes.downsampleLuma(px, w, h, 4)
        val params = ToneParams.plan(SceneStats.of(luma), intent)
        val out = px.copyOf()
        ToneMap(ToneMap.baseFrom(luma), params, w, h).applyTile(out, w, h, 0, 0)
        return out to params
    }

    @Test
    fun nightLiftsShadowsButKeepsAtmosphere() {
        val w = 200; val h = 150
        val dark = scene(w, h, 0.28f, green = false)
        val (out, params) = apply(dark, w, h, ToneIntent.NIGHT)
        val before = Planes.percentile(FloatArray(dark.size) { Px.luma(dark[it]) }, 0.5f)
        val after = Planes.percentile(FloatArray(out.size) { Px.luma(out[it]) }, 0.5f)
        assertTrue("$params", params.shadowLiftEv + params.midLiftEv > 0.3f)
        assertTrue("mediana $before → $after", after > before * 1.3f)
        // Mais claro, perto do Noturno da Samsung, mas não vira dia.
        assertTrue("mediana $after", after < 0.55f)
    }

    @Test
    fun colorFidelityIsPreserved() {
        // Pano verde: a saturação relativa (máx−mín)/máx não pode mudar com o tom.
        val w = 200; val h = 150
        val px = scene(w, h, 0.35f, green = true)
        val (out, _) = apply(px, w, h, ToneIntent.NIGHT)
        var maxDelta = 0f
        for (y in 10 until h - 10) for (x in 10 until w / 2 - 10) {
            fun sat(c: Int): Float { val mx = max(Px.r(c), max(Px.g(c), Px.b(c))); val mn = min(Px.r(c), min(Px.g(c), Px.b(c))); return if (mx < 20) 0f else (mx - mn).toFloat() / mx }
            val c0 = px[y * w + x]; val c1 = out[y * w + x]
            if (max(Px.r(c0), max(Px.g(c0), Px.b(c0))) < 20) continue
            maxDelta = max(maxDelta, abs(sat(c0) - sat(c1)))
        }
        assertTrue("variação máxima de saturação: $maxDelta", maxDelta < 0.03f)
    }

    @Test
    fun wellExposedSceneIsLeftAlone() {
        val w = 200; val h = 150
        val px = scene(w, h, 1.0f, green = true)
        val (_, params) = apply(px, w, h, ToneIntent.LIGHT)
        assertTrue("parâmetros deveriam ser identidade: $params", params.isIdentity)
    }

    @Test
    fun veilIsCorrectedOnlyWhenPresent() {
        val w = 200; val h = 150
        val t = Synth.texture(w, h, 9)
        // Imagem "velada" (aspirador 10×): preto levantado em ~0,45 e pouca faixa dinâmica.
        val veiled = IntArray(w * h) { val v = ((0.45f + t[it] * 0.4f) * 255f).toInt(); Px.argb(v, v, v) }
        val (out, params) = apply(veiled, w, h, ToneIntent.LIGHT)
        assertTrue(params.blackLevel > 0.1f)
        val p01Before = Planes.percentile(FloatArray(veiled.size) { Px.luma(veiled[it]) }, 0.01f)
        val p01After = Planes.percentile(FloatArray(out.size) { Px.luma(out[it]) }, 0.01f)
        assertTrue("preto $p01Before → $p01After", p01After < p01Before - 0.1f)
        // Cena clara legítima, como o pano verde: preto em ~0,2 e brancos em ~0,55 — não pode ser "corrigida".
        val normal = IntArray(w * h) { val v = ((0.10f + t[it] * 0.5f) * 255f).toInt().coerceIn(0, 255); Px.argb(v, v, v) }
        assertEquals(0f, ToneParams.plan(SceneStats.of(Planes.downsampleLuma(normal, w, h, 4)), ToneIntent.LIGHT).blackLevel, 0f)
    }
}

class FusionTest {
    @Test
    fun recoversHighlightDetailFromDarkFrame() {
        val w = 160; val h = 120
        val t = Synth.texture(w, h, 4)
        // Normal: janela (metade direita) estourada em 255. Escuro (−2 EV): mesma janela com textura.
        val normal = IntArray(w * h) { i -> val v = if (i % w >= w / 2) 255 else (t[i] * 160).toInt(); Px.argb(v, v, v) }
        val dark = IntArray(w * h) { i -> val v = if (i % w >= w / 2) (150 + t[i] * 90).toInt() else (t[i] * 40).toInt(); Px.argb(v, v, v) }
        val lumas = listOf(Synth.lumaPlane(normal, w, h), Synth.lumaPlane(dark, w, h)).map { Planes.resize(it, w / 2, h / 2) }
        val weights = Fusion.weights(lumas, lumas[0])
        val out = Fusion.blendTile(listOf(normal, dark), weights, w, h, 0, 0, w, h)
        val window = (0 until w * h).filter { it % w >= w * 3 / 4 }.map { Px.g(out[it]).toFloat() }
        val mean = window.average()
        val spread = sqrt(window.map { (it - mean) * (it - mean) }.average())
        assertTrue("janela deveria ganhar textura (desvio=$spread)", spread > 5)
        assertTrue("janela não deveria ficar estourada (média=$mean)", mean < 250)
    }
}

class PortraitTest {
    @Test
    fun subjectStaysUntouchedAndBackgroundIsBlurred() {
        val w = 120; val h = 90
        val t = Synth.texture(w, h, 7)
        val img = IntArray(w * h) { i -> val v = (t[i] * 255).toInt(); if (i % w < 40) Px.argb(200, 120, 90) else Px.argb(v, v, v) }
        val matte = Plane(w, h, FloatArray(w * h) { if (it % w < 40) 1f else 0f })
        val dist = Portrait.backgroundDistance(matte)
        val near = Portrait.blurBackground(img, w, h, matte, 4)
        val far = Portrait.blurBackground(img, w, h, matte, 12)
        val out = img.copyOf()
        Portrait.compositeTile(out, w, h, 0, 0, w, h, matte, dist, near, far, w, h)
        for (y in 0 until h) for (x in 0 until 40) assertEquals("pessoa alterada em ($x,$y)", img[y * w + x], out[y * w + x])
        val bgBefore = (0 until w * h).filter { it % w > 60 }.map { Px.g(img[it]).toDouble() }
        val bgAfter = (0 until w * h).filter { it % w > 60 }.map { Px.g(out[it]).toDouble() }
        fun sd(v: List<Double>): Double { val m = v.average(); return sqrt(v.map { (it - m) * (it - m) }.average()) }
        assertTrue("fundo deveria perder detalhe: ${sd(bgBefore)} → ${sd(bgAfter)}", sd(bgAfter) < sd(bgBefore) * 0.7)
        // Sem halo: o fundo junto ao contorno não puxa a cor da pessoa (vermelho).
        val edge = out[45 + h / 2 * w]
        assertTrue("cor da pessoa vazou para o fundo: ${Px.r(edge)},${Px.g(edge)},${Px.b(edge)}", abs(Px.r(edge) - Px.g(edge)) < 25)
    }
}

class FoodLookTest {
    @Test
    fun boostsDullColorsMoreThanVividOnes() {
        val dull = intArrayOf(Px.argb(150, 130, 110))
        val vivid = intArrayOf(Px.argb(230, 40, 30))
        val d0 = dull[0]; val v0 = vivid[0]
        FoodLook.applyTile(dull); FoodLook.applyTile(vivid)
        fun sat(c: Int): Float { val mx = max(Px.r(c), max(Px.g(c), Px.b(c))); val mn = min(Px.r(c), min(Px.g(c), Px.b(c))); return (mx - mn).toFloat() / mx }
        assertTrue(sat(dull[0]) - sat(d0) > sat(vivid[0]) - sat(v0))
    }
}

class FusionDeghostTest {
    @Test
    fun movingObjectDoesNotGhostIntoHdr() {
        val w = 64; val h = 48
        // Referência: objeto claro à esquerda. Quadro escuro (−1 EV): o objeto se moveu para a direita.
        val ref = Plane(w, h); val dark = Plane(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val bg = 0.4f
            ref[x, y] = if (x in 8..20 && y in 16..32) 0.8f else bg
            dark[x, y] = (if (x in 40..52 && y in 16..32) 0.8f else bg) * 0.5f
        }
        val masks = Fusion.ghostMasks(listOf(ref, dark))
        // Onde o objeto apareceu só no quadro escuro, ele é rejeitado; no fundo parado, aceito.
        assertTrue(masks[1][46, 24] < 0.1f)
        assertTrue(masks[1][30, 40] > 0.9f)
        assertEquals(0.5f, Fusion.exposureGain(ref, dark), 0.02f)
    }
}

/** Regressões das reclamações da 0.6: o processamento não pode piorar a foto. */
class AdaptiveToneTest {
    private val w = 160; private val h = 120

    private fun scene(blackLift: Int, green: Boolean = false): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val v = (20 + 200 * x / w + if ((x / 8 + y / 8) % 2 == 0) 12 else 0).coerceIn(0, 255)
        val lifted = blackLift + v * (255 - blackLift) / 255
        if (green) Px.argb(lifted * 40 / 100, lifted * 75 / 100, lifted * 42 / 100)
        else Px.argb(lifted, lifted * 96 / 100, lifted * 90 / 100)
    }

    @Test
    fun cleanSceneIsLeftUntouched() {
        // Cena limpa (pretos reais) e cena verde saturada (pano verde): nenhum ajuste.
        for (px in listOf(scene(0), scene(0, green = true))) {
            val plan = Adaptive.plan(px, w, h, ToneIntent.LIGHT)
            assertTrue(plan.reason, plan.skip)
        }
    }

    @Test
    fun greyBlacksAreAnchoredWithoutLosingContrast() {
        val px = scene(45)
        val plan = Adaptive.plan(px, w, h, ToneIntent.LIGHT)
        assertTrue(plan.reason, !plan.skip)
        val out = px.copyOf()
        ToneMap(plan.base, plan.params, w, h, plan.mid).applyTile(out, w, h, 0, 0, plan.strength)
        val q0 = Adaptive.quality(px, w, h); val q1 = Adaptive.quality(out, w, h)
        assertTrue("preto ${q0.black} → ${q1.black}", q1.black < q0.black - 0.05f)
        assertTrue(q1.contrast >= q0.contrast)
        assertTrue(Adaptive.acceptable(q0, q1))
    }

    @Test
    fun guardRejectsFlatteningResult() {
        val px = scene(0)
        val q0 = Adaptive.quality(px, w, h)
        // Simula a 0.6: sombras levantadas → pretos acinzentados e menos contraste.
        val flat = IntArray(px.size) { i -> val c = px[i]; Px.argb(60 + Px.r(c) * 3 / 4, 60 + Px.g(c) * 3 / 4, 60 + Px.b(c) * 3 / 4) }
        assertTrue(!Adaptive.acceptable(q0, Adaptive.quality(flat, w, h)))
    }

    @Test
    fun portraitNeverGetsClarityAndBarelyLifts() {
        val p = ToneParams.plan(SceneStats(0.2f, 0.02f, 0.05f, 0.15f, 0.7f, 0.9f, 0.3f, 0.12f), ToneIntent.PORTRAIT, zoom = 5f)
        assertEquals(0f, p.clarity, 0f)
        assertTrue(p.shadowLiftEv <= 0.25f)
    }

    @Test
    fun clarityOnlyWhenZoomed() {
        val s = SceneStats(0.4f, 0.05f, 0.1f, 0.4f, 0.7f, 0.8f, 0.02f, 0.05f)
        assertEquals(0f, ToneParams.plan(s, ToneIntent.LIGHT, 1f).clarity, 0f)
        assertTrue(ToneParams.plan(s, ToneIntent.LIGHT, 10f).clarity > 0.15f)
    }
}


class ColorConsistencyRegressionTest {
    @org.junit.Test
    fun sameLuminanceDifferentObjectIsRejected() {
        // Luminâncias quase iguais; uma máscara exclusivamente luminosa aceitava o objeto.
        val red = Px.argb(180, 60, 60)
        val green = Px.argb(60, 96, 60)
        org.junit.Assert.assertEquals(Px.luma(red), Px.luma(green), 0.002f)
        org.junit.Assert.assertEquals(0f, Consistency.agreement(red, green, ExposureLut.IDENTITY, 0.002f), 0f)
    }

    @org.junit.Test
    fun sameColorAtAnotherExposureRemainsUsable() {
        val ref = Px.argb(160, 120, 80)
        val mov = Px.argb(80, 60, 40)
        val lut = ExposureLut(FloatArray(257) { (it / 128f).coerceAtMost(1f) })
        org.junit.Assert.assertEquals(1f, Consistency.agreement(ref, mov, lut, 0.002f), 0.01f)
    }

    @org.junit.Test
    fun shiftedOutsideSensorIsNeverTrusted() {
        val ref = Plane(64, 64, FloatArray(64 * 64) { 0.4f })
        val r = Consistency.reliability(ref, ref, arrayOf(Shift(-4, 0)), 1, 1, ExposureLut.IDENTITY, 0.002f)
        for (y in 0 until 64) for (x in 0 until 4) org.junit.Assert.assertEquals(0f, r[x, y], 0f)
        org.junit.Assert.assertTrue(r[32, 32] > 0.9f)
    }
}

class FrameQualityRegressionTest {
    @org.junit.Test
    fun clearMeasuredEdgesBeatBlurredFrame() {
        val sharp = Plane(64, 64, FloatArray(4096) { i -> if (i % 64 / 8 % 2 == 0) 0.25f else 0.75f })
        val blurred = Planes.boxBlur(sharp, 3)
        org.junit.Assert.assertEquals(1, FrameQuality.best(listOf(FrameQuality.score(blurred), FrameQuality.score(sharp))))
    }

    @org.junit.Test
    fun smallDifferenceOrMoreClippingKeepsFirstFrame() {
        org.junit.Assert.assertEquals(0, FrameQuality.best(listOf(FrameQuality.Score(1f, 0f), FrameQuality.Score(1.05f, 0f))))
        org.junit.Assert.assertEquals(0, FrameQuality.best(listOf(FrameQuality.Score(1f, 0f), FrameQuality.Score(2f, 0.1f))))
    }
}
