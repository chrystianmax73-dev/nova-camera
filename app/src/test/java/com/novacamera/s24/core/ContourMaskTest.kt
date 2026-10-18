package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ContourMaskTest {
    private fun square(n: Int, a: Int, b: Int) = FloatArray(n * n) { i -> if (i % n in a until b && i / n in a until b) 1f else 0f }

    @Test fun soABordaDoQuadrado() {
        val r = ContourMask.edges(square(20, 5, 15), 20, 20, 7)
        assertNotNull(r)
        assertEquals(36, r!!.edge.count { it == 7 }) // perímetro de 10×10
        assertEquals(0, r.edge[10 * 20 + 10])     // miolo vazio
    }

    @Test fun objetoMinusculoOuCenaInteiraNaoMostram() {
        assertNull(ContourMask.edges(square(100, 50, 52), 100, 100, 7))   // 0,04%
        assertNull(ContourMask.edges(FloatArray(400) { 1f }, 20, 20, 7))   // 100%
    }
}
