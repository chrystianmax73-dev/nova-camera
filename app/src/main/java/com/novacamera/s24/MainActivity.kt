package com.novacamera.s24

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Nova Camera: protótipo de captura local. Recursos OEM dependem de cada firmware.
 * Não implementa super-resolução, segmentação própria nem IA generativa.
 */
class MainActivity : ComponentActivity() {
    private enum class Mode { FOTO, RETRATO, HDR, NOITE, VIDEO }
    private var mode = Mode.FOTO
    private var isFront = false
    private var currentZoom = 1f
    private var blurPercent = 65
    private var askedForMicrophone = false

    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var shutter: TextView
    private lateinit var modes: LinearLayout
    private lateinit var zooms: LinearLayout
    private lateinit var blurSlider: SeekBar
    private lateinit var blurTitle: TextView

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var activeCamera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val handler = Handler(Looper.getMainLooper())
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val thermalTask = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 10_000L)
        }
    }

    private val askCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) initializeCamera() else toast("Autorize a câmera nas configurações")
    }
    private val askAudio = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startVideo() /* Se negado, grava sem áudio. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(makeInterface())
        updateControls()
        handler.post(thermalTask)
        if (hasPermission(Manifest.permission.CAMERA)) initializeCamera()
        else askCamera.launch(Manifest.permission.CAMERA)
    }

    private fun makeInterface(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val point = meteringPointFactory.createPoint(event.x, event.y)
                    activeCamera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                }
                true
            }
        }
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0xBB070D19.toInt())
        }
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBar.addView(text("NOVA  CAMERA", 17f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, dp(46), 1f))
        val diagnostic = pill("DIAGNÓSTICO") { shareDiagnostic() }
        titleBar.addView(diagnostic)
        top.addView(titleBar)
        status = text("Inicializando…", 12f, 0xFFD1E7F7.toInt())
        top.addView(status)
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(14), dp(10), dp(20))
            setBackgroundColor(0xEE070D19.toInt())
        }
        zooms = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val zoomScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(zooms)
        }
        bottom.addView(zoomScroll, LinearLayout.LayoutParams(-1, dp(50)))

        modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val modeScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(modes)
        }
        bottom.addView(modeScroll, LinearLayout.LayoutParams(-1, dp(58)))

        blurTitle = text("Desfoque: 65%", 12f, Color.WHITE)
        blurTitle.gravity = Gravity.CENTER
        bottom.addView(blurTitle)
        blurSlider = SeekBar(this).apply {
            max = 100
            progress = blurPercent
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, p: Int, fromUser: Boolean) {
                    blurPercent = p
                    blurTitle.text = "Desfoque: $p%"
                    if (fromUser && mode == Mode.RETRATO) {
                        activeCamera?.cameraControl?.let {
                            extensions?.getCameraExtensionsControl(it)?.setExtensionStrength(p)
                        }
                    }
                }
                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
        }
        bottom.addView(blurSlider, LinearLayout.LayoutParams(-1, dp(35)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(pill("↻") {
            if (activeRecording == null) {
                isFront = !isFront
                currentZoom = 1f
                bindCamera()
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        shutter = text("●", 49f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Fotografar"
            background = rounded(Color.WHITE, 42)
            setTextColor(0xFF0A101C.toInt())
            setOnClickListener {
                if (mode == Mode.VIDEO) {
                    if (activeRecording == null) startVideo() else activeRecording?.stop()
                } else capturePhoto()
            }
        }
        controls.addView(shutter, LinearLayout.LayoutParams(dp(76), dp(76)))
        controls.addView(text("LOCAL • PRIVADO", 10f, 0xFF9AACBD.toInt()).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(controls)
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        return root
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = sp
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(color)
    }

    private fun pill(label: String, onTap: () -> Unit) = text(label, 12f, Color.WHITE, true).apply {
        setPadding(dp(14), 0, dp(14), 0)
        gravity = Gravity.CENTER
        background = rounded(0xCC283849.toInt(), 24)
        setOnClickListener { onTap() }
    }

    private fun updateControls() {
        modes.removeAllViews()
        Mode.entries.forEach { option ->
            val selected = mode == option
            val b = pill(option.name) {
                if (activeRecording == null && mode != option) {
                    mode = option
                    bindCamera()
                }
            }
            b.setTextColor(if (selected) 0xFF85DFFF.toInt() else Color.WHITE)
            val lp = LinearLayout.LayoutParams(-2, dp(42))
            lp.setMargins(dp(4), 0, dp(4), 0)
            modes.addView(b, lp)
        }
        shutter.contentDescription = when {
            activeRecording != null -> "Parar gravação"
            mode == Mode.VIDEO -> "Iniciar gravação"
            else -> "Capturar foto"
        }
        shutter.text = if (mode == Mode.VIDEO) "■" else "●"
        shutter.setTextColor(if (mode == Mode.VIDEO) 0xFFE32F52.toInt() else 0xFF0A101C.toInt())
        updateStatus()
    }

    private fun initializeCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                provider = future.get()
                val e = ExtensionsManager.getInstanceAsync(this, future.get())
                e.addListener({
                    extensions = try { e.get() } catch (ex: Exception) {
                        Log.w(TAG, "Extensões OEM indisponíveis", ex)
                        null
                    }
                    bindCamera()
                }, ContextCompat.getMainExecutor(this))
            } catch (ex: Exception) {
                Log.e(TAG, "Falha na inicialização CameraX", ex)
                toast("Falha na câmera: ${ex.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val p = provider ?: return
        if (activeRecording != null) return
        try {
            p.unbindAll()
            val base = if (isFront) CameraSelector.DEFAULT_FRONT_CAMERA
                       else CameraSelector.DEFAULT_BACK_CAMERA
            val extensionMode = when (mode) {
                Mode.RETRATO -> ExtensionMode.BOKEH
                Mode.HDR -> ExtensionMode.HDR
                Mode.NOITE -> ExtensionMode.NIGHT
                else -> ExtensionMode.NONE
            }
            if (extensionMode != ExtensionMode.NONE &&
                extensions?.isExtensionAvailable(base, extensionMode) != true) {
                mode = Mode.FOTO
                toast("Modo OEM indisponível nesta câmera. Usando Foto.")
            }
            val activated = when (mode) {
                Mode.RETRATO -> ExtensionMode.BOKEH
                Mode.HDR -> ExtensionMode.HDR
                Mode.NOITE -> ExtensionMode.NIGHT
                else -> ExtensionMode.NONE
            }
            val chosen = if (activated != ExtensionMode.NONE)
                extensions!!.getExtensionEnabledCameraSelector(base, activated) else base
            val usePreview = Preview.Builder().build().also {
                it.setSurfaceProvider(preview.surfaceProvider)
            }
            imageCapture = null
            videoCapture = null
            if (mode == Mode.VIDEO) {
                val qualities = if (thermalLimit())
                    listOf(Quality.FHD, Quality.HD, Quality.SD)
                else listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.fromOrderedList(qualities))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                activeCamera = p.bindToLifecycle(this, chosen, usePreview, videoCapture)
            } else {
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                activeCamera = p.bindToLifecycle(this, chosen, usePreview, imageCapture)
            }
            val hasStrength = activated == ExtensionMode.BOKEH &&
                activeCamera?.let {
                    extensions?.getCameraExtensionsInfo(it.cameraInfo)?.isExtensionStrengthAvailable
                } == true
            blurTitle.visibility = if (hasStrength) View.VISIBLE else View.GONE
            blurSlider.visibility = if (hasStrength) View.VISIBLE else View.GONE
            if (hasStrength) activeCamera?.cameraControl?.let {
                extensions?.getCameraExtensionsControl(it)?.setExtensionStrength(blurPercent)
            }
            val zoomState = activeCamera?.cameraInfo?.zoomState?.value
            val min = zoomState?.minZoomRatio ?: 1f
            val max = zoomState?.maxZoomRatio ?: 1f
            currentZoom = currentZoom.coerceIn(min, max)
            activeCamera?.cameraControl?.setZoomRatio(currentZoom)
            populateZoom(min, max)
            updateControls()
        } catch (ex: Exception) {
            Log.e(TAG, "Erro ao configurar câmera", ex)
            toast("Não foi possível ativar este modo: ${ex.message}")
        }
    }

    private fun populateZoom(min: Float, max: Float) {
        zooms.removeAllViews()
        val options = listOf(0.6f, 1f, 3f, 5f, 10f, 20f, 30f)
        options.forEach { value ->
            val b = pill("${value}×") {
                currentZoom = value.coerceIn(min, max)
                activeCamera?.cameraControl?.setZoomRatio(currentZoom)
                populateZoom(min, max)
                updateStatus()
            }
            b.alpha = if (value in min..max) 1f else 0.28f
            b.isEnabled = value in min..max
            b.setTextColor(if (value == currentZoom) 0xFF8BDFFF.toInt() else Color.WHITE)
            val lp = LinearLayout.LayoutParams(-2, dp(42))
            lp.setMargins(dp(4), 0, dp(4), 0)
            zooms.addView(b, lp)
        }
    }

    private fun capturePhoto() {
        val cap = imageCapture ?: return toast("A câmera ainda não está pronta")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "NOVA_${timestamp()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Nova Camera")
        }
        val output = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
        cap.takePicture(output, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    toast("Foto salva na galeria")
                }
                override fun onError(exception: ImageCapture.ImageCaptureException) {
                    Log.e(TAG, "Falha foto", exception)
                    toast("Erro ao salvar foto: ${exception.message}")
                }
            })
    }

    private fun startVideo() {
        val cap = videoCapture ?: return toast("A câmera ainda não está pronta")
        if (activeRecording != null) return
        val mic = hasPermission(Manifest.permission.RECORD_AUDIO)
        if (!mic && !askedForMicrophone) {
            askedForMicrophone = true
            askAudio.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "NOVA_${timestamp()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Nova Camera")
        }
        val output = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()
        try {
            var pending = cap.output.prepareRecording(this, output)
            if (mic) pending = pending.withAudioEnabled()
            activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        updateControls()
                        toast("Gravando ${if (mic) "com áudio" else "sem áudio"}")
                    }
                    is VideoRecordEvent.Finalize -> {
                        activeRecording = null
                        updateControls()
                        if (event.hasError()) {
                            Log.e(TAG, "Finalize vídeo: ${event.error}", event.cause)
                            toast("Falha ao finalizar vídeo (${event.error})")
                        } else toast("Vídeo salvo na galeria")
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Falha vídeo", ex)
            activeRecording = null
            toast("Erro ao gravar: ${ex.message}")
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun thermalLimit(): Boolean =
        power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE ||
        power.isPowerSaveMode || batteryPercent() in 0..14

    private fun batteryPercent(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    private fun updateStatus() {
        if (!::status.isInitialized) return
        val thermal = power.currentThermalStatus
        val state = if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) "quente"
                    else "normal"
        val percent = batteryPercent()
        status.text = "${mode.name} · ${currentZoom}× · bateria ${if (percent >= 0) "$percent%" else "?"} · térmico $state" +
            if (activeRecording != null) " · ● REC" else ""
    }

    private fun shareDiagnostic() {
        val ext = buildString {
            val manager = extensions
            if (manager == null) appendLine("Extensões OEM: indisponíveis")
            else listOf(CameraSelector.DEFAULT_BACK_CAMERA to "traseira",
                        CameraSelector.DEFAULT_FRONT_CAMERA to "frontal").forEach { (selector, label) ->
                appendLine("Extensões $label:")
                listOf("Retrato" to ExtensionMode.BOKEH,
                       "HDR" to ExtensionMode.HDR,
                       "Noite" to ExtensionMode.NIGHT).forEach { (name, type) ->
                    val yes = try { manager.isExtensionAvailable(selector, type) }
                              catch (_: Exception) { false }
                    appendLine("- $name: $yes")
                }
            }
        }
        val report = CameraDiagnostics.createReport(this) + "\n" + ext
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Nova Camera - relatório técnico")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(send, "Compartilhar diagnóstico"))
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        handler.removeCallbacks(thermalTask)
        activeRecording?.stop()
        activeRecording = null
        super.onDestroy()
    }

    companion object { private const val TAG = "NovaCamera" }
}
