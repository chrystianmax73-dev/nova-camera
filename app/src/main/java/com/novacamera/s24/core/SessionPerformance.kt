package com.novacamera.s24.core

import java.util.Locale

/** Medições do processo; potência de EnergyLog é do aparelho inteiro. GPU não é inferida. */
object SessionPerformance {
    data class Sample(val atMs: Long, val cpuMs: Long, val pssKb: Long?, val activity: String,
        val frames: Int, val dropped: Int, val probeMs: Double)
    data class Interval(val activity: String, val wallMs: Long, val cpuMs: Long,
        val frames: Int, val dropped: Int)
    class Counter {
        private var previous: Sample? = null
        fun reset() { previous = null }
        fun add(s: Sample): Interval? {
            val p = previous; previous = s
            if (p == null || p.activity != s.activity) return null
            val dt = s.atMs - p.atMs
            if (dt !in 1L..30_000L || s.cpuMs < p.cpuMs || s.frames < p.frames || s.dropped < p.dropped) return null
            return Interval(s.activity, dt, s.cpuMs - p.cpuMs, s.frames - p.frames, s.dropped - p.dropped)
        }
    }
    private val counter = Counter()
    private val samples = ArrayDeque<Sample>()
    private val intervals = ArrayDeque<Interval>()
    @Synchronized fun boundary() { counter.reset() }
    @Synchronized fun add(s: Sample) {
        counter.add(s)?.let { intervals.addLast(it) }
        samples.addLast(s)
        while (samples.size > 720) samples.removeFirst()
        while (intervals.size > 720) intervals.removeFirst()
    }
    @Synchronized fun report(): String = buildString {
        appendLine("Desempenho do processo (amostras ~10 s; CPU em equivalentes de um núcleo)")
        if (samples.isEmpty()) appendLine("  ainda não medido")
        samples.groupBy { it.activity }.forEach { (name, group) ->
            val spans = intervals.filter { it.activity == name }
            val ms = spans.sumOf { it.wallMs }
            append("  $name: ${group.size} amostras")
            if (ms > 0) append(String.format(Locale.US, " · CPU %.1f%% · resultados de captura %.1f/s · perdas estimadas %d",
                spans.sumOf { it.cpuMs } * 100.0 / ms, spans.sumOf { it.frames } * 1000.0 / ms, spans.sumOf { it.dropped }))
            else append(" · CPU/cadência ainda sem intervalo comparável")
            group.mapNotNull { it.pssKb }.takeIf { it.isNotEmpty() }?.let {
                append(String.format(Locale.US, " · PSS %.1f–%.1f MiB", it.min() / 1024.0, it.max() / 1024.0))
            }
            append(String.format(Locale.US, " · coleta máx. %.1f ms\n", group.maxOf { it.probeMs }))
        }
        append("GPU: utilização e energia por acelerador não medidas; latência de cópia não equivale a uso de GPU.\n")
        append("Cadência acima vem de CaptureResult; FPS do MP4 é medido separadamente. Sem comparação de autonomia validada.")
    }
}
