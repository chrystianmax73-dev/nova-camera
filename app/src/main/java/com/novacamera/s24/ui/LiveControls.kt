package com.novacamera.s24.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.novacamera.s24.core.CameraMath
import kotlin.math.roundToInt

/**
 * Controles criativos ao vivo, acima dos modos: Desfoque (Retrato), Exposição, Tom e Temperatura.
 * Um toque abre a régua do controle; outro toque fecha; toque longo volta ao neutro. O valor de
 * cada chip é o mesmo que a foto vai usar.
 */
class LiveControls(context: Context) : LinearLayout(context) {
    enum class Kind(val title: String) { BLUR("Desfoque"), EV("Exposição"), TONE("Tom"), WARMTH("Temperatura") }

    interface Listener {
        fun onBlur(v: Float)
        fun onEv(index: Int)
        fun onTone(v: Float)
        fun onWarmth(v: Float)
    }

    var listener: Listener? = null

    private val note = TextView(context).apply {
        setTextColor(Nova.TEXT_MUTED); textSize = 11f; gravity = Gravity.CENTER; visibility = View.GONE
    }
    private val ruler = RulerView(context).apply { visibility = View.GONE }
    private val chipRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
    private val chips = Kind.entries.associateWith { k -> TextChip(context).apply { contentDescription = k.title } }
    private var open: Kind? = null

    private var blur = 0.6f; private var tone = 0f; private var warmth = 0f
    private var evIndex = 0; private var evMin = 0; private var evMax = 0; private var evNum = 1; private var evDen = 3
    private var available = setOf<Kind>()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(note, LayoutParams(-2, -2))
        addView(ruler, LayoutParams(-1, dpi(52)))
        addView(chipRow, LayoutParams(-1, -2))
        chips.forEach { (k, chip) ->
            chipRow.addView(chip, LayoutParams(-2, -2).apply { marginStart = dpi(4); marginEnd = dpi(4) })
            chip.setOnClickListener { Haptics.tap(chip); toggle(k) }
            chip.setOnLongClickListener { Haptics.heavy(chip); reset(k); true }
        }
        ruler.onIndex = { i -> onRuler(i) }
    }

    /** Quais controles valem nesta sessão (ex.: Desfoque só no Retrato; nenhum em extensões do fabricante). */
    fun configure(kinds: Set<Kind>, blur: Float, tone: Float, warmth: Float, evIndex: Int, evMin: Int, evMax: Int, evNum: Int, evDen: Int) {
        available = kinds
        this.blur = blur; this.tone = tone; this.warmth = warmth
        this.evIndex = evIndex; this.evMin = evMin; this.evMax = evMax; this.evNum = evNum; this.evDen = evDen
        chips.forEach { (k, c) -> c.visibility = if (k in kinds) View.VISIBLE else View.GONE }
        if (open !in kinds) close()
        visibility = if (kinds.isEmpty()) View.GONE else View.VISIBLE
        refresh()
    }

    fun setEvIndex(i: Int) { evIndex = i; refresh() }

    fun setNote(text: String?) { note.text = text ?: ""; note.visibility = if (text == null) View.GONE else View.VISIBLE }

    private fun toggle(k: Kind) { if (open == k) close() else { open = k; ruler.visibility = View.VISIBLE; refresh() } }

    private fun close() { open = null; ruler.visibility = View.GONE; refresh() }

    private fun reset(k: Kind) {
        when (k) {
            Kind.BLUR -> { blur = 0.6f; listener?.onBlur(blur) }
            Kind.EV -> { evIndex = 0.coerceIn(evMin, evMax); listener?.onEv(evIndex) }
            Kind.TONE -> { tone = 0f; listener?.onTone(0f) }
            Kind.WARMTH -> { warmth = 0f; listener?.onWarmth(0f) }
        }
        refresh()
    }

    private fun onRuler(i: Int) {
        when (open) {
            Kind.BLUR -> { blur = i / 10f; listener?.onBlur(blur) }
            Kind.EV -> { evIndex = evMin + i; listener?.onEv(evIndex) }
            Kind.TONE -> { tone = (i - 5) / 5f; listener?.onTone(tone) }
            Kind.WARMTH -> { warmth = (i - 5) / 5f; listener?.onWarmth(warmth) }
            null -> {}
        }
        refreshChips()
    }

    private fun ev(i: Int) = if (evDen == 0) 0.0 else i * evNum.toDouble() / evDen
    private fun step(v: Float) = (v * 5).roundToInt().let { if (it > 0) "+$it" else if (it < 0) "−${-it}" else "0" }

    private fun refresh() {
        refreshChips()
        when (open) {
            Kind.BLUR -> ruler.set(List(11) { if (it == 0) "Sem desfoque" else "${it * 10}%" }, (blur * 10).roundToInt())
            Kind.EV -> ruler.set((evMin..evMax).map { CameraMath.formatEv(ev(it)) + " EV" }, evIndex - evMin)
            Kind.TONE -> ruler.set(List(11) { step((it - 5) / 5f) }, (tone * 5).roundToInt() + 5)
            Kind.WARMTH -> ruler.set(List(11) { step((it - 5) / 5f) }, (warmth * 5).roundToInt() + 5)
            null -> {}
        }
    }

    private fun refreshChips() {
        chips.forEach { (k, c) ->
            val value = when (k) {
                Kind.BLUR -> "${(blur * 100).roundToInt()}%"
                Kind.EV -> CameraMath.formatEv(ev(evIndex))
                Kind.TONE -> step(tone)
                Kind.WARMTH -> step(warmth)
            }
            val changed = when (k) { Kind.BLUR -> false; Kind.EV -> evIndex != 0; Kind.TONE -> tone != 0f; Kind.WARMTH -> warmth != 0f }
            c.text = if (changed || k == Kind.BLUR) "${k.title} $value" else k.title
            c.active = open == k
        }
    }
}
