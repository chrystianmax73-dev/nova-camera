package com.novacamera.s24.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Comparação lado a lado na mesma imagem: à esquerda do divisor o original, à direita a versão
 * Nova. Arraste o divisor para comparar qualquer região; dois dedos ampliam e movem, toque duplo volta.
 * Com um [patch] (IA na região ampliada), o divisor compara a versão Nova sem e com a IA ali dentro.
 */
class CompareView(context: Context) : View(context) {
    var original: Bitmap? = null
        set(v) { field = v; requestLayout(); invalidate() }
    var enhanced: Bitmap? = null
        set(v) { field = v; invalidate() }
    /** Posição do divisor (0 = tudo aprimorado, 1 = tudo original). */
    var split = 0.5f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    /** Recorte em resolução cheia, sem e com IA, em coordenadas normalizadas da foto. */
    class Patch(val before: Bitmap, val after: Bitmap, val rect: RectF)
    var patch: Patch? = null
        set(v) { field?.let { if (it !== v) { it.before.recycle(); it.after.recycle() } }; field = v; invalidate() }

    var zoom = 1f; private set
    /** Área visível (normalizada na foto) depois de cada gesto de ampliação ou movimento. */
    var onViewport: ((RectF, Float) -> Unit)? = null

    private var panX = 0f
    private var panY = 0f
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Nova.TEXT; strokeWidth = dp(2f) }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Nova.TEXT }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Nova.ACCENT_SOFT; style = Paint.Style.STROKE; strokeWidth = dp(1.5f) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Nova.TEXT; textSize = dp(12f); typeface = Nova.MEDIUM
        setShadowLayer(dp(3f), 0f, 0f, 0xCC000000.toInt())
    }
    private val dst = RectF()
    private val fit = RectF()
    private val part = RectF()
    private var gesture = false

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean { gesture = true; return true }
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val next = (zoom * d.scaleFactor).coerceIn(1f, MAX_ZOOM)
            val k = next / zoom
            // Mantém o ponto entre os dedos parado.
            panX = (panX - (d.focusX - width / 2f)) * k + (d.focusX - width / 2f)
            panY = (panY - (d.focusY - height / 2f)) * k + (d.focusY - height / 2f)
            zoom = next; clampPan(); invalidate()
            return true
        }
    })
    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoom = 1f; panX = 0f; panY = 0f; invalidate(); report(); return true
        }
        // Toque e segure: a foto inteira vira o original, no mesmo lugar; soltar volta à Nova. A linha
        // compara partes diferentes da cena e engana o olho (20/10: "a galeria não processou").
        override fun onLongPress(e: MotionEvent) {
            if (gesture || holdSplit != null) return
            holdSplit = split; split = 1f
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }
    })
    private var holdSplit: Float? = null
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    init {
        contentDescription = "Comparação entre original e aprimorado. Arraste para comparar; toque e segure para ver o original inteiro; dois dedos ampliam"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val b = original ?: enhanced
        val h = if (b == null) w * 4 / 3 else (w.toLong() * b.height / b.width).toInt()
        val maxH = (resources.displayMetrics.heightPixels * 0.62f).toInt()
        setMeasuredDimension(w, min(h, maxH))
    }

    private fun layoutImage(b: Bitmap) {
        val scale = min(width.toFloat() / b.width, height.toFloat() / b.height)
        val dw = b.width * scale; val dh = b.height * scale
        fit.set((width - dw) / 2, (height - dh) / 2, (width + dw) / 2, (height + dh) / 2)
        val cx = width / 2f + panX; val cy = height / 2f + panY
        dst.set(cx - dw * zoom / 2, cy - dh * zoom / 2, cx + dw * zoom / 2, cy + dh * zoom / 2)
    }

    private fun clampPan() {
        val b = enhanced ?: original ?: return
        layoutImage(b)
        val mx = max(0f, (fit.width() * zoom - fit.width()) / 2); val my = max(0f, (fit.height() * zoom - fit.height()) / 2)
        panX = panX.coerceIn(-mx, mx); panY = panY.coerceIn(-my, my)
    }

    override fun onDraw(canvas: Canvas) {
        val o = original; val e = enhanced ?: original ?: return
        layoutImage(e)
        canvas.save()
        canvas.clipRect(fit)
        canvas.drawBitmap(e, null, dst, paint)
        val x = (fit.left + fit.width() * split)
        val p = patch
        if (p != null) {
            part.set(dst.left + p.rect.left * dst.width(), dst.top + p.rect.top * dst.height(),
                dst.left + p.rect.right * dst.width(), dst.top + p.rect.bottom * dst.height())
            canvas.drawBitmap(p.after, null, part, paint)
            if (split > 0f) {
                canvas.save(); canvas.clipRect(part.left, part.top, x, part.bottom)
                canvas.drawBitmap(p.before, null, part, paint); canvas.restore()
            }
            canvas.drawRect(part, frame)
        } else if (o != null && split > 0f) {
            canvas.save()
            canvas.clipRect(dst.left, dst.top, x, dst.bottom)
            canvas.drawBitmap(o, null, dst, paint)
            canvas.restore()
        }
        canvas.restore()
        val left = if (p != null) "Sem IA" else "Original"
        val right = if (p != null) "Com IA" else "Nova"
        if (holdSplit != null) {
            // Segurando: só o original, sem linha (o rótulo diz o que está na tela).
            val t = "$left · solte para ver a $right"
            canvas.drawText(t, fit.left + dp(10f), fit.top + dp(22f), label)
        } else {
            canvas.drawLine(x, fit.top, x, fit.bottom, line)
            canvas.drawCircle(x, fit.centerY(), dp(9f), knob)
            canvas.drawText(left, fit.left + dp(10f), fit.top + dp(22f), label)
            canvas.drawText(right, fit.right - dp(10f) - label.measureText(right), fit.top + dp(22f), label)
        }
        if (zoom > 1.01f) {
            val z = "${(zoom * 10).toInt() / 10f}×"
            canvas.drawText(z, fit.centerX() - label.measureText(z) / 2, fit.bottom - dp(12f), label)
        }
    }

    private fun report() {
        val b = enhanced ?: original ?: return
        layoutImage(b)
        if (dst.width() <= 0f || dst.height() <= 0f) return
        val l = ((fit.left - dst.left) / dst.width()).coerceIn(0f, 1f)
        val t = ((fit.top - dst.top) / dst.height()).coerceIn(0f, 1f)
        val r = ((fit.right - dst.left) / dst.width()).coerceIn(0f, 1f)
        val bo = ((fit.bottom - dst.top) / dst.height()).coerceIn(0f, 1f)
        onViewport?.invoke(RectF(l, t, r, bo), zoom)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        taps.onTouchEvent(event)
        scaler.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { gesture = false; moveSplit(event) }
            MotionEvent.ACTION_POINTER_DOWN -> { gesture = true; focus(event) }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    // Dois dedos também arrastam a foto ampliada.
                    val fx = lastFocusX; val fy = lastFocusY
                    focus(event)
                    panX += lastFocusX - fx; panY += lastFocusY - fy
                    clampPan(); invalidate()
                } else if (!gesture && holdSplit == null) moveSplit(event)
            }
            MotionEvent.ACTION_POINTER_UP -> focus(event, skip = event.actionIndex)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                holdSplit?.let { split = it; holdSplit = null; invalidate() }
                if (gesture) report() else performClick()
                gesture = false
            }
        }
        return true
    }

    private fun focus(e: MotionEvent, skip: Int = -1) {
        var sx = 0f; var sy = 0f; var n = 0
        for (i in 0 until e.pointerCount) if (i != skip) { sx += e.getX(i); sy += e.getY(i); n++ }
        if (n > 0) { lastFocusX = sx / n; lastFocusY = sy / n }
    }

    private fun moveSplit(e: MotionEvent) {
        if (fit.width() > 0) split = (e.x - fit.left) / fit.width()
    }

    override fun performClick(): Boolean = super.performClick()

    companion object { const val MAX_ZOOM = 8f }
}
