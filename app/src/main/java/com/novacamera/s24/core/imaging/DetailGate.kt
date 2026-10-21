package com.novacamera.s24.core.imaging

import kotlin.math.abs

/**
 * Checagem de consistência da IA de detalhe (Real-ESRGAN, rede generativa). A rede recebe a foto reduzida
 * à metade (escala real do sensor quando houve zoom digital) e devolve o dobro: o tamanho da foto. Uma rede
 * generativa pode inventar (letra deformada, textura "pintada"); o teste que pega isso é voltar: a saída
 * reduzida à metade precisa bater com a entrada. Por bloco de [BLOCK] px (na metade), o erro médio decide o
 * peso: abaixo de [LO] vale a IA, acima de [HI] fica o original; blocos recusados crescem um bloco para os
 * lados (a borda de uma letra deformada também fica de fora) e a transição é suavizada; o miolo recusado
 * continua perto de zero porque os vizinhos dele também foram zerados pela erosão.
 *
 * Calibrado nas fotos de 10× do dono (15/10): letras pequenas do aspirador e a parede de reboco recusadas;
 * mangueira e o desenho do saco de cimento aceitos (erro mediano ~0,007; letras e parede > 0,02).
 */
object DetailGate {
    const val BLOCK = 16
    const val LO = 0.010f
    const val HI = 0.016f
    /** Menos que isso aceito: não compensa trocar a foto. */
    const val MIN_ACCEPTED = 0.15f

    /** Média 2×2 (filtro de área), ARGB opaco. */
    fun halve(px: IntArray, w: Int, h: Int): IntArray {
        val hw = w / 2; val hh = h / 2
        val out = IntArray(hw * hh)
        for (y in 0 until hh) {
            val r0 = 2 * y * w; val r1 = r0 + w
            for (x in 0 until hw) {
                val a = px[r0 + 2 * x]; val b = px[r0 + 2 * x + 1]; val c = px[r1 + 2 * x]; val d = px[r1 + 2 * x + 1]
                val r = (((a shr 16) and 255) + ((b shr 16) and 255) + ((c shr 16) and 255) + ((d shr 16) and 255) + 2) shr 2
                val g = (((a shr 8) and 255) + ((b shr 8) and 255) + ((c shr 8) and 255) + ((d shr 8) and 255) + 2) shr 2
                val bl = ((a and 255) + (b and 255) + (c and 255) + (d and 255) + 2) shr 2
                out[y * hw + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
        return out
    }

    class Grid(val bw: Int, val bh: Int, val values: FloatArray)

    /** Erro de volta por bloco: |metade(saída) − entrada| médio dos três canais, 0..1. [out] tem 2× o tamanho de [half]. */
    fun backError(out: IntArray, half: IntArray, hw: Int, hh: Int): Grid {
        val bw = (hw + BLOCK - 1) / BLOCK; val bh = (hh + BLOCK - 1) / BLOCK
        val sum = DoubleArray(bw * bh); val count = IntArray(bw * bh)
        val w = hw * 2
        for (y in 0 until hh) {
            val r0 = 2 * y * w; val r1 = r0 + w
            for (x in 0 until hw) {
                val a = out[r0 + 2 * x]; val b = out[r0 + 2 * x + 1]; val c = out[r1 + 2 * x]; val d = out[r1 + 2 * x + 1]
                val s = half[y * hw + x]
                var e = 0
                for (sh in intArrayOf(16, 8, 0)) {
                    val m = (((a shr sh) and 255) + ((b shr sh) and 255) + ((c shr sh) and 255) + ((d shr sh) and 255) + 2) shr 2
                    e += abs(m - ((s shr sh) and 255))
                }
                val k = (y / BLOCK) * bw + x / BLOCK
                sum[k] += e / 3.0; count[k]++
            }
        }
        return Grid(bw, bh, FloatArray(bw * bh) { (sum[it] / maxOf(1, count[it]) / 255.0).toFloat() })
    }

    /** Peso da IA por bloco (0 = original, 1 = IA): rampa entre [LO] e [HI], recusa ampliada e borda suave. */
    fun weights(err: Grid): Grid {
        val bw = err.bw; val bh = err.bh
        val w = FloatArray(bw * bh) { ((HI - err.values[it]) / (HI - LO)).coerceIn(0f, 1f) }
        // Erosão 3×3: um bloco recusado recusa os vizinhos.
        val eroded = FloatArray(bw * bh) { i ->
            val x = i % bw; val y = i / bw
            var m = 1f
            for (dy in -1..1) for (dx in -1..1) {
                val xx = (x + dx).coerceIn(0, bw - 1); val yy = (y + dy).coerceIn(0, bh - 1)
                m = minOf(m, w[yy * bw + xx])
            }
            m
        }
        var cur = eroded
        // Uma passada 3×3 (≈ a suavização calibrada no servidor; duas passadas devolviam ~20% ao miolo recusado).
        repeat(1) {
            val src = cur
            cur = FloatArray(bw * bh) { i ->
                val x = i % bw; val y = i / bw
                var s = 0f
                for (dy in -1..1) for (dx in -1..1) s += src[(y + dy).coerceIn(0, bh - 1) * bw + (x + dx).coerceIn(0, bw - 1)]
                s / 9f
            }
        }
        return Grid(bw, bh, cur)
    }

    fun acceptedFraction(weights: Grid): Float = weights.values.count { it > 0.5f }.toFloat() / weights.values.size.coerceAtLeast(1)

    /**
     * Mistura em [orig] (tamanho cheio [w]×[h]): orig = p·out + (1−p)·orig, com p interpolado entre os
     * centros dos blocos ([BLOCK]·2 px na foto cheia). [out] cobre 2·(w/2) × 2·(h/2); a última linha/coluna
     * de uma foto ímpar fica como está.
     */
    fun blend(orig: IntArray, w: Int, h: Int, out: IntArray, weights: Grid) =
        blendRows(orig, w, 0, minOf(h, (h / 2) * 2), out, (w / 2) * 2, weights)

    /** Faixa de linhas [y0], [y0]+[rows] da foto: [orig] e [out] guardam só essas linhas (larguras [w] e [ow]). */
    fun blendRows(orig: IntArray, w: Int, y0: Int, rows: Int, out: IntArray, ow: Int, weights: Grid) {
        val cell = BLOCK * 2f
        val v = weights.values
        for (ly in 0 until rows) {
            val y = y0 + ly
            val gy = ((y + 0.5f) / cell - 0.5f).coerceIn(0f, (weights.bh - 1).toFloat())
            val gy0 = gy.toInt(); val gy1 = minOf(gy0 + 1, weights.bh - 1); val fy = gy - gy0
            for (x in 0 until ow) {
                val gx = ((x + 0.5f) / cell - 0.5f).coerceIn(0f, (weights.bw - 1).toFloat())
                val gx0 = gx.toInt(); val gx1 = minOf(gx0 + 1, weights.bw - 1); val fx = gx - gx0
                val p = (v[gy0 * weights.bw + gx0] * (1 - fx) + v[gy0 * weights.bw + gx1] * fx) * (1 - fy) +
                    (v[gy1 * weights.bw + gx0] * (1 - fx) + v[gy1 * weights.bw + gx1] * fx) * fy
                if (p <= 0.002f) continue
                val i = ly * w + x
                val a = orig[i]; val b = out[ly * ow + x]
                if (p >= 0.998f) { orig[i] = b or (0xFF shl 24); continue }
                val q = 1 - p
                val r = (((b shr 16) and 255) * p + ((a shr 16) and 255) * q + 0.5f).toInt()
                val g = (((b shr 8) and 255) * p + ((a shr 8) and 255) * q + 0.5f).toInt()
                val bl = ((b and 255) * p + (a and 255) * q + 0.5f).toInt()
                orig[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
    }
}
