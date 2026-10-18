package com.novacamera.s24.ui

import android.app.Activity
import android.app.AlertDialog

/** Confirma apenas o perfil do Retrato; não recebe nem sobrescreve preferências do Vídeo comum. */
object PortraitVideoProfileDialog {
    fun show(activity:Activity,normalProfile:String,onAccept:()->Unit):AlertDialog =
        AlertDialog.Builder(activity).setTitle("Vídeo cinematográfico do Retrato")
            .setMessage("Usar Full HD/30 fps, sem EIS, neste modo? O Vídeo normal manterá $normalProfile e sua estabilização. O efeito exige máscara atual e é experimental nas duas câmeras. Após confirmar, faça o gesto novamente; nenhum arquivo será iniciado por este diálogo.")
            .setNegativeButton("Continuar fotografando",null)
            .setPositiveButton("Usar esse perfil") { _,_ -> if(!activity.isFinishing && !activity.isDestroyed)onAccept() }
            .show()
}
