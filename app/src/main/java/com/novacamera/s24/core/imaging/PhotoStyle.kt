package com.novacamera.s24.core.imaging

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Estilo escolhido antes da foto, ao vivo: **tom** (−1 sombras abertas e menos contraste … +1
 * sombras mais densas e mais contraste) e **temperatura** (−1 mais fria … +1 mais quente). A mesma
 * conta roda no shader da pré-visualização ([GLSL]) e em cada foto gravada: o que se vê é o que sai.
 * Atua na imagem sRGB já pronta, preservando o matiz (escala RGB pela razão de luminância).
 */
data class PhotoStyle(val tone: Float = 0f, val warmth: Float = 0f) {
    val neutral get() = abs(tone) < 0.005f && abs(warmth) < 0.005f

    fun toMeta() = String.format(Locale.US, "%.2f;%.2f", tone, warmth)

    /** Texto curto para a descrição da foto. */
    fun label(): String? = if (neutral) null else buildList {
        if (abs(tone) >= 0.005f) add("tom ${signed(tone)}")
        if (abs(warmth) >= 0.005f) add("temperatura ${signed(warmth)}")
    }.joinToString(", ", prefix = "estilo: ")

    private fun signed(v: Float) = (v * 5).roundToInt().let { if (it > 0) "+$it" else "$it" }

    fun applyTile(px: IntArray) { if (!neutral) for (i in px.indices) px[i] = apply(px[i]) }

    fun apply(c: Int): Int {
        var r = Px.r(c) / 255f; var g = Px.g(c) / 255f; var b = Px.b(c) / 255f
        val y0 = luma(r, g, b)
        // Temperatura: ganhos de balanço de branco, brilho mantido.
        if (warmth != 0f) {
            r *= 1f + WARM * warmth; b *= 1f - WARM * warmth
            val y1 = luma(r, g, b)
            if (y1 > 1e-5f) { val k = y0 / y1; r *= k; g *= k; b *= k }
        }
        // Tom: curva na luminância, aplicada como razão (matiz e saturação relativos mantidos).
        if (tone != 0f) {
            val y = luma(r, g, b)
            if (y > 1e-4f) { val k = curve(y, tone) / y; r *= k; g *= k; b *= k }
        }
        return Px.argb((r.coerceIn(0f, 1f) * 255f + 0.5f).toInt(), (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt(), (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt())
    }

    companion object {
        const val WARM = 0.10f

        fun fromMeta(s: String?): PhotoStyle = s?.split(';')?.takeIf { it.size == 2 }?.let {
            runCatching { PhotoStyle(it[0].toFloat(), it[1].toFloat()) }.getOrNull()
        } ?: PhotoStyle()

        private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b

        /** Monótona em 0..1, fixa 0 e 1. Positivo: curva em S; negativo: abre sombras sem estourar realces. */
        fun curve(y: Float, t: Float): Float {
            val x = min(max(y, 0f), 1f)
            return if (t >= 0f) x + t * 0.6f * (x * x * (3f - 2f * x) - x)
            else x + (-t) * 0.5f * (sqrt(x) - x) * (1f - x)
        }

        /** A mesma conta em GLSL (uTone, uWarmth), para a pré-visualização. */
        const val GLSL = """
            uniform float uTone; uniform float uWarmth;
            float styleLuma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
            float styleCurve(float y, float t) {
                float x = clamp(y, 0.0, 1.0);
                if (t >= 0.0) return x + t * 0.6 * (x * x * (3.0 - 2.0 * x) - x);
                return x + (-t) * 0.5 * (sqrt(x) - x) * (1.0 - x);
            }
            vec3 applyStyle(vec3 c) {
                float y0 = styleLuma(c);
                if (uWarmth != 0.0) {
                    c *= vec3(1.0 + 0.10 * uWarmth, 1.0, 1.0 - 0.10 * uWarmth);
                    float y1 = styleLuma(c);
                    if (y1 > 1e-5) c *= y0 / y1;
                }
                if (uTone != 0.0) {
                    float y = styleLuma(c);
                    if (y > 1e-4) c *= styleCurve(y, uTone) / y;
                }
                return clamp(c, 0.0, 1.0);
            }"""
    }
}
