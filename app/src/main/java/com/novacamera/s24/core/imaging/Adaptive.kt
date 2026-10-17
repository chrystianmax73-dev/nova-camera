package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.min

/**
 * Decide QUANTO processar cada foto. A 0.6 aplicava a mesma correção forte a todas e algumas
 * ficavam piores (pretos acinzentados, contraste chato). Aqui o plano de tom é testado numa prévia
 * reduzida e só é aceito se não piorar a foto em nenhuma medida objetiva; senão a intensidade cai
 * (65%, 35%) ou o processamento é dispensado.
 */
object Adaptive {
    class Plan(
        val params: ToneParams, val strength: Float, val base: Plane, val mid: Plane?,
        /** Explicação curta da decisão (vai para a revisão e para a validação). */
        val reason: String,
        val stats: SceneStats? = null,
        val imaging: AdaptiveImaging.Decision? = null,
    ) {
        /** Nada a fazer: a foto é salva como saiu da câmera. */
        val skip get() = strength <= 0f || params.isIdentity
    }

    /** Medidas objetivas usadas na proteção contra piora. */
    data class Quality(val contrast: Float, val texture: Float, val black: Float, val clipped: Float)

    /** p0,5 do menor canal RGB (ver [SceneStats.darkChannel]). */
    fun darkChannel(px: IntArray): Float {
        val hist = IntArray(256)
        for (c in px) hist[min(Px.r(c), min(Px.g(c), Px.b(c)))]++
        val target = px.size * 0.005f
        var acc = 0
        for (i in 0 until 256) { acc += hist[i]; if (acc >= target) return i / 255f }
        return 1f
    }

    fun quality(px: IntArray, w: Int, h: Int): Quality {
        val l = FloatArray(px.size) { Px.luma(px[it]) }
        val sorted = l.copyOf().also { it.sort() }
        val p05 = sorted[(sorted.size * 0.05f).toInt()]; val p95 = sorted[(sorted.size * 0.95f).toInt()]
        var tex = 0.0; var n = 0; var clip = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val v = l[i]
            if (v > 0.985f) clip++
            if (v !in 0.06f..0.94f) continue
            tex += abs(4 * v - l[i - 1] - l[i + 1] - l[i - w] - l[i + w]); n++
        }
        return Quality(p95 - p05, if (n == 0) 0f else (tex / n).toFloat(), darkChannel(px), clip.toFloat() / px.size)
    }

    /** O resultado não pode ser pior que o original em nenhuma das medidas. */
    fun acceptable(orig: Quality, out: Quality, intent: ToneIntent = ToneIntent.LIGHT): Boolean =
        // O Noturno precisa abrir as sombras (perde um pouco de contraste global por definição).
        out.contrast >= orig.contrast * (if (intent == ToneIntent.NIGHT) 0.8f else 0.93f) &&
            out.texture >= orig.texture * 0.95f &&
            out.black <= orig.black + 0.012f &&
            out.clipped <= orig.clipped + 0.006f

    /**
     * Plano para uma prévia [preview] (w×h, lado maior ≈ 1024) da foto. [zoom] é a ampliação
     * equivalente da captura (contraste local só no zoom).
     */
    fun plan(preview: IntArray, w: Int, h: Int, intent: ToneIntent, zoom: Float = 1f): Plan {
        val luma = Planes.downsampleLuma(preview, w, h, 1)
        val stats = SceneStats.of(luma, darkChannel(preview))
        val params = ToneParams.plan(stats, intent, zoom)
        val base = ToneMap.baseFrom(luma)
        val mid = if (params.clarity > 0f) ToneMap.midFrom(luma) else null
        if (params.isIdentity) return Plan(params, 0f, base, mid, "cena bem exposta: sem ajuste", stats)
        val orig = quality(preview, w, h)
        for (s in floatArrayOf(1f, 0.65f, 0.35f)) {
            val test = preview.copyOf()
            ToneMap(base, params, w, h, mid).applyTile(test, w, h, 0, 0, s)
            if (acceptable(orig, quality(test, w, h), intent)) {
                return Plan(params, s, base, mid, "ajuste ${(s * 100).toInt()}% (${describe(params)})", stats)
            }
        }
        return Plan(params, 0f, base, mid, "ajuste pioraria a foto: mantida como capturada", stats)
    }

    private fun describe(p: ToneParams) = buildList {
        if (p.blackLevel > 0.005f) add("pretos")
        if (p.shadowLiftEv > 0.02f) add("sombras")
        if (p.highlightCompression > 0.02f) add("altas luzes")
        if (p.clarity > 0.01f) add("contraste local")
    }.joinToString()
}

