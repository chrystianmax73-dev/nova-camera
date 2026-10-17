package com.novacamera.s24.core

/** null = consulta indisponível; vazio = sessão recusada. Nunca converte o pedido em outro FPS. */
object VideoSessionSupport {
    fun fps(candidates: List<Int>, ranges: List<Pair<Int, Int>>?): List<Int> =
        if (ranges == null) candidates else candidates.filter { f -> ranges.any { it.first == f && it.second == f } }

    /** Sugestão na mesma resolução. O chamador precisa obter uma escolha explícita do usuário. */
    fun alternative(requested: Int, supported: List<Int>): Int? =
        supported.filter { it < requested && it <= 30 }.maxOrNull()
}
