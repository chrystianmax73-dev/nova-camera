package com.novacamera.s24.camera

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.novacamera.s24.core.VideoContract
import java.util.Locale

/** Lê o arquivo já finalizado; nenhum quadro é decodificado e nenhuma mídia sai do aparelho. */
internal object VideoMetadata {
    data class Result(val size: VideoContract.Size, val rotation: Int, val codec: String, val fps: Double?) {
        val summary: String get() {
            val displayed = if (rotation % 180 != 0) VideoContract.Size(size.height, size.width) else size
            val rate = fps?.let { String.format(Locale.US, "%.2f fps médios", it) } ?: "fps não medido"
            return "MP4: $displayed · $codec · $rate"
        }
    }

    fun read(context: Context, uri: Uri): Result {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: error("MP4 sem faixa de vídeo")
            val format = extractor.getTrackFormat(track)
            val size = VideoContract.Size(format.getInteger(MediaFormat.KEY_WIDTH), format.getInteger(MediaFormat.KEY_HEIGHT))
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
            val codec = format.getString(MediaFormat.KEY_MIME) ?: "codec desconhecido"
            extractor.selectTrack(track)
            val cadence = VideoContract.Cadence()
            while (extractor.sampleTime >= 0) {
                cadence.sample(extractor.sampleTime)
                if (!extractor.advance()) break
            }
            return Result(size, rotation, codec, cadence.fps)
        } finally { extractor.release() }
    }
}
