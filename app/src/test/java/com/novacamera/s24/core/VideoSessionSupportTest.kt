package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class VideoSessionSupportTest {
    @Test fun uhdRecusadoA60NaoViraFullHdNemTrintaSilenciosamente() {
        val options = VideoSessionSupport.fps(listOf(24, 30, 60), listOf(24 to 24, 30 to 30))
        assertFalse(60 in options)
        assertEquals(30, VideoSessionSupport.alternative(60, options))
        assertNull(VideoSessionSupport.alternative(30, listOf(30)))
    }

    @Test fun consultaIndisponivelNaoEquivaleASessaoInvalida() {
        assertEquals(listOf(30, 60), VideoSessionSupport.fps(listOf(30, 60), null))
        assertTrue(VideoSessionSupport.fps(listOf(30, 60), emptyList()).isEmpty())
        assertNull(VideoSessionSupport.alternative(60, emptyList()))
    }

    @Test fun faixaVariavelNaoPrometeSessentaFixosENaoIgnoraLimiteDoCodec() {
        assertEquals(listOf(30), VideoSessionSupport.fps(listOf(30, 60), listOf(30 to 30, 15 to 60)))
        assertEquals(listOf(30), VideoSessionSupport.fps(listOf(30), listOf(30 to 30, 60 to 60)))
    }
}
