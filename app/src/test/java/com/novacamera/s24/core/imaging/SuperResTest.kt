package com.novacamera.s24.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random

class SuperResTest {
    private val big = 256

    /** Cena "real": grades finas (perto do limite de amostragem) e blocos tipo letra. */
    private var fineAmp = 0.18f

    private fun truth(x: Float, y: Float): Float {
        // Formas grandes (alinhamento) + traços finos de período ~3 px da cena: acima do limite de
        // amostragem do quadro reduzido (4 px), como letras pequenas no 10×.
        val coarse = 0.5f + 0.2f * sin(x * 0.11f) * sin(y * 0.07f)
        val fine = fineAmp * sin(x * 2.1f + y * 0.3f)
        return (coarse + fine).coerceIn(0f, 1f)
    }

    /** Quadro reduzido 2× (média 2×2) com a cena deslocada (sx, sy) em pixels da cena, e ruído. */
    private fun frame(sx: Float, sy: Float, rnd: Random, noise: Float): SuperRes.Region {
        val w = big / 2
        val px = IntArray(w * w) { i ->
            val x = i % w; val y = i / w
            var s = 0f
            for (dy in 0..1) for (dx in 0..1) s += truth(2 * x + dx + sx, 2 * y + dy + sy)
            val v = ((s / 4 + noise * (rnd.nextFloat() - 0.5f) * 3.46f) * 255).toInt().coerceIn(0, 255)
            Px.argb(v, v, v)
        }
        return SuperRes.Region(px, 0, 0, w, w)
    }

    private fun psnr(img: IntArray, w: Int, margin: Int): Double {
        var e = 0.0; var n = 0
        for (y in margin until w - margin) for (x in margin until w - margin) {
            val d = Px.r(img[y * w + x]) / 255.0 - truth(x.toFloat(), y.toFloat())
            e += d * d; n++
        }
        return 10 * log10(1.0 / (e / n))
    }

    @Test fun deslocamentoMedidoComFracaoDePixel() {
        fineAmp = 0f // medição em cena sem padrão repetitivo fino (o alinhamento real usa a cena inteira)
        val rnd = Random(1)
        val ref = frame(0f, 0f, rnd, 0f)
        val mov = frame(0.8f, -0.6f, rnd, 0f) // cena andou 0,8 / −0,6 px da cena = 0,4 / −0,3 px do quadro
        val (sx, sy) = SuperRes.estimateShift(SuperRes.Region(ref.px.copyOfRange(0, ref.px.size), 0, 0, ref.w, ref.h).let {
            // região interna da referência, para a busca ter margem dentro do quadro movido
            val m = 8; val w = ref.w - 2 * m
            SuperRes.Region(IntArray(w * w) { i -> ref.px[(i / w + m) * ref.w + (i % w + m)] }, m, m, w, w)
        }, mov, 0, 0)
        // ref p ↔ mov p + s, com mov(i) = cena(2i + 0,8) = ref(i + 0,4) → s = −0,4 / +0,3
        assertEquals(-0.4f, sx, 0.12f)
        assertEquals(0.3f, sy, 0.12f)
    }

    @Test fun oitoQuadrosRecuperamDetalheAlemDoZoomDeUmQuadro() {
        val rnd = Random(7)
        val shifts = listOf(0f to 0f) + List(7) { rnd.nextFloat() * 2f to rnd.nextFloat() * 2f }
        val frames = shifts.map { (sx, sy) -> frame(sx, sy, rnd, 0.01f) }
        // Deslocamentos verdadeiros no quadro reduzido (a medição tem teste próprio).
        val list = frames.mapIndexed { k, f -> f to (-shifts[k].first / 2f to -shifts[k].second / 2f) }
        val sr = SuperRes.mergeTile(big, big, 0, 0, 2f, 0f, 0f, list, 0.01f)
        val single = IntArray(big * big) { i -> SuperRes.bilinear(frames[0], (i % big + 0.5f) / 2f - 0.5f, (i / big + 0.5f) / 2f - 0.5f) }
        val pSr = psnr(sr, big, 8); val pOne = psnr(single, big, 8)
        println("PSNR super-resolução=%.2f dB, zoom de um quadro=%.2f dB".format(pSr, pOne))
        assertTrue("super-resolução deveria ganhar ≥ 1,5 dB: $pSr vs $pOne", pSr > pOne + 1.5)
    }

    @Test fun seisQuadrosComRuidoAindaGanham() {
        val rnd = Random(11)
        val shifts = listOf(0f to 0f) + List(5) { rnd.nextFloat() * 2f to rnd.nextFloat() * 2f }
        val frames = shifts.map { (sx, sy) -> frame(sx, sy, rnd, 0.03f) }
        val list = frames.mapIndexed { k, f -> f to (-shifts[k].first / 2f to -shifts[k].second / 2f) }
        val sr = SuperRes.mergeTile(big, big, 0, 0, 2f, 0f, 0f, list, 0.03f)
        val single = IntArray(big * big) { i -> SuperRes.bilinear(frames[0], (i % big + 0.5f) / 2f - 0.5f, (i / big + 0.5f) / 2f - 0.5f) }
        val pSr = psnr(sr, big, 8); val pOne = psnr(single, big, 8)
        println("PSNR (6 quadros, ruído) super-resolução=%.2f dB, um quadro=%.2f dB".format(pSr, pOne))
        assertTrue(pSr > pOne + 1.0)
    }

    /**
     * Noturno no 10×: 5 quadros escuros e ruidosos. A alternativa sem SR é a fusão comum de quadros já
     * ampliados (média dos quadros alinhados, cada um interpolado). A SR não pode sair pior que ela.
     */
    @Test fun noturnoRuidosoNaoFicaPiorQueAFusaoComum() {
        for (noise in listOf(0.05f, 0.08f)) {
            val rnd = Random(21)
            val shifts = listOf(0f to 0f) + List(4) { rnd.nextFloat() * 2f to rnd.nextFloat() * 2f }
            val frames = shifts.map { (sx, sy) -> frame(sx, sy, rnd, noise) }
            val list = frames.mapIndexed { k, f -> f to (-shifts[k].first / 2f to -shifts[k].second / 2f) }
            val sr = SuperRes.mergeTile(big, big, 0, 0, 2f, 0f, 0f, list, noise, lowLight = true)
            val fused = IntArray(big * big) { i ->
                var s = 0f
                for ((f, d) in list) s += Px.r(SuperRes.bilinear(f, (i % big + 0.5f) / 2f - 0.5f + d.first, (i / big + 0.5f) / 2f - 0.5f + d.second))
                val v = (s / list.size + 0.5f).toInt(); Px.argb(v, v, v)
            }
            val pSr = psnr(sr, big, 8); val pFused = psnr(fused, big, 8)
            println("PSNR noturno (ruído $noise) super-resolução=%.2f dB, fusão comum ampliada=%.2f dB".format(pSr, pFused))
            assertTrue("SR pior que a fusão comum no escuro: $pSr vs $pFused", pSr > pFused - 0.3)
        }
    }

    /** Área lisa no escuro (parede, céu): a SR não pode deixar mais granulado que a fusão comum. */
    @Test fun noturnoAreaLisaSemMaisGranulado() {
        fineAmp = 0f
        val noise = 0.08f
        val rnd = Random(5)
        val shifts = listOf(0f to 0f) + List(4) { rnd.nextFloat() * 2f to rnd.nextFloat() * 2f }
        val frames = shifts.map { (sx, sy) -> frame(sx, sy, rnd, noise) }
        val list = frames.mapIndexed { k, f -> f to (-shifts[k].first / 2f to -shifts[k].second / 2f) }
        val sr = SuperRes.mergeTile(big, big, 0, 0, 2f, 0f, 0f, list, noise, lowLight = true)
        val fused = IntArray(big * big) { i ->
            var s = 0f
            for ((f, d) in list) s += Px.r(SuperRes.bilinear(f, (i % big + 0.5f) / 2f - 0.5f + d.first, (i / big + 0.5f) / 2f - 0.5f + d.second))
            val v = (s / list.size + 0.5f).toInt(); Px.argb(v, v, v)
        }
        val pSr = psnr(sr, big, 8); val pFused = psnr(fused, big, 8)
        println("PSNR noturno área lisa super-resolução=%.2f dB, fusão comum ampliada=%.2f dB".format(pSr, pFused))
        assertTrue("SR mais granulada que a fusão comum: $pSr vs $pFused", pSr > pFused - 1.0)
    }

    @Test fun objetoQueSeMexeuNaoViraFantasma() {
        val rnd = Random(3)
        val f0 = frame(0f, 0f, rnd, 0f)
        // Quadro 1: mesma cena, mas um bloco branco "entrou" (movimento): deve ser rejeitado.
        val f1 = frame(0.5f, 0.5f, rnd, 0f).let { r ->
            val px = r.px.copyOf(); for (y in 40 until 60) for (x in 40 until 60) px[y * r.w + x] = Px.argb(255, 255, 255)
            SuperRes.Region(px, 0, 0, r.w, r.h)
        }
        val out = SuperRes.mergeTile(big, big, 0, 0, 2f, 0f, 0f, listOf(f0 to (0f to 0f), f1 to (-0.25f to -0.25f)), 0.005f)
        val center = Px.r(out[100 * big + 100]) / 255f
        assertTrue("fantasma branco na fusão: $center", center < truth(100f, 100f) + 0.15f)
    }
}
