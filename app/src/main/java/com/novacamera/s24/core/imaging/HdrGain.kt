package com.novacamera.s24.core.imaging

import java.util.Locale
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Ultra HDR a partir das exposições do HDR Nova. A foto final continua SDR; o gainmap diz quanto
 * mais claro cada ponto pode ficar numa tela HDR. Esse brilho vem do quadro mais escuro do bracket,
 * que registrou o realce que estourou na referência: é medido na cena, não inventado.
 *
 * Valores de cena em luz linear, relativos ao branco SDR da referência (1 = branco da foto comum).
 * A curva de tom da câmera é aproximada pela sRGB; o ganho de cada quadro é medido nos meios-tons
 * (não depende do EV pedido nem do EV que a câmera realmente aplicou).
 */
object HdrGain {
    /** Até 2 stops acima do branco SDR. */
    const val MAX_RATIO = 4f
    /** Abaixo disso o quadro escuro não acrescenta faixa útil. */
    const val MIN_GAIN = 1.4f
    private const val MIN_SAMPLES = 200

    @Volatile var lastReport = "Ultra HDR: nenhuma foto HDR processada ainda"

    fun lin(v: Float): Float {
        val c = v.coerceIn(0f, 1f)
        return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** Ganho linear (referência ÷ quadro) medido onde os dois estão bem expostos; null sem amostras. */
    fun exposureGain(ref: Plane, frame: Plane): Float? {
        val ratios = ArrayList<Float>()
        val n = min(ref.data.size, frame.data.size)
        for (i in 0 until n) {
            val r = ref.data[i]; val f = frame.data[i]
            if (r in 0.12f..0.80f && f in 0.04f..0.80f) ratios += lin(r) / max(lin(f), 1e-4f)
        }
        if (ratios.size < MIN_SAMPLES) return null
        ratios.sort()
        return ratios[ratios.size / 2]
    }

    /**
     * Luminância da cena no plano da referência: onde a referência se aproxima do branco, entra o
     * quadro mais escuro escalado pelo ganho medido — só para **aumentar** (máximo), então um objeto
     * que se moveu nunca escurece a cena. Null quando não há faixa acima do branco SDR.
     */
    fun scene(lumas: List<Plane>): Plane? {
        if (lumas.size < 2) return null
        val ref = lumas[0]
        var best = -1; var gain = 0f
        for (k in 1 until lumas.size) {
            val g = exposureGain(ref, lumas[k]) ?: continue
            if (g > gain) { gain = g; best = k }
        }
        if (best < 0 || gain < MIN_GAIN) return null
        val d = lumas[best]
        val out = Plane(ref.w, ref.h)
        var peak = 0f
        for (i in out.data.indices) {
            val r = lin(ref.data[i])
            val t = smoothstep(0.80f, 0.95f, ref.data[i])
            val v = lin(d.data[i]) * gain
            val s = r + (max(r, v) - r) * t
            out.data[i] = s
            if (s > peak) peak = s
        }
        return if (peak > 1.05f) out else null
    }

    /**
     * Razão HDR ÷ SDR para o gainmap. [sdr] é a luminância (0..1, sRGB) da foto final no mesmo
     * plano e orientação de [scene]. Só os pontos de cena além do branco recebem ganho; meios-tons
     * e sombras ficam em 1 (o acabamento SDR não é desfeito).
     */
    fun ratio(scene: Plane, sdr: Plane): Plane {
        val s = if (sdr.w == scene.w && sdr.h == scene.h) sdr else Planes.resize(sdr, scene.w, scene.h)
        return Plane(scene.w, scene.h, FloatArray(scene.data.size) { i ->
            val sc = scene.data[i]
            val r = (sc / max(lin(s.data[i]), 0.02f)).coerceIn(1f, MAX_RATIO)
            1f + (r - 1f) * smoothstep(0.80f, 1.20f, sc)
        })
    }

    /** Maior razão presente (define a faixa do gainmap); 1 = nada a ganhar. */
    fun peak(ratio: Plane): Float = ratio.data.maxOrNull() ?: 1f

    /** Codifica a razão em 8 bits, logarítmica entre 1 e [maxRatio] (gamma 1). */
    fun encode(ratio: Plane, maxRatio: Float): ByteArray {
        val l = ln(maxRatio)
        return ByteArray(ratio.data.size) { i ->
            ((ln(ratio.data[i].coerceIn(1f, maxRatio)) / l) * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
    }

    fun describe(peak: Float, gain: Float?) =
        String.format(Locale.US, "Ultra HDR: gainmap gerado na última foto HDR, até %.1f× o branco SDR", peak) +
            (gain?.let { String.format(Locale.US, " (quadro escuro %.1f× mais escuro)", it) } ?: "")
}

/** Rotação/espelho de um plano, na mesma ordem de Engine.orient (girar no sentido horário, depois espelhar). */
fun Plane.oriented(rotationDegrees: Int, mirror: Boolean): Plane {
    val r = ((rotationDegrees % 360) + 360) % 360
    if (r == 0 && !mirror) return this
    val ow = if (r % 180 == 0) w else h
    val oh = if (r % 180 == 0) h else w
    val out = Plane(ow, oh)
    for (y in 0 until h) for (x in 0 until w) {
        var nx: Int; var ny: Int
        when (r) {
            90 -> { nx = h - 1 - y; ny = x }
            180 -> { nx = w - 1 - x; ny = h - 1 - y }
            270 -> { nx = y; ny = w - 1 - x }
            else -> { nx = x; ny = y }
        }
        if (mirror) nx = ow - 1 - nx
        out.data[ny * ow + nx] = data[y * w + x]
    }
    return out
}
