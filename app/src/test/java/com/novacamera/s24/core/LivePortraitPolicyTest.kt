package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePortraitPolicyTest {
    private fun heat(powerSave: Boolean = false, charging: Boolean = false, battery: Int = 80) =
        DeviceHeat(0, 30f, charging, powerSave, battery)

    @Test fun pessoaSemToqueDispensaProfundidadeNaPrevia() {
        assertTrue(LivePortraitPolicy.personOnly(false, null, 0.3f))
        assertFalse("toque escolhe o assunto (pode ser objeto)", LivePortraitPolicy.personOnly(false, 0.5f to 0.5f, 0.3f))
        assertFalse("planta/objeto sem pessoa", LivePortraitPolicy.personOnly(false, null, 0.02f))
        assertFalse("cinematográfico grava a máscara: regra própria", LivePortraitPolicy.personOnly(true, null, 0.3f))
        assertFalse("entra só a partir de 8%", LivePortraitPolicy.personOnly(false, null, 0.06f))
        assertTrue("já ativo: fica até cair abaixo de 5%", LivePortraitPolicy.personOnly(false, null, 0.06f, wasPersonOnly = true))
        assertFalse(LivePortraitPolicy.personOnly(false, null, 0.04f, wasPersonOnly = true))
    }

    @Test fun poucaEnergia() {
        assertTrue(LivePortraitPolicy.lowPower(heat(powerSave = true)))
        assertTrue(LivePortraitPolicy.lowPower(heat(battery = 15)))
        assertFalse(LivePortraitPolicy.lowPower(heat(battery = 15, charging = true)))
        assertFalse(LivePortraitPolicy.lowPower(heat(battery = 54)))
        assertFalse("leitura ausente", LivePortraitPolicy.lowPower(heat(battery = -1)))
        assertFalse(LivePortraitPolicy.lowPower(null))
    }

    @Test fun profundidadeComPoucaEnergiaSoRecalculaPorMudancaOuDepoisDeUmMinuto() {
        val p = DepthRefreshPolicy()
        val luma = FloatArray(16) { 0.5f }
        p.done(0, luma, null)
        assertTrue(p.needed(20_000, luma, null))
        assertFalse(p.needed(20_000, luma, null, LivePortraitPolicy.LOW_POWER_MAX_AGE_MS))
        assertTrue(p.needed(60_000, luma, null, LivePortraitPolicy.LOW_POWER_MAX_AGE_MS))
    }

    @Test fun iaDispensadaSoParaMesmoTamanhoEOrcamentoPorUmTempo() {
        val s = DenoiseSkip()
        val px = 3060L * 4080
        assertNull(s.skippedSince(px, DenoisePolicy.BACKGROUND, 0))
        s.recordOverBudget(px, DenoisePolicy.BACKGROUND, 1_000)
        assertEquals(4_000L, s.skippedSince(px, DenoisePolicy.BACKGROUND, 5_000))
        assertNull("outro tamanho mede de novo", s.skippedSince(2000L * 1500, DenoisePolicy.BACKGROUND, 5_000))
        assertNull("outro orçamento", s.skippedSince(px, DenoisePolicy.FOREGROUND, 5_000))
        assertNull("depois do prazo mede de novo", s.skippedSince(px, DenoisePolicy.BACKGROUND, 1_000 + DenoiseSkip.HOLD_MS))
    }
}
