package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class NightLensPolicyTest {
    private val logical = LensOption("0", 1f, 0.6f, 10f, logical = true)
    private val main = LensOption("0", 1f, 1f, 8f, false, "5")
    private val tele3 = LensOption("0", 2.77f, 1f, 3.6f, false, "6")
    private val tele5 = LensOption("0", 5.1f, 1f, 1.96f, false, "7")
    private val all = listOf(logical, main, tele3, tele5)

    @Test fun noturnoFixaLenteNativaNaTele() {
        assertFalse(NightLensPolicy.pinNative(true, false, 1f))
        assertFalse("selfie não tem tele", NightLensPolicy.pinNative(true, true, 5f))
        assertFalse("fora do Noturno não muda", NightLensPolicy.pinNative(false, false, 5f))
        assertEquals("0@7", LensSelection.choose(all, "0", "0", 5f, preferNative = true)?.cameraId)
        assertEquals("10× = tele 5× com recorte", "0@7", LensSelection.choose(all, "0", "0", 10f, preferNative = true)?.cameraId)
        assertEquals("0@6", LensSelection.choose(all, "0", "0", 3f, preferNative = true)?.cameraId)
    }
    @Test fun focoQueNaoFechaDevolveAoFirmware() {
        val w = NightLensPolicy.FocusWatch(2500)
        assertFalse(w.failed(0, 1)); assertFalse(w.failed(2000, 6))
        assertTrue("2,5 s sem foco", w.failed(2600, 6))
        w.reset(); assertFalse(w.failed(3000, 3))
        assertFalse("focou: zera", w.failed(4000, 2)); assertFalse(w.failed(6000, 5))
        assertFalse("inativo não conta", w.failed(9000, 0))
        assertTrue(w.failed(9000, 5))
    }
}
