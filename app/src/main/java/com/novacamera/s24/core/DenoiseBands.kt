package com.novacamera.s24.core

/**
 * IA em faixas, no próprio bitmap: o resultado de uma faixa de blocos só volta para a foto quando
 * nenhuma faixa seguinte vai mais ler aquelas linhas como contexto (margem da rede). Assim a memória
 * extra é de ~3 faixas (≈19 MB numa foto de 49 MP) em vez de uma cópia inteira (≈196 MB), o que
 * permite a IA nas fotos de Alta resolução.
 */
object DenoiseBands {
    /** Primeira linha lida (com margem) pelos blocos da faixa [band]. */
    fun inputTop(band: Int, h: Int, tile: Int, pad: Int): Int = (band * tile - pad).coerceIn(0, maxOf(0, h - (tile + 2 * pad)))

    /** Última linha (exclusiva) que a faixa [band] escreve. */
    fun outputEnd(band: Int, h: Int, tile: Int): Int = minOf(h, (band + 1) * tile)

    /** A faixa já processada [band] pode ser gravada antes de a faixa [next] começar a ler. */
    fun writable(band: Int, next: Int, h: Int, tile: Int, pad: Int): Boolean = outputEnd(band, h, tile) <= inputTop(next, h, tile, pad)
}
