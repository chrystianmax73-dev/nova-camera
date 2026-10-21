package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ruído de um bloco antes da IA: mediana do laplaciano da luminância, convertida em σ (8 bits) supondo
 * ruído gaussiano. Contorno e textura só aumentam a estimativa, então um bloco detalhado nunca é
 * pulado por engano; só céu, parede e fundo já limpos deixam de passar pela rede.
 */
object TileNoise {
    /** Abaixo disto o bloco já está limpo para a rede (que mistura 75%): pular não muda a foto. */
    const val CLEAN_SIGMA = 1.2f
    private val SCALE = 1f / (0.6745f * sqrt(20f))

    fun sigma(argb: IntArray, w: Int, h: Int, step: Int = 2): Float {
        if (w < 3 || h < 3) return Float.MAX_VALUE
        val hist = IntArray(1021 * 3)
        var n = 0
        fun luma(p: Int) = ((p shr 16 and 255) * 77 + (p shr 8 and 255) * 150 + (p and 255) * 29) shr 8
        for (y in 1 until h - 1 step step) for (x in 1 until w - 1 step step) {
            val i = y * w + x
            val l = 4 * luma(argb[i]) - luma(argb[i - 1]) - luma(argb[i + 1]) - luma(argb[i - w]) - luma(argb[i + w])
            hist[abs(l).coerceAtMost(hist.size - 1)]++; n++
        }
        var seen = 0
        for (v in hist.indices) { seen += hist[v]; if (seen * 2 >= n) return v * SCALE }
        return Float.MAX_VALUE
    }

    fun clean(argb: IntArray, w: Int, h: Int) = sigma(argb, w, h) < CLEAN_SIGMA
}
