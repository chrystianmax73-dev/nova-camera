package com.novacamera.s24.core

import com.novacamera.s24.core.imaging.Plane
import com.novacamera.s24.core.imaging.Portrait
import org.junit.Assert.assertEquals
import org.junit.Test

class DarkBackgroundTest {
    @Test fun escureceSoOFundo() {
        // Máscara 2×1: esquerda = pessoa (1), direita = fundo (0).
        val matte = Plane(2, 1, floatArrayOf(1f, 0f))
        val grey = 0xFF000000.toInt() or (200 shl 16) or (200 shl 8) or 200
        val tile = IntArray(8) { grey } // 8×1, metade de cada lado
        Portrait.darkenBackgroundTile(tile, 8, 1, 0, 0, 8, 1, matte)
        assertEquals(grey, tile[0])
        assertEquals((200 * Portrait.DARK_BACKGROUND_KEEP + 0.5f).toInt(), tile[7] and 0xFF)
        assertEquals(0xFF, tile[7] ushr 24)
    }
}
