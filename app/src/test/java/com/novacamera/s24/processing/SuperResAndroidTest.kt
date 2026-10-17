package com.novacamera.s24.processing

import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Random
import kotlin.math.log10
import kotlin.math.sin

/**
 * Caminho completo do zoom com super-resolução, como no aparelho: alinhamento do motor (global,
 * regiões, subpixel) + montagem 2× do centro. Quadros = cena fina reduzida 2× com tremido de mão.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SuperResAndroidTest {
    private val w = 480; private val h = 360

    private fun truth(x: Double, y: Double): Double =
        (0.5 + 0.2 * sin(x * 0.05) * sin(y * 0.04) + 0.16 * sin(x * 2.1 + y * 0.3)).coerceIn(0.0, 1.0)

    /** Quadro w×h que vê a cena (resolução 2×) deslocada por (sx, sy) pixels da cena. */
    private fun frame(sx: Double, sy: Double, rnd: Random): Bitmap {
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            var s = 0.0
            for (dy in 0..1) for (dx in 0..1) s += truth(2.0 * x + dx + sx, 2.0 * y + dy + sy)
            val v = ((s / 4 + rnd.nextGaussian() * 0.008) * 255).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Erro contra a cena verdadeira no centro ampliado 2× (a parte que a foto final mostra). */
    private fun psnr(out: Bitmap): Double {
        var e = 0.0; var n = 0
        val cropX = (w - w / 2.0) / 2.0; val cropY = (h - h / 2.0) / 2.0
        for (y in 20 until h - 20) for (x in 20 until w - 20) {
            // Pixel de saída (x, y) ↔ cena (2·(cropX + (x + 0,5)/2 − 0,5) + 0,5)
            val qx = cropX + (x + 0.5) / 2 - 0.5; val qy = cropY + (y + 0.5) / 2 - 0.5
            val d = (out.getPixel(x, y) and 0xFF) / 255.0 - truth(2 * qx + 0.5, 2 * qy + 0.5)
            e += d * d; n++
        }
        return 10 * log10(1.0 / (e / n))
    }

    @Test fun superResolucaoGanhaDoZoomDeUmQuadro() {
        val rnd = Random(5)
        val shifts = listOf(0.0 to 0.0) + List(7) { (rnd.nextDouble() * 6 - 3) to (rnd.nextDouble() * 6 - 3) }
        val bitmaps = shifts.map { (sx, sy) -> frame(sx, sy, rnd) }
        val sources = bitmaps.map { BitmapSource(it) }
        val a = Engine.align(sources, CancelToken()) {}
        val sr = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val n = Engine.superResInto(sources, sr, a, 2f, CancelToken()) {}
        val one = Engine.cropCenter(bitmaps[0].copy(Bitmap.Config.ARGB_8888, true), 2f)
        val pSr = psnr(sr); val pOne = psnr(one)
        System.getenv("SR_OUT")?.let { dir ->
            java.io.File(dir, "sr.png").outputStream().use { sr.compress(Bitmap.CompressFormat.PNG, 100, it) }
            java.io.File(dir, "um.png").outputStream().use { one.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        println("Engine: $n quadros, PSNR super-resolução=%.2f dB, zoom de um quadro=%.2f dB".format(pSr, pOne))
        assertTrue(n >= 6)
        assertTrue("super-resolução deveria ganhar ≥ 1 dB: $pSr vs $pOne", pSr > pOne + 1.0)
    }

    @Test fun giroEEspelhoDiretoNaSaidaIguaisAoOrient() {
        val rnd = Random(9)
        val bitmaps = List(3) { frame(it * 0.7, it * 0.4, rnd) }
        val sources = bitmaps.map { BitmapSource(it) }
        val a = Engine.align(sources, CancelToken()) {}
        for ((rot, mirror) in listOf(90 to false, 180 to false, 270 to false, 90 to true, 0 to true)) {
            val plain = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
            Engine.superResInto(sources, plain, a, 1f, CancelToken()) {}
            val expected = Engine.orient(plain, rot, mirror)
            val (got, _) = Engine.superRes(sources, a, 1f, 2f, rot, mirror, CancelToken()) {}
            assertTrue("dimensões $rot/$mirror", got.width == expected.width && got.height == expected.height)
            var diff = 0
            for (y in 0 until got.height step 7) for (x in 0 until got.width step 7) if (got.getPixel(x, y) != expected.getPixel(x, y)) diff++
            assertTrue("pixels diferentes com giro $rot espelho $mirror: $diff", diff == 0)
        }
    }

    @Test fun altaResolucaoDobraOTamanho() {
        val rnd = Random(2)
        val sources = List(4) { BitmapSource(frame(it * 0.5, it * 0.3, rnd)) }
        val (out, n) = Engine.superRes(sources, Engine.align(sources, CancelToken()) {}, 1f, 2f, 90, false, CancelToken()) {}
        assertTrue(out.width == h * 2 && out.height == w * 2 && n >= 3)
    }
}
