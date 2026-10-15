package com.novacamera.s24.core.imaging

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** A/B sintético do código real. Estes testes não comprovam qualidade no S24 Ultra. */
class AdaptiveImagingTest {
    private fun render(px: IntArray, w: Int, h: Int, p: Adaptive.Plan): IntArray = px.copyOf().also {
        if (!p.skip) ToneMap(p.base, p.params, w, h, p.mid).applyTile(it, w, h, 0, 0, p.strength, p.imaging?.protection ?: 0f)
    }

    @Test fun abMaintainsGreenAndLuminanceAcrossScenesAndZoom() {
        val w = 48; val h = 48
        for (exposure in listOf(0.35f, 0.7f, 1f)) for (zoom in listOf(1f, 5f, 10f)) {
            val colors = listOf(Px.argb(80, 130, 70), Px.argb(220, 120, 80), Px.argb(70, 85, 190), Px.argb(170, 155, 150))
            val px = IntArray(w * h) { i ->
                val c = colors[(i % w / 12 + i / w / 12) % colors.size]
                val k = exposure * (0.65f + (i % 11) * 0.025f)
                Px.argb((Px.r(c)*k).toInt(), (Px.g(c)*k).toInt(), (Px.b(c)*k).toInt())
            }
            val old = Adaptive.plan(px, w, h, ToneIntent.LIGHT, zoom)
            val next = AdaptiveImaging.plan(px, w, h, ToneIntent.LIGHT, zoom)
            assertEquals(old.params, next.params); assertEquals(old.strength, next.strength, 0f)
            val baseline = render(px, w, h, old); val result = render(px, w, h, next)
            assertTrue(AdaptiveImaging.safeAgainstBaseline(px, baseline, result, w, h))
        }
    }

    @Test fun chromaProtectionReducesChannelRatioDrift() {
        val original = Px.argb(200, 120, 80)
        val baseline = Px.argb(180, 90, 50)
        val corrected = AdaptiveImaging.protect(original, baseline, 1f)
        fun drift(c: Int) = abs(Px.g(c).toFloat()/Px.r(c) - 0.6f) + abs(Px.b(c).toFloat()/Px.r(c) - 0.4f)
        assertTrue(drift(corrected) < drift(baseline))
        assertTrue(abs(Px.luma(corrected)-Px.luma(baseline)) <= 1.5f/255f)
    }

    @Test fun unsafeCandidateAndChangedGreensAreRejected() {
        val original = IntArray(9) { Px.argb(60, 100, 40) }
        val changed = original.copyOf().also { it[4] = Px.argb(60, 101, 40) }
        assertFalse(AdaptiveImaging.safeAgainstBaseline(original, original, changed, 3, 3))
        val gray = IntArray(9) { Px.argb(90, 90, 90) }
        assertFalse(AdaptiveImaging.safeAgainstBaseline(gray, gray, IntArray(9) { Px.argb(255,255,255) }, 3, 3))
    }

    @Test fun existingSpecialModesAreBitExact() {
        val px = IntArray(256) { i -> Px.argb(30+i%120, 40+i%160, 20+i%80) }
        for (intent in listOf(ToneIntent.NIGHT, ToneIntent.HDR, ToneIntent.PORTRAIT, ToneIntent.FOOD)) {
            val old = Adaptive.plan(px,16,16,intent,5f)
            val next = AdaptiveImaging.plan(px,16,16,intent,5f)
            assertNull(next.imaging)
            assertArrayEquals(render(px,16,16,old), render(px,16,16,next))
        }
    }

    @Test fun previewAndUnmeasuredLensesCannotActivateCalibration() {
        val facts = ImagingFacts(device="samsung/test", cameraId="0", physicalId="2", focalMm=18f)
        val scene = ImagingScene.ZOOM_5X
        val entry = LensCalibration("measured-test",facts.lensKey,scene,0.5f,"local-test")
        val registry = CalibrationRegistry(listOf(entry))
        assertNull(registry.resolve(facts,scene))
        assertEquals(entry,registry.resolve(facts.copy(captureVerified=true),scene))
        assertNull(registry.resolve(facts.copy(captureVerified=true, physicalId="3"),scene))
        assertNull(CalibrationRegistry(listOf(entry.copy(evidenceId=""))).resolve(facts.copy(captureVerified=true),scene))
        assertEquals(facts,ImagingFacts.fromMeta(facts.toMeta()))
    }
}
