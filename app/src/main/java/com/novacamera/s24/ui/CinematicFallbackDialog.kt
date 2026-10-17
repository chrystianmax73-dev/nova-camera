package com.novacamera.s24.ui

import android.app.Activity
import android.app.AlertDialog

/** A retirada do efeito é uma escolha; nunca inicia gravação de um diálogo tardio. */
object CinematicFallbackDialog {
    fun show(activity:Activity,reason:String,onCommon:()->Unit):AlertDialog =
        AlertDialog.Builder(activity).setTitle("Cinematográfico indisponível")
            .setMessage("$reason.\n\nVídeo comum mantém a resolução e os FPS selecionados. A opção cinematográfica ficará guardada para a próxima sessão.")
            .setNegativeButton("Aguardar",null)
            .setPositiveButton("Usar vídeo comum") { _,_ ->
                if(!activity.isFinishing && !activity.isDestroyed) onCommon()
            }.show()
}
