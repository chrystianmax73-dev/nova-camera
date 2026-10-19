package com.novacamera.s24.processing

import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Prévia da IA por região: o recorte recebe o tom da foto inteira, não o calculado só dele. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RegionRenderTest {
    private fun scene(w: Int, h: Int): Bitmap {
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            // Metade escura com luz pontual: o recorte escuro sozinho pediria outro clareamento.
            val v = if (x < w / 2) 18 + (x * 7 + y * 3) % 23 else 120 + (x + y) % 60
            (0xFF shl 24) or (v shl 16) or ((v * 9 / 10) shl 8) or (v * 8 / 10)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun maxDiff(a: Bitmap, b: Bitmap): Int {
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        var m = 0
        for (i in pa.indices) for (s in intArrayOf(0, 8, 16)) m = max(m, abs((pa[i] shr s and 255) - (pb[i] shr s and 255)))
        return m
    }

    @Test fun recorteComPlanosDaFotoInteiraIgualAoMesmoTrechoDaFotoInteira() {
        val app = RuntimeEnvironment.getApplication()
        val session = Session(File(ProcessingQueue.sessionsDir(app), "1700000000123").apply { mkdirs() })
        session.kind = JobKind.NIGHT
        val w = 600; val h = 800
        val full = scene(w, h)
        val plans = ProcessingQueue.RenderPlans()
        ProcessingQueue.render(session, full, 1f, 0f, CancelToken(), plans = plans) {}
        assertTrue(plans.recorded)
        val x0 = 120; val y0 = 200; val cw = 300; val ch = 320
        val expected = Bitmap.createBitmap(full, x0, y0, cw, ch)
        val crop = Bitmap.createBitmap(scene(w, h), x0, y0, cw, ch).copy(Bitmap.Config.ARGB_8888, true)
        ProcessingQueue.render(session, crop, 1f, 0f, CancelToken(), plans = plans.at(Engine.Region(x0, y0, w, h))) {}
        val replayed = maxDiff(crop, expected)
        val naive = Bitmap.createBitmap(scene(w, h), x0, y0, cw, ch).copy(Bitmap.Config.ARGB_8888, true)
        ProcessingQueue.render(session, naive, 1f, 0f, CancelToken()) {}
        val alone = maxDiff(naive, expected)
        assertTrue("recorte com os planos da foto inteira: diferença $replayed", replayed <= 2)
        assertTrue("o tom calculado só do recorte é que diverge ($alone)", alone > replayed)
    }
}
