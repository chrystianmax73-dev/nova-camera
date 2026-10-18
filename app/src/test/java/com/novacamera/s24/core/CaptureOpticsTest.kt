package com.novacamera.s24.core
import org.junit.Assert.*
import org.junit.Test
class CaptureOpticsTest {
    private val lenses=listOf(LensOption("0",1f,.6f,10f,true,focalMm=6.3f),
        LensOption("0",2.73f,1f,8f,false,"6",7.9f,61f),
        LensOption("0",5.12f,1f,8f,false,"7",18.6f,113f))
    private fun sensor(id:String?,f:Float?)=CaptureOptics.Sensor(10,"0",id,f,1200,40_000_000,4,2,2)
    @Test fun solicitarCincoNaoProvaOpticaCinco() {
        val e=CaptureOptics.evaluate(5f,"0",7.9f,69f,sensor("6",7.9f),lenses)
        assertTrue(e.confirmed);assertEquals("6",e.compatibleId);assertEquals(2.73f,e.opticalZoom!!,0f)
        assertEquals(5f/2.73f,e.digitalRatio!!,.001f)
    }
    @Test fun pinSolicitadoSemResultadoNaoViraConfirmacao() {
        val e=CaptureOptics.evaluate(5f,"0@7",18.6f,113f,null,lenses)
        assertFalse(e.confirmed);assertEquals("7",e.compatibleId);assertTrue(e.report().contains("compatível"))
    }
    @Test fun opticaCincoConfirmadaPeloQuadro() {
        val e=CaptureOptics.evaluate(5f,"0@7",18.6f,113f,sensor("7",18.6f),lenses)
        assertTrue(e.confirmed);assertEquals(1f,e.digitalRatio!!,0f)
    }
    @Test fun naoUsaFocalEquivalenteQuePodeConterRecorteComoProva() {
        val e=CaptureOptics.evaluate(5f,"0",null,113f,null,lenses)
        assertNull(e.opticalZoom);assertFalse(e.confirmed)
    }
    @Test fun resultadoEJpegConflitantesNaoConfirmamId() {
        val e=CaptureOptics.evaluate(5f,"0@7",7.9f,69f,sensor("7",18.6f),lenses)
        assertTrue(e.conflict);assertFalse(e.confirmed);assertNull(e.opticalZoom)
    }
    @Test fun idConhecidoSemFocalNoResultadoAindaDeveConferirJpeg() {
        val e=CaptureOptics.evaluate(5f,"0@7",7.9f,69f,sensor("7",null),lenses)
        assertFalse(e.confirmed);assertTrue(e.conflict);assertNull(e.opticalZoom)
    }

}
