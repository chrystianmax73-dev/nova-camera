package com.novacamera.s24.core

import java.util.Locale

/**
 * CPU da análise ao vivo do Retrato por etapa (tempo de CPU da própria thread, não parede), somado
 * enquanto a câmera está aberta. O bloco "CPU por thread" mostra o total da thread; aqui aparece qual
 * etapa pesa. Diferença entre o total da thread e a soma das etapas = threads auxiliares (ex.: OpenMP
 * da profundidade, que herdam o nome da thread que as criou).
 */
object LiveAnalysisCpu {
    enum class Stage(val label: String) { COPY("cópia/escala/giro"), SEGMENT("pessoa (segmentação)"), DEPTH("profundidade"), MASK("plano/suavização/envio") }

    private val cpu = LongArray(Stage.values().size)
    private val wall = LongArray(Stage.values().size)
    private val runs = IntArray(Stage.values().size)
    private var frames = 0
    private var spanStart = -1L
    private var spanEnd = 0L

    @Synchronized fun add(stage: Stage, cpuMs: Long, wallMs: Long) {
        cpu[stage.ordinal] += cpuMs.coerceAtLeast(0); wall[stage.ordinal] += wallMs.coerceAtLeast(0); runs[stage.ordinal]++
    }

    @Synchronized fun frame(nowMs: Long) { frames++; if (spanStart < 0L) spanStart = nowMs; spanEnd = nowMs }

    @Synchronized fun report(): String = buildString {
        appendLine("Análise ao vivo do Retrato — CPU por etapa (thread da análise)")
        val span = spanEnd - spanStart
        if (frames == 0 || span <= 0) { appendLine("  ainda não medida"); return@buildString }
        appendLine(String.format(Locale.US, "  %d quadros analisados em %.0f s (%.1f/s)", frames, span / 1000.0, frames * 1000.0 / span))
        for (s in Stage.values()) {
            val n = runs[s.ordinal]; if (n == 0) continue
            appendLine(String.format(Locale.US, "    %-26s %.1f%% de um núcleo · %d execuções · CPU média %.1f ms · parede média %.1f ms",
                s.label, cpu[s.ordinal] * 100.0 / span, n, cpu[s.ordinal].toDouble() / n, wall[s.ordinal].toDouble() / n))
        }
    }

    @Synchronized internal fun resetForTest() { cpu.fill(0); wall.fill(0); runs.fill(0); frames = 0; spanStart = -1; spanEnd = 0 }
}
