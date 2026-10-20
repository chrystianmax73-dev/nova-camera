package com.novacamera.s24.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Degraus táteis do zoom (pesquisa de 20/10: dial com detentes como iPhone/Pixel). Durante o gesto do usuário:
 * um tique leve a cada passo ([step]: 0,1× até 3×, 0,5× até 10×, 1× acima) e um tique forte ao passar por uma
 * parada óptica (0,6/1/3/5/10…). O tique leve é limitado a um a cada [MIN_STEP_MS] para não virar zumbido.
 */
object ZoomDetent {
    enum class Kind { NONE, STEP, STOP }
    const val MIN_STEP_MS = 35L

    fun step(z: Float) = when { z < 3f -> 0.1f; z < 10f -> 0.5f; else -> 1f }

    fun crossed(prev: Float, now: Float, stops: List<Float>): Kind {
        if (prev == now) return Kind.NONE
        val lo = min(prev, now); val hi = max(prev, now)
        // Chegar numa parada conta nos dois sentidos: subindo (prev, now], descendo [now, prev).
        val e = 1e-3f
        val hit = if (now > prev) stops.any { it > prev + e && it <= now + e } else stops.any { it >= now - e && it < prev - e }
        if (hit) return Kind.STOP
        val s = step(lo)
        return if (floor(lo / s + 1e-4f) != floor(hi / s + 1e-4f)) Kind.STEP else Kind.NONE
    }
}
