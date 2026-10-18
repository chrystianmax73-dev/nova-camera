package com.novacamera.s24.processing

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tarefas independentes do processamento em vários núcleos, com resultado na ordem dos índices
 * (a saída é a mesma de um núcleo só). [workers] = 1 roda tudo na própria thread, sem o pool.
 */
object Parallel {
    /** Núcleos permitidos agora (definido pela fila a cada foto, conforme calor e economia). */
    @Volatile var workers = 1

    private val created = AtomicInteger()
    private val pool by lazy {
        Executors.newFixedThreadPool(com.novacamera.s24.core.Workers.MAX) { r ->
            Thread(r, "nova-proc-${created.incrementAndGet()}").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }
    }

    fun <T> map(n: Int, block: (Int) -> T): List<T> {
        val w = minOf(workers, n)
        if (w <= 1) return List(n) { block(it) }
        val out = arrayOfNulls<Any?>(n)
        val next = AtomicInteger()
        val tasks = List(w) {
            Callable {
                while (true) {
                    val i = next.getAndIncrement()
                    if (i >= n) break
                    out[i] = block(i)
                }
            }
        }
        try {
            pool.invokeAll(tasks).forEach { it.get() }
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
        @Suppress("UNCHECKED_CAST")
        return out.toList() as List<T>
    }
}
