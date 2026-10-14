package com.novacamera.s24.core.imaging

import kotlin.math.max

/** Escolha conservadora da referência de rajadas com a mesma exposição. Não recupera detalhe. */
object FrameQuality {
    data class Score(val detail: Float, val clipped: Float)

    fun score(luma: Plane): Score {
        val noise = Consistency.noiseOf(luma)
        var energy = 0.0
        var n = 0
        var clipped = 0
        for (v in luma.data) if (v >= 0.985f || v <= 0.01f) clipped++
        // Gradientes de dois pixels: desconta a energia esperada de ruído independente.
        for (y in 1 until luma.h - 1) for (x in 1 until luma.w - 1) {
            if (luma[x, y] !in 0.06f..0.94f) continue
            val dx = luma[x + 1, y] - luma[x - 1, y]
            val dy = luma[x, y + 1] - luma[x, y - 1]
            energy += max(0f, dx * dx + dy * dy - 4f * noise * noise)
            n++
        }
        return Score(if (n == 0) 0f else (energy / n).toFloat(), clipped.toFloat() / luma.data.size)
    }

    /** Só troca a primeira foto com margem de nitidez e sem aumentar recorte tonal. */
    fun best(scores: List<Score>): Int {
        require(scores.isNotEmpty())
        var best = 0
        for (i in 1 until scores.size) {
            if (scores[i].clipped <= scores[0].clipped + 0.01f &&
                scores[i].detail > max(1e-6f, scores[best].detail * 1.10f)) best = i
        }
        return best
    }
}
