package com.novacamera.s24.core

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class SubjectTrackerTest {
    private val w = 240
    private val h = 180
    private fun frame(dx: Int = 0, dy: Int = 0, light: Int = 0): ByteArray = ByteArray(w*h) { i ->
        val x = i % w - dx; val y = i / w - dy
        val v = if (x in 78..142 && y in 55..125)
            122 + 31*sin(x*.31+y*.13) + 28*cos(y*.27-x*.11) + 17*sin(x*.17-y*.37)
        else 90.0
        (v + light).roundToInt().coerceIn(0,255).toByte()
    }
    private fun selected(t: SubjectTracker) = t.select(frame(), w, h, 110f/(w-1), 90f/(h-1), 1000)

    @Test fun acompanhaTranslacaoRealComMudancaDeIluminacao() {
        val t = SubjectTracker()
        assertEquals(SubjectTracker.Phase.TRACKING, selected(t).phase)
        for (k in 1..6) {
            val r = t.update(frame(k*6,k*2,18), w,h,1000L+k*200)
            assertEquals("quadro $k",SubjectTracker.Phase.TRACKING,r.phase)
            assertEquals((110+k*6).toDouble(),r.x*(w-1.0),4.0)
            assertEquals((90+k*2).toDouble(),r.y*(h-1.0),3.0)
        }
        assertTrue(t.result.x > .58f) // não é um ponto de AF imóvel.
    }
    @Test fun oclusaoSuspendeAtualizacoesEEncerraSemInventarAlvo() {
        val t = SubjectTracker(); selected(t)
        val blank = ByteArray(w*h) { 90 }
        assertEquals(SubjectTracker.Phase.UNCERTAIN,t.update(blank,w,h,1200).phase)
        assertEquals(SubjectTracker.Phase.UNCERTAIN,t.update(blank,w,h,1600).phase)
        assertEquals(SubjectTracker.Phase.UNCERTAIN,t.update(blank,w,h,2100).phase)
        assertEquals(SubjectTracker.Phase.LOST,t.update(blank,w,h,2200).phase)
        assertEquals(SubjectTracker.Phase.LOST,t.update(frame(),w,h,2400).phase)
    }
    /** Mesma região, textura que vira outra aos poucos (pessoa girando o rosto, objeto girando). */
    private fun morph(t: Double, dx: Int): ByteArray = ByteArray(w*h) { i ->
        val x = i % w - dx; val y = i / w
        val v = if (x in 78..142 && y in 55..125) {
            val a = 31*sin(x*.31+y*.13) + 28*cos(y*.27-x*.11) + 17*sin(x*.17-y*.37)
            val b = 31*cos(x*.23-y*.29) + 28*sin(y*.19+x*.41) + 17*cos(x*.07+y*.33)
            122 + (1-t)*a + t*b
        } else 90.0
        v.roundToInt().coerceIn(0,255).toByte()
    }
    @Test fun adaptaAAparenciaQueMudaDevagarSemPerderOAssunto() {
        val t = SubjectTracker()
        assertEquals(SubjectTracker.Phase.TRACKING, t.select(morph(0.0,0),w,h,110f/(w-1),90f/(h-1),1000).phase)
        for (k in 1..30) {
            val r = t.update(morph(k*.02,k),w,h,1000L+k*200)
            assertEquals("quadro $k",SubjectTracker.Phase.TRACKING,r.phase)
            assertEquals((110+k).toDouble(),r.x*(w-1.0),4.0)
        }
    }
    @Test fun incertoProcuraNumaAreaMaiorEReencontraOSalto() {
        val t = SubjectTracker(); selected(t)
        val jump = frame(54)
        val first = t.update(jump,w,h,1200) // fora do raio normal: incerto, não pula para outro lugar.
        assertEquals(SubjectTracker.Phase.UNCERTAIN,first.phase)
        assertEquals(110.0,first.x*(w-1.0),4.0)
        val r = t.update(jump,w,h,1400)
        assertEquals(SubjectTracker.Phase.TRACKING,r.phase)
        assertEquals(164.0,t.update(jump,w,h,1600).x*(w-1.0),5.0)
    }
    @Test fun tamanhoConhecidoDoRostoDefineOModelo() {
        val t = SubjectTracker()
        val r = t.select(frame(),w,h,110f/(w-1),90f/(h-1),1000, sideHint = .3f)
        assertEquals(SubjectTracker.Phase.TRACKING,r.phase)
        assertEquals(.3,r.side.toDouble(),.01)
    }
    @Test fun rejeitaRegiaoLisaEBorda() {
        val t=SubjectTracker()
        assertEquals(SubjectTracker.Phase.LOST,t.select(ByteArray(w*h){110},w,h,.5f,.5f,1000).phase)
        assertEquals(SubjectTracker.Phase.LOST,t.select(frame(),w,h,.01f,.5f,1000).phase)
    }
    @Test fun descartaAtrasadosEEncerraAoMudarEnquadramentoOuPerderCadencia() {
        val t=SubjectTracker(); val initial=selected(t)
        assertEquals(initial,t.update(frame(20),w,h,999))
        assertEquals(SubjectTracker.Phase.LOST,t.update(frame(),w,h,2300).phase)
        selected(t)
        assertEquals(SubjectTracker.Phase.LOST,t.update(ByteArray(200*180),200,180,1200).phase)
    }
    @Test fun repousoReduzAnaliseECalorSuspendeSomenteRastreamento() {
        val t=SubjectTracker(); selected(t)
        repeat(4) { t.update(frame(),w,h,1200L+it*200) }
        assertFalse(t.result.moving)
        assertEquals(200L,TrackingBudget.intervalMs(true,0,false,2.0))
        assertEquals(400L,TrackingBudget.intervalMs(false,0,false,2.0))
        assertEquals(500L,TrackingBudget.intervalMs(true,2,false,2.0))
        assertEquals(500L,TrackingBudget.intervalMs(true,0,true,2.0))
        assertEquals(500L,TrackingBudget.intervalMs(true,0,false,40.0))
        assertFalse(TrackingBudget.suspended(2)); assertTrue(TrackingBudget.suspended(3))
    }
    @Test fun regioesNaoGeramFilaDeComandosNemCacamFocoParado() {
        val gate=TrackingMeterGate()
        assertTrue(gate.accept(.5f,.5f,1000))
        assertFalse(gate.accept(.8f,.5f,1100))
        assertTrue(gate.accept(.8f,.5f,1300))
        assertFalse(gate.accept(.801f,.5f,1800))
        assertTrue(gate.accept(.801f,.5f,2500))
        gate.reset(); assertTrue(gate.accept(.2f,.2f,2501))
    }
}
