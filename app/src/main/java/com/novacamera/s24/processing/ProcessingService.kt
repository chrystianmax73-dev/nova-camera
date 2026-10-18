package com.novacamera.s24.processing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Mantém o processo vivo enquanto há foto na fila (serviço curto em primeiro plano, Android 14+).
 * Sem ele, ao abrir a galeria durante os 5–10 s do processamento o app vai para segundo plano com
 * ~500 MB e é forte candidato a ser encerrado pelo sistema — e a foto não chegava à galeria.
 * Não faz trabalho próprio: só sinaliza ao Android que há uma tarefa curta do usuário em andamento.
 * O tempo do "short service" (~3 min) cobre a fila; ao esgotar, o serviço sai e a recuperação na
 * próxima abertura ([ProcessingQueue.recoverInterrupted]) cobre o resto.
 */
class ProcessingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT < 34) { stopSelf(); return START_NOT_STICKY }
        val ok = runCatching {
            startForeground(ID, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        }.onFailure { Log.w(TAG, "primeiro plano recusado", it) }.isSuccess
        if (!ok || ProcessingQueue.pending == 0) stop()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) = stop()

    private fun stop() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    companion object {
        private const val TAG = "NovaProcService"
        private const val ID = 4107
        private const val CHANNEL = "processing"

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT < 34) return
            runCatching { context.startForegroundService(Intent(context, ProcessingService::class.java)) }
                .onFailure { Log.w(TAG, "não foi possível iniciar", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ProcessingService::class.java)) }
        }

        private fun notification(context: Context): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Processamento de fotos", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Aparece só enquanto uma foto termina de ser processada"
                    setShowBadge(false)
                })
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(com.novacamera.s24.R.drawable.ic_nova_foreground)
                .setContentTitle("Finalizando foto")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .build()
        }
    }
}
