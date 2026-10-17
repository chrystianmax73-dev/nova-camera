package com.novacamera.s24.camera

import android.os.Handler
import android.os.Looper
import android.os.Message

/**
 * Fronteira das tarefas GL do OverlayEffect. CameraX só encaminha ProcessingException ao listener;
 * erros de SurfaceTexture são RuntimeException e escapam dele. Mantém o Looper vivo para executar
 * a liberação enfileirada e devolver superfícies; o controlador desliga o efeito e finaliza o vídeo.
 * Não é um uncaughtExceptionHandler global nem reinicia uma thread GL que já morreu.
 */
internal class VideoEffectHandler(looper: Looper, private val onFailure: (RuntimeException) -> Unit) : Handler(looper) {
    override fun dispatchMessage(msg: Message) {
        try { super.dispatchMessage(msg) } catch (e: RuntimeException) { onFailure(e) }
    }
}
