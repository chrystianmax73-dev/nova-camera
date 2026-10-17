package com.novacamera.s24.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Lógica pura (sem Android) usada pela câmera e pela interface.
 * Mantida isolada para ser coberta por testes unitários.
 */
object CameraMath {
    private val PT_BR = Locale.forLanguageTag("pt-BR")

    // ---------- Orientação ----------

    /** Ângulo do aparelho (0/90/180/270) com histerese para não alternar perto das diagonais. */
    fun snapOrientation(rawDegrees: Int, current: Int): Int {
        if (rawDegrees < 0) return current
        val nearest = ((rawDegrees + 45) / 90 % 4) * 90
        if (nearest == current) return current
        val distance = minOf(abs(rawDegrees - nearest), 360 - abs(rawDegrees - nearest))
        return if (distance <= 30) nearest else current
    }

    /** Valor de android.view.Surface.ROTATION_* para o ângulo físico do aparelho. */
    fun surfaceRotationFor(orientationDegrees: Int): Int = when (orientationDegrees) {
        90 -> 3   // ROTATION_270
        180 -> 2  // ROTATION_180
        270 -> 1  // ROTATION_90
        else -> 0 // ROTATION_0
    }

    /** Rotação a aplicar nos ícones para ficarem "em pé" para o usuário. */
    fun iconRotationFor(orientationDegrees: Int): Float = when (orientationDegrees) {
        90 -> -90f
        180 -> 180f
        270 -> 90f
        else -> 0f
    }

    /**
     * Deslocamento horizontal *do ponto de vista do usuário*, com a tela travada em retrato.
     * Com o aparelho deitado, o "horizontal" percebido corresponde ao eixo vertical da tela.
     */
    fun userHorizontalDelta(dx: Float, dy: Float, orientationDegrees: Int): Float = when (orientationDegrees) {
        90 -> -dy
        180 -> -dx
        270 -> dy
        else -> dx
    }

    // ---------- Exposição ----------

    fun evForIndex(index: Int, stepNumerator: Int, stepDenominator: Int): Double =
        if (stepDenominator == 0) 0.0 else index.toDouble() * stepNumerator / stepDenominator

    fun indexForEv(ev: Double, stepNumerator: Int, stepDenominator: Int, min: Int, max: Int): Int {
        if (stepNumerator == 0 || stepDenominator == 0) return 0
        val step = stepNumerator.toDouble() / stepDenominator
        return (ev / step).roundToInt().coerceIn(min, max)
    }

    /** "±0", "+0,7", "−1,3" (sinal de menos tipográfico). */
    fun formatEv(ev: Double): String {
        val rounded = (ev * 10).roundToInt() / 10.0
        if (rounded == 0.0) return "±0"
        val body = String.format(PT_BR, "%.1f", abs(rounded))
        return (if (rounded > 0) "+" else "−") + body
    }

    // ---------- Zoom ----------

    /** Aproxima [current] de [target] em escala logarítmica (percepção de zoom é multiplicativa). */
    fun zoomStep(current: Float, target: Float, factor: Float): Float {
        if (current <= 0f || target <= 0f) return target
        val next = exp(ln(current) + (ln(target) - ln(current)) * factor.coerceIn(0f, 1f))
        return if (abs(ln(next / target)) < 0.003f) target else next
    }

    /**
     * Converte uma fração [0,1] ao longo da barra de zoom em ampliação, de forma linear em log
     * entre as paradas adjacentes (cada parada ocupa a mesma largura visual).
     */
    fun zoomForFraction(stops: List<Float>, fraction: Float): Float {
        if (stops.isEmpty()) return 1f
        if (stops.size == 1) return stops[0]
        val position = fraction.coerceIn(0f, 1f) * (stops.size - 1)
        val i = position.toInt().coerceAtMost(stops.size - 2)
        val t = position - i
        return exp(ln(stops[i]) + (ln(stops[i + 1]) - ln(stops[i])) * t)
    }

    fun fractionForZoom(stops: List<Float>, zoom: Float): Float {
        if (stops.size < 2) return 0f
        if (zoom <= stops.first()) return 0f
        if (zoom >= stops.last()) return 1f
        for (i in 0 until stops.size - 1) {
            if (zoom <= stops[i + 1]) {
                val t = (ln(zoom) - ln(stops[i])) / (ln(stops[i + 1]) - ln(stops[i]))
                return (i + t) / (stops.size - 1)
            }
        }
        return 1f
    }

    // ---------- Controles Pro ----------

    private val ISO_STOPS = listOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000,
        1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800)

    fun isoStops(min: Int, max: Int): List<Int> = ISO_STOPS.filter { it in min..max }

    private const val SECOND_NS = 1_000_000_000L
    private val SHUTTER_DENOMINATORS = listOf(8000, 6400, 5000, 4000, 3200, 2500, 2000, 1600, 1250, 1000,
        800, 640, 500, 400, 320, 250, 200, 160, 125, 100, 80, 60, 50, 40, 30, 25, 20, 15, 10, 8, 6, 5, 4, 3, 2)
    private val SHUTTER_SECONDS = listOf(1.0, 2.0, 4.0, 8.0, 15.0, 30.0)

    /** Tempos de exposição padronizados (ns), do mais rápido ao mais lento, dentro da faixa do sensor. */
    fun shutterStops(minNs: Long, maxNs: Long): List<Long> {
        val all = SHUTTER_DENOMINATORS.map { SECOND_NS / it } + SHUTTER_SECONDS.map { (it * SECOND_NS).roundToLong() }
        return all.filter { it in minNs..maxNs }
    }

    /** "1/125", "1/4", "0,8 s", "2 s". */
    fun shutterLabel(ns: Long): String {
        if (ns <= 0) return "—"
        if (ns >= SECOND_NS * 7 / 10) {
            val seconds = ns.toDouble() / SECOND_NS
            return if (abs(seconds - seconds.roundToInt()) < 0.05) "${seconds.roundToInt()} s"
            else String.format(PT_BR, "%.1f s", seconds)
        }
        return "1/${(SECOND_NS.toDouble() / ns).roundToInt()}"
    }

    /** Distância de foco em dioptrias → rótulo. 0 = infinito. */
    fun focusLabel(diopters: Float): String {
        if (diopters <= 0.01f) return "∞"
        val meters = 1f / diopters
        return if (meters < 1f) "${(meters * 100).roundToInt()} cm"
        else String.format(PT_BR, "%.1f m", meters)
    }

    /** Divide [0, maxDiopters] em passos uniformes em dioptria (mais resolução perto). */
    fun focusStops(maxDiopters: Float, count: Int = 40): List<Float> {
        if (maxDiopters <= 0f || count < 2) return emptyList()
        return (0 until count).map { maxDiopters * it / (count - 1) }
    }

    /** Índice do valor mais próximo em escala logarítmica (ISO, tempos de exposição). */
    fun nearestLogIndex(values: List<Double>, target: Double): Int {
        if (values.isEmpty() || target <= 0.0) return 0
        return values.indices.minByOrNull { abs(ln(values[it] / target)) } ?: 0
    }

    /**
     * Quanto a combinação manual expõe a mais (+) ou a menos (−) que a última medição automática, em EV.
     * Ex.: ISO 1600→50 com o mesmo tempo = −5 EV (imagem praticamente preta).
     */
    fun meterEv(iso: Int, exposureNs: Long, refIso: Int, refExposureNs: Long): Double {
        if (iso <= 0 || exposureNs <= 0 || refIso <= 0 || refExposureNs <= 0) return 0.0
        return ln(iso.toDouble() * exposureNs / (refIso.toDouble() * refExposureNs)) / ln(2.0)
    }

    fun formatDuration(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }
}
