package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Fusão temporal robusta (redução de ruído multiquadro com rejeição de movimento).
 *
 * Cada quadro alinhado contribui com peso que cai quando ele difere da referência além do
 * esperado pelo ruído: áreas paradas são promediadas (ruído cai ~1/√N); pessoas/objetos que
 * se moveram ficam com a referência, evitando "fantasmas". Nada é sintetizado: a saída é sempre
 * uma média ponderada de valores medidos.
 */
object Merge {
    /**
     * Desvio-padrão do ruído (0..255) estimado pela mediana das diferenças entre pixels vizinhos
     * nas regiões mais planas (estimador robusto: bordas reais não inflam o resultado).
     */
    fun estimateNoise(px: IntArray, w: Int, h: Int, step: Int = 3, correlated: Boolean = false): Float {
        val diffs = ArrayList<Float>()
        var y = 1
        while (y < h - 1) {
            var x = 1
            while (x < w - 2) {
                val c = px[y * w + x]
                val l = Px.luma(c) * 255f
                val r = Px.luma(px[y * w + x + 1]) * 255f
                val d = Px.luma(px[(y + 1) * w + x]) * 255f
                diffs += abs(l - r); diffs += abs(l - d)
                x += step
            }
            y += step
        }
        if (diffs.isEmpty()) return 1f
        val arr = diffs.toFloatArray().also { it.sort() }
        // 1º quartil das |diferenças| (regiões planas dominam). Para ruído gaussiano, a diferença
        // entre vizinhos tem desvio σ√2 e o 1º quartil do seu módulo vale 0,3186·σ√2.
        val q1 = arr[arr.size / 4]
        val base = max(0.5f, q1 / 0.3186f / sqrt(2f))
        return if(correlated) CorrelatedNoise.sigma(w,h,base,step) { x,y -> Px.luma(px[y*w+x])*255f } else base
    }

    /** Teto do ruído temporal (0..255): acima disso a diferença entre quadros é movimento, não ruído. */
    const val MAX_TEMPORAL_SIGMA = 8f

    /**
     * Ruído (0..255) medido entre a referência e quadros já alinhados: é a mesma diferença que a
     * fusão compara. O vizinho a vizinho de [estimateNoise] não vê ruído em manchas de vários pixels
     * (JPEG da tele alisado pelo ISP) e caía no piso, rejeitando a cena parada como movimento.
     * 1º quartil: robusto a até ~75% de área em movimento. 0 = sem amostras suficientes.
     */
    fun temporalNoise(ref: IntArray, others: List<IntArray>, step: Int = 2): Float {
        val d = ArrayList<Float>()
        for (o in others) {
            var i = 0
            while (i < ref.size) {
                val r = Px.luma(ref[i]) * 255f; val m = Px.luma(o[i]) * 255f
                if (r in 5f..250f && m in 3f..252f) d += abs(r - m)
                i += step
            }
        }
        if (d.size < 256) return 0f
        val arr = d.toFloatArray().also { it.sort() }
        return min(MAX_TEMPORAL_SIGMA, arr[arr.size / 4] / 0.3186f / sqrt(2f))
    }

    class Result(val pixels: IntArray, val rejectedFraction: Float)

    /**
     * Funde uma região: [ref] é a referência; [others] já recortados com o deslocamento de alinhamento.
     * [sigma] em unidades 0..255; [k] controla a tolerância (múltiplos de σ).
     */
    /**
     * Pixel isolado da referência (ponto de ruído/nitidez do ISP) que os outros quadros desmentem.
     * Ancorar na referência mantinha esses pontos com força total: os outros quadros, por discordarem,
     * eram rejeitados como "movimento". Só vale para ponto isolado na própria referência (objeto que se
     * mexeu ocupa área e concorda com os vizinhos) e quando os outros concordam entre si e com a
     * vizinhança. Devolve o índice (em [others]) do quadro que vira âncora, ou -1.
     */
    private fun impulseAnchor(i: Int, w: Int, h: Int, refLuma: FloatArray, others: List<IntArray>,
                              frameWeights: List<FloatArray?>?, lo: Float, hi: Float, nb: FloatArray, ob: FloatArray, oi: IntArray): Int {
        val x = i % w; val y = i / w
        if (x == 0 || y == 0 || x == w - 1 || y == h - 1) return -1
        val r = refLuma[i]
        // Filtro barato antes de ordenar: o ponto passa do 2º maior (ou fica abaixo do 2º menor) vizinho
        // por mais de kσ — admite ponto de 1–2 px; borda e textura não passam.
        var max1 = -1f; var max2 = -1f; var min1 = 999f; var min2 = 999f
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val v = refLuma[i + dy * w + dx]
            if (v > max1) { max2 = max1; max1 = v } else if (v > max2) max2 = v
            if (v < min1) { min2 = min1; min1 = v } else if (v < min2) min2 = v
        }
        if (r - max2 <= hi && min2 - r <= hi) return -1
        var n = 0
        for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0) nb[n++] = refLuma[i + dy * w + dx]
        java.util.Arrays.sort(nb, 0, 8)
        val neighborhood = (nb[3] + nb[4]) * 0.5f
        var m = 0
        for ((k, o) in others.withIndex()) {
            if ((frameWeights?.get(k)?.get(i) ?: 1f) < 0.5f) continue
            ob[m] = Px.luma(o[i]) * 255f; oi[m] = k; m++
        }
        if (m < 2) return -1
        val sorted = ob.copyOf(m).also { it.sort() }
        val med = if (m % 2 == 1) sorted[m / 2] else (sorted[m / 2 - 1] + sorted[m / 2]) * 0.5f
        var best = -1; var bestD = Float.MAX_VALUE
        for (j in 0 until m) {
            val d = abs(ob[j] - med)
            if (d > hi) return -1
            if (d < bestD) { bestD = d; best = oi[j] }
        }
        if (abs(r - med) <= hi || abs(med - neighborhood) > hi) return -1
        return best
    }

    fun mergeTile(ref: IntArray, others: List<IntArray>, sigma: Float, k: Float = 4.5f, frameWeights: List<FloatArray?>? = null, width: Int = 0): Result {
        val out = IntArray(ref.size)
        // Peso total até 2σ (diferença típica entre dois quadros só com ruído); cai suavemente até kσ.
        val lo = 2f * sigma
        val hi = k * sigma
        val lo2 = lo * lo; val hi2 = hi * hi
        var rejected = 0L; var total = 0L
        val impulses = width > 2 && ref.size % width == 0 && ref.size / width > 2 && others.size >= 2
        val refLuma = if (impulses) FloatArray(ref.size) { Px.luma(ref[it]) * 255f } else null
        val nb = FloatArray(8); val ob = FloatArray(others.size); val idx = IntArray(others.size)
        for (i in ref.indices) {
            val anchor = if (refLuma != null) impulseAnchor(i, width, ref.size / width, refLuma, others, frameWeights, lo, hi, nb, ob, idx) else -1
            val c = if (anchor >= 0) others[anchor][i] else ref[i]
            val rr = Px.r(c); val rg = Px.g(c); val rb = Px.b(c)
            var sr = rr.toFloat(); var sg = rg.toFloat(); var sb = rb.toFloat(); var sw = 1f
            // Com âncora em outro quadro, a referência vira uma amostra comum (o ponto dela cai pela distância).
            for (oi in -1 until others.size) {
                if (oi == anchor || (oi == -1 && anchor < 0)) continue
                val q = if (oi == -1) ref[i] else others[oi][i]
                val fw = if (oi == -1) 1f else frameWeights?.get(oi)?.get(i) ?: 1f
                val dr = (Px.r(q) - rr).toFloat(); val dg = (Px.g(q) - rg).toFloat(); val db = (Px.b(q) - rb).toFloat()
                // Distância de cor dominada pela luminância; cor também conta para não misturar objetos diferentes.
                val d2 = (0.2126f * dr + 0.7152f * dg + 0.0722f * db).let { it * it } * 0.7f + (dr * dr + dg * dg + db * db) / 3f * 0.3f
                val w = fw * when {
                    d2 <= lo2 -> 1f
                    d2 >= hi2 -> 0f
                    else -> { val t = (sqrt(d2) - lo) / (hi - lo); 1f - t * t * (3f - 2f * t) }
                }
                total++
                if (w < 0.05f) rejected++
                sr += w * Px.r(q); sg += w * Px.g(q); sb += w * Px.b(q); sw += w
            }
            out[i] = Px.argb((sr / sw + 0.5f).toInt(), (sg / sw + 0.5f).toInt(), (sb / sw + 0.5f).toInt())
        }
        return Result(out, if (total == 0L) 0f else rejected.toFloat() / total)
    }
}
