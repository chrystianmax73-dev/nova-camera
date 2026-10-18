package com.novacamera.s24.core

import kotlin.math.abs

/**
 * Zoom arrastando o disparador. 0.27.1–0.27.3 seguiam o dedo de forma contínua e o dono via
 * "1.1, 1.2, 1.3…" mesmo mais rápido (0.27.3: "precisa de resposta precisa igual à dos números").
 * Desde 0.27.4 o arraste anda em DEGRAUS: cada [STEP_DP] dp sobe ou desce um valor da [ladder]
 * (paradas da lente + números inteiros), e cada degrau é animado como o toque numa parada.
 * O alvo vem do zoom do INÍCIO do gesto: voltar o dedo ao ponto de partida volta ao zoom inicial.
 */
object ShutterZoom {
    /** ~4 mm de dedo por degrau: 1× → 10× em ~2,5 cm. */
    const val STEP_DP = 26f
    /** Valores fora da ladder (2,4×) contam como "entre" dois degraus, com esta folga relativa. */
    private const val SAME = 0.01f
    private const val NEAR = 0.08f
    private val ABOVE_TEN = listOf(15f, 20f, 30f, 50f, 100f)

    /** Paradas reais + inteiros de 1 a 10 + 15/20/30/50/100, dentro de [min, max], em ordem. */
    fun ladder(stops: List<Float>, min: Float, max: Float): List<Float> {
        val real = stops.filter { it > 0f }
        // Um inteiro colado numa parada real (5× ao lado da tele de 5,1×) vira degrau inútil: fica a parada.
        val round = ((1..10).map { it.toFloat() } + ABOVE_TEN).filter { n -> real.none { abs(it / n - 1f) < NEAR } }
        val sorted = (real + round + listOf(min, max)).filter { it in min..max }.sorted()
        return sorted.fold(mutableListOf()) { acc, v ->
            if (acc.isEmpty() || v > acc.last() * (1f + SAME)) acc.add(v)
            acc
        }
    }

    fun target(startZoom: Float, totalDp: Float, stops: List<Float>, min: Float, max: Float): Float {
        if (startZoom <= 0f) return startZoom
        val steps = (totalDp / STEP_DP).toInt() // em direção a zero: precisa de um degrau inteiro
        if (steps == 0) return startZoom
        val ladder = ladder(stops, min, max)
        return if (steps > 0) {
            val up = ladder.filter { it > startZoom * (1f + SAME) }
            up.getOrNull(steps - 1) ?: up.lastOrNull() ?: startZoom
        } else {
            val down = ladder.filter { it < startZoom * (1f - SAME) }.reversed()
            down.getOrNull(-steps - 1) ?: down.lastOrNull() ?: startZoom
        }
    }
}
