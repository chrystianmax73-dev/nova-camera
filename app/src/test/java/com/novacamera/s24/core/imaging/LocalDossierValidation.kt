package com.novacamera.s24.core.imaging

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale

/**
 * Validação local opcional com fotos reais: roda só se DOSSIE_RAW apontar para uma pasta de
 * arquivos .rgb (int largura, int altura, bytes RGB) gerados fora do repositório.
 * Aplica o motor real (mesmo código do app) e grava .rgb de saída + relatório para medição.
 */
class LocalDossierValidation {
    @Test
    fun runOnLocalPhotos() {
        val dir = System.getenv("DOSSIE_RAW")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val out = File(dir, "out").apply { mkdirs() }
        val report = StringBuilder("arquivo;intencao;liftEV;compressao;preto;p50;p01;sombras;veiled\n")
        dir!!.listFiles().orEmpty().filter { it.extension == "rgb" }.sortedBy { it.name }.forEach { f ->
            val (w, h, px) = read(f)
            for (intent in listOf(ToneIntent.LIGHT, ToneIntent.NIGHT)) {
                val luma = Planes.downsampleLuma(px, w, h, 4)
                val stats = SceneStats.of(luma)
                val params = ToneParams.plan(stats, intent)
                val res = px.copyOf()
                ToneMap(ToneMap.baseFrom(luma), params, w, h).applyTile(res, w, h, 0, 0)
                report.append(String.format(Locale.US, "%s;%s;%.2f;%.2f;%.3f;%.3f;%.3f;%.3f;%s\n", f.nameWithoutExtension, intent,
                    params.shadowLiftEv, params.highlightCompression, params.blackLevel, stats.p50, stats.p01, stats.shadowFraction, stats.veiled))
                write(File(out, "${f.nameWithoutExtension}_$intent.rgb"), w, h, res)
            }
        }
        File(out, "relatorio.csv").writeText(report.toString())
    }

    private fun read(f: File): Triple<Int, Int, IntArray> = DataInputStream(f.inputStream().buffered()).use { s ->
        val w = s.readInt(); val h = s.readInt()
        val bytes = ByteArray(w * h * 3).also { s.readFully(it) }
        Triple(w, h, IntArray(w * h) { i -> Px.argb(bytes[i * 3].toInt() and 0xFF, bytes[i * 3 + 1].toInt() and 0xFF, bytes[i * 3 + 2].toInt() and 0xFF) })
    }

    private fun write(f: File, w: Int, h: Int, px: IntArray) = DataOutputStream(f.outputStream().buffered()).use { s ->
        s.writeInt(w); s.writeInt(h)
        val bytes = ByteArray(w * h * 3)
        for (i in px.indices) { bytes[i * 3] = Px.r(px[i]).toByte(); bytes[i * 3 + 1] = Px.g(px[i]).toByte(); bytes[i * 3 + 2] = Px.b(px[i]).toByte() }
        s.write(bytes)
    }
}
