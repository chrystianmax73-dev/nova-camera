package com.novacamera.s24.core

import kotlin.math.hypot

/**
 * "Palma aberta" a partir dos 21 pontos do detector de mão do MediaPipe (Apache-2.0). A regra é do app:
 * o classificador de gestos pronto da Google não tem licença publicada. Os quatro dedos esticados (ponta
 * bem mais longe do pulso que a junta do meio) e o polegar afastado do indicador; mão pequena demais na
 * imagem (fundo, foto na parede) é ignorada.
 */
object PalmPose {
    /** Mão (pulso → base do dedo médio) com pelo menos esta fração do lado maior da imagem. */
    const val MIN_HAND = 0.06f
    const val FINGER_RATIO = 1.15f

    private val FINGERS = listOf(intArrayOf(8, 6, 5), intArrayOf(12, 10, 9), intArrayOf(16, 14, 13), intArrayOf(20, 18, 17))

    /** [xy]: 42 valores (x0, y0, …, x20, y20) normalizados 0..1; [aspect] = largura/altura da imagem. */
    fun isOpenPalm(xy: FloatArray, aspect: Float = 1f): Boolean {
        if (xy.size < 42) return false
        // Distâncias em unidades do lado maior, para a mesma regra servir em retrato e paisagem.
        val sx = if (aspect >= 1f) 1f else aspect
        val sy = if (aspect >= 1f) 1f / aspect else 1f
        fun d(a: Int, b: Int) = hypot((xy[2 * a] - xy[2 * b]) * sx, (xy[2 * a + 1] - xy[2 * b + 1]) * sy)
        val size = d(0, 9)
        if (size < MIN_HAND) return false
        for ((tip, pip, mcp) in FINGERS.map { Triple(it[0], it[1], it[2]) }) {
            if (d(0, tip) < d(0, pip) * FINGER_RATIO) return false
            if (d(tip, mcp) < 0.55f * size) return false
        }
        // Polegar aberto: ponta longe da base do indicador e mais longe do pulso que a própria junta.
        return d(4, 5) > 0.45f * size && d(0, 4) > d(0, 3)
    }
}

/** Palma mantida por [HOLD_MS] dispara uma vez; depois espera [COOLDOWN_MS] (a foto e o contador terminam). */
class PalmTrigger {
    companion object {
        const val HOLD_MS = 700L
        const val MIN_HITS = 2
        const val COOLDOWN_MS = 5_000L
    }
    private var since = -1L
    private var hits = 0
    private var lastFire = Long.MIN_VALUE / 2

    fun reset() { since = -1L; hits = 0 }

    fun add(palm: Boolean, nowMs: Long): Boolean {
        if (nowMs - lastFire < COOLDOWN_MS) { reset(); return false }
        if (!palm) { reset(); return false }
        if (since < 0) since = nowMs
        hits++
        if (hits >= MIN_HITS && nowMs - since >= HOLD_MS) { lastFire = nowMs; reset(); return true }
        return false
    }
}
