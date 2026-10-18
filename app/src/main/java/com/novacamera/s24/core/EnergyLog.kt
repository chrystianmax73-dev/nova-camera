package com.novacamera.s24.core

import java.util.Locale
import kotlin.math.abs

/**
 * Consumo e temperatura reais durante o uso da câmera, por atividade (Foto, Retrato, Vídeo
 * gravando, processando…). Amostras a cada ~10 s com o app aberto; só as amostras na bateria
 * entram no consumo (carregando, a corrente medida é a da carga). Números do aparelho, para
 * comparar versões em sessões longas — não estimativas.
 */
object EnergyLog {
    data class Sample(
        val tMs: Long,
        val activity: String,
        /** Corrente instantânea em µA (sinal varia por fabricante; usamos o módulo). */
        val currentUa: Long?,
        val voltageMv: Int?,
        val batteryTempC: Float?,
        val percent: Int,
        val charging: Boolean,
        val thermalStatus: Int,
        /** Previsão do Android: 1,0 ≈ limite térmico em 10 s. */
        val headroom: Float?,
    ) {
        /** Potência em mW, só na bateria e com leitura plausível. */
        val powerMw: Double?
            get() {
                if (charging) return null
                val ua = normalizeUa(currentUa ?: return null) ?: return null
                val mv = voltageMv?.takeIf { it in 2500..5000 } ?: return null
                return ua / 1000.0 * mv / 1000.0
            }
    }

    private const val KEEP = 720 // ~2 h a cada 10 s
    private val samples = ArrayDeque<Sample>()

    /**
     * Alguns aparelhos informam mA em vez de µA: abaixo de 10 000 em módulo (10 mA em µA é
     * implausível com a câmera aberta), tratamos como mA.
     */
    fun normalizeUa(raw: Long): Long? {
        val a = abs(raw)
        if (a == 0L) return null
        val ua = if (a < 10_000) a * 1000 else a
        return ua.takeIf { it in 10_000L..15_000_000L }
    }

    @Synchronized fun add(s: Sample) { samples.addLast(s); while (samples.size > KEEP) samples.removeFirst() }
    @Synchronized fun clear() = samples.clear()
    @Synchronized fun snapshot(): List<Sample> = samples.toList()

    @Synchronized fun report(): String = report(samples.toList())

    fun report(list: List<Sample>): String = buildString {
        append("Energia e temperatura da sessão")
        if (list.size < 2) { append(": poucas amostras (deixe a câmera aberta alguns minutos)"); return@buildString }
        val span = (list.last().tMs - list.first().tMs) / 60_000.0
        append(String.format(Locale.US, " (%.0f min, %d amostras)", span, list.size))
        list.groupBy { it.activity }.forEach { (activity, group) ->
            val mw = group.mapNotNull { it.powerMw }
            append("\n  $activity: ${group.size} amostras")
            if (mw.isNotEmpty()) append(String.format(Locale.US, ", %.0f mW em média (máx. %.0f)", mw.average(), mw.max()))
            else append(", consumo não medido (carregando ou sem leitura de corrente)")
            group.mapNotNull { it.batteryTempC }.takeIf { it.isNotEmpty() }?.let { t ->
                append(String.format(Locale.US, ", bateria %.1f–%.1f °C", t.min(), t.max()))
            }
            group.mapNotNull { it.headroom }.takeIf { it.isNotEmpty() }?.let { h ->
                append(String.format(Locale.US, ", folga térmica pior %.2f", h.max()))
            }
            append(", estado térmico máx. ${group.maxOf { it.thermalStatus }}")
        }
        // Queda de bateria por hora, só em trechos contínuos na bateria.
        val onBattery = list.filter { !it.charging && it.percent >= 0 }
        if (onBattery.size >= 2) {
            val first = onBattery.first(); val last = onBattery.last()
            val hours = (last.tMs - first.tMs) / 3_600_000.0
            if (hours >= 5.0 / 60 && list.none { it.charging && it.tMs in first.tMs..last.tMs }) {
                append(String.format(Locale.US, "\n  Bateria: %d%% → %d%% (%.1f%%/h na bateria)",
                    first.percent, last.percent, (first.percent - last.percent) / hours))
            }
        }
        val t = list.mapNotNull { it.batteryTempC }
        if (t.size >= 2) append(String.format(Locale.US, "\n  Temperatura da bateria: %.1f → %.1f °C (máx. %.1f)", t.first(), t.last(), t.max()))
    }
}
