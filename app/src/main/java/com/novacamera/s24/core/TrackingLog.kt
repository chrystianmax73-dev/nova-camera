package com.novacamera.s24.core

import java.util.Locale

/** Só números e motivos de parada; nenhum quadro/imagem é persistido. */
object TrackingLog {
    private var frames = 0
    private var uncertain = 0
    private var copyTotal = 0.0; private var copyMax = 0.0
    private var cpuTotal = 0.0; private var wallTotal = 0.0
    private var last = "ainda não selecionado"
    private var resolution = ""
    @Synchronized fun frame(copyMs: Double, cpuMs: Double, wallMs: Double, w: Int, h: Int,
        result: SubjectTracker.Result) {
        frames++; if (result.phase != SubjectTracker.Phase.TRACKING) uncertain++
        copyTotal += copyMs; copyMax = maxOf(copyMax, copyMs)
        cpuTotal += cpuMs; wallTotal += wallMs
        resolution = "$w×$h"
        last = "${result.phase} · confiança %.2f".format(Locale.US, result.confidence)
    }
    @Synchronized fun stopped(reason: String) { last = reason }
    @Synchronized fun report() = buildString {
        append("Rastreamento de aparência: $last")
        if (frames > 0) append(String.format(Locale.US,
            "\n  %d análises · %s · incertas/perdidas %d · cópia média/máx %.1f/%.1f ms · análise CPU/parede médias %.1f/%.1f ms",
            frames, resolution, uncertain, copyTotal / frames, copyMax, cpuTotal / frames, wallTotal / frames))
        append("\n  Sem medição de GPU/autonomia. Área acompanhada por correlação, sem classificação ou máscara de profundidade.")
    }
}
