package com.novacamera.s24.capture

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Surface
import kotlin.math.max

/**
 * Codifica quadros espaçados em um MP4 H.264 comum (sem áudio). Os quadros são desenhados na
 * superfície de entrada do codificador; o tempo de apresentação é reescrito para [fps]
 * constantes, de modo que 1 quadro por intervalo vira vídeo acelerado.
 * Não é thread-safe: todas as chamadas devem vir da mesma thread.
 */
class TimelapseEncoder(
    private val resolver: ContentResolver,
    val width: Int,
    val height: Int,
    displayName: String,
    private val fps: Int = 30,
) {
    private val uri: Uri
    private val pfd: ParcelFileDescriptor
    private val codec: MediaCodec
    private val surface: Surface
    private val muxer: MediaMuxer
    private var track = -1
    private var muxerStarted = false
    private var written = 0L
    private val info = MediaCodec.BufferInfo()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    var frames = 0; private set

    init {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Nova Camera")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("galeria indisponível")
        try {
            pfd = resolver.openFileDescriptor(uri, "rw") ?: error("sem arquivo")
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                // Timelapse muda pouco entre quadros, mas cada quadro é uma foto: taxa alta mantém detalhe.
                setInteger(MediaFormat.KEY_BIT_RATE, max(8_000_000, width * height * 6))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                if (Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface()
            codec.start()
            muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /**
     * Desenha [bmp] (região [crop], girada por [rotationDegrees]) preenchendo o quadro do vídeo
     * sem distorcer (recorte central do excedente).
     */
    fun addFrame(bmp: Bitmap, crop: Rect, rotationDegrees: Int) {
        val canvas = surface.lockHardwareCanvas()
        try {
            val rot = ((rotationDegrees % 360) + 360) % 360
            val cw = if (rot % 180 == 0) crop.width() else crop.height()
            val ch = if (rot % 180 == 0) crop.height() else crop.width()
            val scale = max(width.toFloat() / cw, height.toFloat() / ch)
            val m = Matrix().apply {
                postTranslate(-crop.exactCenterX(), -crop.exactCenterY())
                postRotate(rot.toFloat())
                postScale(scale, scale)
                postTranslate(width / 2f, height / 2f)
            }
            canvas.drawColor(0xFF000000.toInt())
            canvas.drawBitmap(bmp, m, paint)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
        frames++
        drain(endOfStream = false)
    }

    private fun drain(endOfStream: Boolean) {
        if (endOfStream) codec.signalEndOfInputStream()
        var waits = 0
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            when {
                // No fim, espera o codificador esvaziar (até ~3 s, para nunca travar a finalização).
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || ++waits > 300) return
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start(); muxerStarted = true
                }
                idx >= 0 -> {
                    val buf = codec.getOutputBuffer(idx)
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (buf != null && info.size > 0 && !config && muxerStarted) {
                        // Tempo constante: o enésimo quadro codificado aparece em n/fps segundos.
                        info.presentationTimeUs = written * 1_000_000L / fps
                        written++
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** Finaliza o MP4 e o publica na galeria. Devolve null se nenhum quadro foi gravado. */
    fun finish(): Uri? {
        var ok = false
        try {
            drain(endOfStream = true)
            ok = written > 0
        } finally {
            runCatching { codec.stop() }; runCatching { codec.release() }; runCatching { surface.release() }
            runCatching { if (muxerStarted) muxer.stop() }; runCatching { muxer.release() }
            runCatching { pfd.close() }
        }
        return if (ok) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } else {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** Descarta tudo (falha grave). */
    fun abort() {
        runCatching { codec.stop() }; runCatching { codec.release() }; runCatching { surface.release() }
        runCatching { muxer.release() }; runCatching { pfd.close() }
        runCatching { resolver.delete(uri, null, null) }
    }
}
