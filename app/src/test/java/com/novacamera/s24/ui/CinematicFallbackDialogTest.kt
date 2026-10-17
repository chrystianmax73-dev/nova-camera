package com.novacamera.s24.ui

import android.app.Activity
import android.app.AlertDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
class CinematicFallbackDialogTest {
    @Test fun aguardarNaoRemoveEfeitoEEscolhaPositivaNaoIniciaArquivo() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        var choices=0
        val dialog=CinematicFallbackDialog.show(activity,"Aparelho quente") { choices++ }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0,choices)
        val next=CinematicFallbackDialog.show(activity,"Sem máscara") { choices++ }
        next.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,choices)
    }
    @Test fun escolhaAtrasadaDepoisDeDestruirTelaNaoReconfiguraCamera() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        var choices=0
        val dialog=CinematicFallbackDialog.show(controller.get(),"Aparelho quente") { choices++ }
        controller.pause().stop().destroy()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0,choices)
    }
}
