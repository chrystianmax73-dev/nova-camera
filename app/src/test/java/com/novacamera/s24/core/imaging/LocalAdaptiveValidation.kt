package com.novacamera.s24.core.imaging

import java.io.DataInputStream
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Referências locais opcionais, nunca enviadas ao CI. Mesmo formato .rgb do dossiê. */
class LocalAdaptiveValidation {
    @Test fun abOnLocalReferences() {
        val dir = System.getenv("NOVA_AB_RAW")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val report = StringBuilder("arquivo;zoom;protecao;difRGB;nsLegado;nsAdaptive;limitesAB
")
        dir!!.listFiles { f -> f.extension == "rgb" }!!.sortedBy { it.name }.forEach { file ->
            DataInputStream(file.inputStream().buffered()).use { input ->
                val w = input.readInt(); val h = input.readInt()
                val px = IntArray(w*h) { Px.argb(input.readUnsignedByte(),input.readUnsignedByte(),input.readUnsignedByte()) }
                val zoom = if (file.name.startsWith("10x")) 10f else 1f
                val t0 = System.nanoTime()
                val legacy = Adaptive.plan(px,w,h,ToneIntent.LIGHT,zoom)
                val t1 = System.nanoTime()
                val next = AdaptiveImaging.plan(px,w,h,ToneIntent.LIGHT,zoom)
                val t2 = System.nanoTime()
                fun render(p: Adaptive.Plan) = px.copyOf().also {
                    if (!p.skip) ToneMap(p.base,p.params,w,h,p.mid).applyTile(it,w,h,0,0,p.strength,p.imaging?.protection ?: 0f)
                }
                val a = render(legacy); val b = render(next)
                val safe = AdaptiveImaging.safeAgainstBaseline(px,a,b,w,h)
                report.append("${file.name};$zoom;${next.imaging?.protection};${a.indices.count { a[it]!=b[it] }};${t1-t0};${t2-t1};$safe\n")
                assertTrue("A/B: ${file.name}", safe)
            }
        }
        File(dir,"adaptive-ab.csv").writeText(report.toString())
    }
}
