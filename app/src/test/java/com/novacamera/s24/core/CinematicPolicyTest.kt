package com.novacamera.s24.core
import org.junit.Assert.*
import org.junit.Test
class CinematicPolicyTest {
    @Test fun perfilNaoReduzQualidadeSilenciosamente() {
        assertTrue(CinematicPolicy.profile(1080,1920,30))
        assertFalse(CinematicPolicy.profile(1080,1920,30,true))
        assertFalse(CinematicPolicy.profile(3840,2160,60))
        assertFalse(CinematicPolicy.profile(1920,1080,60))
        assertFalse(CinematicPolicy.profile(1280,720,30))
    }
    @Test fun mascaraAntigaOuFuturaNaoEAtual() {
        assertTrue(CinematicPolicy.fresh(1200,1000));assertFalse(CinematicPolicy.fresh(1750,1000))
        assertFalse(CinematicPolicy.fresh(900,1000));assertFalse(CinematicPolicy.fresh(100,0))
    }
    @Test fun movimentoESelecaoInvalidamMascaraMasNaoPequenoRuido() {
        val g=CinematicSceneGuard()
        assertFalse(g.moved(FloatArray(20){.2f},null))
        assertFalse(g.moved(FloatArray(20){.21f},null))
        assertTrue(g.moved(FloatArray(20){.6f},null))
        assertTrue(g.moved(FloatArray(20){.6f},.5f to .5f))
        assertFalse(g.moved(FloatArray(20){.6f},.51f to .5f))
        assertTrue(g.moved(FloatArray(20){.6f},.8f to .5f))
    }
}
