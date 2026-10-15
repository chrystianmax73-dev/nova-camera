package com.novacamera.s24

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.novacamera.s24.core.ThermalPolicy
import com.novacamera.s24.device.DeviceMonitor
import com.novacamera.s24.settings.NovaSettings
import com.novacamera.s24.settings.HdrOption
import com.novacamera.s24.processing.Signature
import com.novacamera.s24.processing.SignaturePosition
import com.novacamera.s24.processing.SignatureSize
import com.novacamera.s24.settings.VideoQualityOption
import com.novacamera.s24.ui.Nova
import com.novacamera.s24.ui.NovaIconView
import com.novacamera.s24.ui.dp
import com.novacamera.s24.ui.dpi
import java.util.concurrent.Executors

/** Base visual compartilhada pelas telas secundárias (fundo, cabeçalho, áreas seguras). */
abstract class NovaScreen : ComponentActivity() {
    protected lateinit var content: LinearLayout

    protected fun buildScreen(title: String, trailing: View? = null) {
        enableEdgeToEdge()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Nova.BG) }
        val header = FrameLayout(this)
        header.addView(NovaIconView(this, NovaIconView.Icon.CLOSE).apply {
            contentDescription = "Voltar"
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(dpi(48), dpi(48), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dpi(8) })
        header.addView(TextView(this).apply {
            text = title; setTextColor(Nova.TEXT); textSize = 18f; typeface = Nova.MEDIUM
        }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        trailing?.let { header.addView(it, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dpi(16) }) }
        root.addView(header, LinearLayout.LayoutParams(-1, dpi(56)))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16), dpi(4), dpi(16), dpi(24))
        }
        root.addView(ScrollView(this).apply { addView(content); isFillViewport = true }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
    }
}

/** Configurações agrupadas por tarefa. Cada opção aqui altera um comportamento real. */
class SettingsActivity : NovaScreen() {
    private lateinit var settings: NovaSettings
    private lateinit var monitor: DeviceMonitor
    private var deviceText: TextView? = null
    private var group: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = NovaSettings(this)
        buildScreen("Configurações")
        monitor = DeviceMonitor(this) { h -> deviceText?.text = ThermalPolicy.describe(h) }

        section("Foto")
        switch("Salvar selfie como visualizada", "A foto frontal mantém o mesmo lado da pré-visualização (sem recodificar a imagem).",
            { settings.mirrorFrontPhoto }, { settings.mirrorFrontPhoto = it })
        info("Resolução", "Escolhida por lente no botão de megapixels da barra superior, entre as resoluções que cada câmera " +
            "realmente libera a outros apps. O que o fabricante bloqueia aparece como bloqueado ao tocar no botão.")
        switch("Disparo instantâneo", "Dispara com latência mínima (sem esperar o ajuste de foco/exposição da foto). " +
            "O diagnóstico mostra o atraso medido com e sem, para comparar.", { settings.instantShutter }, { settings.instantShutter = it })
        switch("Grade de composição", null, { settings.grid }, { settings.grid = it })
        switch("Som do obturador", null, { settings.shutterSound }, { settings.shutterSound = it })
        switch("Tecla de volume fotografa", "Também inicia e para a gravação no modo Vídeo.",
            { settings.volumeKeyShutter }, { settings.volumeKeyShutter = it })

        section("Foto Nova")
        switch("Processamento automático", "Cada foto é analisada com proteções de contraste e textura. O ajuste é " +
            "adaptativo. Fica uma foto pronta na galeria. A comparação Original × Nova é " +
            "opcional: toque e segure na miniatura.", { settings.autoProcess }, { settings.autoProcess = it })
        switch("Guardar originais", "Quando a foto é ajustada, o JPEG original da câmera (sem recompressão) fica em " +
            "Pictures › Nova Camera › Originais. Desligado: a comparação usa uma cópia privada temporária, " +
            "limitada às quatro últimas fotos processadas.", { settings.keepOriginals }, { settings.keepOriginals = it })

        switch("Controles criativos ao vivo", "Tom e temperatura de cor na faixa acima dos modos, aplicados na tela " +
            "e na foto com a mesma conta. Valem nos modos Foto, Retrato, Noturno e Food do motor Nova, em qualquer lente.",
            { settings.liveControls }, { settings.liveControls = it })
        switch("Prévia do desfoque no Retrato", "O desfoque aparece na tela antes da foto, com a intensidade da régua. " +
            "É uma prévia em resolução reduzida; a foto passa pelo processamento completo. Desligue se o aparelho esquentar.",
            { settings.portraitLivePreview }, { settings.portraitLivePreview = it })

        section("Assinatura")
        switch("Assinatura Nova Camera", "\"NOVA CAMERA · Shot on ${Signature.deviceName()}\" no canto da foto final. " +
            "Nunca é aplicada ao original.",
            { settings.signatureEnabled }, { settings.signatureEnabled = it })
        switch("Dados da captura", "Distância focal equivalente, abertura, obturador e ISO gravados pela câmera " +
            "nesta foto (ex.: 24 mm   f/1.7   1/120 s   ISO 50). Dado que a câmera não informa não aparece.",
            { settings.signatureCaptureInfo }, { settings.signatureCaptureInfo = it })
        switch("Também nos vídeos", "Vídeo e Vídeo Pro (câmera lenta e timelapse não). No vídeo frontal espelhado a " +
            "assinatura não é aplicada, para não sair invertida.", { settings.signatureVideo }, { settings.signatureVideo = it })
        choice("Posição", null, SignaturePosition.entries.map { it.label }, { settings.signaturePosition.ordinal }) {
            settings.signaturePosition = SignaturePosition.entries[it]
        }
        choice("Tamanho", null, SignatureSize.entries.map { it.label }, { settings.signatureSize.ordinal }) {
            settings.signatureSize = SignatureSize.entries[it]
        }
        val opacities = listOf(1f, 0.85f, 0.65f, 0.45f)
        choice("Transparência", null, listOf("Opaca", "Leve", "Média", "Alta"),
            { opacities.indexOfFirst { kotlin.math.abs(it - settings.signatureOpacity) < 0.02f }.coerceAtLeast(0) }) {
            settings.signatureOpacity = opacities[it]
        }

        section("Avançado")
        val aiOk = com.novacamera.s24.processing.NcnnDenoiser.available(this)
        switch("Redução de ruído por IA", if (aiOk)
            "Noturno e Retrato: a foto sai na hora e a IA limpa o ruído em segundo plano, trocando a " +
                "mesma foto na galeria quando termina. Para ao tirar outra foto, com o aparelho quente ou " +
                "em economia de bateria; aí a foto fica como saiu. Compare pelo toque longo na miniatura."
            else "Indisponível nesta versão: o modelo não foi incluído no APK.",
            { settings.aiDenoise && aiOk }, { settings.aiDenoise = it && aiOk })
        choice("HDR da FOTO", "Automático: só captura exposições extras quando a cena tem altas luzes estouradas ou " +
            "sombras profundas, e só funde onde os quadros coincidem. Fabricante: quando disponível nesta câmera.",
            HdrOption.entries.map { it.label }, { settings.hdr.ordinal }) { settings.hdr = HdrOption.entries[it] }
        switch("Retrato do fabricante", "Usa o desfoque da Samsung quando disponível. Ele costuma suavizar a pele; " +
            "o Retrato Nova não suaviza.", { settings.oemPortrait }, { settings.oemPortrait = it })
        switch("Noturno do fabricante", "Usa a Noite da Samsung quando disponível, em vez da fusão própria.",
            { settings.oemNight }, { settings.oemNight = it })

        section("Vídeo")
        choice("Qualidade", "Aplicada se a câmera suportar; caso contrário usa a próxima disponível.",
            VideoQualityOption.entries.map { it.label }, { settings.videoQuality.ordinal }) {
            settings.videoQuality = VideoQualityOption.entries[it]
        }
        choice("Quadros por segundo", "60 fps só é usado quando a câmera aceita a combinação escolhida.",
            listOf("30 fps", "60 fps"), { if (settings.videoFps == 60) 1 else 0 }) { settings.videoFps = if (it == 1) 60 else 30 }
        switch("Estabilização", "Usa a estabilização anunciada pela câmera (pode recortar levemente as bordas).",
            { settings.videoStabilization }, { settings.videoStabilization = it })
        switch("Espelhar vídeo da câmera frontal", "Grava como na pré-visualização. Desative para gravar textos legíveis.",
            { settings.mirrorFrontVideo }, { settings.mirrorFrontVideo = it })
        switch("Gravar áudio", "Sem permissão do microfone, o vídeo é gravado sem som e o app avisa.",
            { settings.recordAudio }, { settings.recordAudio = it })
        val tlMax = com.novacamera.s24.core.TimelapsePlan.MAX_DURATIONS_S
        choice("Duração do timelapse", "A captura para sozinha após o tempo escolhido. O intervalo entre quadros " +
            "fica no botão da barra superior do modo Timelapse.",
            listOf("Até parar", "1 minuto", "5 minutos", "15 minutos", "30 minutos"),
            { tlMax.indexOf(settings.timelapseMaxSeconds).takeIf { it >= 0 } ?: 0 }) { settings.timelapseMaxSeconds = tlMax[it] }
        switch("Exposição estável no zoom", "Sem ponto escolhido, o vídeo mede a exposição pelo centro: ao aproximar, " +
            "áreas claras que saem do quadro não fazem a imagem saltar de brilho.",
            { settings.videoCenterMetering }, { settings.videoCenterMetering = it })

        section("Foco e exposição")
        switch("Priorizar rostos", "Quando a câmera suporta, foco e exposição favorecem rostos. Um toque no visor sempre prevalece.",
            { settings.facePriority }, { settings.facePriority = it })
        val holds = listOf(4, 8, 0)
        choice("Manter foco por toque", "Depois disso o foco volta a acompanhar o assunto; a exposição escolhida é preservada.",
            listOf("4 segundos", "8 segundos", "Até novo toque"), { holds.indexOf(settings.tapFocusHoldSeconds).takeIf { it >= 0 } ?: 0 }) {
            settings.tapFocusHoldSeconds = holds[it]
        }
        switch("Travar balanço de branco com AE/AF", "No toque longo, também fixa as cores.",
            { settings.lockWhiteBalanceWithAeAf }, { settings.lockWhiteBalanceWithAeAf = it })

        section("Energia e temperatura")
        val idle = listOf(60, 120, 300, 0)
        choice("Pausar câmera sem uso", "Desliga o sensor após o tempo escolhido, fora de gravações.",
            listOf("1 minuto", "2 minutos", "5 minutos", "Nunca"), { idle.indexOf(settings.idleTimeoutSeconds).takeIf { it >= 0 } ?: 1 }) {
            settings.idleTimeoutSeconds = idle[it]
        }
        deviceText = info("Estado do aparelho", ThermalPolicy.describe(monitor.current))

        section("Interface")
        switch("Retorno tátil", null, { settings.haptics }, { settings.haptics = it })

        section("Diagnóstico e privacidade")
        action("Relatório técnico da câmera", "Lentes, extensões do fabricante e limites reais deste aparelho. Compartilhamento só manual.") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        info("Privacidade", "Sem internet, conta, anúncios ou telemetria. Fotos e vídeos ficam no aparelho (Pictures/Movies › Nova Camera).")
        info("Processamento local", "Fusão, tons, retrato, profundidade e redução de ruído rodam no aparelho, com modelos " +
            "embutidos no app. Sem permissão de internet: nada é baixado nem enviado.")
        info("Licenças de terceiros", "CameraX/AndroidX, MediaPipe e seu modelo de segmentação, Depth Anything V2 Small " +
            "(Apache-2.0); ncnn (BSD-3-Clause); NAFNet (MIT); libjpeg-turbo (IJG/BSD). Nenhum código GPL.")
        info("Versão", BuildConfigInfo.version(this))
    }

    override fun onStart() { super.onStart(); monitor.start() }
    override fun onStop() { monitor.stop(); super.onStop() }

    // ---------------------------------------------------------------- construtores de linhas

    private fun section(title: String) {
        content.addView(TextView(this).apply {
            text = title.uppercase()
            setTextColor(Nova.ACCENT); textSize = 11.5f; typeface = Nova.MEDIUM; letterSpacing = 0.1f
            setPadding(dpi(6), dpi(22), 0, dpi(8))
        })
        group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Nova.pill(Nova.SURFACE, dp(18f))
            setPadding(0, dpi(4), 0, dpi(4))
        }
        content.addView(group)
    }

    private fun row(title: String, subtitle: String?, trailing: View?): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(16), dpi(12), dpi(12), dpi(12))
            minimumHeight = dpi(56)
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply { text = title; setTextColor(Nova.TEXT); textSize = 15.5f })
        subtitle?.let {
            texts.addView(TextView(this).apply {
                text = it; setTextColor(Nova.TEXT_MUTED); textSize = 12.5f; setLineSpacing(0f, 1.1f)
                setPadding(0, dpi(3), 0, 0)
            })
        }
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        trailing?.let { row.addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(12) }) }
        group?.addView(row)
        return row
    }

    private fun switch(title: String, subtitle: String?, get: () -> Boolean, set: (Boolean) -> Unit) {
        val sw = Switch(this).apply {
            isChecked = get()
            setOnCheckedChangeListener { _, v -> set(v) }
            contentDescription = title
        }
        row(title, subtitle, sw).setOnClickListener { sw.toggle() }
    }

    private fun choice(title: String, subtitle: String?, options: List<String>, current: () -> Int, set: (Int) -> Unit) {
        val value = TextView(this).apply { setTextColor(Nova.ACCENT_SOFT); textSize = 14f; typeface = Nova.MEDIUM; text = options[current()] }
        row(title, subtitle, value).setOnClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(options.toTypedArray(), current()) { d, which ->
                    set(which); value.text = options[which]; d.dismiss()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun action(title: String, subtitle: String?, onClick: () -> Unit) {
        val arrow = TextView(this).apply { text = "›"; setTextColor(Nova.TEXT_MUTED); textSize = 22f }
        row(title, subtitle, arrow).setOnClickListener { onClick() }
    }

    private fun info(title: String, text: String): TextView {
        val r = row(title, text, null)
        val texts = r.getChildAt(0) as LinearLayout
        return texts.getChildAt(1) as TextView
    }
}

/** Relatório técnico: Camera2 + visão do CameraX (extensões, vídeo) + estado térmico. */
class DiagnosticsActivity : NovaScreen() {
    private val io = Executors.newSingleThreadExecutor()
    private var report = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val share = TextView(this).apply {
            text = "Compartilhar"; setTextColor(Nova.ACCENT_SOFT); textSize = 14f; typeface = Nova.MEDIUM
            setPadding(dpi(8), dpi(8), dpi(8), dpi(8))
            setOnClickListener { shareReport() }
        }
        buildScreen("Diagnóstico", share)
        val body = TextView(this).apply {
            text = "Coletando informações…"
            setTextColor(Nova.TEXT); textSize = 12.5f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.15f)
        }
        content.addView(body)
        val heat = DeviceMonitor(this) {}
        heat.start()
        val heatText = ThermalPolicy.describe(heat.current) + (heat.headroom()?.let { "\nMargem térmica prevista (10 s): %.2f".format(it) } ?: "")
        heat.stop()

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull()
            val extFuture = provider?.let { ExtensionsManager.getInstanceAsync(this, it) }
            val build = { ext: ExtensionsManager? ->
                io.execute {
                    val extra = buildString {
                        appendLine("── Energia")
                        appendLine(heatText)
                        appendLine("\n── Visão do CameraX (o que a Nova Camera usa)")
                        if (provider != null) cameraXSummary(provider, ext)
                        else appendLine("CameraX indisponível")
                    }
                    val text = CameraDiagnostics.createReport(this, extra) + "\n── Registro de zoom desta sessão\n" +
                        synchronized(com.novacamera.s24.camera.NovaCamera.telemetry) { com.novacamera.s24.camera.NovaCamera.telemetry.report() }
                    runOnUiThread { report = text; body.text = text }
                }
            }
            if (extFuture == null) build(null)
            else extFuture.addListener({ build(runCatching { extFuture.get() }.getOrNull()) }, ContextCompat.getMainExecutor(this))
        }, ContextCompat.getMainExecutor(this))
    }

    private fun StringBuilder.cameraXSummary(provider: ProcessCameraProvider, ext: ExtensionsManager?) {
        listOf("Traseira" to CameraSelector.DEFAULT_BACK_CAMERA, "Frontal" to CameraSelector.DEFAULT_FRONT_CAMERA).forEach { (label, sel) ->
            val info = runCatching { provider.getCameraInfo(sel) }.getOrNull() ?: run { appendLine("$label: ausente"); return@forEach }
            appendLine("$label padrão:")
            val names = listOf("Retrato (BOKEH)" to ExtensionMode.BOKEH, "HDR" to ExtensionMode.HDR, "Noite" to ExtensionMode.NIGHT,
                "Auto" to ExtensionMode.AUTO, "Retoque" to ExtensionMode.FACE_RETOUCH)
            appendLine("  Extensões OEM via CameraX: " + if (ext == null) "biblioteca do fabricante ausente"
                else names.joinToString { (n, m) -> "$n=" + (if (runCatching { ext.isExtensionAvailable(sel, m) }.getOrDefault(false)) "sim" else "não") })
            val video = runCatching { Recorder.getVideoCapabilities(info) }.getOrNull()
            val qualities = video?.getSupportedQualities(DynamicRange.SDR).orEmpty().map {
                when (it) { Quality.UHD -> "4K"; Quality.FHD -> "FHD"; Quality.HD -> "HD"; Quality.SD -> "SD"; else -> it.toString() }
            }
            appendLine("  Vídeo SDR: ${qualities.joinToString().ifEmpty { "?" }} · estabilização de vídeo: ${video?.isStabilizationSupported}" +
                " · de pré-visualização: ${runCatching { Preview.getPreviewCapabilities(info).isStabilizationSupported }.getOrNull()}")
            appendLine("  Faixas de fps (CameraX): ${info.supportedFrameRateRanges.joinToString()}")
            appendLine("  Zoom: ${info.zoomState.value?.let { "${it.minZoomRatio}–${it.maxZoomRatio}" } ?: "(lido ao abrir a câmera)"}")
        }
        val all = provider.availableCameraInfos
        appendLine("Câmeras acessíveis ao CameraX: ${all.size}")
    }

    private fun shareReport() {
        if (report.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Nova Camera — relatório técnico")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(send, "Compartilhar diagnóstico"))
    }

    override fun onDestroy() { io.shutdown(); super.onDestroy() }
}

