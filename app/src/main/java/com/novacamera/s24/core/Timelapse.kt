package com.novacamera.s24.core

import kotlin.math.max
import kotlin.math.roundToInt

/** Regras do Timelapse: um quadro a cada [intervalMs], vídeo final a [outputFps]. */
data class TimelapsePlan(val intervalMs: Long, val maxCaptureSeconds: Int, val outputFps: Int = 30) {
    /** Duração do vídeo resultante após [captureMs] de captura. */
    fun videoSecondsAfter(captureMs: Long): Float = framesAfter(captureMs).toFloat() / outputFps

    fun framesAfter(captureMs: Long): Int = (captureMs / intervalMs).toInt() + 1

    /** Fator de aceleração (ex.: 1 quadro/s a 30 fps = 30×). */
    val speedup: Int get() = (intervalMs * outputFps / 1000f).roundToInt()

    /** O quadro deve ser gravado agora? (a análise entrega ~30 fps; só um por intervalo é usado). */
    fun due(nowMs: Long, lastMs: Long?): Boolean = lastMs == null || nowMs - lastMs >= intervalMs - 15

    fun finished(captureMs: Long) = maxCaptureSeconds > 0 && captureMs >= maxCaptureSeconds * 1000L

    companion object {
        val INTERVALS_MS = listOf(500L, 1000L, 2000L, 5000L)
        val MAX_DURATIONS_S = listOf(0, 60, 300, 900, 1800)

        /** Tamanho do vídeo: lado maior até 1920, múltiplos de 16 (compatível com codificadores H.264). */
        fun outputSize(srcW: Int, srcH: Int, maxSide: Int = 1920): Pair<Int, Int> {
            val scale = minOf(1f, maxSide.toFloat() / max(srcW, srcH))
            fun even16(v: Float) = max(16, (v / 16).toInt() * 16)
            return even16(srcW * scale) to even16(srcH * scale)
        }

        fun intervalLabel(ms: Long) = if (ms < 1000) "${ms / 1000f}".replace(".", ",") + " s" else "${ms / 1000} s"
    }
}
