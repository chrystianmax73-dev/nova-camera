package com.novacamera.s24.core.imaging

import kotlin.math.max

/** Escolha conservadora da referência de rajadas com a mesma exposição. Não recupera detalhe. */
object FrameQuality {
    data class Score(val detail: Float, val clipped: Float, val noise: Float = 0f)

    fun score(luma: Plane): Score {
        val noise = Consistency.noiseOf(luma,correlated=true)
        var energy = 0.0
        var n = 0
        var clipped = 0
        for (v in luma.data) if (v >= 0.985f || v <= 0.01f) clipped++
        // Gradientes de dois pixels: desconta a energia esperada de ruído independente.
        for (y in 1 until luma.h - 1) for (x in 1 until luma.w - 1) {
            if (luma[x, y] !in 0.06f..0.94f) continue
            val dx = luma[x + 1, y] - luma[x - 1, y]
            val dy = luma[x, y + 1] - luma[x, y - 1]
            // Cortar cada diferença negativa antes de somar faz ruído puro parecer detalhe.
            energy += dx * dx + dy * dy
            n++
        }
        return Score(if (n == 0) 0f else max(0f,(energy / n).toFloat()-4f*noise*noise),
            clipped.toFloat() / luma.data.size,noise)
    }

    /** Só troca a primeira foto com margem de nitidez e sem aumentar recorte tonal. */
    fun best(scores: List<Score>): Int {
        require(scores.isNotEmpty())
        var best = 0
        for (i in 1 until scores.size) {
            // Política conservadora: um quadro mais ruidoso precisa demonstrar ganho de detalhe
            // além do aumento esperado de energia de ruído; não é estimativa de detalhe óptico.
            val noisePenalty=4f*max(0f,scores[i].noise*scores[i].noise-scores[best].noise*scores[best].noise)
            if (scores[i].clipped <= scores[0].clipped + 0.01f &&
                scores[i].detail > max(1e-6f, scores[best].detail * 1.10f)+noisePenalty) best = i
        }
        return best
    }
}
