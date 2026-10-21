package com.novacamera.s24.core

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Folha na foto (documento): a maior região clara e uniforme (papel sobre a mesa), limiar de Otsu na
 * luminância reduzida, e os 4 cantos pelos extremos de x+y e x−y. Sem rede e sem OpenCV; folha escura
 * em fundo claro ou cena sem folha → null (a foto fica como está).
 */
object DocQuad {
    /** Cantos em ordem: superior-esquerdo, superior-direito, inferior-direito, inferior-esquerdo (pixels da imagem dada). */
    data class Quad(val pts: FloatArray) {
        val widthPx get() = max(hypot(pts[2] - pts[0], pts[3] - pts[1]), hypot(pts[4] - pts[6], pts[5] - pts[7]))
        val heightPx get() = max(hypot(pts[6] - pts[0], pts[7] - pts[1]), hypot(pts[4] - pts[2], pts[5] - pts[3]))
    }

    const val MIN_AREA = 0.15f

    fun find(luma: IntArray, w: Int, h: Int): Quad? {
        if (w < 16 || h < 16) return null
        val hist = IntArray(256); for (v in luma) hist[v.coerceIn(0, 255)]++
        val t = otsu(hist, luma.size)
        // Maior componente claro (4-vizinhos), ignorando o que encosta em mais de 2 bordas (fundo claro).
        val label = IntArray(w * h); val stack = IntArray(w * h)
        var bestId = -1; var bestN = 0; var next = 1
        for (s in 0 until w * h) {
            if (label[s] != 0 || luma[s] <= t) continue
            val id = next++; var sp = 0; stack[sp++] = s; label[s] = id; var n = 0
            var touchL = false; var touchR = false; var touchT = false; var touchB = false
            while (sp > 0) {
                val k = stack[--sp]; val x = k % w; val y = k / w; n++
                if (x == 0) touchL = true; if (x == w - 1) touchR = true; if (y == 0) touchT = true; if (y == h - 1) touchB = true
                if (x > 0 && label[k - 1] == 0 && luma[k - 1] > t) { label[k - 1] = id; stack[sp++] = k - 1 }
                if (x < w - 1 && label[k + 1] == 0 && luma[k + 1] > t) { label[k + 1] = id; stack[sp++] = k + 1 }
                if (y > 0 && label[k - w] == 0 && luma[k - w] > t) { label[k - w] = id; stack[sp++] = k - w }
                if (y < h - 1 && label[k + w] == 0 && luma[k + w] > t) { label[k + w] = id; stack[sp++] = k + w }
            }
            val borders = listOf(touchL, touchR, touchT, touchB).count { it }
            if (borders <= 2 && n > bestN) { bestN = n; bestId = id }
        }
        if (bestId < 0 || bestN < MIN_AREA * w * h) return null
        var tl = -1; var tr = -1; var br = -1; var bl = -1
        var sMin = Int.MAX_VALUE; var sMax = Int.MIN_VALUE; var dMin = Int.MAX_VALUE; var dMax = Int.MIN_VALUE
        for (k in 0 until w * h) if (label[k] == bestId) {
            val x = k % w; val y = k / w; val s = x + y; val d = x - y
            if (s < sMin) { sMin = s; tl = k }; if (s > sMax) { sMax = s; br = k }
            if (d > dMax) { dMax = d; tr = k }; if (d < dMin) { dMin = d; bl = k }
        }
        val pts = FloatArray(8)
        listOf(tl, tr, br, bl).forEachIndexed { i, k -> pts[2 * i] = (k % w).toFloat(); pts[2 * i + 1] = (k / w).toFloat() }
        val q = Quad(pts)
        // Quadrilátero de verdade: área do polígono parecida com a do componente (não um borrão disforme).
        val area = polygonArea(pts)
        if (area < MIN_AREA * w * h || bestN < 0.8f * area || q.widthPx < 0.2f * w || q.heightPx < 0.2f * h) return null
        return q
    }

    fun polygonArea(p: FloatArray): Float {
        var a = 0f
        for (i in 0 until 4) { val j = (i + 1) % 4; a += p[2 * i] * p[2 * j + 1] - p[2 * j] * p[2 * i + 1] }
        return kotlin.math.abs(a) / 2f
    }

    private fun otsu(hist: IntArray, total: Int): Int {
        var sum = 0.0; for (i in 0..255) sum += i * hist[i].toDouble()
        var sumB = 0.0; var wB = 0; var best = 0.0; var t = 128
        for (i in 0..255) {
            wB += hist[i]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; t = i }
        }
        return min(254, t)
    }
}
