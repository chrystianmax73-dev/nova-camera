package com.novacamera.s24.core

import kotlin.math.roundToInt

/**
 * Prévia da IA na região ampliada da revisão: qual pedaço da foto em resolução cheia refinar.
 * Até [MAX_SIDE]² (16 blocos da rede, segundos) no centro do que está na tela; nunca menor que o bloco
 * da rede ([MIN_SIDE]). A foto inteira fica para o pedido explícito.
 */
object RegionRefine {
    const val MAX_SIDE = 992 // 16 blocos, abaixo do limite de 1 MP da rede na CPU
    const val MIN_SIDE = 288
    const val MIN_ZOOM = 2f

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** Recorte (imagem já orientada, w×h) para a área visível em coordenadas normalizadas. */
    /** [half]: a foto grande passa pela rede em meia resolução; o recorte precisa de 2× o bloco. */
    fun crop(nl: Float, nt: Float, nr: Float, nb: Float, w: Int, h: Int, half: Boolean = false): Box? {
        val minSide = if (half) 2 * MIN_SIDE else MIN_SIDE
        if (w < minSide || h < minSide || nr <= nl || nb <= nt) return null
        fun span(a: Float, b: Float, size: Int): Pair<Int, Int> {
            val lo = (a.coerceIn(0f, 1f) * size).roundToInt(); val hi = (b.coerceIn(0f, 1f) * size).roundToInt()
            val len = (hi - lo).coerceIn(minSide, maxOf(minSide, MAX_SIDE))
            val start = ((lo + hi) / 2 - len / 2).coerceIn(0, size - len)
            return start to start + len
        }
        val (l, r) = span(nl, nr, w); val (t, b) = span(nt, nb, h)
        return Box(l, t, r, b)
    }

    /**
     * Mesma caixa no JPEG gravado (sem a rotação EXIF). [rotation] é a rotação horária aplicada para
     * exibir; [w]×[h] são as dimensões já orientadas.
     */
    fun unrotated(box: Box, rotation: Int, w: Int, h: Int): Box = when (((rotation % 360) + 360) % 360) {
        90 -> Box(box.top, w - box.right, box.bottom, w - box.left)
        180 -> Box(w - box.right, h - box.bottom, w - box.left, h - box.top)
        270 -> Box(h - box.bottom, box.left, h - box.top, box.right)
        else -> box
    }
}
