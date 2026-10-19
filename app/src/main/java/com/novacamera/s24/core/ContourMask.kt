package com.novacamera.s24.core

/**
 * Contorno do assunto tocado a partir da máscara do MagicTouch (Interactive Segmenter, Apache-2.0).
 * Só a borda é desenhada: marca o que foi entendido como assunto sem cobrir a imagem.
 */
object ContourMask {
    /**
     * Fora desta faixa de cobertura o contorno não é mostrado: abaixo, o objeto é pequeno/distante
     * (fora do uso previsto no model card); acima, o "assunto" virou a cena inteira (toque no fundo).
     */
    const val MIN_COVERAGE = 0.01f
    const val MAX_COVERAGE = 0.6f

    class Result(val w: Int, val h: Int, val edge: IntArray, val coverage: Float)

    /** [mask] = confiança 0..1 de "é o assunto"; devolve pixels ARGB da borda ([color]) ou null se não serve. */
    fun edges(mask: FloatArray, w: Int, h: Int, color: Int, threshold: Float = 0.5f): Result? {
        if (w <= 2 || h <= 2 || mask.size < w * h) return null
        var inside = 0
        for (v in mask) if (v > threshold) inside++
        val coverage = inside / (w * h).toFloat()
        if (coverage < MIN_COVERAGE || coverage > MAX_COVERAGE) return null
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (mask[i] <= threshold) continue
            val border = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                mask[i - 1] <= threshold || mask[i + 1] <= threshold || mask[i - w] <= threshold || mask[i + w] <= threshold
            if (border) out[i] = color
        }
        return Result(w, h, out, coverage)
    }
}
