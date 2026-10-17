package com.novacamera.s24.core
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@LooperMode(LooperMode.Mode.PAUSED)
class QuickVideoWarmupTest {
    @Test fun aguardaMascaraEIniciaUmaUnicaVez() {
        val w=QuickVideoWarmup(Handler(Looper.getMainLooper()));var ready=false;var starts=0
        w.await({true},{ready},{starts++},{fail("não deveria expirar")})
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));assertEquals(0,starts)
        ready=true;shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));assertEquals(1,starts)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals(1,starts)
    }
    @Test fun soltarOuTrocarSessaoDurantePreparacaoNaoIniciaVideoTardio() {
        val w=QuickVideoWarmup(Handler(Looper.getMainLooper()));var held=true;var ready=false;var starts=0
        w.await({held},{ready},{starts++},{fail()})
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        held=false;ready=true;shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(0,starts)
        w.await({true},{false},{fail("sessão antiga")},{fail()})
        w.await({true},{true},{starts++},{fail()})
        shadowOf(Looper.getMainLooper()).idle();assertEquals(1,starts)
    }
    @Test fun ausenciaDeMascaraExpiraSemLoopOuArquivo() {
        val w=QuickVideoWarmup(Handler(Looper.getMainLooper()),500);var expired=0
        w.await({true},{false},{fail("não pode gravar")},{expired++})
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertEquals(1,expired)
    }
}
