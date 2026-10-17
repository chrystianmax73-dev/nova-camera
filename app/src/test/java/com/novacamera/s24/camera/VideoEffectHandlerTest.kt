package com.novacamera.s24.camera

import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class VideoEffectHandlerTest {
    @Test fun falhaDeTexturaEntregaErroEPermiteLiberarAsSuperficiesNaMesmaFila() {
        val failure = IllegalStateException("Unable to update texture contents")
        var received: RuntimeException? = null
        var released = false
        val handler = VideoEffectHandler(Looper.getMainLooper()) { received = it }
        handler.post { throw failure }
        handler.post { released = true }
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(failure, received)
        assertTrue(released)
    }
}
