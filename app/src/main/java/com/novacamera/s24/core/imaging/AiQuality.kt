package com.novacamera.s24.core.imaging


import kotlin.math.sqrt

/**
 * A IA mede antes, age na medida e prova que melhorou (pedido do dono, 19/10):
 * - ruído: mediana do σ de blocos da foto em resolução cheia ([TileNoise]);
 * - bordas: média do 5% de maior gradiente numa versão reduzida 4× (o ruído some na redução, o contorno
 *   real fica) — mede se a IA apagou detalhe.
 * A força da rede depende do ruído medido; a troca na galeria só acontece se o ruído caiu e as bordas
 * ficaram. Sem rede nem referência externa: comparação da própria foto antes × depois.
 */
object AiQuality {
    data class Stats(val noise: Float, val edges: Float)

    /** Força da rede pelo ruído (σ em níveis de 8 bits). 0 = já limpa, nada a fazer. */
    fun strengthFor(noise: Float): Float = when {
        noise < TileNoise.CLEAN_SIGMA -> 0f
        noise < 3f -> 0.55f
        noise < 6f -> 0.75f
        else -> 0.9f
    }

    /** Ruído caiu de verdade e o contorno não perdeu mais que [MAX_EDGE_LOSS]. */
    fun accept(before: Stats, after: Stats): Boolean =
        after.noise <= before.noise * MAX_NOISE_RATIO && after.edges >= before.edges * (1f - MAX_EDGE_LOSS)

    const val MAX_NOISE_RATIO = 0.9f
    const val MAX_EDGE_LOSS = 0.08f

    /** Média dos 5% maiores gradientes (Sobel) da luminância reduzida [luma] (0..255). */
    fun edgeEnergy(luma: IntArray, w: Int, h: Int): Float {
        if (w < 3 || h < 3) return 0f
        val hist = IntArray(1443) // |grad| até 255·4·√2
        val mags = IntArray((w - 2) * (h - 2))
        var k = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = luma[i - w + 1] + 2 * luma[i + 1] + luma[i + w + 1] - luma[i - w - 1] - 2 * luma[i - 1] - luma[i + w - 1]
            val gy = luma[i + w - 1] + 2 * luma[i + w] + luma[i + w + 1] - luma[i - w - 1] - 2 * luma[i - w] - luma[i - w + 1]
            val m = sqrt((gx * gx + gy * gy).toFloat()).toInt().coerceAtMost(hist.size - 1)
            mags[k++] = m; hist[m]++
        }
        // Limiar do 95º percentil, média acima dele.
        var need = (k * 0.05f).toInt().coerceAtLeast(1)
        var cut = hist.size - 1
        while (cut > 0 && need > hist[cut]) { need -= hist[cut]; cut-- }
        var sum = 0L; var n = 0
        for (i in 0 until k) if (mags[i] >= cut) { sum += mags[i]; n++ }
        return if (n == 0) 0f else sum.toFloat() / n
    }

    /** Média 4×4 de luminância de [rows] linhas de [argb] (múltiplo de 4) para [out] (largura w/4) a partir de [oy]. */
    fun box4Luma(argb: IntArray, w: Int, rows: Int, out: IntArray, oy: Int) {
        val sw = w / 4
        for (r in 0 until rows / 4) for (x in 0 until sw) {
            var s = 0
            for (dy in 0 until 4) for (dx in 0 until 4) {
                val p = argb[(4 * r + dy) * w + 4 * x + dx]
                s += ((p shr 16 and 255) * 77 + (p shr 8 and 255) * 150 + (p and 255) * 29) shr 8
            }
            out[(oy + r) * sw + x] = s / 16
        }
    }

    fun median(v: FloatArray): Float = v.sortedArray().let { if (it.isEmpty()) 0f else it[it.size / 2] }
}
