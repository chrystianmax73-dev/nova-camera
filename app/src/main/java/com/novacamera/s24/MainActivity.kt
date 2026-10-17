package com.novacamera.s24

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnNextLayout
import com.novacamera.s24.camera.AutoStats
import com.novacamera.s24.camera.CameraListener
import com.novacamera.s24.camera.CameraState
import com.novacamera.s24.camera.ManualControls
import com.novacamera.s24.camera.NovaCamera
import com.novacamera.s24.camera.RecordingPhase
import com.novacamera.s24.core.CameraMath
import com.novacamera.s24.core.CaptureMode
import kotlin.math.roundToInt
import com.novacamera.s24.core.DeviceHeat
import com.novacamera.s24.core.ModeEngine
import com.novacamera.s24.core.ThermalPolicy
import com.novacamera.s24.device.DeviceMonitor
import com.novacamera.s24.settings.AspectRatioOption
import com.novacamera.s24.settings.FlashOption
import com.novacamera.s24.settings.NovaSettings
import com.novacamera.s24.settings.HdrOption
import com.novacamera.s24.processing.JobKind
import com.novacamera.s24.core.TimelapsePlan
import com.novacamera.s24.processing.JobProgress
import com.novacamera.s24.processing.ProcessingQueue
import com.novacamera.s24.settings.VideoQualityOption
import com.novacamera.s24.ui.Haptics
import com.novacamera.s24.ui.HintView
import com.novacamera.s24.ui.ModeGrid
import com.novacamera.s24.ui.ModeStrip
import com.novacamera.s24.ui.Nova
import com.novacamera.s24.ui.NovaIconView
import com.novacamera.s24.ui.NovaIconView.Icon
import com.novacamera.s24.ui.ProPanel
import com.novacamera.s24.ui.ShutterButton
import com.novacamera.s24.ui.TextChip
import com.novacamera.s24.ui.ThumbnailView
import com.novacamera.s24.ui.ViewfinderOverlay
import com.novacamera.s24.ui.ZoomBar
import com.novacamera.s24.ui.dp
import com.novacamera.s24.ui.dpi
import java.util.concurrent.Executors

/**
 * Tela da câmera. A interface é travada em retrato (a pré-visualização não reinicia ao girar);
 * ícones e textos giram com o aparelho e a orientação real vai para os arquivos.
 */
class MainActivity : ComponentActivity(), CameraListener, ViewfinderOverlay.Gestures {
    private lateinit var settings: NovaSettings
    private lateinit var camera: NovaCamera
    private lateinit var monitor: DeviceMonitor
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val sound = MediaActionSound()

    // Views
    private lateinit var root: FrameLayout
    private lateinit var previewFrame: FrameLayout
    private lateinit var preview: PreviewView
    private lateinit var transitionImage: ImageView
    private lateinit var overlay: ViewfinderOverlay
    private lateinit var bottomScrim: View
    private lateinit var topBar: LinearLayout
    private lateinit var flashButton: NovaIconView
    private lateinit var timerButton: NovaIconView
    private lateinit var settingsButton: NovaIconView
    private lateinit var ratioChip: TextChip
    private lateinit var hdrChip: TextChip
    private lateinit var qualityChip: TextChip
    private lateinit var fpsChip: TextChip
    private lateinit var resChip: TextChip
    private lateinit var rawChip: TextChip
    private lateinit var lensChip: TextView
    private lateinit var modeGrid: ModeGrid
    private lateinit var recChip: TextView
    private lateinit var statusRow: LinearLayout
    private lateinit var lockChip: TextView
    private lateinit var evChip: TextView
    private lateinit var heatChip: TextView
    private lateinit var procChip: TextView
    private lateinit var hint: HintView
    private lateinit var infoCard: TextView
    private lateinit var bottom: LinearLayout
    private lateinit var proPanel: ProPanel
    /** Controles criativos ao vivo (o valor exibido é o que a foto usa). */
    private lateinit var liveControls: com.novacamera.s24.ui.LiveControls
    private lateinit var zoomBar: ZoomBar
    private lateinit var modeStrip: ModeStrip
    private lateinit var shutter: ShutterButton
    private lateinit var thumbnail: ThumbnailView
    private lateinit var flipButton: NovaIconView
    private lateinit var idleOverlay: TextView
    private lateinit var permissionOverlay: LinearLayout

    // Estado da tela
    private var insetTop = 0
    private var insetBottom = 0
    private var orientation = 0
    private var cameraState: CameraState? = null
    private var lastMedia: Pair<Uri, String>? = null
    private var torchOn = false
    private var countdown = 0
    private var evDragStart = 0
    private var lastInteraction = SystemClock.elapsedRealtime()
    private var idlePaused = false
    private var announcedMode: CaptureMode? = null
    private var settingsSignature = ""
    private val quickRecording = com.novacamera.s24.core.QuickRecording()
    private var quickReturnMode: CaptureMode? = null
    private var audioStartPending = false
    /** Sessão de revisão da última foto processada (a miniatura abre a revisão em vez da galeria). */
    private var lastSession: Pair<String, Uri>? = null
    private val onProcessing: (JobProgress) -> Unit = { p -> onProcessingProgress(p) }

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showPermissionOverlay()
    }
    private val askAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val start = audioStartPending
        audioStartPending = false
        if (!granted) hint.show("Microfone negado: o vídeo será gravado sem áudio")
        if (start && camera.mode.isVideo && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            camera.startRecording()
    }

    private val orientationListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                val next = CameraMath.snapOrientation(degrees, orientation)
                if (next != orientation) applyOrientation(next)
            }
        }
    }

    // ------------------------------------------------------------------ ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.novacamera.s24.core.CrashLog.install(this)
        com.novacamera.s24.processing.MediaWriter.identify(this)
        enableEdgeToEdge()
        settings = NovaSettings(this)
        Haptics.enabled = settings.haptics
        buildInterface()
        setContentView(root)
        preview.setScreenFlashWindow(window)
        camera = NovaCamera(this, preview, settings, this)
        monitor = DeviceMonitor(this, ::onHeat)
        settingsSignature = signature()
        sound.load(MediaActionSound.SHUTTER_CLICK)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            insetTop = bars.top
            insetBottom = bars.bottom
            layoutAll()
            insets
        }
        refreshStrip(settings.lastMode, animate = false)
        updateTopBar()
        if (hasPermission(Manifest.permission.CAMERA)) startCamera() else askCamera.launch(Manifest.permission.CAMERA)
        loadLatestMedia()
        ProcessingQueue.addListener(onProcessing)
    }

    override fun onStart() {
        super.onStart()
        monitor.start()
        if (orientationListener.canDetectOrientation()) orientationListener.enable()
        lastInteraction = SystemClock.elapsedRealtime()
        handler.removeCallbacks(idleCheck)
        handler.postDelayed(idleCheck, IDLE_CHECK_MS)
        handler.removeCallbacks(energyTick)
        handler.postDelayed(energyTick, ENERGY_EVERY_MS)
        handler.removeCallbacks(aidTick)
        handler.postDelayed(aidTick, AID_EVERY_MS)
        handler.removeCallbacks(backlightTick)
        handler.postDelayed(backlightTick, BACKLIGHT_EVERY_MS)
    }

    // ------------------------------------------------------------------ auxílios do visor

    private var aidBusy = false
    private var aidSlow = false

    /** Nova medição (histograma, estouro, realce de foco) da prévia exibida, poucas vezes por segundo. */
    private val aidTick = object : Runnable {
        override fun run() {
            val delay = if (aidSlow) AID_SLOW_MS else AID_EVERY_MS
            handler.postDelayed(this, delay)
            try { measure() } catch (e: RuntimeException) {
                aidBusy = false; overlay.aid = null
                com.novacamera.s24.core.CrashLog.record(this@MainActivity, "auxílios (não fatal)", e)
            }
        }

        private fun measure() {
            if (!::camera.isInitialized) return
            val pro = camera.mode.isPro
            val peaking = pro && settings.focusPeaking && camera.manual.focusDiopters != null
            val hist = pro && settings.proHistogram
            val clip = pro && settings.proClipping
            overlay.showHistogram = hist; overlay.showClipping = clip; overlay.showPeaking = peaking
            if (!hist && !clip && !peaking) { if (overlay.aid != null) overlay.aid = null; return }
            if (aidBusy || preview.previewStreamState.value != PreviewView.StreamState.STREAMING) return
            val t0 = SystemClock.elapsedRealtime()
            // A própria prévia exibida (já com o efeito de tom): sem fluxo extra da câmera.
            val bmp = runCatching { preview.bitmap }.getOrNull() ?: return
            aidSlow = SystemClock.elapsedRealtime() - t0 > AID_SLOW_COPY_MS
            aidBusy = true
            io.execute {
                val result = runCatching {
                    val w = AID_WIDTH; val h = (AID_WIDTH * bmp.height / bmp.width.toFloat()).toInt().coerceAtLeast(1)
                    val small = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
                    val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
                    if (small !== bmp) small.recycle()
                    com.novacamera.s24.core.imaging.ViewfinderAid.analyze(px, w, h, peaking)
                }.getOrNull()
                bmp.recycle()
                handler.post { aidBusy = false; if (result != null) overlay.aid = result }
            }
        }
    }

    // ------------------------------------------------------------------ contraluz automática

    private var backlightEv = 0f
    private var backlightBusy = false

    /** Mede a prévia exibida (~1,5×/s) e pede à câmera a compensação de contraluz. */
    private val backlightTick = object : Runnable {
        override fun run() {
            handler.postDelayed(this, BACKLIGHT_EVERY_MS)
            if (!::camera.isInitialized || !settings.autoBacklight || camera.mode.isPro || backlightBusy) {
                if (::camera.isInitialized && backlightEv > 0f && (!settings.autoBacklight || camera.mode.isPro))
                    backlightEv = camera.setBacklightEv(0f)
                return
            }
            if (preview.previewStreamState.value != PreviewView.StreamState.STREAMING) return
            val bmp = runCatching { preview.bitmap }.getOrNull() ?: return
            backlightBusy = true
            io.execute {
                val reading = runCatching {
                    val w = 64; val h = (64 * bmp.height / bmp.width.toFloat()).toInt().coerceAtLeast(1)
                    val small = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
                    val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
                    if (small !== bmp) small.recycle()
                    com.novacamera.s24.core.BacklightMeter.measure(px, w, h)
                }.getOrNull()
                bmp.recycle()
                handler.post {
                    backlightBusy = false
                    if (reading == null || !::camera.isInitialized) return@post
                    val m = com.novacamera.s24.core.BacklightMeter
                    val next = m.step(backlightEv, m.target(backlightEv, reading))
                    if (next != backlightEv) backlightEv = camera.setBacklightEv(next)
                }
            }
        }
    }

    private val sensors by lazy { getSystemService(android.hardware.SensorManager::class.java) }
    private var levelSmoothed: Float? = null
    private val levelListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            val a = com.novacamera.s24.core.imaging.ViewfinderAid.horizon(e.values[0], e.values[1], e.values[2])
            if (a == null) { levelSmoothed = null; overlay.horizon = null; return }
            // Suaviza sem atravessar ±180° pelo caminho longo.
            val prev = levelSmoothed
            val next = if (prev == null) a else {
                var d = a - prev; if (d > 180f) d -= 360f; if (d < -180f) d += 360f
                prev + d * 0.25f
            }
            levelSmoothed = next
            overlay.horizon = next
        }
        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) = Unit
    }

    private fun startLevel() {
        stopLevel()
        if (!settings.levelIndicator) return
        val s = sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY) ?: return
        sensors?.registerListener(levelListener, s, android.hardware.SensorManager.SENSOR_DELAY_UI)
    }

    private fun stopLevel() {
        sensors?.unregisterListener(levelListener)
        levelSmoothed = null
        overlay.horizon = null
    }

    /** Amostra de energia/temperatura por atividade (diagnóstico de sessões longas). */
    private val energyTick = object : Runnable {
        override fun run() {
            if (::camera.isInitialized) {
                val activity = when {
                    camera.isRecording -> "${camera.mode.label} (gravando)"
                    else -> camera.mode.label
                } + if (ProcessingQueue.pending > 0) " + processando" else ""
                com.novacamera.s24.core.EnergyLog.add(monitor.energySample(activity, SystemClock.elapsedRealtime()))
            }
            handler.postDelayed(this, ENERGY_EVERY_MS)
        }
    }

    private var newSessionOnResume = false

    override fun onResume() {
        super.onResume()
        Haptics.enabled = settings.haptics
        overlay.showGrid = settings.grid
        startLevel()
        ProcessingQueue.keepOriginals = settings.keepOriginals
        ProcessingQueue.aiDenoise = settings.aiDenoise
        ProcessingQueue.contrastProfile = settings.contrastProfile
        val sig = signature()
        val settingsChanged = sig != settingsSignature
        settingsSignature = sig
        if (newSessionOnResume) {
            newSessionOnResume = false
            if (::camera.isInitialized) camera.beginNewSession()
        } else if (settingsChanged && ::camera.isInitialized && camera.hasProvider) {
            showTransition(); camera.requestRebind()
        }
    }

    /** Sair do app finaliza a gravação de forma íntegra (home, bloqueio, chamada). */
    override fun onStop() {
        newSessionOnResume = true
        audioStartPending = false
        runQuickAction(quickRecording.interrupt())
        cancelCountdown()
        camera.stopRecording()
        if (torchOn) { torchOn = false; camera.setTorch(false) }
        monitor.stop()
        orientationListener.disable()
        handler.removeCallbacks(idleCheck)
        handler.removeCallbacks(energyTick)
        handler.removeCallbacks(aidTick)
        handler.removeCallbacks(backlightTick)
        backlightEv = 0f
        stopLevel()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            ProcessingQueue.trimModelMemory()
    }

    override fun onDestroy() {
        ProcessingQueue.removeListener(onProcessing)
        camera.release()
        sound.release()
        io.shutdown()
        super.onDestroy()
    }

    private fun startCamera() {
        permissionOverlay.visibility = View.GONE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        camera.start()
    }

    private fun signature() = listOf(settings.mirrorFrontVideo, settings.videoQuality, settings.videoFps,
        settings.videoCenterMetering, settings.photoPixelsSignature, settings.hdr, settings.oemPortrait, settings.oemNight,
        settings.signatureEnabled, settings.signatureVideo, settings.signaturePosition, settings.signatureSize, settings.signatureOpacity,
        settings.videoStabilization, settings.facePriority, settings.lockWhiteBalanceWithAeAf).joinToString()

    // ------------------------------------------------------------------ interface

    private fun buildInterface() {
        root = FrameLayout(this).apply { setBackgroundColor(Nova.BG) }

        previewFrame = FrameLayout(this).apply { clipChildren = true }
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        transitionImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        overlay = ViewfinderOverlay(this).apply { gestures = this@MainActivity; showGrid = settings.grid }
        previewFrame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        previewFrame.addView(transitionImage, FrameLayout.LayoutParams(-1, -1))
        previewFrame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(previewFrame, FrameLayout.LayoutParams(-1, -1))
        preview.previewStreamState.observe(this) { st ->
            if (st == PreviewView.StreamState.STREAMING) hideTransition()
        }

        bottomScrim = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x99000000.toInt(), 0xE6000000.toInt()))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(bottomScrim, FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM))

        // Barra superior: ícones distribuídos, alvos de 48dp, sem rótulos redundantes.
        topBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        settingsButton = icon(Icon.SETTINGS) { startActivity(Intent(this, SettingsActivity::class.java)) }
        flashButton = icon(Icon.FLASH_OFF) { cycleFlash() }
        timerButton = icon(Icon.TIMER_OFF) { cycleTimer() }
        ratioChip = TextChip(this).apply { setOnClickListener { cycleAspect() }; contentDescription = "Proporção da foto" }
        hdrChip = TextChip(this).apply {
            text = "HDR"
            setOnClickListener { cycleHdr() }
            contentDescription = "HDR"
        }
        qualityChip = TextChip(this).apply { setOnClickListener { cycleVideoQuality() }; contentDescription = "Resolução do vídeo" }
        fpsChip = TextChip(this).apply { setOnClickListener { cycleFps() }; contentDescription = "Quadros por segundo" }
        resChip = TextChip(this).apply { setOnClickListener { cyclePhotoResolution() }; contentDescription = "Resolução da foto" }
        rawChip = TextChip(this).apply { text = "RAW"; setOnClickListener { toggleRaw() }; contentDescription = "Salvar RAW (DNG) junto do JPEG" }
        listOf<View>(settingsButton, flashButton, timerButton, ratioChip, resChip, qualityChip, fpsChip, hdrChip, rawChip).forEach { v ->
            val cell = FrameLayout(this)
            cell.addView(v, FrameLayout.LayoutParams(if (v is NovaIconView) dpi(48) else -2, if (v is NovaIconView) dpi(48) else -2, Gravity.CENTER))
            topBar.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
        }
        root.addView(topBar, FrameLayout.LayoutParams(-1, dpi(TOP_BAR_DP), Gravity.TOP))

        recChip = TextView(this).apply {
            setTextColor(Nova.TEXT); textSize = 14f; typeface = Nova.MEDIUM
            setPadding(dpi(12), dpi(5), dpi(12), dpi(5))
            background = Nova.pill(Nova.REC, dp(14f))
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(recChip, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        lockChip = statusChip(Nova.LOCK).apply {
            text = "AE/AF BLOQUEADO  ✕"
            contentDescription = "Foco e exposição bloqueados. Toque para desbloquear"
            setOnClickListener { unlockAll() }
        }
        evChip = statusChip(Nova.TEXT).apply { setOnClickListener { unlockAll() } }
        heatChip = statusChip(Nova.WARN).apply { isClickable = false }
        procChip = statusChip(Nova.ACCENT_SOFT).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setOnClickListener {
                ProcessingQueue.cancelAll()
                this@MainActivity.hint.show("Cancelando… o original fica salvo", 1600)
            }
        }
        listOf(lockChip, evChip, heatChip, procChip).forEach {
            it.visibility = View.GONE
            statusRow.addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(4); marginEnd = dpi(4) })
        }
        root.addView(statusRow, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        hint = HintView(this)
        root.addView(hint, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        infoCard = TextView(this).apply {
            setTextColor(Nova.TEXT); textSize = 14f; typeface = Nova.REGULAR
            setLineSpacing(0f, 1.15f)
            gravity = Gravity.CENTER
            setPadding(dpi(20), dpi(16), dpi(20), dpi(16))
            background = Nova.pill(Nova.SURFACE_GLASS, dp(18f))
            visibility = View.GONE
        }
        root.addView(infoCard, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { marginStart = dpi(32); marginEnd = dpi(32) })

        bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        proPanel = ProPanel(this).apply {
            visibility = View.GONE
            listener = object : ProPanel.Listener {
                override fun onManual(m: ManualControls) {
                    camera.setManual(m)
                    proPanel.syncManual(camera.manual)
                }
                override fun onEv(index: Int) = camera.setExposureIndex(index)
            }
        }
        liveControls = com.novacamera.s24.ui.LiveControls(this).apply {
            visibility = View.GONE
            onRulerChanged = { updateZoomVisibility() }
            listener = object : com.novacamera.s24.ui.LiveControls.Listener {
                override fun onBlur(v: Float) = camera.setPortraitBlur(v)
                override fun onEv(index: Int) = camera.setExposureIndex(index)
                override fun onTone(v: Float) = camera.setStyle(com.novacamera.s24.core.imaging.PhotoStyle(v, settings.styleWarmth))
                override fun onWarmth(v: Float) = camera.setStyle(com.novacamera.s24.core.imaging.PhotoStyle(settings.styleTone, v))
            }
        }
        zoomBar = ZoomBar(this).apply {
            listener = object : ZoomBar.Listener {
                override fun onStop(value: Float) = camera.zoomTo(value)
                override fun onDrag(value: Float) = camera.zoomFollow(value)
                override fun onDragEnd() {}
            }
        }
        modeStrip = ModeStrip(this).apply { onSelect = ::onStripSelect }
        modeGrid = ModeGrid(this).apply {
            visibility = View.GONE
            onPick = { m ->
                val support = camera.supportFor(m)
                if (support.canCapture) { hideGrid(); selectMode(m) } else hint.show(support.note, 3500)
            }
        }

        val controls = FrameLayout(this)
        thumbnail = ThumbnailView(this).apply {
            setOnClickListener { openLastMedia() }
            // Comparação Original × Nova é opcional: toque e segure (o toque simples abre a galeria).
            setOnLongClickListener { openReview(); true }
        }
        shutter = ShutterButton(this).apply {
            setOnClickListener { onShutter() }
            quickListener = object : ShutterButton.QuickListener {
                override fun onHold() = beginQuickRecording()
                override fun onLock() {
                    quickRecording.lock()
                    if (quickRecording.locked) {
                        Haptics.confirm(shutter)
                        hint.show("Gravação bloqueada · toque no botão para finalizar")
                    }
                }
                override fun onRelease() = runQuickAction(quickRecording.release())
                override fun onCancel() = runQuickAction(quickRecording.interrupt())
            }
        }
        flipButton = icon(Icon.FLIP) { onFlip() }.apply {
            background = Nova.pill(0x33FFFFFF, dp(24f))
        }
        controls.addView(thumbnail, FrameLayout.LayoutParams(dpi(48), dpi(48), Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = dpi(36) })
        controls.addView(shutter, FrameLayout.LayoutParams(dpi(78), dpi(78), Gravity.CENTER))
        controls.addView(flipButton, FrameLayout.LayoutParams(dpi(48), dpi(48), Gravity.CENTER_VERTICAL or Gravity.END).apply { marginEnd = dpi(36) })

        bottom.addView(proPanel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(4) })
        bottom.addView(liveControls, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(4) })
        bottom.addView(modeStrip, LinearLayout.LayoutParams(-1, dpi(40)))
        bottom.addView(controls, LinearLayout.LayoutParams(-1, dpi(96)))
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        // Régua dos controles ao vivo abre/fecha: o painel de baixo muda de altura e a barra de
        // zoom precisa subir junto (sem sobrepor a régua).
        bottom.addOnLayoutChangeListener { _, _, top, _, bottomEdge, _, oldTop, _, oldBottom ->
            if (bottomEdge - top != oldBottom - oldTop) cameraState?.let { st -> handler.post { layoutAll(st.mode) } }
        }
        root.addView(zoomBar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        lensChip = statusChip(Nova.ACCENT_SOFT).apply { visibility = View.GONE; isClickable = false }
        root.addView(lensChip, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        root.addView(modeGrid, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { marginStart = dpi(12); marginEnd = dpi(12) })

        idleOverlay = TextView(this).apply {
            text = "Câmera pausada para poupar bateria\n\nToque para continuar"
            setTextColor(Nova.TEXT); textSize = 15f; gravity = Gravity.CENTER
            setBackgroundColor(0xE6070D19.toInt())
            visibility = View.GONE
            setOnClickListener { resumeFromIdle() }
        }
        root.addView(idleOverlay, FrameLayout.LayoutParams(-1, -1))

        permissionOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Nova.BG)
            setPadding(dpi(32), 0, dpi(32), 0)
            visibility = View.GONE
            addView(TextView(context).apply {
                text = "A Nova Camera precisa de acesso à câmera.\nFotos e vídeos ficam só no seu aparelho."
                setTextColor(Nova.TEXT); textSize = 16f; gravity = Gravity.CENTER
                setLineSpacing(0f, 1.2f)
            })
            addView(TextView(context).apply {
                text = "Permitir acesso"
                setTextColor(Nova.BG); textSize = 15f; typeface = Nova.MEDIUM; gravity = Gravity.CENTER
                background = Nova.pill(Nova.ACCENT_SOFT, dp(24f))
                setPadding(dpi(24), dpi(12), dpi(24), dpi(12))
                setOnClickListener { requestCameraAgain() }
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(24) })
        }
        root.addView(permissionOverlay, FrameLayout.LayoutParams(-1, -1))
    }

    private fun icon(i: Icon, onTap: () -> Unit) = NovaIconView(this, i).apply {
        setOnClickListener { Haptics.tap(this); onTap() }
    }

    private fun statusChip(color: Int) = TextView(this).apply {
        setTextColor(color); textSize = 12f; typeface = Nova.MEDIUM
        letterSpacing = 0.04f
        setPadding(dpi(10), dpi(4), dpi(10), dpi(4))
        background = Nova.pill(0x99000000.toInt(), dp(12f))
        minHeight = dpi(28)
        gravity = Gravity.CENTER
    }

    /** Posiciona visor e controles conforme proporção, modo e áreas seguras do sistema. */
    private var zoomAvailable = true
    private var zoomInBand = false

    private fun updateZoomVisibility() {
        zoomBar.visibility = if (zoomAvailable && !(zoomInBand && liveControls.rulerOpen)) View.VISIBLE else View.INVISIBLE
    }

    private fun layoutAll(mode: CaptureMode = camera.mode) {
        val w = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val h = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val topBarBottom = insetTop + dpi(TOP_BAR_DP)
        val video = mode.isVideo
        val aspect = if (video) AspectRatioOption.RATIO_16_9 else settings.aspect
        val (pTop, pHeight) = when (aspect) {
            AspectRatioOption.RATIO_4_3 -> topBarBottom to (w * 4 / 3)
            AspectRatioOption.RATIO_16_9 -> {
                val ph = w * 16 / 9
                (if (topBarBottom + ph <= h) topBarBottom else ((h - ph) / 2).coerceAtLeast(0)) to ph
            }
            AspectRatioOption.RATIO_1_1 -> (topBarBottom + w / 6) to w
            AspectRatioOption.FULL -> 0 to h
        }
        (previewFrame.layoutParams as FrameLayout.LayoutParams).apply {
            width = w; height = pHeight.coerceAtMost(h); topMargin = pTop; gravity = Gravity.TOP
        }
        previewFrame.requestLayout()

        (topBar.layoutParams as FrameLayout.LayoutParams).topMargin = insetTop
        (recChip.layoutParams as FrameLayout.LayoutParams).topMargin = insetTop + dpi(12)
        (statusRow.layoutParams as FrameLayout.LayoutParams).topMargin = maxOf(pTop, topBarBottom) + dpi(10)
        (hint.layoutParams as FrameLayout.LayoutParams).topMargin = maxOf(pTop, topBarBottom) + dpi(48)
        (bottom.layoutParams as FrameLayout.LayoutParams).bottomMargin = insetBottom + dpi(6)
        bottom.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        (bottomScrim.layoutParams as FrameLayout.LayoutParams).height = bottom.measuredHeight + insetBottom + dpi(48)
        zoomBar.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val bottomTop = h - insetBottom - dpi(6) - bottom.measuredHeight
        val previewBottom = pTop + pHeight.coerceAtMost(h)
        // No 4:3/1:1 sobra uma faixa entre o visor e os controles: o zoom fica nela, sem cobrir a imagem.
        // A régua dos controles ao vivo abre nessa mesma faixa; enquanto aberta, o zoom some (a pinça segue).
        val rulerExtra = if (liveControls.rulerOpen) liveControls.rulerSlot else 0
        val band = bottomTop + rulerExtra - previewBottom
        zoomInBand = !video && band >= zoomBar.measuredHeight
        (zoomBar.layoutParams as FrameLayout.LayoutParams).topMargin =
            if (zoomInBand) previewBottom + (band - zoomBar.measuredHeight) / 2
            // Senão, dentro do visor, logo acima da borda inferior (ou acima dos controles, se o visor os cobrir).
            else minOf(previewBottom - dpi(14), bottomTop - dpi(8)) - zoomBar.measuredHeight
        updateZoomVisibility()
        (lensChip.layoutParams as FrameLayout.LayoutParams).topMargin =
            (zoomBar.layoutParams as FrameLayout.LayoutParams).topMargin - dpi(36)
        (modeGrid.layoutParams as FrameLayout.LayoutParams).bottomMargin = h - bottomTop + dpi(4)
        // Sem sobreposição no 4:3 o fundo já é escuro; em 16:9/tela cheia o degradê protege a legibilidade.
        bottomScrim.visibility = if (pTop + pHeight > h - bottom.measuredHeight - insetBottom) View.VISIBLE else View.GONE
        topBar.setBackgroundColor(if (aspect == AspectRatioOption.FULL) 0x59000000 else 0)
        root.requestLayout()
    }

    /** Rebind só depois que o visor assumir o novo tamanho (ViewPort correto). */
    private fun relayoutThenBind(block: () -> Unit) {
        val beforeW = previewFrame.width
        val beforeH = previewFrame.height
        layoutAll()
        val lp = previewFrame.layoutParams
        if (lp.width == beforeW && lp.height == beforeH) block()
        else preview.doOnNextLayout { block() }
    }

    private fun applyOrientation(deg: Int) {
        orientation = deg
        camera.setRotation(CameraMath.surfaceRotationFor(deg))
        overlay.orientation = deg
        zoomBar.orientation = deg
        val target = CameraMath.iconRotationFor(deg)
        listOf<View>(settingsButton, flashButton, timerButton, ratioChip, resChip, hdrChip, rawChip, qualityChip, fpsChip, thumbnail, flipButton, recChip, statusRow, hint, lensChip).forEach { v ->
            // Caminho mais curto (evita giro de 270° entre 90 e −90+...).
            var delta = (target - v.rotation) % 360f
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            v.animate().rotation(v.rotation + delta).setDuration(Nova.NORMAL).setInterpolator(Nova.EASE).start()
        }
    }

    // ------------------------------------------------------------------ transições

    private fun showTransition() {
        val bmp: Bitmap = preview.bitmap ?: return
        transitionImage.setImageBitmap(bmp)
        if (Build.VERSION.SDK_INT >= 31) {
            transitionImage.setRenderEffect(RenderEffect.createBlurEffect(dp(24f), dp(24f), Shader.TileMode.CLAMP))
        }
        transitionImage.animate().cancel()
        transitionImage.alpha = 1f
        transitionImage.visibility = View.VISIBLE
        handler.removeCallbacks(transitionTimeout)
        handler.postDelayed(transitionTimeout, 1800)
    }

    private val transitionTimeout = Runnable { hideTransition() }

    private fun hideTransition() {
        if (transitionImage.visibility != View.VISIBLE) return
        transitionImage.animate().alpha(0f).setDuration(220).setInterpolator(Nova.EASE).withEndAction {
            transitionImage.visibility = View.GONE
            transitionImage.setImageDrawable(null)
        }.start()
    }

    // ------------------------------------------------------------------ ações

    /** Faixa principal: RETRATO · FOTO · VÍDEO · (modo extra ativo ou MAIS). */
    private fun refreshStrip(mode: CaptureMode, animate: Boolean = true) {
        val extra = if (mode.primary) "MAIS" else mode.label
        modeStrip.labels = PRIMARY_MODES.map { it.label } + extra
        val index = PRIMARY_MODES.indexOf(mode).takeIf { it >= 0 } ?: PRIMARY_MODES.size
        if (animate) modeStrip.selectedIndex = index else modeStrip.jumpTo(index)
        modeStrip.unavailable = PRIMARY_MODES.mapIndexedNotNull { i, m -> i.takeIf { camera.hasProvider && !camera.supportFor(m).canCapture } }.toSet()
    }

    private fun onStripSelect(index: Int) {
        if (index < PRIMARY_MODES.size) {
            hideGrid()
            selectMode(PRIMARY_MODES[index])
        } else if (modeGrid.visibility == View.VISIBLE) hideGrid() else showGrid()
    }

    private fun showGrid() {
        if (camera.isRecording) return
        modeGrid.bind(EXTRA_MODES.map { it to camera.supportFor(it) }, camera.mode)
        modeGrid.alpha = 0f
        modeGrid.translationY = dp(16f)
        modeGrid.visibility = View.VISIBLE
        // O painel cobre a área do zoom: esconde zoom e aviso de lente para não "vazarem" pelo vidro.
        zoomBar.animate().alpha(0f).setDuration(Nova.FAST).start()
        handler.removeCallbacks(hideLens); lensChip.visibility = View.GONE
        modeGrid.animate().alpha(1f).translationY(0f).setDuration(Nova.NORMAL).setInterpolator(Nova.EASE).start()
    }

    private fun hideGrid() {
        if (modeGrid.visibility != View.VISIBLE) return
        zoomBar.animate().alpha(1f).setDuration(Nova.NORMAL).start()
        modeGrid.animate().alpha(0f).translationY(dp(12f)).setDuration(Nova.FAST).withEndAction { modeGrid.visibility = View.GONE }.start()
    }

    private fun selectMode(m: CaptureMode) {
        if (camera.isRecording || countdown > 0 || quickReturnMode != null) return
        refreshStrip(m)
        showTransition()
        overlay.hideFocusRing()
        camera.setMode(m, rebind = false)
        relayoutThenBind { camera.bind() }
    }

    private fun onFlip() {
        if (camera.isRecording) { camera.pauseOrResumeRecording(); return }
        if (countdown > 0 || quickReturnMode != null) return
        showTransition()
        overlay.hideFocusRing()
        // Meia volta escondendo o visor, troca a câmera, e a outra meia volta revela a nova imagem.
        previewFrame.animate().rotationY(90f).setDuration(130).setInterpolator(Nova.EASE).withEndAction {
            camera.switchFacing()
            previewFrame.rotationY = -90f
            previewFrame.animate().rotationY(0f).setDuration(170).setInterpolator(Nova.EASE).start()
        }.start()
    }

    private fun onShutter() {
        val s = cameraState ?: return
        if (camera.isRecording) {
            quickRecording.interrupt()
            Haptics.confirm(shutter)
            camera.stopRecording()
            return
        }
        if (quickReturnMode != null) { runQuickAction(quickRecording.interrupt()); return }
        if (!camera.ready) return
        if (s.mode.isVideo) {
            if (camera.isRecording) {
                Haptics.confirm(shutter)
                camera.stopRecording()
            } else startVideo()
            return
        }
        if (!s.support.canCapture) { hint.show(s.support.note, 4000); return }
        if (countdown > 0) { cancelCountdown(); return }
        val timer = settings.timerSeconds
        if (timer > 0) startCountdown(timer) else takePhoto()
    }

    private fun takePhoto() {
        Haptics.tap(shutter)
        camera.capturePhoto()
    }

    private fun startVideo() {
        // Timelapse e câmera lenta não gravam som: não pede o microfone à toa.
        val silent = camera.mode == CaptureMode.TIMELAPSE || camera.mode == CaptureMode.CAMERA_LENTA
        if (!silent && settings.recordAudio && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            audioStartPending = true
            askAudio.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        Haptics.confirm(shutter)
        if (settings.shutterSound) sound.play(MediaActionSound.START_VIDEO_RECORDING)
        camera.startRecording()
    }

    private fun beginQuickRecording(): Boolean {
        if (!camera.ready || shutter.busy || countdown > 0 || quickReturnMode != null ||
            camera.mode !in setOf(CaptureMode.FOTO, CaptureMode.RETRATO)) return false
        if (settings.recordAudio && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            // A permissão interrompe o gesto. Nunca começar vídeo tardio quando o diálogo voltar.
            audioStartPending = false
            hint.show("Autorize o microfone e mantenha pressionado novamente para gravar")
            askAudio.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        if (!quickRecording.hold()) return false
        quickReturnMode = camera.mode
        hint.show("Preparando vídeo · solte para parar · arraste para bloquear")
        Haptics.confirm(shutter)
        showTransition()
        camera.setMode(CaptureMode.VIDEO, rebind = false, remember = false)
        refreshStrip(CaptureMode.VIDEO)
        relayoutThenBind {
            if (quickReturnMode != null && quickRecording.active) {
                camera.bind()
                if (!camera.ready || camera.state?.videoCaptureProblem != null) {
                    quickRecording.finished()
                    restoreQuickMode()
                }
            }
        }
        return true
    }

    private fun runQuickAction(action: com.novacamera.s24.core.QuickRecording.Action) {
        when (action) {
            com.novacamera.s24.core.QuickRecording.Action.STOP -> camera.stopRecording()
            com.novacamera.s24.core.QuickRecording.Action.RESTORE -> restoreQuickMode()
            else -> Unit
        }
    }

    private fun restoreQuickMode() {
        val previous = quickReturnMode ?: return
        quickReturnMode = null
        quickRecording.finished()
        if (isDestroyed || isFinishing) return
        camera.setMode(previous, rebind = false)
        refreshStrip(previous)
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            relayoutThenBind { camera.bind() }
    }

    private fun startCountdown(seconds: Int) {
        countdown = seconds
        tickCountdown()
    }

    private val countdownTick = Runnable { tickCountdown() }

    private fun tickCountdown() {
        if (countdown <= 0) {
            shutter.countdown = 0; overlay.setBigCountdown(0)
            takePhoto()
            return
        }
        shutter.countdown = countdown
        overlay.setBigCountdown(countdown)
        Haptics.tick(shutter)
        countdown--
        handler.postDelayed(countdownTick, 1000)
    }

    private fun cancelCountdown() {
        handler.removeCallbacks(countdownTick)
        countdown = 0
        if (::shutter.isInitialized) { shutter.countdown = 0; overlay.setBigCountdown(0) }
    }

    private fun cycleFlash() {
        val s = cameraState ?: return
        if (s.mode.isVideo) {
            torchOn = !torchOn
            camera.setTorch(torchOn)
        } else {
            settings.flash = when (settings.flash) {
                FlashOption.OFF -> FlashOption.AUTO
                FlashOption.AUTO -> FlashOption.ON
                FlashOption.ON -> FlashOption.OFF
            }
            camera.refreshFlash()
            hint.show("Flash: ${settings.flash.label.lowercase()}" + if (s.screenFlash) " (tela)" else "", 1400)
        }
        updateTopBar()
    }

    private fun cycleTimer() {
        settings.timerSeconds = when (settings.timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
        hint.show(if (settings.timerSeconds == 0) "Temporizador desligado" else "Temporizador: ${settings.timerSeconds} s", 1400)
        updateTopBar()
    }

    private fun cycleAspect() {
        settings.aspect = AspectRatioOption.entries[(settings.aspect.ordinal + 1) % AspectRatioOption.entries.size]
        updateTopBar()
        showTransition()
        overlay.hideFocusRing()
        relayoutThenBind { camera.bind() }
    }

    /** HDR: desligado → Nova (fusão própria) → fabricante (se disponível) → desligado. */
    private fun cycleHdr() {
        val oem = cameraState?.oemHdrAvailable == true
        settings.hdr = when (settings.hdr) {
            HdrOption.AUTO -> HdrOption.NOVA
            HdrOption.OFF -> HdrOption.AUTO
            HdrOption.NOVA -> if (oem) HdrOption.OEM else HdrOption.OFF
            HdrOption.OEM -> HdrOption.OFF
        }
        settingsSignature = signature()
        showTransition()
        camera.bind()
        hint.show(when (settings.hdr) {
            HdrOption.AUTO -> "Foto adaptativa: HDR e redução de ruído quando a cena precisa"
            HdrOption.OFF -> "HDR desligado"
            HdrOption.NOVA -> "HDR Nova: 3 exposições combinadas no aparelho"
            HdrOption.OEM -> "HDR do fabricante"
        }, 1800)
    }

    /** Percorre só as resoluções que esta câmera realmente grava. */
    private fun cycleVideoQuality() {
        val options = cameraState?.videoQualityOptions?.takeIf { it.isNotEmpty() } ?: return
        val i = options.indexOf(settings.videoQuality)
        settings.videoQuality = options[(i + 1) % options.size]
        showTransition()
        camera.requestRebind()
        hint.show("Vídeo: ${settings.videoQuality.label}", 1400)
    }

    /** Percorre só as taxas aceitas (24/30/60; 120/240 na câmera lenta). */
    private fun cycleFps() {
        val s = cameraState ?: return
        if (s.mode == CaptureMode.TIMELAPSE) {
            val list = TimelapsePlan.INTERVALS_MS
            settings.timelapseIntervalMs = list[(list.indexOf(settings.timelapseIntervalMs) + 1) % list.size]
            val plan = TimelapsePlan(settings.timelapseIntervalMs, settings.timelapseMaxSeconds)
            camera.bind()
            return hint.show("1 quadro a cada ${TimelapsePlan.intervalLabel(plan.intervalMs)} · ${plan.speedup}× mais rápido", 1800)
        }
        val options = s.videoFpsOptions.takeIf { it.size > 1 } ?: return hint.show("Esta câmera só oferece ${s.videoFps} fps aqui", 1600)
        val next = options[(options.indexOf(s.videoFps) + 1) % options.size]
        if (s.mode == CaptureMode.CAMERA_LENTA) settings.slowMotionFps = next else settings.videoFps = next
        showTransition()
        camera.requestRebind()
        hint.show("$next fps", 1200)
    }

    /** Percorre as resoluções reais desta lente; o que o fabricante bloqueia é só informado. */
    private fun cyclePhotoResolution() {
        val s = cameraState ?: return
        val blocked = s.photoSizeBlocked.joinToString { it.label }
        val blockedNote = if (blocked.isNotEmpty()) "\n$blocked: modo máximo exige captura nativa JPEG em Foto 4:3" else ""
        if (s.processing != null && s.mode != CaptureMode.FOTO) return hint.show("${s.mode.label} usa a resolução padrão (processamento multiquadro)$blockedNote", 3000)
        val options = s.photoSizeOptions
        if (options.size <= 1) {
            return hint.show("Esta lente libera só ${options.firstOrNull()?.label ?: s.photoResolution?.let { mp(it) } ?: "uma resolução"} a outros apps$blockedNote", 3500)
        }
        val i = options.indexOf(s.photoSize).coerceAtLeast(0)
        // Da padrão para a maior e de volta: a lista vem da maior para a menor.
        val next = options[(i - 1 + options.size) % options.size]
        showTransition()
        camera.setPhotoSize(next)
        settingsSignature = signature()
        hint.show(next.label + if (next.highResolution) " · alta resolução (captura mais lenta)" else "", 1800)
    }

    private fun mp(size: Size) = "${Math.round(size.width.toLong() * size.height / 1e6)} MP"

    private fun toggleRaw() {
        settings.raw = !settings.raw
        showTransition()
        camera.bind()
        hint.show(if (settings.raw) "RAW (DNG) + JPEG" else "Só JPEG", 1400)
    }

    private fun unlockAll() {
        camera.resetToAuto()
        overlay.hideFocusRing()
        lockChip.visibility = View.GONE
        evChip.visibility = View.GONE
        Haptics.tick(lockChip)
    }

    private fun updateTopBar() {
        val s = cameraState
        val video = (s?.mode ?: camera.mode).isVideo
        val photoCapable = s?.support?.canCapture != false && !video
        val flashUsable = s != null && (s.hasFlash || (s.screenFlash && !video)) && !s.extensionActive && s.mode != CaptureMode.NOITE
        flashButton.visibility = if (flashUsable && (photoCapable || video)) View.VISIBLE else View.INVISIBLE
        flashButton.icon = when {
            video -> if (torchOn) Icon.FLASH_ON else Icon.FLASH_OFF
            settings.flash == FlashOption.AUTO -> Icon.FLASH_AUTO
            settings.flash == FlashOption.ON -> Icon.FLASH_ON
            else -> Icon.FLASH_OFF
        }
        // INVISIBLE (não GONE): os demais ícones não mudam de posição entre modos.
        timerButton.visibility = if (photoCapable) View.VISIBLE else View.INVISIBLE
        timerButton.icon = when (settings.timerSeconds) { 3 -> Icon.TIMER_3; 10 -> Icon.TIMER_10; else -> Icon.TIMER_OFF }
        ratioChip.visibility = if (!video) View.VISIBLE else View.GONE
        ratioChip.text = settings.aspect.label
        qualityChip.visibility = if (video && s?.videoQualityLabel != null) View.VISIBLE else View.GONE
        qualityChip.text = s?.videoQualityLabel ?: ""
        fpsChip.visibility = if (video && s != null) View.VISIBLE else View.GONE
        fpsChip.text = s?.timelapseIntervalMs?.let { "⏱ " + TimelapsePlan.intervalLabel(it) } ?: if (s?.videoFpsVariable == true) "15–30 fps alvo" else "${s?.videoFps ?: 30} fps alvo"
        hdrChip.visibility = if (s?.mode == CaptureMode.FOTO && photoCapable) View.VISIBLE else View.GONE
        hdrChip.active = s?.hdr != null && s.hdr != HdrOption.OFF
        hdrChip.text = when(s?.hdr) { HdrOption.AUTO -> "AUTO"; HdrOption.OEM -> "HDR·S"; else -> "HDR" }
        val res = s?.photoResolution
        // Sempre visível nos modos de foto: mostra a resolução real que será gravada nesta lente.
        resChip.visibility = if (!video && photoCapable && res != null) View.VISIBLE else View.GONE
        resChip.text = res?.let(::mp) ?: ""
        resChip.contentDescription = resChip.text.toString() + if(s?.photoNativeCapture==true) ", captura nativa solicitada; resolução conferida após fotografar" else ", resolução de captura"
        resChip.active = s?.photoSize != null && s.photoSizeOptions.size > 1 && s.photoSize != s.photoSizeOptions.last()
        rawChip.visibility = if (s?.rawAvailable == true) View.VISIBLE else View.GONE
        rawChip.active = s?.rawActive == true
        listOf<View>(hdrChip, ratioChip, qualityChip, fpsChip, resChip, rawChip).forEach { (it.parent as? View)?.visibility = it.visibility }
    }

    // ------------------------------------------------------------------ CameraListener

    override fun onCameraState(state: CameraState) {
        cameraState = state
        refreshStrip(state.mode)
        shutter.quickEnabled = state.support.canCapture && quickReturnMode == null &&
            (state.mode == CaptureMode.FOTO || state.mode == CaptureMode.RETRATO)
        shutter.kind = if (state.mode.isVideo) ShutterButton.Kind.VIDEO else ShutterButton.Kind.PHOTO
        shutter.isEnabled = state.support.canCapture && state.videoCaptureProblem == null
        shutter.alpha = if (shutter.isEnabled) 1f else 0.5f
        overlay.focusEnabled = state.support.canCapture
        zoomBar.stops = state.zoomStops
        zoomAvailable = state.zoomStops.size > 1 || state.zoomMax > state.zoomMin * 1.05f
        updateZoomVisibility()
        if (!state.support.canCapture) {
            infoCard.text = "${state.mode.label} indisponível neste aparelho\n\n${state.support.note}"
            infoCard.visibility = View.VISIBLE
        } else if (state.videoCaptureProblem != null) {
            val alternative = state.videoSuggestedFps
            infoCard.text = state.videoCaptureProblem + if (alternative != null)
                "\n\nToque para escolher ${state.videoQualityLabel ?: settings.videoQuality.label}/$alternative fps" else
                "\n\nEscolha outra resolução ou taxa de quadros nos controles acima"
            infoCard.visibility = View.VISIBLE
            infoCard.setOnClickListener {
                if (alternative != null && !camera.isRecording) android.app.AlertDialog.Builder(this)
                    .setTitle("Alterar taxa de quadros?")
                    .setMessage("Manter ${settings.videoQuality.label} e tentar $alternative fps? O arquivo será conferido após a gravação.")
                    .setNegativeButton("Manter seleção", null)
                    .setPositiveButton("Usar $alternative fps") { _, _ ->
                        settings.videoFps = alternative
                        showTransition(); camera.requestRebind()
                    }.show()
            }
        } else { infoCard.visibility = View.GONE; infoCard.setOnClickListener(null) }
        if (state.support.engine == ModeEngine.NOVA && state.support.note.isNotBlank() && announcedMode != state.mode) hint.show(state.support.note, 4500)
        announcedMode = state.mode
        proPanel.visibility = if (state.mode.isPro) View.VISIBLE else View.GONE
        val portrait = state.mode == CaptureMode.RETRATO && state.support.engine == ModeEngine.NOVA && state.support.canCapture &&
            settings.portraitLivePreview && camera.styleActive
        val kinds = buildSet {
            if (state.support.canCapture && !state.mode.isPro && !state.mode.isVideo && state.evMax > state.evMin && !camera.manual.manualExposure)
                add(com.novacamera.s24.ui.LiveControls.Kind.EV)
            if (camera.styleActive && settings.liveControls && !state.mode.isVideo) {
                add(com.novacamera.s24.ui.LiveControls.Kind.TONE); add(com.novacamera.s24.ui.LiveControls.Kind.WARMTH)
            }
            if (portrait) add(com.novacamera.s24.ui.LiveControls.Kind.BLUR)
        }
        liveControls.configure(kinds, settings.portraitBlur, settings.styleTone, settings.styleWarmth,
            state.evIndex, state.evMin, state.evMax, state.evStepNum, state.evStepDen)
        if (state.mode.isPro) {
            proPanel.configure(state.caps, state.evMin, state.evMax, state.evStepNum, state.evStepDen, state.evIndex, camera.manual)
        }
        lockChip.visibility = View.GONE
        updateEvChip(state.evIndex)
        updateTopBar()
        layoutAll(state.mode)
        if (quickReturnMode != null && state.mode == CaptureMode.VIDEO) handler.post {
            if (quickReturnMode != null && camera.mode == CaptureMode.VIDEO &&
                state.videoCaptureProblem == null &&
                quickRecording.ready() == com.novacamera.s24.core.QuickRecording.Action.START) {
                camera.startRecording()
                if (!camera.isRecording) { quickRecording.finished(); restoreQuickMode() }
            }
        }
    }

    override fun onZoom(equivalentZoom: Float) { zoomBar.zoom = equivalentZoom }

    override fun onPortraitPreview(state: String?) = liveControls.setNote(state)

    override fun onFocusResult(success: Boolean, locked: Boolean) {
        overlay.setFocusResult(success, locked && camera.exposureLocked)
        if (locked) {
            Haptics.heavy(overlay)
            lockChip.text = if (camera.exposureLocked) "AE/AF BLOQUEADO  ✕" else "AF BLOQUEADO  ✕"
            lockChip.visibility = View.VISIBLE
        }
    }

    override fun onFocusCleared() {
        overlay.hideFocusRing()
        lockChip.visibility = View.GONE
    }

    override fun onExposureIndex(index: Int) {
        updateEvChip(index)
        proPanel.setEvIndex(index)
        liveControls.setEvIndex(index)
    }

    private fun updateEvChip(index: Int) {
        val s = cameraState ?: return
        if (index == 0 || s.mode.isPro) { evChip.visibility = View.GONE; return }
        evChip.text = "☀ ${CameraMath.formatEv(CameraMath.evForIndex(index, s.evStepNum, s.evStepDen))}  ✕"
        evChip.contentDescription = "Compensação de exposição ${evChip.text}. Toque para voltar ao automático"
        evChip.visibility = View.VISIBLE
    }

    override fun onAutoStats(stats: AutoStats) = proPanel.setStats(stats)

    override fun onManualReverted(m: ManualControls) = proPanel.syncManual(m)

    private val hideLens = Runnable { lensChip.animate().alpha(0f).setDuration(Nova.NORMAL).withEndAction { lensChip.visibility = View.GONE }.start() }

    /** Lente física ativa informada pela câmera (ex.: troca para a tele 5× durante o zoom). */
    override fun onActiveLens(label: String?) {
        if (label == null) return
        lensChip.text = label
        lensChip.alpha = 1f
        lensChip.visibility = View.VISIBLE
        handler.removeCallbacks(hideLens)
        handler.postDelayed(hideLens, 1600)
    }

    override fun onCaptureStarted() {
        overlay.flash()
        shutter.busy = true
        if (settings.shutterSound) sound.play(MediaActionSound.SHUTTER_CLICK)
    }

    override fun onPhotoSaved(uri: Uri?) {
        shutter.busy = false
        if (uri != null) { lastSession = null; showMedia(uri, "image/jpeg", animate = true) }
    }

    override fun onBurstProgress(done: Int, total: Int) = hint.show("Segure firme · $done/$total", 900)

    override fun onProcessingQueued(kind: JobKind) {
        shutter.busy = false
    }

    private fun onProcessingProgress(p: JobProgress) {
        if (isDestroyed) return
        val auto = p.kind == JobKind.AUTO
        if (p.finished) {
            if (!auto || p.pending == 0) procChip.visibility = if (p.pending > 0) View.VISIBLE else View.GONE
            val shown = p.enhancedUri ?: if (auto) null else p.originalUri
            // AUTO regrava a mesma foto: só atualiza a miniatura se ela ainda for a última.
            if (shown != null && (!auto || lastMedia?.first == shown)) {
                showMedia(shown, "image/jpeg", animate = true)
                lastSession = if (p.sessionId != null && p.enhancedUri != null) p.sessionId to p.enhancedUri else null
            }
            p.message?.let { hint.show(it, 3200) }
            return
        }
        // O ajuste automático da FOTO é rápido e silencioso: sem selo de progresso.
        if (auto || p.kind==JobKind.PHOTO) return
        procChip.text = "${p.kind.label} · ${p.stage} ${Math.round(p.fraction * 100)}%  ✕"
        procChip.contentDescription = "Processando ${p.kind.label}, ${Math.round(p.fraction * 100)} por cento. Toque para cancelar"
        procChip.visibility = View.VISIBLE
    }

    override fun onRecording(phase: RecordingPhase, seconds: Long) {
        if (isDestroyed || isFinishing) return
        val active = phase != RecordingPhase.IDLE
        shutter.recording = active
        recChip.visibility = if (active) View.VISIBLE else View.GONE
        recChip.text = (if (phase == RecordingPhase.PAUSED) "❚❚  " else if (quickRecording.locked) "🔒  " else "●  ") + CameraMath.formatDuration(seconds)
        recChip.background = Nova.pill(if (phase == RecordingPhase.PAUSED) Nova.SURFACE else Nova.REC, dp(14f))
        topBar.visibility = if (active) View.INVISIBLE else View.VISIBLE
        modeStrip.isEnabled = !active
        modeStrip.animate().alpha(if (active) 0f else 1f).setDuration(Nova.FAST).start()
        thumbnail.animate().alpha(if (active) 0f else 1f).setDuration(Nova.FAST).start()
        thumbnail.isEnabled = !active
        flipButton.icon = when (phase) {
            RecordingPhase.RECORDING -> Icon.PAUSE
            RecordingPhase.PAUSED -> Icon.RESUME
            RecordingPhase.IDLE -> Icon.FLIP
        }
        if (active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onRecordingFinished(uri: Uri?, error: String?) {
        if (isDestroyed || isFinishing) {
            quickRecording.finished()
            quickReturnMode = null
            return
        }
        onRecording(RecordingPhase.IDLE, 0)
        if (settings.shutterSound) sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
        if (error != null) hint.show(error, 4000)
        if (uri != null) showMedia(uri, "video/mp4", animate = true)
        lastInteraction = SystemClock.elapsedRealtime()
        if (quickReturnMode != null) {
            quickRecording.finished()
            // Evita outro bind dentro do callback Finalize do controlador.
            handler.post { restoreQuickMode() }
        }
    }

    override fun onMessage(text: String) = hint.show(text)

    // ------------------------------------------------------------------ gestos do visor

    override fun onTap(x: Float, y: Float) {
        if (!camera.ready) return
        lockChip.visibility = View.GONE
        overlay.showFocusAt(x, y)
        camera.focusAt(x, y, lock = false)
    }

    override fun onLongPress(x: Float, y: Float) {
        if (!camera.ready) return
        overlay.showFocusAt(x, y)
        camera.focusAt(x, y, lock = true)
    }

    override fun onExposureDragStart() {
        if (camera.manual.manualExposure && camera.mode.isPro) {
            hint.show("Exposição manual ativa: ajuste ISO e velocidade no painel Pro", 2000)
        }
        evDragStart = camera.exposureIndex
    }

    override fun onExposureDrag(totalPx: Float) {
        val s = cameraState ?: return
        if (s.evMax <= s.evMin) return
        val startEv = CameraMath.evForIndex(evDragStart, s.evStepNum, s.evStepDen)
        val ev = startEv + totalPx / dp(EV_DRAG_DP_PER_EV)
        val idx = CameraMath.indexForEv(ev, s.evStepNum, s.evStepDen, s.evMin, s.evMax)
        val before = camera.exposureIndex
        camera.setExposureIndex(idx)
        if (idx != before && (idx == 0 || idx == s.evMin || idx == s.evMax)) Haptics.tick(overlay)
        val fraction = (idx - s.evMin).toFloat() / (s.evMax - s.evMin)
        overlay.showExposure(fraction, CameraMath.formatEv(CameraMath.evForIndex(idx, s.evStepNum, s.evStepDen)), keep = true)
    }

    override fun onExposureDragEnd() {
        val s = cameraState ?: return
        if (s.evMax <= s.evMin) return
        val idx = camera.exposureIndex
        val fraction = (idx - s.evMin).toFloat() / (s.evMax - s.evMin)
        overlay.showExposure(fraction, CameraMath.formatEv(CameraMath.evForIndex(idx, s.evStepNum, s.evStepDen)), keep = false)
    }

    override fun onPinch(scaleFactor: Float) = camera.zoomBy(scaleFactor)

    override fun onPinchEnd() {}

    // ------------------------------------------------------------------ energia e temperatura

    private fun onHeat(h: DeviceHeat) {
        camera.onHeat(h)
        ProcessingQueue.heat = h
        heatChip.visibility = if (ThermalPolicy.showBadge(h)) View.VISIBLE else View.GONE
        heatChip.text = ThermalPolicy.badgeText(h)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastInteraction = SystemClock.elapsedRealtime()
        return super.dispatchTouchEvent(ev)
    }

    private val idleCheck = object : Runnable {
        override fun run() {
            val timeout = settings.idleTimeoutSeconds
            val idleFor = SystemClock.elapsedRealtime() - lastInteraction
            if (timeout > 0 && !idlePaused && !camera.isRecording && countdown == 0 && idleFor > timeout * 1000L) {
                idlePaused = true
                camera.pause()
                idleOverlay.visibility = View.VISIBLE
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            handler.postDelayed(this, IDLE_CHECK_MS)
        }
    }

    private fun resumeFromIdle() {
        if (!idlePaused) return
        idlePaused = false
        idleOverlay.visibility = View.GONE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        camera.resume()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (settings.volumeKeyShutter && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            lastInteraction = SystemClock.elapsedRealtime()
            if (idlePaused) { resumeFromIdle(); return true }
            if (event.repeatCount == 0) onShutter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (settings.volumeKeyShutter && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }

    // ------------------------------------------------------------------ galeria

    private fun showMedia(uri: Uri, mime: String, animate: Boolean) {
        lastMedia = uri to mime
        io.execute {
            val bmp = runCatching { contentResolver.loadThumbnail(uri, Size(192, 192), null) }.getOrNull()
            handler.post { if (!isDestroyed) thumbnail.setImage(bmp, animate) }
        }
    }

    /** Última mídia criada pelo próprio app (não exige permissão de leitura). */
    private fun loadLatestMedia() {
        io.execute {
            val found = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "image/jpeg",
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "video/mp4",
            ).mapNotNull { (base, mime) ->
                runCatching {
                    contentResolver.query(base, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED),
                        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE ?",
                        arrayOf("%Nova Camera%", "%Originais%"),
                        "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                        if (c.moveToFirst()) Triple(Uri.withAppendedPath(base, c.getLong(0).toString()), mime, c.getLong(1)) else null
                    }
                }.getOrNull()
            }.maxByOrNull { it.third }
            if (found != null) handler.post { if (!isDestroyed && lastMedia == null) showMedia(found.first, found.second, animate = false) }
        }
    }

    private fun openReview() {
        val (id, uri) = lastSession ?: return hint.show("Sem comparação para esta foto (ela não foi ajustada)", 1800)
        if (lastMedia?.first != uri) return hint.show("Sem comparação para esta foto", 1400)
        Haptics.heavy(thumbnail)
        startActivity(Intent(this, ReviewActivity::class.java).putExtra(ReviewActivity.EXTRA_SESSION, id))
    }

    private fun openLastMedia() {
        val (uri, mime) = lastMedia ?: return hint.show("Nenhuma captura ainda", 1400)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { Log.w(TAG, "Sem visualizador para $uri", it); hint.show("Nenhum app de galeria disponível") }
    }

    // ------------------------------------------------------------------ permissões

    private fun hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionOverlay() {
        permissionOverlay.visibility = View.VISIBLE
    }

    private fun requestCameraAgain() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) askCamera.launch(Manifest.permission.CAMERA)
        else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun onRestart() {
        super.onRestart()
        if (permissionOverlay.visibility == View.VISIBLE && hasPermission(Manifest.permission.CAMERA)) startCamera()
    }

    companion object {
        private const val TAG = "NovaCamera"
        private const val TOP_BAR_DP = 52
        private val PRIMARY_MODES = CaptureMode.entries.filter { it.primary }
        private val EXTRA_MODES = CaptureMode.entries.filterNot { it.primary }
        private const val IDLE_CHECK_MS = 10_000L
        private const val ENERGY_EVERY_MS = 10_000L
        /** Auxílios do visor: 2 medições por segundo; 1 por segundo se copiar a prévia ficar caro. */
        private const val AID_EVERY_MS = 500L
        /** Contraluz automática: medição a cada 0,65 s (passos de até 1/3 EV, sem saltos). */
        private const val BACKLIGHT_EVERY_MS = 650L
        private const val AID_SLOW_MS = 1_000L
        private const val AID_SLOW_COPY_MS = 30L
        /** Largura da análise (pontos); o estouro e o realce são desenhados nessa grade. */
        private const val AID_WIDTH = 240
        /** Arrastar ~80dp na horizontal = 1 EV. */
        private const val EV_DRAG_DP_PER_EV = 80f
    }

}



