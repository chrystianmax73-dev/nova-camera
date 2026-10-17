package com.novacamera.s24.core

import kotlin.math.abs

/** Dimensões sem depender da orientação; FPS observado vem das amostras do MP4, não do pedido. */
object VideoContract {
    data class Size(val width: Int, val height: Int) {
        val longSide get() = maxOf(width, height)
        val shortSide get() = minOf(width, height)
        fun sameFrame(other: Size) = longSide == other.longSide && shortSide == other.shortSide
        override fun toString() = "${width}×${height}"
    }
    data class Request(val size: Size?, val fps: Int, val variableFps: Boolean)

    fun violation(request: Request, size: Size, measuredFps: Double?): String? = when {
        request.size != null && !request.size.sameFrame(size) -> "Resolução diferente da selecionada (${request.size})"
        measuredFps == null -> "Taxa real de quadros não pôde ser medida"
        !request.variableFps && abs(measuredFps - request.fps) > maxOf(1.0, request.fps * 0.03) ->
            "Taxa real diferente do alvo de ${request.fps} fps"
        else -> null
    }

    /** PTS podem chegar fora de ordem (quadros B); usar extremos sem presumir taxa constante. */
    class Cadence {
        private var count = 0L
        private var first = Long.MAX_VALUE
        private var last = Long.MIN_VALUE
        fun sample(presentationUs: Long) {
            if (presentationUs < 0) return
            count++
            first = minOf(first, presentationUs)
            last = maxOf(last, presentationUs)
        }
        val fps: Double? get() = if (count > 1 && last > first)
            (count - 1) * 1_000_000.0 / (last - first) else null
    }
}
