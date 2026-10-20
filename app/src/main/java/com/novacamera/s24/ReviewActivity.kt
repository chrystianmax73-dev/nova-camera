package com.novacamera.s24

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.novacamera.s24.processing.CancelToken
import com.novacamera.s24.processing.JobKind
import com.novacamera.s24.processing.ProcessingQueue
import com.novacamera.s24.processing.Session
import com.novacamera.s24.ui.CompareView
import com.novacamera.s24.ui.Nova
import com.novacamera.s24.ui.dp
import com.novacamera.s24.ui.dpi
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Revisão de uma foto processada: Original × Nova lado a lado, intensidade do tratamento e
 * (no Retrato) do desfoque, reaplicados a partir da base salva — nada é acumulado.
 * "Salvar" regrava a versão Nova em resolução cheia; o original nunca é alterado.
 */
class ReviewActivity : NovaScreen() {
    private val worker = Executors.newSingleThreadExecutor()
    /** IA na região: fila própria para não travar o reajuste dos controles enquanto a rede roda. */
    private val aiWorker = Executors.newSingleThreadExecutor()
    @Volatile private var plans: ProcessingQueue.RenderPlans? = null
    @Volatile private var regionToken: CancelToken? = null
    private var aiAvailable = false
    private lateinit var aiStatus: TextView
    private lateinit var refineButton: TextView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var session: Session
    private lateinit var compare: CompareView
    private var base: Bitmap? = null
    private var tone = 1f
    private var blur = 0.6f
    @Volatile private var renderToken: CancelToken? = null
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val s = intent.getStringExtra(EXTRA_SESSION)?.let { ProcessingQueue.session(this, it) }
        if (s == null || !s.base.exists()) { finish(); return }
        session = s
        tone = s.tone; blur = s.blur
        buildScreen("Revisão · ${s.kind.label}")

        compare = CompareView(this)
        content.addView(compare, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8) })
        if (s.note.isNotEmpty()) content.addView(caption(s.note))
        content.addView(caption("Toque e segure a foto para ver o original inteiro no mesmo lugar; solte para ver a Nova (é a que está na galeria). Arraste a linha para comparar; dois dedos ampliam; toque duplo volta."))
        aiAvailable = com.novacamera.s24.processing.NcnnDenoiser.available(this) && s.meta.getProperty("banded") != "true"
        aiStatus = caption("")
        refineButton = button("Refinar foto inteira com IA") { requestRefine() }
        if (aiAvailable) {
            content.addView(aiStatus)
            content.addView(refineButton, LinearLayout.LayoutParams(-1, dpi(48)).apply { topMargin = dpi(12) })
            compare.onViewport = { rect, zoom -> onViewport(rect, zoom) }
            showRefineState()
        } else content.addView(caption(if (s.meta.getProperty("banded") == "true") "IA indisponível nesta foto (200 MP)."
            else "IA indisponível nesta versão: o modelo ou a biblioteca nativa não carregou."))

        slider(if (s.kind == JobKind.NIGHT) "Intensidade (tons e sombras)" else "Intensidade", tone) { tone = it; scheduleRender() }
        if (s.kind == JobKind.PORTRAIT && s.hasPerson) slider("Desfoque do fundo", blur) { blur = it; scheduleRender() }

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actions.addView(button("Ver na galeria") { openInGallery() }, LinearLayout.LayoutParams(0, dpi(48), 1f).apply { marginEnd = dpi(8) })
        actions.addView(button("Salvar ajuste", primary = true) { save() }, LinearLayout.LayoutParams(0, dpi(48), 1f))
        content.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(20) })
        content.addView(caption(if (s.originalUri != null) "O original sem processamento está em Pictures › Nova Camera › Originais."
                                else "O original sem processamento fica disponível aqui enquanto esta revisão existir."))

        worker.execute {
            val side = maxOf(resources.displayMetrics.widthPixels, 1080) * 3 / 2
            val o = ProcessingQueue.decodeForScreen(s.original, side)
            val b = ProcessingQueue.decodeForScreen(s.base, side)
            handler.post {
                if (isDestroyed) return@post
                base = b
                compare.original = o
                render()
            }
        }
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text; setTextColor(Nova.TEXT_MUTED); textSize = 12.5f; setLineSpacing(0f, 1.15f)
        setPadding(dpi(4), dpi(10), dpi(4), 0)
    }

    private fun slider(title: String, initial: Float, onChange: (Float) -> Unit) {
        val label = TextView(this).apply { setTextColor(Nova.TEXT); textSize = 14.5f; setPadding(dpi(4), dpi(18), 0, dpi(2)) }
        fun update(v: Float) { label.text = "$title · ${(v * 100).roundToInt()}%" }
        update(initial)
        content.addView(label)
        content.addView(SeekBar(this).apply {
            max = 100
            progress = (initial * 100).roundToInt()
            contentDescription = title
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) { update(p / 100f); onChange(p / 100f) }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }, LinearLayout.LayoutParams(-1, dpi(40)))
    }

    private fun button(text: String, primary: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 15f; typeface = Nova.MEDIUM; gravity = Gravity.CENTER
        setTextColor(if (primary) Nova.BG else Nova.TEXT)
        background = Nova.pill(if (primary) Nova.ACCENT_SOFT else Nova.SURFACE, dp(24f))
        setOnClickListener { onClick() }
    }

    private val renderRunnable = Runnable { render() }

    private fun scheduleRender() {
        handler.removeCallbacks(renderRunnable)
        handler.postDelayed(renderRunnable, 120)
    }

    /** Reaplica o acabamento numa cópia da base em resolução de tela (rápido, em segundo plano). */
    private fun render() {
        val b = base ?: return
        renderToken?.cancelled = true
        val token = CancelToken().also { renderToken = it }
        val t = tone; val bl = blur
        worker.execute {
            if (token.cancelled) return@execute
            val copy = b.copy(Bitmap.Config.ARGB_8888, true)
            val recorded = ProcessingQueue.RenderPlans()
            val ok = runCatching { ProcessingQueue.render(session, copy, t, bl, token, plans = recorded) {} }.isSuccess
            if (ok) plans = recorded
            handler.post {
                if (isDestroyed || !ok || token.cancelled) { copy.recycle(); return@post }
                compare.enhanced?.takeIf { it !== base }?.recycle()
                compare.enhanced = copy
            }
        }
    }

    private val aiDone get() = ProcessingQueue.refineStatus(this, session.id).state == ProcessingQueue.RefineState.DONE

    private fun onViewport(rect: android.graphics.RectF, zoom: Float) {
        regionToken?.cancelled = true
        handler.removeCallbacks(regionRunnable)
        if (zoom < com.novacamera.s24.core.RegionRefine.MIN_ZOOM) {
            compare.patch = null
            if (!aiDone) aiStatus.text = "Amplie ${com.novacamera.s24.core.RegionRefine.MIN_ZOOM.toInt()}× ou mais para ver a IA nesta região antes de refinar a foto inteira."
            return
        }
        if (aiDone) return
        if (session.kind == JobKind.PORTRAIT && session.hasPerson) {
            aiStatus.text = "No Retrato a prévia por região não mostra o desfoque; use \"Refinar foto inteira\"."
            return
        }
        pendingRect = rect
        handler.postDelayed(regionRunnable, 450)
    }

    private var pendingRect: android.graphics.RectF? = null
    private val regionRunnable = Runnable { pendingRect?.let { refineRegion(it) } }

    /**
     * IA só no recorte visível, em resolução cheia: o mesmo acabamento da foto inteira (planos tonais
     * registrados na prévia), com e sem a rede, para comparar. Nada é gravado.
     */
    private fun refineRegion(rect: android.graphics.RectF) {
        val recorded = plans ?: return
        val token = CancelToken().also { regionToken = it }
        val t = tone; val bl = blur
        aiStatus.text = "IA na região ampliada…"
        aiWorker.execute {
            if (token.cancelled) return@execute
            val started = android.os.SystemClock.elapsedRealtime()
            var before: Bitmap? = null; var after: Bitmap? = null
            val message = try {
                val file = session.base
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path, bounds)
                val rotation = runCatching { androidx.exifinterface.media.ExifInterface(file).rotationDegrees }.getOrDefault(0)
                val ow = if (rotation % 180 == 0) bounds.outWidth else bounds.outHeight
                val oh = if (rotation % 180 == 0) bounds.outHeight else bounds.outWidth
                // Mesmo caminho da foto inteira: Alta resolução passa pela rede em meia resolução.
                val half = ow.toLong() * oh > com.novacamera.s24.processing.NcnnDenoiser.HALF_FROM
                val box = com.novacamera.s24.core.RegionRefine.crop(rect.left, rect.top, rect.right, rect.bottom, ow, oh, half)
                    ?: error("região pequena demais")
                val src = com.novacamera.s24.core.RegionRefine.unrotated(box, rotation, ow, oh)
                @Suppress("DEPRECATION")
                val decoder = android.graphics.BitmapRegionDecoder.newInstance(file.path, false) ?: error("base ilegível")
                val raw = try {
                    decoder.decodeRegion(android.graphics.Rect(src.left, src.top, src.right, src.bottom),
                        android.graphics.BitmapFactory.Options().apply { inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888 })
                } finally { decoder.recycle() } ?: error("recorte ilegível")
                val crop = com.novacamera.s24.processing.Engine.orient(raw, rotation, false)
                before = crop.copy(Bitmap.Config.ARGB_8888, true)
                after = crop
                if (token.cancelled) throw com.novacamera.s24.processing.CancelledException()
                val ok = com.novacamera.s24.processing.NcnnDenoiser.denoiseAuto(this, crop, 0.75f, token,
                    com.novacamera.s24.core.DenoisePolicy.REGION, half = half) {}
                if (!ok) error(com.novacamera.s24.processing.NcnnDenoiser.report)
                val region = com.novacamera.s24.processing.Engine.Region(box.left, box.top, ow, oh)
                ProcessingQueue.render(session, before!!, t, bl, token, plans = recorded.at(region)) {}
                ProcessingQueue.render(session, crop, t, bl, token, plans = recorded.at(region)) {}
                val secs = (android.os.SystemClock.elapsedRealtime() - started) / 100 / 10.0
                val b = before!!; val a = after!!
                handler.post {
                    if (isDestroyed || token.cancelled) { b.recycle(); a.recycle(); return@post }
                    compare.patch = CompareView.Patch(b, a, android.graphics.RectF(box.left.toFloat() / ow, box.top.toFloat() / oh,
                        box.right.toFloat() / ow, box.bottom.toFloat() / oh))
                }
                before = null; after = null
                "IA na região: ${box.width}×${box.height} px em $secs s. Arraste a linha: Sem IA × Com IA. " +
                    "Nada foi gravado; a foto inteira só muda se você pedir abaixo."
            } catch (_: com.novacamera.s24.processing.CancelledException) { null }
            catch (e: OutOfMemoryError) { "Memória insuficiente para a prévia da IA" }
            catch (e: Exception) { "Prévia da IA indisponível: ${e.message ?: "falha"}" }
            finally { before?.recycle(); after?.recycle() }
            if (message != null) handler.post { if (!isDestroyed && !token.cancelled) aiStatus.text = message }
        }
    }

    private fun requestRefine() {
        val reason = ProcessingQueue.requestRefine(this, session)
        if (reason != null) { hintToast(reason); return }
        compare.patch = null
        showRefineState()
    }

    private val refinePoll = Runnable { showRefineState() }

    /** Estado do pedido na fila; quando termina, a base (agora com IA) volta para a tela. */
    private fun showRefineState() {
        if (isDestroyed) return
        val st = ProcessingQueue.refineStatus(this, session.id)
        when (st.state) {
            ProcessingQueue.RefineState.WAITING -> {
                refineButton.text = "IA na fila…"
                aiStatus.text = "Começa quando não houver foto processando, sem aquecimento e com bateria acima de 15% (ou carregando). " +
                    "Melhor com o celular no carregador."
            }
            ProcessingQueue.RefineState.RUNNING -> {
                refineButton.text = "Refinando com IA · ${(st.fraction * 100).roundToInt()}%"
                aiStatus.text = "Pode sair desta tela: ao terminar, a foto da galeria é trocada (o original fica em Originais)."
            }
            ProcessingQueue.RefineState.DONE -> {
                refineButton.text = "Foto refinada por IA ✓"; refineButton.isEnabled = false
                aiStatus.text = "A galeria já mostra a versão refinada."
                reloadBase()
                return
            }
            ProcessingQueue.RefineState.FAILED -> {
                refineButton.text = "Refinar foto inteira com IA"
                if (st.detail.isNotBlank()) aiStatus.text = "Última tentativa da IA não concluiu: " + st.detail.substringBefore(" · ")
            }
            ProcessingQueue.RefineState.NONE -> {
                refineButton.text = "Refinar foto inteira com IA"
                if (aiStatus.text.isNullOrEmpty()) aiStatus.text = "Amplie ${com.novacamera.s24.core.RegionRefine.MIN_ZOOM.toInt()}× ou mais para ver a IA nesta região antes de refinar a foto inteira."
                return
            }
        }
        handler.removeCallbacks(refinePoll)
        handler.postDelayed(refinePoll, 1000)
    }

    private var reloaded = false
    private fun reloadBase() {
        if (reloaded) return
        reloaded = true
        worker.execute {
            val side = maxOf(resources.displayMetrics.widthPixels, 1080) * 3 / 2
            val b = ProcessingQueue.decodeForScreen(session.base, side) ?: return@execute
            handler.post { if (isDestroyed) b.recycle() else { base = b; render() } }
        }
    }

    private fun save() {
        if (saving) return
        saving = true
        hintToast("Salvando em resolução cheia…")
        ProcessingQueue.reRender(this, session, tone, blur) { ok ->
            saving = false
            hintToast(if (ok) "Ajuste salvo" else "Não foi possível salvar o ajuste")
            if (ok) finish()
        }
    }

    private fun hintToast(text: String) = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()

    private fun openInGallery() {
        val uri = session.enhancedUri ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }

    override fun onDestroy() {
        renderToken?.cancelled = true
        regionToken?.cancelled = true
        handler.removeCallbacksAndMessages(null)
        worker.shutdown()
        aiWorker.shutdown()
        compare.patch = null
        super.onDestroy()
    }

    companion object { const val EXTRA_SESSION = "session" }
}
