package com.novacamera.s24.core.imaging

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Atenção ao assunto na FOTO no escuro (pedido do dono, 19–20/10): onde há informação na sombra — letras,
 * desenhos, contornos (a caixa com a criança embaixo do frasco) — ou onde o dono tocou, a sombra sobe até
 * [MAX_EV]; o resto da foto fica como está. Sem rede: densidade de contornos numa versão reduzida e já
 * suavizada (o ruído some, a textura real fica), só em regiões escuras. Mesma ideia do tom local guiado por
 * região (ICME 2020 "semantic-aware tone mapping"; patentes de ISP que passam regiões detectadas ao tom local).
 */
object SubjectAttention {
    const val MAX_EV = 0.8f
    /** Cobertura aceitável: abaixo não há assunto; acima, o "assunto" virou a cena (não é atenção). */
    const val MIN_COVERAGE = 0.005f
    const val MAX_COVERAGE = 0.40f

    /** [weight] 0..1 e [base] luminância suavizada 0..1, ambos na resolução reduzida (coordenadas normalizadas). */
    class Maps(val weight: Plane, val base: Plane) {
        /** Fração da cena com peso ≥ 0,5. */
        val coverage get() = weight.data.count { it >= 0.5f }.toFloat() / weight.data.size
    }

    private fun luma(p: Int) = ((p shr 16 and 255) * 77 + (p shr 8 and 255) * 150 + (p and 255) * 29) shr 8

    private fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        for (y in 0 until h) { var s = 0f; var n = 0
            for (x in -r until w + r) {
                val add = x + r; if (add in 0 until w) { s += src[y * w + add]; n++ }
                val rem = x - r - 1; if (rem in 0 until w) { s -= src[y * w + rem]; n-- }
                if (x in 0 until w) tmp[y * w + x] = s / max(1, n)
            } }
        for (x in 0 until w) { var s = 0f; var n = 0
            for (y in -r until h + r) {
                val add = y + r; if (add in 0 until h) { s += tmp[add * w + x]; n++ }
                val rem = y - r - 1; if (rem in 0 until h) { s -= tmp[rem * w + x]; n-- }
                if (y in 0 until h) out[y * w + x] = s / max(1, n)
            } }
        return out
    }

    private fun smooth(e0: Float, e1: Float, v: Float): Float {
        val t = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t)
    }

    /** Mapa a partir da foto reduzida [argb] (w×h, ~512 px) e do toque [tap] (normalizado), se houver. */
    fun map(argb: IntArray, w: Int, h: Int, tap: Pair<Float, Float>?): Maps {
        val y = FloatArray(w * h) { luma(argb[it]) / 255f }
        val soft = boxBlur(y, w, h, 1)
        val grad = FloatArray(w * h)
        for (j in 1 until h - 1) for (i in 1 until w - 1) {
            val k = j * w + i
            val gx = soft[k + 1] - soft[k - 1]; val gy = soft[k + w] - soft[k - w]
            grad[k] = sqrt(gx * gx + gy * gy)
        }
        // Piso de ruído: a maior parte da cena é lisa; contorno de verdade fica muito acima da mediana.
        val floor = grad.sortedArray()[grad.size / 2]
        val info = FloatArray(w * h) { ((grad[it] - 2.5f * floor) / (6f * floor + 0.02f)).coerceIn(0f, 1f) }
        val r = max(2, min(w, h) / 40)
        val density = boxBlur(info, w, h, r)
        val mean = boxBlur(y, w, h, r)
        val weight = FloatArray(w * h) { k ->
            // Escuro (média local abaixo de ~30%) e com contornos densos (texto, desenho), não um fio isolado.
            val dark = smooth(0.32f, 0.12f, mean[k])
            (density[k] * 4f).coerceIn(0f, 1f) * dark
        }
        if (tap != null) {
            val cx = tap.first * w; val cy = tap.second * h; val rad = 0.12f * min(w, h)
            for (j in 0 until h) for (i in 0 until w) {
                val d2 = ((i - cx) * (i - cx) + (j - cy) * (j - cy)) / (rad * rad)
                if (d2 < 4f) { val k = j * w + i; weight[k] = max(weight[k], exp(-d2) * smooth(0.40f, 0.15f, mean[k])) }
            }
        }
        // Bordas suaves: sem "recorte" visível entre a área clareada e o resto.
        val feathered = boxBlur(boxBlur(weight, w, h, r), w, h, r).let { b -> FloatArray(w * h) { (b[it] * 1.6f).coerceIn(0f, 1f) } }
        return Maps(Plane(w, h, feathered), Plane(w, h, boxBlur(y, w, h, max(1, r / 2))))
    }

    /** Clareia a sombra ponderada pelo mapa num bloco (origem [x0],[y0] numa foto [fullW]×[fullH]). */
    fun applyTile(tile: IntArray, tw: Int, th: Int, x0: Int, y0: Int, fullW: Int, fullH: Int, maps: Maps, ev: Float = MAX_EV) {
        val sx = maps.weight.w.toFloat() / fullW; val sy = maps.weight.h.toFloat() / fullH
        for (j in 0 until th) {
            val fy = (y0 + j + 0.5f) * sy - 0.5f
            for (i in 0 until tw) {
                val fx = (x0 + i + 0.5f) * sx - 0.5f
                val w = maps.weight.sample(fx, fy)
                if (w < 0.02f) continue
                val b = maps.base.sample(fx, fy)
                // Sombra sobe; preto profundo (< ~3%) e meio-tom/alta luz ficam.
                val shadow = smooth(0.45f, 0.08f, b) * smooth(0.02f, 0.06f, b)
                if (shadow <= 0f) continue
                var gain = exp(0.6931f * ev * w * shadow)
                val k = j * tw + i; val c = tile[k]
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
                val peak = max(r, max(g, bl))
                if (peak * gain > 250f) gain = max(1f, 250f / max(peak, 1))
                tile[k] = (c and (0xFF shl 24)) or (min(255, (r * gain + 0.5f).toInt()) shl 16) or
                    (min(255, (g * gain + 0.5f).toInt()) shl 8) or min(255, (bl * gain + 0.5f).toInt())
            }
        }
    }

    /**
     * Prova na versão reduzida antes de valer: cobertura de assunto (não a cena toda), a área clareia de fato
     * e não estoura (fração ≥ 250 na área não sobe mais que 0,5%).
     */
    fun accept(argb: IntArray, w: Int, h: Int, maps: Maps): Boolean {
        val cov = maps.coverage
        if (cov < MIN_COVERAGE || cov > MAX_COVERAGE) return false
        val after = argb.copyOf(); applyTile(after, w, h, 0, 0, w, h, maps)
        var n = 0; var before = 0f; var now = 0f; var clipB = 0; var clipA = 0
        for (k in argb.indices) if (maps.weight.data[k] >= 0.5f) {
            n++; val a = luma(argb[k]); val b = luma(after[k]); before += a; now += b
            if (a >= 250) clipB++; if (b >= 250) clipA++
        }
        if (n == 0) return false
        return now / n >= before / n * 1.08f && (clipA - clipB).toFloat() / n <= 0.005f
    }
}
