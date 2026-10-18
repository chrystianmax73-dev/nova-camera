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
import androidx.camera.core.SessionConfig
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
import com.novacamera.s24.core.ShutterLog
import com.novacamera.s24.core.VideoContract
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
@OptIn(ExperimentalCamera2Interop::class, androidx.camera.core.ExperimentalZeroShutterLag::class)
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
    private var freshSessionRequested = false

    var mode: CaptureMode = settings.lastMode; private set
    var isFront = false; private set
    var state: CameraState? = null; private set
    var recordingPhase = RecordingPhase.IDLE; private set

    private var lenses: List<LensOption> = emptyList()
    private var defaultLensId: String? = null
    private var currentLensId: String? = null
    private var currentLens: LensOption? = null
    private val failedPhysicalRoutes = mutableSetOf<String>()
    private var nativeRun: NativePhotoCapture? = null
    private var released = false
    private var nativePhotoSelected = false
    private var nativeTarget: SizeOption? = null
    private var nativeMeterPoint: Pair<Float,Float>? = null
    @Volatile private var referenceEquivalentMm: Float? = null
    @Volatile private var referenceMeasured = false
    private var caps: CameraCapabilities? = null
    private var activeExtension = ExtensionMode.NONE
    @Volatile private var heat: DeviceHeat? = null
    private var targetRotation = Surface.ROTATION_0
    private var oemFacts = mapOf<Boolean, Triple<Boolean, Boolean, Boolean>>() // isFront → (bokeh, night, hdr)

    // Foco / exposição
    private var userPointActive = false
    private var aeLocked = false
    private var focusToken = 0
    /** Ponto tocado (para focar de novo nele antes de uma rajada) e o zoom no momento do toque. */
    private var userPoint: MeteringPoint? = null
    private var userPointFlags = 0
    private var zoomAtTap = 0f
    /** O AF ainda está parado no ponto tocado (falso depois que volta ao contínuo). */
    private var afAtPoint = false
    private var evIndex = 0
    /** Compensação automática de contraluz (passos de EV), somada ao EV do usuário só quando ele está em 0. */
    private var backlightBoost = 0
    private fun appliedEv() = evIndex + backlightBoost
    /** Captura em andamento (rajada/bracketing): a contraluz automática não mexe no EV no meio. */
    @Volatile private var capturing = false
    var manual = ManualControls(); private set
    @Volatile private var latestStats: AutoStats? = null
    /** Giroscópio: só no Noturno do motor Nova, e só com o app em primeiro plano. */
    private val motion = MotionMeter(activity)
    private var motionWanted = false
    private val motionLifecycle = object : androidx.lifecycle.DefaultLifecycleObserver {
        override fun onResume(owner: androidx.lifecycle.LifecycleOwner) { if (motionWanted) motion.start() }
        override fun onPause(owner: androidx.lifecycle.LifecycleOwner) { motion.stop() }
    }.also { activity.lifecycle.addObserver(it) }
    /**
     * Vigia da prévia (thread própria): se a câmera está aberta e os resultados de captura param por
     * [STALL_PREVIEW_MS], registra o contexto ([com.novacamera.s24.core.StallLog]) e reabre a câmera;
     * no Retrato, a reabertura vem sem a análise do desfoque ao vivo nesta sessão (a foto segue com
     * desfoque).
     */
    private val watchdogThread by lazy { android.os.HandlerThread("nova-watchdog").also { it.start() } }
    private val watchdog by lazy { Handler(watchdogThread.looper) }
    private val previewProgress = com.novacamera.s24.core.CaptureProgress()
    private var watchFrames = -1L
    private var watchSince = 0L
    @Volatile private var watchGeneration = 0L
    private var sampledGeneration = -1L
    private var watchedCamera: Camera? = null
    private var watchStartFrames = 0L
    private var watchHasFrame = false
    @Volatile private var watchCamera: Camera? = null
    private var liveBlurBlocked = false
    private val watchTick = object : Runnable {
        override fun run() {
            val cam = watchCamera
            val progress = previewProgress.snapshot()
            val frames = progress.frames
            val now = SystemClock.uptimeMillis()
            val generation = watchGeneration
            if (cam !== watchedCamera || generation != sampledGeneration) {
                watchedCamera = cam; sampledGeneration = generation
                watchStartFrames = frames; watchHasFrame = false
                watchFrames = frames; watchSince = now
            } else if (cam == null || cam !== camera || paused || released) {
                watchFrames = frames; watchSince = now
            } else if (frames != watchFrames) {
                watchHasFrame = frames != watchStartFrames
                watchFrames = frames; watchSince = now
            } else if (now - watchSince >= (if (watchHasFrame) stallLimitMs() else maxOf(stallLimitMs(), 10_000L))) {
                watchSince = now
                handler.post {
                    // A fila principal pode estar ocupada por uma captura. Se houve resultado
                    // novo enquanto o aviso aguardava, não desmonta uma câmera já recuperada.
                    if (generation == watchGeneration && previewProgress.unchanged(progress)) onPreviewStalled(cam)
                }
            }
            if (!released) watchdog.postDelayed(this, 1000L)
        }
    }

    /** Exposições longas (Pro, Noturno apoiado) entregam poucos quadros por segundo legitimamente. */
    private fun stallLimitMs(): Long {
        val expNs = maxOf(latestStats?.exposureNs ?: 0L, if (mode.isPro) manual.exposureNs ?: 0L else 0L,
            nightOverride?.exposureNs ?: 0L)
        return maxOf(STALL_PREVIEW_MS, expNs / 1_000_000L * 3 + 1000L)
    }

    private val recentStalls = ArrayDeque<Long>()
    private val videoRecovery = com.novacamera.s24.core.VideoRecovery()

    private fun onPreviewStalled(cam: Camera) {
        if (cam !== camera || cam !== watchCamera || released || paused) return
        if (mode.isVideo) {
            if ((mode==CaptureMode.VIDEO || mode==CaptureMode.VIDEO_PRO) && videoCapture==null) return
            val action = videoRecovery.stalled(isRecording, boundVideoStabilization)
            if (action == com.novacamera.s24.core.VideoRecovery.Action.IGNORE) return
            // Sem outro tick até uma ligação nova; nunca disputa superfícies com o codificador.
            watchCamera = null
            val context = "${mode.label} · lente ${currentLens?.routeKey} · zoom ${equivalent(zoomOnLens)}× · assinatura=${videoEffect != null || (cinematicBound && settings.signatureEnabled && settings.signatureVideo)} · $cinematicDiagnostic · ${selectedVideoQuality.label}/${boundVideoFps} fps · EIS=$boundVideoStabilization · físico=${previewOptics.first} · apenas prévia=${videoCapture == null} · recuperação=$action"
            com.novacamera.s24.core.StallLog.add(activity, context)
            com.novacamera.s24.core.VideoLog.add(activity, "Prévia parada: $context")
            // Uma parada do sensor não prova falha da assinatura. Primeiro retiramos EIS,
            // mantendo a qualidade, o FPS selecionado e o efeito. Finalize precede o novo bind.
            if (videoRecovery.disableStabilization) videoStabilizationBlocked = true
            pendingZoomOnLens = zoomOnLens
            when (action) {
                com.novacamera.s24.core.VideoRecovery.Action.FINALIZE -> {
                    listener.onMessage("A câmera parou; finalizando o vídeo antes de recuperar")
                    stopRecording()
                }
                com.novacamera.s24.core.VideoRecovery.Action.REBIND -> {
                    listener.onMessage("A câmera parou; recuperando sem mudar resolução ou fps")
                    bind()
                }
                else -> {
                    blockVideoCapture("A câmera não se recuperou. Escolha outra combinação para gravar")
                    if (mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO) bind()
                }
            }
            return
        }
        // Fotos também param o vigia até um novo bind: evita repetir a mensagem a cada 3 s
        // depois de esgotar as tentativas, inclusive com análise/efeito ainda presentes.
        watchCamera = null
        val nowMs = SystemClock.uptimeMillis()
        while (recentStalls.isNotEmpty() && nowMs - recentStalls.first() > 60_000L) recentStalls.removeFirst()
        recentStalls.addLast(nowMs)
        val line = String.format(Locale.US, "%s · %s · lente %s · zoom %.2f× · efeito=%s · análise=%s · versão %s",
            SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date()), mode.label, currentLens?.routeKey ?: "?",
            equivalent(zoomOnLens), portraitEffect != null || videoEffect != null, portraitAnalysis != null,
            runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull() ?: "?")
        Log.w(TAG, "prévia parada: $line")
        com.novacamera.s24.core.StallLog.add(activity, line)
        when {
            recentStalls.size >= 3 -> {
                synchronized(telemetry) { telemetry.log(nowMs,"prévia sem resultados; recuperação encerrada") }
                listener.onMessage("A imagem parou várias vezes; feche e abra a câmera")
                return
            }
            portraitAnalysis != null -> {
                liveBlurBlocked = true
                listener.onMessage("Prévia do desfoque pausada para não travar; a foto continua com desfoque")
            }
            // Segunda parada em 1 min: sem o efeito da prévia nesta sessão (evita reabrir em ciclo).
            recentStalls.size >= 2 && portraitEffect != null -> {
                portraitPreviewFailed = true
                listener.onMessage("Tom e Temperatura ao vivo pausados nesta sessão para a câmera não travar")
            }
            else -> listener.onMessage("A imagem parou; reabrindo a câmera")
        }
        synchronized(telemetry) { telemetry.log(nowMs,"prévia sem resultados; reabrindo") }
        pendingZoomOnLens = zoomOnLens
        bind()
    }

    /** Exposição manual do Noturno durante a rajada (null fora dela). */
    private var nightOverride: com.novacamera.s24.core.NightExposure.Plan? = null
    private var lastStatsPost = 0L

    /** Faixa 15–30 fps aplicada nesta ligação do vídeo; bloqueada na sessão se a câmera recusar. */
    private var autoFpsApplied = false
    private var autoFpsBlocked = false
    /** Vídeo: fps e estabilização da ligação atual; desligados na sessão se a prévia parar com eles. */
    private var boundVideoFps = 30
    private var boundVideoStabilization = false
    private var videoStabilizationBlocked = false
    private var videoProfile = ""
    private var videoBindingIssue: String? = null
    private var requestedVideoSize: VideoContract.Size? = null
    private val videoInspection = java.util.concurrent.Executors.newSingleThreadExecutor()

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
        if (isRecording || paused || nativeRun != null || released) return
        endSubjectTracking()
        paused = true
        nativeRun?.cancel()
        synchronized(telemetry) { telemetry.breakStream() }
        provider?.unbindAll()
        camera = null
    }

    fun resume() {
        if (!paused) return
        paused = false
        bind()
    }

    fun release() {
        if (released) return
        invalidateSubjectTracking()
        released = true
        watchCamera = null
        runCatching { watchdog.removeCallbacks(watchTick); watchdogThread.quitSafely() }
        motion.stop(); motionWanted = false
        activity.lifecycle.removeObserver(motionLifecycle)
        nativeRun?.cancel()
        stopRecording()
        handler.removeCallbacksAndMessages(null)
        // O Finalize ainda precisa do codificador e da fila de trabalho. A Lifecycle do Android
        // também pode interromper a fonte, mas não a soltamos antes de pedir a finalização.
        if (!isRecording) releaseCaptureResources()
        stopPortraitAnalysis()
        portraitEffect?.release(); portraitEffect = null
        portraitExecutor.shutdown()
    }

    private fun releaseCaptureResources() {
        provider?.unbindAll()
        videoEffects.close()
        Handler(effectThread.looper).post { effectThread.quitSafely() }
        io.shutdown()
        videoInspection.shutdown()
        timelapseExecutor.shutdown()
    }

    // ------------------------------------------------------------------ seleção

    /**
     * Troca o modo. [rebind] = false quando a interface ainda vai mudar a proporção do visor:
     * o ViewPort precisa ser lido depois do novo layout para a foto corresponder ao enquadramento.
     */
    fun setMode(m: CaptureMode, rebind: Boolean = true, remember: Boolean = true, portraitVideo: Boolean = false) {
        if (isRecording || nativeRun != null || m == mode) return
        videoRecovery.reset()
        portraitVideoSession=portraitVideo && m==CaptureMode.VIDEO
        mode = m
        if (remember) settings.lastMode = m
        resetFocusState()
        evIndex = 0; backlightBoost = 0
        if (!m.isPro) manual = ManualControls()
        if (rebind) bind()
    }

    fun switchFacing(rebind: Boolean = true) {
        if (isRecording || nativeRun != null) return
        videoRecovery.reset()
        isFront = !isFront
        resetFocusState()
        evIndex = 0; backlightBoost = 0
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
        lenses = LensCatalog.lenses(infos, def,referenceEquivalentMm)
        defaultLensId = def?.let { Camera2CameraInfo.from(it).cameraId }
        currentLensId = defaultLensId
        zoomOnLens = 1f
        zoomTarget = 1f
    }

    private fun selectorFor(id: String?): CameraSelector {
        val base = if (isFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val actualId = lenses.firstOrNull { it.routeKey == id }?.cameraId ?: id
        if (actualId == null || actualId == defaultLensId) return base
        return CameraSelector.Builder().addCameraFilter { infos ->
            infos.filter { Camera2CameraInfo.from(it).cameraId == actualId }
        }.build()
    }

    fun beginNewSession() {
        videoRecovery.reset()
        freshSessionRequested = true
        if (paused) paused = false
        if (hasProvider && !isRecording) bind()
    }

    fun bind() {
        val p = provider ?: return
        if (isRecording || paused || nativeRun != null || released) return
        if (cinematicClosing) return
        if (!settings.cinematicVideo) commonVideoChosen=false
        if(cinematicRequested && commonEffectPending) {
            cinematicClosing=true;invalidateSubjectTracking();watchCamera=null;watchGeneration++;videoEffectGeneration++
            p.unbindAll();camera=null
            EffectQueueBarrier.drain(Handler(effectThread.looper),main,{ videoEffects.close();videoSignatureOverlay=null }) {
                commonEffectPending=false;cinematicClosing=false
                if(!released && !paused)bind()
            }
            return
        }
        if (portraitEffect != null && (cinematicBound || cinematicRequested || mode.isVideo)) {
            val old=portraitEffect!!; portraitEffect=null; cinematicBound=false; cinematicClosing=true
            stopPortraitAnalysis(); p.unbindAll(); camera=null
            old.releaseThen { handler.post { cinematicClosing=false; if(!released && !paused) bind() } }
            return
        }
        invalidateSubjectTracking()
        watchCamera = null
        watchGeneration++
        previewProgress.begin(watchGeneration)
        videoEffectGeneration++
        p.unbindAll()
        camera = null
        videoEffects.close()
        videoSignatureOverlay = null
        videoBindingIssue = if (videoRecovery.exhausted &&
            (mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO))
            "A sessão de gravação não pôde ser mantida; prévia apenas. Escolha outra combinação" else null
        requestedVideoSize = null
        // Rajada do Noturno interrompida pela troca de câmera/modo: a sessão nova volta ao automático.
        nightOverride = null
        synchronized(telemetry) { telemetry.breakStream() }
        Choreographer.getInstance().removeFrameCallback(zoomFrame)
        zoomAnimating = false
        zoomGate.reset()
        if (freshSessionRequested) {
            freshSessionRequested = false
            liveBlurBlocked = false
            cinematicFailed=false; commonVideoChosen=false; cinematicTap=null
            referenceEquivalentMm=null;referenceMeasured=false
            refreshLenses()
            failedPhysicalRoutes.clear()
            pendingZoomOnLens = 1f
            zoomCurve.jumpTo(1f)
            resetFocusState()
        }
        val support = supportFor(mode)
        val (_, _, oemHdr) = oemFacts[isFront] ?: Triple(false, false, false)

        // Vídeo e Retrato na câmera lógica (o aparelho troca para a tele no mesmo zoom). No Retrato,
        // prévia + foto + análise do desfoque numa lente física fixa podem não montar a sessão e a
        // prévia congela (relatado no 3×).
        if(mode.isVideo || mode == CaptureMode.RETRATO) lenses.firstOrNull { it.routeKey==currentLensId && it.physicalCameraId!=null }?.let { child ->
            val parent=lenses.firstOrNull { it.cameraId==child.cameraId && it.physicalCameraId==null }
            pendingZoomOnLens=child.intrinsic*zoomOnLens/(parent?.intrinsic ?: 1f)
            currentLensId=child.cameraId
        }

        // Extensões do fabricante só existem na câmera padrão de cada lado.
        val wantedExtension = when {
            !support.canCapture -> ExtensionMode.NONE
            mode == CaptureMode.RETRATO && support.engine == ModeEngine.OEM_EXTENSION -> ExtensionMode.BOKEH
            mode == CaptureMode.NOITE && support.engine == ModeEngine.OEM_EXTENSION -> ExtensionMode.NIGHT
            mode == CaptureMode.FOTO && oemHdr && settings.hdr == HdrOption.OEM && (settings.photoPixels(currentLensKey()) ?: 0)<16_500_000L -> ExtensionMode.HDR
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
        val profile = "${Camera2CameraInfo.from(info).cameraId}/${selectedVideoQuality}/${selectedVideoFps}/${isFront}/${mode}/${settings.mirrorFrontVideo}/${settings.signatureEnabled && settings.signatureVideo}/${cinematicRequested}/${portraitVideoSession}"
        if (profile != videoProfile) {
            videoProfile = profile; videoStabilizationBlocked = false
            videoEffectFailed = profile in failedVideoEffectProfiles
        }
        currentLens = lenses.firstOrNull { it.routeKey == currentLensId }
            ?: lenses.firstOrNull { it.cameraId == Camera2CameraInfo.from(info).cameraId && it.physicalCameraId == null }
        val pinned = currentLens?.physicalCameraId?.takeIf { wantedExtension == ExtensionMode.NONE && !mode.isVideo }
        val sizeInfo = pinned?.let { id -> info.physicalCameraInfos.firstOrNull { Camera2CameraInfo.from(it).cameraId == id } } ?: info

        val wide = mode.isVideo || settings.aspect == AspectRatioOption.RATIO_16_9 || settings.aspect == AspectRatioOption.FULL
        val aspectStrategy = if (wide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                             else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val resolution = ResolutionSelector.Builder().setAspectRatioStrategy(aspectStrategy).build()

        if (cinematicRequested && (mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO) &&
            (!com.novacamera.s24.core.CinematicPolicy.profile(if(selectedVideoQuality==VideoQualityOption.FHD)1920 else 0,1080,selectedVideoFps,selectedVideoStabilization)))
            videoBindingIssue="Cinematográfico requer Full HD/30 e EIS desligada, escolhidos por você. Ajuste esse perfil ou desative o efeito"
        val limitVideo = heat?.let(ThermalPolicy::limitVideo) ?: false
        val previewBuilder = Preview.Builder().setResolutionSelector(resolution)
        pinned?.let { Camera2Interop.Extender(previewBuilder).setPhysicalCameraId(it) }
        var stabilization = false
        var fps = 30
        var qualityLabel: String? = null
        var qualityOptions = emptyList<VideoQualityOption>()
        var fpsOptions = emptyList<Int>()
        var suggestedVideoFps: Int? = null
        autoFpsApplied = false
        var rawAvailable = false
        var rawActive = false
        val highSpeed = mode == CaptureMode.CAMERA_LENTA && support.canCapture

        if (wantedExtension == ExtensionMode.NONE && !highSpeed) {
            Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(progressCallback(watchGeneration))
        }
        motionWanted = mode == CaptureMode.NOITE && support.engine == ModeEngine.NOVA
        if (motionWanted && activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) motion.start()
        else if (!motionWanted) motion.stop()
        val ranges = info.supportedFrameRateRanges.map { it.lower to it.upper }
        if (mode == CaptureMode.NOITE && support.engine == ModeEngine.NOVA) {
            val minFps = ModeResolver.extendedExposureMinFps(ranges)
            // Sem faixa abaixo de 15 fps (S24 Ultra: [15, 30] é a mais longa), usa a de 15–30.
            val range = info.supportedFrameRateRanges.firstOrNull { it.lower == minFps && it.upper <= 30 }
                ?: ModeResolver.autoVideoFps(ranges)?.let { Range(it.first, it.second) }
            if (range != null) previewBuilder.setTargetFrameRate(range)
        } else if (!mode.isVideo && !highSpeed && wantedExtension == ExtensionMode.NONE && mode != CaptureMode.TIMELAPSE) {
            // Foto, Retrato e Pro no escuro: com 30 fps fixos o automático para em 1/30 s e ISO 3200 e a
            // foto sai escura (tele 5× à noite). Com 15–30 fps ele pode ir a 1/15 s: até +1 EV de luz
            // real, ou o mesmo brilho com ISO menor. Com luz boa continua a 30 fps.
            ModeResolver.autoVideoFps(ranges)?.let { (lo, hi) -> previewBuilder.setTargetFrameRate(Range(lo, hi)) }
        }

        val useCases = mutableListOf<UseCase>()
        analysis = null
        stopPortraitAnalysis()
        retiredPortraitEffect = portraitEffect
        portraitEffect = null
        sizeOptions = emptyList(); sizeBlocked = emptyList(); chosenSize = null
        nativePhotoSelected = false; nativeTarget = null
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
            val pref = selectedVideoQuality
            val explicit = pref != VideoQualityOption.AUTO
            val available = pref in qualityOptions
            if (explicit && !available) videoBindingIssue = "${pref.label} não está disponível nesta câmera; escolha outra resolução"
            if (limitVideo && explicit && (pref == VideoQualityOption.UHD || pref == VideoQualityOption.MAX))
                videoBindingIssue = "${pref.label} bloqueado por temperatura/economia. Escolha outra qualidade ou aguarde"
            val wanted = when (pref) {
                VideoQualityOption.AUTO -> if (limitVideo) listOf(Quality.FHD, Quality.HD) else listOf(Quality.UHD, Quality.FHD, Quality.HD)
                VideoQualityOption.MAX -> listOf(Quality.HIGHEST)
                VideoQualityOption.UHD -> listOf(Quality.UHD)
                VideoQualityOption.FHD -> listOf(Quality.FHD)
                VideoQualityOption.HD -> listOf(Quality.HD)
            }
            val chosen = wanted.firstOrNull { it in supported || it == Quality.HIGHEST }
            val requestedSize = chosen?.let(::resolutionOf)
            if (explicit && requestedSize != null) requestedVideoSize = VideoContract.Size(requestedSize.width, requestedSize.height)
            qualityLabel = requestedSize?.let(::videoLabel)
            // Somente AUTO aceita fallback. Uma seleção indisponível pode ter prévia,
            // mas o obturador permanece bloqueado; ela nunca gera um MP4 de qualidade inferior.
            val selectorForVideo = if (explicit && available) QualitySelector.from(wanted.single())
                else if (explicit) QualitySelector.from(supported.firstOrNull() ?: Quality.FHD)
                else QualitySelector.fromOrderedList(wanted, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
            val recorder = Recorder.Builder().setQualitySelector(selectorForVideo).build()
            val vb = VideoCapture.Builder(recorder)
                .setTargetRotation(targetRotation)
                .setMirrorMode(if (settings.mirrorFrontVideo) MirrorMode.MIRROR_MODE_ON_FRONT_ONLY else MirrorMode.MIRROR_MODE_OFF)
            if (selectedVideoStabilization && !videoStabilizationBlocked) {
                // EIS da gravação, sem solicitar PREVIEW_STABILIZATION (modo 2) na sessão.
                // O suporte de EIS da prévia não garante UHD/60 nem a troca para a tele.
                if (capabilities.isStabilizationSupported) {
                    vb.setVideoStabilizationEnabled(true); stabilization = true
                }
            }
            fpsOptions = VIDEO_FPS.filter { f ->
                info.supportedFrameRateRanges.any { it.lower == f && it.upper == f } &&
                    requestedSize != null && VideoPreflight.codecSupports(requestedSize, f)
            }
            val requestedFps = selectedVideoFps
            if (requestedFps !in fpsOptions)
                videoBindingIssue = "${requestedFps} fps não disponível com ${pref.label} nesta câmera; escolha outra combinação"
            if (limitVideo && requestedFps > 30)
                videoBindingIssue = "${requestedFps} fps bloqueado por temperatura/economia; escolha 30 fps ou aguarde"
            val wantedFps = requestedFps.takeIf { it in fpsOptions } ?: 30 // só prévia quando bloqueado
            if (wantedFps != 30) { vb.setTargetFrameRate(Range(wantedFps, wantedFps)); fps = wantedFps }
            else if (settings.videoAutoFps && !portraitProfile && !autoFpsBlocked && mode != CaptureMode.TIMELAPSE) {
                ModeResolver.autoVideoFps(info.supportedFrameRateRanges.map { it.lower to it.upper })
                    ?.let { (lo, hi) -> vb.setTargetFrameRate(Range(lo, hi)); autoFpsApplied = true }
            }
            if (wantedFps == 30 && !autoFpsApplied) vb.setTargetFrameRate(Range(30, 30))
            boundVideoFps = fps; boundVideoStabilization = stabilization
            videoCapture = vb.build()
        } else if (support.canCapture) {
            // Resoluções reais desta câmera (por lente); o processamento Nova usa a padrão (memória/tempo).
            val lists = JpegSizeLists.from { k ->
                @Suppress("UNCHECKED_CAST")
                Camera2CameraInfo.from(sizeInfo).getCameraCharacteristic(k as android.hardware.camera2.CameraCharacteristics.Key<Any?>)
            }
            val aspect = PhotoSizes.aspectFor(settings.aspect.label)
            val allowNative = mode == CaptureMode.FOTO && !isFront &&
                settings.aspect == AspectRatioOption.RATIO_4_3
            sizeOptions = PhotoSizes.nativeOptions(lists.regular,lists.highRes,lists.maxResOnly,aspect,
                allowNative && Build.VERSION.SDK_INT>=31 && lists.ultraHighResolutionSensor)
            sizeBlocked = PhotoSizes.blocked(sizeOptions, lists.maxResOnly, aspect)
            val processing = processingKind(support) != null
            chosenSize = if (processing && mode != CaptureMode.FOTO) PhotoSizes.standard(sizeOptions)
                         else PhotoSizes.pick(sizeOptions, settings.photoPixels(currentLensKey()))
            nativePhotoSelected = allowNative && wantedExtension==ExtensionMode.NONE && (chosenSize?.megapixels ?: 0.0)>16.5
            nativeTarget = chosenSize
            val cameraXSize = if (nativePhotoSelected) PhotoSizes.standard(sizeOptions) else chosenSize
            val photoSelector = cameraXSize?.let { c ->
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspectStrategy)
                    .apply { if (c.highResolution) setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE) }
                    .setResolutionStrategy(ResolutionStrategy(android.util.Size(c.width, c.height),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
            } ?: resolution
            // Rajadas (Noturno/HDR Nova) precisam de intervalo curto entre quadros.
            val burst = processingKind(support) == JobKind.NIGHT || processingKind(support) == JobKind.HDR
            // Foto de um quadro: disparo sem atraso (ZSL, quadro do buffer recente) quando a câmera
            // anuncia suporte; rajadas continuam com latência mínima, e RAW/extensões sem ZSL.
            // ZSL acrescenta um fluxo de reprocessamento. Somado à análise do desfoque ao vivo do
            // Retrato (prévia + foto + análise + ZSL), a câmera do S24 Ultra para de entregar quadros
            // ~2 s depois de abrir (diagnóstico 0.14.1: 58 quadros e a prévia congela).
            // Diagnóstico 0.14.3 (S24 Ultra, Android 17): com ZSL a prévia também para na frontal e na
            // troca para 3×/5× na Foto (efeito da prévia + foto + reprocessamento), e o quadro ZSL em
            // pouca luz sai com a redução de ruído rápida da prévia (pele "encerada"). ZSL desligado:
            // o disparo instantâneo usa latência mínima, que passa pelo processamento de foto do aparelho.
            val captureMode = when {
                burst -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                settings.instantShutter && wantedExtension == ExtensionMode.NONE -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                else -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            }
            ShutterLog.configured = when {
                wantedExtension != ExtensionMode.NONE -> "extensão do fabricante"
                else -> captureModeLabel(captureMode)
            } + " · ZSL anunciado: ${runCatching { info.isZslSupported }.getOrDefault(false)}" +
                if (settings.instantShutter) "" else " (desligado nas configurações)"
            val ib = ImageCapture.Builder()
                .setCaptureMode(captureMode)
                .setResolutionSelector(photoSelector)
                .setTargetRotation(targetRotation)
            pinned?.let { Camera2Interop.Extender(ib).setPhysicalCameraId(it) }
            val formats = runCatching { ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats }.getOrDefault(emptySet())
            com.novacamera.s24.processing.UltraHdr.deviceReport = "Ultra HDR do aparelho (JPEG_R) para apps: " +
                (if (ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR in formats) "anunciado" else "não anunciado") +
                " · a Nova gera o próprio gainmap no HDR"
            rawAvailable = mode == CaptureMode.PRO && !isFront && ImageCapture.OUTPUT_FORMAT_RAW_JPEG in formats
            rawActive = rawAvailable && settings.raw
            if (rawActive) ib.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
            imageCapture = ib.build()
        }

        var group = if (highSpeedConfig == null) {
            val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }
            useCases += preview
            imageCapture?.takeUnless { nativePhotoSelected }?.let { useCases += it }
            videoCapture?.let { useCases += it }
            analysis?.let { useCases += it }
            portraitPreviewFor(support, wide, pinned, wantedExtension)?.let { (effect, a) ->
                portraitEffect = effect; portraitAnalysis = a; a?.let { useCases += it }
            } ?: listener.onPortraitPreview(null)
            val sig = signatureStyle()
            // O renderizador compensa recorte, rotação e espelho para manter o texto legível.
            if (videoBindingIssue==null && !cinematicBound && sig != null && settings.signatureVideo && !videoEffectFailed && !videoRecovery.exhausted && videoCapture != null &&
                (mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO)) {
                videoEffects.replace { signatureEffect(sig) }
            }
            UseCaseGroup.Builder().apply {
                // O ViewPort faz a foto salva corresponder exatamente ao que a pré-visualização mostra.
                previewView.viewPort?.let(::setViewPort)
                useCases.forEach(::addUseCase)
                videoEffect?.let(::addEffect)
                portraitEffect?.let(::addEffect)
            }.build()
        } else null

        retiredPortraitEffect?.release(); retiredPortraitEffect = null
        if ((mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO) && videoCapture != null) {
            val effects = listOfNotNull(videoEffect, portraitEffect)
            val ranges = runCatching {
                info.getSupportedFrameRateRanges(SessionConfig(useCases, previewView.viewPort, effects))
                    .map { it.lower to it.upper }
            }.onFailure {
                com.novacamera.s24.core.VideoLog.add(activity, "Consulta da sessão indisponível: ${it.javaClass.simpleName}: ${it.message}")
            }.getOrNull()
            val automaticRange = ModeResolver.autoVideoFps(info.supportedFrameRateRanges.map { it.lower to it.upper })
            if (autoFpsApplied && ranges != null && automaticRange !in ranges) {
                autoFpsBlocked = true
                listener.onMessage("Faixa automática recusada pela sessão; tentando 30 fps fixos")
                handler.post { if (!released && !paused) bind() }
                return
            }
            fpsOptions = com.novacamera.s24.core.VideoSessionSupport.fps(fpsOptions, ranges)
            com.novacamera.s24.core.VideoLog.add(activity,
                "Sessão ${videoProfile} · zoom=${equivalent(pendingZoomOnLens ?: zoomOnLens)}× · EIS=$stabilization · assinatura=${videoEffect != null || (cinematicBound && settings.signatureEnabled && settings.signatureVideo)} · cinematográfico=$cinematicBound · FPS da sessão=${ranges ?: "consulta indisponível"}")
            if (selectedVideoFps !in fpsOptions && videoBindingIssue == null) {
                if (stabilization) {
                    videoStabilizationBlocked = true
                    listener.onMessage("Tentando ${selectedVideoQuality.label}/${selectedVideoFps} fps sem estabilização eletrônica")
                    handler.post { if (!released && !paused) bind() }
                    return
                }
                if (videoEffect != null) {
                    videoEffectFailed = true
                    failedVideoEffectProfiles.add(videoProfile)
                    com.novacamera.s24.core.VideoLog.add(activity, "Combinação com assinatura recusada em $videoProfile; testando a mesma resolução/FPS sem efeito")
                    listener.onMessage("Combinação com assinatura recusada; tentando a mesma qualidade sem efeito")
                    handler.post { if (!released && !paused) bind() }
                    return
                }
                videoBindingIssue = "${selectedVideoQuality.label}/${selectedVideoFps} fps não aceito pela sessão desta câmera"
                suggestedVideoFps = com.novacamera.s24.core.VideoSessionSupport.alternative(selectedVideoFps, fpsOptions)
            }
            if (videoBindingIssue != null && (videoRecovery.exhausted || selectedVideoFps !in fpsOptions) && !limitVideo &&
                selectedVideoQuality in qualityOptions)
                suggestedVideoFps = com.novacamera.s24.core.VideoSessionSupport.alternative(selectedVideoFps, fpsOptions)
            if (videoBindingIssue != null) {
                // A combinação recusada não deve congelar a câmera. Apenas prévia; nenhum MP4
                // de qualidade inferior será produzido. Uma alternativa exige escolha explícita.
                videoEffects.close()
                if(cinematicBound) { stopPortraitAnalysis(); portraitEffect?.release(); portraitEffect=null; cinematicBound=false }
                val preview = useCases.filterIsInstance<Preview>().single()
                group = UseCaseGroup.Builder().addUseCase(preview).apply {
                    previewView.viewPort?.let(::setViewPort)
                }.build()
                videoCapture = null
                stabilization = false
                autoFpsApplied = false
                fps = selectedVideoFps
            }
        }

        if (mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO) {
            boundVideoStabilization = stabilization
            boundVideoFps = if (videoCapture == null) 30 else fps
        }
        try {
            previewOptics = null to null
            camera = if (highSpeedConfig != null) p.bindToLifecycle(activity, selector, highSpeedConfig)
                     else p.bindToLifecycle(activity, selector, group!!)
            if (pinned != null) watchPinnedStart(camera, currentLens)
            // Após esgotar o orçamento só a prévia é religada. Não reinicia gravação nem
            // continua o ciclo automático; uma escolha explícita renova o orçamento.
            // Extensões OEM/alta velocidade não fornecem o callback de resultados usado pelo
            // vigia. Ausência de telemetria nesses modos não prova prévia congelada.
            watchCamera = if (com.novacamera.s24.core.CaptureProgress.canWatch(
                wantedExtension==ExtensionMode.NONE && !highSpeed,
                mode==CaptureMode.VIDEO || mode==CaptureMode.VIDEO_PRO,
                videoCapture!=null, videoRecovery.exhausted)) camera else null
            watchdog.removeCallbacks(watchTick); watchdog.postDelayed(watchTick, 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao configurar $mode", e)
            if (mode.isVideo) com.novacamera.s24.core.VideoLog.add(activity,
                "Bind recusado: $videoProfile · EIS=$stabilization · assinatura=${videoEffect != null} · ${e.javaClass.simpleName}: ${e.message}")
            camera = null
            if ((mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO) && stabilization) {
                videoStabilizationBlocked = true
                listener.onMessage("Esta combinação recusou EIS; tentando a mesma resolução e FPS sem EIS")
                handler.post { if (!released && !paused) bind() }
                return
            }
            if ((mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO) && videoEffect == null && !autoFpsApplied) {
                val reason = "Combinação de vídeo indisponível. Altere resolução, fps ou estabilização"
                videoBindingIssue = reason
                if (videoCapture != null) {
                    videoRecovery.rejectBinding()
                    pendingZoomOnLens = pendingZoomOnLens ?: zoomOnLens
                    handler.post { if (!released && !paused) bind() }
                    return
                }
                // Se até a prévia isolada falhar, não reinicia o ciclo.
                com.novacamera.s24.core.VideoLog.add(activity, "Bind recusado: ${selectedVideoQuality.label}/${selectedVideoFps} fps · ${e.javaClass.simpleName}: ${e.message}")
                state?.copy(mode = mode, support = support, isFront = isFront,
                    videoQualityLabel = selectedVideoQuality.label, videoFps = selectedVideoFps,
                    videoQualityOptions = qualityOptions, videoFpsOptions = fpsOptions,
                    videoFpsVariable = false, videoCaptureProblem = reason,
                    videoSuggestedFps = com.novacamera.s24.core.VideoSessionSupport.alternative(selectedVideoFps, fpsOptions))?.let { state = it; listener.onCameraState(it) }
                listener.onMessage(reason)
                return
            }
            // Recuo em degraus, do ajuste mais específico ao mais básico.
            when {
                videoEffect != null -> {
                    videoEffects.close()
                    videoEffectFailed = true
                    failedVideoEffectProfiles.add(videoProfile)
                    listener.onMessage("Assinatura no vídeo indisponível nesta configuração; gravando sem ela")
                }
                autoFpsApplied -> {
                    autoFpsBlocked = true
                    listener.onMessage("Quadros automáticos indisponíveis nesta combinação; gravando a 30 fps fixos")
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
                currentLens?.physicalCameraId != null -> {
                    failedPhysicalRoutes.add(currentLens!!.routeKey)
                    pendingZoomOnLens = equivalent(zoomOnLens)
                    currentLensId = defaultLensId
                    listener.onMessage("Esta lente não pôde ser usada diretamente; a câmera escolhe a lente automaticamente")
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
        val zMin = if (pinned != null) lens?.minZoom ?: 1f else zState?.minZoomRatio ?: lens?.minZoom ?: 1f
        val zMax = if (pinned != null) lens?.maxZoom ?: 1f else zState?.maxZoomRatio ?: lens?.maxZoom ?: 1f
        zoomOnLens = (pendingZoomOnLens ?: zoomOnLens).coerceIn(zMin, zMax)
        pendingZoomOnLens = null
        zoomTarget = zoomOnLens
        zoomCurve = ZoomCurve(photo = !mode.isVideo)
        zoomCurve.jumpTo(zoomOnLens)
        zoomGate.reset()
        cam.cameraControl.setZoomRatio(cameraZoom(zoomOnLens))
        listener.onZoom(equivalent(zoomOnLens))
        watchInitialZoom(cam)
        physicalLabels = LensCatalog.physicalLabels(cam.cameraInfo,referenceEquivalentMm)
        pinned?.let { listener.onActiveLens(lensLabel(it)) }
        synchronized(telemetry) {
            telemetry.setExpectedFps(fps)
            telemetry.log(SystemClock.uptimeMillis(), "sessão ${mode.label} · câmera ${caps?.cameraId} · ${fps} fps")
        }

        val exposure = cam.cameraInfo.exposureState
        evIndex = evIndex.coerceIn(exposure.exposureCompensationRange.lower, exposure.exposureCompensationRange.upper)
        capturing = false
        backlightBoost = backlightBoost.coerceIn(0, maxOf(0, exposure.exposureCompensationRange.upper - evIndex))
        if (exposure.isExposureCompensationSupported) cam.cameraControl.setExposureCompensationIndex(appliedEv())
        applyInterop()
        applyVideoMetering()

        if (mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO) {
            val actual = videoCapture?.resolutionInfo?.cropRect
            if (actual != null) {
                val size = VideoContract.Size(actual.width(), actual.height())
                qualityLabel = videoLabel(android.util.Size(size.width, size.height))
                if (requestedVideoSize?.sameFrame(size) == false)
                    videoBindingIssue = "A sessão não entrega ${selectedVideoQuality.label}; gravação bloqueada. Escolha outra combinação"
            }
            videoBindingIssue?.let { listener.onMessage(it) }
        }
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
                settings.hdr == HdrOption.AUTO && (chosenSize?.megapixels ?: 12.0)<=16.5 -> HdrOption.AUTO
                settings.hdr == HdrOption.NOVA && (chosenSize?.megapixels ?: 12.0)<=16.5 -> HdrOption.NOVA
                else -> HdrOption.OFF
            } else HdrOption.OFF,
            processing = processingKind(support),
            oemHdrActive = wantedExtension == ExtensionMode.HDR,
            extensionActive = wantedExtension != ExtensionMode.NONE,
            canLockExposure = wantedExtension == ExtensionMode.NONE && caps!!.aeLockAvailable,
            videoQualityLabel = qualityLabel, videoFps = fps, stabilization = stabilization,
            screenFlash = isFront && !cam.cameraInfo.hasFlashUnit(),
            photoResolution = if (nativePhotoSelected) chosenSize?.let { android.util.Size(it.width,it.height) } else imageCapture?.resolutionInfo?.resolution,
            photoSizeOptions = if (mode == CaptureMode.FOTO || processingKind(support) == null) sizeOptions else emptyList(),
            photoSize = chosenSize, photoNativeCapture=nativePhotoSelected, photoSizeBlocked = sizeBlocked,
            rawAvailable = rawAvailable, rawActive = rawActive,
            videoQualityOptions = qualityOptions, videoFpsOptions = fpsOptions,
            videoFpsVariable = autoFpsApplied, videoCaptureProblem = videoBindingIssue,
            videoSuggestedFps = suggestedVideoFps,
            timelapseIntervalMs = if (mode == CaptureMode.TIMELAPSE) settings.timelapseIntervalMs else null,
        )
        state = s
        listener.onCameraState(s)
        // A resolução escolhida tem de ser a gravada: se o CameraX recuou para outra, o usuário é avisado.
        val actual = s.photoResolution
        val wanted = chosenSize
        if (!nativePhotoSelected && actual != null && wanted != null && actual.width.toLong()*actual.height < wanted.pixels*0.9 &&
            mode == CaptureMode.FOTO && !isFront && wantedExtension == ExtensionMode.NONE && !settings.raw && settings.aspect==AspectRatioOption.RATIO_4_3) {
            nativePhotoSelected=true;nativeTarget=wanted
            val corrected=s.copy(photoResolution=android.util.Size(wanted.width,wanted.height),photoNativeCapture=true)
            state=corrected;listener.onCameraState(corrected)
            listener.onMessage("${wanted.label} será capturada diretamente pelo sensor")
        } else if (actual != null && wanted != null && !nativePhotoSelected && actual.width.toLong() * actual.height < wanted.pixels * 0.9)
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
        if (mode==CaptureMode.FOTO && option!=null && option.megapixels>16.5 && settings.hdr==HdrOption.OEM) {
            listener.onMessage("Alta resolução usa captura nativa; HDR do fabricante disponível na resolução padrão")
        }
        bind()
    }

    /** Qual processamento Nova este modo usa (null = foto comum). */
    private fun processingKind(support: ModeSupport = supportFor(mode)): JobKind? = when {
        !support.canCapture -> null
        mode == CaptureMode.NOITE && support.engine == ModeEngine.NOVA -> JobKind.NIGHT
        mode == CaptureMode.RETRATO && support.engine == ModeEngine.NOVA -> JobKind.PORTRAIT
        mode == CaptureMode.FOOD -> JobKind.FOOD
        mode == CaptureMode.FOTO && settings.hdr == HdrOption.AUTO && settings.autoProcess && (chosenSize?.megapixels ?: 12.0)<=16.5 -> JobKind.PHOTO
        mode == CaptureMode.FOTO && settings.hdr == HdrOption.NOVA && (chosenSize?.megapixels ?: 12.0)<=16.5 -> JobKind.HDR
        // HDR desligado: a tele com pouca luz ainda junta quadros de mesma exposição (ruído, não HDR).
        mode == CaptureMode.FOTO && settings.hdr == HdrOption.OFF && settings.autoProcess && !isFront &&
            equivalent(zoomOnLens) >= com.novacamera.s24.core.AutoPhotoPolicy.ZOOM_FROM && (chosenSize?.megapixels ?: 12.0)<=16.5 -> JobKind.PHOTO
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

    private fun cameraZoom(onLens: Float): Float {
        val l=currentLens ?: return onLens
        val parent=lenses.firstOrNull { it.cameraId==l.cameraId && it.physicalCameraId==null }
        return if(l.physicalCameraId!=null) l.intrinsic*onLens/(parent?.intrinsic ?: 1f) else onLens
    }

    private fun equivalent(onLens: Float) = (currentLens?.intrinsic ?: 1f) * onLens

    /** Toque numa parada: anima até [equivalentZoom]; troca de câmera se outra cobrir melhor (fora da gravação). */
    fun zoomTo(equivalentZoom: Float, allowLensSwitch: Boolean = true) {
        val lens = currentLens
        if (allowLensSwitch && !isRecording && activeExtension == ExtensionMode.NONE) {
            val st = latestStats
            val enoughLight = st != null && st.iso < 800 && st.exposureNs < 20_000_000L
            val choice = LensSelection.choose(lenses.filter { it.routeKey !in failedPhysicalRoutes &&
                    ((!mode.isVideo && mode != CaptureMode.RETRATO) || it.physicalCameraId==null) }, currentLensId, defaultLensId, equivalentZoom,
                preferNative = !mode.isVideo && !isFront && enoughLight)
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
        dropPointIfReframed(target)
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
        dropPointIfReframed(target)
        zoomTarget = target
        // No vídeo, o mesmo tremor do dedo vira um salto de enquadramento maior quanto maior a
        // ampliação: a suavização cresce com ela (110 ms em 1x, ~200 ms em 10x).
        if (mode.isVideo) zoomCurve.animateTo(zoomOnLens, target, SystemClock.uptimeMillis(),
            com.novacamera.s24.core.ZoomCurve.followSmoothingMs(FOLLOW_SMOOTHING_MS, equivalent(target)))
        else zoomCurve.jumpTo(target)
        startZoomLoop()
    }

    /**
     * Logo depois de vincular, a câmera ainda não está aberta e o CameraX recusa o zoom (o pedido
     * acima pode se perder): a barra mostraria 10x com a imagem em 5x. Quando a câmera abre, o zoom
     * pedido é reaplicado; se o efetivo ainda divergir, a barra passa a mostrar o efetivo — nunca
     * um zoom que não está aplicado.
     */
    private fun watchInitialZoom(cam: androidx.camera.core.Camera) {
        val state = cam.cameraInfo.cameraState
        val obs = object : androidx.lifecycle.Observer<androidx.camera.core.CameraState> {
            override fun onChanged(value: androidx.camera.core.CameraState) {
                if (camera !== cam) { state.removeObserver(this); return }
                if (value.type != androidx.camera.core.CameraState.Type.OPEN) return
                state.removeObserver(this)
                val wanted = cameraZoom(zoomOnLens)
                cam.cameraControl.setZoomRatio(wanted).addListener({
                    if (camera !== cam) return@addListener
                    val actual = cam.cameraInfo.zoomState.value?.zoomRatio ?: return@addListener
                    if (kotlin.math.abs(actual - wanted) > 0.02f * wanted) {
                        synchronized(telemetry) { telemetry.log(SystemClock.uptimeMillis(), "zoom pedido $wanted, aplicado $actual") }
                        val lens = currentLens
                        val parent = lenses.firstOrNull { it.cameraId == lens?.cameraId && it.physicalCameraId == null }
                        zoomOnLens = if (lens?.physicalCameraId != null) actual * (parent?.intrinsic ?: 1f) / lens.intrinsic else actual
                        zoomTarget = zoomOnLens; zoomCurve.jumpTo(zoomOnLens)
                        listener.onZoom(equivalent(zoomOnLens))
                    }
                }, main)
            }
        }
        state.observe(activity, obs)
    }

    /**
     * Lente física fixa que abre mas não entrega quadros (sessão recusada em silêncio pelo
     * firmware): a prévia ficaria congelada. Se os resultados de captura já chegavam antes e
     * param por [PINNED_STALL_MS], a rota é marcada como falha e a câmera reabre na lente
     * automática, no mesmo zoom.
     */
    private fun watchPinnedStart(cam: Camera?, lens: LensOption?) {
        if (cam == null || lens?.physicalCameraId == null) return
        val before = synchronized(telemetry) { telemetry.frames }
        if (before <= 0) return // firmware sem resultados de captura: não há como medir
        handler.postDelayed({
            if (camera !== cam || released || paused || currentLens?.routeKey != lens.routeKey) return@postDelayed
            val now = synchronized(telemetry) { telemetry.frames }
            if (now > before) return@postDelayed
            Log.w(TAG, "lente ${lens.routeKey} sem quadros; voltando à lente automática")
            failedPhysicalRoutes.add(lens.routeKey)
            pendingZoomOnLens = equivalent(zoomOnLens)
            currentLensId = defaultLensId
            listener.onMessage("Esta lente não respondeu; a câmera escolhe a lente automaticamente")
            bind()
        }, PINNED_STALL_MS)
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
        val future = cam.cameraControl.setZoomRatio(cameraZoom(value))
        val generation = zoomGate.generation
        future.addListener({
            if (camera === cam) zoomGate.completed(generation)?.let(::sendZoom)
        }, main)
    }

    /** Nome da lente física ativa, quando a câmera informa (LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID). */
    fun lensLabel(physicalId: String?): String? = physicalId?.let { physicalLabels[it] ?: "Lente $it" }

    // ------------------------------------------------------------------ foco e exposição

    var subjectTracking = false; private set
    var trackingEpoch = 0L; private set
    private var trackedRegion: android.hardware.camera2.params.MeteringRectangle? = null
    private var trackedAfMode: Int? = null
    private var trackedAe = false
    private var trackingBusy = false
    private var trackingBusySince = 0L
    private val trackingGate = com.novacamera.s24.core.TrackingMeterGate()
    val trackingControls get() = listOfNotNull(if (trackedAfMode != null) "AF" else null,
        if (trackedAe && !manual.manualExposure) "AE" else null).joinToString("/")

    private fun invalidateSubjectTracking() {
        if (subjectTracking) userPointActive = false
        subjectTracking = false; trackedRegion = null; trackingBusy = false
        trackedAfMode = null; trackedAe = false; trackingGate.reset(); trackingEpoch++
    }

    fun beginSubjectTracking(): Boolean {
        val cam = camera ?: return false
        if (paused || released || (mode != CaptureMode.VIDEO && mode != CaptureMode.VIDEO_PRO && mode != CaptureMode.RETRATO) ||
            activeExtension!=ExtensionMode.NONE || videoBindingIssue != null || (mode.isPro && manual.focusDiopters != null)) return false
        val info = Camera2CameraInfo.from(cam.cameraInfo)
        val afModes = info.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        val afRegions = info.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
        val aeRegions = info.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0
        val preferredAf=if(mode.isVideo) listOf(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            else listOf(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        val af = if (afRegions > 0) preferredAf.firstOrNull { it in afModes } else null
        if (af == null && (aeRegions == 0 || manual.manualExposure)) return false
        resetFocusState()
        subjectTracking = true; userPointActive = true; zoomAtTap = zoomTarget
        trackedAfMode = af; trackedAe = aeRegions > 0
        backlightBoost = 0
        cam.cameraControl.setExposureCompensationIndex(appliedEv())
        cam.cameraControl.cancelFocusAndMetering()
        applyInterop()
        return true
    }

    /** Atualiza regiões; AF continua no modo contínuo, sem uma fila de disparos AF_TRIGGER. */
    fun meterTrackedSubject(x: Float, y: Float, side: Float): Boolean {
        val cam = camera ?: return false
        if (!subjectTracking || paused || released) return false
        if (trackingBusy) return SystemClock.elapsedRealtime() - trackingBusySince < 1500
        val transform = previewView.sensorToViewTransform ?: return false
        val sensor = Camera2CameraInfo.from(cam.cameraInfo).getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return false
        val region = TrackingMetering.region(transform, sensor, previewView.width, previewView.height, x, y, side) ?: return false
        if (!trackingGate.accept(x, y, SystemClock.elapsedRealtime())) return true
        selectCinematicSubject(x*previewView.width,y*previewView.height)
        tapOnView=x to y
        trackedRegion = region
        trackingBusy = true
        trackingBusySince = SystemClock.elapsedRealtime()
        val token = trackingEpoch
        val future = applyInterop() ?: run { trackingBusy = false; return false }
        future.addListener({
            if (token != trackingEpoch || !subjectTracking) return@addListener
            trackingBusy = false
            if (runCatching { future.get() }.isFailure) {
                endSubjectTracking()
                listener.onMessage("A câmera recusou a região do assunto; acompanhamento encerrado")
            }
        }, main)
        return true
    }

    fun endSubjectTracking() {
        if (!subjectTracking) return
        cinematicTap=null
        resetFocusState()
        camera?.cameraControl?.cancelFocusAndMetering()
        applyInterop(); applyVideoMetering()
    }

    private fun resetFocusState() {
        invalidateSubjectTracking()
        focusToken++
        userPointActive = false
        aeLocked = false
        nativeMeterPoint = null
        userPoint = null
        afAtPoint = false
    }

    /**
     * Toque: foco + medição no ponto. Depois de [NovaSettings.tapFocusHoldSeconds] o AF volta ao contínuo
     * (acompanha aproximação/afastamento), mantendo a medição de exposição no ponto para não "saltar".
     * [lock] = toque prolongado: AF fica travado e AE/AWB são bloqueados via Camera2.
     */
    /** Último toque na prévia (0..1 da área visível), para o Retrato escolher o assunto do desfoque. */
    private var tapOnView: Pair<Float, Float>? = null

    /**
     * Ponto tocado nas coordenadas da foto final. A prévia mostra exatamente a área salva (ViewPort);
     * só com o aparelho em pé (a foto sai na mesma orientação da tela). Frontal sem espelho: x invertido.
     */
    private fun subjectPointForPhoto(mirror: Boolean): Pair<Float, Float>? {
        val t = tapOnView?.takeIf { userPointActive } ?: return null
        if (targetRotation != Surface.ROTATION_0) return null
        return (if (isFront && !mirror) 1f - t.first else t.first) to t.second
    }

    fun selectCinematicSubject(x:Float,y:Float) {
        val inverse=android.graphics.Matrix()
        val transform=previewView.sensorToViewTransform ?: return
        if(transform.invert(inverse)) {
            val p=floatArrayOf(x,y); inverse.mapPoints(p); cinematicTap=p[0] to p[1]
        }
    }

    fun focusAt(x: Float, y: Float, lock: Boolean) {
        endSubjectTracking()
        selectCinematicSubject(x,y)
        val cam = camera ?: return
        val token = ++focusToken
        userPointActive = true
        if (previewView.width > 0 && previewView.height > 0)
            tapOnView = (x / previewView.width).coerceIn(0f, 1f) to (y / previewView.height).coerceIn(0f, 1f)
        aeLocked = false
        applyInterop()
        val point = previewView.meteringPointFactory.createPoint(x, y)
        // Transformação pública: respeita rotação, recorte e escala da prévia.
        // MeteringPoint.x/y são API interna do CameraX e não devem ser lidos aqui.
        nativeMeterPoint = runCatching {
            val inverse=android.graphics.Matrix()
            val transform=previewView.sensorToViewTransform ?: return@runCatching null
            if(!transform.invert(inverse)) return@runCatching null
            val array=Camera2CameraInfo.from(cam.cameraInfo).getCameraCharacteristic(
                android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return@runCatching null
            val mapped=floatArrayOf(x,y);inverse.mapPoints(mapped)
            ((mapped[0]-array.left)/array.width()).coerceIn(0f,1f) to
                ((mapped[1]-array.top)/array.height()).coerceIn(0f,1f)
        }.getOrNull()
        val manualFocus = mode.isPro && manual.focusDiopters != null
        val flags = if (manualFocus) FocusMeteringAction.FLAG_AE
                    else FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        val action = FocusMeteringAction.Builder(point, flags).disableAutoCancel().build()
        val chosen = if (cam.cameraInfo.isFocusMeteringSupported(action)) action else {
            val exposureOnly = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE).disableAutoCancel().build()
            if (!cam.cameraInfo.isFocusMeteringSupported(exposureOnly)) {
                userPointActive = false
                applyInterop()
                listener.onFocusResult(false, false)
                return
            }
            exposureOnly
        }
        userPoint = point
        userPointFlags = if (chosen === action) flags else FocusMeteringAction.FLAG_AE
        zoomAtTap = zoomTarget
        afAtPoint = false
        val future = cam.cameraControl.startFocusAndMetering(chosen)
        future.addListener({
            if (token != focusToken || camera !== cam) return@addListener
            val result = runCatching { future.get() }.getOrNull()
            if (result == null) {
                userPointActive = false
                applyInterop()
                listener.onFocusResult(false, false)
                return@addListener
            }
            val ok = result.isFocusSuccessful
            afAtPoint = userPointFlags and FocusMeteringAction.FLAG_AF != 0
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
            // Rajada em andamento: o foco não pode mudar entre os quadros; tenta de novo depois.
            if (capturing) { scheduleFocusRelease(token, point); return@postDelayed }
            // Estágio 1: AF volta ao contínuo; AE segue medido no ponto escolhido. Ao disparar uma
            // rajada, o foco é refeito no ponto (ver focusBeforeBurst).
            afAtPoint = false
            cam.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE).disableAutoCancel().build())
            handler.postDelayed({
                if (token != focusToken || aeLocked || evIndex != 0 || capturing) return@postDelayed
                // Estágio 2: sem ajuste manual de EV, retorna ao automático completo (inclui prioridade a rostos).
                userPointActive = false
                camera?.cameraControl?.cancelFocusAndMetering()
                applyInterop()
                applyVideoMetering()
            }, AUTO_RETURN_MS)
        }, hold * 1000L)
    }

    /**
     * Zoom mudou depois do toque: a região medida (fixa no sensor) já não cobre o mesmo objeto — na
     * tele pode até sair do quadro, e o AF passa a focar o que estiver lá. Volta ao foco automático,
     * como a câmera nativa. Toque prolongado (bloqueio) é escolha explícita e fica.
     */
    private fun dropPointIfReframed(target: Float) {
        if (!userPointActive || aeLocked || capturing) return
        if (!com.novacamera.s24.core.FocusPolicy.zoomInvalidatesPoint(zoomAtTap, target)) return
        resetFocusState()
        camera?.cameraControl?.cancelFocusAndMetering()
        applyInterop()
        applyVideoMetering()
        listener.onFocusCleared()
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
        if (clamped == evIndex && backlightBoost == 0) return
        // EV escolhido pelo usuário manda: a contraluz automática sai.
        evIndex = clamped; backlightBoost = 0
        cam.cameraControl.setExposureCompensationIndex(clamped)
        listener.onExposureIndex(clamped)
    }

    /**
     * Contraluz automática ([com.novacamera.s24.core.BacklightMeter]): [ev] ≥ 0 sobre o EV 0. Só na
     * Foto, Retrato e Vídeo parado, sem EV/AE travado/toque do usuário. Devolve o EV aplicado.
     */
    fun setBacklightEv(ev: Float): Float {
        val cam = camera ?: return 0f
        val ex = cam.cameraInfo.exposureState
        val allowed = ex.isExposureCompensationSupported && !mode.isPro && mode != CaptureMode.NOITE &&
            mode != CaptureMode.TIMELAPSE && mode != CaptureMode.CAMERA_LENTA && activeExtension == ExtensionMode.NONE &&
            !aeLocked && evIndex == 0 && !userPointActive && !isRecording && nightOverride == null && !capturing
        val stepEv = ex.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: return 0f
        val wanted = if (allowed) Math.round(ev / stepEv).coerceIn(0, ex.exposureCompensationRange.upper) else 0
        if (wanted != backlightBoost && (allowed || !capturing)) {
            backlightBoost = wanted
            cam.cameraControl.setExposureCompensationIndex(appliedEv())
        }
        return backlightBoost * stepEv
    }

    val exposureIndex get() = evIndex

    fun setManual(m: ManualControls) {
        if (m.focusDiopters != null) endSubjectTracking()
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

    private fun applyInterop(): com.google.common.util.concurrent.ListenableFuture<Void?>? {
        val cam = camera ?: return null
        if (activeExtension != ExtensionMode.NONE) return null
        val c = caps ?: return null
        val b = CaptureRequestOptions.Builder()
        val m = if (mode.isPro) manual else ManualControls()
        val night = nightOverride?.takeIf { c.manualSensor && !mode.isPro }
        if (night != null) {
            // Noturno: tempo longo + ISO baixo nos quadros da rajada (mesma luz, menos ruído).
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, night.iso)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, night.exposureNs)
        } else if (m.manualExposure && c.manualSensor) {
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
        if (subjectTracking) {
            trackedAfMode?.let { b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, it) }
            trackedRegion?.let { region ->
                if (trackedAfMode != null) b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
                if (trackedAe && !m.manualExposure) b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
            }
        }
        if (m.awbMode != null && m.awbMode in c.awbModes) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, m.awbMode)
        }
        val normalVideoMode = mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO
        val facePriority = settings.facePriority && c.facePriorityScene && !userPointActive && !normalVideoMode &&
            !mode.isPro && !m.manualExposure && night == null
        if (facePriority) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
            b.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_FACE_PRIORITY)
        }
        if (aeLocked && c.aeLockAvailable && !m.manualExposure && night == null) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
            if (settings.lockWhiteBalanceWithAeAf && c.awbLockAvailable && m.awbMode == null) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
            }
        }
        // No vídeo, CameraX coordena OIS/EIS e os perfis de FPS; não sobrescrever o template.
        // Nas fotos, mantemos a OIS explícita existente.
        if (c.opticalStabilization && !normalVideoMode) {
            b.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
        }
        val manualActive = m.manualExposure && c.manualSensor && night == null
        return runCatching {
            val future = Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())
            if (manualActive) watchForStall()
            future
        }.onFailure {
            Log.w(TAG, "Interop Camera2 recusado", it)
            if (manualActive) revertManual("A câmera recusou esta combinação manual")
        }.getOrNull()
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
    @Volatile private var previewOptics: Pair<String?, Float?> = null to null

    private fun progressCallback(generation:Long) = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session:CameraCaptureSession,request:CaptureRequest,result:TotalCaptureResult) {
            if (generation!=watchGeneration || !previewProgress.frame(generation)) return
            statsCallback.onCaptureCompleted(session,request,result)
        }
    }

    private val statsCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val opticalResult = currentLens?.physicalCameraId?.let { result.physicalCameraResults[it] } ?: result
            val zoom = if (Build.VERSION.SDK_INT >= 30) result.get(CaptureResult.CONTROL_ZOOM_RATIO) else null
            val physical = runCatching { result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) }.getOrNull()
            val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: 0L
            val lensChanged = synchronized(telemetry) { telemetry.frame(ts, zoom, physical, SystemClock.uptimeMillis()) }
            if (lensChanged) handler.post { listener.onActiveLens(lensLabel(currentLens?.physicalCameraId ?: physical)) }
            // ISO/exposição de uma lente física são opcionais; não interrompem o sinal de vida.
            val iso = opticalResult.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
            val exp = opticalResult.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
            val focus = opticalResult.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f
            val stats = AutoStats(iso, exp, focus)
            latestStats = stats
            previewOptics = (currentLens?.physicalCameraId ?: physical) to opticalResult.get(CaptureResult.LENS_FOCAL_LENGTH)
            if(!isFront && !referenceMeasured && currentLensId==defaultLensId && currentLens?.physicalCameraId==null && zoom!=null && kotlin.math.abs(zoom-1f)<0.02f) {
                val realSize=runCatching { activity.getSystemService(android.hardware.camera2.CameraManager::class.java)
                    .getCameraCharacteristics(physical ?: caps?.cameraId ?: error("Sem câmera"))
                    .get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) }.getOrNull()
                val realEq=LensCatalog.equivalentFocal(opticalResult.get(CaptureResult.LENS_FOCAL_LENGTH),realSize)
                if(realEq!=null){
                    referenceMeasured=true
                    handler.post {
                        referenceEquivalentMm=realEq
                        val p=provider
                        if(p!=null && !isFront && !released){
                            val infos=p.availableCameraInfos.filter { it.lensFacing==CameraSelector.LENS_FACING_BACK }
                            lenses=LensCatalog.lenses(infos,defaultInfo(false),realEq)
                            currentLens=lenses.firstOrNull { it.routeKey==currentLensId }
                            camera?.let { physicalLabels=LensCatalog.physicalLabels(it.cameraInfo,realEq) }
                        }
                    }
                }
            }
            val now = System.currentTimeMillis()
            if (now - lastSensorReportMs >= 500L) {
                lastSensorReportMs = now
                sensorReport = "Prévia (não metadados da foto): saída física vinculada ${currentLens?.physicalCameraId}, câmera ${caps?.cameraId}, físico ${physical ?: "não informado"}, " +
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
        if(capturing)return listener.onMessage("Concluindo a aquisição da foto anterior")
        if(nativeRun!=null)return
        if(nativePhotoSelected) return captureNativePhoto()
        val ic = imageCapture ?: return listener.onMessage("A câmera ainda não está pronta")
        ic.targetRotation = targetRotation
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "NOVA_${timestamp()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Nova Camera")
        }
        val shot = ShotTimer(ic)
        processingKind()?.let { return captureForProcessing(ic, it, shot) }
        if (isFront && settings.mirrorFrontPhoto) return captureMirrored(ic, values, shot)
        if (state?.rawActive == true) {
            return if (settings.collectBursts) captureRawBurst(ic, RAW_BURST_FRAMES) else captureRawPlusJpeg(ic, values)
        }
        val metadata = ImageCapture.Metadata()
        val output = ImageCapture.OutputFileOptions.Builder(
            activity.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).setMetadata(metadata).build()
        ic.takePicture(output, main, object : ImageCapture.OnImageSavedCallback {
            override fun onCaptureStarted() { shot.started(); listener.onCaptureStarted() }
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                shot.done(null)
                listener.onPhotoSaved(result.savedUri)
                result.savedUri?.let(::enhanceIfEnabled)
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Falha na foto", exception)
                listener.onPhotoSaved(null)
                listener.onMessage("A foto não foi salva (erro ${exception.imageCaptureError})")
            }
        })
    }

    /** Um único JPEG do sensor: CameraX volta após a sessão nativa, sem cópia extra na galeria. */
    private fun captureNativePhoto() {
        val target=nativeTarget ?: return
        val cam=camera ?: return
        if(!ProcessingQueue.canAccept())return listener.onMessage("Aguarde: processando as fotos anteriores")
        if((heat?.thermalStatus ?: 0)>=ThermalPolicy.SEVERE)return listener.onMessage("Aguarde o aparelho esfriar para alta resolução")
        val id=Camera2CameraInfo.from(cam.cameraInfo).cameraId
        val c=activity.getSystemService(android.hardware.camera2.CameraManager::class.java).getCameraCharacteristics(id)
        val sensor=c.get(android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val display=when(targetRotation){Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0}
        val r=NativePhotoCapture.Request(id,currentLens?.physicalCameraId,target,cameraZoom(zoomOnLens),(sensor-display+360)%360,appliedEv(),
            point=if(userPointActive)nativeMeterPoint else null,flashMode=imageCapture?.flashMode ?: ImageCapture.FLASH_MODE_OFF,
            locked=if(aeLocked)latestStats else null)
        val runner=NativePhotoCapture(activity);nativeRun=runner
        listener.onCaptureStarted()
        provider?.unbindAll();camera=null
        runner.capture(r) { bytes, captureResult, error ->
            if(released)return@capture
            if(bytes==null) handler.post {
                nativeRun=null
                if(!paused&&!released){
                    settings.setPhotoPixels(currentLensKey(),null);bind()
                    Log.w(TAG, "alta resolução recusada: ${error ?: "sessão indisponível"}")
                    listener.onMessage("${target.label} indisponível agora; capturando na resolução padrão")
                    if(imageCapture!=null)capturePhoto() else listener.onPhotoSaved(null)
                }
            } else io.execute {
                val physical=r.physicalId ?: captureResult?.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
                val sensorResult=physical?.let { captureResult?.physicalCameraResults?.get(it) } ?: captureResult
                val sensorSize=runCatching { activity.getSystemService(android.hardware.camera2.CameraManager::class.java)
                    .getCameraCharacteristics(physical ?: id).get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) }.getOrNull()
                val facts=com.novacamera.s24.core.imaging.ImagingFacts("${Build.MANUFACTURER}/${Build.MODEL}",id,physical,
                    sensorResult?.get(CaptureResult.LENS_FOCAL_LENGTH),sensorSize?.width,sensorSize?.height,
                    sensorResult?.get(CaptureResult.SENSOR_SENSITIVITY),sensorResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                    physical!=null && (r.physicalId==null || captureResult?.physicalCameraResults?.containsKey(physical)==true))
                val uri=runCatching { com.novacamera.s24.processing.MediaWriter.insertBytes(activity.contentResolver,bytes,
                    "NOVA_${timestamp()}.jpg",com.novacamera.s24.processing.MediaWriter.GALLERY) }.getOrNull()
                handler.post {
                    nativeRun=null
                    if(!paused&&!released)bind()
                    listener.onPhotoSaved(uri)
                    if(uri!=null)enhanceIfEnabled(uri,facts) else listener.onMessage("A foto em alta resolução não foi salva")
                }
            }
        }
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
                listener.onMessage("A foto RAW não foi salva (erro ${exception.imageCaptureError})")
            }
        })
    }

    /**
     * Testes de super-resolução: os quadros da rajada exatamente como chegaram da câmera (JPEG com
     * EXIF), numa pasta por disparo. Só com a opção de testes ligada.
     */
    private fun saveBurstFrames(tag: String, frames: List<CapturedFrame>) = io.execute {
        val folder = "${com.novacamera.s24.processing.MediaWriter.GALLERY}/Rajadas/${tag}_${timestamp()}"
        frames.forEachIndexed { i, f ->
            com.novacamera.s24.processing.MediaWriter.insertBytes(activity.contentResolver, f.jpeg, "q${i + 1}_ev${f.evIndex}.jpg", folder)
        }
    }

    /**
     * Pro com RAW e a opção de testes: [count] pares RAW (DNG) + JPEG seguidos, sem mexer em nada
     * entre eles. O pequeno tremido da mão entre os quadros é justamente o que a super-resolução usa.
     */
    private fun captureRawBurst(ic: ImageCapture, count: Int) {
        val folder = "${com.novacamera.s24.processing.MediaWriter.GALLERY}/Rajadas/raw_${timestamp()}"
        val resolver = activity.contentResolver
        fun values(name: String, mime: String) = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
        }
        var lastJpeg: android.net.Uri? = null
        fun take(i: Int) {
            if (i == count || released || paused) { listener.onPhotoSaved(lastJpeg); return }
            val raw = ImageCapture.OutputFileOptions.Builder(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values("q${i + 1}.dng", "image/x-adobe-dng")).build()
            val jpeg = ImageCapture.OutputFileOptions.Builder(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values("q${i + 1}.jpg", "image/jpeg")).build()
            var pending = 2
            ic.takePicture(raw, jpeg, main, object : ImageCapture.OnImageSavedCallback {
                override fun onCaptureStarted() { if (i == 0) listener.onCaptureStarted() }
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val uri = result.savedUri
                    if (uri != null && runCatching { resolver.getType(uri) }.getOrNull()?.contains("jpeg") == true) lastJpeg = uri
                    if (--pending == 0) { listener.onBurstProgress(i + 1, count); take(i + 1) }
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Falha no quadro RAW ${i + 1}", exception)
                    pending = -1
                    listener.onMessage("Rajada RAW parou no quadro ${i + 1} de $count (erro ${exception.imageCaptureError})")
                    listener.onPhotoSaved(lastJpeg)
                }
            })
        }
        take(0)
    }

    /** Selfie como visualizada: captura em memória e espelha os pixels em segundo plano. */
    private fun captureMirrored(ic: ImageCapture, values: ContentValues, shot: ShotTimer) {
        ic.takePicture(io, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureStarted() { shot.started(); handler.post { listener.onCaptureStarted() } }
            override fun onCaptureSuccess(image: ImageProxy) {
                shot.done(image.imageInfo.timestamp)
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
                handler.post { listener.onPhotoSaved(null); listener.onMessage("A selfie não foi salva (erro ${exception.imageCaptureError})") }
            }
        })
    }

    // ------------------------------------------------------------------ processamento Nova

    /**
     * Captura em memória os quadros que o processamento precisa (rajada para Noturno, bracketing
     * de exposição para HDR, um quadro para Retrato/Food) e entrega à fila em segundo plano.
     * O obturador fica livre assim que os quadros chegam; o processamento não bloqueia a câmera.
     */
    private fun captureForProcessing(ic: ImageCapture, initialKind: JobKind, shot: ShotTimer? = null) {
        val trace=com.novacamera.s24.core.PhotoTrace("${initialKind.label} · ${equivalent(zoomOnLens)}×",
            { android.os.SystemClock.elapsedRealtimeNanos() },shot?.tap)
        var kind=initialKind
        if (!ProcessingQueue.canAccept()) return listener.onMessage("Aguarde: processando as fotos anteriores")
        val cam = camera ?: return
        val h = heat
        var captureFacts = previewImagingFacts()
        val exposure = cam.cameraInfo.exposureState
        var captureNote: String? = null
        var nightPlan: com.novacamera.s24.core.NightExposure.Plan? = null
        var steps: List<Int> = when (kind) {
            JobKind.NIGHT -> {
                val max = when {
                    h != null && h.thermalStatus >= ThermalPolicy.SEVERE -> 1
                    h != null && (h.powerSave || h.thermalStatus >= ThermalPolicy.MODERATE) -> 3
                    else -> ModeResolver.NIGHT_FRAMES
                }
                val st = latestStats
                val c = caps
                val stIso = st?.iso; val stExp = st?.exposureNs
                if (max > 1 && c != null && c.manualSensor && stIso != null && stExp != null && activeExtension == ExtensionMode.NONE) {
                    val gyro = motion.rms()
                    val limit = com.novacamera.s24.core.NightExposure.maxFrameNs(gyro, equivalent(zoomOnLens), c.opticalStabilization)
                    nightPlan = com.novacamera.s24.core.NightExposure.plan(stIso, stExp,
                        c.isoRange?.lower ?: 50, c.isoRange?.upper ?: 3200, c.exposureRangeNs?.upper ?: limit,
                        limit, max, com.novacamera.s24.core.NightExposure.steady(gyro))
                }
                val plan = nightPlan
                if (plan != null) {
                    captureNote = "Noturno: ${plan.label}"
                    List(plan.frames) { 0 }
                } else {
                    val (n, note) = CaptureDecision.nightFrames(stIso, stExp, max)
                    captureNote = note
                    List(n) { 0 }
                }
            }
            JobKind.HDR -> if (exposure.isExposureCompensationSupported && aeAdjustable())
                ModeResolver.hdrBracket(exposure.exposureCompensationStep.numerator, exposure.exposureCompensationStep.denominator,
                    exposure.exposureCompensationRange.lower - appliedEv(), exposure.exposureCompensationRange.upper - appliedEv())
                else listOf(0)
            else -> listOf(0)
        }
        // Super-resolução: zoom digital sobre uma tele física (ex.: 10× na lente 5×). A rajada é feita na
        // lente sem recorte e a foto final é montada com as amostras de todos os quadros.
        val lensNow = currentLens
        val srFactor = if (settings.superRes && !isFront && activeExtension == ExtensionMode.NONE &&
            (kind == JobKind.PHOTO || kind == JobKind.HDR || kind == JobKind.ZOOM) &&
            lensNow?.physicalCameraId != null && lensNow.intrinsic >= 2.5f && zoomOnLens >= SR_MIN_DIGITAL) zoomOnLens else 1f
        if (srFactor > 1f) {
            kind = JobKind.ZOOM
            steps = List(SR_FRAMES) { 0 }
            captureNote = "Super-resolução: $SR_FRAMES quadros na lente sem recorte"
        }
        // Alta resolução na tele (opção): no zoom óptico da tele (3×/5×, sem recorte digital), Foto ou
        // Retrato, a foto sai 2× maior montada com 8 quadros — detalhe real na pinça.
        val outScale = if (srFactor <= 1f && settings.teleHighRes && !isFront && activeExtension == ExtensionMode.NONE &&
            (kind == JobKind.PHOTO || kind == JobKind.HDR || kind == JobKind.ZOOM || kind == JobKind.PORTRAIT) &&
            equivalent(zoomOnLens) >= TELE_HIGH_RES_FROM) 2f else 1f
        if (outScale > 1f) {
            if (kind != JobKind.PORTRAIT) kind = JobKind.ZOOM
            steps = List(SR_FRAMES) { 0 }
            captureNote = "Alta resolução: $SR_FRAMES quadros"
            listener.onMessage("Segure firme · alta resolução")
        }
        val mirror = isFront && settings.mirrorFrontPhoto
        val frames = ArrayList<CapturedFrame>()
        val baseEv = appliedEv()
        trace.mark("plano ${steps.size} quadros/outScale$outScale/SR$srFactor")
        capturing = true
        trace.publish("aquisição em andamento")
        ic.targetRotation = targetRotation

        fun submit() {
            trace.mark("rajada concluída ${frames.size}/${steps.size}");trace.publish("aquisição concluída, processamento pendente")
            capturing = false
            if (nightOverride != null) { nightOverride = null; applyInterop() }
            if (srFactor > 1f && camera === cam) cam.cameraControl.setZoomRatio(cameraZoom(zoomOnLens))
            releaseBurstFocus()
            if (baseEv != appliedEv() || steps.any { it != 0 }) cam.cameraControl.setExposureCompensationIndex(baseEv)
            if (frames.isEmpty()) { listener.onPhotoSaved(null); listener.onMessage("A foto não foi capturada"); return }
            if (settings.collectBursts && frames.size > 1) saveBurstFrames(kind.name.lowercase(Locale.US), frames.toList())
            // Ordem do HDR: a referência (EV 0) é sempre o índice 0.
            ProcessingQueue.submit(activity, Job(kind, frames.sortedBy { if (it.evIndex == baseEv) 0 else 1 }, mirror,
                zoom = equivalent(zoomOnLens), srFactor = srFactor, outScale = outScale, signature = signatureStyle(), captureNote = captureNote, imagingFacts=captureFacts,
                portraitBlur = settings.portraitBlur, style = currentStyle(),
                subjectPoint = if (kind == JobKind.PORTRAIT) subjectPointForPhoto(mirror) else null, trace=trace))
            listener.onProcessingQueued(kind)
        }

        fun shoot(i: Int) {
            if (i == steps.size || camera !== cam) return submit()
            val take = {
                trace.mark("solicitação quadro${i+1}")
                ic.takePicture(io, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureStarted() { trace.mark("início quadro${i+1}"); if (i == 0) { shot?.started(); handler.post { listener.onCaptureStarted() } } }
                    override fun onCaptureSuccess(image: ImageProxy) {
                        if (i == 0) shot?.done(image.imageInfo.timestamp)
                        var cameraImageClosed=false
                        try {
                            trace.mark("entrega quadro${i+1}")
                            cameraImageClosed=true
                            val frame=com.novacamera.s24.core.FrameSnapshot.readAndClose(image) {
                                trace.sensor(image.imageInfo.timestamp)
                                val rotation=image.imageInfo.rotationDegrees
                                val crop=android.graphics.Rect(image.cropRect)
                                val buffer=image.planes[0].buffer
                                CapturedFrame(ByteArray(buffer.remaining()).also { buffer.get(it) },rotation,crop,baseEv+steps[i])
                            }
                            trace.mark("cópia/liberação quadro${i+1} (${frame.jpeg.size}bytes)")
                            trace.publish("aquisição ${i+1}/${steps.size}")
                            val bytes=frame.jpeg
                            frames += frame
                            if(i==0) {
                                val exif=androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                                captureFacts=captureFacts.copy(
                                    iso=exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,0).takeIf { it>0 },
                                    exposureNs=exif.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME,0.0)
                                        .takeIf { it.isFinite() && it>0 && it<60 }?.let { (it*1_000_000_000).toLong() })
                            }
                            if(i==0 && initialKind==JobKind.PHOTO && srFactor <= 1f && outScale <= 1f) {
                                val (clip,deep)=frameStats(bytes)
                                val st=latestStats
                                // Testes de super-resolução: rajada da tele também com luz boa, e mais longa.
                                val collectTele = settings.collectBursts && equivalent(zoomOnLens) >= com.novacamera.s24.core.AutoPhotoPolicy.ZOOM_FROM
                                when(if (collectTele) com.novacamera.s24.core.AutoPhotoPolicy.Path.ZOOM else com.novacamera.s24.core.AutoPhotoPolicy.choose(captureFacts.iso ?: st?.iso,captureFacts.exposureNs ?: st?.exposureNs,clip,deep,
                                    h!=null && (h.powerSave || h.thermalStatus>=ThermalPolicy.MODERATE), equivalent(zoomOnLens),
                                    allowFusion = settings.hdr != HdrOption.OFF)) {
                                    com.novacamera.s24.core.AutoPhotoPolicy.Path.ZOOM -> {
                                        kind=JobKind.ZOOM
                                        val n = if (collectTele) COLLECT_TELE_FRAMES else com.novacamera.s24.core.AutoPhotoPolicy.ZOOM_FRAMES
                                        steps=List(n){0}
                                        captureNote=if (collectTele) "Tele (coleta de testes): $n quadros alinhados" else "Tele com pouca luz: $n quadros alinhados"
                                    }
                                    com.novacamera.s24.core.AutoPhotoPolicy.Path.MERGE -> {
                                        val n = com.novacamera.s24.core.AutoPhotoPolicy.MERGE_FRAMES
                                        steps=List(n){0}
                                        captureNote="Luz média: $n quadros alinhados (menos ruído)"
                                    }
                                    com.novacamera.s24.core.AutoPhotoPolicy.Path.NIGHT -> {
                                        kind=JobKind.NIGHT
                                        val (n,note)=CaptureDecision.nightFrames(captureFacts.iso ?: st?.iso,captureFacts.exposureNs ?: st?.exposureNs,3)
                                        steps=List(n){0};captureNote=note ?: "Pouca luz: redução de ruído adaptativa"
                                    }
                                    com.novacamera.s24.core.AutoPhotoPolicy.Path.HDR -> if(exposure.isExposureCompensationSupported && aeAdjustable()) {
                                        kind=JobKind.HDR
                                        steps=ModeResolver.hdrBracket(exposure.exposureCompensationStep.numerator,exposure.exposureCompensationStep.denominator,
                                            exposure.exposureCompensationRange.lower-baseEv,exposure.exposureCompensationRange.upper-baseEv)
                                    }
                                    else -> captureNote="Foto bem exposta: um quadro"
                                }
                            }
                            // HDR: depois da referência, decide se os quadros extras valem a pena.
                            if (i == 0 && kind == JobKind.HDR && steps.size > 1) {
                                val (clip, deep) = frameStats(bytes)
                                val (keep, note) = CaptureDecision.hdrSteps(steps, clip, deep)
                                steps = keep; captureNote = note
                                // Pouca luz (ISO alto) ou sem faixa dinâmica a recuperar: exposições diferentes não
                                // ajudam — a escura é só ruído e a clara treme. Quadros iguais somados, como o HDR+
                                // (frasco 5× com HDR forçado, ISO 2056: saía escuro e com ruído).
                                val iso = captureFacts.iso ?: latestStats?.iso
                                val sameExposure = com.novacamera.s24.core.AutoPhotoPolicy.hdrAsMerge(iso, keep.size)
                                if (sameExposure > 1) {
                                    val tele = equivalent(zoomOnLens) >= com.novacamera.s24.core.AutoPhotoPolicy.ZOOM_FROM
                                    kind = if (tele) JobKind.ZOOM else JobKind.PHOTO
                                    steps = List(sameExposure) { 0 }
                                    captureNote = if (keep.size > 1) "Pouca luz: $sameExposure quadros iguais em vez de exposições diferentes"
                                        else "Sem faixa dinâmica a recuperar: $sameExposure quadros iguais (menos ruído)"
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "quadro $i inválido", e)
                        } finally { if(!cameraImageClosed)image.close() }
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
        fun startBurst() {
            if (camera !== cam) return submit()
            val plan = nightPlan
            if (plan != null) {
                nightOverride = plan
                val total = plan.exposureNs * plan.frames
                if (total >= 600_000_000L) listener.onMessage("Segure firme · ${String.format(Locale.US, "%.1f", total / 1e9).replace('.', ',')} s")
                val applied = applyInterop()
                if (applied != null) applied.addListener({ shoot(0) }, main) else { nightOverride = null; shoot(0) }
            } else shoot(0)
        }
        val zoomOut = {
            if (srFactor > 1f) {
                // Lente sem recorte durante a rajada; um instante para o quadro novo chegar ao sensor.
                // O foco já foi feito no recorte (no modo AF de disparo a lente não se move com o zoom).
                listener.onMessage("Segure firme · super-resolução")
                cam.cameraControl.setZoomRatio(cameraZoom(1f)).addListener({ handler.postDelayed({ startBurst() }, SR_SETTLE_MS) }, main)
            } else startBurst()
        }
        // Foco de verdade antes da rajada: todos os quadros saem com o mesmo foco, no ponto tocado (se o
        // AF já tinha voltado ao contínuo) ou no centro (Noturno, super-resolução, tele multiquadro —
        // o AF contínuo "caça" no escuro e na tele, e um quadro fora de foco borra a fusão).
        val plan = com.novacamera.s24.core.FocusPolicy.beforeBurst(
            night = kind == JobKind.NIGHT, frames = steps.size, equivalentZoom = equivalent(zoomOnLens), superRes = srFactor > 1f,
            userPoint = userPointActive, afAtPoint = afAtPoint, locked = aeLocked,
            manualFocus = mode.isPro && manual.focusDiopters != null, vendorExtension = activeExtension != ExtensionMode.NONE)
        trace.mark("AF solicitado ${plan.target} limite${plan.waitMs}ms")
        focusBeforeBurst(cam, plan,trace) { trace.mark("AF liberou aquisição"); zoomOut() }
    }

    private var burstFocusHeld = false

    private fun focusBeforeBurst(cam: Camera, plan: com.novacamera.s24.core.FocusPolicy.BeforeBurst, trace:com.novacamera.s24.core.PhotoTrace, then: () -> Unit) {
        val action = when (plan.target) {
            com.novacamera.s24.core.FocusPolicy.Target.NONE -> null
            com.novacamera.s24.core.FocusPolicy.Target.POINT -> userPoint?.let {
                FocusMeteringAction.Builder(it, userPointFlags or FocusMeteringAction.FLAG_AF).disableAutoCancel().build() }
            com.novacamera.s24.core.FocusPolicy.Target.CENTER -> FocusMeteringAction.Builder(
                previewView.meteringPointFactory.createPoint(previewView.width / 2f, previewView.height / 2f),
                FocusMeteringAction.FLAG_AF).disableAutoCancel().build()
        }
        if (action == null || !cam.cameraInfo.isFocusMeteringSupported(action)) { trace.mark("AF dispensado/não suportado");return then() }
        var done = false
        val go = { reason:String -> if (!done) { done = true; trace.mark(reason);then() } }
        // Novo ciclo do ponto: os retornos ao automático agendados pelo toque antigo deixam de valer.
        val token = if (plan.target == com.novacamera.s24.core.FocusPolicy.Target.POINT) ++focusToken else focusToken
        burstFocusHeld = plan.target == com.novacamera.s24.core.FocusPolicy.Target.CENTER
        val future = cam.cameraControl.startFocusAndMetering(action)
        future.addListener({
            if (plan.target == com.novacamera.s24.core.FocusPolicy.Target.POINT && token == focusToken) {
                afAtPoint = true
                // O ponto volta a ficar parado pelo tempo escolhido, como num toque novo.
                userPoint?.let { scheduleFocusRelease(token, it) }
            }
            val result=runCatching { future.get().isFocusSuccessful }.getOrNull()
            go("AF callback: sucesso=$result")
        }, main)
        handler.postDelayed({ go("AF limite de espera") }, plan.waitMs)
    }

    /** Depois da rajada: sem ponto do usuário, AF volta ao contínuo. */
    private fun releaseBurstFocus() {
        if (!burstFocusHeld) return
        burstFocusHeld = false
        if (!userPointActive) camera?.cameraControl?.cancelFocusAndMetering()
    }

    private fun captureModeLabel(m: Int) = when (m) {
        ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG -> "sem atraso (ZSL)"
        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY -> "latência mínima"
        else -> "qualidade máxima"
    }

    /** Mede um disparo a partir do toque (ver [ShutterLog]). */
    private inner class ShotTimer(ic: ImageCapture) {
        val tap = android.os.SystemClock.elapsedRealtimeNanos()
        private val mode = if (activeExtension != ExtensionMode.NONE) "extensão do fabricante" else captureModeLabel(ic.captureMode)
        @Volatile private var start: Long? = null
        fun started() { start = (android.os.SystemClock.elapsedRealtimeNanos() - tap) / 1_000_000L }
        fun done(sensorTimestampNs: Long?) = ShutterLog.add(ShutterLog.Entry(mode, start,
            sensorTimestampNs?.let { ShutterLog.sensorDelayMs(it, tap) }, (android.os.SystemClock.elapsedRealtimeNanos() - tap) / 1_000_000L))
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

    // ------------------------------------------------------------------ prévia do Retrato

    private var portraitEffect: PortraitPreviewEffect? = null
    var cinematicBound = false; private set
    private var commonVideoChosen = false
    var portraitVideoSession=false;private set
    private val portraitProfile get() = portraitVideoSession && settings.cinematicVideo
    private val selectedVideoQuality get() = if(portraitProfile) VideoQualityOption.FHD else settings.videoQuality
    private val selectedVideoFps get() = if(portraitProfile) 30 else settings.videoFps
    private val selectedVideoStabilization get() = if(portraitProfile) false else settings.videoStabilization
    val cinematicRequested get() = com.novacamera.s24.core.CinematicPolicy.enabled(settings.cinematicVideo,portraitVideoSession,commonVideoChosen)
    val cinematicUnavailableReason get() = if (!cinematicBound) {
        if(cinematicRequested) "Cinematográfico indisponível nesta sessão; nenhum vídeo com desfoque será iniciado" else null
    } else com.novacamera.s24.core.CinematicPolicy.unavailableReason(heat?.thermalStatus ?: 0,
            com.novacamera.s24.processing.VideoFinishJob.state.running, portraitEffect?.maskReady==true)
    val cinematicReady get() = com.novacamera.s24.core.CinematicPolicy.canStart(cinematicRequested,cinematicBound,cinematicUnavailableReason==null)
    val portraitVideoThermallyBlocked get() = settings.cinematicVideo && (heat?.thermalStatus ?: 0)>=ThermalPolicy.SEVERE
    val cinematicThermallyBlocked get() = cinematicRequested && (heat?.thermalStatus ?: 0)>=ThermalPolicy.SEVERE
    val cinematicDiagnostic get() = when {
        commonVideoChosen && settings.cinematicVideo -> "cinematográfico dispensado por escolha nesta sessão"
        cinematicBound && cinematicUnavailableReason!=null -> "cinematográfico ligado, máscara suspensa/indisponível"
        cinematicBound -> "cinematográfico com máscara atual"
        else -> "sem cinematográfico"
    }

    /** Escolha explícita: mantém resolução/FPS/EIS e a preferência do efeito para a próxima sessão.
     * Reconfigurar não inicia arquivo. O usuário toca novamente depois que a câmera estiver pronta.
     */
    fun chooseCommonVideo(): Boolean {
        if(released || paused || isRecording || !cinematicBound) return false
        commonVideoChosen=true
        com.novacamera.s24.core.VideoLog.add(activity,"Vídeo comum escolhido pelo usuário nesta sessão; qualidade/FPS preservados")
        listener.onPortraitPreview(null)
        bind()
        return true
    }
    private var cinematicClosing = false
    private var cinematicFailed = false
    @Volatile private var cinematicTap: Pair<Float,Float>? = null
    private var retiredPortraitEffect: PortraitPreviewEffect? = null
    private var portraitAnalysis: ImageAnalysis? = null
    private var portraitAnalyzer: PortraitMaskAnalyzer? = null
    private val portraitExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    /** GL falhou nesta execução do app: sem nova tentativa até reabrir (a foto final não muda). */
    private var portraitPreviewFailed = false

    /** Tom e temperatura ao vivo: a prévia muda na hora e a foto usa a mesma conta. */
    fun setStyle(style: com.novacamera.s24.core.imaging.PhotoStyle) {
        settings.styleTone = style.tone; settings.styleWarmth = style.warmth
        portraitEffect?.style = style
    }

    /** O estilo vale nesta sessão (efeito ativo e modo que grava pelo motor Nova). */
    val styleActive get() = portraitEffect != null

    private fun currentStyle() =
        if (portraitEffect != null) com.novacamera.s24.core.imaging.PhotoStyle(settings.styleTone, settings.styleWarmth)
        else com.novacamera.s24.core.imaging.PhotoStyle()

    /** Intensidade do desfoque (0..1): a prévia muda na hora e a foto usa o mesmo valor. */
    fun setPortraitBlur(v: Float) {
        settings.portraitBlur = v.coerceIn(0f, 1f)
        portraitEffect?.strength = settings.portraitBlur
    }

    private fun stopPortraitAnalysis() {
        val a = portraitAnalysis; val an = portraitAnalyzer
        portraitAnalysis = null; portraitAnalyzer = null
        a?.clearAnalyzer()
        an?.stopAcceptingFrames()
        an?.let { portraitExecutor.execute { it.close() } }
    }

    /**
     * Efeito de pré-visualização desta sessão: estilo ao vivo nos modos de foto do motor Nova (todas
     * as lentes) e, no Retrato, também o desfoque com a máscara. Extensões do fabricante não aceitam
     * efeitos — nelas não há estilo ao vivo (e a foto também não recebe estilo).
     */
    private fun portraitPreviewFor(support: ModeSupport, wide: Boolean, pinned: String?, extension: Int): Pair<PortraitPreviewEffect, ImageAnalysis?>? {
        if (cinematicRequested && !cinematicFailed && videoCapture != null &&
            (mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO) && videoBindingIssue==null) {
            val generation=videoEffectGeneration
            val effect=PortraitPreviewEffect.cinematic(signatureStyle()?.takeIf { settings.signatureVideo }) { e ->
                if(generation==videoEffectGeneration && !released) {
                Log.w(TAG,"Cinematográfico indisponível",e); cinematicFailed=true
                com.novacamera.s24.core.VideoLog.add(activity,"Cinematográfico desligado: ${e.javaClass.simpleName}: ${e.message}")
                listener.onMessage("Cinematográfico falhou; próximo vídeo comum. Original/gravação serão finalizados")
                if(isRecording) { settingsChangedDuringRecording=true; stopRecording() }
                else handler.post { bind() }
                }
            }.apply { strength=settings.portraitBlur; blurEnabled=true }
            val a=ImageAnalysis.Builder().setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(android.util.Size(640,360),ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                .setTargetRotation(targetRotation).build()
            val analyzer=PortraitMaskAnalyzer(activity,effect,{heat},
                { note -> handler.post {
                    if(generation==videoEffectGeneration && cinematicBound && !released && !paused) listener.onPortraitPreview(note)
                } },true,{cinematicTap})
            a.setAnalyzer(portraitExecutor,analyzer); portraitAnalyzer=analyzer; cinematicBound=true
            return effect to a
        }
        cinematicBound=false
        if (portraitPreviewFailed || extension != ExtensionMode.NONE || !support.canCapture) return null
        val portrait = mode == CaptureMode.RETRATO && processingKind(support) == JobKind.PORTRAIT && settings.portraitLivePreview
        val styleMode = settings.liveControls && (mode == CaptureMode.FOTO ||
            (mode in listOf(CaptureMode.RETRATO, CaptureMode.NOITE, CaptureMode.FOOD) && processingKind(support) != null))
        if (!portrait && !styleMode) return null
        val effect = PortraitPreviewEffect.create { e ->
            Log.w(TAG, "prévia do retrato desligada", e)
            portraitPreviewFailed = true
            listener.onMessage("Prévia do desfoque indisponível neste aparelho; a foto continua com o desfoque")
            listener.onPortraitPreview(null)
            handler.post { bind() }
        }.apply {
            strength = settings.portraitBlur
            blurEnabled = portrait
            style = if (styleMode) com.novacamera.s24.core.imaging.PhotoStyle(settings.styleTone, settings.styleWarmth)
                    else com.novacamera.s24.core.imaging.PhotoStyle()
        }
        if (!portrait || liveBlurBlocked) return effect to null
        // Mesma proporção da prévia: os dois buffers cobrem o mesmo campo do sensor.
        val ab = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(ResolutionSelector.Builder()
                .setAspectRatioStrategy(if (wide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(if (wide) android.util.Size(640, 360) else android.util.Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build())
        pinned?.let { Camera2Interop.Extender(ab).setPhysicalCameraId(it) }
        val analysis = ab.build()
        val analysisGeneration=videoEffectGeneration
        val analyzer = PortraitMaskAnalyzer(activity, effect, { heat }, onState = { s -> handler.post {
            if(analysisGeneration==videoEffectGeneration && !released && !paused)listener.onPortraitPreview(s)
        } },tapSensor={cinematicTap},captureBusy={capturing || ProcessingQueue.pending>0})
        analysis.setAnalyzer(portraitExecutor, analyzer)
        portraitAnalyzer = analyzer
        return effect to analysis
    }

    private val videoEffects = com.novacamera.s24.core.SerialEffectSlot<androidx.camera.effects.OverlayEffect>()
    private val videoEffect get() = videoEffects.current
    /** Isola a configuração incompatível; trocar UHD/60 por FHD não perde a assinatura. */
    private var videoEffectFailed = false
    private var commonEffectPending = false
    private val effectThread by lazy { android.os.HandlerThread("nova-video-signature").also { it.start() } }
    private val failedVideoEffectProfiles = mutableSetOf<String>()
    private var videoEffectGeneration = 0L
    private var videoSignatureOverlay: com.novacamera.s24.processing.VideoSignatureOverlay? = null

    private fun onVideoEffectFailure(error: Throwable, generation: Long) {
        Log.e(TAG, "Falha GL da assinatura do vídeo", error)
        handler.post {
            if (released || videoEffectFailed || generation != videoEffectGeneration) return@post
            videoEffectFailed = true
            failedVideoEffectProfiles.add(videoProfile)
            com.novacamera.s24.core.VideoLog.add(activity,
                "Assinatura desligada em $videoProfile: ${error.javaClass.simpleName}: ${error.message}")
            // Se a falha ocorreu durante a gravação, Finalize decide quando é seguro religar.
            if (isRecording) {
                listener.onMessage("Assinatura falhou; finalizando este vídeo. Próxima gravação sem assinatura")
                settingsChangedDuringRecording = true
                stopRecording()
            } else {
                listener.onMessage("Assinatura incompatível nesta configuração; vídeo disponível sem ela")
                pendingZoomOnLens = zoomOnLens
                bind()
            }
        }
    }

    /** Desenha a assinatura em cada quadro gravado (não na pré-visualização). */
    private fun signatureEffect(style: com.novacamera.s24.processing.SignatureStyle): androidx.camera.effects.OverlayEffect {
        commonEffectPending=true
        val generation = videoEffectGeneration
        // Cada handler identifica a geração que falhou, mesmo usando a mesma fila GL serial.
        val effectHandler = VideoEffectHandler(effectThread.looper) { onVideoEffectFailure(it, generation) }
        val effect = androidx.camera.effects.OverlayEffect(androidx.camera.core.CameraEffect.VIDEO_CAPTURE, 0,
            effectHandler) { t -> onVideoEffectFailure(t, generation) }
        val overlay = com.novacamera.s24.processing.VideoSignatureOverlay(style)
        videoSignatureOverlay = overlay
        effect.setOnDrawListener { frame ->
            if (overlay.draw(frame.cropRect, frame.rotationDegrees, frame.isMirroring, frame.size) { frame.overlayCanvas }) {
                if (overlay.drawings == 1) com.novacamera.s24.core.VideoLog.add(activity,
                    "Assinatura desenhada na textura: ${frame.size} · recorte=${frame.cropRect} · rotação=${frame.rotationDegrees} · espelho=${frame.isMirroring}")
            }
            true // textura estática continua aplicada; nunca descarta quadros da gravação.
        }
        return effect
    }

    private fun signatureStyle(): com.novacamera.s24.processing.SignatureStyle? =
        if (!settings.signatureEnabled) null else com.novacamera.s24.processing.SignatureStyle(
            settings.signaturePosition, settings.signatureSize, settings.signatureOpacity,
            captureInfo = settings.signatureCaptureInfo)

    /** Sem exposição manual nem extensão: a compensação de EV realmente muda a exposição. */
    private fun aeAdjustable() = !manual.manualExposure && activeExtension == ExtensionMode.NONE

    /**
     * FOTO: a foto comum já está salva; em segundo plano ela é analisada e, se valer a pena (ou se
     * a assinatura estiver ligada), substituída pela versão pronta.
     */
    private fun enhanceIfEnabled(uri: android.net.Uri, captureFacts: com.novacamera.s24.core.imaging.ImagingFacts? = null) {
        val sig = signatureStyle()
        // Modos do fabricante (Retrato/Noturno Samsung): a foto já vem tratada pelo aparelho; aqui só
        // entra a assinatura (sem ajuste de tom nem estilo, que as extensões não recebem).
        val oem = activeExtension != ExtensionMode.NONE
        val style = if (oem) com.novacamera.s24.core.imaging.PhotoStyle() else currentStyle()
        val autoTone = settings.autoProcess && !oem
        if (oem && sig == null) return
        if ((!autoTone && sig == null && style.neutral) || (mode != CaptureMode.FOTO && !oem) || !ProcessingQueue.canAccept()) return
        val zoom = equivalent(zoomOnLens)
        val facts = captureFacts ?: previewImagingFacts()
        io.execute {
            val bytes = runCatching { activity.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@execute
            val rotation = runCatching {
                androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).rotationDegrees
            }.getOrDefault(0)
            val job = Job(JobKind.AUTO, listOf(CapturedFrame(bytes, rotation, null)), mirror = false, zoom = zoom,
                sourceUri = uri, signature = sig, autoTone = autoTone, imagingFacts = facts, style = style)
            handler.post { ProcessingQueue.submit(activity, job) }
        }
    }

    /** Contexto óptico da prévia; continua explicitamente sem confirmação da lente na foto. */
    private fun previewImagingFacts(): com.novacamera.s24.core.imaging.ImagingFacts {
        val cameraId = camera?.let { runCatching { Camera2CameraInfo.from(it.cameraInfo).cameraId }.getOrNull() }
        val optics = previewOptics
        val sensor = runCatching {
            activity.getSystemService(android.hardware.camera2.CameraManager::class.java)
                .getCameraCharacteristics(optics.first ?: cameraId ?: error("sem câmera"))
                .get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        }.getOrNull()
        return com.novacamera.s24.core.imaging.ImagingFacts(
            device = "${Build.MANUFACTURER}/${Build.MODEL}", cameraId = cameraId,
            physicalId = optics.first, focalMm = optics.second,
            sensorWidthMm = sensor?.width, sensorHeightMm = sensor?.height)
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
                if (released) releaseCaptureResources()
                else if (settingsChangedDuringRecording || freshSessionRequested) { settingsChangedDuringRecording = false; bind() }
            }
        }
    }

    fun startRecording() {
        if (mode == CaptureMode.TIMELAPSE) return startTimelapse()
        val vc = videoCapture ?: return listener.onMessage("A câmera ainda não está pronta")
        if (recording != null || released || paused) return
        videoBindingIssue?.let { listener.onMessage(it); return }
        cinematicUnavailableReason?.let {
            listener.onMessage(it)
            return
        }
        if (camera == null) return listener.onMessage("A câmera ainda não está vinculada")
        val normalVideo = mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO
        val request = VideoContract.Request(requestedVideoSize, state?.videoFps ?: selectedVideoFps, autoFpsApplied)
        if (normalVideo) {
            val crop = vc.resolutionInfo?.cropRect ?: return listener.onMessage("Aguarde a configuração do vídeo")
            if (request.size?.sameFrame(VideoContract.Size(crop.width(), crop.height())) == false)
                return listener.onMessage("Resolução vinculada diferente da selecionada; gravação bloqueada")
        }
        val captureContext = "${if(portraitVideoSession) "RETRATO · VÍDEO" else mode.label} · ${if (isFront) "frontal" else "traseira"} · ${request.size ?: "AUTO"}/${request.fps} fps alvo · assinatura solicitada=${settings.signatureEnabled && settings.signatureVideo}, ativa=${videoEffect != null || (cinematicBound && settings.signatureEnabled && settings.signatureVideo)} · cinematográfico=$cinematicBound"
        val captureOverlay = videoSignatureOverlay
        val captureCompositor=portraitEffect?.takeIf { cinematicBound }
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
                        val uri = event.outputResults.outputUri.takeIf { it != android.net.Uri.EMPTY }
                        listener.onRecordingFinished(uri, err)
                        if (uri != null && normalVideo) inspectVideo(uri, request,
                            "$captureContext · desenhos da assinatura na textura=${captureCompositor?.signatureDrawings ?: captureOverlay?.drawings ?: 0} · caminho=${if(captureCompositor!=null) "compositor" else "OverlayEffect/comum"}")
                        val recovery = videoRecovery.finalized()
                        if (released) releaseCaptureResources()
                        else if (recovery == com.novacamera.s24.core.VideoRecovery.Action.STOP) {
                            blockVideoCapture("Vídeo finalizado. Escolha outra combinação para gravar")
                            if (mode == CaptureMode.VIDEO || mode == CaptureMode.VIDEO_PRO) bind()
                        } else if (recovery == com.novacamera.s24.core.VideoRecovery.Action.REBIND ||
                            settingsChangedDuringRecording || freshSessionRequested) {
                            settingsChangedDuringRecording = false
                            if (!videoRecovery.exhausted) bind()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar gravação", e)
            listener.onMessage("Não foi possível iniciar a gravação")
            null
        }
    }

    private fun blockVideoCapture(reason: String) {
        videoBindingIssue = reason
        state?.copy(videoCaptureProblem = reason,
            videoSuggestedFps = com.novacamera.s24.core.VideoSessionSupport.alternative(selectedVideoFps, state?.videoFpsOptions.orEmpty()))
            ?.let { state = it; listener.onCameraState(it) }
        listener.onMessage(reason)
    }

    private fun inspectVideo(uri: android.net.Uri, request: VideoContract.Request, captureContext: String) {
        videoInspection.execute {
            val report = try {
                val actual = VideoMetadata.read(activity.applicationContext, uri)
                val violation = VideoContract.violation(request, actual.size, actual.fps)
                actual.summary + (violation?.let { " · ATENÇÃO: $it" } ?: "")
            } catch (e: Exception) {
                "MP4 salvo; metadados não medidos: ${e.javaClass.simpleName}: ${e.message}"
            }
            com.novacamera.s24.core.VideoLog.add(activity.applicationContext, "$captureContext · $report")
            handler.post { if (!released) listener.onMessage(report) }
        }
    }

    private var settingsChangedDuringRecording = false

    fun requestRebind() {
        if (isRecording) settingsChangedDuringRecording = true
        else { videoRecovery.reset(); bind() }
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
        /** Rajada RAW de testes no Pro (super-resolução precisa de vários quadros com leve deslocamento). */
        private const val RAW_BURST_FRAMES = 6
        /** Super-resolução: a partir de 1,4× de zoom digital sobre a tele; 8 quadros; espera após trocar o zoom. */
        private const val SR_MIN_DIGITAL = 1.4f
        private const val SR_FRAMES = 8
        private const val SR_SETTLE_MS = 150L
        /** Alta resolução na tele: a partir do 2,5× equivalente (lentes 3× e 5×). */
        private const val TELE_HIGH_RES_FROM = 2.5f
        /** Rajada da tele na coleta de testes (a super-resolução usa de 6 a 8 quadros). */
        private const val COLLECT_TELE_FRAMES = 8
        private const val PINNED_STALL_MS = 3000L
        private const val STALL_PREVIEW_MS = 3000L
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



