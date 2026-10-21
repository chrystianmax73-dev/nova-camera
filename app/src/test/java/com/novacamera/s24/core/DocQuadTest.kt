package com.novacamera.s24.core

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class DocQuadTest {
    private val w = 200; private val h = 260
    /** Folha clara inclinada (trapézio, como foto de cima em ângulo) sobre mesa escura. */
    private fun scene(): IntArray {
        val tl = 40f to 50f; val tr = 165f to 40f; val br = 175f to 220f; val bl = 25f to 210f
        fun inside(x: Float, y: Float): Boolean {
            val p = listOf(tl, tr, br, bl)
            for (i in 0 until 4) { val (x0, y0) = p[i]; val (x1, y1) = p[(i + 1) % 4]
                if ((x1 - x0) * (y - y0) - (y1 - y0) * (x - x0) < 0) return false }
            return true
        }
        return IntArray(w * h) { k -> val x = k % w; val y = k / w
            if (inside(x + .5f, y + .5f)) 210 + (x * 7 + y * 3) % 20 else 60 + (x * 5 + y * 11) % 25 }
    }

    @Test fun achaOsQuatroCantosDaFolha() {
        val q = DocQuad.find(scene(), w, h)!!
        val expect = floatArrayOf(40f, 50f, 165f, 40f, 175f, 220f, 25f, 210f)
        for (i in 0 until 8) assertTrue("canto $i: ${q.pts[i]} vs ${expect[i]}", abs(q.pts[i] - expect[i]) <= 3f)
    }

    @Test fun semFolhaOuFundoClaroNaoInventa() {
        assertNull(DocQuad.find(IntArray(w * h) { 90 + (it * 13) % 30 }, w, h)) // mesa sem folha
        assertNull(DocQuad.find(IntArray(w * h) { k -> if (k % w < 100) 220 else 40 }, w, h)) // parede clara encostada nas bordas
    }
}
