package com.novacamera.s24

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Atalhos de zoom: só são habilitados dentro da faixa real reportada pela câmera ativa. */
object ZoomPresets {
    val PRESETS = listOf(0.6f, 1f, 3f, 5f, 10f, 20f, 30f)

    /** Tolerância para faixas como 0.6000001..99.99999 reportadas em ponto flutuante. */
    private const val EPSILON = 0.01f

    fun isSupported(value: Float, min: Float, max: Float): Boolean =
        value >= min - EPSILON && value <= max + EPSILON

    fun isActive(value: Float, current: Float): Boolean = abs(value - current) < 0.05f

    /** "1×", "10×", "0.6×", "2.4×" — uma casa decimal só quando necessária. */
    fun label(value: Float): String {
        val tenths = (value * 10).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}×"
        else String.format(Locale.US, "%.1f×", tenths / 10f)
    }
}
