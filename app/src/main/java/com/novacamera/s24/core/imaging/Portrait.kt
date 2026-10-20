package com.novacamera.s24.core.imaging

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Retrato próprio: a pessoa (máscara de segmentação refinada) fica intacta — sem suavizar pele,
 * barba ou cabelo — e só o fundo recebe desfoque, mais forte quanto mais longe do contorno
 * (aproximação de profundidade sem sensor de profundidade).
 */
object Portrait {
    /**
     * Refina a máscara grosseira da segmentação com filtro guiado na luminância da foto:
     * a transição acompanha bordas reais (fios de cabelo, barba, armação de óculos).
     */
    fun refineMatte(coarse: Plane, luma: Plane): Plane {
        val m = if (coarse.w != luma.w || coarse.h != luma.h) Planes.resize(coarse, luma.w, luma.h) else coarse
        val r = max(2, min(luma.w, luma.h) / 90)
        val refined = Planes.guidedFilter(luma, m, r, 1e-3f)
        for (i in refined.data.indices) refined.data[i] = refined.data[i].coerceIn(0f, 1f)
        return refined
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t)
    }

    /**
     * Máscara final do retrato, em cor e em duas passadas: a máscara grosseira da segmentação
     * (256 px no modelo) é ajustada às bordas reais pela cor da foto ([low] = RGB reduzido para os
     * coeficientes, [full] = RGB na resolução da máscara). Na segunda passada o raio cai à metade
     * para fechar a borda. Fora do cabelo a transição é curta (mão, braço, objetos: borda firme,
     * sem franja de fundo nítido); só no cabelo ([hair], confiança do modelo) ela pende para a
     * pessoa, onde um fio de fundo nítido incomoda menos que um fio de cabelo desfocado.
     */
    fun refineMatteColor(coarse: Plane, low: Array<Plane>, full: Array<Plane>, hair: Plane?): Plane {
        val lw = low[0].w; val lh = low[0].h
        val m0 = if (coarse.w != lw || coarse.h != lh) Planes.resize(coarse, lw, lh) else coarse
        val r1 = max(2, min(lw, lh) / 60)
        val pass1 = Planes.guidedFilterColor(low, m0, r1, 4e-4f, low)
        val m1 = Plane(lw, lh, FloatArray(lw * lh) { smooth(0.25f, 0.75f, pass1.data[it]) })
        val q = Planes.guidedFilterColor(low, m1, max(1, r1 / 2), 2e-4f, full)
        val fw = full[0].w; val fh = full[0].h
        val hairW = hair?.let { hp ->
            val hb = Planes.boxBlur(Planes.resize(hp, lw, lh), max(1, r1 / 2))
            Plane(lw, lh, FloatArray(lw * lh) { smooth(0.15f, 0.45f, hb.data[it]) })
        }
        val out = Plane(fw, fh)
        val sx = lw.toFloat() / fw; val sy = lh.toFloat() / fh
        for (y in 0 until fh) for (x in 0 until fw) {
            val i = y * fw + x
            val a = q.data[i].coerceIn(0f, 1f)
            // Borda firme um pouco para fora: sem o fio claro do fundo desfocado na beira de folhas e dedos.
            val firm = smooth(0.25f, 0.55f, a)
            val hw = hairW?.sample((x + 0.5f) * sx - 0.5f, (y + 0.5f) * sy - 0.5f) ?: 0f
            out.data[i] = if (hw <= 0f) firm else firm + (smooth(0.08f, 0.55f, a) - firm) * hw
        }
        return out
    }

    /**
     * Distância normalizada (0 no contorno → 1 longe) de cada ponto do fundo até a pessoa,
     * usada para o desfoque gradual. Transformada de distância em duas passadas (chanfro).
     */
    fun backgroundDistance(matte: Plane): Plane {
        val w = matte.w; val h = matte.h
        val inf = (w + h).toFloat()
        val d = Plane(w, h)
        for (i in d.data.indices) d.data[i] = if (matte.data[i] >= 0.5f) 0f else inf
        for (y in 0 until h) for (x in 0 until w) {
            var v = d[x, y]
            if (x > 0) v = min(v, d[x - 1, y] + 1f)
            if (y > 0) v = min(v, d[x, y - 1] + 1f)
            if (x > 0 && y > 0) v = min(v, d[x - 1, y - 1] + 1.414f)
            if (x < w - 1 && y > 0) v = min(v, d[x + 1, y - 1] + 1.414f)
            d[x, y] = v
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            var v = d[x, y]
            if (x < w - 1) v = min(v, d[x + 1, y] + 1f)
            if (y < h - 1) v = min(v, d[x, y + 1] + 1f)
            if (x < w - 1 && y < h - 1) v = min(v, d[x + 1, y + 1] + 1.414f)
            if (x > 0 && y < h - 1) v = min(v, d[x - 1, y + 1] + 1.414f)
            d[x, y] = v
        }
        val norm = max(1f, sqrt((w * w + h * h).toFloat()) * 0.35f)
        for (i in d.data.indices) d.data[i] = (d.data[i] / norm).coerceIn(0f, 1f)
        return d
    }

    /**
     * Desfoque do fundo em resolução reduzida ([small] = imagem reduzida, ARGB). O fundo é
     * pré-multiplicado por (1−α) para que a cor da pessoa não "vaze" para o desfoque (sem halo).
     * Realces recebem leve ênfase antes do desfoque (bolhas de luz como numa lente real).
     */
    fun blurBackground(small: IntArray, w: Int, h: Int, matte: Plane, radius: Int): IntArray {
        if (radius <= 0) return small.copyOf()
        val gamma = 2.2f
        val chans = Array(3) { Plane(w, h) }
        val weight = Plane(w, h)
        // Máscara alargada ~3 px (da imagem reduzida) só para o que entra no desfoque: a borda do
        // assunto, onde a máscara fica abaixo de 1, não espalha luz no fundo escuro (brilho em volta
        // dos AirPods brancos no Retrato). A composição continua com a máscara original.
        val grown = DepthPortrait.maxFilter(matte, max(1, (3f * matte.w / w + 0.5f).toInt()))
        for (i in 0 until w * h) {
            val a = 1f - grown.sample(i % w * matte.w.toFloat() / w, i / w * matte.h.toFloat() / h)
            val c = small[i]
            chans[0].data[i] = (Px.r(c) / 255f).pow(gamma) * a
            chans[1].data[i] = (Px.g(c) / 255f).pow(gamma) * a
            chans[2].data[i] = (Px.b(c) / 255f).pow(gamma) * a
            weight.data[i] = a
        }
        // Três passadas de média ≈ desfoque gaussiano largo (custo independente do raio).
        fun blur3(p: Plane): Plane { var q = p; repeat(3) { q = Planes.boxBlur(q, max(1, radius / 2)) }; return q }
        val br = blur3(chans[0]); val bg = blur3(chans[1]); val bb = blur3(chans[2]); val bw = blur3(weight)
        val out = IntArray(w * h)
        for (i in 0 until w * h) {
            val s = max(bw.data[i], 1e-4f)
            fun enc(v: Float) = ((v / s).coerceIn(0f, 1f).pow(1f / gamma) * 255f + 0.5f).toInt()
            out[i] = if (bw.data[i] < 1e-3f) small[i] else Px.argb(enc(br.data[i]), enc(bg.data[i]), enc(bb.data[i]))
        }
        return out
    }

    /**
     * Composição final de uma região em resolução cheia. [blurNear]/[blurFar] são o fundo
     * desfocado com raio menor e maior; [distance] escolhe entre eles (desfoque gradual).
     * Onde α = 1 (pessoa) o pixel original é copiado sem alteração.
     */
    /**
     * Raio do desfoque do fundo (px por 1000 px do lado maior) para a intensidade [s] 0..1. Foto e prévia usam
     * a mesma conta. 0.36 (garrafa × Samsung, 20/10: textura da parede ainda visível): +33% sobre 5 + 18·s,
     * no padrão 0,6 ≈ 2,6% da largura, perto do f/1.4–f/2 a 50 mm da pesquisa (raio ≈ 2% da largura).
     */
    fun farRadius(s: Float) = 6f + 24f * s
    fun nearRadius(s: Float) = 4f + 8f * s

    /**
     * Destaque do assunto: o fundo desfocado desce um pouco (0,92 nos valores sRGB ≈ −0,27 EV em luz linear),
     * como a vinheta de uma lente aberta. Na garrafa da Samsung o fundo fica abaixo do assunto; no Nova
     * empatava. Foto e prévia iguais.
     */
    const val BACKGROUND_KEEP = 0.92f

    /** Fundo escuro: multiplica o fundo por [keep] (0,42 ≈ −1,25 EV), em transição suave pela máscara. */
    const val DARK_BACKGROUND_KEEP = 0.42f

    fun darkenBackgroundTile(tile: IntArray, tw: Int, th: Int, x0: Int, y0: Int, fullW: Int, fullH: Int,
                             matte: Plane, keep: Float = DARK_BACKGROUND_KEEP) {
        val mx = matte.w.toFloat() / fullW; val my = matte.h.toFloat() / fullH
        for (ty in 0 until th) for (tx in 0 until tw) {
            val a = matte.sample((x0 + tx + 0.5f) * mx - 0.5f, (y0 + ty + 0.5f) * my - 0.5f).coerceIn(0f, 1f)
            if (a >= 0.999f) continue
            val f = keep + (1f - keep) * a
            val i = ty * tw + tx
            val c = tile[i]
            val r = ((c shr 16 and 0xFF) * f + 0.5f).toInt()
            val g = ((c shr 8 and 0xFF) * f + 0.5f).toInt()
            val b = ((c and 0xFF) * f + 0.5f).toInt()
            tile[i] = (c and -0x1000000) or (r shl 16) or (g shl 8) or b
        }
    }

    fun compositeTile(
        tile: IntArray, tw: Int, th: Int, x0: Int, y0: Int, fullW: Int, fullH: Int,
        matte: Plane, distance: Plane, blurNear: IntArray, blurFar: IntArray, smallW: Int, smallH: Int,
        /** Desvio do ruído do sensor (níveis 0..255) devolvido ao fundo desfocado; 0 = sem grão. */
        grainSigma: Float = 0f,
        /**
         * [distance] é o desfoque pela profundidade (0 = no plano de foco, nítido; 1 = máximo), e não
         * a distância ao contorno: pontos no foco ficam com o pixel original mesmo fora da máscara.
         */
        depthMode: Boolean = false,
    ) {
        val mx = matte.w.toFloat() / fullW; val my = matte.h.toFloat() / fullH
        val dx = distance.w.toFloat() / fullW; val dy = distance.h.toFloat() / fullH
        val bx = smallW.toFloat() / fullW; val by = smallH.toFloat() / fullH
        for (ty in 0 until th) for (tx in 0 until tw) {
            val gx = x0 + tx + 0.5f; val gy = y0 + ty + 0.5f
            val a = matte.sample(gx * mx - 0.5f, gy * my - 0.5f)
            if (a >= 0.999f) continue
            val t = distance.sample(gx * dx - 0.5f, gy * dy - 0.5f)
            val near = sampleArgb(blurNear, smallW, smallH, gx * bx - 0.5f, gy * by - 0.5f)
            val far = sampleArgb(blurFar, smallW, smallH, gx * bx - 0.5f, gy * by - 0.5f)
            val i = ty * tw + tx
            val c = tile[i]
            val k = if (depthMode) smoothRange(0.25f, 0.9f, t) else smoothT(t)
            // Profundidade: do nítido (no foco) ao desfoque próximo; sem profundidade, sempre desfoca.
            val kSharp = if (depthMode) 1f - smoothRange(0.02f, 0.25f, t) else 0f
            // O desfoque apaga o ruído: sem devolver o grão, o fundo fica liso e a pessoa parece recortada.
            val grain = if (grainSigma > 0f) grainSigma * gaussHash(x0 + tx, y0 + ty) * (1f - a) * (1f - kSharp) else 0f
            val keep = 1f - (1f - BACKGROUND_KEEP) * (1f - kSharp)
            fun mix(o: Int, n: Int, f: Int): Int {
                val blurred = (n + (f - n) * k) * keep + grain
                val bg = blurred + (o - blurred) * kSharp
                return (o * a + bg * (1f - a) + 0.5f).toInt()
            }
            tile[i] = Px.argb(mix(Px.r(c), Px.r(near), Px.r(far)), mix(Px.g(c), Px.g(near), Px.g(far)), mix(Px.b(c), Px.b(near), Px.b(far)))
        }
    }

    /** Ruído gaussiano determinístico por posição (soma de 4 uniformes), média 0 e desvio ≈ 1. */
    fun gaussHash(x: Int, y: Int): Float {
        var hsh = x * 73856093 xor y * 19349663
        var s = 0f
        repeat(4) {
            hsh = hsh xor (hsh ushr 13); hsh *= 0x5bd1e995; hsh = hsh xor (hsh ushr 15)
            s += (hsh ushr 8).toFloat() / (1 shl 24)
        }
        return (s - 2f) * 1.7320508f
    }

    /**
     * Desvio do ruído (luminância, 0..255) estimado pelo laplaciano: mediana por bloco e o
     * percentil 20 entre blocos — os blocos mais lisos dominam, então textura não conta como ruído.
     */
    fun noiseSigma(px: IntArray, w: Int, h: Int, block: Int = 64): Float {
        val medians = ArrayList<Float>()
        val vals = FloatArray((block / 2) * (block / 2))
        var by = 1
        while (by + block < h - 1) {
            var bx = 1
            while (bx + block < w - 1) {
                var n = 0
                var y = by
                while (y < by + block) {
                    var x = bx
                    while (x < bx + block) {
                        fun l(i: Int) = Px.luma(px[i]) * 255f
                        val i = y * w + x
                        vals[n++] = kotlin.math.abs(4 * l(i) - l(i - 1) - l(i + 1) - l(i - w) - l(i + w))
                        x += 2
                    }
                    y += 2
                }
                vals.sort(0, n)
                medians += vals[n / 2]
                bx += block
            }
            by += block
        }
        if (medians.isEmpty()) return 0f
        medians.sort()
        // Mediana de |N(0, 20σ²)| = 0,6745·√20·σ.
        return (medians[medians.size / 5] / (0.6745f * 4.4721f)).coerceIn(0f, 6f)
    }

    private fun smoothRange(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t)
    }

    private fun smoothT(t: Float) = (t * 2.2f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }

    fun sampleArgb(px: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val fx = x.coerceIn(0f, (w - 1).toFloat()); val fy = y.coerceIn(0f, (h - 1).toFloat())
        val x0 = fx.toInt(); val y0 = fy.toInt(); val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
        val tx = fx - x0; val ty = fy - y0
        fun ch(sh: Int): Int {
            fun v(c: Int) = ((c shr sh) and 0xFF).toFloat()
            val a = v(px[y0 * w + x0]) + (v(px[y0 * w + x1]) - v(px[y0 * w + x0])) * tx
            val b = v(px[y1 * w + x0]) + (v(px[y1 * w + x1]) - v(px[y1 * w + x0])) * tx
            return (a + (b - a) * ty + 0.5f).toInt()
        }
        return Px.argb(ch(16), ch(8), ch(0))
    }
}

/**
 * Food: realce moderado de vivacidade (só cores pouco saturadas, sem "estourar" as já vivas),
 * leve calor e contraste local suave via [ToneMap]. Preserva o equilíbrio de branco medido.
 */
object FoodLook {
    fun applyTile(tile: IntArray, strength: Float = 1f) {
        val s = strength.coerceIn(0f, 1f)
        for (i in tile.indices) {
            val c = tile[i]
            var r = Px.r(c).toFloat(); var g = Px.g(c).toFloat(); var b = Px.b(c).toFloat()
            val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            val sat = if (mx > 0) (mx - mn) / mx else 0f
            // Vivacidade: mais efeito em cores apagadas; cores já saturadas quase não mudam.
            val vib = 1f + 0.22f * s * (1f - sat)
            r = l + (r - l) * vib; g = l + (g - l) * vib; b = l + (b - l) * vib
            // Calor sutil (+2% vermelho, −2% azul) — próximo de luz de mesa, sem virar filtro.
            r *= 1f + 0.02f * s; b *= 1f - 0.02f * s
            tile[i] = Px.argb((r + 0.5f).toInt(), (g + 0.5f).toInt(), (b + 0.5f).toInt())
        }
    }
}
