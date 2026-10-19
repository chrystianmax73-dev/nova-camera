package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class AiAutoPolicyTest {
    private fun w(zoom: Float = 1f, iso: Int? = 100, night: Boolean = false, food: Boolean = false, px: Long = 12_000_000, on: Boolean = true) =
        AiAutoPolicy.wanted(on, night, food, zoom, iso, px)

    @Test fun entraNoZoomENoEscuroNaoNaFotoBoaDeDia() {
        assertTrue(w(zoom = 3f)); assertTrue(w(zoom = 1.4f)); assertTrue(w(iso = 1600)); assertTrue(w(night = true))
        assertFalse(w()) // 1×, ISO baixo: foto boa de dia
        assertFalse(w(zoom = 1.2f, iso = 400))
    }

    @Test fun altaResolucaoEntra200MpEFoodNaoDesligadaNunca() {
        assertTrue(w(zoom = 3f, px = 49_900_000)); assertFalse(w(zoom = 3f, px = 200_000_000))
        assertFalse(w(zoom = 5f, food = true)); assertFalse(w(zoom = 5f, on = false))
    }

    @Test fun faixasSoVoltamQuandoNinguemMaisLeAsLinhas() {
        val tile = 256; val pad = 16
        for (h in listOf(288, 300, 512, 530, 1000, 3060, 8160, 8170, 4096 + 20)) {
            val bands = (h + tile - 1) / tile
            var written = 0 // faixas gravadas, em ordem
            for (t in 0 until bands) {
                while (written < t && DenoiseBands.writable(written, t, h, tile, pad)) written++
                // Nenhuma faixa já gravada pode ter linhas que esta faixa ou as seguintes leem.
                for (b in 0 until written) for (n in t until bands)
                    assertTrue("h=$h faixa $b gravada antes de $n ler", DenoiseBands.outputEnd(b, h, tile) <= DenoiseBands.inputTop(n, h, tile, pad))
            }
            assertTrue(DenoiseBands.inputTop(bands - 1, h, tile, pad) + tile + 2 * pad <= maxOf(h, tile + 2 * pad))
        }
    }
}
