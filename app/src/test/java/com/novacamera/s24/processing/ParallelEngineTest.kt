package com.novacamera.s24.processing

import android.graphics.Bitmap
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Random
import kotlin.math.sin

/**
 * Alinhamento e super-resolução em vários núcleos devem dar exatamente a mesma foto de um núcleo só
 * (só o tempo muda). Também imprime o tempo das duas formas (servidor, não o S24).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ParallelEngineTest {
    private val w = 640; private val h = 480

    @After fun reset() { Parallel.workers = 1 }

    private fun frame(sx: Double, sy: Double, rnd: Random): Bitmap {
        val px = IntArray(w * h) { i ->
            val x = i % w + sx; val y = i / w + sy
            val v = ((0.5 + 0.2 * sin(x * 0.05) * sin(y * 0.04) + 0.15 * sin(x * 1.3 + y * 0.4) + rnd.nextGaussian() * 0.01) * 255).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or ((v * 0.9).toInt() shl 8) or (255 - v)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun run(workers: Int, sources: List<BitmapSource>): Triple<IntArray, Engine.Alignment, Long> {
        Parallel.workers = workers
        val t0 = System.nanoTime()
        val a = Engine.align(sources, CancelToken()) {}
        val (out, _) = Engine.superRes(sources, a, 1f, 2f, 90, false, CancelToken()) {}
        val ms = (System.nanoTime() - t0) / 1_000_000
        val px = IntArray(out.width * out.height).also { out.getPixels(it, 0, out.width, 0, 0, out.width, out.height) }
        return Triple(px, a, ms)
    }

    @Test fun variosNucleosDaoAMesmaFoto() {
        val rnd = Random(4)
        val sources = List(6) { BitmapSource(frame(it * 0.37, it * -0.29, rnd)) }
        run(1, sources) // aquecimento (JIT), fora da medição
        val (one, a1, t1) = run(1, sources)
        val (many, a4, t4) = run(4, sources)
        println("alinhamento + alta resolução 2× (6 quadros ${w}x$h): 1 núcleo ${t1} ms, 4 núcleos ${t4} ms")
        assertEquals(a1.usable.toList(), a4.usable.toList())
        for (k in a1.fields.indices) assertEquals(a1.fields[k].toList(), a4.fields[k].toList())
        assertArrayEquals(one, many)
    }

    @Test fun mapaParaleloMantemAOrdemEPropagaErro() {
        Parallel.workers = 4
        assertEquals((0 until 50).map { it * it }, Parallel.map(50) { it * it })
        val err = runCatching { Parallel.map(10) { if (it == 7) throw CancelledException() else it } }.exceptionOrNull()
        assertEquals(CancelledException::class, err!!::class)
    }
}
