package com.novacamera.s24.core

/** Limites de custo da IA opcional. Não são benchmarks de qualidade ou tempo no S24 Ultra. */
object DenoisePolicy {
    const val IDLE_MS = 60_000L
    const val TOTAL_BUDGET_MS = 8_000L
    const val INFERENCE_BUDGET_MS = 6_000L
    const val CPU_MAX_PIXELS = 1_000_000L

    fun allowed(heat: DeviceHeat?) = heat == null ||
        (heat.thermalStatus < ThermalPolicy.MODERATE && !heat.powerSave)

    fun canRun(pixels: Long, gpu: Boolean, heapHeadroom: Long): Boolean =
        pixels > 0 && (gpu || pixels <= CPU_MAX_PIXELS) && heapHeadroom >= pixels * 8L + 16L * 1024 * 1024

    /** A primeira chamada inclui preparação de pipelines; projeção conservadora, sem downscale. */
    fun projectedFits(firstTileMs: Long, tiles: Int, loadMs: Long): Boolean =
        firstTileMs >= 0 && tiles > 0 && loadMs < TOTAL_BUDGET_MS &&
            firstTileMs.toDouble() * tiles <= minOf(INFERENCE_BUDGET_MS, TOTAL_BUDGET_MS - loadMs)

    fun expired(elapsedTotalMs: Long, elapsedInferenceMs: Long): Boolean =
        elapsedTotalMs >= TOTAL_BUDGET_MS || elapsedInferenceMs >= INFERENCE_BUDGET_MS
}
