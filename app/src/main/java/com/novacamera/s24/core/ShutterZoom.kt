package com.novacamera.s24.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Zoom arrastando o disparador (dono, 0.27.1: "no 3x ele não pula pro 4 ou 5, vai 3.1, 3.2…").
 * O alvo é calculado a partir do zoom do INÍCIO do gesto (não acumula arredondamentos) e:
 * - [DP_PER_E] dp de arraste multiplicam o zoom por e (3× → 5× em ~60 dp, ~1 cm);
 * - perto de uma parada real da lente (0,6 · 1 · 3 · 5 · 10…), o zoom gruda nela ([DETENT]) — dá para
 *   parar exatamente em 3× ou 5× sem acertar o milímetro.
 */
object ShutterZoom {
    const val DP_PER_E = 110f
    /** Faixa de "imã" em torno de cada parada, em ln(zoom) (≈ ±6%). */
    const val DETENT = 0.06f

    fun target(startZoom: Float, totalDp: Float, stops: List<Float>, min: Float, max: Float): Float {
        if (startZoom <= 0f) return startZoom
        val raw = (startZoom * exp(totalDp / DP_PER_E)).coerceIn(min, max)
        return stops.firstOrNull { it > 0f && abs(ln(raw / it)) < DETENT } ?: raw
    }
}
