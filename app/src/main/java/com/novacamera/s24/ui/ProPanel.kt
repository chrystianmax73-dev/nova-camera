package com.novacamera.s24.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.hardware.camera2.CameraMetadata
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.novacamera.s24.camera.AutoStats
import com.novacamera.s24.camera.CameraCapabilities
import com.novacamera.s24.camera.ManualControls
import com.novacamera.s24.core.CameraMath
import kotlin.math.roundToInt

/** Controles manuais reais (Camera2). Parâmetros que o sensor não expõe aparecem desativados. */
class ProPanel(context: Context) : LinearLayout(context) {
    enum class Param(val caption: String) { ISO("ISO"), SHUTTER("VEL."), FOCUS("FOCO"), WB("BALANÇO"), EV("EV") }

    interface Listener {
        fun onManual(m: ManualControls)
        fun onEv(index: Int)
    }

    var listener: Listener? = null
    private val ruler = RulerView(context)
    private val chips = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
    private val chipViews = mutableMapOf<Param, TextView>()
    private var selected: Param? = null

    private var manual = ManualControls()
    private var stats: AutoStats? = null
    private var isoValues: List<Int> = emptyList()
    private var shutterValues: List<Long> = emptyList()
    private var focusValues: List<Float> = emptyList()
    private var wbValues: List<Int> = emptyList()
    private var evMin = 0; private var evMax = 0; private var evNum = 1; private var evDen = 1; private var evIndex = 0
    private var enabledParams: Set<Param> = emptySet()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        ruler.visibility = GONE
        addView(ruler, LayoutParams(LayoutParams.MATCH_PARENT, dpi(52)))
        addView(chips, LayoutParams(LayoutParams.MATCH_PARENT, dpi(46)))
        Param.entries.forEach { p ->
            val chip = TextView(context).apply {
                gravity = Gravity.CENTER
                typeface = Nova.MEDIUM
                setTextColor(Nova.TEXT)
                setPadding(dpi(6), 0, dpi(6), 0)
                setOnClickListener { select(p) }
            }
            chipViews[p] = chip
            chips.addView(chip, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        ruler.onIndex = { onRuler(it) }
    }

    fun configure(caps: CameraCapabilities, evMin: Int, evMax: Int, stepNum: Int, stepDen: Int, evIndex: Int, m: ManualControls) {
        isoValues = caps.isoRange?.let { CameraMath.isoStops(it.lower, it.upper) }.orEmpty()
        shutterValues = caps.exposureRangeNs?.let { CameraMath.shutterStops(it.lower, it.upper) }.orEmpty()
        focusValues = if (caps.manualFocus) CameraMath.focusStops(caps.minFocusDistance) else emptyList()
        wbValues = WB_ORDER.filter { it in caps.awbModes }
        this.evMin = evMin; this.evMax = evMax; this.evNum = stepNum; this.evDen = stepDen; this.evIndex = evIndex
        manual = m
        enabledParams = buildSet {
            if (caps.manualSensor && isoValues.isNotEmpty()) add(Param.ISO)
            if (caps.manualSensor && shutterValues.isNotEmpty()) add(Param.SHUTTER)
            if (focusValues.isNotEmpty()) add(Param.FOCUS)
            if (wbValues.size > 1) add(Param.WB)
            if (evMax > evMin) add(Param.EV)
        }
        if (selected != null && selected !in enabledParams) select(null)
        refreshChips()
    }

    fun setStats(s: AutoStats) { stats = s; refreshChips() }

    fun setEvIndex(i: Int) { evIndex = i; refreshChips() }

    private fun select(p: Param?) {
        if (p != null && p !in enabledParams) return
        selected = if (selected == p) null else p
        Haptics.tick(this)
        val sel = selected
        if (sel == null) {
            ruler.visibility = GONE
        } else {
            ruler.visibility = VISIBLE
            val (labels, index) = rulerFor(sel)
            ruler.set(labels, index)
        }
        refreshChips()
    }

    private fun rulerFor(p: Param): Pair<List<String>, Int> = when (p) {
        Param.ISO -> (listOf("AUTO") + isoValues.map { it.toString() }) to (manual.iso?.let { isoValues.indexOf(it) + 1 } ?: 0)
        Param.SHUTTER -> (listOf("AUTO") + shutterValues.map(CameraMath::shutterLabel)) to
            (manual.exposureNs?.let { shutterValues.indexOf(it) + 1 } ?: 0)
        Param.FOCUS -> (listOf("AF") + focusValues.map(CameraMath::focusLabel)) to
            (manual.focusDiopters?.let { d -> focusValues.indexOfFirst { kotlin.math.abs(it - d) < 1e-3f } + 1 } ?: 0)
        Param.WB -> (listOf("AUTO") + wbValues.map(::wbLabel)) to (manual.awbMode?.let { wbValues.indexOf(it) + 1 } ?: 0)
        Param.EV -> (evMin..evMax).map { CameraMath.formatEv(CameraMath.evForIndex(it, evNum, evDen)) } to (evIndex - evMin)
    }

    private fun onRuler(index: Int) {
        when (selected ?: return) {
            Param.ISO -> manual = manual.copy(iso = isoValues.getOrNull(index - 1))
            Param.SHUTTER -> manual = manual.copy(exposureNs = shutterValues.getOrNull(index - 1))
            Param.FOCUS -> manual = manual.copy(focusDiopters = focusValues.getOrNull(index - 1))
            Param.WB -> manual = manual.copy(awbMode = wbValues.getOrNull(index - 1))
            Param.EV -> {
                evIndex = evMin + index
                listener?.onEv(evIndex)
                refreshChips()
                return
            }
        }
        listener?.onManual(manual)
        refreshChips()
    }

    /** O controlador pode completar ISO/velocidade com o valor que o automático usava. */
    fun syncManual(m: ManualControls) { manual = m; refreshChips() }

    private fun refreshChips() {
        Param.entries.forEach { p ->
            val chip = chipViews[p] ?: return@forEach
            val enabled = p in enabledParams
            val value = when (p) {
                Param.ISO -> manual.iso?.toString() ?: auto(stats?.iso?.toString())
                Param.SHUTTER -> manual.exposureNs?.let(CameraMath::shutterLabel) ?: auto(stats?.exposureNs?.let(CameraMath::shutterLabel))
                Param.FOCUS -> manual.focusDiopters?.let(CameraMath::focusLabel) ?: "AF"
                Param.WB -> manual.awbMode?.let(::wbLabel) ?: "AUTO"
                Param.EV -> CameraMath.formatEv(CameraMath.evForIndex(evIndex, evNum, evDen))
            }
            val isManual = when (p) {
                Param.ISO -> manual.iso != null
                Param.SHUTTER -> manual.exposureNs != null
                Param.FOCUS -> manual.focusDiopters != null
                Param.WB -> manual.awbMode != null
                Param.EV -> evIndex != 0
            }
            val caption = p.caption
            val sb = SpannableStringBuilder(caption).apply {
                setSpan(AbsoluteSizeSpan(dpi(9)), 0, caption.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(if (selected == p) Nova.ACCENT else Nova.TEXT_MUTED), 0, caption.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append("\n")
                val start = length
                append(if (enabled) value else "—")
                setSpan(AbsoluteSizeSpan(dpi(13)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(when {
                    !enabled -> Nova.TEXT_DISABLED
                    isManual -> Nova.ACCENT_SOFT
                    else -> Nova.TEXT
                }), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            chip.text = sb
            chip.isEnabled = enabled
            chip.contentDescription = if (enabled) "${p.caption} $value" else "${p.caption} indisponível neste sensor"
        }
    }

    private fun auto(live: String?) = if (live != null) "A·$live" else "AUTO"

    private fun wbLabel(mode: Int) = when (mode) {
        CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT -> "Sol"
        CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "Nublado"
        CameraMetadata.CONTROL_AWB_MODE_SHADE -> "Sombra"
        CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT -> "Incand."
        CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT -> "Fluor."
        CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "Fluor. q."
        CameraMetadata.CONTROL_AWB_MODE_TWILIGHT -> "Crepúsc."
        else -> "Auto"
    }

    companion object {
        private val WB_ORDER = listOf(
            CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT, CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT,
            CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT, CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT,
            CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, CameraMetadata.CONTROL_AWB_MODE_TWILIGHT,
            CameraMetadata.CONTROL_AWB_MODE_SHADE,
        )
    }
}

/** Régua horizontal com marcas; arrastar seleciona o valor sob o marcador central. */
class RulerView(context: Context) : View(context) {
    var onIndex: ((Int) -> Unit)? = null
    private var labels: List<String> = emptyList()
    private var offset = 0f       // posição contínua (em índices)
    private var index = 0
    private var downX = 0f
    private var downOffset = 0f
    private val spacing = dp(18f)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Nova.MEDIUM }

    fun set(values: List<String>, selected: Int) {
        labels = values
        index = selected.coerceIn(0, (values.size - 1).coerceAtLeast(0))
        offset = index.toFloat()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (labels.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downOffset = offset; parent?.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> {
                offset = (downOffset - (event.x - downX) / spacing).coerceIn(0f, (labels.size - 1).toFloat())
                val i = offset.roundToInt()
                if (i != index) { index = i; Haptics.tick(this); onIndex?.invoke(i) }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { offset = index.toFloat(); invalidate() }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val baseY = height - dp(10f)
        labels.forEachIndexed { i, label ->
            val x = cx + (i - offset) * spacing
            if (x < -spacing || x > width + spacing) return@forEachIndexed
            val major = i == index
            tick.color = if (major) Nova.ACCENT_SOFT else Nova.TEXT
            tick.alpha = if (major) 255 else 120
            tick.strokeWidth = dp(if (major) 2f else 1.2f)
            canvas.drawLine(x, baseY, x, baseY - dp(if (major) 14f else 8f), tick)
        }
        labels.getOrNull(index)?.let {
            text.textSize = dp(13f); text.color = Nova.ACCENT_SOFT
            canvas.drawText(it, cx, baseY - dp(22f), text)
        }
    }
}
