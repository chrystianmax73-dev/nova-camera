package com.novacamera.s24.core.imaging

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Selfie em contraluz (pedido do dono, 20/10: rosto ~84/255 com o fundo em ~167). Com a máscara de pessoa do
 * segmentador do Retrato, mede o rosto (pele) contra o fundo; se a pessoa está bem mais escura, clareia só a
 * pessoa até o rosto chegar a ~[TARGET] (no máximo [BG_RATIO] do fundo e [MAX_EV] em valores sRGB). Só os
 * tons mais escuros que o fundo sobem (faixa [Plan.lo]–[Plan.hi]): sem halo claro em volta da cabeça.
 * Validado na selfie do dono: rosto 84 → 133, estouro igual (0,24%).
 * 0.36 (selfie de 20/10 na 0.35: rosto 0,38 × parede 0,80 ficava de fora pelo limite de 0,40): alvo 0,52 e até
 * 1 EV, na faixa da pesquisa (rosto 0,45–0,55; +1,5–2 EV em luz linear ≈ 0,7–0,9 EV em sRGB). Rosto entre 0,40 e
 * [ENTER_MAX] só entra com fundo bem mais claro ([WIDE_GAP]). Simulado na foto do dono: 0,38 → 0,52, sem halo.
 */
object BacklitLift {
    const val TARGET = 0.52f
    const val BG_RATIO = 0.70f
    /** Multiplicador máximo nos valores sRGB: 2^1 = 2× (≈ 2,2 EV em luz linear). */
    const val MAX_EV = 1.0f
    /** Rosto até aqui ainda entra, se o fundo estiver pelo menos [WIDE_GAP] mais claro. */
    const val ENTER_MAX = 0.46f
    const val WIDE_GAP = 0.30f
    const val MIN_EV = 0.15f

    class Plan(val ev: Float, val lo: Float, val hi: Float, val faceLum: Float, val bgLum: Float) {
        fun toMeta() = "$ev,$lo,$hi"
        companion object {
            fun fromMeta(s: String?): Plan? = s?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 3 }
                ?.let { Plan(it[0], it[1], it[2], 0f, 0f) }
        }
    }

    private fun median(values: FloatArray, n: Int): Float { if (n == 0) return 0f; val v = values.copyOf(n); v.sort(); return v[n / 2] }

    /**
     * [lum]: luminância sRGB 0..1; [person]/[face]: confiança no mesmo tamanho. Null quando não é contraluz
     * (pessoa pequena ou grande demais, fundo pouco visível, rosto já claro ou diferença pequena).
     */
    fun plan(lum: FloatArray, person: FloatArray, face: FloatArray?): Plan? {
        val n = lum.size
        val pv = FloatArray(n); var pn = 0
        val bv = FloatArray(n); var bn = 0
        val fv = FloatArray(n); var fn = 0
        for (k in 0 until n) {
            if (person[k] >= 0.5f) pv[pn++] = lum[k] else if (person[k] < 0.2f) bv[bn++] = lum[k]
            if (face != null && face[k] >= 0.5f) fv[fn++] = lum[k]
        }
        if (pn < 0.08f * n || pn > 0.85f * n || bn < 0.10f * n) return null
        val subject = if (fn >= 0.01f * n) median(fv, fn) else median(pv, pn)
        val bg = median(bv, bn)
        if (subject >= ENTER_MAX || bg - subject < 0.18f || (subject >= 0.40f && bg - subject < WIDE_GAP)) return null
        val target = min(TARGET, BG_RATIO * bg)
        val ev = (ln(target / max(subject, 0.02f)) / ln(2f)).coerceIn(0f, MAX_EV)
        if (ev < MIN_EV) return null
        return Plan(ev, subject + 0.45f * (bg - subject), subject + 0.8f * (bg - subject), subject, bg)
    }

    private fun smooth(e0: Float, e1: Float, v: Float): Float {
        val t = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t)
    }

    /** Clareia um bloco (origem [x0],[y0] numa foto [fullW]×[fullH]); peso = máscara da pessoa suavizada. */
    fun applyTile(tile: IntArray, tw: Int, th: Int, x0: Int, y0: Int, fullW: Int, fullH: Int, maps: SubjectAttention.Maps, plan: Plan) {
        val sx = maps.weight.w.toFloat() / fullW; val sy = maps.weight.h.toFloat() / fullH
        for (j in 0 until th) {
            val fy = (y0 + j + 0.5f) * sy - 0.5f
            for (i in 0 until tw) {
                val fx = (x0 + i + 0.5f) * sx - 0.5f
                val w = maps.weight.sample(fx, fy)
                if (w < 0.02f) continue
                val b = maps.base.sample(fx, fy)
                val protect = smooth(plan.hi, plan.lo, b) * smooth(0.01f, 0.04f, b)
                if (protect <= 0f) continue
                var gain = Math.pow(2.0, (plan.ev * w * protect).toDouble()).toFloat()
                val k = j * tw + i; val c = tile[k]
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
                val peak = max(r, max(g, bl))
                if (peak * gain > 245f) gain = max(1f, 245f / max(peak, 1))
                tile[k] = (c and (0xFF shl 24)) or (min(255, (r * gain + 0.5f).toInt()) shl 16) or
                    (min(255, (g * gain + 0.5f).toInt()) shl 8) or min(255, (bl * gain + 0.5f).toInt())
            }
        }
    }

    /** Prova na versão reduzida: a pessoa clareia ≥ 8% e não estoura mais que 0,5% dela. */
    fun accept(argb: IntArray, w: Int, h: Int, maps: SubjectAttention.Maps, plan: Plan): Boolean {
        val after = argb.copyOf(); applyTile(after, w, h, 0, 0, w, h, maps, plan)
        var n = 0; var before = 0f; var now = 0f; var clipB = 0; var clipA = 0
        fun luma(p: Int) = ((p shr 16 and 255) * 77 + (p shr 8 and 255) * 150 + (p and 255) * 29) shr 8
        for (k in argb.indices) if (maps.weight.data[k] >= 0.5f) {
            n++; val a = luma(argb[k]); val b = luma(after[k]); before += a; now += b
            if (a >= 250) clipB++; if (b >= 250) clipA++
        }
        if (n == 0) return false
        return now >= before * 1.08f && (clipA - clipB).toFloat() / n <= 0.005f
    }

    /** Mapas na resolução da prévia reduzida: peso = pessoa (suave, borda curta) e base = luminância suavizada. */
    fun maps(lum: FloatArray, person: FloatArray, w: Int, h: Int): SubjectAttention.Maps {
        val edge = max(1, (max(w, h) * 0.006f).toInt())
        val soft = boxBlur(FloatArray(w * h) { smooth(0.3f, 0.7f, person[it]) }, w, h, edge)
        val base = boxBlur(lum, w, h, max(1, max(w, h) / 100))
        return SubjectAttention.Maps(Plane(w, h, soft), Plane(w, h, base))
    }

    private fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        for (y in 0 until h) { var s = 0f; var c = 0
            for (x in -r until w + r) {
                if (x + r < w) { s += src[y * w + min(w - 1, max(0, x + r))]; c++ }
                if (x - r - 1 >= 0) { s -= src[y * w + x - r - 1]; c-- }
                if (x in 0 until w) tmp[y * w + x] = s / max(1, c)
            } }
        for (x in 0 until w) { var s = 0f; var c = 0
            for (y in -r until h + r) {
                if (y + r < h) { s += tmp[min(h - 1, max(0, y + r)) * w + x]; c++ }
                if (y - r - 1 >= 0) { s -= tmp[(y - r - 1) * w + x]; c-- }
                if (y in 0 until h) out[y * w + x] = s / max(1, c)
            } }
        return out
    }
}
