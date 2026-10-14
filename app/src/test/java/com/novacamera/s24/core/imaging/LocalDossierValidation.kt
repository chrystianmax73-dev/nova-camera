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
    /**
     * Pasta com .rgb e um plan.txt ("nome INTENCAO zoom" por linha). Aplica o plano adaptativo
     * real (mesmo código do app) e registra a decisão de cada foto.
     */
    @Test
    fun runOnLocalPhotos() {
        val dir = System.getenv("DOSSIE_RAW")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory && File(dir, "plan.txt").exists())
        val out = File(dir, "out").apply { mkdirs() }
        val report = StringBuilder("arquivo;intencao;zoom;forca;preto;liftEV;compressao;clareza;canal_escuro;motivo\n")
        File(dir!!, "plan.txt").readLines().filter { it.isNotBlank() }.forEach { line ->
            val (name, intentName, zoomS) = line.trim().split(Regex("\\s+"))
            val (w, h, px) = read(File(dir, "$name.rgb"))
            val f = maxOf(1, maxOf(w, h) / 1024)
            val pw = w / f; val ph = h / f
            val preview = IntArray(pw * ph) { i -> px[(i / pw) * f * w + (i % pw) * f] }
            val plan = Adaptive.plan(preview, pw, ph, ToneIntent.valueOf(intentName), zoomS.toFloat())
            val res = px.copyOf()
            if (!plan.skip) ToneMap(plan.base, plan.params, w, h, plan.mid).applyTile(res, w, h, 0, 0, plan.strength)
            val p = plan.params
            report.append(String.format(Locale.US, "%s;%s;%s;%.2f;%.3f;%.2f;%.2f;%.2f;%.3f;%s\n", name, intentName, zoomS, plan.strength,
                p.blackLevel, p.shadowLiftEv, p.highlightCompression, p.clarity, Adaptive.darkChannel(preview), plan.reason))
            write(File(out, "$name.rgb"), w, h, res)
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
