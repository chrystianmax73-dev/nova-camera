package com.novacamera.s24.core

import java.util.Locale
import kotlin.math.abs

/**
 * Consumo e temperatura reais durante o uso da câmera, por atividade (Foto, Retrato, Vídeo
 * gravando, processando…). Amostras a cada ~10 s com o app aberto; só as amostras na bateria
 * entram no consumo (carregando, a corrente medida é a da carga). Potência instantânea calculada de corrente/tensão do aparelho; não energia integrada
 * nem consumo isolado do aplicativo. Comparar apenas condições controladas.
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
     * Unidade documentada pela API: µA, sem reescala heurística. Preserva bruto/sinal no diagnóstico.
     * Zero, sentinelas e leitura fora da faixa são indisponíveis, não consumo nulo.
     */
    fun normalizeUa(raw: Long): Long? {
        if(raw==Long.MIN_VALUE || raw==Int.MIN_VALUE.toLong()) return null
        // BatteryManager.CURRENT_NOW define µA; magnitude não permite inferir outra unidade.
        return abs(raw).takeIf { it in 1L..15_000_000L }
    }

    @Synchronized fun add(s: Sample) { samples.addLast(s); while (samples.size > KEEP) samples.removeFirst() }
    @Synchronized fun clear() = samples.clear()
    @Synchronized fun snapshot(): List<Sample> = samples.toList()

    @Synchronized fun report(): String = report(samples.toList())

    fun report(list: List<Sample>): String = buildString {
        append("Energia e temperatura da sessão")
        if (list.isEmpty()) { append(": sem amostras"); return@buildString }
        val span = (list.last().tMs - list.first().tMs) / 60_000.0
        append(String.format(Locale.US, " (%.0f min, %d amostras)", span, list.size))
        list.groupBy { it.activity }.forEach { (activity, group) ->
            val mw = group.mapNotNull { it.powerMw }
            append("\n  $activity: ${group.size} amostras")
            if (mw.isNotEmpty()) append(String.format(Locale.US, ", %.0f mW em média (máx. %.0f; n válido=%d/%d)", mw.average(), mw.max(), mw.size, group.size))
            else append(", consumo não medido (carregando ou sem leitura de corrente)")
            group.mapNotNull { it.batteryTempC }.takeIf { it.isNotEmpty() }?.let { t ->
                append(String.format(Locale.US, ", bateria %.1f–%.1f °C", t.min(), t.max()))
            }
            group.mapNotNull { it.headroom }.takeIf { it.isNotEmpty() }?.let { h ->
                append(String.format(Locale.US, ", folga térmica pior %.2f", h.max()))
            }
            append(", estado térmico máx. ${group.maxOf { it.thermalStatus }}")
        }
        append("\n  Leituras recentes (aparelho inteiro; instantâneas, não energia integrada; módulo do sinal):")
        list.takeLast(12).forEach { s ->
            append("\n    t=${s.tMs}ms · ${s.activity} · bruto=${s.currentUa} · unidade=µA (API, sem reescala)" +
                " · módulo válido=${s.currentUa?.let(::normalizeUa)}µA · tensão=${s.voltageMv}mV" +
                " · potência=${s.powerMw?.let { String.format(Locale.US,"%.1f",it) } ?: "não medida"}mW" +
                " · conectado=${s.charging} · bateria=${s.percent}%/${s.batteryTempC}°C · térmica=${s.thermalStatus}")
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
