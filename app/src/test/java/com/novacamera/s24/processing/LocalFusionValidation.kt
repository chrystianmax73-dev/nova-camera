package com.novacamera.s24.processing

import android.graphics.Bitmap
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Validação LOCAL do HDR com quadros reais (não versionados): roda só se DOSSIE_HDR apontar para
 * f1.jpg (referência), f2.jpg, f3.jpg… Grava DOSSIE_HDR/out/fused.jpg para medir fantasmas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LocalFusionValidation {
    @Test
    fun runHdrFusionOnRealFrames() {
        val dir = System.getenv("DOSSIE_HDR")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val files = dir!!.listFiles { f -> f.name.matches(Regex("f\\d+\\.jpg")) }!!.sortedBy { it.name }
        val sources = files.map { JpegSource(CapturedFrame(it.readBytes(), 0, null)) }
        val a = Engine.align(sources, CancelToken()) {}
        val out = Bitmap.createBitmap(sources[0].width, sources[0].height, Bitmap.Config.ARGB_8888)
        val used = Engine.fuseInto(sources, out, a, CancelToken()) {}
        sources.forEach { it.close() }
        val o = File(dir, "out").apply { mkdirs() }
        File(o, "fused.jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        a.reliability.forEachIndexed { k, r ->
            if (r == null) return@forEachIndexed
            val px = IntArray(r.w * r.h) { val v = (r.data[it] * 255).toInt().coerceIn(0, 255); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
            File(o, "rel$k.png").outputStream().use { Bitmap.createBitmap(px, r.w, r.h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        File(o, "relatorio.txt").writeText("quadros usados=$used inseguro=${a.unsafe}\n")
    }
}
