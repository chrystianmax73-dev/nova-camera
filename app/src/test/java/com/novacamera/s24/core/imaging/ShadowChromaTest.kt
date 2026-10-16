package com.novacamera.s24.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ShadowChromaTest {
    @Test fun roxoNoEscuroComIsoAltoViraNeutro() {
        val px = intArrayOf(Px.argb(40, 18, 52), Px.argb(200, 120, 60))
        ShadowChroma.applyTile(px, ShadowChroma.strengthFor(3069))
        val d = px[0]
        assertTrue("sombra roxa deveria perder cor: ${Px.r(d)},${Px.g(d)},${Px.b(d)}", abs(Px.b(d) - Px.g(d)) < 12)
        // Meio-tom/claro intacto.
        assertEquals(Px.argb(200, 120, 60), px[1])
    }

    @Test fun isoBaixoNaoMexe() {
        assertEquals(0f, ShadowChroma.strengthFor(400), 0f)
        assertEquals(0f, ShadowChroma.strengthFor(null), 0f)
        val px = intArrayOf(Px.argb(40, 18, 52))
        ShadowChroma.applyTile(px, ShadowChroma.strengthFor(400))
        assertEquals(Px.argb(40, 18, 52), px[0])
    }
}
