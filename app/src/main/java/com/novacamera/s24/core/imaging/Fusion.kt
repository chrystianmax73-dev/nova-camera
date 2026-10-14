package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * HDR por fusão de exposições (inspirado em Mertens et al.; pesos refinados por filtro guiado,
 * como em Li et al. 2013). Cada pixel final é média ponderada dos quadros medidos: o quadro
 * mais escuro contribui onde o normal estourou (céu, janela) e o mais claro onde havia sombra.
 * Pesos suaves e guiados pelas bordas da referência evitam halos.
 */
object Fusion {
    /**
     * Pesos por quadro em resolução reduzida. [lumas] já alinhadas; índice 0 = exposição de referência.
     * Retorna planos normalizados (soma 1 em cada ponto).
     */
    fun weights(lumas: List<Plane>, guide: Plane, deghost: Boolean = true): List<Plane> {
        val w = guide.w; val h = guide.h
        val ghost = if (deghost) ghostMasks(lumas) else null
        val raw = lumas.mapIndexed { k, l ->
            val p = Plane(w, h)
            for (y in 0 until h) for (x in 0 until w) {
                val v = l[x, y]
                // Bem-exposto: perto do meio-tom. Contraste local: Laplaciano.
                val well = exp(-((v - 0.5f) * (v - 0.5f)) / (2f * 0.2f * 0.2f))
                val lap = if (x in 1 until w - 1 && y in 1 until h - 1)
                    abs(4 * v - l[x - 1, y] - l[x + 1, y] - l[x, y - 1] - l[x, y + 1]) else 0f
                p[x, y] = well * (lap * 4f + 0.05f) * (ghost?.get(k)?.get(x, y) ?: 1f) + 1e-6f
            }
            Planes.boxBlur(p, 1)
        }
        val r = max(2, minOf(w, h) / 16)
        val refined = raw.map { Planes.guidedFilter(guide, it, r, 0.01f) }
        val out = refined.map { Plane(w, h) }
        for (i in 0 until w * h) {
            var s = 0f
            for (k in refined.indices) s += max(0f, refined[k].data[i])
            for (k in refined.indices) out[k].data[i] = if (s > 1e-6f) max(0f, refined[k].data[i]) / s else if (k == 0) 1f else 0f
        }
        return out
    }

    /**
     * Ganho de exposição de [mov] em relação a [ref] (mediana das razões onde ambos estão bem
     * medidos). Usado para comparar quadros de exposições diferentes.
     */
    fun exposureGain(ref: Plane, mov: Plane): Float {
        val ratios = ArrayList<Float>()
        val step = max(1, ref.data.size / 20000)
        var i = 0
        while (i < ref.data.size) {
            val a = ref.data[i]; val b = mov.data[i]
            if (a in 0.04f..0.9f && b in 0.04f..0.9f) ratios += b / a
            i += step
        }
        if (ratios.size < 16) return 1f
        return Planes.percentile(ratios.toFloatArray(), 0.5f)
    }

    /**
     * Antifantasma: onde um quadro não bate com o previsto pela referência (alguém se mexeu
     * entre as exposições), o peso dele cai a quase zero e a referência prevalece. Onde a
     * referência estourou ou ficou preta não há previsão confiável, e o quadro mantém o peso.
     */
    fun ghostMasks(lumas: List<Plane>): List<Plane> {
        val ref = lumas[0]
        return lumas.mapIndexed { k, l ->
            val m = Plane(l.w, l.h)
            if (k == 0) { m.data.fill(1f); return@mapIndexed m }
            val gain = exposureGain(ref, l)
            for (i in m.data.indices) {
                val r = ref.data[i]
                val pred = r * gain
                m.data[i] = if (r !in 0.03f..0.97f || pred > 0.97f) 1f
                    else if (abs(l.data[i] - pred) > 0.08f + 0.15f * pred) 0.02f else 1f
            }
            // Expande levemente a região rejeitada para cobrir a borda do objeto em movimento.
            val b = Planes.boxBlur(m, 1)
            for (i in m.data.indices) m.data[i] = if (b.data[i] < 0.95f) 0.02f else 1f
            m
        }
    }

    /** Mistura uma região: [tiles] alinhados, mesmos índices de [weights] (resolução reduzida). */
    fun blendTile(tiles: List<IntArray>, weights: List<Plane>, tw: Int, th: Int, x0: Int, y0: Int, fullW: Int, fullH: Int): IntArray {
        val out = IntArray(tw * th)
        val sx = weights[0].w.toFloat() / fullW; val sy = weights[0].h.toFloat() / fullH
        val wk = FloatArray(tiles.size)
        for (ty in 0 until th) for (tx in 0 until tw) {
            val fx = (x0 + tx + 0.5f) * sx - 0.5f; val fy = (y0 + ty + 0.5f) * sy - 0.5f
            var s = 0f
            for (k in tiles.indices) { wk[k] = weights[k].sample(fx, fy); s += wk[k] }
            var r = 0f; var g = 0f; var b = 0f
            val i = ty * tw + tx
            for (k in tiles.indices) {
                val c = tiles[k][i]; val w = if (s > 0f) wk[k] / s else if (k == 0) 1f else 0f
                r += w * Px.r(c); g += w * Px.g(c); b += w * Px.b(c)
            }
            out[i] = Px.argb((r + 0.5f).toInt(), (g + 0.5f).toInt(), (b + 0.5f).toInt())
        }
        return out
    }
}
