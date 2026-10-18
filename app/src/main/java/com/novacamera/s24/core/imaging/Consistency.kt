package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Curva que leva a luminância de um quadro (outra exposição, outro ponto da curva de tom do
 * JPEG) para a escala da referência. Obtida por casamento de histogramas acumulados nos pixels
 * bem medidos dos dois quadros — monotônica por construção e insensível a pequenos deslocamentos.
 * Um ganho único (0.6) não serve: o JPEG já passou por curva de tom, então −2 EV não é ×0,25 em
 * todos os tons, e a diferença virava "movimento" falso ou escondia movimento real.
 */
class ExposureLut(private val table: FloatArray) {
    fun map(v: Float): Float {
        val f = (v.coerceIn(0f, 1f) * (table.size - 1))
        val i = min(table.size - 2, f.toInt())
        val t = f - i
        return table[i] + (table[i + 1] - table[i]) * t
    }

    /** Inversa: luminância na escala da referência → luminância esperada neste quadro. */
    fun inverse(v: Float): Float {
        val t = v.coerceIn(0f, 1f)
        var lo = 0; var hi = table.size - 1
        while (hi - lo > 1) { val mid = (lo + hi) / 2; if (table[mid] < t) lo = mid else hi = mid }
        val a = table[lo]; val b = table[hi]
        val f = if (b > a) ((t - a) / (b - a)).coerceIn(0f, 1f) else 0f
        return (lo + f) / (table.size - 1)
    }

    /** Derivada local (quanto um passo de luminância no quadro vale na referência). */
    fun slope(v: Float): Float {
        val e = 1f / 64f
        return (map(v + e) - map(v - e)) / (2 * e)
    }

    companion object {
        val IDENTITY = ExposureLut(FloatArray(257) { it / 256f })

        fun match(ref: Plane, mov: Plane): ExposureLut {
            val bins = 256
            val hr = DoubleArray(bins); val hm = DoubleArray(bins)
            var n = 0
            for (i in ref.data.indices) {
                val a = ref.data[i]; val b = mov.data[i]
                // Só onde nenhum dos dois está estourado ou no preto (lá não há correspondência).
                if (a in 0.01f..0.985f && b in 0.01f..0.985f) {
                    hr[(a * (bins - 1)).toInt()]++; hm[(b * (bins - 1)).toInt()]++; n++
                }
            }
            if (n < 256) return IDENTITY
            val cr = DoubleArray(bins); val cm = DoubleArray(bins)
            var sr = 0.0; var sm = 0.0
            for (i in 0 until bins) { sr += hr[i]; sm += hm[i]; cr[i] = sr / n; cm[i] = sm / n }
            val table = FloatArray(257)
            var j = 0
            for (i in 0..256) {
                val v = i / 256f
                val q = cm[min(bins - 1, (v * (bins - 1)).toInt())]
                while (j < bins - 1 && cr[j] < q) j++
                table[i] = j / (bins - 1f)
            }
            // Monotônica e sem degraus bruscos.
            for (i in 1..256) table[i] = max(table[i], table[i - 1])
            val smooth = FloatArray(257) { i ->
                var s = 0f; var c = 0
                for (k in max(0, i - 3)..min(256, i + 3)) { s += table[k]; c++ }
                s / c
            }
            return ExposureLut(smooth)
        }
    }
}

/**
 * Verificação de consistência entre um quadro alinhado e a referência. É o que impede fantasmas:
 * nenhum pixel de outro quadro entra na foto se não "bate" com a referência depois de alinhado e
 * levado à mesma exposição. Na dúvida, fica a referência.
 */
object Consistency {
    /** Tolerância em luminância (0..1) para considerar dois valores o mesmo ponto da cena. */
    fun tolerance(refLuma: Float, noise: Float) = 0.03f + 0.08f * refLuma + 2.5f * noise

    /**
     * Mapa de confiabilidade (0..1) do quadro [mov] na resolução das luminâncias de alinhamento.
     * [field] é o campo de deslocamentos (grade gx×gy, em pixels dessa resolução).
     * Onde a referência está estourada ou preta não há como verificar: herda a confiança da vizinhança.
     */
    fun reliability(
        ref: Plane, mov: Plane, field: Array<Shift>, gx: Int, gy: Int, lut: ExposureLut, noise: Float,
    ): Plane {
        val w = ref.w; val h = ref.h
        val ok = Plane(w, h); val valid = Plane(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val s = Align.shiftAt(field, gx, gy, w, h, x.toFloat(), y.toFloat(), 1f)
            val mx = x + s.dx; val my = y + s.dy
            if (mx !in 0 until w || my !in 0 until h) continue
            val r = ref[x, y]; val m = mov[mx, my]
            val i = y * w + x
            if (r !in 0.02f..0.97f || m !in 0.01f..0.985f) continue
            valid.data[i] = 1f
            val pred = lut.map(m)
            // A tolerância cresce onde a curva do quadro é achatada (pouca informação por passo).
            val tol = tolerance(r, noise) * max(1f, lut.slope(m) * 0.5f)
            ok.data[i] = if (abs(pred - r) <= tol) 1f else 0f
        }
        // Rejeições se expandem 2 px (borda do objeto que mudou) e o mapa é suavizado.
        val bad = Plane(w, h)
        for (i in bad.data.indices) bad.data[i] = if (valid.data[i] > 0f && ok.data[i] == 0f) 1f else 0f
        val badWide = Planes.boxBlur(bad, 2)
        for (i in ok.data.indices) if (badWide.data[i] > 0.04f) ok.data[i] = 0f
        // Regiões grandes sem verificação (janela estourada): vale a confiança do quadro inteiro.
        var nValid = 0; var nOk = 0
        for (i in ok.data.indices) if (valid.data[i] > 0f) { nValid++; if (ok.data[i] > 0f) nOk++ }
        val frameOk = if (nValid == 0) 0f else nOk.toFloat() / nValid
        val deep = if (frameOk > 0.9f) 1f else 0f
        // Bordas dessas regiões: confiança média ao redor.
        val num = Planes.boxBlur(Plane(w, h, FloatArray(w * h) { ok.data[it] * valid.data[it] }), 6)
        val den = Planes.boxBlur(valid, 6)
        val out = Plane(w, h)
        for (i in out.data.indices) {
            out.data[i] = when {
                valid.data[i] > 0f -> ok.data[i]
                den.data[i] > 0.05f -> (num.data[i] / den.data[i]).let { if (it > 0.9f) it else 0f }
                else -> deep
            }
        }
        return Planes.boxBlur(out, 1).also { p ->
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val s = Align.shiftAt(field, gx, gy, w, h, x.toFloat(), y.toFloat(), 1f)
                // A suavização e a confiança global não podem validar amostras fora do sensor.
                if (x + s.dx !in 0 until w || y + s.dy !in 0 until h || p.data[i] < 0.6f) p.data[i] = 0f
            }
        }
    }

    /** Desvio do ruído em luminância (0..1), robusto a bordas (1º quartil das diferenças vizinhas). */
    fun noiseOf(p: Plane): Float {
        val d = ArrayList<Float>()
        var y = 1
        while (y < p.h - 1) {
            var x = 1
            while (x < p.w - 1) { d += abs(p[x, y] - p[x + 1, y]); x += 3 }
            y += 3
        }
        if (d.isEmpty()) return 0.005f
        val arr = d.toFloatArray().also { it.sort() }
        return max(0.002f, arr[arr.size / 4] / 0.3186f / 1.4142f)
    }

    /** Fração da área verificável em que o quadro não pode ser usado. */
    fun unsafeFraction(rel: Plane, ref: Plane): Float {
        var bad = 0; var n = 0
        for (i in rel.data.indices) {
            if (ref.data[i] !in 0.02f..0.97f) continue
            n++; if (rel.data[i] < 0.5f) bad++
        }
        return if (n == 0) 1f else bad.toFloat() / n
    }

    /**
     * O que o quadro deveria mostrar neste pixel se nada tivesse se movido: a referência levada à
     * exposição do quadro (mesma cor, outra luminância). Usado no lugar de pixels rejeitados para
     * que a fusão não fique mais clara/escura no formato do objeto que se mexeu.
     */
    fun predicted(refPx: Int, lut: ExposureLut): Int {
        val r = Px.luma(refPx)
        if (r < 1e-3f) return refPx
        val g = lut.inverse(r) / r
        return Px.argb((Px.r(refPx) * g + 0.5f).toInt(), (Px.g(refPx) * g + 0.5f).toInt(), (Px.b(refPx) * g + 0.5f).toInt())
    }

    /**
     * Referência estourada (ou preta): ela só diz que a cena está acima do branco (ou abaixo do
     * preto) ali. O quadro de outra exposição é a única medida real — concorda se o que ele prevê na
     * exposição da referência também está no limite (com tolerância de ruído). Null quando a
     * referência está medida normalmente (vale [agreement]).
     *
     * Sem isso, grades e fios brancos estourados (mais finos que o mapa de confiança em meia
     * resolução) recebiam a previsão a partir da própria referência estourada: branco chapado com
     * degraus no formato da grade do mapa (planta 3× HDR, 0.14.5).
     */
    fun clippedAgreement(refPx: Int, movPx: Int, lut: ExposureLut, noise: Float): Float? {
        val r = Px.luma(refPx); val m = Px.luma(movPx)
        if (r in 0.02f..0.97f) return null
        // O quadro também saturou/zerou (ou é a própria referência copiada fora do sensor): não informa.
        if (m !in 0.01f..0.985f) return null
        val pred = lut.map(m)
        val tol = tolerance(min(r, 0.97f), noise) * max(1f, lut.slope(m) * 0.5f) * 1.5f
        val miss = if (r > 0.97f) 0.97f - pred else pred - 0.02f
        return ((2f * tol - miss) / tol).coerceIn(0f, 1f)
    }

    /** Concordância por pixel em resolução cheia (0..1), para o que a meia resolução não vê. */
    fun agreement(refPx: Int, movPx: Int, lut: ExposureLut, noise: Float): Float {
        val r = Px.luma(refPx); val m = Px.luma(movPx)
        // Realces/sombras dependem da confiança da vizinhança, calculada em reliability.
        if (r !in 0.02f..0.97f || m !in 0.01f..0.985f) return 1f
        val d = abs(lut.map(m) - r)
        val tol = tolerance(r, noise) * max(1f, lut.slope(m) * 0.5f) * 1.5f
        val lumaAgreement = ((2f * tol - d) / tol).coerceIn(0f, 1f)
        // Crominância relativa à luminância: objetos de mesma luminosidade e cores diferentes
        // não são o mesmo ponto da cena. Tolerância maior no escuro, onde a cor é ruidosa.
        val chromaTolerance = 0.10f + 3f * noise / max(0.08f, min(r, m))
        val dr = abs(Px.r(refPx) / (255f * r) - Px.r(movPx) / (255f * m))
        val db = abs(Px.b(refPx) / (255f * r) - Px.b(movPx) / (255f * m))
        val chromaAgreement = ((2f * chromaTolerance - max(dr, db)) / chromaTolerance).coerceIn(0f, 1f)
        return min(lumaAgreement, chromaAgreement)
    }
}

