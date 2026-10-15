package com.novacamera.s24

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraExtensionCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Size
import com.novacamera.s24.camera.JpegSizeLists
import com.novacamera.s24.camera.LensCatalog
import com.novacamera.s24.core.PhotoSizes
import java.util.Locale

/**
 * Relatório das capacidades anunciadas via Camera2. Não presume acesso a sensores privados:
 * o que não aparece aqui não está disponível para apps de terceiros neste firmware.
 */
object CameraDiagnostics {
    fun createReport(context: Context, extra: String = ""): String {
        val manager = context.getSystemService(CameraManager::class.java)
        return buildString {
            appendLine("Nova Camera ${BuildConfigInfo.version(context)} — Diagnóstico")
            appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine(com.novacamera.s24.camera.NovaCamera.sensorReport)
            appendLine(com.novacamera.s24.core.imaging.AdaptiveImaging.lastReport)
            appendLine(com.novacamera.s24.processing.NcnnDenoiser.report)
            appendLine(com.novacamera.s24.processing.DepthEstimator.report)
            appendLine(com.novacamera.s24.core.ShutterLog.report())
            appendLine(com.novacamera.s24.core.CostLog.report())
            appendLine(com.novacamera.s24.core.imaging.HdrGain.lastReport)
            appendLine(com.novacamera.s24.processing.UltraHdr.deviceReport)
            if (extra.isNotBlank()) { appendLine(); append(extra.trimEnd()); appendLine() }
            val ids = runCatching { manager.cameraIdList.toList() }.getOrElse {
                appendLine("Falha ao listar câmeras: ${it.message}"); return@buildString
            }
            val mainEq = ids.firstNotNullOfOrNull { id ->
                runCatching { manager.getCameraCharacteristics(id) }.getOrNull()
                    ?.takeIf { it.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                    ?.let(::equivalent)
            }
            ids.forEach { id ->
                try {
                    val c = manager.getCameraCharacteristics(id)
                    appendLine("\n── Câmera ID $id (${facing(c)})")
                    describe(c, mainEq)
                    if (c.physicalCameraIds.isNotEmpty()) {
                        appendLine("Lentes físicas sob este ID:")
                        c.physicalCameraIds.forEach { pid ->
                            runCatching { manager.getCameraCharacteristics(pid) }.getOrNull()?.let { pc ->
                                val eq = equivalent(pc)
                                val rel = if (eq != null && mainEq != null) String.format(Locale.US, " ≈ %.1fx", eq / mainEq) else ""
                                appendLine("  · ID $pid: ${eq?.let { String.format(Locale.US, "%.0f mm eq.", it) } ?: "?"}$rel, " +
                                    "sensor ${pc.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)}")
                                appendLine("    " + resolutionVerdict(pc).replace("\n", "\n    "))
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        val ext = runCatching { manager.getCameraExtensionCharacteristics(id).supportedExtensions }
                            .getOrDefault(emptyList())
                        appendLine("Extensões Camera2 (API 31+): ${ext.joinToString { extName(it) }.ifEmpty { "nenhuma" }}")
                    }
                } catch (e: Exception) {
                    appendLine("ID $id: erro ${e.javaClass.simpleName} - ${e.message}")
                }
            }
            appendLine("\nObservação: capacidades anunciadas não garantem combinação com zoom, vídeo ou todas as lentes.")
        }
    }

    private fun StringBuilder.describe(c: CameraCharacteristics, mainEq: Float?) {
        val eq = equivalent(c)
        val rel = if (eq != null && mainEq != null) String.format(Locale.US, " (≈ %.2fx da principal)", eq / mainEq) else ""
        appendLine("Focal: ${c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString()} mm" +
            (eq?.let { String.format(Locale.US, " · %.0f mm eq.", it) } ?: "") + rel)
        appendLine("Sensor: ${c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)} · físico ${c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)} mm")
        if (Build.VERSION.SDK_INT >= 30) appendLine("Faixa de zoom: ${c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)}")
        appendLine("Zoom digital máx.: ${c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)}")
        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        appendLine("Capacidades: " + listOfNotNull(
            "MANUAL_SENSOR".takeIf { CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps },
            "RAW".takeIf { CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in caps },
            "LÓGICA_MULTICÂMERA".takeIf { CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in caps },
            "BURST".takeIf { CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE in caps },
        ).joinToString().ifEmpty { "básicas" })
        appendLine("Nível de hardware: ${hwLevel(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))}")
        appendLine("ISO: ${c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)} · exposição (ns): ${c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)}")
        appendLine("EV: ${c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)} passo ${c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)}")
        appendLine("Faixas de fps do AE: ${c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.joinToString()}")
        val minFocus = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        appendLine("Foco mínimo: " + if (minFocus > 0f) String.format(Locale.US, "%.1f dioptrias (≈ %.0f cm)", minFocus, 100f / minFocus) else "foco fixo")
        appendLine("Bloqueio AE: ${c.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)} · AWB: ${c.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE)}")
        appendLine("Cena prioridade a rostos: ${c.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)?.contains(CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY) == true}" +
            " · detecção de rostos: ${c.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)?.joinToString()}")
        appendLine("Estabilização óptica: ${c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.contains(1) == true}" +
            " · eletrônica de vídeo: ${c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.joinToString()}")
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        listOf("JPEG" to ImageFormat.JPEG, "YUV" to ImageFormat.YUV_420_888, "RAW_SENSOR" to ImageFormat.RAW_SENSOR).forEach { (name, f) ->
            if (name == "RAW_SENSOR" && CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW !in caps) return@forEach
            val size = runCatching { map?.getOutputSizes(f)?.maxByOrNull { it.width.toLong() * it.height } }.getOrNull()
            appendLine("$name máx.: ${size?.pixels() ?: "indisponível"}")
        }
        val highRes = runCatching { map?.getHighResolutionOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height } }.getOrNull()
        appendLine("JPEG alta resolução (captura lenta): ${highRes?.pixels() ?: "não anunciado"}")
        appendLine(resolutionVerdict(c))
        if (Build.VERSION.SDK_INT >= 31) {
            val ultra = 16 in caps // REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR
            val maxMap = runCatching { c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION) }.getOrNull()
            val maxJpeg = runCatching { maxMap?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height } }.getOrNull()
            val maxRaw = runCatching { maxMap?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width.toLong() * it.height } }.getOrNull()
            appendLine("Sensor de ultra-alta resolução: $ultra · matriz máxima ${c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION) ?: "—"}")
            appendLine("  Saídas no modo de resolução máxima: JPEG ${maxJpeg?.pixels() ?: "—"} · RAW ${maxRaw?.pixels() ?: "—"}")
        }
        val hsSizes = runCatching { map?.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
        if (hsSizes.isNotEmpty()) {
            val fpsList = hsSizes.flatMap { s -> runCatching { map!!.getHighSpeedVideoFpsRangesFor(s).map { it.upper } }.getOrDefault(emptyList()) }.distinct().sorted()
            appendLine("Vídeo em alta velocidade: ${hsSizes.joinToString { "${it.width}×${it.height}" }} · até ${fpsList.lastOrNull()} fps")
        } else appendLine("Vídeo em alta velocidade: não anunciado")
    }

    /** Resoluções de foto que o app consegue oferecer nesta câmera e as que o fabricante não libera. */
    fun resolutionVerdict(c: CameraCharacteristics): String {
        @Suppress("UNCHECKED_CAST")
        val lists = JpegSizeLists.from { k -> c.get(k as CameraCharacteristics.Key<Any?>) }
        val opts = PhotoSizes.nativeOptions(lists.regular,lists.highRes,lists.maxResOnly,4f/3f,Build.VERSION.SDK_INT>=31 && lists.ultraHighResolutionSensor)
        val blocked = PhotoSizes.blocked(opts, lists.maxResOnly, 4f / 3f)
        val pixelArray = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val maxArray = if (Build.VERSION.SDK_INT >= 31) c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION) else null
        val sensorMp = ((maxArray ?: pixelArray)?.let { it.width.toLong() * it.height / 1e6 } ?: 0.0)
        return buildString {
            append("Resoluções de foto 4:3 oferecidas: ")
            append(opts.joinToString { it.label + if (it.maximumPixelMode) " (Camera2 nativo; sessão a validar)" else if (it.highResolution) " (alta res.)" else "" }.ifEmpty { "nenhuma" })
            append("\nNão disponíveis nesta configuração: ")
            append(blocked.joinToString { it.label }.ifEmpty { "nenhuma anunciada" })
            val best = opts.maxOfOrNull { it.megapixels } ?: 0.0
            if (sensorMp > best * 1.5) append(String.format(Locale.US,
                "\nO sensor tem ≈ %.0f MP, mas só ≈ %.0f MP são anunciados a outros apps pela API pública.", sensorMp, best))
        }
    }

    private fun equivalent(c: CameraCharacteristics) = LensCatalog.equivalentFocal(
        c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull(),
        c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE),
    )

    private fun facing(c: CameraCharacteristics) = when (c.get(CameraCharacteristics.LENS_FACING)) {
        CameraCharacteristics.LENS_FACING_BACK -> "traseira"
        CameraCharacteristics.LENS_FACING_FRONT -> "frontal"
        else -> "externa"
    }

    private fun hwLevel(level: Int?) = when (level) {
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "?"
    }

    private fun extName(e: Int) = when (e) {
        CameraExtensionCharacteristics.EXTENSION_AUTOMATIC -> "AUTOMATIC"
        1 -> "FACE_RETOUCH"
        CameraExtensionCharacteristics.EXTENSION_BOKEH -> "BOKEH"
        CameraExtensionCharacteristics.EXTENSION_HDR -> "HDR"
        CameraExtensionCharacteristics.EXTENSION_NIGHT -> "NIGHT"
        else -> "#$e"
    }

    private fun Size.pixels(): String =
        "$width×$height (${String.format(Locale.US, "%.1f", width.toDouble() * height / 1e6)} MP)"
}

object BuildConfigInfo {
    fun version(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("?")
}

