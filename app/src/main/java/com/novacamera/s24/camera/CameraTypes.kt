package com.novacamera.s24.camera

import android.net.Uri
import com.novacamera.s24.core.CaptureMode
import com.novacamera.s24.core.ModeSupport

/** Valores manuais do modo Pro. null = automático. */
data class ManualControls(
    val iso: Int? = null,
    val exposureNs: Long? = null,
    /** Dioptrias; 0 = infinito. */
    val focusDiopters: Float? = null,
    /** CaptureRequest.CONTROL_AWB_MODE_*. */
    val awbMode: Int? = null,
) {
    val manualExposure get() = iso != null || exposureNs != null
}

/** Últimos valores escolhidos pelo automático (lidos dos resultados de captura). */
data class AutoStats(val iso: Int, val exposureNs: Long, val focusDiopters: Float)

enum class RecordingPhase { IDLE, RECORDING, PAUSED }

/** Tudo o que a interface precisa saber após cada configuração da câmera. */
data class CameraState(
    val mode: CaptureMode,
    val support: ModeSupport,
    val isFront: Boolean,
    val caps: CameraCapabilities,
    /** Paradas da barra de zoom em ampliação equivalente (1 = principal). */
    val zoomStops: List<Float>,
    /** Limites da pinça na câmera atual, em ampliação equivalente. */
    val zoomMin: Float,
    val zoomMax: Float,
    val evMin: Int,
    val evMax: Int,
    val evStepNum: Int,
    val evStepDen: Int,
    val evIndex: Int,
    val hasFlash: Boolean,
    val oemHdrAvailable: Boolean,
    val oemHdrActive: Boolean,
    val extensionActive: Boolean,
    val canLockExposure: Boolean,
    val videoQualityLabel: String?,
    val videoFps: Int,
    val stabilization: Boolean,
    val screenFlash: Boolean,
)

interface CameraListener {
    fun onCameraState(state: CameraState)
    fun onZoom(equivalentZoom: Float)
    fun onFocusResult(success: Boolean, locked: Boolean)
    fun onExposureIndex(index: Int)
    fun onAutoStats(stats: AutoStats)
    fun onCaptureStarted()
    fun onPhotoSaved(uri: Uri?)
    fun onRecording(phase: RecordingPhase, seconds: Long)
    fun onRecordingFinished(uri: Uri?, error: String?)
    fun onMessage(text: String)
}
