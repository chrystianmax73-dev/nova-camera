package com.novacamera.s24.camera

import android.hardware.camera2.CameraCharacteristics
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.util.Size

/** Limites anunciados de sensor + codificador. O bind e o MP4 ainda precisam ser conferidos. */
internal object VideoPreflight {
    fun supports(c: CameraCharacteristics, size: Size, fps: Int): Boolean {
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return false
        val sensor = runCatching {
            val duration = map.getOutputMinFrameDuration(MediaRecorder::class.java, size)
            duration == 0L || duration <= 1_000_000_000L / fps + 100_000L
        }.getOrDefault(false)
        if (!sensor) return false
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
