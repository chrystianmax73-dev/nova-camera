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
    /**
     * "Canal escuro": p0,5 do menor canal RGB. Numa foto limpa algo na cena é quase preto
     * (≈ 0,03–0,07); véu de flare/neblina ou pretos levantados pelo processamento sobem esse valor
     * (aspirador 10× ≈ 0,14; carvão Nova 0,6 ≈ 0,17). O pano verde aprovado fica em 0,035.
     */
    val darkChannel: Float = 0f,
) {
    /**
     * Imagem "velada": preto muito levantado e pouca faixa dinâmica (flare/neblina; ex.: aspirador 10×,
     * ponto de preto ≈ 0,43). Critério estrito para não tocar cenas claras legítimas
     * (ex.: pano verde, ponto de preto ≈ 0,21, cuja cor foi aprovada pelo usuário).
     */
    val veiled get() = p01 > 0.24f && (p99 - p01) < 0.62f

    companion object {
        fun of(luma: Plane, darkChannel: Float = 0f): SceneStats {
            val d = luma.data
            var sum = 0f; var dark = 0
            for (v in d) { sum += v; if (v < 0.12f) dark++ }
            val sorted = d.copyOf().also { it.sort() }
            fun p(q: Float) = sorted[((sorted.size - 1) * q).toInt()]
            return SceneStats(sum / d.size, p(0.01f), p(0.05f), p(0.5f), p(0.95f), p(0.99f), dark.toFloat() / d.size, darkChannel)
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
    /**
     * Contraste local de média escala (0..0.3): realça a forma dos objetos (zoom com véu), não a
     * textura fina nem o ruído — não é nitidez artificial e não cria detalhe.
     */
    val clarity: Float = 0f,
    /**
     * Clareamento dos meios-tons em EV (só Noturno): sobe a cena inteira até perto do Noturno da
     * Samsung, sem tocar pretos profundos e diminuindo até zero nas altas luzes (não estoura).
     */
    val midLiftEv: Float = 0f,
    /**
     * Preto firme do Noturno (0..1), aplicado como ganho igual em R, G e B a partir da luminância:
     * escurece o véu de ruído sem saturar a cor das sombras (subtrair o mesmo valor de cada canal
     * deixava um armário escuro magenta).
     */
    val lumaBlack: Float = 0f,
) {
    val isIdentity get() = shadowLiftEv < 0.02f && highlightCompression < 0.02f && blackLevel < 0.005f && clarity < 0.01f &&
        midLiftEv < 0.02f && lumaBlack < 0.005f

    companion object {
        /** Contraste local do perfil "Contraste" (o automático usa só na tele com véu). */
        const val PROFILE_CLARITY = 0.3f
        /** Onde o perfil deixa o canal escuro (p0,5): ~1,5% — preto de verdade, sem engolir sombras. */
        private const val PROFILE_BLACK_TARGET = 0.015f
        private const val PROFILE_MAX_BLACK = 0.06f

        /**
         * Perfil "Contraste" (opcional): preto firme e contraste local, a direção medida nas
         * referências vivo X300 Pro, iPhone 18 Pro e Pixel 10 Pro (preto p2 0,7–3,9 e contraste local
         * 6–14, contra 6–19 e 4–7 no Nova). Aplicado depois do tom automático; não clareia nada.
         */
        /** Mediana-alvo da foto comum com pouca luz (o Noturno vai a 0,42; aqui mais contido). */
        const val LOW_LIGHT_TARGET = 0.28f
        private const val LOW_LIGHT_MAX_EV = 1.0f

        /**
         * Foto comum (Foto, tele, HDR, Retrato) com ISO alto e cena escura: clareia os meios-tons como o
         * Noturno, mais contido — o automático da câmera para em ~ISO 3200 e a foto sai escura (frasco 5×,
         * ISO 2056, mediana ~0,15). Limitado pela folga das altas luzes (não estoura) e só a partir do
         * ISO 800, inteiro a partir de 1600 (luz boa: nada). Pretos profundos e claros ficam (pesos do midLift).
         */
        fun lowLightLift(iso: Int?, p50: Float, p95: Float): ToneParams? {
            if (iso == null || iso < 800) return null
            val fade = ((iso - 800) / 800f).coerceIn(0f, 1f)
            val need = ln(LOW_LIGHT_TARGET / max(p50, 0.02f)) / ln(2f)
            val headroom = ln(0.9f / max(p95, 0.05f)) / ln(2f)
            val lift = min(min(need, headroom), LOW_LIGHT_MAX_EV) * fade
            if (lift < 0.1f) return null
            return ToneParams(shadowLiftEv = 0f, highlightCompression = 0f, blackLevel = 0f, midLiftEv = lift)
        }

        fun contrastProfile(darkChannel: Float, medianLuma: Float = 0.5f): ToneParams {
            // Cena escura (mediana baixa): o preto firme escureceria o ambiente inteiro (quarto a 1×
            // descia de 26 para 21 na mediana); entra aos poucos entre 0,15 e 0,35.
            val t = ((medianLuma - 0.15f) / 0.20f).coerceIn(0f, 1f)
            val fade = t * t * (3f - 2f * t)
            return ToneParams(
                shadowLiftEv = 0f, highlightCompression = 0f, blackLevel = 0f,
                clarity = PROFILE_CLARITY,
                lumaBlack = (darkChannel - PROFILE_BLACK_TARGET).coerceIn(0f, PROFILE_MAX_BLACK) * fade,
            )
        }

        /**
         * Decide os parâmetros a partir da cena. Conservador: cenas bem expostas recebem quase nada;
         * a noite é clareada até um alvo próximo do Noturno da Samsung (mediana ~0,42), com preto firme.
         */
        fun plan(s: SceneStats, intent: ToneIntent, zoom: Float = 1f): ToneParams {
            // Véu: pelo canal escuro (preserva cenas limpas como o pano verde) e, no caso extremo, pela luminância.
            val fromDark = if (s.darkChannel > 0.09f) ((s.darkChannel - 0.06f) * 0.85f).coerceIn(0f, 0.22f) else 0f
            val fromVeil = if (s.veiled) ((s.p01 - 0.05f) * 0.6f).coerceIn(0f, 0.22f) else 0f
            // Noturno: preto firme — o ruído das sombras levanta o preto e deixa a foto "lavada".
            // Pelo canal escuro (como o véu): cena sem preto real (parede lisa, pano colorido) não é tocada.
            val fromNight = if (intent == ToneIntent.NIGHT) ((s.darkChannel - 0.03f) * 0.8f).coerceIn(0f, 0.08f) else 0f
            val black = max(fromDark, fromVeil)
            // Elevação de sombras: só quando a cena está realmente escura, e bem menos que na 0.6.
            val (target, factor, maxLift) = when (intent) {
                ToneIntent.LIGHT -> Triple(0.22f, 0.4f, 0.35f)
                ToneIntent.NIGHT -> Triple(0.42f, 0.9f, 1.5f)
                ToneIntent.HDR -> Triple(0.28f, 0.6f, 0.7f)
                ToneIntent.PORTRAIT -> Triple(0.22f, 0.3f, 0.25f)
                ToneIntent.FOOD -> Triple(0.26f, 0.4f, 0.4f)
            }
            val lift = if (s.p50 < target) (log2(target / max(s.p50, 0.01f)) * factor).coerceIn(0f, maxLift) else 0f
            val compress = when {
                intent == ToneIntent.HDR && s.p99 > 0.92f -> 0.35f
                s.p99 > 0.98f && intent != ToneIntent.PORTRAIT -> 0.15f
                else -> 0f
            }
            // Contraste local só no zoom (onde o véu achata a imagem) e em cenas com véu.
            val clarity = (when {
                zoom >= 9f -> 0.22f
                zoom >= 4.5f -> 0.18f
                zoom >= 2.5f -> 0.1f
                else -> 0f
            } + if (black > 0.04f) 0.08f else 0f).coerceAtMost(if (intent == ToneIntent.PORTRAIT) 0f else 0.28f)
            // Noturno: os meios-tons levam a cena até o alvo; as sombras recebem só o que faltar
            // (mínimo 0,25 EV a mais), sem somar os dois clareamentos na mediana.
            val need = if (s.p50 < target) log2(target / max(s.p50, 0.01f)) else 0f
            // Contra a luz (janela, lâmpada): as partes claras já estão perto do branco; clarear os
            // meios-tons deixaria a foto esbranquiçada. O teto vem da folga até 0,9 do p95.
            val headroom = max(0f, log2(0.9f / max(s.p95, 0.01f)))
            val midLift = if (intent == ToneIntent.NIGHT) need.coerceIn(0f, min(1.4f, headroom)) else 0f
            val shadow = if (intent == ToneIntent.NIGHT && need > 0f) max(need - midLift, 0.25f).coerceAtMost(maxLift) else lift
            return ToneParams(shadow, compress, black, clarity, midLift, fromNight)
        }
    }
}

/**
 * Mapeamento tonal local em duas escalas: camada-base suave (filtro guiado em log-luminância, sem
 * halos nas bordas) recebe as correções; os detalhes finos são mantidos intactos.
 */
class ToneMap(
    private val base: Plane, private val params: ToneParams, private val fullW: Int, private val fullH: Int,
    /** Camada de média escala (mesma resolução de [base]); necessária só com clareza > 0. */
    private val mid: Plane? = null,
) {
    private val sx = base.w.toFloat() / fullW
    private val sy = base.h.toFloat() / fullH
    private val lnShadowStart = ln(0.45f)
    private val lnShadowFull = ln(0.08f)
    private val lnHighStart = ln(0.6f)
    // Pretos profundos não sobem (corrige os "pretos acinzentados" da 0.6).
    private val lnBlackFull = ln(0.02f)
    private val lnBlackEnd = ln(0.07f)
    private val lnMidFull = ln(0.25f)
    private val lnMidEnd = ln(0.85f)
    // Meios-tons sobem; pretos (listras escuras, cantos) ficam: sem "pretos lavados".
    private val lnMidBlackFull = ln(0.04f)
    private val lnMidBlackEnd = ln(0.13f)

    /** Aplica a uma região [tile] (w×h) cuja origem na imagem cheia é (x0, y0). */
    fun applyTile(tile: IntArray, tw: Int, th: Int, x0: Int, y0: Int, strength: Float = 1f, chromaProtection: Float = 0f) {
        if (params.isIdentity || strength <= 0f) return
        val s = strength.coerceIn(0f, 1f)
        val black = params.blackLevel * s
        val bl = black * 255f
        val blScale = 1f / (1f - black)
        val lb = params.lumaBlack * s
        val lbScale = 1f / (1f - lb)
        for (ty in 0 until th) {
            val by = (y0 + ty + 0.5f) * sy - 0.5f
            for (tx in 0 until tw) {
                val i = ty * tw + tx
                val c = tile[i]
                var r = max(0f, (Px.r(c) - bl) * blScale)
                var g = max(0f, (Px.g(c) - bl) * blScale)
                var b = max(0f, (Px.b(c) - bl) * blScale)
                if (lb > 0f) {
                    val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    val k = if (y <= 0.5f) 0f else max(0f, (y - lb * 255f) * lbScale) / y
                    r *= k; g *= k; b *= k
                }
                val baseLog = base.sample((x0 + tx + 0.5f) * sx - 0.5f, by)
                val shadowW = smooth(lnShadowStart, lnShadowFull, baseLog) * smooth(lnBlackFull, lnBlackEnd, baseLog)
                val highW = smooth(lnHighStart, 0f, baseLog)
                var deltaLog = params.shadowLiftEv * 0.6931f * shadowW - params.highlightCompression * highW * (baseLog - lnHighStart)
                if (params.midLiftEv > 0f) deltaLog += params.midLiftEv * 0.6931f *
                    smooth(lnMidEnd, lnMidFull, baseLog) * smooth(lnMidBlackFull, lnMidBlackEnd, baseLog)
                if (mid != null && params.clarity > 0f) {
                    val m = mid.sample((x0 + tx + 0.5f) * sx - 0.5f, by)
                    // Forma de média escala (mid − base), limitada: sem halos e sem tocar textura fina.
                    deltaLog += params.clarity * (m - baseLog).coerceIn(-0.35f, 0.35f)
                }
                var gain = exp(deltaLog * s)
                // Nunca estoura: se o canal mais forte passaria de 255, reduz o ganho (matiz preservada).
                val peak = max(r, max(g, b))
                if (peak * gain > 255f) gain = 255f / max(peak, 1f)
                val baseline = Px.argb((r * gain + 0.5f).toInt(), (g * gain + 0.5f).toInt(), (b * gain + 0.5f).toInt())
                tile[i] = if (chromaProtection > 0f) AdaptiveImaging.protect(c, baseline, chromaProtection) else baseline
            }
        }
    }

    companion object {
        /** Camada de média escala para a clareza (estruturas de ~1% da imagem). */
        fun midFrom(luma: Plane): Plane {
            val log = Plane(luma.w, luma.h)
            for (i in luma.data.indices) log.data[i] = ln(max(luma.data[i], 1f / 255f))
            val r = max(1, min(luma.w, luma.h) / 120)
            return Planes.guidedFilter(log, log, r, 0.02f)
        }

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

