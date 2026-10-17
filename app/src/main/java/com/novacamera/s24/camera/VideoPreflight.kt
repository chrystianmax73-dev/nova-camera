package com.novacamera.s24.camera

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Size

/** Capacidade do codificador. Sensor/FPS são consultados com a sessão CameraX completa. */
internal object VideoPreflight {
    fun codecSupports(size: Size, fps: Int): Boolean {
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { mime ->
                (mime == "video/avc" || mime == "video/hevc") && runCatching {
                    val caps = info.getCapabilitiesForType(mime)
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface in caps.colorFormats &&
                        caps.videoCapabilities.areSizeAndRateSupported(size.width, size.height, fps.toDouble())
                }.getOrDefault(false)
            }
        }
    }
}

