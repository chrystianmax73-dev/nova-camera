package com.novacamera.s24.core

import kotlin.math.max
import kotlin.math.min

/**
 * Pós-processamento da leitura de texto (PP-OCRv5), sem OpenCV: caixas do mapa de probabilidade do
 * detector DB (componentes conectados + expansão proporcional à altura da linha, como o exemplo do ncnn
 * aproxima o "unclip") e decodificação CTC do reconhecedor. Texto horizontal; inclinado/vertical fica
 * fora nesta versão.
 */
object OcrPost {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float, val score: Float) {
        val width get() = right - left
        val height get() = bottom - top
    }

    const val THRESHOLD = 0.3f
    const val BOX_THRESHOLD = 0.6f
    const val ENLARGE = 1.95f
    const val MAX_BOXES = 80

    fun boxes(prob: FloatArray, w: Int, h: Int, minSize: Float = 3f): List<Box> {
        val label = IntArray(w * h)
        val out = ArrayList<Box>()
        val stack = IntArray(w * h)
        var next = 1
        for (start in 0 until w * h) {
            if (label[start] != 0 || prob[start] < THRESHOLD) continue
            val id = next++
            var sp = 0; stack[sp++] = start; label[start] = id
            var minX = w; var minY = h; var maxX = -1; var maxY = -1; var sum = 0f; var n = 0
            while (sp > 0) {
                val k = stack[--sp]; val x = k % w; val y = k / w
                minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
                sum += prob[k]; n++
                if (x > 0) { val j = k - 1; if (label[j] == 0 && prob[j] >= THRESHOLD) { label[j] = id; stack[sp++] = j } }
                if (x < w - 1) { val j = k + 1; if (label[j] == 0 && prob[j] >= THRESHOLD) { label[j] = id; stack[sp++] = j } }
                if (y > 0) { val j = k - w; if (label[j] == 0 && prob[j] >= THRESHOLD) { label[j] = id; stack[sp++] = j } }
                if (y < h - 1) { val j = k + w; if (label[j] == 0 && prob[j] >= THRESHOLD) { label[j] = id; stack[sp++] = j } }
            }
            val score = sum / n
            val bw = (maxX - minX + 1).toFloat(); val bh = (maxY - minY + 1).toFloat()
            if (score < BOX_THRESHOLD || max(bw, bh) < minSize || n <= 2) continue
            // Linha horizontal: altura × ENLARGE; comprimento + altura × (ENLARGE − 1).
            val short = min(bw, bh); val grow = short * (ENLARGE - 1f) / 2f
            val cx = (minX + maxX + 1) / 2f; val cy = (minY + maxY + 1) / 2f
            val nw = if (bw >= bh) bw + 2 * grow else bw * ENLARGE
            val nh = if (bw >= bh) bh * ENLARGE else bh + 2 * grow
            out += Box((cx - nw / 2).coerceAtLeast(0f), (cy - nh / 2).coerceAtLeast(0f),
                (cx + nw / 2).coerceAtMost(w.toFloat()), (cy + nh / 2).coerceAtMost(h.toFloat()), score)
            if (out.size >= MAX_BOXES) break
        }
        return out
    }

    /**
     * CTC: [steps] = (classe, prob) por passo, vindo do reconhecedor. Classe 0 = vazio; repetição seguida
     * vira uma letra só; classe além do dicionário = espaço. Devolve o texto e a confiança média.
     */
    fun decode(steps: FloatArray, dict: List<String>): Pair<String, Float> {
        val sb = StringBuilder()
        var last = 0; var sum = 0f; var n = 0
        for (i in 0 until steps.size / 2) {
            val idx = steps[2 * i].toInt()
            if (idx == last) continue
            last = idx
            if (idx <= 0) continue
            val id = idx - 1
            sum += steps[2 * i + 1]; n++
            if (id >= dict.size) { if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ') }
            else sb.append(dict[id])
        }
        return sb.toString().trim() to (if (n == 0) 0f else sum / n)
    }

    /** Ordem de leitura: linhas de cima para baixo (sobreposição vertical), cada uma da esquerda para a direita. */
    fun <T> readingOrder(items: List<T>, box: (T) -> Box): List<T> {
        val sorted = items.sortedBy { box(it).top }
        val lines = ArrayList<MutableList<T>>()
        for (it in sorted) {
            val b = box(it)
            val line = lines.lastOrNull()?.takeIf { l -> l.any { o -> val ob = box(o)
                min(ob.bottom, b.bottom) - max(ob.top, b.top) > 0.5f * min(ob.height, b.height) } }
            if (line != null) line += it else lines += mutableListOf(it)
        }
        return lines.flatMap { l -> l.sortedBy { box(it).left } }
    }
}
