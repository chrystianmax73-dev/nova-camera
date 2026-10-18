package com.novacamera.s24.core

/** Janela limitada da captura JPEG: uma pausa legítima da prévia não desmonta a rajada.
 * Não é sinal de vida do sensor. Pedidos seguintes nunca prolongam o limite total. */
class StillCaptureLease {
    private var start:Long?=null
    private var requested=0L
    private var idleLimit=10_000L
    private var totalLimit=30_000L
    @Synchronized fun begin(now:Long, exposureMs:Long=0, frames:Int=8) {
        start=now;requested=now
        idleLimit=maxOf(10_000L,exposureMs+3_000L)
        totalLimit=maxOf(30_000L,(exposureMs+2_000L)*frames).coerceAtMost(180_000L)
    }
    @Synchronized fun request(now:Long) { if(start!=null)requested=now }
    @Synchronized fun end() { start=null }
    @Synchronized fun defersRecovery(now:Long):Boolean {
        val at=start ?: return false
        return now>=at && now-at<totalLimit && now-requested<idleLimit
    }
}
