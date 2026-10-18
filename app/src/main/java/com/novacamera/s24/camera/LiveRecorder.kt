package com.novacamera.s24.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.novacamera.s24.processing.LiveClips
import java.io.File
import java.nio.ByteBuffer

/**
 * Nova Live: codifica os quadros da análise (YUV ~1280×960) em H.264 o tempo todo e guarda só os
 * últimos [KEEP_US] já comprimidos (~2 MB). No disparo, junta [BEFORE_US] antes e [AFTER_US] depois
 * num MP4 curto. Custo: o codificador de hardware ligado enquanto o Nova Live estiver ligado na Foto.
 */
class LiveRecorder(private val dir: File) : ImageAnalysis.Analyzer {
    private class Sample(val data: ByteArray, val ptsUs: Long, val key: Boolean)
    private class Request(val atUs: Long, val clip: LiveClips.Clip, val createdUs: Long)

    private val samples = ArrayDeque<Sample>()
    private val requests = mutableListOf<Request>()
    private var codec: MediaCodec? = null
    private var format: MediaFormat? = null
    private var width = 0; private var height = 0
    private val info = MediaCodec.BufferInfo()
    @Volatile private var rotation = 0
    @Volatile private var lastFrameUs = 0L
    @Volatile private var failed = false
    @Volatile private var closed = false

    init {
        // Clipes já anexados (ou abandonados) não servem mais: ~2 MB cada no cache.
        val old = System.currentTimeMillis() - 3_600_000L
        dir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
    }

    /** Chamado no disparo (qualquer thread). O clipe fica pronto ~[AFTER_US] depois. */
    fun capture(): LiveClips.Clip {
        val clip = LiveClips.Clip()
        val at = lastFrameUs
        if (failed || closed || at == 0L) clip.complete(null, 0)
        else synchronized(requests) { requests += Request(at, clip, at) }
        return clip
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (failed || closed) return
            val c = codec ?: start(image.width and 1.inv(), image.height and 1.inv())
            rotation = image.imageInfo.rotationDegrees
            val ptsUs = image.imageInfo.timestamp / 1000
            val index = c.dequeueInputBuffer(0)
            if (index >= 0) {
                val input = c.getInputImage(index)
                val ok = input != null && copy(image, input)
                c.queueInputBuffer(index, 0, if (ok) width * height * 3 / 2 else 0, ptsUs, 0)
                lastFrameUs = ptsUs
            }
            drain(c)
            serve(ptsUs)
        } catch (e: Throwable) {
            Log.w(TAG, "Nova Live desligado nesta sessão", e)
            failed = true
            release()
        } finally { image.close() }
    }

    private fun start(w: Int, h: Int): MediaCodec {
        width = w; height = h
        val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); start()
        }.also { codec = it }
    }

    private fun copy(src: ImageProxy, dst: android.media.Image): Boolean {
        if (src.planes.size < 3 || dst.planes.size < 3) return false
        for (p in 0..2) {
            val w = if (p == 0) width else width / 2
            val h = if (p == 0) height else height / 2
            val s = src.planes[p]; val d = dst.planes[p]
            val sb = s.buffer; val db = d.buffer
            if (s.pixelStride == d.pixelStride) {
                val rowLen = (w - 1) * s.pixelStride + 1
                for (y in 0 until h) {
                    val so = y * s.rowStride; val dOff = y * d.rowStride
                    if (so + rowLen > sb.limit() || dOff + rowLen > db.limit()) return false
                    val slice = sb.duplicate().apply { position(so); limit(so + rowLen) }
                    db.duplicate().apply { position(dOff) }.put(slice)
                }
            } else {
                for (y in 0 until h) for (x in 0 until w)
                    db.put(y * d.rowStride + x * d.pixelStride, sb.get(y * s.rowStride + x * s.pixelStride))
            }
        }
        return true
    }

    private fun drain(c: MediaCodec) {
        while (true) {
            val i = c.dequeueOutputBuffer(info, 0)
            when {
                i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> format = c.outputFormat
                i < 0 -> return
                else -> {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val out = c.getOutputBuffer(i)!!
                        val bytes = ByteArray(info.size)
                        out.position(info.offset); out.get(bytes)
                        samples.addLast(Sample(bytes, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0))
                    }
                    c.releaseOutputBuffer(i, false)
                }
            }
        }
    }

    private fun serve(nowUs: Long) {
        val newest = samples.lastOrNull()?.ptsUs ?: return
        val ready = synchronized(requests) {
            requests.filter { newest >= it.atUs + AFTER_US || nowUs - it.createdUs > TIMEOUT_US }
                .also { requests.removeAll(it) }
        }
        ready.forEach { r -> r.clip.complete(runCatching { write(r) }.onFailure { Log.w(TAG, "clipe falhou", it) }.getOrNull(), presentation(r)) }
        // Guarda só o necessário, sempre começando num quadro-chave.
        val keepFrom = newest - KEEP_US
        while (samples.size > 1 && samples.first().ptsUs < keepFrom) {
            val nextKey = samples.drop(1).firstOrNull { it.key } ?: break
            if (nextKey.ptsUs > keepFrom) break
            while (samples.first() !== nextKey) samples.removeFirst()
        }
    }

    private fun startSample(r: Request) =
        samples.lastOrNull { it.key && it.ptsUs <= r.atUs - BEFORE_US } ?: samples.firstOrNull { it.key }

    private fun presentation(r: Request) = (r.atUs - (startSample(r)?.ptsUs ?: r.atUs)).coerceAtLeast(0)

    private fun write(r: Request): File? {
        val fmt = format ?: return null
        val first = startSample(r) ?: return null
        val clip = samples.filter { it.ptsUs >= first.ptsUs && it.ptsUs <= r.atUs + AFTER_US }
        if (clip.size < 10) return null
        dir.mkdirs()
        val file = File(dir, "live_${r.atUs}.mp4")
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            muxer.setOrientationHint(rotation)
            val track = muxer.addTrack(fmt)
            muxer.start()
            val bi = MediaCodec.BufferInfo()
            clip.forEach { s ->
                bi.set(0, s.data.size, s.ptsUs - first.ptsUs, if (s.key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(track, ByteBuffer.wrap(s.data), bi)
            }
            muxer.stop()
        } finally { runCatching { muxer.release() } }
        return file
    }

    /** Na thread da análise, depois de [ImageAnalysis.clearAnalyzer]. Pedidos pendentes saem sem vídeo. */
    fun close() {
        closed = true
        synchronized(requests) { requests.toList().also { requests.clear() } }.forEach { it.clip.complete(null, 0) }
        release()
    }

    private fun release() {
        runCatching { codec?.stop() }; runCatching { codec?.release() }; codec = null
        samples.clear()
    }

    companion object {
        private const val TAG = "NovaLive"
        const val BEFORE_US = 1_500_000L
        const val AFTER_US = 1_500_000L
        private const val KEEP_US = 3_200_000L
        private const val TIMEOUT_US = 4_000_000L
        private const val BITRATE = 6_000_000
    }
}
