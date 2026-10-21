package com.novacamera.s24.core

/**
 * Pausa do efeito da prévia (Tom, Temperatura, Desfoque) depois de uma falha — sem desligar para o resto da
 * sessão. Bug relatado pelo dono (20/10): os controles sumiam e só voltavam saindo e entrando no app, porque
 * uma única falha do efeito (troca de lente/superfície) ou duas paradas da prévia marcavam "falhou" para
 * sempre. Agora: 1ª falha pausa [FIRST_MS], 2ª [SECOND_MS], 3ª ou mais dentro de [WINDOW_MS] pausa [LONG_MS];
 * falhas mais antigas que [WINDOW_MS] são esquecidas. Depois da pausa o efeito volta sozinho.
 */
class EffectBreaker {
    companion object {
        const val FIRST_MS = 4_000L
        const val SECOND_MS = 20_000L
        const val LONG_MS = 120_000L
        const val WINDOW_MS = 180_000L
    }
    private val failures = ArrayDeque<Long>()
    private var until = Long.MIN_VALUE

    fun blocked(nowMs: Long) = nowMs < until

    /** Registra uma falha e devolve quanto tempo o efeito fica pausado. */
    fun fail(nowMs: Long): Long {
        while (failures.isNotEmpty() && nowMs - failures.first() > WINDOW_MS) failures.removeFirst()
        failures.addLast(nowMs)
        val wait = when (failures.size) { 1 -> FIRST_MS; 2 -> SECOND_MS; else -> LONG_MS }
        until = nowMs + wait
        return wait
    }

    fun reset() { failures.clear(); until = Long.MIN_VALUE }
}
