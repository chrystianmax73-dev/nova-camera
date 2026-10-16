package com.novacamera.s24.core.imaging

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Retrato com profundidade estimada: o desfoque cresce com a distância ao plano de foco, como numa
 * lente real — o que está na mesma distância do assunto fica nítido (a mão e o objeto que ela
 * segura, o chão sob os pés), o que está à frente também desfoca. A profundidade do modelo é
 * relativa (sem escala métrica), então tudo é medido em fração da faixa de profundidade da cena.
 */
object DepthPortrait {
    /** Distância à faixa do assunto até o desfoque máximo (fração da faixa da cena). */
    const val SPAN = 0.35f
    /** Pessoa só define o foco se ocupar ao menos isso do quadro. */
    private const val MIN_PERSON = 0.01f

    /** 0..1 pela faixa da cena (percentis 2–98); maior = mais perto. */
    fun normalize(d: Plane): Plane {
        val sorted = d.data.copyOf().also { it.sort() }
        val lo = sorted[(sorted.size * 0.02f).toInt()]
        val hi = sorted[min(sorted.size - 1, (sorted.size * 0.98f).toInt())]
        val span = max(hi - lo, 1e-6f)
        return Plane(d.w, d.h, FloatArray(d.data.size) { ((d.data[it] - lo) / span).coerceIn(0f, 1f) })
    }

    /**
     * Profundidade do plano de foco: mediana dentro da pessoa (confiança ≥ 0,8); sem pessoa, o que
     * está mais perto no centro do quadro (percentil 85 do retângulo central) — retrato de objeto.
     */
    fun focus(depth: Plane, person: Plane?): Float {
        if (person != null) {
            val p = if (person.w == depth.w && person.h == depth.h) person else Planes.resize(person, depth.w, depth.h)
            val inside = ArrayList<Float>()
            for (i in depth.data.indices) if (p.data[i] >= 0.8f) inside += depth.data[i]
            if (inside.size >= depth.data.size * MIN_PERSON) { inside.sort(); return inside[inside.size / 2] }
        }
        val center = ArrayList<Float>()
        for (y in (depth.h * 0.25f).toInt() until (depth.h * 0.75f).toInt())
            for (x in (depth.w * 0.3f).toInt() until (depth.w * 0.7f).toInt()) center += depth[x, y]
        center.sort()
        return center[(center.size * 0.85f).toInt().coerceAtMost(center.size - 1)]
    }

    /** Assunto (máscara grosseira, 0..1) e desfoque (0 = nítido) prontos para o retrato. */
    class Plan(val subject: Plane, val blur: Plane, val near: Float, val far: Float, val fromPerson: Boolean)

    /** Folga em torno da faixa de profundidade do assunto e desfoque mínimo do que não é assunto. */
    private const val MARGIN = 0.04f
    private const val BACKGROUND_FLOOR = 0.3f

    /**
     * O assunto é um **objeto inteiro**, não uma fatia de profundidade: uma planta ou um braço
     * estendido ocupam uma faixa grande de distâncias. Com pessoa, a faixa é a da pessoa (percentis
     * 5–95 dentro dela, com folga) — o que ela segura na mesma faixa entra junto. Sem pessoa, a
     * profundidade é dividida em perto × longe (Otsu) e o assunto é o lado do que está no centro.
     * Só entra o que está **conectado** à pessoa ou ao centro: manchas soltas na mesma distância não
     * viram assunto. O desfoque cresce com a distância até a faixa do assunto.
     */
    fun plan(depth: Plane, person: Plane?, tap: Pair<Float, Float>? = null): Plan {
        val w = depth.w; val h = depth.h; val n = w * h
        val p0 = person?.let { if (it.w == w && it.h == h) it else Planes.resize(it, w, h) }
        // Toque do usuário (0..1 na foto): fora da pessoa, o assunto passa a ser o objeto tocado.
        val tapIndex = tap?.let { (tx, ty) ->
            (ty.coerceIn(0f, 1f) * (h - 1)).roundToInt() * w + (tx.coerceIn(0f, 1f) * (w - 1)).roundToInt()
        }
        val tapOnPerson = tapIndex != null && p0 != null && p0.data[tapIndex] >= 0.5f
        val p = if (tapIndex != null && !tapOnPerson) null else p0
        val inPerson = p?.let { pp -> depth.data.indices.filter { pp.data[it] >= 0.8f } }.orEmpty()
        val fromPerson = inPerson.size >= n * MIN_PERSON
        val seed: (Int) -> Boolean = if (tapIndex != null && !tapOnPerson) { i -> near(i, tapIndex, w, max(1, min(w, h) / 40)) }
            else { i -> central(i, w, h) }
        var near: Float; var far: Float
        var connected: BooleanArray
        if (fromPerson) {
            val v = inPerson.map { depth.data[it] }.sorted()
            far = v[(v.size * 0.05f).toInt()] - MARGIN
            near = v[min(v.size - 1, (v.size * 0.95f).toInt())] + MARGIN
            connected = grow(depth, far, near) { i -> p!!.data[i] >= 0.5f }
        } else {
            // Perto × longe; se o "perto" conectado ao centro tomar quase o quadro (chão e parede junto
            // com o objeto), separa em três faixas e fica com a do centro.
            val f = if (tapIndex != null && !tapOnPerson) tapDepth(depth, tapIndex) else focus(depth, null)
            val t = otsu(depth)
            var range = if (f >= t) t to 1f else 0f to t
            connected = grow(depth, range.first, range.second, seed)
            if (connected.count { it } > n * MAX_OBJECT) {
                val (t1, t2) = otsu3(depth)
                range = when { f >= t2 -> t2 to 1f; f >= t1 -> t1 to t2; else -> 0f to t1 }
                val refined = grow(depth, range.first, range.second, seed)
                if (refined.count { it } >= n * MIN_PERSON) connected = refined
                else range = if (f >= t) t to 1f else 0f to t
            }
            far = range.first; near = range.second
        }
        val subject = Plane(w, h, FloatArray(n) { i -> max(if (connected[i]) 1f else 0f, p?.data?.get(i) ?: 0f) })
        val blur = Plane(w, h, FloatArray(n) { i ->
            if (subject.data[i] >= 0.5f) 0f else {
                val d = depth.data[i]
                val dist = if (d < far) far - d else if (d > near) d - near else 0f
                max(BACKGROUND_FLOOR, (dist / SPAN).coerceIn(0f, 1f))
            }
        })
        // Máximo afasta o assunto do fundo colado no contorno; a média em seguida tira o degrau
        // quadrado do filtro de máximo (transições de desfoque suaves).
        val r = max(2, min(w, h) / 120)
        return Plan(Planes.boxBlur(subject, 1), Planes.boxBlur(maxFilter(blur, 2 * r), r), near, far, fromPerson)
    }

    /** O objeto sem pessoa não pode ocupar quase o quadro inteiro (seria chão/parede junto). */
    private const val MAX_OBJECT = 0.45f

    private fun near(i: Int, c: Int, w: Int, r: Int): Boolean {
        val dx = i % w - c % w; val dy = i / w - c / w
        return dx * dx + dy * dy <= r * r
    }

    /** Profundidade no ponto tocado: mediana 5×5 (um pixel ruidoso não decide o assunto). */
    private fun tapDepth(depth: Plane, c: Int): Float {
        val w = depth.w; val h = depth.h; val cx = c % w; val cy = c / w
        val v = ArrayList<Float>(25)
        for (dy in -2..2) for (dx in -2..2) {
            val x = cx + dx; val y = cy + dy
            if (x in 0 until w && y in 0 until h) v += depth[x, y]
        }
        v.sort(); return v[v.size / 2]
    }

    private fun central(i: Int, w: Int, h: Int): Boolean {
        val x = i % w; val y = i / w
        return x in (w * 3 / 10)..(w * 7 / 10) && y in (h / 4)..(h * 3 / 4)
    }

    /** Região na faixa [lo, hi] conectada (8 vizinhos) às sementes. */
    private fun grow(depth: Plane, lo: Float, hi: Float, seed: (Int) -> Boolean): BooleanArray {
        val w = depth.w; val h = depth.h; val n = w * h
        val inRange = BooleanArray(n) { depth.data[it] in lo..hi }
        val out = BooleanArray(n)
        val queue = IntArray(n); var head = 0; var tail = 0
        for (i in 0 until n) if (inRange[i] && seed(i)) { out[i] = true; queue[tail++] = i }
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (!out[j] && inRange[j]) { out[j] = true; queue[tail++] = j }
            }
        }
        return out
    }

    /** Otsu com três classes (64 níveis): limiares entre longe, meio e perto. */
    fun otsu3(depth: Plane): Pair<Float, Float> {
        val bins = 64
        val p = DoubleArray(bins)
        for (v in depth.data) p[min(bins - 1, (v.coerceIn(0f, 1f) * bins).toInt())] += 1.0
        val total = depth.data.size.toDouble(); for (i in 0 until bins) p[i] /= total
        val cw = DoubleArray(bins + 1); val cm = DoubleArray(bins + 1)
        for (i in 0 until bins) { cw[i + 1] = cw[i] + p[i]; cm[i + 1] = cm[i] + p[i] * (i + 0.5) }
        val mt = cm[bins]
        var best = -1.0; var ba = bins / 3; var bb = 2 * bins / 3
        for (a in 1 until bins - 1) for (b in a + 1 until bins) {
            val w0 = cw[a]; val w1 = cw[b] - cw[a]; val w2 = 1.0 - cw[b]
            if (w0 <= 0 || w1 <= 0 || w2 <= 0) continue
            val m0 = cm[a] / w0; val m1 = (cm[b] - cm[a]) / w1; val m2 = (mt - cm[b]) / w2
            val v = w0 * (m0 - mt) * (m0 - mt) + w1 * (m1 - mt) * (m1 - mt) + w2 * (m2 - mt) * (m2 - mt)
            if (v > best) { best = v; ba = a; bb = b }
        }
        return ba.toFloat() / bins to bb.toFloat() / bins
    }

    /** Limiar de Otsu (256 níveis) entre perto e longe. */
    fun otsu(depth: Plane): Float {
        val hist = IntArray(256)
        for (v in depth.data) hist[(v.coerceIn(0f, 1f) * 255f).toInt()]++
        val total = depth.data.size.toDouble()
        var sumAll = 0.0; for (i in 0 until 256) sumAll += i * hist[i].toDouble()
        var wB = 0.0; var sumB = 0.0; var best = 0.0; var thr = 128
        for (i in 0 until 256) {
            wB += hist[i]; if (wB == 0.0) continue
            val wF = total - wB; if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB; val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; thr = i }
        }
        return (thr + 0.5f) / 255f
    }

    /** Máximo em janela quadrada (2r+1)², separável. */
    fun maxFilter(p: Plane, r: Int): Plane {
        if (r <= 0) return p
        val w = p.w; val h = p.h
        val tmp = Plane(w, h); val out = Plane(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0f
            for (k in max(0, x - r)..min(w - 1, x + r)) m = max(m, p.data[y * w + k])
            tmp.data[y * w + x] = m
        }
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0f
            for (k in max(0, y - r)..min(h - 1, y + r)) m = max(m, tmp.data[k * w + x])
            out.data[y * w + x] = m
        }
        return out
    }
}
