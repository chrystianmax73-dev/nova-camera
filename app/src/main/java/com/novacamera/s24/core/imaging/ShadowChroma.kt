package com.novacamera.s24.core.imaging

/**
 * Cor falsa nas sombras com ISO alto: o ruído de cor do sensor vira manchas roxas e azuladas no
 * escuro (frasco 5× no Retrato, ISO 3069, 0.17.0) e, depois do desfoque do retrato, "aquarela".
 * Como o ISP das câmeras, a saturação das sombras profundas cai em proporção ao ISO; a luminância
 * (detalhe) fica intacta e meios-tons e claros não mudam.
 */
object ShadowChroma {
    /** Intensidade pelo ISO: nada até 800, máxima (0,8) a partir de 3200. */
    fun strengthFor(iso: Int?): Float = if (iso == null) 0f else ((iso - 800) / 2400f).coerceIn(0f, 1f) * 0.8f

    /**
     * Peso pela luminância (0..1, sRGB): sombra profunda inteira até 0,04, some até 0,12. (Até 0,25
     * acinzentava a madeira numa foto escura, onde o conteúdo vive entre 0,15 e 0,25.)
     */
    private fun weight(y: Float): Float {
        val t = ((0.12f - y) / (0.12f - 0.04f)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun applyTile(px: IntArray, strength: Float) {
        if (strength <= 0f) return
        for (i in px.indices) {
            val c = px[i]
            val r = Px.r(c); val g = Px.g(c); val b = Px.b(c)
            val y = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            val k = strength * weight(y)
            if (k <= 0.001f) continue
            val l = y * 255f
            px[i] = Px.argb((r + (l - r) * k + 0.5f).toInt().coerceIn(0, 255),
                (g + (l - g) * k + 0.5f).toInt().coerceIn(0, 255), (b + (l - b) * k + 0.5f).toInt().coerceIn(0, 255))
        }
    }
}
