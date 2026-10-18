package com.novacamera.s24.core

import java.util.Locale

/**
 * Custo real de cada processamento no aparelho, para o diagnóstico: duração e variação de
 * temperatura da bateria / estado térmico entre o início e o fim. Base para otimizar o
 * aquecimento com números do S24 Ultra, não com estimativas.
 */
object CostLog {
    data class Entry(val kind: String, val ms: Long, val start: DeviceHeat?, val end: DeviceHeat?)

    private const val KEEP = 30
    private val entries = ArrayDeque<Entry>()

    @Synchronized fun add(e: Entry) { entries.addLast(e); while (entries.size > KEEP) entries.removeFirst() }
    @Synchronized fun clear() = entries.clear()

    @Synchronized fun report(): String = buildString {
        append("Custo do processamento")
        if (entries.isEmpty()) { append(": nenhuma foto processada ainda"); return@buildString }
        entries.groupBy { it.kind }.forEach { (kind, list) ->
            val ms = list.map { it.ms }.sorted()
            val dT = list.mapNotNull { e -> e.start?.batteryTempC?.let { a -> e.end?.batteryTempC?.let { it - a } } }
            val worst = list.maxOf { it.end?.thermalStatus ?: 0 }
            append(String.format(Locale.US, "\n  %s (%d): mediana %.1f s, máx. %.1f s", kind, list.size, ms[ms.size / 2] / 1000.0, ms.last() / 1000.0))
            if (dT.isNotEmpty()) append(String.format(Locale.US, ", bateria %+.1f °C em média", dT.average()))
            append(", estado térmico máx. $worst")
        }
    }
}
