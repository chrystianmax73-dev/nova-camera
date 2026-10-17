package com.novacamera.s24.camera
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.Executor
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@LooperMode(LooperMode.Mode.PAUSED)
class EffectQueueBarrierTest {
    @Test fun proximoContextoNaoNasceAntesDoCleanupEnfileiradoPeloCameraX() {
        val owner=Handler(Looper.getMainLooper())
        val order=mutableListOf<String>();val main=mutableListOf<Runnable>()
        EffectQueueBarrier.drain(owner,Executor { main+=it },{
            order+="close solicitado";owner.post { order+="EGL liberado" }
        },{order+="próximo contexto"})
        assertEquals(listOf("close solicitado"),order);assertTrue(main.isEmpty())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("close solicitado","EGL liberado"),order);assertEquals(1,main.size)
        main.single().run()
        assertEquals(listOf("close solicitado","EGL liberado","próximo contexto"),order)
    }
    @Test fun slotJaFechadoAindaAguardaCleanupPendenteNaFilaAntiga() {
        val owner=Handler(Looper.getMainLooper())
        val order=mutableListOf<String>();val ready=mutableListOf<Runnable>()
        owner.post { order+="cleanup anterior" }
        EffectQueueBarrier.drain(owner,Executor { ready+=it },{}, { order+="novo renderizador" })
        assertTrue(ready.isEmpty())
        shadowOf(Looper.getMainLooper()).idle();ready.single().run()
        assertEquals(listOf("cleanup anterior","novo renderizador"),order)
    }

}
