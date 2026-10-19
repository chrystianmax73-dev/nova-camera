package com.novacamera.s24.core
import org.junit.Assert.*
import org.junit.Test
class CinematicPolicyTest {
    @Test fun falhaOuAusenciaDoCompositorNaoIniciaRetratoComoVideoComumSilenciosamente() {
        assertFalse(CinematicPolicy.canStart(true,false,true))
        assertFalse(CinematicPolicy.canStart(true,true,false))
        assertTrue(CinematicPolicy.canStart(true,true,true))
        assertTrue(CinematicPolicy.canStart(false,false,false))
    }

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
        // Sem máscara no Retrato fotográfico, uOn=0: blur seria descartado pelo shader.
        assertEquals(CinematicPolicy.RenderWork(false,false),CinematicPolicy.renderWork(true,false,false,0f,false))
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
        assertTrue("troca de assunto com cena parada mantém a profundidade", g.selectionOnly)
        assertTrue(g.moved(FloatArray(20){.1f},.8f to .5f))
        assertFalse(g.selectionOnly)
    }
    @Test fun tremorNaTeleNaoDescartaProfundidadeMasTrocaDeCenaSim() {
        val g=CinematicSceneGuard()
        g.moved(FloatArray(20){.30f},null)
        assertTrue("tremor ainda conta como movimento",g.moved(FloatArray(20){.38f},null))
        assertFalse("tremor (0,08) não é outra cena",g.sceneCut)
        assertTrue(g.moved(FloatArray(20){.70f},null))
        assertTrue("troca de cena (0,32) descarta a profundidade",g.sceneCut)
    }
    @Test fun pistasDoTombstoneNativo() {
        val bytes="\u0000\u0008SIGSEGV\u0012\u0003abc\u001anova-cine-depth\u0000libncnn.so\u0001ncnn::VkCompute::submit_and_wait\u0002hello world".toByteArray(Charsets.ISO_8859_1)
        val hints=NativeCrashHints.extract(bytes)
        assertEquals(listOf("SIGSEGV","nova-cine-depth","libncnn.so","ncnn::VkCompute::submit_and_wait"),hints)
    }
    @Test fun figuraPequenaNaoViraAssuntoDoVideo() {
        assertFalse("pôster/TV a 4% do quadro não desfoca o objeto real",CinematicPolicy.personSubject(.04f,false))
        assertTrue("pessoa em plano médio",CinematicPolicy.personSubject(.2f,false))
        assertTrue("toque na pessoa vale mesmo pequena",CinematicPolicy.personSubject(.04f,true))
    }
}
