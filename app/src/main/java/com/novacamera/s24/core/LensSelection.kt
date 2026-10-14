package com.novacamera.s24.core

import kotlin.math.abs

/**
 * Uma câmera exposta pelo Android ao aplicativo (pode ser lógica, agrupando várias lentes físicas).
 * [intrinsic] é o campo de visão relativo à câmera principal (1.0 = principal, 0.6 = ultra-angular).
 */
data class LensOption(
    val cameraId: String,
    val intrinsic: Float,
    val minZoom: Float,
    val maxZoom: Float,
) {
    /** Faixa de ampliação equivalente (em relação à principal) que esta câmera cobre. */
    val effectiveMin get() = intrinsic * minZoom
    val effectiveMax get() = intrinsic * maxZoom
    fun covers(target: Float) = target >= effectiveMin - 0.01f && target <= effectiveMax + 0.01f
}

data class LensChoice(val cameraId: String, val zoomOnLens: Float)

object LensSelection {
    /**
     * Escolhe a câmera para uma ampliação equivalente.
     * Preferência: a câmera atual (evita reabrir a sessão); depois a padrão; depois a de maior
     * campo de visão nativo que ainda cubra o alvo sem zoom digital abaixo de 1.
     */
    fun choose(lenses: List<LensOption>, currentId: String?, defaultId: String?, target: Float): LensChoice? {
        if (lenses.isEmpty()) return null
        fun choice(l: LensOption) = LensChoice(l.cameraId, (target / l.intrinsic).coerceIn(l.minZoom, l.maxZoom))
        lenses.firstOrNull { it.cameraId == currentId && it.covers(target) }?.let { return choice(it) }
        lenses.firstOrNull { it.cameraId == defaultId && it.covers(target) }?.let { return choice(it) }
        lenses.filter { it.covers(target) && it.intrinsic <= target + 0.01f }
            .maxByOrNull { it.intrinsic }?.let { return choice(it) }
        return lenses.filter { it.covers(target) }.minByOrNull { abs(it.intrinsic - target) }?.let(::choice)
    }

    /** Paradas da barra de zoom realmente alcançáveis por alguma câmera disponível. */
    fun reachableStops(lenses: List<LensOption>, candidates: List<Float>): List<Float> =
        candidates.filter { c -> lenses.any { it.covers(c) } }
}
