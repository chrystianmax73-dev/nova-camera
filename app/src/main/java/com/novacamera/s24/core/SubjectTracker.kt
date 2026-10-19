package com.novacamera.s24.core

import kotlin.math.*

/**
 * Rastreamento de aparência em luminância. Não classifica nem segmenta pessoas/objetos.
 * O modelo se adapta devagar quando a leitura é inequívoca (pessoa virando, objeto girando), preso ao
 * modelo do toque para não derivar para o fundo; incerto, procura numa área maior antes de desistir.
 */
class SubjectTracker {
    enum class Phase { TRACKING, UNCERTAIN, LOST }
    data class Result(val phase: Phase, val x: Float, val y: Float, val side: Float,
        val confidence: Float, val moving: Boolean, val reason: String = "")
    private val n = 12
    private val scales = floatArrayOf(0.96f, 1f, 1.04f)
    private val template = FloatArray(n * n)
    private val anchor = FloatArray(n * n)
    private val patch = FloatArray(n * n)
    private val peaksX = FloatArray(1600)
    private val peaksY = FloatArray(1600)
    private val peaksScore = FloatArray(1600)
    private var width = 0
    private var height = 0
    private var x = 0f; private var y = 0f; private var side = 0f
    private var shownX = 0f; private var shownY = 0f
    private var vx = 0f; private var vy = 0f
    private var lastMs = 0L; private var missSince = -1L
    private var still = 0
    private var active = false
    var result = Result(Phase.LOST, 0f, 0f, 0f, 0f, false, "sem seleção")
        private set

    /** [sideHint]: tamanho conhecido do assunto (ex.: rosto do sensor), relativo ao menor lado; 0 = padrão. */
    fun select(gray: ByteArray, w: Int, h: Int, nx: Float, ny: Float, nowMs: Long, sideHint: Float = 0f): Result {
        require(w >= 48 && h >= 48 && gray.size >= w * h)
        width = w; height = h
        side = if (sideHint > 0f) sideHint * min(w, h) else min(w, h) * 0.20f
        side = side.coerceIn(22f, max(22f, min(w, h) * 0.45f))
        x = nx.coerceIn(0f, 1f) * (w - 1); y = ny.coerceIn(0f, 1f) * (h - 1)
        shownX = x; shownY = y; vx = 0f; vy = 0f; still = 0
        lastMs = nowMs; missSince = -1; active = false
        if (!fits(x, y, side)) return lost("toque mais longe da borda")
        var sum = 0f
        for (i in template.indices) {
            val value = value(gray, x, y, side, i)
            template[i] = value; sum += value
        }
        val mean = sum / template.size
        var norm = 0f
        for (i in template.indices) { template[i] -= mean; norm += template[i] * template[i] }
        if (sqrt(norm / template.size) < 7f) return lost("pouca textura; toque num contorno")
        norm = sqrt(norm)
        for (i in template.indices) template[i] /= norm
        template.copyInto(anchor)
        active = true
        return output(Phase.TRACKING, 1f, true)
    }

    fun update(gray: ByteArray, w: Int, h: Int, nowMs: Long): Result {
        if (!active) return result
        if (w != width || h != height || gray.size < w * h) return lost("enquadramento alterado")
        if (nowMs <= lastMs) return result // nunca aplicar quadro atrasado.
        val dt = nowMs - lastMs
        if (dt > 1200) return lost("quadros não atuais")
        lastMs = nowMs
        val short = min(w, h).toFloat()
        val searching = missSince >= 0
        val radius = if (searching) min(short * WIDE_RADIUS, max(24f, side * 1.5f)) else min(short * 0.16f, max(12f, side * 0.7f))
        val step = if (searching) 4 else 3
        val px = (x + vx * dt.coerceAtMost(400)).coerceIn(side / 2, w - 1f - side / 2)
        val py = (y + vy * dt.coerceAtMost(400)).coerceIn(side / 2, h - 1f - side / 2)
        var best = -1f; var bx = x; var by = y; var bs = side
        var count = 0
        for (yy in floor(py - radius).toInt()..ceil(py + radius).toInt() step step)
            for (xx in floor(px - radius).toInt()..ceil(px + radius).toInt() step step) {
                val score = score(gray, xx.toFloat(), yy.toFloat(), side)
                if (count < peaksScore.size) {
                    peaksX[count] = xx.toFloat(); peaksY[count] = yy.toFloat(); peaksScore[count++] = score
                }
                if (score > best) { best = score; bx = xx.toFloat(); by = yy.toFloat() }
            }
        val cx = bx.roundToInt(); val cy = by.roundToInt()
        for (scale in scales) {
            val size = (side * scale).coerceIn(22f, maxOf(22f, short * 0.45f))
            for (yy in cy - 3..cy + 3) for (xx in cx - 3..cx + 3) {
                val score = score(gray, xx.toFloat(), yy.toFloat(), size)
                if (score > best) { best = score; bx = xx.toFloat(); by = yy.toFloat(); bs = size }
            }
        }
        var second = -1f
        for (i in 0 until count) if (hypot(peaksX[i] - bx, peaksY[i] - by) > side * 0.65f)
            second = max(second, peaksScore[i])
        if (best < 0.76f || best - second < 0.055f) {
            if (missSince < 0) missSince = nowMs
            vx = 0f; vy = 0f
            if (nowMs - missSince >= LOST_AFTER_MS) return lost("assunto perdido ou ambíguo")
            return output(Phase.UNCERTAIN, best.coerceIn(0f, 1f), false)
        }
        missSince = -1
        val distance = hypot(bx - x, by - y)
        vx = (bx - x) / dt * 0.5f + vx * 0.5f
        vy = (by - y) / dt * 0.5f + vy * 0.5f
        x = bx; y = by; side = bs
        if (best >= ADAPT_SCORE && best - second >= ADAPT_MARGIN) adapt(gray)
        shownX += (x - shownX) * 0.75f; shownY += (y - shownY) * 0.75f
        still = if (distance < short * 0.008f) still + 1 else 0
        return output(Phase.TRACKING, best, still < 3)
    }

    /** Mistura lenta com a leitura atual; recusada se o modelo se afastaria demais do que foi tocado. */
    private fun adapt(gray: ByteArray) {
        if (!fits(x, y, side)) return
        var sum = 0f
        for (i in patch.indices) { patch[i] = value(gray, x, y, side, i); sum += patch[i] }
        val mean = sum / patch.size
        var norm = 0f
        for (i in patch.indices) { patch[i] -= mean; norm += patch[i] * patch[i] }
        if (norm < patch.size * 49f) return
        norm = sqrt(norm)
        var mixed = 0f
        for (i in patch.indices) { patch[i] = template[i] * (1 - ADAPT_RATE) + patch[i] / norm * ADAPT_RATE; mixed += patch[i] * patch[i] }
        mixed = sqrt(mixed)
        var toAnchor = 0f
        for (i in patch.indices) { patch[i] /= mixed; toAnchor += patch[i] * anchor[i] }
        if (toAnchor < MIN_ANCHOR) return
        patch.copyInto(template)
    }

    private fun output(phase: Phase, confidence: Float, moving: Boolean): Result =
        Result(phase, shownX / (width - 1), shownY / (height - 1), side / min(width, height),
            confidence, moving).also { result = it }
    private fun lost(reason: String): Result {
        active = false
        return result.copy(phase = Phase.LOST, reason = reason, confidence = 0f).also { result = it }
    }
    private fun fits(cx: Float, cy: Float, s: Float) =
        cx - s / 2 >= 0 && cy - s / 2 >= 0 && cx + s / 2 < width - 1 && cy + s / 2 < height - 1
    private fun score(frame: ByteArray, cx: Float, cy: Float, s: Float): Float {
        if (!fits(cx, cy, s)) return -1f
        var sum = 0f; var squares = 0f; var dot = 0f
        for (i in template.indices) {
            val v = value(frame, cx, cy, s, i)
            sum += v; squares += v * v; dot += v * template[i]
        }
        val norm = squares - sum * sum / template.size
        if (norm < template.size * 25f) return -1f
        return (dot / sqrt(norm)).coerceIn(-1f, 1f)
    }
    private fun value(frame: ByteArray, cx: Float, cy: Float, s: Float, i: Int): Float {
        val fx = cx + ((i % n).toFloat() / (n - 1) - 0.5f) * s
        val fy = cy + ((i / n).toFloat() / (n - 1) - 0.5f) * s
        val ix = fx.toInt(); val iy = fy.toInt(); val a = fx - ix; val b = fy - iy
        val p = iy * width + ix
        val v0 = (frame[p].toInt() and 255) * (1 - a) + (frame[p + 1].toInt() and 255) * a
        val v1 = (frame[p + width].toInt() and 255) * (1 - a) + (frame[p + width + 1].toInt() and 255) * a
        return v0 * (1 - b) + v1 * b
    }
}

private const val WIDE_RADIUS = 0.32f
private const val LOST_AFTER_MS = 1000L
private const val ADAPT_SCORE = 0.9f
private const val ADAPT_MARGIN = 0.1f
private const val ADAPT_RATE = 0.12f
private const val MIN_ANCHOR = 0.6f

object TrackingBudget {
    /** Reduz apenas a frequência da análise, nunca FPS/resolução da gravação. */
    fun intervalMs(moving: Boolean, thermal: Int, powerSave: Boolean, copyMs: Double): Long =
        if (thermal >= 2 || powerSave || copyMs > 25) 500L else if (moving) 200L else 400L
    fun suspended(thermal: Int) = thermal >= 3
}
