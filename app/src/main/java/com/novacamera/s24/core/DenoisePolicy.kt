package com.novacamera.s24.core

/** Limites de custo da IA opcional. Não são benchmarks de qualidade ou tempo no S24 Ultra. */
object DenoisePolicy {
    const val IDLE_MS = 60_000L
    const val TOTAL_BUDGET_MS = 8_000L
    const val INFERENCE_BUDGET_MS = 6_000L
    const val CPU_MAX_PIXELS = 1_000_000L

    /** Limites de uma execução: o primeiro plano bloqueia a foto; o segundo plano não. */
    data class Budget(val totalMs: Long, val inferenceMs: Long)
    val FOREGROUND = Budget(TOTAL_BUDGET_MS, INFERENCE_BUDGET_MS)
    /**
     * Depois que a foto já está na galeria: uma foto de 12 MP tem ~190 blocos e o custo estimado
     * na GPU é de dezenas de ms por bloco, o que não cabe em 6 s. Térmica e bateria continuam
     * interrompendo a qualquer momento.
     */
    val BACKGROUND = Budget(60_000L, 50_000L)

    /** Prévia da IA só na região ampliada (até 16 blocos), com a revisão aberta esperando. */
    val REGION = Budget(20_000L, 18_000L)
    /**
     * Foto inteira pedida na revisão: o usuário escolheu esperar (~190 blocos em 12 MP, minutos).
     * Térmica, economia de bateria e bateria baixa continuam interrompendo.
     */
    val MANUAL = Budget(300_000L, 280_000L)

    /** Bateria baixa fora do carregador: a IA opcional espera (a foto já está pronta na galeria). */
    const val LOW_BATTERY = 15

    fun allowed(heat: DeviceHeat?) = heat == null ||
        (heat.thermalStatus < ThermalPolicy.MODERATE && !heat.powerSave &&
            (heat.charging || heat.batteryPercent < 0 || heat.batteryPercent > LOW_BATTERY))

    /** Memória extra: ~3 faixas de 256 linhas ([DenoiseBands]), não uma cópia da foto. */
    fun canRun(pixels: Long, gpu: Boolean, heapHeadroom: Long,
               width: Int = kotlin.math.sqrt(pixels * 4.0 / 3.0).toInt()): Boolean =
        pixels > 0 && (gpu || pixels <= CPU_MAX_PIXELS) && heapHeadroom >= 3L * 256 * width * 4 + 16L * 1024 * 1024

    /** A primeira chamada inclui preparação de pipelines; projeção conservadora, sem downscale. */
    fun projectedFits(firstTileMs: Long, tiles: Int, loadMs: Long, budget: Budget = FOREGROUND): Boolean =
        firstTileMs >= 0 && tiles > 0 && loadMs < budget.totalMs &&
            firstTileMs.toDouble() * tiles <= minOf(budget.inferenceMs, budget.totalMs - loadMs)

    fun expired(elapsedTotalMs: Long, elapsedInferenceMs: Long, budget: Budget = FOREGROUND): Boolean =
        elapsedTotalMs >= budget.totalMs || elapsedInferenceMs >= budget.inferenceMs

    /** Aquecimento já pago; só o trabalho restante usa o custo dos blocos seguintes. */
    fun remainingFits(
        elapsedTotalMs: Long, elapsedInferenceMs: Long, steadyTileMs: Long, remainingTiles: Int, budget: Budget = FOREGROUND,
    ): Boolean =
        elapsedTotalMs >= 0 && elapsedInferenceMs >= 0 && steadyTileMs >= 0 && remainingTiles >= 0 &&
            !expired(elapsedTotalMs, elapsedInferenceMs, budget) &&
            steadyTileMs.toDouble() * remainingTiles <=
                minOf(budget.totalMs - elapsedTotalMs, budget.inferenceMs - elapsedInferenceMs)
}
