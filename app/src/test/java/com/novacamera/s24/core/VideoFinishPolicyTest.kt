package com.novacamera.s24.core
import org.junit.Assert.*
import org.junit.Test
class VideoFinishPolicyTest {
    @Test fun sombrasTemLimiteEAltasLuzesNaoSaoCortadas() {
        val m=VideoFinishPolicy.matrix(VideoFinishPolicy.lift(.05))
        assertEquals(1f,m[0]+m[12],.00001f)
        assertTrue(m[12] in 0f..0.003f)
        assertEquals(0f,VideoFinishPolicy.lift(.4),0f)
        for(x in 0..255) assertTrue(m[0]*(x/255f)+m[12] in 0f..1.000001f)
    }
    @Test fun quadrosFaltantesDuplicadosOuCadenciaMudadaSaoRecusados() {
        val a=longArrayOf(0,16667,33333,50000)
        assertTrue(VideoFinishPolicy.timeline(a,longArrayOf(1000,17667,34333,51000)))
        assertFalse(VideoFinishPolicy.timeline(a,longArrayOf(0,16667,50000)))
        assertFalse(VideoFinishPolicy.timeline(a,longArrayOf(0,33333,66667,100000)))
        assertFalse(VideoFinishPolicy.timeline(a,longArrayOf(0,16667,16667,50000)))
    }
    @Test fun aceitaQuadrosBComPTSReordenadoMasNaoAudioForaDeSincronia() {
        assertTrue(VideoFinishPolicy.timeline(longArrayOf(0,33333,16667),longArrayOf(1000,17667,34333)))
        assertTrue(VideoFinishPolicy.sync(0,-21000,1000,-20000))
        assertFalse(VideoFinishPolicy.sync(0,-21000,1000,0))
        assertEquals(1080 to 1920,VideoFinishPolicy.displayed(1920,1080,90))
    }
}
