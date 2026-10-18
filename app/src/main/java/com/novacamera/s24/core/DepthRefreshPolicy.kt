package com.novacamera.s24.core

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Prévia do Retrato (foto): a profundidade ao vivo é a etapa mais cara (diagnóstico 0.26.10: ~0,9 s de
 * CPU por cálculo, ~23% de um núcleo com a câmera parada). Com a cena parada ela dá o mesmo resultado,
 * então só é recalculada quando o enquadramento mudou desde o último cálculo (luminância média por
 * bloco), quando o toque mudou, ou depois de [MAX_AGE_MS] como garantia.
 */
class DepthRefreshPolicy {
    companion object {
        /** Mesma régua do vigia de cena do cinematográfico (diferença média de luminância por bloco). */
        const val CHANGE = 0.045
        const val TAP_CHANGE = 0.04f
        const val MAX_AGE_MS = 15_000L
    }

    private var lumaAtDepth: FloatArray? = null
    private var tapAtDepth: Pair<Float, Float>? = null
    private var atMs = Long.MIN_VALUE

    fun needed(nowMs: Long, luma: FloatArray, tap: Pair<Float, Float>?, maxAgeMs: Long = MAX_AGE_MS): Boolean {
        val old = lumaAtDepth ?: return true
        if (nowMs - atMs >= maxAgeMs) return true
        if ((tapAtDepth == null) != (tap == null)) return true
        val t0 = tapAtDepth
        if (t0 != null && tap != null && hypot(t0.first - tap.first, t0.second - tap.second) > TAP_CHANGE) return true
        if (old.size != luma.size) return true
        var d = 0.0
        for (i in luma.indices) d += abs(old[i] - luma[i])
        return d / luma.size > CHANGE
    }

    fun done(nowMs: Long, luma: FloatArray, tap: Pair<Float, Float>?) {
        lumaAtDepth = luma.copyOf(); tapAtDepth = tap; atMs = nowMs
    }

    fun reset() { lumaAtDepth = null; tapAtDepth = null; atMs = Long.MIN_VALUE }
}
