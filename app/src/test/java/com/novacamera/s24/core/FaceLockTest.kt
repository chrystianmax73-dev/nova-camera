package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class FaceLockTest {
    private val a = FaceLock.Box(100f, 100f, 200f, 200f)
    private val b = FaceLock.Box(400f, 120f, 480f, 200f)

    @Test fun toqueEscolheORostoTocadoComFolga() {
        assertEquals(a, FaceLock.pick(listOf(a, b), 150f, 220f)) // queixo, fora da caixa mas na folga.
        assertEquals(b, FaceLock.pick(listOf(a, b), 440f, 160f))
        assertNull(FaceLock.pick(listOf(a, b), 300f, 400f)) // fundo: vai ao rastreio por aparência.
        assertNull(FaceLock.pick(emptyList(), 150f, 150f))
    }
    @Test fun seguimentoAceitaOMesmoRostoERecusaOOutro() {
        val moved = FaceLock.Box(130f, 110f, 235f, 215f)
        assertEquals(moved, FaceLock.follow(listOf(b, moved), a))
        assertNull(FaceLock.follow(listOf(b), a)) // longe: não troca de pessoa.
        assertNull(FaceLock.follow(listOf(FaceLock.Box(120f, 120f, 340f, 340f)), a)) // tamanho incompatível.
    }
}
