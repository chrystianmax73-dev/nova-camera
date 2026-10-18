package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Recuperação de detalhe na tele (3×, 5× e 10×): devolve parte da nitidez que a lente longa e a
 * interpolação de cor perdem, sem criar o que não foi capturado.
 *
 * - Só a luminância (cor não ganha borda colorida).
 * - Escala fina (σ ≈ 1 px): letras, fios, texturas; não mexe em formas grandes.
 * - Portão de ruído: diferenças abaixo de ~2σ do ruído medido não são realçadas (o grão não sobe).
 * - Sem halo: o resultado fica preso entre o mínimo e o máximo da vizinhança 3×3 original.
 */
object Detail {
    /** Intensidade por ampliação: tele 3×, 5× e 10× (mais perda de nitidez óptica); nada na principal. */
    fun amountFor(zoom: Float): Float = when {
        zoom >= 9f -> 0.6f
        zoom >= 4.5f -> 0.4f
        zoom >= 2.5f -> 0.3f
        // Principal: testado na 0.15 (selfie e planta, 12 MP) — 1,7% dos pixels mudam, diferença quase
        // invisível por ~1 s de processamento. Não compensa.
        else -> 0f
    }

    /**
     * Pele (faixa YCbCr clássica de tons de pele): o realce cai para 30% — poros e marcas não viram
     * "textura de lixa"; barba, cabelo, olhos e sobrancelhas (fora da faixa) recebem o realce inteiro.
     */
    private fun skinWeight(c: Int): Float {
        val r = Px.r(c); val g = Px.g(c); val b = Px.b(c)
        val cb = 128 - 0.168736f * r - 0.331264f * g + 0.5f * b
        val cr = 128 + 0.5f * r - 0.418688f * g - 0.081312f * b
        return if (cb in 77f..127f && cr in 133f..173f) 0.3f else 1f
    }

    private fun luma(c: Int) = (Px.r(c) * 77 + Px.g(c) * 150 + Px.b(c) * 29) shr 8

    /** Desfoque [1 4 6 4 1]/16 nas duas direções (σ ≈ 1 px). */
    private fun blur(y: IntArray, w: Int, h: Int): IntArray {
        val tmp = IntArray(w * h)
        for (r in 0 until h) {
            val o = r * w
            for (x in 0 until w) {
                val a = y[o + max(0, x - 2)]; val b = y[o + max(0, x - 1)]; val c = y[o + x]
                val d = y[o + min(w - 1, x + 1)]; val e = y[o + min(w - 1, x + 2)]
                tmp[o + x] = a + 4 * b + 6 * c + 4 * d + e
            }
        }
        val out = IntArray(w * h)
        for (r in 0 until h) {
            val a = max(0, r - 2) * w; val b = max(0, r - 1) * w; val c = r * w
            val d = min(h - 1, r + 1) * w; val e = min(h - 1, r + 2) * w
            for (x in 0 until w) out[c + x] = (tmp[a + x] + 4 * tmp[b + x] + 6 * tmp[c + x] + 4 * tmp[d + x] + tmp[e + x] + 128) shr 8
        }
        return out
    }

    /**
     * Ruído da foto (σ em níveis 0..255) pela mediana do |detalhe fino| — robusto a bordas, que
     * são minoria. Usado no portão.
     */
    fun noiseSigma(px: IntArray, w: Int, h: Int): Float {
        if (w < 5 || h < 5) return 0f
        val y = IntArray(w * h) { luma(px[it]) }
        val b = blur(y, w, h)
        val hist = IntArray(256)
        var n = 0
        for (r in 2 until h - 2) for (x in 2 until w - 2) { hist[min(255, abs(y[r * w + x] - b[r * w + x]))]++; n++ }
        var acc = 0
        for (i in 0 until 256) { acc += hist[i]; if (acc * 2 >= n) return max(0.5f, i * 1.4826f) }
        return 0.5f
    }

    /**
     * Aplica numa faixa [px] (w×h). [validTop]/[validBottom] delimitam as linhas que serão gravadas
     * (as demais são margem de vizinhança). Devolve quantos pixels mudaram.
     */
    fun applyBand(px: IntArray, w: Int, h: Int, amount: Float, sigma: Float, validTop: Int = 0, validBottom: Int = h): Int {
        if (amount <= 0f || w < 3 || h < 3) return 0
        if(PhotoKernels.available)return PhotoKernels.detail(px,w,h,amount,sigma,validTop,validBottom)
        val y = IntArray(w * h) { luma(px[it]) }
        val b = blur(y, w, h)
        val lo = 2f * sigma; val hi = 4f * sigma
        val out = px.copyOf()
        var changed = 0
        for (r in max(1, validTop) until min(h - 1, validBottom)) for (x in 1 until w - 1) {
            val i = r * w + x
            val d = (y[i] - b[i]).toFloat()
            val ad = abs(d)
            if (ad <= lo) continue
            val gate = if (ad >= hi) 1f else { val t = (ad - lo) / (hi - lo); t * t * (3f - 2f * t) }
            var mn = 255; var mx = 0
            for (dy in -1..1) for (dx in -1..1) { val v = y[i + dy * w + dx]; if (v < mn) mn = v; if (v > mx) mx = v }
            val target = (y[i] + amount * gate * skinWeight(px[i]) * d).coerceIn(mn.toFloat(), mx.toFloat())
            val delta = (target - y[i]).toInt()
            if (delta == 0) continue
            val c = px[i]
            out[i] = Px.argb((Px.r(c) + delta).coerceIn(0, 255), (Px.g(c) + delta).coerceIn(0, 255), (Px.b(c) + delta).coerceIn(0, 255))
            changed++
        }
        System.arraycopy(out, 0, px, 0, px.size)
        return changed
    }
}
