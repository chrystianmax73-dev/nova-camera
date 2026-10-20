package com.novacamera.s24.core

import com.novacamera.s24.core.imaging.BacklitLift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BacklitLiftTest {
    private val w = 120; private val h = 160
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    /** Pessoa no meio (colunas 30–90, linhas 40–160), rosto em cima dela. */
    private val person = FloatArray(w * h) { k -> val x = k % w; val y = k / w; if (x in 30 until 90 && y >= 40) 1f else 0f }
    private val face = FloatArray(w * h) { k -> val x = k % w; val y = k / w; if (x in 40 until 80 && y in 50 until 100) 1f else 0f }
    private fun scene(subject: Int, bg: Int) = IntArray(w * h) { if (person[it] > 0.5f) gray(subject) else gray(bg) }
    private fun lum(px: IntArray) = FloatArray(px.size) { (px[it] and 255) / 255f }

    @Test fun selfieContraLuzClareiaSoAPessoa() {
        val px = scene(75, 170)
        val plan = BacklitLift.plan(lum(px), person, face)
        assertNotNull(plan)
        val maps = BacklitLift.maps(lum(px), person, w, h)
        assertTrue(BacklitLift.accept(px, w, h, maps, plan!!))
        val out = px.copyOf(); BacklitLift.applyTile(out, w, h, 0, 0, w, h, maps, plan)
        val faceAfter = out[75 * w + 60] and 255
        assertTrue("rosto $faceAfter", faceAfter in 110..125)   // mira min(0,52; 0,70 × fundo) ≈ 0,47 (119)
        assertEquals(170, out[10 * w + 5] and 255)               // fundo intacto
        assertEquals(170, out[45 * w + 25] and 255)              // borda do fundo sem halo
    }

    @Test fun semContraLuzNaoMexe() {
        assertNull(BacklitLift.plan(lum(scene(120, 150)), person, face)) // rosto já claro
        assertNull(BacklitLift.plan(lum(scene(60, 80)), person, face))   // fundo também escuro
    }

    @Test fun rostoMedioContraFundoMuitoClaroEntra() {
        // Selfie de 20/10 na 0.35: rosto 0,38 × parede 0,80 ficava de fora (limite antigo 0,40).
        val plan = BacklitLift.plan(lum(scene(97, 204)), person, face)
        assertNotNull(plan)
        assertEquals(0.45f, plan!!.ev, 0.05f)
        // Rosto 0,43 com fundo só 0,16 mais claro: não é contraluz forte.
        assertNull(BacklitLift.plan(lum(scene(110, 150)), person, face))
    }

    @Test fun semRostoNaoEPessoa() {
        // Pano verde no sofá: o segmentador marcou "roupa/corpo" sem rosto; não pode clarear.
        assertNull(BacklitLift.plan(lum(scene(75, 170)), person, FloatArray(w * h)))
        assertNull(BacklitLift.plan(lum(scene(75, 170)), person, null))
    }

    @Test fun objetoDoRetratoEmContraluz() {
        // Garrafa (recorte do Retrato) 0,15 contra parede 0,62: sobe ~0,65 EV; objeto já claro não muda.
        val plan = BacklitLift.planSubject(lum(scene(38, 158)), person)
        assertNotNull(plan)
        assertEquals(0.65f, plan!!.ev, 0.06f)
        assertNull(BacklitLift.planSubject(lum(scene(110, 170)), person))
    }

    @Test fun limitesDoAumento() {
        val plan = BacklitLift.plan(lum(scene(30, 200)), person, face)!!
        assertEquals(BacklitLift.MAX_EV, plan.ev, 1e-4f)
    }
}
