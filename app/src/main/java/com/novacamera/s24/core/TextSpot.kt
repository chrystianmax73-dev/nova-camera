package com.novacamera.s24.core

/**
 * "Tem texto na prévia?" a partir das caixas do detector (PP-OCRv5, prévia reduzida): mostra o botão de
 * texto como a câmera da Samsung. Texto = caixas em forma de linha (lado maior ≥ 2× o menor: deitada ou em pé,
 * como o "RECICLE-ME" da garrafa de 20/10); uma linha só
 * precisa ser grande (≥ 0,6% da cena), duas ou mais bastam. Liga após [ON] leituras seguidas com texto e
 * desliga após [OFF] sem, para o botão não piscar.
 */
object TextSpot {
    const val ON = 2
    const val OFF = 2

    fun hasText(boxes: List<OcrPost.Box>, w: Int, h: Int): Boolean {
        val lines = boxes.filter { val bw = it.right - it.left; val bh = it.bottom - it.top; bw > 0 && bh > 0 && maxOf(bw, bh) >= 2f * minOf(bw, bh) }
        if (lines.size >= 2) return true
        return lines.any { (it.right - it.left) * (it.bottom - it.top) >= 0.006f * w * h }
    }

    class Hysteresis {
        private var yes = 0; private var no = 0
        var visible = false; private set
        /** Devolve true quando [visible] mudou. */
        fun add(text: Boolean): Boolean {
            if (text) { yes++; no = 0 } else { no++; yes = 0 }
            val before = visible
            if (!visible && yes >= ON) visible = true
            if (visible && no >= OFF) visible = false
            return before != visible
        }
        fun reset() { yes = 0; no = 0; visible = false }
    }
}
