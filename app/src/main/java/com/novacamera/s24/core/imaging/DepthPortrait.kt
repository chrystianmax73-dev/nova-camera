package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Retrato com profundidade estimada: o desfoque cresce com a distância ao plano de foco, como numa
 * lente real — o que está na mesma distância do assunto fica nítido (a mão e o objeto que ela
 * segura, o chão sob os pés), o que está à frente também desfoca. A profundidade do modelo é
 * relativa (sem escala métrica), então tudo é medido em fração da faixa de profundidade da cena.
 */
object DepthPortrait {
    /** Zona nítida em torno do foco e faixa até o desfoque máximo (fração da faixa da cena). */
    const val DEAD_ZONE = 0.04f
    const val SPAN = 0.35f
    /** Pessoa só define o foco se ocupar ao menos isso do quadro. */
    private const val MIN_PERSON = 0.01f

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t)
    }

    /** 0..1 pela faixa da cena (percentis 2–98); maior = mais perto. */
    fun normalize(d: Plane): Plane {
        val sorted = d.data.copyOf().also { it.sort() }
        val lo = sorted[(sorted.size * 0.02f).toInt()]
        val hi = sorted[min(sorted.size - 1, (sorted.size * 0.98f).toInt())]
        val span = max(hi - lo, 1e-6f)
        return Plane(d.w, d.h, FloatArray(d.data.size) { ((d.data[it] - lo) / span).coerceIn(0f, 1f) })
    }

    /**
     * Profundidade do plano de foco: mediana dentro da pessoa (confiança ≥ 0,8); sem pessoa, o que
     * está mais perto no centro do quadro (percentil 85 do retângulo central) — retrato de objeto.
     */
    fun focus(depth: Plane, person: Plane?): Float {
        if (person != null) {
            val p = if (person.w == depth.w && person.h == depth.h) person else Planes.resize(person, depth.w, depth.h)
            val inside = ArrayList<Float>()
            for (i in depth.data.indices) if (p.data[i] >= 0.8f) inside += depth.data[i]
            if (inside.size >= depth.data.size * MIN_PERSON) { inside.sort(); return inside[inside.size / 2] }
        }
        val center = ArrayList<Float>()
        for (y in (depth.h * 0.25f).toInt() until (depth.h * 0.75f).toInt())
            for (x in (depth.w * 0.3f).toInt() until (depth.w * 0.7f).toInt()) center += depth[x, y]
        center.sort()
        return center[(center.size * 0.85f).toInt().coerceAtMost(center.size - 1)]
    }

    /**
     * Quanto desfocar cada ponto (0 = nítido, 1 = desfoque máximo). O mapa de profundidade é
     * reduzido e mistura assunto e fundo na borda; um filtro de máximo faz o fundo colado no
     * contorno herdar o desfoque do fundo vizinho (sem a franja nítida em volta do assunto). O
     * próprio assunto é protegido pela máscara de borda firme, não por este mapa.
     */
    fun blurMap(depth: Plane, focus: Float, dilate: Int = max(2, min(depth.w, depth.h) / 120)): Plane {
        val t = Plane(depth.w, depth.h, FloatArray(depth.data.size) {
            ((abs(depth.data[it] - focus) - DEAD_ZONE) / SPAN).coerceIn(0f, 1f)
        })
        return maxFilter(t, dilate)
    }

    /** Região no plano de foco (máscara grosseira do objeto quando não há pessoa). */
    fun inFocus(depth: Plane, focus: Float): Plane =
        Plane(depth.w, depth.h, FloatArray(depth.data.size) { 1f - smooth(DEAD_ZONE, DEAD_ZONE + 0.08f, abs(depth.data[it] - focus)) })

    /** Máximo em janela quadrada (2r+1)², separável. */
    fun maxFilter(p: Plane, r: Int): Plane {
        if (r <= 0) return p
        val w = p.w; val h = p.h
        val tmp = Plane(w, h); val out = Plane(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0f
            for (k in max(0, x - r)..min(w - 1, x + r)) m = max(m, p.data[y * w + k])
            tmp.data[y * w + x] = m
        }
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0f
            for (k in max(0, y - r)..min(h - 1, y + r)) m = max(m, tmp.data[k * w + x])
            out.data[y * w + x] = m
        }
        return out
    }
}
