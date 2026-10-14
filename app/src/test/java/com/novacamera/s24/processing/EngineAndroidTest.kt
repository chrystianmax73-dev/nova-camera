package com.novacamera.s24.processing

import android.graphics.Bitmap
import android.graphics.Rect
import com.novacamera.s24.core.imaging.Merge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.Random
import kotlin.math.sin

/**
 * Camada Android do motor: JPEGs reais decodificados em faixas, alinhamento de quadros
 * deslocados, fusão e orientação — o mesmo caminho usado pelo Noturno no aparelho.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EngineAndroidTest {
    private val w = 960
    private val h = 720

    private fun scene(dx: Int, dy: Int, seed: Long, noise: Float): ByteArray {
        val rnd = Random(seed)
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = x + dx; val sy = y + dy
            val base = 90 + 50 * sin(sx / 23.0) * sin(sy / 31.0) + if ((sx / 60 + sy / 60) % 2 == 0) 30 else 0
            fun ch(k: Double) = (base * k + rnd.nextGaussian() * noise).toInt().coerceIn(0, 255)
            px[y * w + x] = (0xFF shl 24) or (ch(1.0) shl 16) or (ch(0.9) shl 8) or ch(0.7)
        }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 97, it) }.toByteArray()
    }

    @Test
    fun bandedJpegSourceMatchesFullDecode() {
        val frame = CapturedFrame(scene(0, 0, 1, 4f), 0, Rect(10, 20, 910, 700))
        val src = JpegSource(frame)
        assertEquals(900, src.width); assertEquals(680, src.height)
        val full = src.decodeFull()
        val buf = IntArray(64 * 64)
        for ((x, y) in listOf(0 to 0, 400 to 300, 836 to 616, 100 to 600)) {
            src.read(x, y, 64, 64, buf)
            for (i in buf.indices step 97) assertEquals(full.getPixel(x + i % 64, y + i / 64), buf[i])
        }
        src.close()
    }

    @Test
    fun nightPipelineAlignsAndReducesNoise() {
        val shifts = listOf(0 to 0, 6 to -4, -5 to 3, 3 to 7, -7 to -6)
        val frames = shifts.mapIndexed { i, (dx, dy) -> CapturedFrame(scene(dx, dy, 100L + i, 14f), 0, null) }
        val sources = frames.map(::JpegSource)
        val a = Engine.align(sources, CancelToken()) {}
        // Deslocamentos recuperados. O quadro k mostra a cena deslocada de +d; o alinhamento lê
        // em x − d para coincidir com a referência (escala do alinhamento: meia resolução).
        for (k in 1 until shifts.size) {
            val f = a.fields[k][a.fields[k].size / 2]
            assertEquals("quadro $k dx", -shifts[k].first.toFloat(), f.dx * a.scale, 2.5f)
            assertEquals("quadro $k dy", -shifts[k].second.toFloat(), f.dy * a.scale, 2.5f)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Engine.mergeInto(sources, out, a, CancelToken()) {}
        sources.forEach { it.close() }
        val before = IntArray(400 * 300).also { JpegSource(frames[0]).decodeFull().getPixels(it, 0, 400, 280, 210, 400, 300) }
        val after = IntArray(400 * 300).also { out.getPixels(it, 0, 400, 280, 210, 400, 300) }
        val nb = Merge.estimateNoise(before, 400, 300)
        val na = Merge.estimateNoise(after, 400, 300)
        assertTrue("ruído $nb → $na", na < nb * 0.7f)
    }

    @Test
    fun cancelStopsProcessing() {
        val sources = listOf(CapturedFrame(scene(0, 0, 1, 8f), 0, null), CapturedFrame(scene(2, 2, 2, 8f), 0, null)).map(::JpegSource)
        val token = CancelToken().apply { cancelled = true }
        val r = runCatching { Engine.align(sources, token) {} }
        assertTrue(r.exceptionOrNull() is CancelledException)
    }

    @Test
    fun orientRotatesAndMirrors() {
        val px = IntArray(4 * 2) { if (it == 0) 0xFFFF0000.toInt() else 0xFF000000.toInt() }
        val src = Bitmap.createBitmap(px, 4, 2, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
        val r = Engine.orient(src, 90, mirror = false)
        assertEquals(2, r.width); assertEquals(4, r.height)
        // Canto superior esquerdo vai para o superior direito após 90° horário.
        assertEquals(0xFFFF0000.toInt(), r.getPixel(1, 0))
        val m = Engine.orient(r, 0, mirror = true)
        assertEquals(0xFFFF0000.toInt(), m.getPixel(0, 0))
        assertTrue(m.isMutable)
    }
}
