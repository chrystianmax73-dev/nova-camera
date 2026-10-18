package com.novacamera.s24.core

import com.novacamera.s24.core.SuperResPolicy.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class SuperResPolicyTest {
    private fun f(
        kind: Kind = Kind.PHOTO, zoom: Float = 2f, intrinsic: Float = 5f, physical: Boolean = true,
        enabled: Boolean = true, front: Boolean = false, ext: Boolean = false,
        frames: Int = 5, steady: Boolean = false, plan: Boolean = true,
    ) = SuperResPolicy.factor(enabled, front, ext, kind, physical, intrinsic, zoom, frames, steady, plan)

    @Test fun fotoNo10xSobreA5xUsaSR() = assertEquals(2f, f(), 0f)

    @Test fun semZoomDigitalOuSemTeleNaoUsa() {
        assertEquals(1f, f(zoom = 1.2f), 0f)
        assertEquals(1f, f(intrinsic = 1f), 0f)
        assertEquals(1f, f(physical = false), 0f)
        assertEquals(1f, f(enabled = false), 0f)
        assertEquals(1f, f(front = true), 0f)
        assertEquals(1f, f(ext = true), 0f)
        assertEquals(1f, f(kind = Kind.OTHER), 0f)
    }

    @Test fun noturnoNaMaoComPlanoProprioUsa() = assertEquals(2f, f(kind = Kind.NIGHT), 0f)

    @Test fun noturnoApoiadoSemPlanoOuComPoucosQuadrosNaoUsa() {
        assertEquals(1f, f(kind = Kind.NIGHT, steady = true), 0f)
        assertEquals(1f, f(kind = Kind.NIGHT, plan = false), 0f)
        assertEquals(1f, f(kind = Kind.NIGHT, frames = 2), 0f)
    }
}
