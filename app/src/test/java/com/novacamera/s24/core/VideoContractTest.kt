package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class VideoContractTest {
    private val uhd = VideoContract.Size(3840, 2160)
    private val request = VideoContract.Request(uhd, 60, false)

    @Test fun denunciaFhdMesmoComRotacaoEAlvoDeSessenta() {
        assertNotNull(VideoContract.violation(request, VideoContract.Size(1080, 1920), 60.0))
        assertNull(VideoContract.violation(request, VideoContract.Size(2160, 3840), 59.94))
    }
    @Test fun denunciaTrintaQuandoSelecionadoSessentaSemConfiarNaTaxaNominal() {
        assertNotNull(VideoContract.violation(request, uhd, 30.0))
        assertNotNull(VideoContract.violation(request, uhd, null))
        assertNull(VideoContract.violation(request.copy(fps = 30, variableFps = true), uhd, 15.0))
    }
    @Test fun taxaRealUsaTodasAsAmostrasMesmoComQuadrosBForaDeOrdem() {
        val c = VideoContract.Cadence()
        listOf(0L, 100_000L, 33_333L, 66_667L, 133_333L).forEach(c::sample)
        assertEquals(30.0, c.fps!!, 0.01)
    }
    @Test fun videoVazioOuCurtoNaoInventaFps() {
        val c = VideoContract.Cadence()
        c.sample(-1)
        assertNull(c.fps)
        c.sample(0)
        assertNull(c.fps)
        c.sample(0)
        assertNull(c.fps)
    }
}
