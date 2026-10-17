package com.novacamera.s24.camera

import android.os.Handler
import java.util.concurrent.Executor

/** A liberação do CameraX enfileira trabalho GL; só criar outro contexto depois desse trabalho. */
object EffectQueueBarrier {
    fun drain(owner:Handler,completion:Executor,close:()->Unit,next:()->Unit) {
        close()
        check(owner.post { completion.execute { next() } }) { "Fila GL encerrada antes de concluir liberação" }
    }
}
