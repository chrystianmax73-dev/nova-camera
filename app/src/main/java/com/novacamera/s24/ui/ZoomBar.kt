package com.novacamera.s24.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.novacamera.s24.ZoomPresets
import com.novacamera.s24.core.CameraMath
import kotlin.math.abs

/**
 * Barra de zoom compacta: paradas reais (só as alcançáveis), a ativa mostra o valor exato.
 * Toque numa parada = transição suave; arrastar ao longo da barra = zoom contínuo.
 */
class ZoomBar(context: Context) : View(context) {
    interface Listener {
        fun onStop(value: Float)
        fun onDrag(value: Float)
        fun onDragEnd()
    }

    var listener: Listener? = null
    var stops: List<Float> = listOf(1f)
        set(value) { field = value; requestLayout(); invalidate() }
    var zoom = 1f
        set(value) { field = value; invalidate() }
    var orientation = 0
        set(value) { field = value; invalidate() }

    private val item = dp(34f)
    private val gap = dp(4f)
    private val pad = dp(4f)
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val activeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6142338.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Nova.MEDIUM }
    private val rect = RectF()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var dragging = false
    private var lastStopIndex = -1

    init {
        contentDescription = "Zoom"
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (pad * 2 + stops.size * item + (stops.size - 1) * gap).toInt()
        setMeasuredDimension(w, (item + pad * 2).toInt())
    }

    private fun centerX(i: Int) = pad + item / 2 + i * (item + gap)

    /** Parada "dona" do zoom atual: a maior parada ≤ zoom (com tolerância). */
    private fun activeIndex(): Int {
        var idx = 0
        stops.forEachIndexed { i, s -> if (zoom >= s * 0.97f) idx = i }
        return idx
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, height / 2f, height / 2f, bg)
        val active = activeIndex()
        val cy = height / 2f
        stops.forEachIndexed { i, s ->
            val cx = centerX(i)
            val isActive = i == active
            if (isActive) canvas.drawCircle(cx, cy, item / 2f, activeBg)
            val label = if (isActive) ZoomPresets.label(zoom) else ZoomPresets.label(s).removeSuffix("×")
            text.color = if (isActive) Nova.ACCENT_SOFT else Nova.TEXT
            text.textSize = dp(if (isActive) 12f else 11.5f)
            if (isActive && label.length > 4) text.textSize = dp(10.5f)
            canvas.save()
            canvas.rotate(CameraMath.iconRotationFor(orientation), cx, cy)
            canvas.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2f, text)
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || stops.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; dragging = false
                lastStopIndex = activeIndex()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.x - downX) > slop && stops.size > 1) dragging = true
                if (dragging) {
                    val f = ((event.x - centerX(0)) / (centerX(stops.size - 1) - centerX(0))).coerceIn(0f, 1f)
                    val z = CameraMath.zoomForFraction(stops, f)
                    zoom = z
                    val idx = activeIndex()
                    if (idx != lastStopIndex) { lastStopIndex = idx; Haptics.tick(this) }
                    listener?.onDrag(z)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) listener?.onDragEnd()
                else {
                    val i = ((event.x - pad) / (item + gap)).toInt().coerceIn(0, stops.size - 1)
                    Haptics.tick(this)
                    performClick()
                    listener?.onStop(stops[i])
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> { if (dragging) listener?.onDragEnd(); dragging = false }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
