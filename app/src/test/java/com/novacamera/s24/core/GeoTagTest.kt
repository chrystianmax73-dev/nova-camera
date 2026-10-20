package com.novacamera.s24.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTagTest {
    @After fun reset() { GeoTag.enabled = false; GeoTag.clear() }

    private fun fix(acc: Float = 10f, at: Long = 1_000L) =
        GeoTag.Fix(-23.5505, -46.6333, 760.4, acc, 1_760_918_400_000L, at)

    @Test fun desligadoNaoGravaNada() {
        GeoTag.update(fix())
        assertTrue(GeoTag.tagsAt(2_000L).isEmpty())
        assertTrue(GeoTag.tagsNow().isEmpty())
    }

    @Test fun grausMinutosSegundosEHemisferio() {
        GeoTag.enabled = true; GeoTag.update(fix())
        val t = GeoTag.tagsAt(2_000L)
        assertEquals("23/1,33/1,18000/10000", t["GPSLatitude"])
        assertEquals("S", t["GPSLatitudeRef"])
        assertEquals("46/1,37/1,598800/10000", t["GPSLongitude"])
        assertEquals("W", t["GPSLongitudeRef"])
        assertEquals("76040/100", t["GPSAltitude"])
        assertEquals("2025:10:20", t["GPSDateStamp"])
    }

    @Test fun posicaoVelhaOuImprecisaFicaDeFora() {
        GeoTag.enabled = true; GeoTag.update(fix(at = 0L))
        assertTrue(GeoTag.tagsAt(GeoTag.MAX_AGE_MS + 1).isEmpty())
        GeoTag.clear(); GeoTag.update(fix(acc = 2_000f))
        assertTrue(GeoTag.tagsAt(2_000L).isEmpty())
    }

    @Test fun leituraPioLogoDepoisNaoSubstitui() {
        GeoTag.enabled = true; GeoTag.update(fix(acc = 10f, at = 1_000L))
        GeoTag.update(GeoTag.Fix(0.0, 0.0, null, 300f, 0L, 5_000L))
        assertEquals("S", GeoTag.tagsAt(6_000L)["GPSLatitudeRef"])
    }
}
