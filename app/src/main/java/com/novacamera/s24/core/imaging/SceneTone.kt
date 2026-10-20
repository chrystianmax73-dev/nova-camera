package com.novacamera.s24.core.imaging

import com.novacamera.s24.core.SceneDetect.Scene
import kotlin.math.max
import kotlin.math.min

/**
 * Ajuste leve por cena na foto salva (o Scene Optimizer da Samsung é criticado por exagerar: aqui fica abaixo
 * de +10%, valores da pesquisa de 20/10). Vibrance (sobe mais o que é pouco saturado), nunca em pele:
 * - Planta: só nos verdes e amarelo-esverdeados, +8%.
 * - Comida: vibrance +8% e leve aquecimento (+2% no vermelho, −2% no azul).
 * Pessoa e animal: sem mudança de cor (o ícone indica o tratamento que já existe: contraluz, foco no rosto).
 */
object SceneTone {
    const val PLANT_VIBRANCE = 0.08f
    const val FOOD_VIBRANCE = 0.08f
    const val FOOD_WARMTH = 0.02f

    fun changesColor(scene: Scene) = scene == Scene.PLANT || scene == Scene.FOOD

    fun applyTile(tile: IntArray, scene: Scene) {
        if (!changesColor(scene)) return
        for (k in tile.indices) {
            val c = tile[k]
            var r = ((c shr 16) and 255).toFloat(); var g = ((c shr 8) and 255).toFloat(); var b = (c and 255).toFloat()
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            if (mx < 8f) continue
            val sat = (mx - mn) / mx
            val d = mx - mn
            val hue = when {
                d < 1e-3f -> 0f
                mx == r -> 60f * (((g - b) / d) % 6f)
                mx == g -> 60f * ((b - r) / d + 2f)
                else -> 60f * ((r - g) / d + 4f)
            }.let { if (it < 0f) it + 360f else it }
            // Pele (laranja-avermelhado pouco saturado) fica como está.
            val skin = hue in 5f..45f && sat in 0.15f..0.6f
            val amount = when (scene) {
                Scene.PLANT -> if (hue in 60f..170f) PLANT_VIBRANCE * smooth(60f, 80f, hue) * (1f - smooth(150f, 170f, hue)) else 0f
                Scene.FOOD -> if (skin) 0f else FOOD_VIBRANCE
                else -> 0f
            } * (1f - sat)
            if (amount > 0f) {
                val y = 0.299f * r + 0.587f * g + 0.114f * b
                r = y + (r - y) * (1f + amount); g = y + (g - y) * (1f + amount); b = y + (b - y) * (1f + amount)
            }
            if (scene == Scene.FOOD && !skin) { r *= 1f + FOOD_WARMTH; b *= 1f - FOOD_WARMTH }
            tile[k] = (c and (0xFF shl 24)) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
        }
    }

    private fun clamp(v: Float) = (v + 0.5f).toInt().coerceIn(0, 255)
    private fun smooth(e0: Float, e1: Float, v: Float): Float { val t = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
}
