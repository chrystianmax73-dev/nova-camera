package com.novacamera.s24.core

import kotlin.math.max
import kotlin.math.min

/**
 * Linhas lidas → blocos para a tela de texto (20/10: "muito genérico", caixas sobrepostas). Primeiro tira as
 * caixas repetidas (uma dentro da outra, ≥ 60% coberta por uma maior), depois junta linhas vizinhas do mesmo
 * parágrafo: altura parecida, pouco espaço entre elas e sobreposição horizontal. Cada bloco vira um toque e
 * um destaque só, como o texto ao vivo do iPhone.
 */
object TextBlocks {
    class Rect(val l: Float, val t: Float, val r: Float, val b: Float) {
        val w get() = r - l
        val h get() = b - t
        fun area() = max(0f, w) * max(0f, h)
        fun inter(o: Rect) = max(0f, min(r, o.r) - max(l, o.l)) * max(0f, min(b, o.b) - max(t, o.t))
        fun union(o: Rect) = Rect(min(l, o.l), min(t, o.t), max(r, o.r), max(b, o.b))
    }
    class Line(val text: String, val box: Rect)
    class Block(val lines: List<Line>) {
        val box: Rect = lines.drop(1).fold(lines.first().box) { acc, l -> acc.union(l.box) }
        val text get() = lines.joinToString("\n") { it.text }
    }

    /** Remove caixas quase inteiras dentro de outra maior (o detector às vezes repete pedaços da mesma linha). */
    fun dedupe(lines: List<Line>): List<Line> {
        val sorted = lines.sortedByDescending { it.box.area() }
        val kept = ArrayList<Line>()
        for (l in sorted) {
            val a = l.box.area()
            if (a <= 0f) continue
            if (kept.none { it.box.inter(l.box) >= 0.6f * a }) kept += l
        }
        return kept
    }

    fun group(lines: List<Line>): List<Block> {
        val clean = dedupe(lines).sortedBy { it.box.t }
        val blocks = ArrayList<MutableList<Line>>()
        for (l in clean) {
            val target = blocks.lastOrNull { b ->
                val last = b.last().box
                val hRatio = max(last.h, l.box.h) / max(1f, min(last.h, l.box.h))
                val gap = l.box.t - last.b
                val overlapX = max(0f, min(last.r, l.box.r) - max(last.l, l.box.l))
                hRatio < 1.8f && gap < 0.9f * max(last.h, l.box.h) && gap > -0.5f * last.h &&
                    overlapX >= 0.3f * min(last.w, l.box.w)
            }
            if (target != null) target += l else blocks += mutableListOf(l)
        }
        return blocks.map { Block(it.sortedWith(compareBy({ it.box.t }, { it.box.l }))) }.sortedWith(compareBy({ it.box.t }, { it.box.l }))
    }
}
