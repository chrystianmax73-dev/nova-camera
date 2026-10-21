package com.novacamera.s24.core.imaging

import kotlin.math.floor

/**
 * IA em meia resolução para fotos grandes (Alta resolução, ~49 MP): a foto é reduzida 2× (média 2×2),
 * a rede limpa a versão reduzida (¼ dos blocos) e só a diferença que ela fez (o ruído retirado) volta
 * ampliada para a foto cheia. O detalhe próprio da foto grande não passa pela rede e fica intacto; o
 * ruído de 1 pixel que sobra é o que a super-resolução já atenuou ao juntar os quadros.
 */
object HalfResidual {
    /** Média 2×2 de [rows] linhas (par) de [src] (largura [w]) para a linha [oy] de [out] (largura w/2). */
    fun box2(src: IntArray, w: Int, rows: Int, out: IntArray, oy: Int) {
        val sw = w / 2
        for (r in 0 until rows / 2) for (x in 0 until sw) {
            val i = 2 * r * w + 2 * x
            val a = src[i]; val b = src[i + 1]; val c = src[i + w]; val d = src[i + w + 1]
            fun ch(s: Int) = (((a shr s) and 255) + ((b shr s) and 255) + ((c shr s) and 255) + ((d shr s) and 255) + 2) / 4
            out[(oy + r) * sw + x] = (255 shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }

    /** Soma à faixa [band] (linhas [y0]..) a diferença [den]−[orig] (meia resolução sw×sh), interpolada. */
    fun addResidual(band: IntArray, w: Int, rows: Int, y0: Int, orig: IntArray, den: IntArray, sw: Int, sh: Int) {
        for (r in 0 until rows) {
            val v = ((y0 + r + 0.5f) / 2f - 0.5f).coerceIn(0f, sh - 1f)
            val v0 = floor(v).toInt(); val v1 = minOf(v0 + 1, sh - 1); val fv = v - v0
            for (x in 0 until w) {
                val u = ((x + 0.5f) / 2f - 0.5f).coerceIn(0f, sw - 1f)
                val u0 = floor(u).toInt(); val u1 = minOf(u0 + 1, sw - 1); val fu = u - u0
                val i00 = v0 * sw + u0; val i01 = v0 * sw + u1; val i10 = v1 * sw + u0; val i11 = v1 * sw + u1
                val p = band[r * w + x]
                fun ch(s: Int): Int {
                    fun d(i: Int) = ((den[i] shr s) and 255) - ((orig[i] shr s) and 255)
                    val top = d(i00) + (d(i01) - d(i00)) * fu
                    val bottom = d(i10) + (d(i11) - d(i10)) * fu
                    val delta = top + (bottom - top) * fv
                    return (((p shr s) and 255) + delta + 0.5f).toInt().coerceIn(0, 255)
                }
                band[r * w + x] = (p and (0xFF shl 24)) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
    }
}
