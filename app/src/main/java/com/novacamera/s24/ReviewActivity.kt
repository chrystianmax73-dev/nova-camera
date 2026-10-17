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
        content.addView(caption("Arraste a linha para comparar o original com a versão Nova."))

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
            val ok = runCatching { ProcessingQueue.render(session, copy, t, bl, token) {} }.isSuccess
            handler.post {
                if (isDestroyed || !ok || token.cancelled) { copy.recycle(); return@post }
                compare.enhanced?.takeIf { it !== base }?.recycle()
                compare.enhanced = copy
            }
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
        worker.shutdown()
        super.onDestroy()
    }

    companion object { const val EXTRA_SESSION = "session" }
}
