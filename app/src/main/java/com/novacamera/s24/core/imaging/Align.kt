package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Deslocamento inteiro de um quadro em relação à referência (em pixels da resolução de trabalho). */
data class Shift(val dx: Int, val dy: Int)

/** Deslocamento com fração de pixel (alinhamento subpixel). */
data class FShift(val x: Float, val y: Float)

/**
 * Alinhamento de quadros de mão livre por busca hierárquica (grosseira → fina) minimizando a
 * diferença absoluta de luminância. Global primeiro; depois refinamento por regiões para
 * absorver pequena rotação/paralaxe.
 */
object Align {
    /**
     * Melhor deslocamento de [mov] sobre [ref] (mesmo tamanho), procurando em ±[radius] em torno de [prior].
     * Retorna o deslocamento a aplicar em [mov]: mov(x+dx, y+dy) ≈ ref(x, y).
     */
    fun search(ref: Plane, mov: Plane, radius: Int, prior: Shift = Shift(0, 0), region: IntArray? = null, step: Int = 1): Shift {
        val x0 = region?.get(0) ?: 0; val y0 = region?.get(1) ?: 0
        val x1 = region?.get(2) ?: ref.w; val y1 = region?.get(3) ?: ref.h
        var best = prior; var bestCost = Double.MAX_VALUE
        for (dy in prior.dy - radius..prior.dy + radius) for (dx in prior.dx - radius..prior.dx + radius) {
            var cost = 0.0; var n = 0
            var y = max(y0, -dy); val ye = min(y1, ref.h - dy)
            while (y < ye) {
                var x = max(x0, -dx); val xe = min(x1, ref.w - dx)
                while (x < xe) {
                    cost += abs(ref.data[y * ref.w + x] - mov.data[(y + dy) * mov.w + x + dx]); n++
                    x += step
                }
                y += step
            }
            // Exige sobreposição mínima para não premiar deslocamentos que "fogem" da imagem.
            if (n < 16) continue
            val c = cost / n + 1e-5 * (abs(dx - prior.dx) + abs(dy - prior.dy))
            if (c < bestCost) { bestCost = c; best = Shift(dx, dy) }
        }
        return best
    }

    /** Pirâmide de luminância: níveis com fatores crescentes (ex.: 1, 4, 16). */
    fun pyramid(level0: Plane, factors: IntArray): List<Plane> = factors.map { f ->
        if (f == 1) level0 else {
            val out = Plane(max(1, level0.w / f), max(1, level0.h / f))
            for (y in 0 until out.h) for (x in 0 until out.w) {
                var s = 0f
                for (yy in 0 until f) for (xx in 0 until f) s += level0[min(level0.w - 1, x * f + xx), min(level0.h - 1, y * f + yy)]
                out[x, y] = s / (f * f)
            }
            out
        }
    }

    /**
     * Deslocamento global hierárquico entre duas luminâncias de mesma resolução.
     * [maxShift] em pixels dessa resolução.
     */
    fun globalShift(ref: Plane, mov: Plane, maxShift: Int): Shift {
        val factors = intArrayOf(8, 2, 1)
        val pr = pyramid(ref, factors); val pm = pyramid(mov, factors)
        var s = Shift(0, 0)
        for ((i, f) in factors.withIndex()) {
            val radius = if (i == 0) max(2, maxShift / f) else 3
            val prior = if (i == 0) Shift(0, 0) else Shift(s.dx * factors[i - 1] / f, s.dy * factors[i - 1] / f)
            s = search(pr[i], pm[i], radius, prior, step = if (f == 1) 2 else 1)
        }
        return s
    }

    /**
     * Campo de deslocamentos por regiões (grade gx×gy), refinado em torno do global.
     * Regiões sem textura (céu, parede lisa) herdam o deslocamento global.
     */
    fun tileShifts(ref: Plane, mov: Plane, global: Shift, gx: Int, gy: Int, radius: Int = 2): Array<Shift> {
        val out = Array(gx * gy) { global }
        for (ty in 0 until gy) for (tx in 0 until gx) {
            val rx0 = tx * ref.w / gx; val ry0 = ty * ref.h / gy
            val rx1 = (tx + 1) * ref.w / gx; val ry1 = (ty + 1) * ref.h / gy
            if (textureOf(ref, rx0, ry0, rx1, ry1) < 0.004f) continue
            out[ty * gx + tx] = search(ref, mov, radius, global, intArrayOf(rx0, ry0, rx1, ry1))
        }
        return out
    }

    /** Custo médio (diferença absoluta) de um deslocamento inteiro numa região. */
    private fun cost(ref: Plane, mov: Plane, dx: Int, dy: Int, region: IntArray?): Double {
        val x0 = region?.get(0) ?: 0; val y0 = region?.get(1) ?: 0
        val x1 = region?.get(2) ?: ref.w; val y1 = region?.get(3) ?: ref.h
        var c = 0.0; var n = 0
        for (y in max(y0, -dy) until min(y1, ref.h - dy)) for (x in max(x0, -dx) until min(x1, ref.w - dx)) {
            c += abs(ref.data[y * ref.w + x] - mov.data[(y + dy) * mov.w + x + dx]); n++
        }
        return if (n < 16) Double.MAX_VALUE else c / n
    }

    /**
     * Refino subpixel: ajusta uma parábola ao custo em s−1, s, s+1 (em x e em y separadamente) e
     * toma o mínimo. Limitado a ±0,5 px; se a curva não for convexa, mantém o inteiro.
     */
    fun refine(ref: Plane, mov: Plane, s: Shift, region: IntArray? = null): FShift {
        fun axis(cm: Double, c0: Double, cp: Double): Float {
            if (cm == Double.MAX_VALUE || cp == Double.MAX_VALUE) return 0f
            val den = cm - 2 * c0 + cp
            if (den <= 1e-9) return 0f
            return (0.5 * (cm - cp) / den).toFloat().coerceIn(-0.5f, 0.5f)
        }
        val c0 = cost(ref, mov, s.dx, s.dy, region)
        val fx = axis(cost(ref, mov, s.dx - 1, s.dy, region), c0, cost(ref, mov, s.dx + 1, s.dy, region))
        val fy = axis(cost(ref, mov, s.dx, s.dy - 1, region), c0, cost(ref, mov, s.dx, s.dy + 1, region))
        return FShift(s.dx + fx, s.dy + fy)
    }

    /** Campo subpixel a partir do campo inteiro (regiões sem textura herdam o global refinado). */
    fun refineField(ref: Plane, mov: Plane, field: Array<Shift>, global: Shift, gx: Int, gy: Int): Array<FShift> {
        val g = refine(ref, mov, global)
        return Array(gx * gy) { i ->
            val s = field[i]
            if (s == global) g else {
                val tx = i % gx; val ty = i / gx
                refine(ref, mov, s, intArrayOf(tx * ref.w / gx, ty * ref.h / gy, (tx + 1) * ref.w / gx, (ty + 1) * ref.h / gy))
            }
        }
    }

    /** Como [shiftAt], com fração de pixel (resultado já na escala da imagem cheia). */
    fun shiftAtF(field: Array<FShift>, gx: Int, gy: Int, w: Int, h: Int, x: Float, y: Float, scale: Float): FShift {
        val fx = (x / w * gx - 0.5f).coerceIn(0f, (gx - 1).toFloat())
        val fy = (y / h * gy - 0.5f).coerceIn(0f, (gy - 1).toFloat())
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = min(x0 + 1, gx - 1); val y1 = min(y0 + 1, gy - 1)
        val tx = fx - x0; val ty = fy - y0
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        val s00 = field[y0 * gx + x0]; val s10 = field[y0 * gx + x1]; val s01 = field[y1 * gx + x0]; val s11 = field[y1 * gx + x1]
        return FShift(lerp(lerp(s00.x, s10.x, tx), lerp(s01.x, s11.x, tx), ty) * scale,
            lerp(lerp(s00.y, s10.y, tx), lerp(s01.y, s11.y, tx), ty) * scale)
    }

    /** Deslocamento interpolado (bilinear entre centros de regiões) num ponto (x, y). */
    fun shiftAt(field: Array<Shift>, gx: Int, gy: Int, w: Int, h: Int, x: Float, y: Float, scale: Float): Shift {
        val fx = (x / w * gx - 0.5f).coerceIn(0f, (gx - 1).toFloat())
        val fy = (y / h * gy - 0.5f).coerceIn(0f, (gy - 1).toFloat())
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = min(x0 + 1, gx - 1); val y1 = min(y0 + 1, gy - 1)
        val tx = fx - x0; val ty = fy - y0
        fun lerp(a: Int, b: Int, t: Float) = a + (b - a) * t
        val s00 = field[y0 * gx + x0]; val s10 = field[y0 * gx + x1]; val s01 = field[y1 * gx + x0]; val s11 = field[y1 * gx + x1]
        val dx = lerp(lerp(s00.dx, s10.dx, tx).roundToInt(), lerp(s01.dx, s11.dx, tx).roundToInt(), ty)
        val dy = lerp(lerp(s00.dy, s10.dy, tx).roundToInt(), lerp(s01.dy, s11.dy, tx).roundToInt(), ty)
        return Shift((dx * scale).roundToInt(), (dy * scale).roundToInt())
    }

    private fun textureOf(p: Plane, x0: Int, y0: Int, x1: Int, y1: Int): Float {
        var s = 0f; var n = 0
        for (y in y0 until y1 - 1) for (x in x0 until x1 - 1) {
            s += abs(p[x, y] - p[x + 1, y]) + abs(p[x, y] - p[x, y + 1]); n++
        }
        return if (n == 0) 0f else s / n
    }
}
