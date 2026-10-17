package com.novacamera.s24.core

import android.os.Handler
import android.os.SystemClock

/** Prepara vídeo com profundidade sem consumir a intenção de captura antes de a máscara chegar. */
class QuickVideoWarmup(private val handler:Handler,private val timeoutMs:Long=8000) {
    private var generation=0L
    private var pending:Runnable?=null
    fun cancel() { generation++;pending?.let(handler::removeCallbacks);pending=null }
    fun await(valid:()->Boolean,ready:()->Boolean,start:()->Unit,expired:()->Unit) {
        cancel();val token=generation;val since=SystemClock.uptimeMillis()
        val tick=object:Runnable {
            override fun run() {
                if(token!=generation)return
                if(!valid()) { cancel();return }
                if(ready()) { pending=null;generation++;start();return }
                if(SystemClock.uptimeMillis()-since>=timeoutMs) { cancel();expired();return }
                handler.postDelayed(this,100)
            }
        }
        pending=tick;handler.post(tick)
    }
}
