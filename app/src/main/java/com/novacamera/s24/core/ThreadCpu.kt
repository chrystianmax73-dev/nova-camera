package com.novacamera.s24.core

import java.io.File
import java.util.Locale

/**
 * CPU do processo por thread (agrupada pelo nome), para saber QUEM consome na prévia — o total do
 * processo sozinho (ex.: 155% no Retrato parado) não diz se é a câmera, a interface, a análise ou um
 * auxílio de visor. Lê /proc/self/task/<tid>/stat (utime + stime, em ticks do relógio do kernel).
 */
object ThreadCpu {
    /** Ticks por segundo do kernel (USER_HZ): 100 no Android. */
    const val HZ = 100.0

    /** (nome, ticks de CPU) de uma linha de /proc/.../stat; null se ilegível. */
    fun parse(stat: String): Pair<String, Long>? {
        val open = stat.indexOf('('); val close = stat.lastIndexOf(')')
        if (open < 0 || close < open) return null
        val name = stat.substring(open + 1, close)
        val f = stat.substring(close + 2).split(' ')
        // Depois do ")": estado(3) … utime(14) stime(15) → índices 11 e 12.
        val u = f.getOrNull(11)?.toLongOrNull() ?: return null
        val s = f.getOrNull(12)?.toLongOrNull() ?: return null
        return name to (u + s)
    }

    /** Agrupa threads do mesmo tipo: "nova-proc-3" → "nova-proc-#", "Binder:123_4" → "Binder:#_#". */
    fun group(name: String): String = name.replace(Regex("\\d+"), "#")

    private var last: Map<Int, Pair<String, Long>>? = null
    private var lastAtMs = 0L
    private var lastActivity: String? = null
    /** atividade → (grupo → ticks), e tempo total medido por atividade. */
    private val ticks = HashMap<String, HashMap<String, Long>>()
    private val wall = HashMap<String, Long>()

    fun snapshot(dir: File = File("/proc/self/task")): Map<Int, Pair<String, Long>> =
        (dir.listFiles() ?: emptyArray()).mapNotNull { t ->
            val tid = t.name.toIntOrNull() ?: return@mapNotNull null
            val p = runCatching { File(t, "stat").readText() }.getOrNull()?.let(::parse) ?: return@mapNotNull null
            tid to p
        }.toMap()

    /** Nova amostra; o intervalo desde a anterior conta para [activity] (descartado se a atividade mudou). */
    @Synchronized fun add(activity: String, atMs: Long, now: Map<Int, Pair<String, Long>>) {
        val prev = last; val prevAt = lastAtMs; val prevActivity = lastActivity
        last = now; lastAtMs = atMs; lastActivity = activity
        if (prev == null || prevActivity != activity || atMs - prevAt !in 1L..30_000L) return
        val byGroup = ticks.getOrPut(activity) { HashMap() }
        for ((tid, cur) in now) {
            val before = prev[tid]?.second ?: 0L // thread nova no intervalo: conta tudo dela
            val d = cur.second - before
            if (d > 0) byGroup.merge(group(cur.first), d, Long::plus)
        }
        wall.merge(activity, atMs - prevAt, Long::plus)
    }

    @Synchronized fun boundary() { last = null }

    @Synchronized fun report(top: Int = 10): String = buildString {
        appendLine("CPU por thread (equivalentes de um núcleo; agrupadas pelo nome)")
        if (wall.isEmpty()) { appendLine("  ainda não medido"); return@buildString }
        for ((activity, ms) in wall) {
            val g = ticks[activity] ?: continue
            val total = g.values.sum() / HZ * 100_000.0 / ms
            appendLine(String.format(Locale.US, "  %s (%.0f s): total %.1f%%", activity, ms / 1000.0, total))
            g.entries.sortedByDescending { it.value }.take(top).forEach { (name, t) ->
                appendLine(String.format(Locale.US, "    %-28s %.1f%%", name, t / HZ * 100_000.0 / ms))
            }
        }
    }

    @Synchronized internal fun resetForTest() { last = null; ticks.clear(); wall.clear() }
}
