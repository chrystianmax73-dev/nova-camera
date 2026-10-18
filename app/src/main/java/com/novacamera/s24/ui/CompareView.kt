package com.novacamera.s24.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Comparação lado a lado na mesma imagem: à esquerda do divisor o original, à direita a versão
 * Nova. Arraste o divisor para comparar qualquer região.
 */
class CompareView(context: Context) : View(context) {
    var original: Bitmap? = null
        set(v) { field = v; requestLayout(); invalidate() }
    var enhanced: Bitmap? = null
        set(v) { field = v; invalidate() }
    /** Posição do divisor (0 = tudo aprimorado, 1 = tudo original). */
    var split = 0.5f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Nova.TEXT; strokeWidth = dp(2f) }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Nova.TEXT }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Nova.TEXT; textSize = dp(12f); typeface = Nova.MEDIUM
        setShadowLayer(dp(3f), 0f, 0f, 0xCC000000.toInt())
    }
    private val dst = RectF()

    init {
        contentDescription = "Comparação entre original e aprimorado. Arraste para comparar"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val b = original ?: enhanced
        val h = if (b == null) w * 4 / 3 else (w.toLong() * b.height / b.width).toInt()
        val maxH = (resources.displayMetrics.heightPixels * 0.62f).toInt()
        setMeasuredDimension(w, min(h, maxH))
    }

    override fun onDraw(canvas: Canvas) {
        val o = original; val e = enhanced ?: original ?: return
        val scale = min(width.toFloat() / e.width, height.toFloat() / e.height)
        val dw = e.width * scale; val dh = e.height * scale
        dst.set((width - dw) / 2, (height - dh) / 2, (width + dw) / 2, (height + dh) / 2)
        canvas.drawBitmap(e, null, dst, paint)
        val x = dst.left + dst.width() * split
        if (o != null && split > 0f) {
            canvas.save()
            canvas.clipRect(dst.left, dst.top, x, dst.bottom)
            canvas.drawBitmap(o, null, dst, paint)
            canvas.restore()
        }
        canvas.drawLine(x, dst.top, x, dst.bottom, line)
        canvas.drawCircle(x, dst.centerY(), dp(9f), knob)
        canvas.drawText("Original", dst.left + dp(10f), dst.top + dp(22f), label)
        val nova = "Nova"
        canvas.drawText(nova, dst.right - dp(10f) - label.measureText(nova), dst.top + dp(22f), label)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (dst.width() > 0) split = (event.x - dst.left) / dst.width()
                return true
            }
            MotionEvent.ACTION_UP -> { performClick(); return true }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}
