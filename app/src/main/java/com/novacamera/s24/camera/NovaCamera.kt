package com.novacamera.s24.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.Choreographer
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.novacamera.s24.core.CameraMath
import com.novacamera.s24.core.CaptureMode
import com.novacamera.s24.core.DeviceHeat
import com.novacamera.s24.core.DeviceModeFacts
import com.novacamera.s24.core.LensOption
import com.novacamera.s24.core.LensSelection
import com.novacamera.s24.core.ModeEngine
import com.novacamera.s24.core.ModeResolver
import com.novacamera.s24.core.ModeSupport
import com.novacamera.s24.core.ThermalPolicy
import com.novacamera.s24.settings.AspectRatioOption
import com.novacamera.s24.settings.FlashOption
import com.novacamera.s24.settings.NovaSettings
import com.novacamera.s24.settings.VideoQualityOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sessão de câmera da Nova Camera: CameraX para pré-visualização/captura e Camera2 (interop)
 * para o que o CameraX não expõe (prioridade a rostos, bloqueio AE/AWB, controles manuais).
 * Toda decisão de recurso parte das capacidades anunciadas pelo aparelho.
 */
@OptIn(ExperimentalCamera2Interop::class)
class NovaCamera(
    private val activity: ComponentActivity,
    private val previewView: PreviewView,
    private val settings: NovaSettings,
    private val listener: CameraListener,
) {
    private val main = ContextCompat.getMainExecutor(activity)
    private val handler = Handler(Looper.getMainLooper())

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var paused = false

    var mode: CaptureMode = settings.lastMode; private set
    var isFront = false; private set
    var state: CameraState? = null; private set
    var recordingPhase = RecordingPhase.IDLE; private set

    private var lenses: List<LensOption> = emptyList()
    private var defaultLensId: String? = null
    private var currentLensId: String? = null
    private var currentLens: LensOption? = null
    private var caps: CameraCapabilities? = null
    private var activeExtension = ExtensionMode.NONE
    private var heat: DeviceHeat? = null
    private var targetRotation = Surface.ROTATION_0
    private var oemFacts = mapOf<Boolean, Triple<Boolean, Boolean, Boolean>>() // isFront → (bokeh, night, hdr)

    // Foco / exposição
    private var userPointActive = false
    private var aeLocked = false
    private var focusToken = 0
    private var evIndex = 0
    var manual = ManualControls(); private set
    @Volatile private var latestStats: AutoStats? = null
    private var lastStatsPost = 0L

    // Zoom suave
    private var zoomOnLens = 1f
    private var zoomTarget = 1f
    private var pendingZoomOnLens: Float? = null
    private var zoomAnimating = false

    val ready get() = camera != null && !paused
    val isRecording get() = recording != null

    // ------------------------------------------------------------------ ciclo de vida

    fun start() {
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            val p = runCatching { future.get() }.getOrElse {
                Log.e(TAG, "CameraX indisponível", it)
                listener.onMessage("Não foi possível abrir a câmera")
                return@addListener
            }
            provider = p
            val ext = ExtensionsManager.getInstanceAsync(activity, p)
            ext.addListener({
                extensions = runCatching { ext.get() }.onFailure { Log.w(TAG, "Extensões OEM indisponíveis", it) }.getOrNull()
                oemFacts = listOf(false, true).associateWith { front ->
                    val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    fun has(m: Int) = runCatching { extensions?.isExtensionAvailable(sel, m) == true }.getOrDefault(false)
                    Triple(has(ExtensionMode.BOKEH), has(ExtensionMode.NIGHT), has(ExtensionMode.HDR))
                }
                if (!runCatching { p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(true)) isFront = true
                refreshLenses()
                bind()
            }, main)
        }, main)
    }

    /** Libera a câmera (economia de energia por inatividade). */
    fun pause() {
        if (recording != null || paused) return
        paused = true
        provider?.unbindAll()
        camera = null
    }

    fun resume() {
        if (!paused) return
        paused = false
        bind()
    }

    fun release() {
        stopRecording()
        handler.removeCallbacksAndMessages(null)
        provider?.unbindAll()
    }

    // ------------------------------------------------------------------ seleção

    /**
     * Troca o modo. [rebind] = false quando a interface ainda vai mudar a proporção do visor:
     * o ViewPort precisa ser lido depois do novo layout para a foto corresponder ao enquadramento.
     */
    fun setMode(m: CaptureMode, rebind: Boolean = true) {
        if (recording != null || m == mode) return
        mode = m
        settings.lastMode = m
        resetFocusState()
        evIndex = 0
        if (m != CaptureMode.PRO) manual = ManualControls()
        if (rebind) bind()
    }

    fun switchFacing(rebind: Boolean = true) {
        if (recording != null) return
        isFront = !isFront
        resetFocusState()
        evIndex = 0
        refreshLenses()
        if (rebind) bind()
    }

    /** Antes do primeiro bind: lado inicial e modo salvo. */
    val hasProvider get() = provider != null

    fun setRotation(surfaceRotation: Int) {
        targetRotation = surfaceRotation
        imageCapture?.targetRotation = surfaceRotation
        // Durante a gravação a orientação do MP4 já está fixada.
        if (recording == null) videoCapture?.targetRotation = surfaceRotation
    }

    fun onHeat(h: DeviceHeat) {
        val before = heat?.let(ThermalPolicy::limitVideo)
        heat = h
        if (ThermalPolicy.stopRecording(h) && recording != null) {
            listener.onMessage("Gravação finalizada para proteger o aparelho do aquecimento")
            stopRecording()
        }
        // Reconfigura o vídeo só quando o limite muda e não há gravação em andamento.
        if (mode.isVideo && before != null && before != ThermalPolicy.limitVideo(h) && recording == null) bind()
    }

    fun supportFor(m: CaptureMode, front: Boolean = isFront): ModeSupport {
        val (bokeh, night, _) = oemFacts[front] ?: Triple(false, false, false)
        val info = defaultInfo(front)
        val ranges = info?.supportedFrameRateRanges?.map { it.lower to it.upper }.orEmpty()
        return ModeResolver.resolve(m, DeviceModeFacts(bokeh, night, ModeResolver.extendedExposureMinFps(ranges)))
    }

    fun oemReport(): Map<Boolean, Triple<Boolean, Boolean, Boolean>> = oemFacts

    // ------------------------------------------------------------------ configuração (bind)

    private fun defaultInfo(front: Boolean): CameraInfo? {
        val p = provider ?: return null
        val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        return runCatching { p.getCameraInfo(sel) }.getOrNull()
    }

    private fun refreshLenses() {
        val p = provider ?: return
        val facing = if (isFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        val infos = p.availableCameraInfos.filter { it.lensFacing == facing }
        val def = defaultInfo(isFront)
        lenses = LensCatalog.lenses(infos, def)
        defaultLensId = def?.let { Camera2CameraInfo.from(it).cameraId }
        currentLensId = defaultLensId
        zoomOnLens = 1f
        zoomTarget = 1f
    }

    private fun selectorFor(id: String?): CameraSelector {
        val base = if (isFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (id == null || id == defaultLensId) return base
        return CameraSelector.Builder().addCameraFilter { infos ->
            infos.filter { Camera2CameraInfo.from(it).cameraId == id }
        }.build()
    }

    fun bind() {
        val p = provider ?: return
        if (recording != null || paused) return
        val support = supportFor(mode)
        val (_, _, oemHdr) = oemFacts[isFront] ?: Triple(false, false, false)

        // Extensões do fabricante só existem na câmera padrão de cada lado.
        val wantedExtension = when {
            !support.canCapture -> ExtensionMode.NONE
            mode == CaptureMode.RETRATO && support.engine == ModeEngine.OEM_EXTENSION -> ExtensionMode.BOKEH
            mode == CaptureMode.NOITE && support.engine == ModeEngine.OEM_EXTENSION -> ExtensionMode.NIGHT
            mode == CaptureMode.FOTO && oemHdr && settings.oemHdr -> ExtensionMode.HDR
            else -> ExtensionMode.NONE
        }
        if (wantedExtension != ExtensionMode.NONE) currentLensId = defaultLensId
        val baseSelector = selectorFor(currentLensId)
        val selector = if (wantedExtension != ExtensionMode.NONE)
            runCatching { extensions!!.getExtensionEnabledCameraSelector(baseSelector, wantedExtension) }.getOrNull()
        else baseSelector
        if (selector == null) {
            listener.onMessage("Modo do fabricante não pôde ser ativado")
            return
        }
        val info = runCatching { p.getCameraInfo(baseSelector) }.getOrNull() ?: return
        currentLens = lenses.firstOrNull { it.cameraId == Camera2CameraInfo.from(info).cameraId }

        val wide = mode.isVideo || settings.aspect != AspectRatioOption.RATIO_4_3
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(if (wide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                                    else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        val limitVideo = heat?.let(ThermalPolicy::limitVideo) ?: false
        val previewBuilder = Preview.Builder().setResolutionSelector(resolution)
        var stabilization = false
        var fps = 30
        var qualityLabel: String? = null

        if (wantedExtension == ExtensionMode.NONE) {
            Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(statsCallback)
        }
        if (mode == CaptureMode.NOITE && support.engine == ModeEngine.NOVA_BASIC) {
            val minFps = ModeResolver.extendedExposureMinFps(info.supportedFrameRateRanges.map { it.lower to it.upper })
            val range = info.supportedFrameRateRanges.firstOrNull { it.lower == minFps && it.upper <= 30 }
            if (range != null) previewBuilder.setTargetFrameRate(range)
        }

        val useCases = mutableListOf<UseCase>()
        imageCapture = null
        videoCapture = null

        if (mode.isVideo) {
            val capabilities = Recorder.getVideoCapabilities(info)
            val supported = capabilities.getSupportedQualities(DynamicRange.SDR)
            val pref = settings.videoQuality
            // Sob economia de energia ou aquecimento severo, 4K cede lugar ao Full HD.
            val effective = if (limitVideo && pref == VideoQualityOption.UHD) VideoQualityOption.FHD else pref
            val wanted = when (effective) {
                VideoQualityOption.AUTO -> if (limitVideo) listOf(Quality.FHD, Quality.HD) else listOf(Quality.UHD, Quality.FHD, Quality.HD)
                VideoQualityOption.UHD -> listOf(Quality.UHD, Quality.FHD)
                VideoQualityOption.FHD -> listOf(Quality.FHD, Quality.HD)
                VideoQualityOption.HD -> listOf(Quality.HD)
            }
            val chosen = wanted.firstOrNull { it in supported }
            qualityLabel = when (chosen) { Quality.UHD -> "4K"; Quality.FHD -> "FHD"; Quality.HD -> "HD"; else -> null }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.fromOrderedList(wanted, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            val vb = VideoCapture.Builder(recorder)
                .setTargetRotation(targetRotation)
                .setMirrorMode(if (settings.mirrorFrontVideo) MirrorMode.MIRROR_MODE_ON_FRONT_ONLY else MirrorMode.MIRROR_MODE_OFF)
            if (settings.videoStabilization) {
                when {
                    Preview.getPreviewCapabilities(info).isStabilizationSupported -> {
                        previewBuilder.setPreviewStabilizationEnabled(true); stabilization = true
                    }
                    capabilities.isStabilizationSupported -> { vb.setVideoStabilizationEnabled(true); stabilization = true }
                }
            }
            if (settings.videoFps == 60 && !limitVideo && info.supportedFrameRateRanges.any { it.lower == 60 && it.upper == 60 }) {
                vb.setTargetFrameRate(Range(60, 60)); fps = 60
            }
            videoCapture = vb.build()
        } else if (support.canCapture) {
            val ib = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(resolution)
                .setTargetRotation(targetRotation)
            imageCapture = ib.build()
        }

        val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }
        useCases += preview
        imageCapture?.let { useCases += it }
        videoCapture?.let { useCases += it }

        val group = UseCaseGroup.Builder().apply {
            // O ViewPort faz a foto salva corresponder exatamente ao que a pré-visualização mostra.
            previewView.viewPort?.let(::setViewPort)
            useCases.forEach(::addUseCase)
        }.build()

        try {
            p.unbindAll()
            camera = p.bindToLifecycle(activity, selector, group)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao configurar $mode", e)
            camera = null
            // Recuo em degraus, do ajuste mais específico ao mais básico.
            when {
                fps == 60 -> {
                    settings.videoFps = 30
                    listener.onMessage("60 fps indisponível nesta combinação; usando 30 fps")
                }
                currentLensId != defaultLensId -> {
                    currentLensId = defaultLensId
                    listener.onMessage("Esta lente não pôde ser aberta; usando a principal")
                }
                mode != CaptureMode.FOTO -> {
                    listener.onMessage("Não foi possível ativar ${mode.label}; usando FOTO")
                    mode = CaptureMode.FOTO
                }
                else -> {
                    listener.onMessage("Não foi possível abrir a câmera")
                    return
                }
            }
            handler.post { bind() }
            return
        }
        activeExtension = wantedExtension
        val cam = camera ?: return
        caps = CameraCapabilities.from(cam.cameraInfo)
        applyFlash(cam)

        // Zoom inicial na nova câmera.
        val lens = currentLens
        val zState = cam.cameraInfo.zoomState.value
        val zMin = zState?.minZoomRatio ?: lens?.minZoom ?: 1f
        val zMax = zState?.maxZoomRatio ?: lens?.maxZoom ?: 1f
        zoomOnLens = (pendingZoomOnLens ?: zoomOnLens).coerceIn(zMin, zMax)
        pendingZoomOnLens = null
        zoomTarget = zoomOnLens
        cam.cameraControl.setZoomRatio(zoomOnLens)
        listener.onZoom(equivalent(zoomOnLens))

        val exposure = cam.cameraInfo.exposureState
        evIndex = evIndex.coerceIn(exposure.exposureCompensationRange.lower, exposure.exposureCompensationRange.upper)
        if (exposure.isExposureCompensationSupported) cam.cameraControl.setExposureCompensationIndex(evIndex)
        applyInterop()

        val intrinsic = lens?.intrinsic ?: 1f
        val candidates = if (isFront) listOf(1f) else ZOOM_CANDIDATES
        val stops = LensSelection.reachableStops(lenses, candidates).ifEmpty { listOf(1f) }
        val s = CameraState(
            mode = mode, support = support, isFront = isFront, caps = caps!!,
            zoomStops = stops, zoomMin = intrinsic * zMin, zoomMax = intrinsic * zMax,
            evMin = exposure.exposureCompensationRange.lower, evMax = exposure.exposureCompensationRange.upper,
            evStepNum = exposure.exposureCompensationStep.numerator, evStepDen = exposure.exposureCompensationStep.denominator,
            evIndex = evIndex,
            hasFlash = cam.cameraInfo.hasFlashUnit(),
            oemHdrAvailable = oemHdr && mode == CaptureMode.FOTO,
            oemHdrActive = wantedExtension == ExtensionMode.HDR,
            extensionActive = wantedExtension != ExtensionMode.NONE,
            canLockExposure = wantedExtension == ExtensionMode.NONE && caps!!.aeLockAvailable,
            videoQualityLabel = qualityLabel, videoFps = fps, stabilization = stabilization,
            screenFlash = isFront && !cam.cameraInfo.hasFlashUnit(),
        )
        state = s
        listener.onCameraState(s)
    }

    private fun applyFlash(cam: Camera) {
        val ic = imageCapture ?: return
        val hasUnit = cam.cameraInfo.hasFlashUnit()
        ic.flashMode = when {
            mode == CaptureMode.NOITE || activeExtension != ExtensionMode.NONE -> ImageCapture.FLASH_MODE_OFF
            settings.flash == FlashOption.OFF -> ImageCapture.FLASH_MODE_OFF
            hasUnit -> if (settings.flash == FlashOption.ON) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_AUTO
            isFront -> {
                // Flash de tela para selfies: a tela ilumina o rosto durante a captura.
                ic.screenFlash = previewView.screenFlash
                ImageCapture.FLASH_MODE_SCREEN
            }
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun refreshFlash() { camera?.let(::applyFlash) }

    fun setTorch(on: Boolean) {
        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(on)
    }

    // ------------------------------------------------------------------ zoom

    private fun equivalent(onLens: Float) = (currentLens?.intrinsic ?: 1f) * onLens

    /** Leva o zoom a [equivalentZoom] com animação; troca de câmera se outra cobrir melhor (fora da gravação). */
    fun zoomTo(equivalentZoom: Float, allowLensSwitch: Boolean = true) {
        val lens = currentLens
        if (allowLensSwitch && recording == null && activeExtension == ExtensionMode.NONE) {
            val choice = LensSelection.choose(lenses, currentLensId, defaultLensId, equivalentZoom)
            if (choice != null && choice.cameraId != currentLensId) {
                currentLensId = choice.cameraId
                pendingZoomOnLens = choice.zoomOnLens
                resetFocusState()
                bind()
                return
            }
        }
        val intrinsic = lens?.intrinsic ?: 1f
        setZoomOnLensTarget(equivalentZoom / intrinsic)
    }

    /** Pinça: multiplica o alvo atual, dentro dos limites da câmera em uso. */
    fun zoomBy(scale: Float) {
        setZoomOnLensTarget(zoomTarget * scale)
    }

    private fun setZoomOnLensTarget(onLens: Float) {
        val cam = camera ?: return
        val z = cam.cameraInfo.zoomState.value
        zoomTarget = onLens.coerceIn(z?.minZoomRatio ?: 1f, z?.maxZoomRatio ?: 1f)
        if (!zoomAnimating) {
            zoomAnimating = true
            Choreographer.getInstance().postFrameCallback(zoomFrame)
        }
    }

    private val zoomFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val cam = camera
            if (cam == null) { zoomAnimating = false; return }
            // Vídeo: aproximação mais lenta para transições suaves e estáveis na gravação.
            val factor = if (mode.isVideo) 0.10f else 0.32f
            val next = CameraMath.zoomStep(zoomOnLens, zoomTarget, factor)
            if (next != zoomOnLens) {
                zoomOnLens = next
                cam.cameraControl.setZoomRatio(next)
                listener.onZoom(equivalent(next))
            }
            if (zoomOnLens != zoomTarget) Choreographer.getInstance().postFrameCallback(this)
            else zoomAnimating = false
        }
    }

    // ------------------------------------------------------------------ foco e exposição

    private fun resetFocusState() {
        focusToken++
        userPointActive = false
        aeLocked = false
    }

    /**
     * Toque: foco + medição no ponto. Depois de [NovaSettings.tapFocusHoldSeconds] o AF volta ao contínuo
     * (acompanha aproximação/afastamento), mantendo a medição de exposição no ponto para não "saltar".
     * [lock] = toque prolongado: AF fica travado e AE/AWB são bloqueados via Camera2.
     */
    fun focusAt(x: Float, y: Float, lock: Boolean) {
        val cam = camera ?: return
        val token = ++focusToken
        userPointActive = true
        aeLocked = false
        applyInterop()
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val manualFocus = mode == CaptureMode.PRO && manual.focusDiopters != null
        val flags = if (manualFocus) FocusMeteringAction.FLAG_AE
                    else FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        val action = FocusMeteringAction.Builder(point, flags).disableAutoCancel().build()
        if (!cam.cameraInfo.isFocusMeteringSupported(action)) {
            listener.onFocusResult(false, false)
            return
        }
        val future = cam.cameraControl.startFocusAndMetering(action)
        future.addListener({
            if (token != focusToken) return@addListener
            val ok = runCatching { future.get().isFocusSuccessful }.getOrDefault(false)
            if (lock) {
                aeLocked = true
                applyInterop()
                if (activeExtension != ExtensionMode.NONE) listener.onMessage("Bloqueio de exposição indisponível neste modo do fabricante; foco travado")
            } else scheduleFocusRelease(token, point)
            listener.onFocusResult(ok, lock)
        }, main)
    }

    private fun scheduleFocusRelease(token: Int, point: MeteringPoint) {
        val hold = settings.tapFocusHoldSeconds
        if (hold <= 0) return
        handler.postDelayed({
            val cam = camera ?: return@postDelayed
            if (token != focusToken || aeLocked) return@postDelayed
            // Estágio 1: AF volta ao contínuo; AE segue medido no ponto escolhido.
            cam.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE).disableAutoCancel().build())
            handler.postDelayed({
                if (token != focusToken || aeLocked || evIndex != 0) return@postDelayed
                // Estágio 2: sem ajuste manual de EV, retorna ao automático completo (inclui prioridade a rostos).
                userPointActive = false
                camera?.cameraControl?.cancelFocusAndMetering()
                applyInterop()
            }, AUTO_RETURN_MS)
        }, hold * 1000L)
    }

    /** Desfaz ponto manual, bloqueio e compensação: volta ao automático completo. */
    fun resetToAuto() {
        resetFocusState()
        camera?.cameraControl?.cancelFocusAndMetering()
        setExposureIndex(0)
        applyInterop()
    }

    val exposureLocked get() = aeLocked

    fun setExposureIndex(index: Int) {
        val cam = camera ?: return
        val ex = cam.cameraInfo.exposureState
        if (!ex.isExposureCompensationSupported) return
        val clamped = index.coerceIn(ex.exposureCompensationRange.lower, ex.exposureCompensationRange.upper)
        if (clamped == evIndex) return
        evIndex = clamped
        cam.cameraControl.setExposureCompensationIndex(clamped)
        listener.onExposureIndex(clamped)
    }

    val exposureIndex get() = evIndex

    fun setManual(m: ManualControls) {
        val stats = latestStats
        // Ao fixar só ISO ou só velocidade, o outro parâmetro congela no valor que o automático usava.
        manual = if (m.manualExposure && stats != null) m.copy(
            iso = m.iso ?: m.exposureNs?.let { manual.iso ?: stats.iso },
            exposureNs = m.exposureNs ?: m.iso?.let { manual.exposureNs ?: stats.exposureNs },
        ) else m
        applyInterop()
    }

    fun clearManual() {
        manual = ManualControls()
        applyInterop()
    }

    private fun applyInterop() {
        val cam = camera ?: return
        if (activeExtension != ExtensionMode.NONE) return
        val c = caps ?: return
        val b = CaptureRequestOptions.Builder()
        val m = if (mode == CaptureMode.PRO) manual else ManualControls()
        if (m.manualExposure && c.manualSensor) {
            val iso = (m.iso ?: latestStats?.iso ?: 100).let { v -> c.isoRange?.let { v.coerceIn(it.lower, it.upper) } ?: v }
            val exp = (m.exposureNs ?: latestStats?.exposureNs ?: 16_666_666L)
                .let { v -> c.exposureRangeNs?.let { v.coerceIn(it.lower, it.upper) } ?: v }
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exp)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(exp, 33_333_333L))
        }
        if (m.focusDiopters != null && c.manualFocus) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, m.focusDiopters.coerceIn(0f, c.minFocusDistance))
        }
        if (m.awbMode != null && m.awbMode in c.awbModes) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, m.awbMode)
        }
        val facePriority = settings.facePriority && c.facePriorityScene && !userPointActive &&
            mode != CaptureMode.PRO && !m.manualExposure
        if (facePriority) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
            b.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_FACE_PRIORITY)
        }
        if (aeLocked && c.aeLockAvailable && !m.manualExposure) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
            if (settings.lockWhiteBalanceWithAeAf && c.awbLockAvailable && m.awbMode == null) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
            }
        }
        runCatching { Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build()) }
            .onFailure { Log.w(TAG, "Interop Camera2 recusado", it) }
    }

    private val statsCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
            val exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
            val focus = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f
            val stats = AutoStats(iso, exp, focus)
            latestStats = stats
            val now = System.currentTimeMillis()
            // Só o modo Pro exibe esses valores; limite de 4 atualizações por segundo.
            if (mode == CaptureMode.PRO && now - lastStatsPost > 250) {
                lastStatsPost = now
                handler.post { listener.onAutoStats(stats) }
            }
        }
    }

    // ------------------------------------------------------------------ foto

    fun capturePhoto() {
        val ic = imageCapture ?: return listener.onMessage("A câmera ainda não está pronta")
        ic.targetRotation = targetRotation
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "NOVA_${timestamp()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Nova Camera")
        }
        // Selfie salva como visualizada: inversão gravada como orientação EXIF, sem recodificar o JPEG.
        val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = isFront && settings.mirrorFrontPhoto }
        val output = ImageCapture.OutputFileOptions.Builder(
            activity.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).setMetadata(metadata).build()
        ic.takePicture(output, main, object : ImageCapture.OnImageSavedCallback {
            override fun onCaptureStarted() = listener.onCaptureStarted()
            override fun onImageSaved(result: ImageCapture.OutputFileResults) = listener.onPhotoSaved(result.savedUri)
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Falha na foto", exception)
                listener.onPhotoSaved(null)
                listener.onMessage("A foto não foi salva (${exception.imageCaptureError})")
            }
        })
    }

    // ------------------------------------------------------------------ vídeo

    fun startRecording() {
        val vc = videoCapture ?: return listener.onMessage("A câmera ainda não está pronta")
        if (recording != null) return
        val free = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (free < MIN_FREE_BYTES) return listener.onMessage("Pouco espaço livre para gravar")
        vc.targetRotation = targetRotation
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "NOVA_${timestamp()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Nova Camera")
        }
        val output = MediaStoreOutputOptions.Builder(activity.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values).build()
        var pending = vc.output.prepareRecording(activity, output)
        var audio = false
        if (settings.recordAudio && ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) {
            try {
                pending = pending.withAudioEnabled(); audio = true
            } catch (e: SecurityException) {
                Log.w(TAG, "Microfone recusado; gravando sem áudio", e)
            }
        }
        var lastSecond = -1L
        recording = try {
            pending.start(main) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordingPhase = RecordingPhase.RECORDING
                        listener.onRecording(recordingPhase, 0)
                        if (!audio) listener.onMessage("Gravando sem áudio")
                    }
                    is VideoRecordEvent.Status -> {
                        val s = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                        if (s != lastSecond) { lastSecond = s; listener.onRecording(recordingPhase, s) }
                    }
                    is VideoRecordEvent.Pause -> { recordingPhase = RecordingPhase.PAUSED; listener.onRecording(recordingPhase, lastSecond.coerceAtLeast(0)) }
                    is VideoRecordEvent.Resume -> { recordingPhase = RecordingPhase.RECORDING; listener.onRecording(recordingPhase, lastSecond.coerceAtLeast(0)) }
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        recordingPhase = RecordingPhase.IDLE
                        val err = if (event.hasError()) describeVideoError(event.error) else null
                        if (err != null) Log.e(TAG, "Finalize vídeo: ${event.error}", event.cause)
                        // Muitos erros ainda produzem arquivo válido (ex.: fonte interrompida).
                        listener.onRecordingFinished(event.outputResults.outputUri.takeIf { it != android.net.Uri.EMPTY }, err)
                        if (settingsChangedDuringRecording) { settingsChangedDuringRecording = false; bind() }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar gravação", e)
            listener.onMessage("Não foi possível iniciar a gravação")
            null
        }
    }

    private var settingsChangedDuringRecording = false

    fun requestRebind() {
        if (recording != null) settingsChangedDuringRecording = true else bind()
    }

    fun pauseOrResumeRecording() {
        val r = recording ?: return
        if (recordingPhase == RecordingPhase.PAUSED) r.resume() else r.pause()
    }

    fun stopRecording() {
        recording?.stop()
    }

    private fun describeVideoError(code: Int): String? = when (code) {
        VideoRecordEvent.Finalize.ERROR_NONE -> null
        VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "Armazenamento cheio: vídeo salvo até o limite"
        VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "Câmera interrompida: vídeo salvo até aquele ponto"
        VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
        VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "Limite de tamanho atingido"
        VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "Gravação curta demais: nenhum vídeo salvo"
        else -> "Falha ao finalizar o vídeo (código $code)"
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    companion object {
        private const val TAG = "NovaCamera"
        private const val AUTO_RETURN_MS = 20_000L
        private const val MIN_FREE_BYTES = 300L * 1024 * 1024
        val ZOOM_CANDIDATES = listOf(0.6f, 1f, 3f, 5f, 10f, 20f, 30f)
    }
}
