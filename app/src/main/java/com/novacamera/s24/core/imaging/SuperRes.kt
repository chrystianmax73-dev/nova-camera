package com.novacamera.s24.core.imaging

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Super-resolução multiquadro para o zoom digital da tele (ideia publicada: Wronski et al., 2019,
 * "Handheld Multi-Frame Super-Resolution"; implementação própria e simplificada).
 *
 * No 10× sobre a lente 5×, a câmera recorta o centro de UM quadro e amplia 2× por interpolação: não
 * há informação entre os pixels, e a pinça mostra granulado e borrão. Aqui a rajada é feita na lente
 * sem recorte; o tremido natural da mão desloca cada quadro por frações de pixel diferentes, e cada
 * amostra é colocada na posição exata numa grade [factor]× mais fina. Com 6–8 quadros a grade fica
 * preenchida por medidas reais (detalhe que existe na cena), não por interpolação nem por IA.
 *
 * Robustez: amostra que não bate com a referência na mesma posição (algo se mexeu) perde peso; onde
 * nenhuma amostra chega, vale a referência interpolada (nunca fica pior que o zoom de um quadro).
 */
object SuperRes {
    /** Região de um quadro lida da memória: pixels ARGB, origem (x0, y0) no próprio quadro. */
    class Region(val px: IntArray, val x0: Int, val y0: Int, val w: Int, val h: Int) {
        private val lumas by lazy { FloatArray(px.size) { Px.luma(px[it]) } }
        fun luma(i: Int): Float = lumas[i]
        /**
         * Luminância suavizada (3×3) só para medir o deslocamento: o detalhe fino serrilhado entre os
         * quadros (justamente o que a SR aproveita) enviesa a parábola do erro na imagem crua.
         */
        val smooth: FloatArray by lazy {
            FloatArray(px.size) { i ->
                val x = i % w; val y = i / w
                var s = 0f; var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx in 0 until w && yy in 0 until h) { s += lumas[yy * w + xx]; n++ }
                }
                s / n
            }
        }
    }

    /**
     * Deslocamento de [mov] em relação a [ref] (em pixels do quadro): [ref] na posição p corresponde a
     * [mov] na posição p + s. Busca inteira em ±[radius] em torno de ([gx], [gy]) e fração pelo mínimo
     * da parábola do erro quadrático em cada eixo. [mov] deve cobrir [ref] com margem ≥ radius + |g| + 1.
     */
    fun estimateShift(ref: Region, mov: Region, gx: Int, gy: Int, radius: Int = 2): Pair<Float, Float> {
        val step = 1
        val rs = ref.smooth; val ms = mov.smooth
        fun ssd(dx: Int, dy: Int): Double {
            var e = 0.0; var n = 0
            var y = 0
            while (y < ref.h) {
                var x = 0
                val my = ref.y0 + y + dy - mov.y0
                if (my in 0 until mov.h) while (x < ref.w) {
                    val mx = ref.x0 + x + dx - mov.x0
                    if (mx in 0 until mov.w) {
                        val d = rs[y * ref.w + x] - ms[my * mov.w + mx]
                        e += d * d; n++
                    }
                    x += step
                }
                y += step
            }
            return if (n == 0) Double.MAX_VALUE else e / n
        }
        var bx = gx; var by = gy; var best = Double.MAX_VALUE
        for (dy in gy - radius..gy + radius) for (dx in gx - radius..gx + radius) {
            val e = ssd(dx, dy); if (e < best) { best = e; bx = dx; by = dy }
        }
        fun vertex(a: Double, b: Double, c: Double): Float {
            val den = a - 2 * b + c
            return if (den <= 1e-12) 0f else (0.5 * (a - c) / den).toFloat().coerceIn(-0.5f, 0.5f)
        }
        val fx = vertex(ssd(bx - 1, by), best, ssd(bx + 1, by))
        val fy = vertex(ssd(bx, by - 1), best, ssd(bx, by + 1))
        return (bx + fx) to (by + fy)
    }

    /**
     * Uma região de saída ([outW] × [outH], origem ([ox0], [oy0]) na foto final) do recorte ampliado
     * [factor]×. O pixel de saída u corresponde, na referência, a cropX + (u + 0,5)/factor − 0,5.
     * [frames] = regiões de cada quadro e o deslocamento medido (ref p ↔ quadro p + s); o índice 0 é
     * a referência (deslocamento 0). [noise] = desvio do ruído em luminância (0..1).
     */
    fun mergeTile(
        outW: Int, outH: Int, ox0: Int, oy0: Int, factor: Float, cropX: Float, cropY: Float,
        frames: List<Pair<Region, Pair<Float, Float>>>, noise: Float,
    ): IntArray {
        val n = outW * outH
        val accR = FloatArray(n); val accG = FloatArray(n); val accB = FloatArray(n); val accW = FloatArray(n)
        val sigma = 0.28f * factor
        val inv2s2 = 1f / (2f * sigma * sigma)
        val rad = max(1, (2f * sigma + 0.5f).toInt())
        val ref = frames[0].first
        val tol = 0.04f + 3f * noise
        // Robustez pela faixa local da referência (mín./máx. 3×3 em volta da posição): o detalhe fino
        // real fica dentro dela e é aceito; algo que entrou ou saiu da cena fica fora e é rejeitado.
        // (Comparar com a referência interpolada rejeitava justamente o detalhe que a SR recupera.)
        fun outside(qx: Float, qy: Float, v: Float): Float {
            val cx = (qx - ref.x0).roundToInt().coerceIn(1, ref.w - 2); val cy = (qy - ref.y0).roundToInt().coerceIn(1, ref.h - 2)
            var lo = 1f; var hi = 0f
            for (dy in -1..1) for (dx in -1..1) { val l = ref.luma((cy + dy) * ref.w + cx + dx); if (l < lo) lo = l; if (l > hi) hi = l }
            return max(0f, max(lo - v, v - hi))
        }
        for ((k, pair) in frames.withIndex()) {
            val (reg, s) = pair
            for (j in 0 until reg.h) for (i in 0 until reg.w) {
                // Posição desta amostra na referência e na saída.
                val qx = reg.x0 + i - s.first; val qy = reg.y0 + j - s.second
                val ux = (qx - cropX + 0.5f) * factor - 0.5f - ox0
                val uy = (qy - cropY + 0.5f) * factor - 0.5f - oy0
                if (ux < -rad || uy < -rad || ux > outW + rad || uy > outH + rad) continue
                val c = reg.px[j * reg.w + i]
                var robust = 1f
                if (k > 0) {
                    val d = outside(qx, qy, Px.luma(c))
                    if (d > tol) robust = (1f - (d - tol) / tol).coerceIn(0f, 1f)
                    if (robust <= 0f) continue
                }
                val r = Px.r(c).toFloat(); val g = Px.g(c).toFloat(); val b = Px.b(c).toFloat()
                val cx = ux.roundToInt(); val cy = uy.roundToInt()
                for (y in max(0, cy - rad)..min(outH - 1, cy + rad)) {
                    val dy = y - uy
                    for (x in max(0, cx - rad)..min(outW - 1, cx + rad)) {
                        val dx = x - ux
                        val w = exp(-(dx * dx + dy * dy) * inv2s2) * robust
                        if (w < 1e-3f) continue
                        val o = y * outW + x
                        accR[o] += w * r; accG[o] += w * g; accB[o] += w * b; accW[o] += w
                    }
                }
            }
        }
        val out = IntArray(n)
        for (y in 0 until outH) for (x in 0 until outW) {
            val o = y * outW + x
            val w = accW[o]
            out[o] = if (w >= 0.05f) Px.argb((accR[o] / w + 0.5f).toInt().coerceIn(0, 255),
                (accG[o] / w + 0.5f).toInt().coerceIn(0, 255), (accB[o] / w + 0.5f).toInt().coerceIn(0, 255))
            else bilinear(ref, cropX + (ox0 + x + 0.5f) / factor - 0.5f, cropY + (oy0 + y + 0.5f) / factor - 0.5f)
        }
        return out
    }

    /** Referência interpolada (recurso onde nenhuma amostra chegou e base do zoom de um quadro). */
    fun bilinear(reg: Region, qx: Float, qy: Float): Int {
        val x = qx - reg.x0; val y = qy - reg.y0
        val x0 = floor(x).toInt().coerceIn(0, reg.w - 2); val y0 = floor(y).toInt().coerceIn(0, reg.h - 2)
        val tx = (x - x0).coerceIn(0f, 1f); val ty = (y - y0).coerceIn(0f, 1f)
        val a = reg.px[y0 * reg.w + x0]; val b = reg.px[y0 * reg.w + x0 + 1]
        val c = reg.px[(y0 + 1) * reg.w + x0]; val d = reg.px[(y0 + 1) * reg.w + x0 + 1]
        fun ch(sh: Int): Int {
            fun v(p: Int) = ((p shr sh) and 0xFF).toFloat()
            val top = v(a) + (v(b) - v(a)) * tx; val bot = v(c) + (v(d) - v(c)) * tx
            return (top + (bot - top) * ty + 0.5f).toInt().coerceIn(0, 255)
        }
        return Px.argb(ch(16), ch(8), ch(0))
    }
}
