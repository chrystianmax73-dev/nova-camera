package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Auxílios de visor das câmeras profissionais, medidos na própria prévia exibida (sem fluxo extra da
 * câmera — fluxo extra já travou a prévia no S24 Ultra):
 * - histograma de luminância;
 * - alerta de estouro: onde algum canal já está no limite (detalhe perdido);
 * - realce de foco: bordas mais fortes da cena, para conferir o foco manual.
 */
object ViewfinderAid {
    const val BINS = 64
    /** Canal a partir daqui conta como estourado (JPEG da prévia, 0..255). */
    const val CLIP = 250
    /** Borda de realce: as mais fortes da cena (percentil) e acima de um mínimo absoluto. */
    private const val PEAK_PERCENTILE = 0.95f
    private const val PEAK_MIN = 48

    class Result(
        val w: Int, val h: Int,
        /** Contagem por faixa de luminância, [BINS] faixas. */
        val histogram: IntArray,
        val clipped: BooleanArray,
        val edges: BooleanArray,
        /** Fração do quadro estourada. */
        val clippedFraction: Float,
    )

    fun analyze(px: IntArray, w: Int, h: Int, wantEdges: Boolean): Result {
        val n = w * h
        val hist = IntArray(BINS)
        val clipped = BooleanArray(n)
        val y = IntArray(n)
        var nClip = 0
        for (i in 0 until n) {
            val c = px[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val l = (r * 77 + g * 150 + b * 29) shr 8
            y[i] = l
            hist[l * BINS / 256]++
            if (r >= CLIP || g >= CLIP || b >= CLIP) { clipped[i] = true; nClip++ }
        }
        val edges = BooleanArray(n)
        if (wantEdges && w >= 3 && h >= 3) {
            val mag = IntArray(n)
            val mh = IntArray(1024)
            for (yy in 1 until h - 1) for (x in 1 until w - 1) {
                val i = yy * w + x
                val gx = (y[i - w + 1] + 2 * y[i + 1] + y[i + w + 1]) - (y[i - w - 1] + 2 * y[i - 1] + y[i + w - 1])
                val gy = (y[i + w - 1] + 2 * y[i + w] + y[i + w + 1]) - (y[i - w - 1] + 2 * y[i - w] + y[i - w + 1])
                val m = (abs(gx) + abs(gy)) / 2
                mag[i] = m; mh[m.coerceAtMost(1023)]++
            }
            val inner = (w - 2) * (h - 2)
            var acc = 0; var thr = 1023
            for (k in 0 until 1024) { acc += mh[k]; if (acc >= inner * PEAK_PERCENTILE) { thr = k; break } }
            val t = max(thr, PEAK_MIN)
            for (i in 0 until n) edges[i] = mag[i] > t
        }
        return Result(w, h, hist, clipped, edges, if (n == 0) 0f else nClip.toFloat() / n)
    }

    /**
     * Ângulo do horizonte real na tela (graus; para `canvas.rotate`, sentido horário positivo) a
     * partir da gravidade no referencial do aparelho (sensor de gravidade). A tela do app não gira
     * (só os ícones), então vale para retrato e paisagem. Null quando o aparelho aponta para o chão
     * ou para o céu — aí não há horizonte.
     */
    fun horizon(gx: Float, gy: Float, gz: Float): Float? {
        val norm = sqrt(gx * gx + gy * gy + gz * gz)
        if (norm < 1f) return null
        if (abs(gz) / norm > 0.85f) return null
        return Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
    }

    /** Desvio (graus, sem sinal) até o nível mais próximo: retrato, paisagem ou de cabeça para baixo. */
    fun levelError(horizon: Float): Float {
        val m = ((horizon % 90f) + 90f) % 90f
        return if (m > 45f) 90f - m else m
    }
}
