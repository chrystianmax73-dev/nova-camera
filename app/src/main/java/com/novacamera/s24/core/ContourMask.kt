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

    /** [focus]: retângulo dentro do objeto tocado (normalizado 0..1 da prévia) para a região do AF. */
    class Result(val w: Int, val h: Int, val edge: IntArray, val coverage: Float, val focus: FloatArray? = null)

    /** Lado mínimo/máximo da região de foco, relativo ao menor lado da prévia. */
    const val FOCUS_MIN = 0.03f
    const val FOCUS_MAX = 0.2f
    /** Fração de cada nova linha/coluna que precisa ser do objeto para o retângulo crescer. */
    private const val FOCUS_FILL = 0.9f

    /**
     * Maior retângulo em volta do toque que fica dentro do objeto: o AF mede só o objeto, sem o fundo
     * que a região padrão (15% da imagem) pegaria nas bordas. Devolve l,t,r,b normalizados ou null.
     */
    fun focusBox(mask: FloatArray, w: Int, h: Int, nx: Float, ny: Float, threshold: Float = 0.5f): FloatArray? {
        if (w <= 2 || h <= 2 || mask.size < w * h) return null
        var tx = (nx * (w - 1)).toInt().coerceIn(0, w - 1); var ty = (ny * (h - 1)).toInt().coerceIn(0, h - 1)
        if (mask[ty * w + tx] <= threshold) {
            // Dedo na borda do objeto: o ponto do objeto mais perto, até 3% da prévia.
            val r = maxOf(2, (minOf(w, h) * 0.03f).toInt())
            var best = Int.MAX_VALUE
            var bx = -1; var by = -1
            for (y in maxOf(0, ty - r)..minOf(h - 1, ty + r)) for (x in maxOf(0, tx - r)..minOf(w - 1, tx + r)) {
                val d = (x - tx) * (x - tx) + (y - ty) * (y - ty)
                if (mask[y * w + x] > threshold && d < best) { best = d; bx = x; by = y }
            }
            if (bx < 0) return null
            tx = bx; ty = by
        }
        val maxSide = (minOf(w, h) * FOCUS_MAX).toInt().coerceAtLeast(1)
        var l = tx; var r = tx; var t = ty; var b = ty
        fun rowFill(y: Int, x0: Int, x1: Int): Boolean { var n = 0; for (x in x0..x1) if (mask[y * w + x] > threshold) n++; return n >= (x1 - x0 + 1) * FOCUS_FILL }
        fun colFill(x: Int, y0: Int, y1: Int): Boolean { var n = 0; for (y in y0..y1) if (mask[y * w + x] > threshold) n++; return n >= (y1 - y0 + 1) * FOCUS_FILL }
        var grew = true
        while (grew) {
            grew = false
            if (r - l + 1 < maxSide) {
                if (l > 0 && colFill(l - 1, t, b)) { l--; grew = true }
                if (r - l + 1 < maxSide && r < w - 1 && colFill(r + 1, t, b)) { r++; grew = true }
            }
            if (b - t + 1 < maxSide) {
                if (t > 0 && rowFill(t - 1, l, r)) { t--; grew = true }
                if (b - t + 1 < maxSide && b < h - 1 && rowFill(b + 1, l, r)) { b++; grew = true }
            }
        }
        val minSide = minOf(w, h) * FOCUS_MIN
        if (r - l + 1 < minSide || b - t + 1 < minSide) return null
        return floatArrayOf(l.toFloat() / w, t.toFloat() / h, (r + 1f) / w, (b + 1f) / h)
    }

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
