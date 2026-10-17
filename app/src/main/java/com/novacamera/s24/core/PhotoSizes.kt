package com.novacamera.s24.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Uma resolução de foto que a câmera realmente anuncia ao Android. */
data class SizeOption(val width: Int, val height: Int, val highResolution: Boolean, val maximumPixelMode: Boolean = false) {
    val pixels get() = width.toLong() * height
    val megapixels get() = pixels / 1e6
    val label get() = "${megapixels.roundToInt()} MP"
    val key get() = "${width}x$height"
}

/**
 * Escolha de resolução por câmera, a partir das listas públicas do Camera2:
 * - [regular]: SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(JPEG)
 * - [highRes]: getHighResolutionOutputSizes(JPEG) (captura mais lenta, mas pública)
 * - [maxResOnly]: tamanhos que só existem no mapa de "resolução máxima do sensor"
 *   (SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION) — exigem uma sessão Camera2 especial
 *   que o CameraX não oferece; são informados como bloqueados, nunca simulados.
 */
object PhotoSizes {
    /** Proporção do sensor de cada opção do app (null = qualquer). */
    fun aspectFor(ratio: String): Float? = when (ratio) {
        "4:3" -> 4f / 3f
        "16:9" -> 16f / 9f
        "1:1" -> 4f / 3f   // 1:1 é recortado de 4:3 pelo visor
        else -> 16f / 9f   // "Tela": captura 16:9 e o visor recorta
    }

    private fun matches(w: Int, h: Int, aspect: Float?): Boolean {
        if (aspect == null) return true
        val r = max(w, h).toFloat() / min(w, h)
        return abs(r - aspect) / aspect < 0.03f
    }

    /**
     * Opções distintas (uma por "classe" de megapixels, a maior de cada), da maior para a menor.
     * Só a padrão e as maiores entram (como na câmera do sistema: 12 / 50 / 200 MP).
     */
    fun options(regular: List<Pair<Int, Int>>, highRes: List<Pair<Int, Int>>, aspect: Float?): List<SizeOption> {
        val all = regular.filter { matches(it.first, it.second, aspect) }.map { SizeOption(it.first, it.second, false) } +
            highRes.filter { matches(it.first, it.second, aspect) }.map { SizeOption(it.first, it.second, true) }
        if (all.isEmpty()) return emptyList()
        val sorted = all.sortedByDescending { it.pixels }
        val out = ArrayList<SizeOption>()
        for (s in sorted) {
            // Mesma classe se a diferença for menor que 25% (ex.: 12,0 e 12,2 MP).
            if (out.any { it.pixels <= s.pixels * 1.25 }) continue
            out += s
        }
        val standard = standard(out) ?: return out
        return out.filter { it.pixels * 10 >= standard.pixels * 9 }
    }

    /** JPEGs do mapa máximo, acessíveis por sessão Camera2 própria, nunca tamanhos RAW. */
    fun nativeOptions(regular: List<Pair<Int, Int>>, highRes: List<Pair<Int, Int>>,
        maximumJpeg: List<Pair<Int, Int>>, aspect: Float?, maximumModeAvailable: Boolean): List<SizeOption> {
        val standard = options(regular, highRes, aspect)
        if (!maximumModeAvailable) return standard
        val known = (regular + highRes).toSet()
        val maximum = maximumJpeg.filter { it !in known && matches(it.first,it.second,aspect) }
            .map { SizeOption(it.first,it.second,true,true) }
        return (standard + maximum).sortedByDescending { it.pixels }.fold(mutableListOf<SizeOption>()) { result, size ->
            if (result.none { abs(it.pixels.toDouble()/size.pixels-1.0)<0.20 }) result.add(size)
            result
        }
    }

    /** Resolução padrão: a maior saída normal (não de alta resolução) até ~16 MP, ou a maior normal. */
    fun standard(options: List<SizeOption>): SizeOption? {
        val regular = options.filter { !it.highResolution }
        return regular.filter { it.megapixels <= 16.5 }.maxByOrNull { it.pixels } ?: regular.maxByOrNull { it.pixels }
            ?: options.minByOrNull { it.pixels }
    }

    /** Megapixels dos tamanhos que só existem no modo de resolução máxima (não oferecidos). */
    fun blocked(options: List<SizeOption>, maxResOnly: List<Pair<Int, Int>>, aspect: Float?): List<SizeOption> {
        val best = options.maxOfOrNull { it.pixels } ?: 0L
        return maxResOnly.filter { matches(it.first, it.second, aspect) }
            .map { SizeOption(it.first, it.second, true) }
            .filter { it.pixels > best * 1.25 }
            .sortedByDescending { it.pixels }
            .distinctBy { it.megapixels.roundToInt() / 10 }
    }

    /**
     * A opção mais próxima dos megapixels escolhidos pelo usuário (vale entre proporções: 50 MP
     * em 4:3 vira a classe equivalente em 16:9); sem escolha salva, a padrão.
     */
    fun pick(options: List<SizeOption>, savedPixels: Long?): SizeOption? {
        if (savedPixels == null || savedPixels <= 0) return standard(options)
        return options.minByOrNull { abs(kotlin.math.ln(it.pixels.toDouble() / savedPixels)) }
    }
}

