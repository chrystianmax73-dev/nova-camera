package com.novacamera.s24.processing

import android.graphics.Bitmap
import com.novacamera.s24.core.imaging.Merge
import com.novacamera.s24.core.imaging.ToneIntent
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Validação LOCAL com quadros reais (não versionados): roda só se DOSSIE_BURST apontar para uma
 * pasta com f1.jpg…fN.jpg. Executa o mesmo caminho do Noturno no aparelho (JPEG em faixas,
 * alinhamento, fusão robusta, tom NIGHT) e grava o resultado e métricas em DOSSIE_BURST/out.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LocalBurstValidation {
    @Test
    fun runNightPipelineOnRealBurst() {
        val dir = System.getenv("DOSSIE_BURST")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val files = dir!!.listFiles { f -> f.name.matches(Regex("f\\d+\\.jpg")) }!!.sortedBy { it.name }
        val frames = files.map { CapturedFrame(it.readBytes(), 0, null) }
        val sources = frames.map(::JpegSource)
        val t0 = System.nanoTime()
        val a = Engine.align(sources, CancelToken()) {}
        val out = Bitmap.createBitmap(sources[0].width, sources[0].height, Bitmap.Config.ARGB_8888)
        val rejected = Engine.mergeInto(sources, out, a, CancelToken()) {}
        val tMerge = (System.nanoTime() - t0) / 1e6
        val ref = sources[0].decodeFull()
        sources.forEach { it.close() }
        val plan = Engine.planTone(out, ToneIntent.NIGHT)
        val p = plan.reason
        val toned = out.copy(Bitmap.Config.ARGB_8888, true)
        Engine.toneInPlace(toned, plan, 1f, false, CancelToken()) {}
        val o = File(dir, "out").apply { mkdirs() }
        File(o, "ref.jpg").outputStream().use { ref.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        File(o, "merged.jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        File(o, "night.jpg").outputStream().use { toned.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        fun noise(b: Bitmap): Float {
            val w = minOf(600, b.width); val h = minOf(600, b.height)
            val px = IntArray(w * h).also { b.getPixels(it, 0, w, (b.width - w) / 2, (b.height - h) / 2, w, h) }
            return Merge.estimateNoise(px, w, h)
        }
        val shifts = a.fields.drop(1).joinToString { f -> val s = f[f.size / 2]; "(${(s.dx * a.scale).toInt()},${(s.dy * a.scale).toInt()})" }
        File(o, "relatorio.txt").writeText(
            "quadros=${frames.size} tamanho=${ref.width}x${ref.height}\n" +
                "deslocamentos centrais=$shifts\n" +
                "rejeição por movimento=${"%.3f".format(rejected)}\n" +
                "ruído ref=${"%.2f".format(noise(ref))} fundido=${"%.2f".format(noise(out))}\n" +
                "tempo alinhamento+fusão (JVM)=${"%.0f".format(tMerge)} ms\n" +
                "tom: $p\n")
    }
}
