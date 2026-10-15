package com.novacamera.s24.core.imaging

import kotlin.math.max
import kotlin.math.min

/**
 * Primitivas do motor de imagem em Kotlin puro (sem Android), para serem testadas na JVM.
 * Pixels são ARGB empacotados em IntArray (mesmo formato de Bitmap.getPixels).
 */

/** Plano de ponto flutuante (luminância, pesos, máscaras) em resolução reduzida ou cheia. */
class Plane(val w: Int, val h: Int, val data: FloatArray = FloatArray(w * h)) {
    operator fun get(x: Int, y: Int) = data[y * w + x]
    operator fun set(x: Int, y: Int, v: Float) { data[y * w + x] = v }

    /** Amostra bilinear em coordenadas do plano (com borda presa). */
    fun sample(x: Float, y: Float): Float {
        val fx = x.coerceIn(0f, (w - 1).toFloat())
        val fy = y.coerceIn(0f, (h - 1).toFloat())
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
        val tx = fx - x0; val ty = fy - y0
        val a = data[y0 * w + x0] + (data[y0 * w + x1] - data[y0 * w + x0]) * tx
        val b = data[y1 * w + x0] + (data[y1 * w + x1] - data[y1 * w + x0]) * tx
        return a + (b - a) * ty
    }

    fun copy() = Plane(w, h, data.copyOf())
}

object Px {
    fun r(c: Int) = (c shr 16) and 0xFF
    fun g(c: Int) = (c shr 8) and 0xFF
    fun b(c: Int) = c and 0xFF
    fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Luminância (Rec.709 sobre valores codificados), 0..1. */
    fun luma(c: Int) = (0.2126f * r(c) + 0.7152f * g(c) + 0.0722f * b(c)) / 255f
}

object Planes {
    /** Luminância média por blocos factor×factor. */
    fun downsampleLuma(px: IntArray, w: Int, h: Int, factor: Int): Plane {
        val ow = max(1, w / factor); val oh = max(1, h / factor)
        val out = Plane(ow, oh)
        for (oy in 0 until oh) for (ox in 0 until ow) {
            var s = 0f; var n = 0
            val y0 = oy * factor; val x0 = ox * factor
            for (y in y0 until min(h, y0 + factor)) {
                val row = y * w
                for (x in x0 until min(w, x0 + factor)) { s += Px.luma(px[row + x]); n++ }
            }
            out.data[oy * ow + ox] = if (n > 0) s / n else 0f
        }
        return out
    }

    /** Média em janela (2r+1)² via imagem integral: custo O(N) independente do raio. */
    fun boxBlur(src: Plane, r: Int): Plane {
        val w = src.w; val h = src.h
        val integral = DoubleArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0.0
            for (x in 0 until w) {
                rowSum += src.data[y * w + x]
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + rowSum
            }
        }
        val out = Plane(w, h)
        for (y in 0 until h) {
            val y0 = max(0, y - r); val y1 = min(h - 1, y + r)
            for (x in 0 until w) {
                val x0 = max(0, x - r); val x1 = min(w - 1, x + r)
                val s = integral[(y1 + 1) * (w + 1) + x1 + 1] - integral[y0 * (w + 1) + x1 + 1] -
                    integral[(y1 + 1) * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
                out.data[y * w + x] = (s / ((y1 - y0 + 1) * (x1 - x0 + 1))).toFloat()
            }
        }
        return out
    }

    /**
     * Filtro guiado (He, Sun, Tang): suaviza [input] preservando as bordas presentes em [guide].
     * Base do mapeamento tonal local (sem halos) e do refinamento de máscara de retrato (fios de cabelo).
     */
    fun guidedFilter(guide: Plane, input: Plane, r: Int, eps: Float): Plane {
        val n = guide.w * guide.h
        val ip = Plane(guide.w, guide.h); val ii = Plane(guide.w, guide.h)
        for (i in 0 until n) { ip.data[i] = guide.data[i] * input.data[i]; ii.data[i] = guide.data[i] * guide.data[i] }
        val mI = boxBlur(guide, r); val mP = boxBlur(input, r)
        val mIP = boxBlur(ip, r); val mII = boxBlur(ii, r)
        val a = Plane(guide.w, guide.h); val b = Plane(guide.w, guide.h)
        for (i in 0 until n) {
            val cov = mIP.data[i] - mI.data[i] * mP.data[i]
            val varI = mII.data[i] - mI.data[i] * mI.data[i]
            a.data[i] = cov / (varI + eps)
            b.data[i] = mP.data[i] - a.data[i] * mI.data[i]
        }
        val mA = boxBlur(a, r); val mB = boxBlur(b, r)
        val out = Plane(guide.w, guide.h)
        for (i in 0 until n) out.data[i] = mA.data[i] * guide.data[i] + mB.data[i]
        return out
    }

    /**
     * Filtro guiado em cor (He, Sun, Tang), na versão rápida: coeficientes calculados em [lowGuide]
     * (RGB reduzido) e aplicados em [fullGuide] (RGB na resolução de saída). A cor separa bordas que
     * a luminância não vê — pele e tapete bege com o mesmo brilho, por exemplo.
     */
    fun guidedFilterColor(lowGuide: Array<Plane>, input: Plane, r: Int, eps: Float, fullGuide: Array<Plane>): Plane {
        val w = input.w; val h = input.h; val n = w * h
        val (ir, ig, ib) = lowGuide
        fun prod(a: Plane, b: Plane) = Plane(w, h, FloatArray(n) { a.data[it] * b.data[it] })
        val mR = boxBlur(ir, r); val mG = boxBlur(ig, r); val mB = boxBlur(ib, r); val mP = boxBlur(input, r)
        val mRP = boxBlur(prod(ir, input), r); val mGP = boxBlur(prod(ig, input), r); val mBP = boxBlur(prod(ib, input), r)
        val vRR = boxBlur(prod(ir, ir), r); val vRG = boxBlur(prod(ir, ig), r); val vRB = boxBlur(prod(ir, ib), r)
        val vGG = boxBlur(prod(ig, ig), r); val vGB = boxBlur(prod(ig, ib), r); val vBB = boxBlur(prod(ib, ib), r)
        val aR = Plane(w, h); val aG = Plane(w, h); val aB = Plane(w, h); val b = Plane(w, h)
        for (i in 0 until n) {
            val mr = mR.data[i]; val mg = mG.data[i]; val mb = mB.data[i]; val mp = mP.data[i]
            val cr = mRP.data[i] - mr * mp; val cg = mGP.data[i] - mg * mp; val cb = mBP.data[i] - mb * mp
            val rr = vRR.data[i] - mr * mr + eps; val rg = vRG.data[i] - mr * mg; val rb = vRB.data[i] - mr * mb
            val gg = vGG.data[i] - mg * mg + eps; val gb = vGB.data[i] - mg * mb; val bb = vBB.data[i] - mb * mb + eps
            // Inversa da matriz simétrica 3×3 (covariância + eps·I).
            val i00 = gg * bb - gb * gb; val i01 = rb * gb - rg * bb; val i02 = rg * gb - rb * gg
            val i11 = rr * bb - rb * rb; val i12 = rb * rg - rr * gb; val i22 = rr * gg - rg * rg
            val det = rr * i00 + rg * i01 + rb * i02
            if (kotlin.math.abs(det) < 1e-12f) { b.data[i] = mp; continue }
            val ar = (cr * i00 + cg * i01 + cb * i02) / det
            val ag = (cr * i01 + cg * i11 + cb * i12) / det
            val ab = (cr * i02 + cg * i12 + cb * i22) / det
            aR.data[i] = ar; aG.data[i] = ag; aB.data[i] = ab
            b.data[i] = mp - ar * mr - ag * mg - ab * mb
        }
        val qR = boxBlur(aR, r); val qG = boxBlur(aG, r); val qB = boxBlur(aB, r); val qb = boxBlur(b, r)
        val (fr, fg, fb) = fullGuide
        val out = Plane(fr.w, fr.h)
        val sx = w.toFloat() / fr.w; val sy = h.toFloat() / fr.h
        for (y in 0 until fr.h) {
            val ly = (y + 0.5f) * sy - 0.5f
            for (x in 0 until fr.w) {
                val lx = (x + 0.5f) * sx - 0.5f
                val i = y * fr.w + x
                out.data[i] = qR.sample(lx, ly) * fr.data[i] + qG.sample(lx, ly) * fg.data[i] +
                    qB.sample(lx, ly) * fb.data[i] + qb.sample(lx, ly)
            }
        }
        return out
    }

    /** Média RGB (0..1) por blocos factor×factor. */
    fun downsampleRgb(px: IntArray, w: Int, h: Int, factor: Int): Array<Plane> {
        val ow = max(1, w / factor); val oh = max(1, h / factor)
        val out = Array(3) { Plane(ow, oh) }
        for (oy in 0 until oh) for (ox in 0 until ow) {
            var r = 0f; var g = 0f; var b = 0f; var n = 0
            for (y in oy * factor until min(h, oy * factor + factor)) for (x in ox * factor until min(w, ox * factor + factor)) {
                val c = px[y * w + x]; r += Px.r(c); g += Px.g(c); b += Px.b(c); n++
            }
            val k = 1f / (255f * max(1, n)); val i = oy * ow + ox
            out[0].data[i] = r * k; out[1].data[i] = g * k; out[2].data[i] = b * k
        }
        return out
    }

    fun resize(src: Plane, w: Int, h: Int): Plane {
        val out = Plane(w, h)
        val sx = src.w.toFloat() / w; val sy = src.h.toFloat() / h
        for (y in 0 until h) for (x in 0 until w) out.data[y * w + x] = src.sample((x + 0.5f) * sx - 0.5f, (y + 0.5f) * sy - 0.5f)
        return out
    }

    fun percentile(values: FloatArray, p: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.copyOf().also { it.sort() }
        return sorted[((sorted.size - 1) * p.coerceIn(0f, 1f)).toInt()]
    }
}
