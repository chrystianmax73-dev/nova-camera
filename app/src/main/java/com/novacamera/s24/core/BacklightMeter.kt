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
 * contraluz e nada muda. Com rosto na prévia (ou objeto tocado), a medida é o rosto contra o resto da cena.
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

    /**
     * [subject]/[rest]: mediana do rosto (ou objeto tocado) e do resto da cena, quando há assunto. A selfie em
     * contraluz de 20/10 (rosto 84, fundo 167, só 0,24% estourado) não entrava pela regra do estouro: o rosto
     * escuro contra o fundo claro agora também é contraluz.
     */
    data class Reading(val clipped: Float, val center: Float, val subject: Float? = null, val rest: Float = 0f)

    /** Rosto: meio-tom um pouco acima do centro genérico (pele lida bem ~0,40–0,45). */
    const val TARGET_SUBJECT = 0.42f
    /** Assunto mais escuro que o resto por pelo menos isso (entrar) / isso (continuar). */
    const val ENTER_GAP = 0.16f
    const val KEEP_GAP = 0.08f
    /** Estouro acima disso: não sobe mais (o fundo já está sacrificado o bastante). */
    const val MAX_CLIPPED = 0.25f

    private fun median(hist: IntArray, n: Int): Float {
        if (n == 0) return 1f
        var acc = 0
        for (i in 0 until 256) { acc += hist[i]; if (acc * 2 >= n) return i / 255f }
        return 1f
    }

    /**
     * [px] prévia reduzida (ARGB). Centro = 60% × 60% do quadro, sem os pixels estourados. [subject]: caixa do
     * rosto/objeto em frações do quadro (l, t, r, b), ou null.
     */
    fun measure(px: IntArray, w: Int, h: Int, subject: FloatArray? = null): Reading {
        var clip = 0
        val hist = IntArray(256); var n = 0
        val sHist = IntArray(256); var sn = 0
        val oHist = IntArray(256); var on = 0
        val x0 = w / 5; val x1 = w - w / 5; val y0 = h / 5; val y1 = h - h / 5
        val sx0 = subject?.let { (it[0] * w).toInt() } ?: 0; val sx1 = subject?.let { (it[2] * w).toInt() } ?: -1
        val sy0 = subject?.let { (it[1] * h).toInt() } ?: 0; val sy1 = subject?.let { (it[3] * h).toInt() } ?: -1
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            val r = Px.r(c); val g = Px.g(c); val b = Px.b(c)
            val l = (r * 77 + g * 150 + b * 29) shr 8
            val clipped = max(r, max(g, b)) >= 245
            if (clipped) clip++
            if (!clipped && x in x0 until x1 && y in y0 until y1) { hist[l]++; n++ }
            if (subject != null) { if (x in sx0..sx1 && y in sy0..sy1) { sHist[l]++; sn++ } else { oHist[l]++; on++ } }
        }
        return Reading(if (px.isEmpty()) 0f else clip.toFloat() / px.size, median(hist, n),
            if (subject != null && sn >= 4) median(sHist, sn) else null, median(oHist, on))
    }

    /** Compensação desejada (EV ≥ 0) dada a atual [currentEv], já embutida na prévia medida. */
    fun target(currentEv: Float, r: Reading): Float {
        val s = r.subject
        if (s != null) {
            val gap = r.rest - s
            val backlit = s < TARGET_SUBJECT && gap >= (if (currentEv > 0.01f) KEEP_GAP else ENTER_GAP)
            if (backlit) {
                // EV é luz linear: a razão em valores da prévia (gama ~2,2) vira 2,2× em EV.
                val need = 2.2f * ln(TARGET_SUBJECT / max(s, 0.02f)) / ln(2f)
                val wanted = (currentEv + need).coerceIn(0f, MAX_EV)
                return if (r.clipped >= MAX_CLIPPED) min(wanted, currentEv) else wanted
            }
            if (currentEv > 0.01f && s >= TARGET_SUBJECT) return currentEv // rosto no ponto: segura, sem oscilar
        }
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
