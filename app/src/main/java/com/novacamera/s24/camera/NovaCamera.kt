package com.novacamera.s24.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.Environment
import android.os.SystemClock
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
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.HighSpeedVideoSessionConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.novacamera.s24.capture.MirroredPhotoSaver
import com.novacamera.s24.capture.TimelapseEncoder
import com.novacamera.s24.core.TimelapsePlan
import com.novacamera.s24.core.CaptureMode
import com.novacamera.s24.core.DeviceHeat
import com.novacamera.s24.core.CaptureDecision
import com.novacamera.s24.core.DeviceModeFacts
import com.novacamera.s24.core.EnginePrefs
import com.novacamera.s24.core.PhotoSizes
import com.novacamera.s24.core.SizeOption
import com.novacamera.s24.processing.CapturedFrame
import com.novacamera.s24.processing.Job
import com.novacamera.s24.processing.JobKind
import com.novacamera.s24.processing.ProcessingQueue
import com.novacamera.s24.core.LensOption
import com.novacamera.s24.core.LensSelection
import com.novacamera.s24.core.ModeEngine
import com.novacamera.s24.core.ModeResolver
import com.novacamera.s24.core.ModeSupport
import com.novacamera.s24.core.ThermalPolicy
import com.novacamera.s24.core.ZoomCurve
import com.novacamera.s24.core.ZoomRequestGate
import com.novacamera.s24.core.ZoomTelemetry
import com.novacamera.s24.settings.AspectRatioOption
import com.novacamera.s24.settings.FlashOption
import com.novacamera.s24.settings.HdrOption
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
    /** Trabalho pesado fora da thread de interface (ex.: espelhar a selfie). */
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
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

    // Zoom: curva com duração limitada + no máximo uma requisição em voo (ver core/ZoomEngine.kt).
    private var zoomOnLens = 1f          // valor exibido/pretendido agora (na câmera atual)
    private var zoomTarget = 1f
    private var pendingZoomOnLens: Float? = null
    private var zoomAnimating = false
    private var zoomCurve = ZoomCurve(photo = true)
    private val zoomGate = ZoomRequestGate()
    private var physicalLabels: Map<String, String> = emptyMap()
    private var stallCheckFrames = -1

    val ready get() = camera != null && !paused
    val isRecording get() = recording != null || timelapse != null

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
        if (isRecording || paused) return
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
        io.shutdown()
        timelapseExecutor.shutdown()
        videoEffect?.close(); videoEffect = null
    }

    // ------------------------------------------------------------------ seleção

    /**
     * Troca o modo. [rebind] = false quando a interface ainda vai mudar a proporção do visor:
     * o ViewPort precisa ser lido depois do novo layout para a foto corresponder ao enquadramento.
     */
    fun setMode(m: CaptureMode, rebind: Boolean = true) {
        if (isRecording || m == mode) return
        mode = m
        settings.lastMode = m
        resetFocusState()
        evIndex = 0
        if (!m.isPro) manual = ManualControls()
        if (rebind) bind()
    }

    fun switchFacing(rebind: Boolean = true) {
        if (isRecording) return
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
        if (!isRecording) videoCapture?.targetRotation = surfaceRotation
    }

    fun onHeat(h: DeviceHeat) {
        val before = heat?.let(ThermalPolicy::limitVideo)
        heat = h
        if (ThermalPolicy.stopRecording(h) && isRecording) {
            listener.onMessage("Gravação finalizada para proteger o aparelho do aquecimento")
            stopRecording()
        }
        // Reconfigura o vídeo só quando o limite muda e não há gravação em andamento.
        if (mode.isVideo && before != null && before != ThermalPolicy.limitVideo(h) && !isRecording) bind()
    }

    fun supportFor(m: CaptureMode, front: Boolean = isFront): ModeSupport {
        val (bokeh, night, _) = oemFacts[front] ?: Triple(false, false, false)
        val info = defaultInfo(front)
        val ranges = info?.supportedFrameRateRanges?.map { it.lower to it.upper }.orEmpty()
        return ModeResolver.resolve(m, DeviceModeFacts(bokeh, night, ModeResolver.extendedExposureMinFps(ranges),
            highSpeedFps = highSpeedFps(front, info)), EnginePrefs(settings.oemPortrait, settings.oemNight))
    }

    private val highSpeedCache = mutableMapOf<Boolean, Int?>()

    /** Maior taxa de alta velocidade (≥ 120 fps) que a câmera aceita numa sessão real de câmera lenta. */
    private fun highSpeedFps(front: Boolean, info: CameraInfo?): Int? = highSpeedCache.getOrPut(front) {
        if (info == null) return@getOrPut null
        runCatching {
            val hs = Recorder.getHighSpeedVideoCapabilities(info) ?: return@runCatching null
            if (hs.getSupportedQualities(DynamicRange.SDR).isEmpty()) return@runCatching null
            val probe = HighSpeedVideoSessionConfig(VideoCapture.withOutput(Recorder.Builder().build()), Preview.Builder().build())
            info.getSupportedFrameRateRanges(probe).maxOfOrNull { it.upper }?.takeIf { it >= 120 }
        }.getOrNull()
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
        if (isRecording || paused) return
        val support = supportFor(mode)
        val (_, _, oemHdr) = oemFacts[isFront] ?: Triple(false, false, false)

        // Extensões do fabricante só existem na câmera padrão de cada lado.
        val wantedExtension = when {
            !support.canCapture -> ExtensionMode.NONE
            mode == CaptureMode.RETRATO && support.engine == ModeEngine.OEM_EXTENSION -> ExtensionMode.BOKEH
            mode == CaptureMode.NOITE && support.engine == ModeEngine.OEM_EXTENSION -> ExtensionMode.NIGHT
            mode == CaptureMode.FOTO && oemHdr && settings.hdr == HdrOption.OEM -> ExtensionMode.HDR
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

        val wide = mode.isVideo || settings.aspect == AspectRatioOption.RATIO_16_9 || settings.aspect == AspectRatioOption.FULL
        val aspectStrategy = if (wide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                             else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val resolution = ResolutionSelector.Builder().setAspectRatioStrategy(aspectStrategy).build()

        val limitVideo = heat?.let(ThermalPolicy::limitVideo) ?: false
        val previewBuilder = Preview.Builder().setResolutionSelector(resolution)
        var stabilization = false
        var fps = 30
        var qualityLabel: String? = null
        var qualityOptions = emptyList<VideoQualityOption>()
        var fpsOptions = emptyList<Int>()
        var rawAvailable = false
        var rawActive = false
        val highSpeed = mode == CaptureMode.CAMERA_LENTA && support.canCapture

        if (wantedExtension == ExtensionMode.NONE && !highSpeed) {
            Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(statsCallback)
        }
        if (mode == CaptureMode.NOITE && support.engine == ModeEngine.NOVA) {
            val minFps = ModeResolver.extendedExposureMinFps(info.supportedFrameRateRanges.map { it.lower to it.upper })
            val range = info.supportedFrameRateRanges.firstOrNull { it.lower == minFps && it.upper <= 30 }
            if (range != null) previewBuilder.setTargetFrameRate(range)
        }

        val useCases = mutableListOf<UseCase>()
        analysis = null
        sizeOptions = emptyList(); sizeBlocked = emptyList(); chosenSize = null
        imageCapture = null
        videoCapture = null
        var highSpeedConfig: HighSpeedVideoSessionConfig? = null

        if (highSpeed) {
            // Câmera lenta real: sessão de alta velocidade, gravada com marcação de reprodução lenta.
            val hs = Recorder.getHighSpeedVideoCapabilities(info)
            val q = listOf(Quality.FHD, Quality.HD).firstOrNull { hs?.isQualitySupported(it, DynamicRange.SDR) == true } ?: Quality.HD
            val vc = VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(q)).build())
            val pv = Preview.Builder().build()
            val ranges = info.getSupportedFrameRateRanges(HighSpeedVideoSessionConfig(vc, pv))
            fpsOptions = ranges.map { it.upper }.filter { it >= 120 }.distinct().sorted()
            fps = fpsOptions.firstOrNull { it == settings.slowMotionFps } ?: fpsOptions.firstOrNull() ?: 120
            val range = ranges.filter { it.upper == fps }.maxByOrNull { it.lower } ?: Range(fps, fps)
            pv.surfaceProvider = previewView.surfaceProvider
            videoCapture = vc
            highSpeedConfig = HighSpeedVideoSessionConfig(vc, pv, range, true)
            qualityLabel = if (q == Quality.FHD) "FHD" else "HD"
        } else if (mode == CaptureMode.TIMELAPSE) {
            // Timelapse: quadros da análise (RGBA, ~1080p), um por intervalo, codificados no aparelho.
            analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(android.util.Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build())
                .setTargetRotation(targetRotation)
                .build()
            qualityLabel = "1080p"
        } else if (mode.isVideo) {
            val capabilities = Recorder.getVideoCapabilities(info)
            val supported = capabilities.getSupportedQualities(DynamicRange.SDR)
            // API pública de resolução por qualidade (a de VideoCapabilities é interna da biblioteca).
            fun resolutionOf(q: Quality) = runCatching { QualitySelector.getResolution(info, q) }.getOrNull()
            val maxRes = resolutionOf(Quality.HIGHEST)
            val uhdRes = resolutionOf(Quality.UHD)
            qualityOptions = buildList {
                add(VideoQualityOption.AUTO)
                if (maxRes != null && uhdRes != null && maxRes.height > uhdRes.height) add(VideoQualityOption.MAX)
                if (Quality.UHD in supported) add(VideoQualityOption.UHD)
                if (Quality.FHD in supported) add(VideoQualityOption.FHD)
                if (Quality.HD in supported) add(VideoQualityOption.HD)
            }
            val pref = settings.videoQuality.takeIf { it in qualityOptions } ?: VideoQualityOption.AUTO
            // Sob economia de energia ou aquecimento severo, resoluções acima de Full HD cedem lugar.
            val effective = if (limitVideo && (pref == VideoQualityOption.UHD || pref == VideoQualityOption.MAX)) VideoQualityOption.FHD else pref
            val wanted = when (effective) {
                VideoQualityOption.AUTO -> if (limitVideo) listOf(Quality.FHD, Quality.HD) else listOf(Quality.UHD, Quality.FHD, Quality.HD)
                VideoQualityOption.MAX -> listOf(Quality.HIGHEST)
                VideoQualityOption.UHD -> listOf(Quality.UHD, Quality.FHD)
                VideoQualityOption.FHD -> listOf(Quality.FHD, Quality.HD)
                VideoQualityOption.HD -> listOf(Quality.HD)
            }
            val chosen = wanted.firstOrNull { it in supported || it == Quality.HIGHEST }
            qualityLabel = chosen?.let(::resolutionOf)?.let(::videoLabel)
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
            fpsOptions = VIDEO_FPS.filter { f -> info.supportedFrameRateRanges.any { it.lower == f && it.upper == f } }
            val wantedFps = settings.videoFps.takeIf { it in fpsOptions && !(limitVideo && it > 30) } ?: 30
            if (wantedFps != 30) { vb.setTargetFrameRate(Range(wantedFps, wantedFps)); fps = wantedFps }
            videoCapture = vb.build()
        } else if (support.canCapture) {
            // Resoluções reais desta câmera (por lente); o processamento Nova usa a padrão (memória/tempo).
            val lists = JpegSizeLists.from { k ->
                @Suppress("UNCHECKED_CAST")
                Camera2CameraInfo.from(info).getCameraCharacteristic(k as android.hardware.camera2.CameraCharacteristics.Key<Any?>)
            }
            val aspect = PhotoSizes.aspectFor(settings.aspect.label)
            sizeOptions = PhotoSizes.options(lists.regular, lists.highRes, aspect)
            sizeBlocked = PhotoSizes.blocked(sizeOptions, lists.maxResOnly, aspect)
            val processing = processingKind(support) != null
            chosenSize = if (processing) PhotoSizes.standard(sizeOptions)
                         else PhotoSizes.pick(sizeOptions, settings.photoPixels(currentLensKey()))
            val photoSelector = chosenSize?.let { c ->
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspectStrategy)
                    .apply { if (c.highResolution) setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE) }
                    .setResolutionStrategy(ResolutionStrategy(android.util.Size(c.width, c.height),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
            } ?: resolution
            // Rajadas (Noturno/HDR Nova) precisam de intervalo curto entre quadros.
            val burst = processingKind(support) == JobKind.NIGHT || processingKind(support) == JobKind.HDR
            val ib = ImageCapture.Builder()
                .setCaptureMode(if (burst) ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY else ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(photoSelector)
                .setTargetRotation(targetRotation)
            val formats = runCatching { ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats }.getOrDefault(emptySet())
            rawAvailable = mode == CaptureMode.PRO && !isFront && ImageCapture.OUTPUT_FORMAT_RAW_JPEG in formats
            rawActive = rawAvailable && settings.raw
            if (rawActive) ib.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
            imageCapture = ib.build()
        }

        val group = if (highSpeedConfig == null) {
            val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }
            useCases += preview
            imageCapture?.let { useCases += it }
            videoCapture?.let { useCases += it }
            analysis?.let { useCases += it }
            val retired = videoEffect
            videoEffect = null
            retiredEffect = retired
            val sig = signatureStyle()
            // Assinatura no vídeo: só no Vídeo/Vídeo Pro e fora do vídeo frontal espelhado (a ordem
            // espelho/rotação do codificador não é garantida e o texto poderia sair invertido).
            if (sig != null && settings.signatureVideo && !videoEffectFailed && videoCapture != null &&
                (mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO) && !(isFront && settings.mirrorFrontVideo)) {
                videoEffect = signatureEffect(sig)
            }
            UseCaseGroup.Builder().apply {
                // O ViewPort faz a foto salva corresponder exatamente ao que a pré-visualização mostra.
                previewView.viewPort?.let(::setViewPort)
                useCases.forEach(::addUseCase)
                videoEffect?.let(::addEffect)
            }.build()
        } else null

        try {
            p.unbindAll()
            retiredEffect?.close(); retiredEffect = null
            camera = if (highSpeedConfig != null) p.bindToLifecycle(activity, selector, highSpeedConfig)
                     else p.bindToLifecycle(activity, selector, group!!)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao configurar $mode", e)
            camera = null
            // Recuo em degraus, do ajuste mais específico ao mais básico.
            when {
                videoEffect != null -> {
                    videoEffect?.close(); videoEffect = null
                    videoEffectFailed = true
                    listener.onMessage("Assinatura no vídeo indisponível nesta configuração; gravando sem ela")
                }
                fps > 30 && !highSpeed -> {
                    settings.videoFps = 30
                    listener.onMessage("$fps fps indisponível nesta combinação; usando 30 fps")
                }
                rawActive -> {
                    settings.raw = false
                    listener.onMessage("RAW indisponível nesta configuração; usando só JPEG")
                }
                settings.photoPixels(currentLensKey()) != null && !mode.isVideo -> {
                    settings.setPhotoPixels(currentLensKey(), null)
                    listener.onMessage("${chosenSize?.label ?: "Esta resolução"} indisponível nesta combinação; usando a padrão")
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
        zoomCurve = ZoomCurve(photo = !mode.isVideo)
        zoomCurve.jumpTo(zoomOnLens)
        zoomGate.reset()
        cam.cameraControl.setZoomRatio(zoomOnLens)
        listener.onZoom(equivalent(zoomOnLens))
        physicalLabels = LensCatalog.physicalLabels(cam.cameraInfo)
        synchronized(telemetry) {
            telemetry.setExpectedFps(fps)
            telemetry.log(SystemClock.uptimeMillis(), "sessão ${mode.label} · câmera ${caps?.cameraId} · ${fps} fps")
        }

        val exposure = cam.cameraInfo.exposureState
        evIndex = evIndex.coerceIn(exposure.exposureCompensationRange.lower, exposure.exposureCompensationRange.upper)
        if (exposure.isExposureCompensationSupported) cam.cameraControl.setExposureCompensationIndex(evIndex)
        applyInterop()
        applyVideoMetering()

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
            hdr = if (mode == CaptureMode.FOTO) when {
                wantedExtension == ExtensionMode.HDR -> HdrOption.OEM
                settings.hdr == HdrOption.NOVA -> HdrOption.NOVA
                else -> HdrOption.OFF
            } else HdrOption.OFF,
            processing = processingKind(support),
            oemHdrActive = wantedExtension == ExtensionMode.HDR,
            extensionActive = wantedExtension != ExtensionMode.NONE,
            canLockExposure = wantedExtension == ExtensionMode.NONE && caps!!.aeLockAvailable,
            videoQualityLabel = qualityLabel, videoFps = fps, stabilization = stabilization,
            screenFlash = isFront && !cam.cameraInfo.hasFlashUnit(),
            photoResolution = imageCapture?.resolutionInfo?.resolution,
            photoSizeOptions = if (processingKind(support) == null) sizeOptions else emptyList(),
            photoSize = chosenSize, photoSizeBlocked = sizeBlocked,
            rawAvailable = rawAvailable, rawActive = rawActive,
            videoQualityOptions = qualityOptions, videoFpsOptions = fpsOptions,
            timelapseIntervalMs = if (mode == CaptureMode.TIMELAPSE) settings.timelapseIntervalMs else null,
        )
        state = s
        listener.onCameraState(s)
        // A resolução escolhida tem de ser a gravada: se o CameraX recuou para outra, o usuário é avisado.
        val actual = s.photoResolution
        val wanted = chosenSize
        if (actual != null && wanted != null && actual.width.toLong() * actual.height < wanted.pixels * 0.9)
            listener.onMessage("${wanted.label} indisponível nesta combinação; a foto sairá com " +
                "${Math.round(actual.width.toLong() * actual.height / 1e6)} MP")
    }

    private var sizeOptions: List<SizeOption> = emptyList()
    private var sizeBlocked: List<SizeOption> = emptyList()
    private var chosenSize: SizeOption? = null

    /** Chave da câmera para a resolução escolhida (cada lente exposta tem a sua). */
    private fun currentLensKey() = "${if (isFront) "f" else "b"}:${currentLensId ?: defaultLensId}"

    /** Define a resolução da lente atual (null = padrão) e reconfigura. */
    fun setPhotoSize(option: SizeOption?) {
        val standard = PhotoSizes.standard(sizeOptions)
        settings.setPhotoPixels(currentLensKey(), option?.takeIf { it != standard }?.pixels)
        bind()
    }

    /** Qual processamento Nova este modo usa (null = foto comum). */
    private fun processingKind(support: ModeSupport = supportFor(mode)): JobKind? = when {
        !support.canCapture -> null
        mode == CaptureMode.NOITE && support.engine == ModeEngine.NOVA -> JobKind.NIGHT
        mode == CaptureMode.RETRATO && support.engine == ModeEngine.NOVA -> JobKind.PORTRAIT
        mode == CaptureMode.FOOD -> JobKind.FOOD
        mode == CaptureMode.FOTO && settings.hdr == HdrOption.NOVA -> JobKind.HDR
        else -> null
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

    private fun videoLabel(size: android.util.Size): String {
        val h = minOf(size.width, size.height)
        return when {
            h >= 4320 -> "8K"; h >= 2160 -> "4K"; h >= 1080 -> "FHD"; h >= 720 -> "HD"; else -> "${h}p"
        }
    }

    fun refreshFlash() { camera?.let(::applyFlash) }

    fun setTorch(on: Boolean) {
        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(on)
    }

    // ------------------------------------------------------------------ zoom

    private fun equivalent(onLens: Float) = (currentLens?.intrinsic ?: 1f) * onLens

    /** Toque numa parada: anima até [equivalentZoom]; troca de câmera se outra cobrir melhor (fora da gravação). */
    fun zoomTo(equivalentZoom: Float, allowLensSwitch: Boolean = true) {
        val lens = currentLens
        if (allowLensSwitch && !isRecording && activeExtension == ExtensionMode.NONE) {
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
        val target = clampZoom(equivalentZoom / intrinsic) ?: return
        zoomTarget = target
        zoomCurve.animateTo(zoomOnLens, target, SystemClock.uptimeMillis())
        startZoomLoop()
    }

    /** Arraste na barra: segue o dedo (no vídeo com um amortecimento curto para não tremer). */
    fun zoomFollow(equivalentZoom: Float) {
        val target = clampZoom(equivalentZoom / (currentLens?.intrinsic ?: 1f)) ?: return
        followTarget(target)
    }

    /** Pinça: multiplica o alvo atual, dentro dos limites da câmera em uso. */
    fun zoomBy(scale: Float) {
        val target = clampZoom(zoomTarget * scale) ?: return
        followTarget(target)
    }

    private fun followTarget(target: Float) {
        zoomTarget = target
        if (mode.isVideo) zoomCurve.animateTo(zoomOnLens, target, SystemClock.uptimeMillis(), FOLLOW_SMOOTHING_MS)
        else zoomCurve.jumpTo(target)
        startZoomLoop()
    }

    private fun clampZoom(onLens: Float): Float? {
        val z = camera?.cameraInfo?.zoomState?.value ?: return null
        return onLens.coerceIn(z.minZoomRatio, z.maxZoomRatio)
    }

    private fun startZoomLoop() {
        if (zoomAnimating) return
        zoomAnimating = true
        Choreographer.getInstance().postFrameCallback(zoomFrame)
    }

    private val zoomFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (camera == null) { zoomAnimating = false; return }
            val now = SystemClock.uptimeMillis()
            val value = zoomCurve.valueAt(now)
            if (value != zoomOnLens) {
                zoomOnLens = value
                listener.onZoom(equivalent(value))
            }
            // A interface acompanha a curva a cada quadro; a câmera recebe só o valor mais recente
            // quando terminou de aplicar o anterior (sem fila de requisições = sem engasgos).
            zoomGate.offer(value)?.let(::sendZoom)
            if (!zoomCurve.finished(now)) Choreographer.getInstance().postFrameCallback(this)
            else zoomAnimating = false
        }
    }

    private fun sendZoom(value: Float) {
        val cam = camera ?: return zoomGate.reset()
        synchronized(telemetry) { telemetry.command(value, SystemClock.uptimeMillis()) }
        val future = cam.cameraControl.setZoomRatio(value)
        future.addListener({ zoomGate.completed()?.let(::sendZoom) }, main)
    }

    /** Nome da lente física ativa, quando a câmera informa (LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID). */
    fun lensLabel(physicalId: String?): String? = physicalId?.let { physicalLabels[it] ?: "Lente $it" }

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
        val manualFocus = mode.isPro && manual.focusDiopters != null
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
                applyVideoMetering()
            }, AUTO_RETURN_MS)
        }, hold * 1000L)
    }

    /** Desfaz ponto manual, bloqueio e compensação: volta ao automático completo. */
    fun resetToAuto() {
        resetFocusState()
        camera?.cameraControl?.cancelFocusAndMetering()
        setExposureIndex(0)
        applyInterop()
        applyVideoMetering()
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
        val m = if (mode.isPro) manual else ManualControls()
        if (m.manualExposure && c.manualSensor) {
            val iso = (m.iso ?: latestStats?.iso ?: 100).let { v -> c.isoRange?.let { v.coerceIn(it.lower, it.upper) } ?: v }
            val exp = (m.exposureNs ?: latestStats?.exposureNs ?: 16_666_666L)
                .let { v -> c.exposureRangeNs?.let { v.coerceIn(it.lower, it.upper) } ?: v }
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exp)
            // Sem SENSOR_FRAME_DURATION: a câmera estende o quadro quando a exposição exige.
        }
        if (m.focusDiopters != null && c.manualFocus) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, m.focusDiopters.coerceIn(0f, c.minFocusDistance))
        }
        if (m.awbMode != null && m.awbMode in c.awbModes) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, m.awbMode)
        }
        val facePriority = settings.facePriority && c.facePriorityScene && !userPointActive &&
            !mode.isPro && !m.manualExposure
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
        // OIS explícita quando a lente anuncia (alguns firmwares só ligam em templates de foto).
        if (c.opticalStabilization) {
            b.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
        }
        val manualActive = m.manualExposure && c.manualSensor
        runCatching {
            val future = Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())
            if (manualActive) watchForStall()
            future
        }.onFailure {
            Log.w(TAG, "Interop Camera2 recusado", it)
            if (manualActive) revertManual("A câmera recusou esta combinação manual")
        }
    }

    /**
     * Vigia do modo manual: se o sensor parar de entregar quadros depois de uma combinação
     * ISO/velocidade, volta ao automático em vez de deixar a tela preta.
     */
    private fun watchForStall() {
        stallCheckFrames = synchronized(telemetry) { telemetry.frames }
        // Sem resultados de captura (firmware não entrega o callback), não há como medir: não interfere.
        if (stallCheckFrames <= 0) return
        handler.removeCallbacks(stallCheck)
        handler.postDelayed(stallCheck, STALL_TIMEOUT_MS)
    }

    private val stallCheck = Runnable {
        val now = synchronized(telemetry) { telemetry.frames }
        val exposureNs = manual.exposureNs ?: 0L
        // Exposições longas legitimamente entregam poucos quadros por segundo.
        if (camera != null && manual.manualExposure && now == stallCheckFrames && exposureNs < STALL_TIMEOUT_MS * 1_000_000L) {
            revertManual("O sensor parou de responder a esta combinação; voltando ao automático")
        }
    }

    private fun revertManual(message: String) {
        manual = manual.copy(iso = null, exposureNs = null)
        listener.onMessage(message)
        listener.onManualReverted(manual)
        applyInterop()
    }

    /**
     * Vídeo sem ponto escolhido: mede a exposição pela região central. Ao aproximar, a borda
     * (janelas, céu) sai do quadro sem puxar a exposição, reduzindo os saltos durante o zoom.
     */
    private fun applyVideoMetering() {
        val cam = camera ?: return
        if (!mode.isVideo || userPointActive || !settings.videoCenterMetering || activeExtension != ExtensionMode.NONE) return
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE).disableAutoCancel().build()
        if (cam.cameraInfo.isFocusMeteringSupported(action)) cam.cameraControl.startFocusAndMetering(action)
    }

    @Volatile private var lowLightFpsWarned = false
    private var lastSensorReportMs = 0L

    private val statsCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
            val exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
            val focus = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f
            val stats = AutoStats(iso, exp, focus)
            latestStats = stats
            val zoom = if (Build.VERSION.SDK_INT >= 30) result.get(CaptureResult.CONTROL_ZOOM_RATIO) else null
            val physical = runCatching { result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) }.getOrNull()
            val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: 0L
            val lensChanged = synchronized(telemetry) { telemetry.frame(ts, zoom, physical, SystemClock.uptimeMillis()) }
            if (lensChanged) handler.post { listener.onActiveLens(lensLabel(physical)) }
            val now = System.currentTimeMillis()
            if (now - lastSensorReportMs >= 500L) {
                lastSensorReportMs = now
                sensorReport = "Prévia (não metadados da foto): câmera ${caps?.cameraId}, físico ${physical ?: "não informado"}, " +
                    "zoom $zoom, focal ${result.get(CaptureResult.LENS_FOCAL_LENGTH)} mm, ISO $iso, exposição $exp ns, " +
                    "foco $focus D, AF ${result.get(CaptureResult.CONTROL_AF_STATE)}, AE ${result.get(CaptureResult.CONTROL_AE_STATE)}, " +
                    "AWB ${result.get(CaptureResult.CONTROL_AWB_STATE)}, ganhos ${result.get(CaptureResult.COLOR_CORRECTION_GAINS)}, " +
                    "OIS ${result.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)}, " +
                    "EIS ${result.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)}, " +
                    "foto ${imageCapture?.resolutionInfo?.resolution} · atualizado $now ms"
            }
            // Vídeo a 60 fps em pouca luz: a exposição bate no teto de 1/60 s com ISO alto e a imagem
            // escurece (visto nos vídeos 1×→10× do dossiê 0.6, sobretudo nas teles). Avisa uma vez.
            if (mode.isVideo && (state?.videoFps ?: 30) >= 60 && !lowLightFpsWarned &&
                iso >= 1600 && exp >= 15_000_000L) {
                lowLightFpsWarned = true
                handler.post { listener.onMessage("Pouca luz a 60 fps: a imagem escurece, sobretudo no zoom. Use 30 fps para mais luz") }
            }
            // Só o modo Pro exibe esses valores; limite de 4 atualizações por segundo.
            if (mode.isPro && now - lastStatsPost > 250) {
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
        processingKind()?.let { return captureForProcessing(ic, it) }
        if (isFront && settings.mirrorFrontPhoto) return captureMirrored(ic, values)
        if (state?.rawActive == true) return captureRawPlusJpeg(ic, values)
        val metadata = ImageCapture.Metadata()
        val output = ImageCapture.OutputFileOptions.Builder(
            activity.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).setMetadata(metadata).build()
        ic.takePicture(output, main, object : ImageCapture.OnImageSavedCallback {
            override fun onCaptureStarted() = listener.onCaptureStarted()
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                listener.onPhotoSaved(result.savedUri)
                result.savedUri?.let(::enhanceIfEnabled)
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Falha na foto", exception)
                listener.onPhotoSaved(null)
                listener.onMessage("A foto não foi salva (${exception.imageCaptureError})")
            }
        })
    }

    /** RAW (DNG) + JPEG da mesma exposição; a miniatura usa o JPEG. */
    private fun captureRawPlusJpeg(ic: ImageCapture, jpegValues: ContentValues) {
        val base = (jpegValues.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) ?: "NOVA_${timestamp()}.jpg").removeSuffix(".jpg")
        val rawValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$base.dng")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Nova Camera")
        }
        val resolver = activity.contentResolver
        val rawOut = ImageCapture.OutputFileOptions.Builder(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rawValues).build()
        val jpegOut = ImageCapture.OutputFileOptions.Builder(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, jpegValues).build()
        var pending = 2
        var jpegUri: android.net.Uri? = null
        ic.takePicture(rawOut, jpegOut, main, object : ImageCapture.OnImageSavedCallback {
            override fun onCaptureStarted() = listener.onCaptureStarted()
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                val uri = result.savedUri
                if (uri != null && runCatching { resolver.getType(uri) }.getOrNull()?.contains("jpeg") == true) jpegUri = uri
                if (--pending == 0) listener.onPhotoSaved(jpegUri ?: uri)
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Falha na foto RAW+JPEG", exception)
                pending = -1
                listener.onPhotoSaved(null)
                listener.onMessage("A foto RAW não foi salva (${exception.imageCaptureError})")
            }
        })
    }

    /** Selfie como visualizada: captura em memória e espelha os pixels em segundo plano. */
    private fun captureMirrored(ic: ImageCapture, values: ContentValues) {
        ic.takePicture(io, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureStarted() { handler.post { listener.onCaptureStarted() } }
            override fun onCaptureSuccess(image: ImageProxy) {
                val uri = try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    MirroredPhotoSaver.save(activity.contentResolver, bytes, image.cropRect, image.imageInfo.rotationDegrees, values)
                } catch (e: Exception) {
                    Log.e(TAG, "Falha ao salvar selfie espelhada", e)
                    null
                } finally {
                    image.close()
                }
                handler.post {
                    listener.onPhotoSaved(uri)
                    if (uri == null) listener.onMessage("A selfie não foi salva") else enhanceIfEnabled(uri)
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Falha na selfie", exception)
                handler.post { listener.onPhotoSaved(null); listener.onMessage("A selfie não foi salva (${exception.imageCaptureError})") }
            }
        })
    }

    // ------------------------------------------------------------------ processamento Nova

    /**
     * Captura em memória os quadros que o processamento precisa (rajada para Noturno, bracketing
     * de exposição para HDR, um quadro para Retrato/Food) e entrega à fila em segundo plano.
     * O obturador fica livre assim que os quadros chegam; o processamento não bloqueia a câmera.
     */
    private fun captureForProcessing(ic: ImageCapture, kind: JobKind) {
        if (!ProcessingQueue.canAccept()) return listener.onMessage("Aguarde: ainda processando as fotos anteriores")
        val cam = camera ?: return
        val h = heat
        val exposure = cam.cameraInfo.exposureState
        var captureNote: String? = null
        var steps: List<Int> = when (kind) {
            JobKind.NIGHT -> {
                val max = when {
                    h != null && h.thermalStatus >= ThermalPolicy.SEVERE -> 1
                    h != null && (h.powerSave || h.thermalStatus >= ThermalPolicy.MODERATE) -> 3
                    else -> ModeResolver.NIGHT_FRAMES
                }
                val st = latestStats
                val (n, note) = CaptureDecision.nightFrames(st?.iso, st?.exposureNs, max)
                captureNote = note
                List(n) { 0 }
            }
            JobKind.HDR -> if (exposure.isExposureCompensationSupported && aeAdjustable())
                ModeResolver.hdrBracket(exposure.exposureCompensationStep.numerator, exposure.exposureCompensationStep.denominator,
                    exposure.exposureCompensationRange.lower - evIndex, exposure.exposureCompensationRange.upper - evIndex)
                else listOf(0)
            else -> listOf(0)
        }
        val mirror = isFront && settings.mirrorFrontPhoto
        val frames = ArrayList<CapturedFrame>()
        val baseEv = evIndex
        ic.targetRotation = targetRotation

        fun submit() {
            if (baseEv != evIndex || steps.any { it != 0 }) cam.cameraControl.setExposureCompensationIndex(baseEv)
            if (frames.isEmpty()) { listener.onPhotoSaved(null); listener.onMessage("A foto não foi capturada"); return }
            // Ordem do HDR: a referência (EV 0) é sempre o índice 0.
            ProcessingQueue.submit(activity, Job(kind, frames.sortedBy { if (it.evIndex == baseEv) 0 else 1 }, mirror,
                zoom = equivalent(zoomOnLens), signature = signatureStyle(), captureNote = captureNote))
            listener.onProcessingQueued(kind)
        }

        fun shoot(i: Int) {
            if (i == steps.size || camera !== cam) return submit()
            val take = {
                ic.takePicture(io, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureStarted() { if (i == 0) handler.post { listener.onCaptureStarted() } }
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                            frames += CapturedFrame(bytes, image.imageInfo.rotationDegrees, android.graphics.Rect(image.cropRect), baseEv + steps[i])
                            // HDR: depois da referência, decide se os quadros extras valem a pena.
                            if (i == 0 && kind == JobKind.HDR && steps.size > 1) {
                                val (clip, deep) = frameStats(bytes)
                                val (keep, note) = CaptureDecision.hdrSteps(steps, clip, deep)
                                steps = keep; captureNote = note
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "quadro $i inválido", e)
                        } finally { image.close() }
                        handler.post {
                            if (steps.size > 1 && i + 1 < steps.size) listener.onBurstProgress(i + 1, steps.size)
                            shoot(i + 1)
                        }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Falha no quadro $i", exception)
                        // Com parte dos quadros já capturada, o processamento segue com o que houver.
                        handler.post { submit() }
                    }
                })
            }
            if (steps[i] != 0) {
                val f = cam.cameraControl.setExposureCompensationIndex(baseEv + steps[i])
                // O futuro termina quando o AE converge na nova compensação.
                f.addListener({ take() }, main)
            } else if (i > 0 && steps[i - 1] != 0) {
                cam.cameraControl.setExposureCompensationIndex(baseEv).addListener({ take() }, main)
            } else take()
        }
        shoot(0)
    }

    /** Fração estourada e de sombras profundas de um JPEG (decodificado bem reduzido, rápido). */
    private fun frameStats(jpeg: ByteArray): Pair<Float, Float> = runCatching {
        val bmp = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = 16 }) ?: return 0f to 0f
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        bmp.recycle()
        var clip = 0; var deep = 0
        for (c in px) {
            val l = com.novacamera.s24.core.imaging.Px.luma(c)
            if (l > 0.97f) clip++ else if (l < 0.06f) deep++
        }
        clip.toFloat() / px.size to deep.toFloat() / px.size
    }.getOrDefault(0f to 0f)

    private var videoEffect: androidx.camera.effects.OverlayEffect? = null
    /** Efeito da configuração anterior: só é fechado depois que a câmera solta a sessão. */
    private var retiredEffect: androidx.camera.effects.OverlayEffect? = null
    /** Falhou nesta sessão do app: não tenta de novo até reabrir (a preferência do usuário fica). */
    private var videoEffectFailed = false
    private val effectThread by lazy { android.os.HandlerThread("nova-video-signature").also { it.start() } }

    /** Desenha a assinatura em cada quadro gravado (não na pré-visualização). */
    private fun signatureEffect(style: com.novacamera.s24.processing.SignatureStyle): androidx.camera.effects.OverlayEffect {
        val effect = androidx.camera.effects.OverlayEffect(androidx.camera.core.CameraEffect.VIDEO_CAPTURE, 0,
            Handler(effectThread.looper)) { t -> Log.e(TAG, "Assinatura no vídeo", t) }
        var cached: Triple<android.graphics.Matrix, Int, Int>? = null
        var cachedKey = ""
        effect.setOnDrawListener { frame ->
            val canvas = frame.overlayCanvas
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            if (!frame.isMirroring) {
                val key = "${frame.cropRect}/${frame.rotationDegrees}"
                if (key != cachedKey) { cached = com.novacamera.s24.processing.Signature.uprightToBuffer(frame.cropRect, frame.rotationDegrees); cachedKey = key }
                val (m, w, h) = cached!!
                canvas.save()
                canvas.concat(m)
                com.novacamera.s24.processing.Signature.drawOn(canvas, w, h, style)
                canvas.restore()
            }
            true
        }
        return effect
    }

    private fun signatureStyle(): com.novacamera.s24.processing.SignatureStyle? =
        if (!settings.signatureEnabled) null else com.novacamera.s24.processing.SignatureStyle(
            settings.signaturePosition, settings.signatureSize, settings.signatureOpacity)

    /** Sem exposição manual nem extensão: a compensação de EV realmente muda a exposição. */
    private fun aeAdjustable() = !manual.manualExposure && activeExtension == ExtensionMode.NONE

    /**
     * FOTO: a foto comum já está salva; em segundo plano ela é analisada e, se valer a pena (ou se
     * a assinatura estiver ligada), substituída pela versão pronta.
     */
    private fun enhanceIfEnabled(uri: android.net.Uri) {
        val sig = signatureStyle()
        if ((!settings.autoProcess && sig == null) || mode != CaptureMode.FOTO || !ProcessingQueue.canAccept()) return
        val zoom = equivalent(zoomOnLens)
        io.execute {
            val bytes = runCatching { activity.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@execute
            val rotation = runCatching {
                androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).rotationDegrees
            }.getOrDefault(0)
            val job = Job(JobKind.AUTO, listOf(CapturedFrame(bytes, rotation, null)), mirror = false, zoom = zoom,
                sourceUri = uri, signature = sig, autoTone = settings.autoProcess)
            handler.post { ProcessingQueue.submit(activity, job) }
        }
    }

    // ------------------------------------------------------------------ vídeo

    // ------------------------------------------------------------------ timelapse

    private var analysis: ImageAnalysis? = null
    private var timelapse: TimelapseRun? = null
    private val timelapseExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private class TimelapseRun(val plan: TimelapsePlan, val startMs: Long) {
        var encoder: TimelapseEncoder? = null
        var lastFrameMs: Long? = null
        @Volatile var stopping = false
        var failed: String? = null
    }

    private fun startTimelapse() {
        val a = analysis ?: return listener.onMessage("A câmera ainda não está pronta")
        if (timelapse != null) return
        val free = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (free < MIN_FREE_BYTES) return listener.onMessage("Pouco espaço livre para gravar")
        val run = TimelapseRun(TimelapsePlan(settings.timelapseIntervalMs, settings.timelapseMaxSeconds), SystemClock.elapsedRealtime())
        timelapse = run
        a.targetRotation = targetRotation
        val name = "NOVA_${timestamp()}_timelapse.mp4"
        recordingPhase = RecordingPhase.RECORDING
        listener.onRecording(recordingPhase, 0)
        listener.onMessage("Timelapse: 1 quadro a cada ${TimelapsePlan.intervalLabel(run.plan.intervalMs)} · ${run.plan.speedup}× mais rápido")
        a.setAnalyzer(timelapseExecutor) { image ->
            try {
                if (run.stopping || run.failed != null) return@setAnalyzer
                val now = SystemClock.elapsedRealtime()
                if (!run.plan.due(now, run.lastFrameMs)) return@setAnalyzer
                run.lastFrameMs = now
                val bmp = image.toBitmap()
                // toBitmap() pode já vir recortado conforme a versão; só aplica o recorte se não veio.
                val crop = if (bmp.width == image.width && bmp.height == image.height) android.graphics.Rect(image.cropRect)
                           else android.graphics.Rect(0, 0, bmp.width, bmp.height)
                val rot = image.imageInfo.rotationDegrees
                val enc = run.encoder ?: run {
                    val (w, h) = if (rot % 180 == 0) TimelapsePlan.outputSize(crop.width(), crop.height())
                                 else TimelapsePlan.outputSize(crop.height(), crop.width())
                    TimelapseEncoder(activity.contentResolver, w, h, name).also { run.encoder = it }
                }
                enc.addFrame(bmp, crop, rot)
                bmp.recycle()
                val elapsed = now - run.startMs
                handler.post { if (timelapse === run) listener.onRecording(RecordingPhase.RECORDING, elapsed / 1000) }
                if (run.plan.finished(elapsed)) handler.post { stopRecording() }
            } catch (e: Exception) {
                Log.e(TAG, "Falha no timelapse", e)
                run.failed = "Falha ao gravar o timelapse"
                handler.post { stopRecording() }
            } finally {
                image.close()
            }
        }
    }

    private fun stopTimelapse() {
        val run = timelapse ?: return
        if (run.stopping) return
        run.stopping = true
        analysis?.clearAnalyzer()
        timelapseExecutor.execute {
            val uri = runCatching { run.encoder?.finish() }.onFailure { Log.e(TAG, "finalizar timelapse", it) }.getOrNull()
            val frames = run.encoder?.frames ?: 0
            handler.post {
                timelapse = null
                recordingPhase = RecordingPhase.IDLE
                val err = run.failed ?: if (uri == null) "Timelapse curto demais: nenhum vídeo salvo" else null
                listener.onRecordingFinished(uri, err)
                if (uri != null) listener.onMessage("Timelapse salvo: $frames quadros · " +
                    String.format(Locale.US, "%.1f s de vídeo", frames / run.plan.outputFps.toFloat()).replace('.', ','))
                if (settingsChangedDuringRecording) { settingsChangedDuringRecording = false; bind() }
            }
        }
    }

    fun startRecording() {
        if (mode == CaptureMode.TIMELAPSE) return startTimelapse()
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
        // Câmera lenta é gravada sem áudio (o som esticado não teria utilidade).
        if (mode != CaptureMode.CAMERA_LENTA && settings.recordAudio && ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
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
                        if (!audio && mode != CaptureMode.CAMERA_LENTA) listener.onMessage("Gravando sem áudio")
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
        if (isRecording) settingsChangedDuringRecording = true else bind()
    }

    fun pauseOrResumeRecording() {
        if (timelapse != null) return listener.onMessage("O timelapse não pode ser pausado; toque no obturador para finalizar")
        val r = recording ?: return
        if (recordingPhase == RecordingPhase.PAUSED) r.resume() else r.pause()
    }

    fun stopRecording() {
        recording?.stop()
        stopTimelapse()
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
        private const val STALL_TIMEOUT_MS = 1_500L
        /** Amortecimento do zoom seguindo o dedo no vídeo (evita tremor de pinça na gravação). */
        private const val FOLLOW_SMOOTHING_MS = 110L
        /** Registro do zoom compartilhado com a tela de diagnóstico. */
        val telemetry = ZoomTelemetry()
        @Volatile var sensorReport = "Nenhum resultado de prévia recebido"
            private set
        private const val MIN_FREE_BYTES = 300L * 1024 * 1024
        val ZOOM_CANDIDATES = listOf(0.6f, 1f, 3f, 5f, 10f, 20f, 30f)
        val VIDEO_FPS = listOf(24, 30, 60)
    }
}

