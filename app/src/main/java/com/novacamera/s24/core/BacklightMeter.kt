package com.novacamera.s24.core

import com.novacamera.s24.core.imaging.Px
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Contraluz: o fotômetro da câmera mede a média do quadro e, com janela, céu ou lâmpada no
 * enquadramento, escurece todo o resto. Aqui, pela própria prévia exibida, o centro do quadro
 * (onde costuma estar o assunto) é levado de volta a um brilho de meio-tom com compensação de
 * exposição — como a contraluz automática das câmeras dos fabricantes. Sem área estourada não há
 * contraluz e nada muda.
 */
object BacklightMeter {
    /** Fração estourada para começar (2%) e para continuar compensando (0,5%). */
    const val ENTER_CLIPPED = 0.02f
    const val KEEP_CLIPPED = 0.005f
    /** Brilho desejado do centro (mediana da luminância, 0..1). */
    const val TARGET_CENTER = 0.30f
    const val MAX_EV = 1.5f
    /** Passo máximo por medição (sem saltos na prévia) e zona morta. */
    const val MAX_STEP_EV = 0.34f
    const val DEADBAND_EV = 0.2f

    data class Reading(val clipped: Float, val center: Float)

    /** [px] prévia reduzida (ARGB). Centro = 60% × 60% do quadro, sem os pixels estourados. */
    fun measure(px: IntArray, w: Int, h: Int): Reading {
        var clip = 0
        val hist = IntArray(256); var n = 0
        val x0 = w / 5; val x1 = w - w / 5; val y0 = h / 5; val y1 = h - h / 5
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            val r = Px.r(c); val g = Px.g(c); val b = Px.b(c)
            val clipped = max(r, max(g, b)) >= 245
            if (clipped) clip++
            if (!clipped && x in x0 until x1 && y in y0 until y1) { hist[(r * 77 + g * 150 + b * 29) shr 8]++; n++ }
        }
        var acc = 0; var median = 0
        for (i in 0 until 256) { acc += hist[i]; if (acc * 2 >= n) { median = i; break } }
        return Reading(if (px.isEmpty()) 0f else clip.toFloat() / px.size, if (n == 0) 1f else median / 255f)
    }

    /** Compensação desejada (EV ≥ 0) dada a atual [currentEv], já embutida na prévia medida. */
    fun target(currentEv: Float, r: Reading): Float {
        val backlit = if (currentEv > 0.01f) r.clipped >= KEEP_CLIPPED else r.clipped >= ENTER_CLIPPED
        if (!backlit) return 0f
        val need = (ln(TARGET_CENTER / max(r.center, 0.02f)) / ln(2f))
        return (currentEv + need).coerceIn(0f, MAX_EV)
    }

    /** Próximo valor: aproxima-se de [target] em passos limitados; ignora diferenças pequenas. */
    fun step(currentEv: Float, target: Float): Float {
        val d = target - currentEv
        if (abs(d) < DEADBAND_EV) return currentEv
        return currentEv + d.coerceIn(-MAX_STEP_EV, MAX_STEP_EV).let { if (d > 0) min(it, d) else max(it, d) }
    }
}
