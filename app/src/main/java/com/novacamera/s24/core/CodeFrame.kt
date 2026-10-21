package com.novacamera.s24.core

/**
 * Geometria da moldura do QR no visor (como a câmera da Samsung): quatro cantos em "L" que seguem a forma
 * real do código (inclinado ou em perspectiva), e a aproximação suave entre leituras (~2,5/s) para a moldura
 * deslizar em vez de pular.
 */
object CodeFrame {
    /** Fração de cada lado ocupada pelo braço do "L". */
    const val ARM = 0.24f

    /**
     * Oito segmentos (x0,y0,x1,y1 cada; 32 valores) a partir dos cantos [quad] (x0,y0…x3,y3, em volta),
     * ampliados por [scale] em torno do centro (entrada animada).
     */
    fun brackets(quad: FloatArray, scale: Float = 1f, arm: Float = ARM): FloatArray {
        val cx = (quad[0] + quad[2] + quad[4] + quad[6]) / 4f
        val cy = (quad[1] + quad[3] + quad[5] + quad[7]) / 4f
        val q = FloatArray(8) { i -> if (i % 2 == 0) cx + (quad[i] - cx) * scale else cy + (quad[i] - cy) * scale }
        val out = FloatArray(32)
        for (c in 0 until 4) {
            val x = q[2 * c]; val y = q[2 * c + 1]
            val n = (c + 1) % 4; val p = (c + 3) % 4
            val base = c * 8
            out[base] = x; out[base + 1] = y
            out[base + 2] = x + (q[2 * n] - x) * arm; out[base + 3] = y + (q[2 * n + 1] - y) * arm
            out[base + 4] = x; out[base + 5] = y
            out[base + 6] = x + (q[2 * p] - x) * arm; out[base + 7] = y + (q[2 * p + 1] - y) * arm
        }
        return out
    }

    /** Aproxima [now] de [target] por [rate] (0..1); devolve true enquanto ainda falta mais de meio ponto. */
    fun follow(now: FloatArray, target: FloatArray, rate: Float): Boolean {
        var moving = false
        for (i in now.indices) {
            val d = target[i] - now[i]
            if (kotlin.math.abs(d) > 0.5f) { now[i] += d * rate; moving = true } else now[i] = target[i]
        }
        return moving
    }
}
