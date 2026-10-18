package com.novacamera.s24.core
import org.junit.Assert.*
import org.junit.Test
class CinematicPolicyTest {
    @Test fun cinemaSoAbreNaOrigemRetratoSemAlterarPreferenciaDoVideoNormal() {
        assertFalse(CinematicPolicy.enabled(true,false,false))
        assertTrue(CinematicPolicy.enabled(true,true,false))
        assertFalse(CinematicPolicy.enabled(false,true,false))
        assertFalse(CinematicPolicy.enabled(true,true,true))
    }

    @Test fun pausaSemAnaliseNaoExecutaBlurNemImpedeCalibracaoDaPrimeiraMascara() {
        assertEquals(CinematicPolicy.RenderWork(false,false),CinematicPolicy.renderWork(true,true,false,0f,false))
        assertEquals(CinematicPolicy.RenderWork(true,false),CinematicPolicy.renderWork(true,true,false,0f,true))
        assertEquals(CinematicPolicy.RenderWork(true,true),CinematicPolicy.renderWork(true,true,true,0f,false))
        assertEquals(CinematicPolicy.RenderWork(true,true),CinematicPolicy.renderWork(true,true,false,.2f,false))
        assertEquals(CinematicPolicy.RenderWork(false,false),CinematicPolicy.renderWork(false,true,true,1f,true))
        // Retrato fotográfico mantém o caminho anterior, sem prazo da máscara.
        assertEquals(CinematicPolicy.RenderWork(true,true),CinematicPolicy.renderWork(true,false,false,0f,false))
    }

    @Test fun calorNaoPrometeMascaraMesmoQuandoAAnteriorAindaEstaNoGl() {
        assertNotNull(CinematicPolicy.unavailableReason(3,false,true))
        assertNotNull(CinematicPolicy.unavailableReason(2,true,true))
        assertNotNull(CinematicPolicy.unavailableReason(0,false,false))
        assertNull(CinematicPolicy.unavailableReason(2,false,true))
    }

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
