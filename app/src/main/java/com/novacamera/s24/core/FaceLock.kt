package com.novacamera.s24.core

import kotlin.math.hypot
import kotlin.math.max

/**
 * Foco travado no rosto tocado (vídeo). Os rostos vêm do próprio sensor (modo simples, sem CPU do app);
 * aqui só se decide qual deles é o tocado e qual continua sendo ele no quadro seguinte.
 */
object FaceLock {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val cx get() = (left + right) / 2f
        val cy get() = (top + bottom) / 2f
        val size get() = max(right - left, bottom - top)
    }

    /** Folga em volta do rosto para aceitar o toque (dedo cai na testa, no queixo). */
    const val TAP_MARGIN = 0.35f
    /** Sem rosto por mais que isto, passa ao rastreio por aparência na última posição. */
    const val HANDOFF_MS = 800L

    fun pick(faces: List<Box>, x: Float, y: Float): Box? = faces.filter { f ->
        val m = f.size * TAP_MARGIN
        x >= f.left - m && x <= f.right + m && y >= f.top - m && y <= f.bottom + m
    }.minByOrNull { hypot(it.cx - x, it.cy - y) }

    /** O mesmo rosto no quadro seguinte: perto (até um rosto de distância) e de tamanho parecido. */
    fun follow(faces: List<Box>, previous: Box): Box? = faces.filter { f ->
        val ratio = f.size / previous.size.coerceAtLeast(1f)
        ratio in 0.6f..1.7f && hypot(f.cx - previous.cx, f.cy - previous.cy) <= previous.size * 1.2f
    }.minByOrNull { hypot(it.cx - previous.cx, it.cy - previous.cy) }
}
