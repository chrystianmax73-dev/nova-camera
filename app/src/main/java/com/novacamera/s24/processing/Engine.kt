package com.novacamera.s24.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import com.novacamera.s24.core.imaging.Adaptive
import com.novacamera.s24.core.imaging.Align
import com.novacamera.s24.core.imaging.Consistency
import com.novacamera.s24.core.imaging.ExposureLut
import com.novacamera.s24.core.imaging.FShift
import com.novacamera.s24.core.imaging.FoodLook
import com.novacamera.s24.core.imaging.Fusion
import com.novacamera.s24.core.imaging.Merge
import com.novacamera.s24.core.imaging.Plane
import com.novacamera.s24.core.imaging.Planes
import com.novacamera.s24.core.imaging.Portrait
import com.novacamera.s24.core.imaging.Px
import com.novacamera.s24.core.imaging.SceneStats
import com.novacamera.s24.core.imaging.Shift
import com.novacamera.s24.core.imaging.ToneIntent
import com.novacamera.s24.core.imaging.ToneMap
import com.novacamera.s24.core.imaging.ToneParams
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Um quadro capturado em memória (JPEG do sensor, ainda sem recorte/rotação). */
class CapturedFrame(val jpeg: ByteArray, val rotationDegrees: Int, val crop: Rect?, val evIndex: Int = 0, val optics: com.novacamera.s24.core.CaptureOptics.Evidence? = null)

/** Cancelamento cooperativo: verificado entre blocos de processamento. */
class CancelToken { @Volatile var cancelled = false }

class CancelledException : RuntimeException("Processamento cancelado")

/**
 * Fonte de pixels de um quadro, lida por regiões. Na fusão de vários quadros, cada um fica só
 * como JPEG na memória (~4 MB) e é decodificado em faixas: 6 quadros de 12 MP não precisam de
 * 6 × 48 MB de Bitmaps.
 */
interface FrameSource {
    val width: Int
    val height: Int
    /** Lê a região (x, y, w, h), presa às bordas, em [buf] (linha a linha, largura w). */
    fun read(x: Int, y: Int, w: Int, h: Int, buf: IntArray)
    /** Luminância média em blocos factor×factor. */
    fun luma(factor: Int): Plane
    fun close() {}
}

class BitmapSource(val bitmap: Bitmap) : FrameSource {
    override val width get() = bitmap.width
    override val height get() = bitmap.height
    override fun read(x: Int, y: Int, w: Int, h: Int, buf: IntArray) {
        val sx = x.coerceIn(0, width - w); val sy = y.coerceIn(0, height - h)
        bitmap.getPixels(buf, 0, w, sx, sy, w, h)
    }
    override fun luma(factor: Int) = Engine.luma(bitmap, factor)
}

/** JPEG decodificado sob demanda em faixas horizontais (com cache da faixa atual). */
class JpegSource(frame: CapturedFrame) : FrameSource {
    @Suppress("DEPRECATION")
    private val decoder: BitmapRegionDecoder =
        if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(frame.jpeg, 0, frame.jpeg.size)
        else BitmapRegionDecoder.newInstance(frame.jpeg, 0, frame.jpeg.size, false)!!
    private val crop: Rect = frame.crop?.takeIf {
        it.width() > 0 && it.height() > 0 && it.right <= decoder.width && it.bottom <= decoder.height
    } ?: Rect(0, 0, decoder.width, decoder.height)
    override val width = crop.width()
    override val height = crop.height()
    private var band: Bitmap? = null
    private var decodes = 0L
    private var decodeNs = 0L
    private var maxBandBytes = 0L
    fun cacheReport() = "faixas=$decodes · decode=${decodeNs/1_000_000}ms · cache pico=$maxBandBytes bytes"
    private var bandY0 = 0
    private var bandY1 = 0
    private val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }

    override fun read(x: Int, y: Int, w: Int, h: Int, buf: IntArray) {
        val sx = x.coerceIn(0, width - w); val sy = y.coerceIn(0, height - h)
        if (band == null || sy < bandY0 || sy + h > bandY1) {
            band?.recycle()
            // Faixa com margem para os deslocamentos de alinhamento (lida uma vez por fileira de blocos).
            bandY0 = max(0, sy - BAND_MARGIN)
            bandY1 = min(height, sy + h + BAND_MARGIN + Engine.TILE)
            val at = System.nanoTime()
            band = decoder.decodeRegion(Rect(crop.left, crop.top + bandY0, crop.right, crop.top + bandY1), opts)
                ?: error("falha ao decodificar faixa")
            decodes++; decodeNs += System.nanoTime()-at
            maxBandBytes = maxOf(maxBandBytes, band!!.allocationByteCount.toLong())
        }
        band!!.getPixels(buf, 0, w, sx, sy - bandY0, w, h)
    }

    override fun luma(factor: Int): Plane {
        var sample = 1
        while (sample * 2 <= factor) sample *= 2
        val small = decoder.decodeRegion(crop, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888; inSampleSize = sample
        }) ?: error("falha ao decodificar")
        val l = Engine.luma(small, 1)
        small.recycle()
        val tw = max(1, width / factor); val th = max(1, height / factor)
        return if (l.w == tw && l.h == th) l else Planes.resize(l, tw, th)
    }

    /** Quadro inteiro como Bitmap mutável (quando o processamento precisa da imagem toda). */
    fun decodeFull(): Bitmap = decoder.decodeRegion(crop, BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888; inMutable = true
    }) ?: error("JPEG inválido")

    override fun close() { band?.recycle(); band = null; decoder.recycle() }

    companion object { const val BAND_MARGIN = 320 }
}

/**
 * Executa os algoritmos do núcleo (core/imaging) sobre Bitmaps em blocos, para manter a memória
 * de trabalho pequena mesmo em 12–50 MP. Roda sempre fora da thread de interface.
 */
object Engine {
    const val TILE = 256

    /** Luminância média por blocos factor×factor, lida em faixas (sem copiar a imagem inteira). */
    fun luma(bmp: Bitmap, factor: Int): Plane {
        val w = bmp.width; val h = bmp.height
        val out = Plane(max(1, w / factor), max(1, h / factor))
        val band = IntArray(w * factor)
        for (oy in 0 until out.h) {
            bmp.getPixels(band, 0, w, 0, oy * factor, w, min(factor, h - oy * factor))
            for (ox in 0 until out.w) {
                var s = 0f
                for (yy in 0 until factor) for (xx in 0 until factor) s += Px.luma(band[yy * w + ox * factor + xx])
                out[ox, oy] = s / (factor * factor)
            }
        }
        return out
    }

    private inline fun tiles(w: Int, h: Int, block: (x0: Int, y0: Int, tw: Int, th: Int) -> Unit) {
        var y = 0
        while (y < h) { var x = 0; while (x < w) { block(x, y, min(TILE, w - x), min(TILE, h - y)); x += TILE }; y += TILE }
    }

    private fun tileCount(w: Int, h: Int) = ((w + TILE - 1) / TILE) * ((h + TILE - 1) / TILE)

    /**
     * Resultado do alinhamento. Para cada quadro: campo de deslocamentos, curva de exposição até a
     * referência e mapa de confiabilidade. [usable] diz se o quadro pode entrar na fusão.
     */
    class Alignment(
        val fields: List<Array<Shift>>, val gx: Int, val gy: Int, val scale: Float, val lw: Int, val lh: Int,
        val luts: List<ExposureLut>, val reliability: List<Plane?>, val unsafe: List<Float>, val noise: Float,
        /** Campos com fração de pixel (null = alinhamento só inteiro). */
        val fieldsF: List<Array<FShift>>? = null,
        /** Ruído usado na verificação de cada quadro (máximo entre o espacial e o medido no par). */
        val pairNoise: List<Float> = emptyList(),
    ) {
        val usable: List<Boolean> = unsafe.mapIndexed { k, u -> k == 0 || u < MAX_UNSAFE }
        val usedFrames get() = usable.count { it }
    }

    /** Acima desta fração de área inconsistente o quadro é descartado (fusão insegura). */
    const val MAX_UNSAFE = 0.22f

    /**
     * Alinha todos os quadros ao primeiro: curva de exposição por casamento de histogramas,
     * deslocamento global hierárquico e campo por regiões (12×16, absorve rotação leve da mão).
     * Em seguida mede, pixel a pixel, se cada quadro alinhado "bate" com a referência.
     */
    fun align(frames: List<FrameSource>, cancel: CancelToken, subpixel: Boolean = true, retainReliability: Boolean = true, temporalNoise: Boolean = false, progress: (Float) -> Unit): Alignment {
        val factor = max(2, max(frames[0].width, frames[0].height) / 2000)
        val ref = frames[0].luma(factor)
        val noise = Consistency.noiseOf(ref, correlated = temporalNoise)
        val gx = 12; val gy = 16
        val fields = ArrayList<Array<Shift>>()
        val luts = ArrayList<ExposureLut>()
        val rel = ArrayList<Plane?>()
        val unsafe = ArrayList<Float>()
        fields += Array(gx * gy) { Shift(0, 0) }; luts += ExposureLut.IDENTITY; rel += null; unsafe += 0f
        val fieldsF = ArrayList<Array<FShift>>()
        fieldsF += Array(gx * gy) { FShift(0f, 0f) }
        // Cada quadro é alinhado à referência de forma independente: em paralelo ([Parallel]), com o
        // resultado montado na ordem dos quadros (igual ao de um núcleo só).
        class One(val field: Array<Shift>, val lut: ExposureLut, val rel: Plane?, val unsafe: Float, val fieldF: Array<FShift>?, val pair: Float)
        val pairs = ArrayList<Float>().apply { add(noise) }
        val doneFrames = java.util.concurrent.atomic.AtomicInteger()
        val each = Parallel.map(frames.size - 1) { j ->
            val i = j + 1
            if (cancel.cancelled) throw CancelledException()
            val mov = frames[i].luma(factor)
            val lut = ExposureLut.match(ref, mov)
            val movN = Plane(mov.w, mov.h, FloatArray(mov.data.size) { lut.map(mov.data[it]) })
            val global = Align.globalShift(ref, movN, maxShift = max(ref.w, ref.h) / 20)
            val field = Align.tileShifts(ref, movN, global, gx, gy, radius = 3)
            val pair = max(noise, Consistency.pairNoise(ref, mov, field, gx, gy, lut))
            val r = Consistency.reliability(ref, mov, field, gx, gy, lut, pair)
            val one = One(field, lut, if (retainReliability) r else null, Consistency.unsafeFraction(r, ref),
                if (subpixel) Align.refineField(ref, movN, field, global, gx, gy) else null, pair)
            progress(doneFrames.incrementAndGet().toFloat() / frames.size)
            one
        }
        for (o in each) {
            fields += o.field; luts += o.lut; rel += o.rel; unsafe += o.unsafe; pairs += o.pair
            o.fieldF?.let { fieldsF += it }
        }
        return Alignment(fields, gx, gy, factor.toFloat(), ref.w, ref.h, luts, rel, unsafe, noise, if (subpixel) fieldsF else null, pairs)
    }

    private fun shiftFor(a: Alignment, k: Int, x0: Int, y0: Int, tw: Int, th: Int): Shift =
        Align.shiftAt(a.fields[k], a.gx, a.gy, a.lw, a.lh, (x0 + tw / 2f) / a.scale, (y0 + th / 2f) / a.scale, a.scale)

    /**
     * Lê a região do quadro [k] alinhada à referência. Com alinhamento subpixel, a fração de pixel
     * é medida aqui, em resolução cheia e por bloco: erro quadrático contra a referência em ±1 px,
     * mínimo da parábola, e interpolação bilinear. Sem ele, lê o deslocamento inteiro.
     */
    internal fun readAligned(frame: FrameSource, a: Alignment, k: Int, x0: Int, y0: Int, tw: Int, th: Int, ref: IntArray?): IntArray {
        val n = tw * th
        val ff = a.fieldsF
        if (k == 0 || ff == null || ref == null) {
            val s = if (k == 0) Shift(0, 0) else shiftFor(a, k, x0, y0, tw, th)
            val out = IntArray(n).also { readClamped(frame, x0 + s.dx, y0 + s.dy, tw, th, it) }
            if (ref != null) keepReferenceOutside(out, ref, tw, th, x0 + s.dx, y0 + s.dy, 0, 0, 0, 0, frame.width, frame.height)
            return out
        }
        val f = Align.shiftAtF(ff[k], a.gx, a.gy, a.lw, a.lh, (x0 + tw / 2f) / a.scale, (y0 + th / 2f) / a.scale, a.scale)
        val ix = kotlin.math.round(f.x).toInt(); val iy = kotlin.math.round(f.y).toInt()
        // Janela com 2 px de margem em cada lado (medir ±1 e interpolar com 4×4 vizinhos).
        val m = 2
        val rw = tw + 2 * m; val rh = th + 2 * m
        val src = IntArray(rw * rh).also { readClamped(frame, x0 + ix - m, y0 + iy - m, rw, rh, it) }
        val lumaSrc = FloatArray(src.size) { a.luts[k].map(Px.luma(src[it])) }
        val lumaRef = FloatArray(n) { Px.luma(ref[it]) }
        fun ssd(ox: Int, oy: Int): Double {
            var e = 0.0
            var y = 0
            while (y < th) {
                var x = 0
                while (x < tw) { val d = lumaSrc[(y + m + oy) * rw + x + m + ox] - lumaRef[y * tw + x]; e += d * d; x += 2 }
                y += 2
            }
            return e
        }
        fun frac(cm: Double, c0: Double, cp: Double): Float {
            val den = cm - 2 * c0 + cp
            return if (den <= 1e-9) 0f else (0.5 * (cm - cp) / den).toFloat().coerceIn(-0.5f, 0.5f)
        }
        val c0 = ssd(0, 0)
        val tx = frac(ssd(-1, 0), c0, ssd(1, 0)); val ty = frac(ssd(0, -1), c0, ssd(0, 1))
        if (kotlin.math.abs(tx) < 0.03f && kotlin.math.abs(ty) < 0.03f) {
            return IntArray(n) { i -> src[(i / tw + m) * rw + i % tw + m] }
                .also { keepReferenceOutside(it, ref, tw, th, x0 + ix, y0 + iy, 0, 0, 0, 0, frame.width, frame.height) }
        }
        // Interpolação cúbica (Catmull-Rom, separável): mantém as bordas nítidas — a bilinear
        // equivale a um filtro de média de meio pixel e borraria cada quadro deslocado.
        val bx = kotlin.math.floor(tx).toInt(); val by = kotlin.math.floor(ty).toInt()
        val wx = cubicWeights(tx - bx); val wy = cubicWeights(ty - by)
        val tmp = FloatArray(3 * tw * rh)
        for (y in 0 until rh) for (x in 0 until tw) {
            var r = 0f; var g = 0f; var b2 = 0f
            for (j in 0..3) {
                val c = src[y * rw + x + m + bx - 1 + j]; val wj = wx[j]
                r += wj * ((c shr 16) and 0xFF); g += wj * ((c shr 8) and 0xFF); b2 += wj * (c and 0xFF)
            }
            val o = (y * tw + x) * 3
            tmp[o] = r; tmp[o + 1] = g; tmp[o + 2] = b2
        }
        return IntArray(n) { i ->
            val x = i % tw; val y = i / tw
            var r = 0f; var g = 0f; var b2 = 0f
            for (j in 0..3) {
                val o = ((y + m + by - 1 + j) * tw + x) * 3; val wj = wy[j]
                r += wj * tmp[o]; g += wj * tmp[o + 1]; b2 += wj * tmp[o + 2]
            }
            Px.argb((r + 0.5f).toInt(), (g + 0.5f).toInt(), (b2 + 0.5f).toInt())
        }.also { keepReferenceOutside(it, ref, tw, th, x0 + ix, y0 + iy, bx - 1, bx + 2, by - 1, by + 2, frame.width, frame.height) }
    }

    /**
     * Lê [w]×[h] a partir de (x, y) sem mover os pixels de dentro do quadro (limitar a origem
     * deslocaria o bloco inteiro); o que cai fora repete a borda e é tratado por [keepReferenceOutside].
     */
    private fun readClamped(frame: FrameSource, x: Int, y: Int, w: Int, h: Int, buf: IntArray) {
        if (x >= 0 && y >= 0 && x + w <= frame.width && y + h <= frame.height) { frame.read(x, y, w, h, buf); return }
        val ix0 = x.coerceIn(0, frame.width - 1); val iy0 = y.coerceIn(0, frame.height - 1)
        val ix1 = (x + w).coerceIn(ix0 + 1, frame.width); val iy1 = (y + h).coerceIn(iy0 + 1, frame.height)
        val iw = ix1 - ix0; val ih = iy1 - iy0
        val inner = IntArray(iw * ih).also { frame.read(ix0, iy0, iw, ih, it) }
        for (j in 0 until h) {
            val row = ((y + j).coerceIn(iy0, iy1 - 1) - iy0) * iw
            for (i in 0 until w) buf[j * w + i] = inner[row + (x + i).coerceIn(ix0, ix1 - 1) - ix0]
        }
    }

    /**
     * Pixels cujas amostras ([lo]..[hi] em torno de origem + posição) saem do quadro não têm dado
     * real: recebem a referência. Só esses pixels — o resto do bloco continua com o quadro alinhado,
     * senão o bloco inteiro perderia a fusão e apareceria como um quadrado de tom diferente.
     */
    private fun keepReferenceOutside(
        out: IntArray, ref: IntArray, tw: Int, th: Int, ax: Int, ay: Int,
        loX: Int, hiX: Int, loY: Int, hiY: Int, fw: Int, fh: Int,
    ) {
        if (ax + loX >= 0 && ay + loY >= 0 && ax + tw - 1 + hiX < fw && ay + th - 1 + hiY < fh) return
        for (y in 0 until th) {
            val outY = ay + y + loY < 0 || ay + y + hiY >= fh
            for (x in 0 until tw) if (outY || ax + x + loX < 0 || ax + x + hiX >= fw) out[y * tw + x] = ref[y * tw + x]
        }
    }

    /** Pesos Catmull-Rom para as amostras em −1, 0, 1, 2 com fração [t] ∈ [0, 1). */
    private fun cubicWeights(t: Float): FloatArray {
        val t2 = t * t; val t3 = t2 * t
        return floatArrayOf(
            -0.5f * t3 + t2 - 0.5f * t,
            1.5f * t3 - 2.5f * t2 + 1f,
            -1.5f * t3 + 2f * t2 + 0.5f * t,
            0.5f * t3 - 0.5f * t2,
        )
    }

    /** Confiabilidade por pixel do quadro [k] numa região (amostrada do mapa em meia resolução). */
    private fun reliabilityTile(a: Alignment, k: Int, x0: Int, y0: Int, tw: Int, th: Int): FloatArray? {
        val r = a.reliability[k] ?: return null
        return FloatArray(tw * th) { i -> r.sample((x0 + i % tw + 0.5f) / a.scale - 0.5f, (y0 + i / tw + 0.5f) / a.scale - 0.5f) }
    }

    /**
     * Noite/redução de ruído: fusão robusta de N quadros de mesma exposição, só com os quadros e
     * pixels confiáveis. Retorna a fração de pixels rejeitados.
     */
    fun mergeInto(frames: List<FrameSource>, out: Bitmap, a: Alignment, cancel: CancelToken, temporalNoise: Boolean = false,
                  onNoise:((String)->Unit)?=null, progress: (Float) -> Unit): Float {
        val w = out.width; val h = out.height
        val sw = min(w, 1024); val sh = min(h, 1024)
        val sx = (w - sw) / 2; val sy = (h - sh) / 2
        val sample = IntArray(sw * sh).also { frames[0].read(sx, sy, sw, sh, it) }
        val used = frames.indices.filter { it > 0 && a.usable[it] }
        val spatial = Merge.estimateNoise(sample, sw, sh, correlated = temporalNoise)
        val temporal = Merge.temporalNoise(sample, used.take(2).map { k -> readAligned(frames[k], a, k, sx, sy, sw, sh, sample) })
        val sigma = max(spatial, temporal)
        onNoise?.invoke("sigma rejeição=$sigma níveis0..255 (espacial=$spatial, entre quadros=$temporal)")
        var rejected = 0.0; var count = 0
        val total = tileCount(w, h)
        var done = 0
        tiles(w, h) { x0, y0, tw, th ->
            if (cancel.cancelled) throw CancelledException()
            val n = tw * th
            val ref = IntArray(n).also { frames[0].read(x0, y0, tw, th, it) }
            if (used.isEmpty()) {
                out.setPixels(ref, 0, tw, x0, y0, tw, th)
            } else {
                val others = used.map { k -> readAligned(frames[k], a, k, x0, y0, tw, th, ref) }
                val r = Merge.mergeTile(ref, others, sigma, frameWeights = used.map { reliabilityTile(a, it, x0, y0, tw, th) })
                out.setPixels(r.pixels, 0, tw, x0, y0, tw, th)
                rejected += r.rejectedFraction; count++
            }
            done++; progress(done.toFloat() / total)
        }
        return if (count == 0) 1f else (rejected / count).toFloat()
    }

    /** Zoom digital de um quadro: centro 1/[factor] ampliado ao tamanho original (bilinear). */
    fun cropCenter(bmp: Bitmap, factor: Float): Bitmap {
        val cw = max(1, (bmp.width / factor).toInt()); val ch = max(1, (bmp.height / factor).toInt())
        val crop = Bitmap.createBitmap(bmp, (bmp.width - cw) / 2, (bmp.height - ch) / 2, cw, ch)
        val out = Bitmap.createScaledBitmap(crop, bmp.width, bmp.height, true)
        if (crop !== bmp && crop !== out) crop.recycle()
        if (out !== bmp) bmp.recycle()
        return out
    }

    /**
     * Super-resolução do zoom digital da tele: o centro 1/[factor] dos quadros (feitos na lente sem
     * recorte) vira a foto inteira, montada com as amostras de todos os quadros na posição exata
     * (ver [com.novacamera.s24.core.imaging.SuperRes]). Sem alinhamento ([a] null) ou com um quadro
     * só, equivale ao zoom digital de um quadro. Devolve quantos quadros entraram.
     */
    fun superResInto(frames: List<FrameSource>, out: Bitmap, a: Alignment?, factor: Float, cancel: CancelToken, progress: (Float) -> Unit): Int =
        superResTiles(frames, a, factor, out.width.toFloat() / frames[0].width, cancel, progress) { px, x0, y0, tw, th ->
            out.setPixels(px, 0, tw, x0, y0, tw, th)
        }

    /**
     * Super-resolução já na orientação final ([rotationDegrees], [mirror]), sem a cópia girada de uma
     * foto de até 48 MP (a rotação de [orient] dobraria a memória). [cropFactor] = zoom digital sobre
     * a lente (1 = quadro inteiro); [outScale] = tamanho da saída em relação ao quadro (2 = alta
     * resolução). Devolve a foto e quantos quadros entraram.
     */
    fun superRes(frames: List<FrameSource>, a: Alignment?, cropFactor: Float, outScale: Float, rotationDegrees: Int, mirror: Boolean,
                 cancel: CancelToken, progress: (Float) -> Unit): Pair<Bitmap, Int> {
        val ow = (frames[0].width * outScale).roundToInt(); val oh = (frames[0].height * outScale).roundToInt()
        val r = ((rotationDegrees % 360) + 360) % 360
        val turned = r == 90 || r == 270
        val dw = if (turned) oh else ow; val dh = if (turned) ow else oh
        val out = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
        val n = superResTiles(frames, a, cropFactor, outScale, cancel, progress) { px, x0, y0, tw, th ->
            // Bloco (x0, y0, tw, th) da saída sem giro → mesmo bloco girado/espelhado na foto final.
            val rw = if (turned) th else tw; val rh = if (turned) tw else th
            val dst = IntArray(px.size)
            for (j in 0 until th) for (i in 0 until tw) {
                var dx: Int; val dy: Int
                when (r) {
                    90 -> { dx = th - 1 - j; dy = i }
                    180 -> { dx = tw - 1 - i; dy = th - 1 - j }
                    270 -> { dx = j; dy = tw - 1 - i }
                    else -> { dx = i; dy = j }
                }
                if (mirror) dx = rw - 1 - dx
                dst[dy * rw + dx] = px[j * tw + i]
            }
            var rx = when (r) { 90 -> oh - (y0 + th); 180 -> ow - (x0 + tw); 270 -> y0; else -> x0 }
            val ry = when (r) { 90 -> x0; 180 -> oh - (y0 + th); 270 -> ow - (x0 + tw); else -> y0 }
            if (mirror) rx = dw - (rx + rw)
            out.setPixels(dst, 0, rw, rx, ry, rw, rh)
        }
        return out to n
    }

    private fun superResTiles(frames: List<FrameSource>, a: Alignment?, cropFactor: Float, outScale: Float, cancel: CancelToken,
                              progress: (Float) -> Unit, sink: (IntArray, Int, Int, Int, Int) -> Unit): Int {
        val w = frames[0].width; val h = frames[0].height
        val factor = cropFactor * outScale
        val ow = (w * outScale).roundToInt(); val oh = (h * outScale).roundToInt()
        val cropX = (w - w / cropFactor) / 2f; val cropY = (h - h / cropFactor) / 2f
        val used = frames.indices.filter { a != null && a.usable[it] }.ifEmpty { listOf(0) }
        val noise = a?.noise ?: 0.01f
        val tile = 320
        val total = ((ow + tile - 1) / tile) * ((oh + tile - 1) / tile)
        var done = 0
        val margin = 4
        // Por fileira de blocos: as leituras ficam em série (o JPEG é decodificado em faixas, na ordem
        // das fileiras, e a faixa não muda dentro da fileira), o cálculo de cada bloco roda em paralelo ([Parallel]) e a gravação volta em
        // série, na mesma ordem — a foto sai idêntica à de um núcleo só.
        class TileJob(val ox0: Int, val oy0: Int, val otw: Int, val oth: Int, val ref: com.novacamera.s24.core.imaging.SuperRes.Region,
                      val movs: List<Triple<com.novacamera.s24.core.imaging.SuperRes.Region, Int, Int>>)
        // Lote de poucos blocos por vez (não a fileira inteira): memória de trabalho pequena mesmo a 48 MP.
        val batch = 2 * Parallel.workers
        val xs = (0 until ow step tile).toList()
        for (oy0 in 0 until oh step tile) for (chunk in xs.chunked(batch)) {
            val row = ArrayList<TileJob>()
            for (ox0 in chunk) {
                if (cancel.cancelled) throw CancelledException()
                val otw = min(tile, ow - ox0); val oth = min(tile, oh - oy0)
                val rx0 = (cropX + ox0 / factor).toInt() - margin; val ry0 = (cropY + oy0 / factor).toInt() - margin
                val rw = (otw / factor).toInt() + 2 * margin + 1; val rh = (oth / factor).toInt() + 2 * margin + 1
                val refPx = IntArray(rw * rh).also { readClamped(frames[0], rx0, ry0, rw, rh, it) }
                val ref = com.novacamera.s24.core.imaging.SuperRes.Region(refPx, rx0, ry0, rw, rh)
                val movs = ArrayList<Triple<com.novacamera.s24.core.imaging.SuperRes.Region, Int, Int>>()
                if (a != null) for (k in used) {
                    if (k == 0) continue
                    val cx = (rx0 + rw / 2f) / a.scale; val cy = (ry0 + rh / 2f) / a.scale
                    val g = a.fieldsF?.let { Align.shiftAtF(it[k], a.gx, a.gy, a.lw, a.lh, cx, cy, a.scale) }
                        ?: Align.shiftAt(a.fields[k], a.gx, a.gy, a.lw, a.lh, cx, cy, a.scale).let { FShift(it.dx.toFloat(), it.dy.toFloat()) }
                    val gx = kotlin.math.round(g.x).toInt(); val gy = kotlin.math.round(g.y).toInt()
                    val m = 3
                    val movPx = IntArray((rw + 2 * m) * (rh + 2 * m)).also { readClamped(frames[k], rx0 + gx - m, ry0 + gy - m, rw + 2 * m, rh + 2 * m, it) }
                    movs += Triple(com.novacamera.s24.core.imaging.SuperRes.Region(movPx, rx0 + gx - m, ry0 + gy - m, rw + 2 * m, rh + 2 * m), gx, gy)
                }
                row += TileJob(ox0, oy0, otw, oth, ref, movs)
            }
            val results = Parallel.map(row.size) { i ->
                if (cancel.cancelled) throw CancelledException()
                val t = row[i]
                val list = ArrayList<Pair<com.novacamera.s24.core.imaging.SuperRes.Region, Pair<Float, Float>>>()
                list += t.ref to (0f to 0f)
                for ((mov, gx, gy) in t.movs) list += mov to com.novacamera.s24.core.imaging.SuperRes.estimateShift(t.ref, mov, gx, gy)
                com.novacamera.s24.core.imaging.SuperRes.mergeTile(t.otw, t.oth, t.ox0, t.oy0, factor, cropX, cropY, list, noise)
            }
            for ((i, t) in row.withIndex()) {
                sink(results[i], t.ox0, t.oy0, t.otw, t.oth)
                done++; progress(done.toFloat() / total)
            }
        }
        return used.size
    }

    /**
     * HDR: fusão das exposições alinhadas (índice 0 = referência). Cada pixel de outro quadro só
     * entra se for consistente com a referência (meia resolução + verificação em resolução cheia).
     * Devolve quantos quadros entraram; 1 = só a referência (fusão insegura).
     */
    /**
     * [onScene] recebe a luminância da cena acima do branco SDR (quadro escuro do bracket), em
     * resolução reduzida e na orientação do sensor, para o gainmap Ultra HDR; null se não houver.
     */
    fun fuseInto(
        frames: List<FrameSource>, out: Bitmap, a: Alignment, cancel: CancelToken,
        onScene: ((Plane?) -> Unit)? = null, progress: (Float) -> Unit,
    ): Int {
        val w = out.width; val h = out.height
        val used = frames.indices.filter { a.usable[it] }
        if (used.size < 2) {
            tiles(w, h) { x0, y0, tw, th ->
                val buf = IntArray(tw * th).also { frames[0].read(x0, y0, tw, th, it) }
                out.setPixels(buf, 0, tw, x0, y0, tw, th)
            }
            progress(1f)
            return 1
        }
        val factor = max(4, max(w, h) / 768)
        val lumas = used.map { k ->
            val l = frames[k].luma(factor)
            if (k == 0) l else {
                val field = a.fieldsF?.get(k) ?: Array(a.fields[k].size) { j -> FShift(a.fields[k][j].dx.toFloat(), a.fields[k][j].dy.toFloat()) }
                Plane(l.w, l.h, FloatArray(l.w * l.h) { i ->
                    val x = i % l.w; val y = i / l.w
                    val shift = Align.shiftAtF(
                        field, a.gx, a.gy, a.lw, a.lh,
                        (x + 0.5f) * factor / a.scale, (y + 0.5f) * factor / a.scale, a.scale / factor)
                    l.sample(x + shift.x, y + shift.y)
                })
            }
        }
        onScene?.invoke(runCatching { com.novacamera.s24.core.imaging.HdrGain.scene(lumas) }.getOrNull())
        val weights = Fusion.weights(lumas, lumas[0])
        val noise = a.noise
        val total = tileCount(w, h)
        var done = 0
        tiles(w, h) { x0, y0, tw, th ->
            if (cancel.cancelled) throw CancelledException()
            val n = tw * th
            val ref0 = IntArray(tw * th).also { frames[0].read(x0, y0, tw, th, it) }
            val tilesK = used.map { k -> if (k == 0) ref0 else readAligned(frames[k], a, k, x0, y0, tw, th, ref0) }
            // Pixels não confiáveis de cada quadro são trocados pela previsão a partir da referência
            // (mesma exposição daquele quadro): sem fantasma e sem "buraco" de tom no formato do objeto.
            // Só remover o peso deixaria a referência sozinha ali, com o tom de outra mistura.
            for ((j, k) in used.withIndex()) {
                if (k == 0) continue
                val r = reliabilityTile(a, k, x0, y0, tw, th)!!
                val lut = a.luts[k]
                val t = tilesK[j]
                for (i in 0 until n) {
                    // Referência estourada: decide o próprio pixel do quadro, não a vizinhança em meia resolução.
                    val p = Consistency.clippedAgreement(ref0[i], t[i], lut, noise)
                        ?: if (r[i] > 0f) r[i] * Consistency.agreement(ref0[i], t[i], lut, noise) else 0f
                    if (p >= 0.999f) continue
                    val sub = Consistency.predicted(ref0[i], lut)
                    t[i] = if (p <= 0.001f) sub else Px.argb(
                        (Px.r(sub) + (Px.r(t[i]) - Px.r(sub)) * p).toInt(),
                        (Px.g(sub) + (Px.g(t[i]) - Px.g(sub)) * p).toInt(),
                        (Px.b(sub) + (Px.b(t[i]) - Px.b(sub)) * p).toInt())
                }
            }
            out.setPixels(Fusion.blendTile(tilesK, weights, tw, th, x0, y0, w, h), 0, tw, x0, y0, tw, th)
            done++; progress(done.toFloat() / total)
        }
        return used.size
    }

    /** Prévia reduzida (lado maior ≈ 1024) usada para medir a cena e testar o plano. */
    fun preview(bmp: Bitmap, maxSide: Int = 1024): Triple<IntArray, Int, Int> {
        val f = max(1f, max(bmp.width, bmp.height) / maxSide.toFloat())
        val w = max(1, (bmp.width / f).toInt()); val h = max(1, (bmp.height / f).toInt())
        val small = if (f > 1f) Bitmap.createScaledBitmap(bmp, w, h, true) else bmp
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        if (small !== bmp) small.recycle()
        return Triple(px, w, h)
    }

    /** Planeja o tom da cena de forma adaptativa (ver [Adaptive]). */
    fun planTone(bmp: Bitmap, intent: ToneIntent, zoom: Float = 1f): Adaptive.Plan {
        val (px, w, h) = preview(bmp)
        return Adaptive.plan(px, w, h, intent, zoom)
    }

    /** Complemento automático da FOTO; plano legado continua disponível para A/B e demais modos. */
    fun planAdaptive(bmp: Bitmap, zoom: Float, facts: com.novacamera.s24.core.imaging.ImagingFacts): Adaptive.Plan {
        val (px, w, h) = preview(bmp)
        return com.novacamera.s24.core.imaging.AdaptiveImaging.plan(px, w, h, ToneIntent.LIGHT, zoom, facts)
    }

    /**
     * Aplica o plano (+ visual Food, se pedido) no lugar. [userStrength] (revisão) multiplica a
     * intensidade decidida pelo plano; 1 = a decisão automática.
     */
    fun toneInPlace(bmp: Bitmap, plan: Adaptive.Plan, userStrength: Float, food: Boolean, cancel: CancelToken, progress: (Float) -> Unit) {
        val s = (plan.strength * userStrength).coerceIn(0f, 1f)
        // ToneMap já dispensava esses planos por bloco, mas ainda copiávamos a foto inteira.
        // Food tem acabamento próprio mesmo quando o plano tonal é neutro.
        if(!food && (plan.params.isIdentity || s<=0f)) {
            if(cancel.cancelled)throw CancelledException()
            progress(1f)
            return
        }
        val map = ToneMap(plan.base, plan.params, bmp.width, bmp.height, plan.mid)
        if(!food) {
            // Faixa curta reutilizável: menos buffers e preparação geométrica repetida.
            // Mesmas coordenadas/pixels; cancelamento a cada64 linhas, sem JNI tonal.
            val rows=64
            val buffer=IntArray(bmp.width*min(rows,bmp.height))
            for(y0 in 0 until bmp.height step rows) {
                if(cancel.cancelled)throw CancelledException()
                val h=min(rows,bmp.height-y0)
                val band=if(buffer.size==bmp.width*h)buffer else IntArray(bmp.width*h)
                bmp.getPixels(band,0,bmp.width,0,y0,bmp.width,h)
                map.applyTile(band,bmp.width,h,0,y0,s,plan.imaging?.protection ?: 0f)
                bmp.setPixels(band,0,bmp.width,0,y0,bmp.width,h)
                progress((y0+h).toFloat()/bmp.height)
            }
            return
        }
        val total = tileCount(bmp.width, bmp.height)
        var done = 0
        tiles(bmp.width, bmp.height) { x0, y0, tw, th ->
            if (cancel.cancelled) throw CancelledException()
            val buf = IntArray(tw * th).also { bmp.getPixels(it, 0, tw, x0, y0, tw, th) }
            map.applyTile(buf, tw, th, x0, y0, s, plan.imaging?.protection ?: 0f)
            if (food) FoodLook.applyTile(buf, userStrength)
            bmp.setPixels(buf, 0, tw, x0, y0, tw, th)
            done++; progress(done.toFloat() / total)
        }
    }

    /** Pouca luz na foto comum: clareia meios-tons (ver [ToneParams.lowLightLift]). Devolve o EV aplicado. */
    fun lowLightInPlace(bmp: Bitmap, iso: Int?, cancel: CancelToken): Float {
        val (px, w, h) = preview(bmp)
        val luma = com.novacamera.s24.core.imaging.Planes.downsampleLuma(px, w, h, 1)
        val params = ToneParams.lowLightLift(iso, com.novacamera.s24.core.imaging.Planes.percentile(luma.data, 0.5f),
            com.novacamera.s24.core.imaging.Planes.percentile(luma.data, 0.95f)) ?: return 0f
        toneInPlace(bmp, com.novacamera.s24.core.imaging.Adaptive.Plan(params, 1f, ToneMap.baseFrom(luma), null, "pouca luz"), 1f, false, cancel) {}
        return params.midLiftEv
    }

    /** Sombras sem cor falsa de ruído (ver [com.novacamera.s24.core.imaging.ShadowChroma]). */
    fun shadowChromaInPlace(bmp: Bitmap, iso: Int?, cancel: CancelToken) {
        val k = com.novacamera.s24.core.imaging.ShadowChroma.strengthFor(iso)
        if (k <= 0f) return
        tiles(bmp.width, bmp.height) { x0, y0, tw, th ->
            if (cancel.cancelled) throw CancelledException()
            val buf = IntArray(tw * th).also { bmp.getPixels(it, 0, tw, x0, y0, tw, th) }
            com.novacamera.s24.core.imaging.ShadowChroma.applyTile(buf, k)
            bmp.setPixels(buf, 0, tw, x0, y0, tw, th)
        }
    }

    /** Perfil "Contraste": preto firme e contraste local sobre a foto já com o tom automático. */
    fun profileInPlace(bmp: Bitmap, cancel: CancelToken) {
        val (px, w, h) = preview(bmp)
        val luma = com.novacamera.s24.core.imaging.Planes.downsampleLuma(px, w, h, 1)
        val params = ToneParams.contrastProfile(com.novacamera.s24.core.imaging.Adaptive.darkChannel(px),
            com.novacamera.s24.core.imaging.Planes.percentile(luma.data, 0.5f))
        val plan = com.novacamera.s24.core.imaging.Adaptive.Plan(params, 1f, ToneMap.baseFrom(luma), ToneMap.midFrom(luma), "perfil contraste")
        toneInPlace(bmp, plan, 1f, false, cancel) {}
    }

    /** Resolução da máscara refinada do retrato para uma foto w×h. */
    fun matteFactor(w: Int, h: Int) = max(1, max(w, h) / 1536)

    /**
     * Máscara final do retrato: pessoa refinada nas bordas reais da foto, unida às regiões de
     * cabelo e acessórios (também refinadas) — fio de cabelo e armação de óculos não desfocam.
     */
    fun portraitMatte(bmp: Bitmap, person: Plane, hair: Plane?): Plane {
        val f = matteFactor(bmp.width, bmp.height)
        return Portrait.refineMatteColor(person, rgb(bmp, f * 2), rgb(bmp, f), hair)
    }

    /** Ruído do sensor na foto inteira, lido numa faixa central de até 1024 linhas. */
    private fun noiseSigma(bmp: Bitmap): Float {
        val w = bmp.width; val rows = min(bmp.height, 1024); val y0 = (bmp.height - rows) / 2
        val px = IntArray(w * rows).also { bmp.getPixels(it, 0, w, 0, y0, w, rows) }
        return Portrait.noiseSigma(px, w, rows)
    }

    /** Resultado do assunto do retrato: máscara final (resolução da máscara) e desfoque, se houver profundidade. */
    class PortraitSubject(val matte: Plane, val blur: Plane?, val note: String?)

    /**
     * Decide o assunto do retrato a partir da segmentação de pessoa (pode faltar) e da profundidade
     * estimada (pode faltar). Mesma lógica no app e na validação local. Null = sem assunto.
     */
    fun portraitSubject(bmp: Bitmap, person: Plane?, hair: Plane?, depthRaw: Plane?, tap: Pair<Float, Float>? = null,
                        report:((String)->Unit)?=null): PortraitSubject? {
        val depth = depthRaw?.let { DepthPortraitNorm.normalized(it, max(1, bmp.width / 8), max(1, bmp.height / 8)) }
        if (depth == null) return person?.let { PortraitSubject(portraitMatte(bmp, it, hair), null, null) }
        val plan = com.novacamera.s24.core.imaging.DepthPortrait.plan(depth, person, tap)
        if (!plan.fromPerson && plan.subject.data.count { it >= 0.5f } < plan.subject.data.size * 0.01f) return null
        val note = when {
            plan.fromPerson -> null
            tap != null -> "Assunto escolhido pelo toque (profundidade estimada)"
            else -> "Sem pessoa: assunto pela profundidade estimada (o que está mais perto no centro)"
        }
        val matte=portraitMatte(bmp, plan.subject, if (plan.fromPerson) hair else null)
        report?.invoke("máscara Retrato: origem=${if(plan.fromPerson) "pessoa" else if(tap!=null) "objeto/toque" else "objeto/centro"}"+
            " · profundidade=${depth.w}x${depth.h} · faixa relativa=${plan.far}..${plan.near}"+
            " · máscara=${matte.w}x${matte.h} · cobertura>=0,5=${matte.data.count{it>=.5f}.toFloat()/matte.data.size}"+
            " · valores intermediários=${matte.data.count{it>.01f && it<.99f}.toFloat()/matte.data.size}; não mede precisão de borda")
        return PortraitSubject(matte, plan.blur, note)
    }

    private object DepthPortraitNorm {
        fun normalized(raw: Plane, w: Int, h: Int) = com.novacamera.s24.core.imaging.DepthPortrait.normalize(Planes.resize(raw, w, h))
    }

    /** Estilo ao vivo (tom/temperatura) na imagem pronta, em blocos. */
    fun styleInPlace(bmp: Bitmap, style: com.novacamera.s24.core.imaging.PhotoStyle, cancel: CancelToken) {
        if (style.neutral) return
        tiles(bmp.width, bmp.height) { x0, y0, tw, th ->
            if (cancel.cancelled) throw CancelledException()
            val buf = IntArray(tw * th).also { bmp.getPixels(it, 0, tw, x0, y0, tw, th) }
            style.applyTile(buf)
            bmp.setPixels(buf, 0, tw, x0, y0, tw, th)
        }
    }

    /**
     * Detalhe fino da tele ([com.novacamera.s24.core.imaging.Detail]), em faixas com margem de 2
     * linhas (a vizinhança do desfoque não "vê" a borda da faixa). Devolve a fração alterada.
     */
    fun detailInPlace(bmp: Bitmap, zoom: Float, cancel: CancelToken): Float {
        val amount = com.novacamera.s24.core.imaging.Detail.amountFor(zoom)
        if (amount <= 0f) return 0f
        val w = bmp.width; val h = bmp.height
        val (pw, ph) = (min(w, 1024)) to (min(h, 1024))
        val probe = IntArray(pw * ph).also { bmp.getPixels(it, 0, pw, (w - pw) / 2, (h - ph) / 2, pw, ph) }
        val sigma = com.novacamera.s24.core.imaging.Detail.noiseSigma(probe, pw, ph)
        val band = 256; val margin = 3
        var changed = 0L
        var y0 = 0
        while (y0 < h) {
            if (cancel.cancelled) throw CancelledException()
            val top = max(0, y0 - margin); val bottom = min(h, y0 + band + margin)
            val rows = bottom - top
            val buf = IntArray(w * rows).also { bmp.getPixels(it, 0, w, 0, top, w, rows) }
            val validTop = y0 - top; val validBottom = min(rows, validTop + band)
            changed += com.novacamera.s24.core.imaging.Detail.applyBand(buf, w, rows, amount, sigma, validTop, validBottom)
            val keep = validBottom - validTop
            bmp.setPixels(buf, validTop * w, w, 0, y0, w, keep)
            y0 += band
        }
        return changed.toFloat() / (w.toLong() * h)
    }

    /** RGB médio (0..1) por blocos factor×factor, lido em faixas. */
    fun rgb(bmp: Bitmap, factor: Int): Array<Plane> {
        val w = bmp.width; val h = bmp.height
        val ow = max(1, w / factor); val oh = max(1, h / factor)
        val out = Array(3) { Plane(ow, oh) }
        val band = IntArray(w * factor)
        for (oy in 0 until oh) {
            val rows = min(factor, h - oy * factor)
            bmp.getPixels(band, 0, w, 0, oy * factor, w, rows)
            val part = Planes.downsampleRgb(band, w, rows, factor)
            for (k in 0 until 3) System.arraycopy(part[k].data, 0, out[k].data, oy * ow, min(ow, part[k].w))
        }
        return out
    }

    /** Refina a máscara grosseira da segmentação nas bordas reais da foto. */
    fun refineMatte(bmp: Bitmap, coarseMask: Plane): Plane =
        Portrait.refineMatte(coarseMask, luma(bmp, matteFactor(bmp.width, bmp.height)))

    /**
     * Retrato: o fundo (fora de [matte]) é desfocado em resolução reduzida e composto em blocos;
     * a pessoa é copiada sem alteração. [strength] 0..1 controla o raio do desfoque.
     */
    /** [blurMap] (profundidade estimada, ver DepthPortrait) substitui a distância ao contorno. */
    fun portraitInPlace(bmp: Bitmap, matte: Plane, strength: Float, cancel: CancelToken, blurMap: Plane? = null, progress: (Float) -> Unit) {
        val w = bmp.width; val h = bmp.height
        val s = strength.coerceIn(0f, 1f)
        if (s <= 0.01f) { progress(1f); return }
        if (cancel.cancelled) throw CancelledException()
        val sf = max(2, max(w, h) / 1000)
        val small = Bitmap.createScaledBitmap(bmp, max(1, w / sf), max(1, h / sf), true)
        val sw = small.width; val sh = small.height
        val smallPx = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
        if (small !== bmp) small.recycle()
        // A máscara já vem final de [portraitMatte] (borda firme; transição larga só no cabelo).
        val protectedMatte = matte
        val smallMatte = Planes.resize(protectedMatte, sw, sh)
        val near = Portrait.blurBackground(smallPx, sw, sh, smallMatte, max(1, (3 + 6 * s).toInt()))
        val far = Portrait.blurBackground(smallPx, sw, sh, smallMatte, max(1, (5 + 18 * s).toInt()))
        val dist = blurMap ?: Portrait.backgroundDistance(protectedMatte)
        val grain = noiseSigma(bmp)
        progress(0.4f)
        val total = tileCount(w, h)
        var done = 0
        tiles(w, h) { x0, y0, tw, th ->
            if (cancel.cancelled) throw CancelledException()
            val buf = IntArray(tw * th).also { bmp.getPixels(it, 0, tw, x0, y0, tw, th) }
            Portrait.compositeTile(buf, tw, th, x0, y0, w, h, protectedMatte, dist, near, far, sw, sh, grain, depthMode = blurMap != null)
            bmp.setPixels(buf, 0, tw, x0, y0, tw, th)
            done++; progress(0.4f + 0.6f * (done.toFloat() / total))
        }
    }

    /** Retrato · iluminação "Fundo escuro": só onde a máscara diz fundo; a pessoa não muda. */
    fun darkBackgroundInPlace(bmp: Bitmap, matte: Plane, cancel: CancelToken) {
        val w = bmp.width; val h = bmp.height
        tiles(w, h) { x0, y0, tw, th ->
            if (cancel.cancelled) throw CancelledException()
            val buf = IntArray(tw * th).also { bmp.getPixels(it, 0, tw, x0, y0, tw, th) }
            Portrait.darkenBackgroundTile(buf, tw, th, x0, y0, w, h, matte)
            bmp.setPixels(buf, 0, tw, x0, y0, tw, th)
        }
    }

    /**
     * Gira para a orientação real e, se pedido, espelha (selfie como no visor). Recicla [src]
     * quando cria uma nova imagem.
     */
    fun orient(src: Bitmap, rotationDegrees: Int, mirror: Boolean): Bitmap {
        val r = ((rotationDegrees % 360) + 360) % 360
        if (r == 0 && !mirror) return src
        val m = Matrix().apply { postRotate(r.toFloat()); if (mirror) postScale(-1f, 1f) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return if (out.isMutable) out else out.copy(Bitmap.Config.ARGB_8888, true).also { out.recycle() }
    }
}

