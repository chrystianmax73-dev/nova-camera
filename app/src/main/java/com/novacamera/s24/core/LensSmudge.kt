package com.novacamera.s24.core

/**
 * Aviso de lente suja (como o do Pixel): gordura na lente espalha luz — preto vira cinza e o contraste cai
 * na cena inteira, em qualquer enquadramento. Neblina, parede branca ou contraluz também fazem isso, mas
 * só numa cena; por isso o aviso exige luz boa e o mesmo véu em ≥ [MIN_SCENES] cenas diferentes, ≥ 80% das
 * amostras de [WINDOW_MS], e aparece no máximo uma vez a cada [COOLDOWN_MS]. Sem custo extra: usa o mesmo
 * quadro reduzido que o leitor de QR já lê.
 */
class LensSmudge {
    companion object {
        const val VEIL_BLACK = 0.12f
        const val VEIL_CONTRAST = 0.45f
        const val WINDOW_MS = 30_000L
        const val MIN_SPAN_MS = 10_000L
        const val MIN_SAMPLES = 8
        const val MIN_SCENES = 3
        const val COOLDOWN_MS = 30 * 60_000L

        /** Ponto preto (0,5º percentil do menor canal) e contraste (p95 − p5 da luminância), 0..1. */
        fun measure(px: IntArray): Pair<Float, Float> {
            val dark = IntArray(256); val luma = IntArray(256)
            for (c in px) {
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
                dark[minOf(r, minOf(g, b))]++
                luma[(r * 77 + g * 150 + b * 29) shr 8]++
            }
            fun pct(h: IntArray, q: Float): Float { val t = px.size * q; var a = 0
                for (i in 0 until 256) { a += h[i]; if (a >= t) return i / 255f }; return 1f }
            return pct(dark, 0.005f) to (pct(luma, 0.95f) - pct(luma, 0.05f))
        }

        /** Assinatura grosseira da cena (4×4 blocos de luminância) para saber se o enquadramento mudou. */
        fun sceneKey(px: IntArray, w: Int, h: Int): Int {
            var key = 0
            for (by in 0 until 4) for (bx in 0 until 4) {
                var s = 0L; var n = 0
                for (y in by * h / 4 until (by + 1) * h / 4 step 4) for (x in bx * w / 4 until (bx + 1) * w / 4 step 4) {
                    val c = px[y * w + x]; s += ((c shr 16 and 255) * 77 + (c shr 8 and 255) * 150 + (c and 255) * 29) shr 8; n++
                }
                key = key * 31 + ((s / maxOf(1, n)) / 32).toInt()
            }
            return key
        }
    }

    private class Sample(val at: Long, val veiled: Boolean, val scene: Int)
    private val samples = ArrayDeque<Sample>()
    private var lastWarn = Long.MIN_VALUE / 2

    /** Registra uma amostra com luz boa; devolve true quando é hora de avisar. */
    fun add(black: Float, contrast: Float, scene: Int, nowMs: Long): Boolean {
        samples.addLast(Sample(nowMs, black >= VEIL_BLACK && contrast <= VEIL_CONTRAST, scene))
        while (samples.isNotEmpty() && nowMs - samples.first().at > WINDOW_MS) samples.removeFirst()
        if (nowMs - lastWarn < COOLDOWN_MS || samples.size < MIN_SAMPLES) return false
        val veiled = samples.filter { it.veiled }
        if (veiled.size < 0.8f * samples.size || veiled.size < MIN_SAMPLES) return false
        if (veiled.last().at - veiled.first().at < MIN_SPAN_MS) return false
        if (veiled.map { it.scene }.distinct().size < MIN_SCENES) return false
        lastWarn = nowMs; samples.clear()
        return true
    }
}
