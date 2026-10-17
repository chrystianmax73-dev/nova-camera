package com.novacamera.s24.core

import kotlin.math.hypot

/** Região contínua de AF/AE; sem disparar um novo ciclo de foco a cada quadro analisado. */
class TrackingMeterGate {
    private var at = -1L
    private var x = 0f; private var y = 0f
    fun reset() { at = -1L }
    fun accept(nx: Float, ny: Float, now: Long): Boolean {
        if (at >= 0 && (now - at < 300 || (now - at < 1200 && hypot(nx - x, ny - y) < 0.015f))) return false
        at = now; x = nx; y = ny
        return true
    }
}
