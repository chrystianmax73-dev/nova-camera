package com.novacamera.s24.core

import kotlin.math.sqrt

/**
 * A máscara da prévia vem do quadro da análise; a imagem desenhada vem da textura da câmera. Os dois
 * são o mesmo buffer do sensor, mas a convenção de linhas da textura (topo em t = 0 ou t = 1) e
 * eventuais espelhamentos dependem do aparelho. Em vez de presumir, compara-se uma grade de
 * luminância das duas fontes nas quatro orientações e fica a de maior correlação.
 */
object PreviewMaskCalibration {
    const val GW = 32
    const val GH = 24
    /** Abaixo disso a cena não diz nada (parede lisa, lente tapada): tenta de novo depois. */
    const val MIN_CORRELATION = 0.6f

    /** Média por célula numa grade GW×GH. Linha 0 da entrada = linha 0 da grade. */
    fun grid(luma: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(GW * GH); val n = IntArray(GW * GH)
        for (y in 0 until h) {
            val gy = y * GH / h
            for (x in 0 until w) { val i = gy * GW + x * GW / w; out[i] += luma[y * w + x]; n[i]++ }
        }
        for (i in out.indices) if (n[i] > 0) out[i] /= n[i]
        return out
    }

    /**
     * (espelho x, espelho y) tal que leitura(x, y) ≈ análise(x', y'); null se nenhuma orientação
     * correlaciona bem o suficiente.
     */
    fun bestFlip(readback: FloatArray, analysis: FloatArray): Pair<Boolean, Boolean>? {
        var best: Pair<Boolean, Boolean>? = null; var bestC = MIN_CORRELATION
        for (fx in listOf(false, true)) for (fy in listOf(false, true)) {
            val c = correlation(readback) { x, y -> analysis[(if (fy) GH - 1 - y else y) * GW + (if (fx) GW - 1 - x else x)] }
            if (c > bestC) { bestC = c; best = fx to fy }
        }
        return best
    }

    private inline fun correlation(a: FloatArray, b: (Int, Int) -> Float): Float {
        var sa = 0.0; var sb = 0.0; var saa = 0.0; var sbb = 0.0; var sab = 0.0
        val n = GW * GH
        for (y in 0 until GH) for (x in 0 until GW) {
            val va = a[y * GW + x].toDouble(); val vb = b(x, y).toDouble()
            sa += va; sb += vb; saa += va * va; sbb += vb * vb; sab += va * vb
        }
        val cov = sab / n - sa / n * sb / n
        val den = sqrt((saa / n - sa / n * sa / n) * (sbb / n - sb / n * sb / n))
        return if (den < 1e-9) 0f else (cov / den).toFloat()
    }
}
