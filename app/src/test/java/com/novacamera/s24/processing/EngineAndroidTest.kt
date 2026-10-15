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

    /**
     * Bloco na borda com deslocamento para fora: só os pixels sem dado (fora do quadro) recebem a
     * referência; os de dentro vêm do quadro, na posição certa (sem deslocar o bloco inteiro e sem
     * descartar o bloco todo, o que deixava quadrados de tom diferente na foto real do dossiê).
     */
    @Test
    fun shiftedBorderUsesReferenceOnlyOutsideTheFrame() {
        val ref = IntArray(16 * 16) { 0xFF777777.toInt() }
        fun col(c: Int) = 40 + 3 * c
        val bmp = Bitmap.createBitmap(IntArray(64 * 64) { i -> val v = col(i % 64); (0xFF shl 24) or (v shl 16) or (v shl 8) or v },
            64, 64, Bitmap.Config.ARGB_8888)
        val field = Array(1) { com.novacamera.s24.core.imaging.Shift(-4, 0) }
        for (subpixel in listOf(false, true)) {
            val a = Engine.Alignment(listOf(Array(1) { com.novacamera.s24.core.imaging.Shift(0, 0) }, field),
                1, 1, 1f, 64, 64,
                listOf(com.novacamera.s24.core.imaging.ExposureLut.IDENTITY, com.novacamera.s24.core.imaging.ExposureLut.IDENTITY),
                listOf(null, null), listOf(0f, 0f), 0.002f,
                if (subpixel) listOf(Array(1) { com.novacamera.s24.core.imaging.FShift(0f, 0f) },
                    Array(1) { com.novacamera.s24.core.imaging.FShift(-4f, 0f) }) else null)
            val out = Engine.readAligned(BitmapSource(bmp), a, 1, 0, 16, 16, 16, ref)
            for (y in 0 until 16) {
                for (x in 0 until 3) assertEquals("fora do quadro (x=$x, subpixel=$subpixel)", ref[y * 16 + x], out[y * 16 + x])
                for (x in 6 until 16) {
                    val v = out[y * 16 + x] and 0xFF
                    assertTrue("x=$x, subpixel=$subpixel: $v ≠ ${col(x - 4)}", kotlin.math.abs(v - col(x - 4)) <= 2)
                }
                if (!subpixel) for (x in 3 until 6) assertEquals(if (x < 4) ref[0] and 0xFF else col(x - 4), out[y * 16 + x] and 0xFF)
            }
        }
        bmp.recycle()
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

    /** Cena com linhas finas (rejunte), exposição em [ev] EV, deslocada/girada e com um cabo em [barX]. */
    private fun bracketFrame(ev: Float, dx: Float, dy: Float, rotDeg: Float, barX: Int, seed: Long): ByteArray {
        val rnd = Random(seed)
        val c = kotlin.math.cos(Math.toRadians(rotDeg.toDouble())); val s = kotlin.math.sin(Math.toRadians(rotDeg.toDouble()))
        val px = IntArray(w * h)
        val gain = Math.pow(2.0, ev.toDouble())
        for (y in 0 until h) for (x in 0 until w) {
            // Coordenada da cena vista por este quadro (rotação em torno do centro + translação).
            val cx = x - w / 2.0; val cy = y - h / 2.0
            val sx = c * cx - s * cy + w / 2.0 + dx; val sy = s * cx + c * cy + h / 2.0 + dy
            val grout = (sx.toInt() % 80 < 3) || (sy.toInt() % 80 < 3)
            var lin = if (grout) 0.08 else 0.35 + 0.25 * sin(sx / 90.0)
            var r = lin; var g = lin * 0.95; var b = lin * 0.85
            if (x in barX until barX + 14 && y in 200 until 520) { r = 0.55; g = 0.02; b = 0.03 }
            fun enc(v: Double) = ((Math.pow((v * gain).coerceIn(0.0, 1.0), 1 / 2.2) + rnd.nextGaussian() * 0.01) * 255).toInt().coerceIn(0, 255)
            px[y * w + x] = (0xFF shl 24) or (enc(r) shl 16) or (enc(g) shl 8) or enc(b)
        }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    }

    /**
     * Regressão das falhas da 0.6 (vassoura/cadeira duplicadas): exposições −2/+1 EV deslocadas e
     * giradas pela mão, com um objeto que se move entre os quadros. A fusão não pode criar cópias
     * do objeto nem duplicar linhas finas.
     */
    @Test
    fun hdrFusionNeverCreatesGhosts() {
        val frames = listOf(
            bracketFrame(0f, 0f, 0f, 0f, 400, 1),
            bracketFrame(-2f, 6f, -4f, 0.4f, 460, 2),
            bracketFrame(1f, -5f, 5f, -0.35f, 520, 3),
        ).map { CapturedFrame(it, 0, null) }
        val sources = frames.map(::JpegSource)
        val a = Engine.align(sources, CancelToken()) {}
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Engine.fuseInto(sources, out, a, CancelToken()) {}
        sources.forEach { it.close() }
        val ref = JpegSource(frames[0]).decodeFull()
        fun red(c: Int) = ((c shr 16) and 0xFF) > 110 && ((c shr 8) and 0xFF) < 70
        // O cabo dos outros quadros (x≈460 e x≈520 antes do alinhamento) não pode aparecer.
        var ghostRed = 0
        for (y in 220 until 500 step 2) for (x in 430 until 560) if (red(out.getPixel(x, y))) ghostRed++
        assertEquals("cópias do objeto em movimento", 0, ghostRed)
        // O cabo verdadeiro continua lá.
        assertTrue(red(out.getPixel(407, 360)))
        // Linhas finas: onde a referência é lisa, a fusão não pode ter uma borda nova forte.
        fun luma(c: Int) = (0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)) / 255f
        var phantom = 0; var flat = 0
        for (y in 4 until h - 4 step 2) for (x in 4 until w - 4 step 2) {
            var refMax = 0f
            for (k in -3..3) refMax = maxOf(refMax, kotlin.math.abs(luma(ref.getPixel(x + k, y)) - luma(ref.getPixel(x + k + 1, y))),
                kotlin.math.abs(luma(ref.getPixel(x, y + k)) - luma(ref.getPixel(x, y + k + 1))))
            if (refMax > 0.04f) continue
            flat++
            val g = maxOf(kotlin.math.abs(luma(out.getPixel(x, y)) - luma(out.getPixel(x + 1, y))),
                kotlin.math.abs(luma(out.getPixel(x, y)) - luma(out.getPixel(x, y + 1))))
            if (g > 0.08f) phantom++
        }
        assertTrue("bordas fantasma: $phantom de $flat", phantom.toFloat() / flat < 0.001f)
    }

    /**
     * Blocos na borda (onde o deslocamento da mão leva parte do bloco para fora do quadro) não podem
     * sair da fusão inteiros: isso deixava quadrados de tom diferente na foto real da cadeira.
     * Mede o salto de luminância exatamente na divisa dos blocos contra o salto logo ao lado.
     */
    @Test
    fun borderTilesDoNotLeaveToneSquares() {
        val frames = listOf(
            bracketFrame(0f, 0f, 0f, 0f, 400, 1),
            bracketFrame(-2f, 6f, -4f, 0.4f, 460, 2),
            bracketFrame(1f, -5f, 5f, -0.35f, 520, 3),
        ).map { CapturedFrame(it, 0, null) }
        val sources = frames.map(::JpegSource)
        val a = Engine.align(sources, CancelToken()) {}
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Engine.fuseInto(sources, out, a, CancelToken()) {}
        sources.forEach { it.close() }
        fun luma(x: Int, y: Int) = out.getPixel(x, y).let { 0.2126f * ((it shr 16) and 0xFF) + 0.7152f * ((it shr 8) and 0xFF) + 0.0722f * (it and 0xFF) }
        var seam = 0f; var beside = 0f
        for (b in Engine.TILE until w step Engine.TILE) for (y in 0 until h) {
            seam += kotlin.math.abs(luma(b, y) - luma(b - 1, y)); beside += kotlin.math.abs(luma(b + 3, y) - luma(b + 2, y))
        }
        for (b in Engine.TILE until h step Engine.TILE) for (x in 0 until w) {
            seam += kotlin.math.abs(luma(x, b) - luma(x, b - 1)); beside += kotlin.math.abs(luma(x, b + 3) - luma(x, b + 2))
        }
        assertTrue("divisa dos blocos ${seam / beside}× o salto vizinho", seam / beside < 1.4f)
    }

    /** Quadro impossível de alinhar (giro grande): é descartado e a referência sai intacta. */
    @Test
    fun unsafeFusionFallsBackToReference() {
        val frames = listOf(
            bracketFrame(0f, 0f, 0f, 0f, 400, 1),
            bracketFrame(-2f, 30f, 25f, 6f, 460, 2),
        ).map { CapturedFrame(it, 0, null) }
        val sources = frames.map(::JpegSource)
        val a = Engine.align(sources, CancelToken()) {}
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val used = Engine.fuseInto(sources, out, a, CancelToken()) {}
        sources.forEach { it.close() }
        assertEquals("fusão insegura deve usar só a referência (inseguro=${a.unsafe})", 1, used)
        val ref = JpegSource(frames[0]).decodeFull()
        for ((x, y) in listOf(10 to 10, 400 to 300, 700 to 600)) assertEquals(ref.getPixel(x, y), out.getPixel(x, y))
    }

    /**
     * Cena de bordas suaves (como depois da lente: a óptica limita a nitidez antes do sensor),
     * vista com deslocamento fracionário (dx, dy); [noise] = 0 dá a verdade.
     */
    private fun subpixelScene(dx: Double, dy: Double, seed: Long, noise: Double): IntArray {
        val rnd = Random(seed)
        return IntArray(w * h) { i ->
            val x = i % w + dx; val y = i / w + dy
            val v = 90 + 50 * sin(x / 9.0) * sin(y / 13.0) + 45 * kotlin.math.tanh(2.5 * sin(x / 6.0) * sin(y / 8.0))
            val g = (v + rnd.nextGaussian() * noise).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
    }

    private fun jpeg(px: IntArray) = ByteArrayOutputStream().also {
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 98, it)
    }.toByteArray()

    /**
     * Com deslocamentos fracionários, cada quadro alinhado com subpixel fica mais perto da cena
     * real do que com alinhamento inteiro. (O efeito no resultado final foi medido na rajada real do
     * dossiê: erro nas bordas 4,28 → 4,00; numa cena sintética de bordas duras a suavização comum da
     * interpolação pode empatar ou perder — ver docs/STATUS.md.)
     */
    @Test
    fun subpixelAlignmentPutsEachFrameCloserToTheScene() {
        val shifts = listOf(0.0 to 0.0, 0.3 to 0.7, -1.2 to 0.4, 2.6 to -1.35, 0.15 to -2.45)
        val frames = shifts.mapIndexed { i, (dx, dy) -> CapturedFrame(jpeg(subpixelScene(dx, dy, 50L + i, 10.0)), 0, null) }
        val truth = subpixelScene(0.0, 0.0, 0, 0.0)
        fun errors(sub: Boolean): List<Double> {
            val sources = frames.map(::JpegSource)
            val a = Engine.align(sources, CancelToken(), subpixel = sub) {}
            val tw = 256; val th = 256; val x0 = 256; val y0 = 256
            val ref = IntArray(tw * th).also { sources[0].read(x0, y0, tw, th, it) }
            val out = (1 until sources.size).map { k ->
                val t = Engine.readAligned(sources[k], a, k, x0, y0, tw, th, ref)
                var e = 0.0
                for (i in t.indices) { val d = ((t[i] shr 8) and 0xFF) - ((truth[(y0 + i / tw) * w + x0 + i % tw] shr 8) and 0xFF); e += d * d }
                kotlin.math.sqrt(e / t.size)
            }
            sources.forEach { it.close() }
            return out
        }
        val integer = errors(false); val sub = errors(true)
        for (k in integer.indices) assertTrue("quadro ${k + 1}: subpixel ${sub[k]} ≥ inteiro ${integer[k]}", sub[k] < integer[k] * 0.9)
    }
}

