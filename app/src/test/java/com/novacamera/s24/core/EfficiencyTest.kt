package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EfficiencyTest {
    private fun s(t: Long, act: String, ua: Long?, pct: Int, charging: Boolean = false, temp: Float = 30f) =
        EnergyLog.Sample(t, act, ua, 3900, temp, pct, charging, 0, 0.4f)

    @Test fun corrente_mA_ou_uA_normaliza() {
        assertEquals(850_000L, EnergyLog.normalizeUa(-850_000L))
        assertEquals(850_000L, EnergyLog.normalizeUa(850L)) // aparelho que informa mA
        assertNull(EnergyLog.normalizeUa(0L))
    }

    @Test fun potencia_soNaBateria() {
        assertEquals(3315.0, s(0, "Foto", -850_000L, 80).powerMw!!, 1.0)
        assertNull(s(0, "Foto", -850_000L, 80, charging = true).powerMw)
    }

    @Test fun relatorio_porAtividade_eQuedaPorHora() {
        val list = (0..36).map { i -> s(i * 10_000L, if (i < 18) "Foto" else "Vídeo (gravando)", -900_000L - i * 10_000L, 80 - i / 12, temp = 30f + i * 0.1f) }
        val r = EnergyLog.report(list)
        assertTrue(r, r.contains("Foto:") && r.contains("Vídeo (gravando):") && r.contains("mW"))
        assertTrue(r, r.contains("%/h na bateria"))
    }

    @Test fun relatorio_carregando_naoInventaConsumo() {
        val list = (0..3).map { i -> s(i * 10_000L, "Foto", -900_000L, 50, charging = true) }
        val r = EnergyLog.report(list)
        assertTrue(r, r.contains("consumo não medido"))
        assertFalse(r, r.contains("%/h"))
    }

    @Test fun ia_esperaComBateriaBaixa() {
        val low = DeviceHeat(0, 30f, charging = false, powerSave = false, batteryPercent = 12)
        assertFalse(DenoisePolicy.allowed(low))
        assertTrue(DenoisePolicy.allowed(low.copy(charging = true)))
        assertTrue(DenoisePolicy.allowed(low.copy(batteryPercent = 60)))
    }

    @Test fun tele_comGanhoAlto_usaMultiquadro() {
        assertEquals(AutoPhotoPolicy.Path.ZOOM, AutoPhotoPolicy.choose(400, 10_000_000L, 0f, 0f, hot = false, zoom = 10f))
        assertEquals(AutoPhotoPolicy.Path.SINGLE, AutoPhotoPolicy.choose(100, 2_000_000L, 0f, 0f, hot = false, zoom = 10f))
        assertEquals(AutoPhotoPolicy.Path.SINGLE, AutoPhotoPolicy.choose(400, 10_000_000L, 0f, 0f, hot = false, zoom = 1f))
        // Aquecido: um quadro só, como antes.
        assertEquals(AutoPhotoPolicy.Path.SINGLE, AutoPhotoPolicy.choose(800, 10_000_000L, 0f, 0f, hot = true, zoom = 10f))
    }
}
