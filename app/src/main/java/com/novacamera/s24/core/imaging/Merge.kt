package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Fusão temporal robusta (redução de ruído multiquadro com rejeição de movimento).
 *
 * Cada quadro alinhado contribui com peso que cai quando ele difere da referência além do
 * esperado pelo ruído: áreas paradas são promediadas (ruído cai ~1/√N); pessoas/objetos que
 * se moveram ficam com a referência, evitando "fantasmas". Nada é sintetizado: a saída é sempre
 * uma média ponderada de valores medidos.
 */
object Merge {
    /**
     * Desvio-padrão do ruído (0..255) estimado pela mediana das diferenças entre pixels vizinhos
     * nas regiões mais planas (estimador robusto: bordas reais não inflam o resultado).
     */
    fun estimateNoise(px: IntArray, w: Int, h: Int, step: Int = 3): Float {
        val diffs = ArrayList<Float>()
        var y = 1
        while (y < h - 1) {
            var x = 1
            while (x < w - 2) {
                val c = px[y * w + x]
                val l = Px.luma(c) * 255f
                val r = Px.luma(px[y * w + x + 1]) * 255f
                val d = Px.luma(px[(y + 1) * w + x]) * 255f
                diffs += abs(l - r); diffs += abs(l - d)
                x += step
            }
            y += step
        }
        if (diffs.isEmpty()) return 1f
        val arr = diffs.toFloatArray().also { it.sort() }
        // 1º quartil das |diferenças| (regiões planas dominam). Para ruído gaussiano, a diferença
        // entre vizinhos tem desvio σ√2 e o 1º quartil do seu módulo vale 0,3186·σ√2.
        val q1 = arr[arr.size / 4]
        return max(0.5f, q1 / 0.3186f / sqrt(2f))
    }

    class Result(val pixels: IntArray, val rejectedFraction: Float)

    /**
     * Funde uma região: [ref] é a referência; [others] já recortados com o deslocamento de alinhamento.
     * [sigma] em unidades 0..255; [k] controla a tolerância (múltiplos de σ).
     */
    fun mergeTile(ref: IntArray, others: List<IntArray>, sigma: Float, k: Float = 4.5f): Result {
        val out = IntArray(ref.size)
        // Peso total até 2σ (diferença típica entre dois quadros só com ruído); cai suavemente até kσ.
        val lo = 2f * sigma
        val hi = k * sigma
        val lo2 = lo * lo; val hi2 = hi * hi
        var rejected = 0L; var total = 0L
        for (i in ref.indices) {
            val c = ref[i]
            val rr = Px.r(c); val rg = Px.g(c); val rb = Px.b(c)
            var sr = rr.toFloat(); var sg = rg.toFloat(); var sb = rb.toFloat(); var sw = 1f
            for (o in others) {
                val q = o[i]
                val dr = (Px.r(q) - rr).toFloat(); val dg = (Px.g(q) - rg).toFloat(); val db = (Px.b(q) - rb).toFloat()
                // Distância de cor dominada pela luminância; cor também conta para não misturar objetos diferentes.
                val d2 = (0.2126f * dr + 0.7152f * dg + 0.0722f * db).let { it * it } * 0.7f + (dr * dr + dg * dg + db * db) / 3f * 0.3f
                val w = when {
                    d2 <= lo2 -> 1f
                    d2 >= hi2 -> 0f
                    else -> { val t = (sqrt(d2) - lo) / (hi - lo); 1f - t * t * (3f - 2f * t) }
                }
                total++
                if (w < 0.05f) rejected++
                sr += w * Px.r(q); sg += w * Px.g(q); sb += w * Px.b(q); sw += w
            }
            out[i] = Px.argb((sr / sw + 0.5f).toInt(), (sg / sw + 0.5f).toInt(), (sb / sw + 0.5f).toInt())
        }
        return Result(out, if (total == 0L) 0f else rejected.toFloat() / total)
    }
}
