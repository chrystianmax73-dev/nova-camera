package com.novacamera.s24

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Size
import java.util.Locale

/** Lista capacidades anunciadas pela API Camera2; não presume acesso a sensores privados. */
object CameraDiagnostics {
    fun createReport(context: Context): String {
        val manager = context.getSystemService(CameraManager::class.java)
        return buildString {
            appendLine("Nova Camera — Diagnóstico Camera2")
            appendLine("Fabricante/modelo: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Formatos anunciados não garantem uso com zoom, preview ou vídeo.")
            try {
                manager.cameraIdList.forEach { id ->
                    try {
                        val c = manager.getCameraCharacteristics(id)
                        appendLine("\nCâmera ID $id")
                        val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                            CameraCharacteristics.LENS_FACING_BACK -> "Traseira"
                            CameraCharacteristics.LENS_FACING_FRONT -> "Frontal"
                            else -> "Outra"
                        }
                        appendLine("Posição: $facing")
                        appendLine("IDs físicos: ${c.physicalCameraIds.joinToString().ifEmpty { "não expostos" }}")
                        appendLine("Distância focal (mm): ${c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString()}")
                        appendLine("Sensor pixels: ${c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)}")
                        appendLine("Zoom máximo: ${c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)}")
                        if (Build.VERSION.SDK_INT >= 30)
                            appendLine("Faixa zoom: ${c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)}")
                        val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                        val raw = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
                        appendLine("RAW capability: $raw")
                        val config = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        listOf("JPEG" to ImageFormat.JPEG, "YUV" to ImageFormat.YUV_420_888,
                               "RAW_SENSOR" to ImageFormat.RAW_SENSOR).forEach { (name, format) ->
                            if (name != "RAW_SENSOR" || raw) {
                                val size = config?.getOutputSizes(format)?.maxByOrNull {
                                    it.width.toLong() * it.height
                                }
                                appendLine("$name max: ${size?.pixels() ?: "indisponível"}")
                            }
                        }
                    } catch (e: Exception) {
                        appendLine("ID $id: erro ${e.javaClass.simpleName} - ${e.message}")
                    }
                }
            } catch (e: Exception) {
                appendLine("Falha lista câmera: ${e.message}")
            }
            appendLine("Este relatório não comprova suporte a 50/200MP ou RAW na teleobjetiva 5x.")
        }
    }

    private fun Size.pixels(): String =
        "$width×$height (${String.format(Locale.US, "%.1f", width.toDouble() * height / 1e6)}MP)"
}
