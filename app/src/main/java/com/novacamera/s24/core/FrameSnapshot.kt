package com.novacamera.s24.core

/** A inferência só usa a cópia: nunca mantém um buffer do produtor durante trabalho pesado. */
object FrameSnapshot {
    inline fun <T> readAndClose(source:AutoCloseable,read:()->T):T = try { read() } finally { source.close() }
}

/** Limita a ocupação de profundidade pelo tempo realmente gasto, inclusive carga do modelo.
 * Não altera resolução/FPS da gravação nem permite reutilizar máscara inválida.
 */
class DepthDutyBudget {
    private var next=0L
    var lastCostMs=0L;private set
    fun allowed(now:Long,interval:Long,lastStart:Long)=now>=next && now-lastStart>=interval
    fun completed(start:Long,end:Long) { lastCostMs=(end-start).coerceAtLeast(0);next=end+lastCostMs*3 }
}
