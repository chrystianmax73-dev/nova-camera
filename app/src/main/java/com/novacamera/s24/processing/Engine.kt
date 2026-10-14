package com.novacamera.s24.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import com.novacamera.s24.core.imaging.Align
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

/** Um quadro capturado em memória (JPEG do sensor, ainda sem recorte/rotação). */
class CapturedFrame(val jpeg: ByteArray, val rotationDegrees: Int, val crop: Rect?, val evIndex: Int = 0)

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
            band = decoder.decodeRegion(Rect(crop.left, crop.top + bandY0, crop.right, crop.top + bandY1), opts)
                ?: error("falha ao decodificar faixa")
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

    class Alignment(val fields: List<Array<Shift>>, val gx: Int, val gy: Int, val scale: Float, val lw: Int, val lh: Int)

    /**
     * Alinha todos os quadros ao primeiro (global + por regiões), em resolução reduzida.
     * Quadros de exposições diferentes são normalizados pelo ganho antes da comparação.
     */
    fun align(frames: List<FrameSource>, cancel: CancelToken, progress: (Float) -> Unit): Alignment {
        val factor = max(2, max(frames[0].width, frames[0].height) / 2000)
        val ref = frames[0].luma(factor)
        val gx = 6; val gy = 8
        val fields = ArrayList<Array<Shift>>()
        fields += Array(gx * gy) { Shift(0, 0) }
        for (i in 1 until frames.size) {
            if (cancel.cancelled) throw CancelledException()
            val mov = frames[i].luma(factor)
            val gain = Fusion.exposureGain(ref, mov)
            if (gain !in 0.97f..1.03f) for (k in mov.data.indices) mov.data[k] = (mov.data[k] / gain).coerceIn(0f, 1f)
            val global = Align.globalShift(ref, mov, maxShift = max(ref.w, ref.h) / 20)
            fields += Align.tileShifts(ref, mov, global, gx, gy)
            progress(i.toFloat() / frames.size)
        }
        return Alignment(fields, gx, gy, factor.toFloat(), ref.w, ref.h)
    }

    private fun shiftFor(a: Alignment, k: Int, x0: Int, y0: Int, tw: Int, th: Int): Shift =
        Align.shiftAt(a.fields[k], a.gx, a.gy, a.lw, a.lh, (x0 + tw / 2f) / a.scale, (y0 + th / 2f) / a.scale, a.scale)

    /** Noite/redução de ruído: fusão robusta de N quadros de mesma exposição. Retorna fração rejeitada. */
    fun mergeInto(frames: List<FrameSource>, out: Bitmap, a: Alignment, cancel: CancelToken, progress: (Float) -> Unit): Float {
        val w = out.width; val h = out.height
        val sw = min(w, 1024); val sh = min(h, 1024)
        val sample = IntArray(sw * sh).also { frames[0].read((w - sw) / 2, (h - sh) / 2, sw, sh, it) }
        val sigma = Merge.estimateNoise(sample, sw, sh)
        var rejected = 0.0; var count = 0
        val total = tileCount(w, h)
        var done = 0
        tiles(w, h) { x0, y0, tw, th ->
            if (cancel.cancelled) throw CancelledException()
            val n = tw * th
            val ref = IntArray(n).also { frames[0].read(x0, y0, tw, th, it) }
            val others = (1 until frames.size).map { k ->
                val s = shiftFor(a, k, x0, y0, tw, th)
                IntArray(n).also { frames[k].read(x0 + s.dx, y0 + s.dy, tw, th, it) }
            }
            val r = Merge.mergeTile(ref, others, sigma)
            out.setPixels(r.pixels, 0, tw, x0, y0, tw, th)
            rejected += r.rejectedFraction; count++
            done++; progress(done.toFloat() / total)
        }
        return if (count == 0) 0f else (rejected / count).toFloat()
    }

    /** HDR: fusão das exposições alinhadas (índice 0 = referência), com antifantasma. */
    fun fuseInto(frames: List<FrameSource>, out: Bitmap, a: Alignment, cancel: CancelToken, progress: (Float) -> Unit) {
        val w = out.width; val h = out.height
        val factor = max(4, max(w, h) / 768)
        val lumas = frames.mapIndexed { k, f ->
            val l = f.luma(factor)
            if (k == 0) l else {
                // Desloca a luminância reduzida pelo alinhamento (centro) para os pesos coincidirem.
                val s = shiftFor(a, k, w / 2, h / 2, 1, 1)
                val dx = s.dx / factor; val dy = s.dy / factor
                Plane(l.w, l.h, FloatArray(l.w * l.h) { i -> l[(i % l.w + dx).coerceIn(0, l.w - 1), (i / l.w + dy).coerceIn(0, l.h - 1)] })
            }
        }
        val weights = Fusion.weights(lumas, lumas[0])
        val total = tileCount(w, h)
        var done = 0
        tiles(w, h) { x0, y0, tw, th ->
            if (cancel.cancelled) throw CancelledException()
            val n = tw * th
            val tilesK = frames.indices.map { k ->
                val s = if (k == 0) Shift(0, 0) else shiftFor(a, k, x0, y0, tw, th)
                IntArray(n).also { frames[k].read(x0 + s.dx, y0 + s.dy, tw, th, it) }
            }
            out.setPixels(Fusion.blendTile(tilesK, weights, tw, th, x0, y0, w, h), 0, tw, x0, y0, tw, th)
            done++; progress(done.toFloat() / total)
        }
    }

    /** Planeja o tom da cena (parâmetros + camada-base). */
    fun planTone(bmp: Bitmap, intent: ToneIntent): Pair<ToneParams, Plane> {
        val factor = max(2, max(bmp.width, bmp.height) / 1024)
        val l = luma(bmp, factor)
        return ToneParams.plan(SceneStats.of(l), intent) to ToneMap.baseFrom(l)
    }

    /** Aplica tom local (+ visual Food, se pedido) no lugar. */
    fun toneInPlace(bmp: Bitmap, params: ToneParams, base: Plane, strength: Float, food: Boolean, cancel: CancelToken, progress: (Float) -> Unit) {
        val map = ToneMap(base, params, bmp.width, bmp.height)
        val total = tileCount(bmp.width, bmp.height)
        var done = 0
        tiles(bmp.width, bmp.height) { x0, y0, tw, th ->
            if (cancel.cancelled) throw CancelledException()
            val buf = IntArray(tw * th).also { bmp.getPixels(it, 0, tw, x0, y0, tw, th) }
            map.applyTile(buf, tw, th, x0, y0, strength)
            if (food) FoodLook.applyTile(buf, strength)
            bmp.setPixels(buf, 0, tw, x0, y0, tw, th)
            done++; progress(done.toFloat() / total)
        }
    }

    /** Resolução da máscara refinada do retrato para uma foto w×h. */
    fun matteFactor(w: Int, h: Int) = max(1, max(w, h) / 1536)

    /** Refina a máscara grosseira da segmentação nas bordas reais da foto. */
    fun refineMatte(bmp: Bitmap, coarseMask: Plane): Plane =
        Portrait.refineMatte(coarseMask, luma(bmp, matteFactor(bmp.width, bmp.height)))

    /**
     * Retrato: o fundo (fora de [matte]) é desfocado em resolução reduzida e composto em blocos;
     * a pessoa é copiada sem alteração. [strength] 0..1 controla o raio do desfoque.
     */
    fun portraitInPlace(bmp: Bitmap, matte: Plane, strength: Float, cancel: CancelToken, progress: (Float) -> Unit) {
        val w = bmp.width; val h = bmp.height
        val s = strength.coerceIn(0f, 1f)
        if (s <= 0.01f) { progress(1f); return }
        if (cancel.cancelled) throw CancelledException()
        val sf = max(2, max(w, h) / 1000)
        val small = Bitmap.createScaledBitmap(bmp, max(1, w / sf), max(1, h / sf), true)
        val sw = small.width; val sh = small.height
        val smallPx = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
        if (small !== bmp) small.recycle()
        val smallMatte = Planes.resize(matte, sw, sh)
        val near = Portrait.blurBackground(smallPx, sw, sh, smallMatte, max(1, (3 + 6 * s).toInt()))
        val far = Portrait.blurBackground(smallPx, sw, sh, smallMatte, max(1, (6 + 24 * s).toInt()))
        val dist = Portrait.backgroundDistance(matte)
        progress(0.4f)
        val total = tileCount(w, h)
        var done = 0
        tiles(w, h) { x0, y0, tw, th ->
            if (cancel.cancelled) throw CancelledException()
            val buf = IntArray(tw * th).also { bmp.getPixels(it, 0, tw, x0, y0, tw, th) }
            Portrait.compositeTile(buf, tw, th, x0, y0, w, h, matte, dist, near, far, sw, sh)
            bmp.setPixels(buf, 0, tw, x0, y0, tw, th)
            done++; progress(0.4f + 0.6f * (done.toFloat() / total))
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
