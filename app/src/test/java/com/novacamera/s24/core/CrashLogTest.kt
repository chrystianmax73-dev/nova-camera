package com.novacamera.s24.core

import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    @Test fun formatoTrazTipoMensagemLinhaECausa() {
        val e = IllegalStateException("camera fechada", IllegalArgumentException("fps 15-30"))
        val s = CrashLog.format(e)
        assertTrue(s.startsWith("java.lang.IllegalStateException: camera fechada"))
        assertTrue(s.contains("    at com.novacamera.s24.core.CrashLogTest"))
        assertTrue(s.contains("causado por: java.lang.IllegalArgumentException: fps 15-30"))
    }
}
