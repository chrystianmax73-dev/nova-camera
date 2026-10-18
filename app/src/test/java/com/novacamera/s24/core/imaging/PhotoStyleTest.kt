package com.novacamera.s24.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoStyleTest {
    private fun y(c: Int) = Px.luma(c)

    @Test fun neutralIsIdentity() {
        val s = PhotoStyle()
        for (c in listOf(0xFF000000.toInt(), 0xFF808080.toInt(), 0xFF3366CC.toInt(), 0xFFFFFFFF.toInt())) assertEquals(c, s.apply(c))
    }

    @Test fun curveIsMonotonicAndFixesEnds() {
        for (t in listOf(-1f, -0.4f, 0.4f, 1f)) {
            assertEquals(0f, PhotoStyle.curve(0f, t), 1e-6f); assertEquals(1f, PhotoStyle.curve(1f, t), 1e-6f)
            var prev = -1f
            for (i in 0..1000) { val v = PhotoStyle.curve(i / 1000f, t); assertTrue("t=$t", v >= prev - 1e-6f); prev = v }
        }
    }

    @Test fun toneDeepensOrOpensShadows() {
        val shadow = Px.argb(50, 50, 50); val mid = Px.argb(128, 128, 128)
        assertTrue(y(PhotoStyle(tone = 1f).apply(shadow)) < y(shadow))
        assertTrue(y(PhotoStyle(tone = -1f).apply(shadow)) > y(shadow))
        // Tom não muda a cor: cinza continua cinza.
        val out = PhotoStyle(tone = 0.8f).apply(mid)
        assertEquals(Px.r(out), Px.b(out))
    }

    @Test fun warmthShiftsBalanceKeepingBrightness() {
        val gray = Px.argb(120, 120, 120)
        val warm = PhotoStyle(warmth = 1f).apply(gray); val cold = PhotoStyle(warmth = -1f).apply(gray)
        assertTrue(Px.r(warm) > Px.b(warm)); assertTrue(Px.b(cold) > Px.r(cold))
        assertEquals(y(gray), y(warm), 0.01f)
    }

    @Test fun metaRoundTrip() {
        assertEquals(PhotoStyle(0.4f, -0.6f), PhotoStyle.fromMeta(PhotoStyle(0.4f, -0.6f).toMeta()))
        assertEquals(PhotoStyle(), PhotoStyle.fromMeta(null))
        assertEquals("estilo: tom +2, temperatura -3", PhotoStyle(0.4f, -0.6f).label())
    }
}
