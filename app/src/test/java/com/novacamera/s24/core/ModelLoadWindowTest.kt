package com.novacamera.s24.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLoadWindowTest {
    @Test fun vigiaAguardaDuranteCargaECarenciaComLimite() {
        ModelLoadWindow.resetForTest()
        assertFalse(ModelLoadWindow.defersStall(1_000))
        ModelLoadWindow.begin("profundidade", 1_000)
        assertTrue(ModelLoadWindow.defersStall(3_000))
        assertFalse("carga travada não segura o vigia para sempre", ModelLoadWindow.defersStall(1_000 + ModelLoadWindow.MAX_MS))
        ModelLoadWindow.end(4_000)
        assertTrue(ModelLoadWindow.defersStall(5_000))
        assertFalse(ModelLoadWindow.defersStall(4_000 + ModelLoadWindow.GRACE_MS))
        assertTrue(ModelLoadWindow.describe(5_000).contains("3000 ms"))
    }

    @Test fun contaLacunasPorCarga() {
        ModelLoadWindow.resetForTest()
        ModelLoadWindow.begin("profundidade", 0); ModelLoadWindow.noteGap(); ModelLoadWindow.noteGap(); ModelLoadWindow.end(2_500)
        ModelLoadWindow.begin("profundidade", 10_000); ModelLoadWindow.end(12_000)
        val r = ModelLoadWindow.report()
        assertTrue(r, r.contains("2 cargas") && r.contains("1 com prévia sem quadros") && r.contains("2 s sem quadros"))
    }
}
