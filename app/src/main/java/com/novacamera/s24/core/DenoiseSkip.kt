package com.novacamera.s24.core

/**
 * A IA de ruído decide se cabe no orçamento só depois de carregar o modelo e rodar 3 blocos.
 * Diagnóstico 0.26.13 (12 MP, segundo plano): carga 2,2 s + 3 blocos, projeção 192 × 353 ms ≈ 68 s
 * > 60 s → desiste em toda foto, com o mesmo resultado (foto sem IA). Depois de uma projeção que não
 * coube, o mesmo tamanho com o mesmo orçamento é dispensado sem carregar por [HOLD_MS]; depois disso
 * mede de novo (o aparelho frio pode caber).
 */
class DenoiseSkip {
    companion object { const val HOLD_MS = 30 * 60_000L }

    private var pixels = -1L
    private var budget: DenoisePolicy.Budget? = null
    private var atMs = 0L

    @Synchronized fun recordOverBudget(pixels: Long, budget: DenoisePolicy.Budget, nowMs: Long) {
        this.pixels = pixels; this.budget = budget; atMs = nowMs
    }

    /** Há quanto tempo a projeção não coube para este tamanho/orçamento; null = tentar. */
    @Synchronized fun skippedSince(pixels: Long, budget: DenoisePolicy.Budget, nowMs: Long): Long? =
        if (pixels == this.pixels && budget == this.budget && nowMs - atMs in 0 until HOLD_MS) nowMs - atMs else null
}
