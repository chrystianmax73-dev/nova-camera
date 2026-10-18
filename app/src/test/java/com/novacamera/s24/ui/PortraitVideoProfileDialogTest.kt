package com.novacamera.s24.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import com.novacamera.s24.settings.NovaSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PortraitVideoProfileDialogTest {
    @Test fun confirmarSoGuardaEscolhaSemAlterarPerfilDoVideoNormal() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val settings=NovaSettings(activity)
        settings.videoFps=60;settings.videoStabilization=true;settings.portraitVideoProfileAccepted=false
        val oldQuality=settings.videoQuality
        val dialog=PortraitVideoProfileDialog.show(activity,"4K/60 fps") { settings.portraitVideoProfileAccepted=true }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        assertFalse(settings.portraitVideoProfileAccepted)
        val next=PortraitVideoProfileDialog.show(activity,"4K/60 fps") { settings.portraitVideoProfileAccepted=true }
        next.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        assertTrue(settings.portraitVideoProfileAccepted)
        assertEquals(60,settings.videoFps);assertTrue(settings.videoStabilization);assertEquals(oldQuality,settings.videoQuality)
    }
    @Test fun dialogoDeTelaDestruidaNaoAceitaConfiguracaoTardia() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        var accepted=false
        val dialog=PortraitVideoProfileDialog.show(controller.get(),"4K/60 fps") { accepted=true }
        controller.pause().stop().destroy()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        assertFalse(accepted)
    }
}
