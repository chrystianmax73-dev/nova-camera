package com.novacamera.s24.core

/** Uma mensagem pendente: substituições não acrescentam tarefas à fila do consumidor. */
class LatestPending<T:Any> {
    private var value:T?=null
    private var scheduled=false
    @Synchronized fun offer(next:T):Boolean {
        value=next
        if(scheduled)return false
        scheduled=true;return true
    }
    @Synchronized fun take():T? {
        scheduled=false
        return value.also { value=null }
    }
}
