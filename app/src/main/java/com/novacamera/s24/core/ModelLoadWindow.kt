package com.novacamera.s24.core

/**
 * Carga de modelo na GPU (profundidade: instância Vulkan + pipelines do ncnn, 2,2–3,3 s no S24) coincide
 * com a prévia parada ao abrir o Retrato: StallLog 0.26.8–0.26.13 "efeito=true análise=true" ~2 s após
 * ligar a câmera, e "prévia sem resultados; reabrindo" em +5 s (2 s de quadros + 3 s do vigia). A
 * escalada do vigia desligava o desfoque e depois Tom/Temperatura na sessão inteira.
 * Hipótese (não confirmada): a GPU fica ocupada pela carga e a prévia GL para de consumir quadros.
 * Durante a carga e por [GRACE_MS] depois, o vigia espera em vez de reabrir (limite [MAX_MS]); conta
 * os segundos sem quadro nesse intervalo para confirmar ou descartar a hipótese no diagnóstico.
 */
object ModelLoadWindow {
    const val GRACE_MS = 2_000L
    const val MAX_MS = 10_000L

    private var label = ""
    private var since = -1L
    private var endedAt = -1L
    private var lastLoadMs = -1L
    private var loads = 0
    private var gapTicks = 0
    private var gapLoads = 0
    private var gapThisLoad = false

    @Synchronized fun begin(what: String, nowMs: Long) { label = what; since = nowMs; endedAt = -1L; gapThisLoad = false; loads++ }

    @Synchronized fun end(nowMs: Long) {
        if (since >= 0) lastLoadMs = nowMs - since
        since = -1L; endedAt = nowMs
    }

    /** Vigia da prévia: aguardar em vez de reabrir a câmera. */
    @Synchronized fun defersStall(nowMs: Long): Boolean =
        (since >= 0 && nowMs - since in 0 until MAX_MS) || (endedAt >= 0 && nowMs - endedAt in 0 until GRACE_MS)

    /** Um tick do vigia (1 s) sem resultado de captura enquanto [defersStall]. */
    @Synchronized fun noteGap() { gapTicks++; if (!gapThisLoad) { gapThisLoad = true; gapLoads++ } }

    @Synchronized fun describe(nowMs: Long): String = when {
        since >= 0 -> "$label carregando há ${nowMs - since} ms"
        endedAt >= 0 -> "última carga ($label, $lastLoadMs ms) terminou há ${nowMs - endedAt} ms"
        else -> "nenhuma carga de modelo nesta execução"
    }

    @Synchronized fun report(): String =
        "Carga de modelos × prévia: $loads cargas · $gapLoads com prévia sem quadros · $gapTicks s sem quadros durante carga/carência (vigia aguardou em vez de reabrir)"

    @Synchronized internal fun resetForTest() { label = ""; since = -1; endedAt = -1; lastLoadMs = -1; loads = 0; gapTicks = 0; gapLoads = 0; gapThisLoad = false }
}
