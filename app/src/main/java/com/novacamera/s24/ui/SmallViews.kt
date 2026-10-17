package com.novacamera.s24.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.widget.TextView

/** Miniatura da última captura (cantos arredondados, recorte central). */
class ThumbnailView(context: Context) : View(context) {
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x99EDF7FF.toInt() }
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Nova.SURFACE }
    private val rect = RectF()
    private val matrix = Matrix()
    private var pop = 1f

    init {
        contentDescription = "Abrir última captura na galeria"
        isClickable = true
    }

    fun setImage(b: Bitmap?, animate: Boolean) {
        bitmap = b
        paint.shader = b?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        updateMatrix()
        if (animate) ValueAnimator.ofFloat(0.6f, 1f).apply {
            duration = Nova.NORMAL; interpolator = Nova.EASE
            addUpdateListener { pop = it.animatedValue as Float; invalidate() }
        }.start() else invalidate()
    }

    val hasImage get() = bitmap != null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateMatrix()

    private fun updateMatrix() {
        val b = bitmap ?: return
        if (width == 0) return
        val scale = maxOf(width.toFloat() / b.width, height.toFloat() / b.height)
        matrix.setScale(scale, scale)
        matrix.postTranslate((width - b.width * scale) / 2f, (height - b.height * scale) / 2f)
        paint.shader?.setLocalMatrix(matrix)
    }

    override fun onDraw(canvas: Canvas) {
        val inset = dp(1f)
        val cx = width / 2f; val cy = height / 2f
        canvas.save()
        canvas.scale(pop, pop, cx, cy)
        rect.set(inset, inset, width - inset, height - inset)
        val r = dp(12f)
        canvas.drawRoundRect(rect, r, r, if (bitmap != null) paint else empty)
        border.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(rect, r, r, border)
        canvas.restore()
    }
}

/** Mensagem breve dentro do app (substitui o Toast do sistema, que cobria os controles). */
class HintView(context: Context) : TextView(context) {
    private var hideRunnable = Runnable { animate().alpha(0f).setDuration(Nova.NORMAL).withEndAction { visibility = GONE }.start() }

    init {
        setTextColor(Nova.TEXT)
        textSize = 13f
        typeface = Nova.REGULAR
        gravity = Gravity.CENTER
        setPadding(dpi(14), dpi(8), dpi(14), dpi(8))
        background = Nova.pill(Nova.SURFACE_GLASS, dp(18f))
        visibility = GONE
        maxWidth = dpi(320)
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }

    fun show(message: String, durationMs: Long = 2600L) {
        removeCallbacks(hideRunnable)
        text = message
        if (visibility != VISIBLE) { alpha = 0f; visibility = VISIBLE }
        animate().alpha(1f).setDuration(Nova.FAST).start()
        postDelayed(hideRunnable, durationMs)
    }
}

/** Rótulo-botão compacto da barra superior (ex.: "4:3", "HDR", "4K · 30"). */
class TextChip(context: Context) : TextView(context) {
    var active = false
        set(value) {
            field = value
            setTextColor(if (value) Nova.BG else Nova.TEXT)
            background = if (value) Nova.pill(Nova.ACCENT_SOFT, dp(11f)) else null
        }

    init {
        textSize = 12f
        typeface = Nova.MEDIUM
        letterSpacing = 0.04f
        gravity = Gravity.CENTER
        setTextColor(Nova.TEXT)
        setPadding(dpi(8), dpi(3), dpi(8), dpi(3))
        isClickable = true
    }
}
