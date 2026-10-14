package com.novacamera.s24.core.imaging

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

/** Estatísticas da cena medidas na luminância em resolução reduzida. */
data class SceneStats(
    val mean: Float, val p01: Float, val p05: Float, val p50: Float, val p95: Float, val p99: Float,
    /** Fração da imagem abaixo de 0,12 (sombras profundas). */
    val shadowFraction: Float,
) {
    /**
     * Imagem "velada": preto muito levantado e pouca faixa dinâmica (flare/neblina; ex.: aspirador 10×,
     * ponto de preto ≈ 0,43). Critério estrito para não tocar cenas claras legítimas
     * (ex.: pano verde, ponto de preto ≈ 0,21, cuja cor foi aprovada pelo usuário).
     */
    val veiled get() = p01 > 0.24f && (p99 - p01) < 0.62f

    companion object {
        fun of(luma: Plane): SceneStats {
            val d = luma.data
            var sum = 0f; var dark = 0
            for (v in d) { sum += v; if (v < 0.12f) dark++ }
            val sorted = d.copyOf().also { it.sort() }
            fun p(q: Float) = sorted[((sorted.size - 1) * q).toInt()]
            return SceneStats(sum / d.size, p(0.01f), p(0.05f), p(0.5f), p(0.95f), p(0.99f), dark.toFloat() / d.size)
        }
    }
}

/** Intenção do processamento: define quanto o motor pode agir. */
enum class ToneIntent { LIGHT, NIGHT, HDR, PORTRAIT, FOOD }

/**
 * Parâmetros do mapeamento tonal local. Todos atuam só na luminância, aplicada como ganho igual
 * em R, G e B: matiz e saturação relativa ficam preservadas (fidelidade de cor, ex.: pano verde).
 */
data class ToneParams(
    /** Elevação máxima das sombras, em EV. */
    val shadowLiftEv: Float,
    /** Compressão das altas luzes (0 = nenhuma, 1 = forte). */
    val highlightCompression: Float,
    /** Nível de preto removido (correção de véu), 0..1. */
    val blackLevel: Float,
) {
    val isIdentity get() = shadowLiftEv < 0.02f && highlightCompression < 0.02f && blackLevel < 0.005f

    companion object {
        /**
         * Decide os parâmetros a partir da cena. Conservador: cenas bem expostas recebem quase nada;
         * a noite é clareada só até um alvo moderado (mantém a atmosfera, não "vira dia").
         */
        fun plan(s: SceneStats, intent: ToneIntent): ToneParams {
            val black = if (s.veiled) ((s.p01 - 0.05f) * 0.6f).coerceIn(0f, 0.2f) else 0f
            val (target, factor, maxLift) = when (intent) {
                ToneIntent.LIGHT -> Triple(0.26f, 0.55f, 0.6f)
                ToneIntent.NIGHT -> Triple(0.30f, 0.85f, 1.4f)
                ToneIntent.HDR -> Triple(0.32f, 0.75f, 1.0f)
                ToneIntent.PORTRAIT -> Triple(0.28f, 0.5f, 0.5f)
                ToneIntent.FOOD -> Triple(0.30f, 0.5f, 0.5f)
            }
            val lift = if (s.p50 < target) (log2(target / max(s.p50, 0.01f)) * factor).coerceIn(0f, maxLift) else 0f
            val compress = when {
                intent == ToneIntent.HDR && s.p99 > 0.92f -> 0.45f
                s.p99 > 0.97f -> 0.2f
                else -> 0f
            }
            return ToneParams(lift, compress, black)
        }
    }
}

/**
 * Mapeamento tonal local em duas escalas: camada-base suave (filtro guiado em log-luminância, sem
 * halos nas bordas) recebe as correções; os detalhes finos são mantidos intactos.
 */
class ToneMap(private val base: Plane, private val params: ToneParams, private val fullW: Int, private val fullH: Int) {
    private val sx = base.w.toFloat() / fullW
    private val sy = base.h.toFloat() / fullH
    private val lnShadowStart = ln(0.45f)
    private val lnShadowFull = ln(0.08f)
    private val lnHighStart = ln(0.6f)

    /** Aplica a uma região [tile] (w×h) cuja origem na imagem cheia é (x0, y0). */
    fun applyTile(tile: IntArray, tw: Int, th: Int, x0: Int, y0: Int, strength: Float = 1f) {
        if (params.isIdentity || strength <= 0f) return
        val s = strength.coerceIn(0f, 1f)
        val black = params.blackLevel * s
        val bl = black * 255f
        val blScale = 1f / (1f - black)
        for (ty in 0 until th) {
            val by = (y0 + ty + 0.5f) * sy - 0.5f
            for (tx in 0 until tw) {
                val i = ty * tw + tx
                val c = tile[i]
                val r = max(0f, (Px.r(c) - bl) * blScale)
                val g = max(0f, (Px.g(c) - bl) * blScale)
                val b = max(0f, (Px.b(c) - bl) * blScale)
                val baseLog = base.sample((x0 + tx + 0.5f) * sx - 0.5f, by)
                val shadowW = smooth(lnShadowStart, lnShadowFull, baseLog)
                val highW = smooth(lnHighStart, 0f, baseLog)
                val deltaLog = params.shadowLiftEv * 0.6931f * shadowW - params.highlightCompression * highW * (baseLog - lnHighStart)
                var gain = exp(deltaLog * s)
                // Nunca estoura: se o canal mais forte passaria de 255, reduz o ganho (matiz preservada).
                val peak = max(r, max(g, b))
                if (peak * gain > 255f) gain = 255f / max(peak, 1f)
                tile[i] = Px.argb((r * gain + 0.5f).toInt(), (g * gain + 0.5f).toInt(), (b * gain + 0.5f).toInt())
            }
        }
    }

    companion object {
        /** Camada-base: log-luminância suavizada preservando bordas, em resolução reduzida. */
        fun baseFrom(luma: Plane): Plane {
            val log = Plane(luma.w, luma.h)
            for (i in luma.data.indices) log.data[i] = ln(max(luma.data[i], 1f / 255f))
            val r = max(2, min(luma.w, luma.h) / 24)
            return Planes.guidedFilter(log, log, r, 0.05f)
        }

        private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
