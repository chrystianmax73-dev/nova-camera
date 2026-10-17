package com.novacamera.s24.core

/**
 * Efeitos GL que usam a mesma fila devem liberar o contexto anterior ANTES de inicializar o novo.
 * close() e a fábrica enfileiram trabalho na mesma thread; inverter a ordem invalida o contexto
 * do efeito novo. Chamado somente pela thread de controle da câmera.
 */
class SerialEffectSlot<T : AutoCloseable> {
    var current: T? = null
        private set

    fun replace(create: () -> T?): T? {
        close()
        return create().also { current = it }
    }

    fun close() {
        val old = current
        current = null
        old?.close()
    }
}
