package com.novacamera.s24.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Range
import android.util.SizeF
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import com.novacamera.s24.core.LensOption
import kotlin.math.sqrt

/** O que a câmera ativa realmente anuncia via Camera2. Nenhum controle é exibido sem estar aqui. */
data class CameraCapabilities(
    val cameraId: String,
    val isFront: Boolean,
    val manualSensor: Boolean,
    val isoRange: Range<Int>?,
    val exposureRangeNs: Range<Long>?,
    /** Dioptrias; 0 = foco fixo. */
    val minFocusDistance: Float,
    val awbModes: List<Int>,
    val facePriorityScene: Boolean,
    val aeLockAvailable: Boolean,
    val awbLockAvailable: Boolean,
    val aeFpsRanges: List<Range<Int>>,
    val physicalCameraIds: Set<String>,
    val opticalStabilization: Boolean = false,
) {
    val manualFocus get() = minFocusDistance > 0f && manualSensor

    companion object {
        @OptIn(ExperimentalCamera2Interop::class)
        fun from(info: CameraInfo): CameraCapabilities {
            val c = Camera2CameraInfo.from(info)
            fun <T> get(key: CameraCharacteristics.Key<T>): T? = runCatching { c.getCameraCharacteristic(key) }.getOrNull()
            val caps = get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
            return CameraCapabilities(
                cameraId = c.cameraId,
                isFront = info.lensFacing == CameraSelector.LENS_FACING_FRONT,
                manualSensor = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps,
                isoRange = get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
                exposureRangeNs = get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
                minFocusDistance = get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
                awbModes = get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList().orEmpty(),
                facePriorityScene = get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
                    ?.contains(CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY) == true,
                aeLockAvailable = get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true,
                awbLockAvailable = get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true,
                aeFpsRanges = get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList().orEmpty(),
                opticalStabilization = get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                    ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true,
                // Preenchido só em câmeras lógicas (várias lentes físicas sob um ID).
                physicalCameraIds = runCatching {
                    info.physicalCameraInfos.map { Camera2CameraInfo.from(it).cameraId }.toSet()
                }.getOrDefault(emptySet()),
            )
        }
    }
}

/** Câmeras que o Android expõe ao app, com campo de visão relativo calculado pela óptica anunciada. */
object LensCatalog {
    /** Distância focal equivalente em 35 mm (diagonal), a partir da focal e do tamanho físico do sensor. */
    fun equivalentFocal(focalMm: Float?, sensor: SizeF?): Float? {
        if (focalMm == null || sensor == null || sensor.width <= 0f) return null
        val diag = sqrt(sensor.width * sensor.width + sensor.height * sensor.height)
        return focalMm * 43.27f / diag
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun equivalentFocal(info: CameraInfo): Float? {
        val c = Camera2CameraInfo.from(info)
        val focal = runCatching { c.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) }
            .getOrNull()?.minOrNull()
        val sensor = runCatching { c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) }.getOrNull()
        return equivalentFocal(focal, sensor)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun zoomRange(info: CameraInfo): Pair<Float, Float> {
        val c = Camera2CameraInfo.from(info)
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { c.getCameraCharacteristic(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) }.getOrNull()
                ?.let { return it.lower to it.upper }
        }
        val max = runCatching { c.getCameraCharacteristic(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) }
            .getOrNull() ?: 1f
        return 1f to max
    }

    /**
     * Nome de cada lente física de uma câmera lógica, pela focal equivalente relativa à lógica
     * (ex.: "Ultra-angular", "Principal", "Tele 3×", "Tele 5×").
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun physicalLabels(info: CameraInfo): Map<String, String> {
        val reference = equivalentFocal(info) ?: return emptyMap()
        return runCatching { info.physicalCameraInfos }.getOrDefault(emptySet()).mapNotNull { p ->
            val id = Camera2CameraInfo.from(p).cameraId
            val eq = equivalentFocal(p) ?: return@mapNotNull null
            id to lensName(eq / reference)
        }.toMap()
    }

    fun lensName(relative: Float): String = when {
        relative < 0.8f -> "Ultra-angular"
        relative < 1.6f -> "Principal"
        else -> "Tele ${Math.round(relative)}×"
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun lenses(infos: List<CameraInfo>, defaultInfo: CameraInfo?): List<LensOption> {
        val reference = defaultInfo?.let(::equivalentFocal)
        return infos.map { info ->
            val eq = equivalentFocal(info)
            val intrinsic = if (eq != null && reference != null && reference > 0f) eq / reference else 1f
            val (lo, hi) = zoomRange(info)
            LensOption(Camera2CameraInfo.from(info).cameraId, intrinsic, lo, hi)
        }
    }
}

/** Listas públicas de tamanhos JPEG de uma câmera (ver [com.novacamera.s24.core.PhotoSizes]). */
class JpegSizeLists(
    val regular: List<Pair<Int, Int>>,
    val highRes: List<Pair<Int, Int>>,
    /** Só no mapa de resolução máxima do sensor (API 31+): exigem sessão Camera2 especial. */
    val maxResOnly: List<Pair<Int, Int>>,
    val ultraHighResolutionSensor: Boolean,
) {
    companion object {
        fun from(get: (android.hardware.camera2.CameraCharacteristics.Key<*>) -> Any?): JpegSizeLists {
            @Suppress("UNCHECKED_CAST")
            fun <T> key(k: android.hardware.camera2.CameraCharacteristics.Key<T>): T? = runCatching { get(k) as T? }.getOrNull()
            val map = key(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpeg = android.graphics.ImageFormat.JPEG
            fun conv(a: Array<android.util.Size>?) = a?.map { it.width to it.height }.orEmpty()
            val regular = runCatching { conv(map?.getOutputSizes(jpeg)) }.getOrDefault(emptyList())
            val high = runCatching { conv(map?.getHighResolutionOutputSizes(jpeg)) }.getOrDefault(emptyList())
            var maxOnly = emptyList<Pair<Int, Int>>()
            var ultra = false
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                ultra = key(android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(16) == true
                val maxMap = key(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                val known = (regular + high).toSet()
                maxOnly = runCatching { conv(maxMap?.getOutputSizes(jpeg)) + conv(maxMap?.getOutputSizes(android.graphics.ImageFormat.RAW_SENSOR)) }
                    .getOrDefault(emptyList()).filter { it !in known }
            }
            return JpegSizeLists(regular, high, maxOnly, ultra)
        }
    }
}
