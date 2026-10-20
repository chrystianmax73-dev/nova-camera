package com.novacamera.s24.core

import java.util.concurrent.locks.ReentrantLock

/**
 * Uma inferência nativa por vez (MediaPipe/TFLite e ncnn/Vulkan/OpenMP). Os dois fechamentos nativos
 * registrados (Cinema 0.29.2: abort em ImageSegmenter.segment e em DepthEstimator.nativeRun; FOTO 19/10
 * 20:01: SIGABRT com a thread do MediaPipe e a do processamento vivas) aconteceram com dois motores
 * nativos ativos ao mesmo tempo. Trabalho de foto espera a vez (no máximo um bloco da rede, < 0,5 s);
 * análise ao vivo pula o quadro se estiver ocupado. A causa exata ainda é desconhecida: o diagnóstico
 * agora mostra a thread que caiu, o abort e o log ([NativeTombstone]).
 */
object NativeGate {
    private val lock = ReentrantLock()
    @Volatile var current: String? = null
        private set

    @Volatile private var liveMissAt = 0L

    fun <T> run(name: String, block: () -> T): T {
        lock.lock()
        val previous = current
        current = name
        try { return block() } finally {
            current = previous; lock.unlock()
            // Prévia ao vivo pulou quadro há pouco (ex.: Cinema durante a IA em segundo plano): dá a vez a
            // ela antes do próximo bloco, senão a profundidade/máscara congela até a IA terminar.
            if (!lock.isHeldByCurrentThread && System.nanoTime() - liveMissAt < LIVE_WINDOW_NS) Thread.sleep(LIVE_YIELD_MS)
        }
    }

    private const val LIVE_WINDOW_NS = 500_000_000L
    private const val LIVE_YIELD_MS = 120L

    /** Ao vivo: se outro motor nativo estiver rodando, devolve null (pula este quadro). */
    fun <T> tryRun(name: String, block: () -> T): T? {
        if (!lock.tryLock()) { liveMissAt = System.nanoTime(); return null }
        val previous = current
        current = name
        try { return block() } finally { current = previous; lock.unlock() }
    }
}
