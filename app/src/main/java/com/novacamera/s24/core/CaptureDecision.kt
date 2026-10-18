package com.novacamera.s24.core

/**
 * Quando vale capturar mais de um quadro. Multiquadro tem custo (tempo, calor, risco de fantasma
 * se algo se mexe) e só é feito quando a cena realmente pede.
 */
object CaptureDecision {
    /** Noturno: rajada só com pouca luz de verdade (ISO alto ou exposição longa). */
    fun nightFrames(iso: Int?, exposureNs: Long?, maxFrames: Int): Pair<Int, String?> {
        if (maxFrames <= 1) return 1 to "Aparelho quente: um quadro, sem fusão"
        if (iso == null || exposureNs == null) return maxFrames to null
        val bright = iso < 500 && exposureNs < 40_000_000L
        if (bright) return 1 to "Luz suficiente: um quadro, sem fusão"
        // Limita apenas o tempo de exposição acumulado; overhead CameraX não está incluído.
        val byExposure = if (exposureNs > 0L) (1_200_000_000L / exposureNs).coerceIn(1L, maxFrames.toLong()).toInt() else maxFrames
        return byExposure to if (byExposure < maxFrames) "Exposição longa: rajada reduzida para preservar estabilidade" else null
    }

    /**
     * HDR: dos passos planejados (0, subexposto, superexposto), mantém só os que a referência
     * precisa: subexposto se há altas luzes estouradas; superexposto se há sombras profundas.
     */
    fun hdrSteps(planned: List<Int>, clippedFraction: Float, deepShadowFraction: Float): Pair<List<Int>, String?> {
        val keep = planned.filter { it == 0 || (it < 0 && clippedFraction >= 0.006f) || (it > 0 && deepShadowFraction >= 0.04f) }
        val note = if (keep.size == 1) "Sem altas luzes estouradas nem sombras profundas: um quadro, sem fusão" else null
        return keep to note
    }
}

