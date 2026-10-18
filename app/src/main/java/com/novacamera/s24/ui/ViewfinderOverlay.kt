package com.novacamera.s24.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.novacamera.s24.core.CameraMath
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Camada sobre a pré-visualização: interpreta gestos (toque, toque longo, arraste de exposição,
 * pinça) sem conflito entre eles e desenha retículo de foco, régua de EV, grade e contagem.
 */
class ViewfinderOverlay(context: Context) : View(context) {
    interface Gestures {
        fun onTap(x: Float, y: Float)
        fun onLongPress(x: Float, y: Float)
        fun onExposureDragStart()
        /** Deslocamento acumulado (px) no eixo horizontal do usuário desde o início do arraste. */
        fun onExposureDrag(totalPx: Float)
        fun onExposureDragEnd()
        fun onPinch(scaleFactor: Float)
        fun onPinchEnd()
    }

    var gestures: Gestures? = null
    var orientation = 0
        set(value) { field = value; invalidate() }
    var showGrid = false
        set(value) { field = value; invalidate() }

    /** Auxílios do visor (ver [com.novacamera.s24.core.imaging.ViewfinderAid]); null = desligado. */
    var aid: com.novacamera.s24.core.imaging.ViewfinderAid.Result? = null
        set(value) { field = value; aidBitmapDirty = true; invalidate() }
    var showHistogram = false
    var showClipping = false
    var showPeaking = false
    /** Ângulo do horizonte na tela (graus); null = nível desligado ou sem horizonte. */
    var horizon: Float? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    private var aidBitmap: android.graphics.Bitmap? = null
    private var aidBitmapDirty = false
    private val aidPaint = Paint().apply { isFilterBitmap = false }
    private val histPaint = Paint().apply { color = 0xD9FFFFFF.toInt(); style = Paint.Style.FILL }
    private val histBg = Paint().apply { color = 0x66000000; style = Paint.Style.FILL }
    private val histClipPaint = Paint().apply { color = 0xFFFF5252.toInt(); style = Paint.Style.FILL }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    /** Máscara do estouro (vermelho) e do realce de foco (verde) na resolução da análise. */
    private fun aidMask(r: com.novacamera.s24.core.imaging.ViewfinderAid.Result): android.graphics.Bitmap? {
        if (!showClipping && !showPeaking) return null
        if (!aidBitmapDirty && aidBitmap?.let { it.width == r.w && it.height == r.h } == true) return aidBitmap
        val px = IntArray(r.w * r.h)
        for (i in px.indices) px[i] = when {
            showPeaking && r.edges[i] -> 0xE600E676.toInt()
            showClipping && r.clipped[i] -> 0x99FF1744.toInt()
            else -> 0
        }
        val b = aidBitmap?.takeIf { it.width == r.w && it.height == r.h }
            ?: android.graphics.Bitmap.createBitmap(r.w, r.h, android.graphics.Bitmap.Config.ARGB_8888).also { aidBitmap?.recycle(); aidBitmap = it }
        b.setPixels(px, 0, r.w, 0, 0, r.w, r.h)
        aidBitmapDirty = false
        return b
    }

    private fun drawAids(canvas: Canvas) {
        val r = aid
        if (r != null) {
            aidMask(r)?.let { canvas.drawBitmap(it, null, android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), aidPaint) }
            if (showHistogram) {
                // Canto superior esquerdo, abaixo da barra superior; barras em escala de raiz (sombras visíveis).
                val bw = dp(120f); val bh = dp(52f)
                val x0 = dp(12f); val y0 = dp(96f)
                canvas.drawRoundRect(x0, y0, x0 + bw, y0 + bh, dp(6f), dp(6f), histBg)
                val maxC = r.histogram.maxOrNull()?.coerceAtLeast(1) ?: 1
                val step = bw / r.histogram.size
                for ((k, c) in r.histogram.withIndex()) {
                    val v = kotlin.math.sqrt(c.toFloat() / maxC) * (bh - dp(6f))
                    val paint = if (k == r.histogram.size - 1 && c > 0) histClipPaint else histPaint
                    canvas.drawRect(x0 + k * step, y0 + bh - dp(3f) - v, x0 + (k + 1) * step, y0 + bh - dp(3f), paint)
                }
            }
        }
        horizon?.let { a ->
            val err = com.novacamera.s24.core.imaging.ViewfinderAid.levelError(a)
            val ok = err < 1f
            levelPaint.color = if (ok) 0xFF00E676.toInt() else 0xCCFFFFFF.toInt()
            levelPaint.strokeWidth = dp(if (ok) 2f else 1.4f)
            val cx = width / 2f; val cy = height / 2f
            val half = dp(70f)
            canvas.save()
            canvas.rotate(a, cx, cy)
            canvas.drawLine(cx - half, cy, cx - dp(14f), cy, levelPaint)
            canvas.drawLine(cx + dp(14f), cy, cx + half, cy, levelPaint)
            canvas.restore()
            // Referência fixa (nível do aparelho) curta, para ver a diferença.
            if (!ok) {
                levelPaint.color = 0x80FFFFFF.toInt(); levelPaint.strokeWidth = dp(1f)
                val ref = Math.round(a / 90f) * 90f
                canvas.save(); canvas.rotate(ref, cx, cy)
                canvas.drawLine(cx - dp(10f), cy, cx + dp(10f), cy, levelPaint)
                canvas.restore()
            }
        }
    }

    /** Desliga gestos de foco/exposição (ex.: modo indisponível ou câmera pausada). */
    var focusEnabled = true

    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressMs = 450L
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var longPressed = false
    private var multiTouch = false
    private var draggingEv = false
    private var ignoreGesture = false

    // Estado visual
    private var ringX = -1f
    private var ringY = -1f
    private var ringScale = 1f
    private var ringAlpha = 0f
    private var ringColor = Nova.TEXT
    private var locked = false
    private var evFraction = 0.5f
    private var evText: String? = null
    private var evVisible = false
    private var flashAlpha = 0f
    private var bigCountdown = 0
    private var fadeAnimator: ValueAnimator? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Nova.MEDIUM; textAlign = Paint.Align.CENTER; color = Nova.TEXT
        setShadowLayer(dp(3f), 0f, 0f, 0x99000000.toInt())
    }
    private val gridPaint = Paint().apply { color = 0x59FFFFFF; strokeWidth = 1f }
    private val flashPaint = Paint().apply { color = Color.BLACK }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            gestures?.onPinch(detector.scaleFactor)
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) { gestures?.onPinchEnd() }
    })

    private val longPressRunnable = Runnable {
        if (!moved && !multiTouch && focusEnabled) {
            longPressed = true
            gestures?.onLongPress(downX, downY)
        }
    }

    init {
        contentDescription = "Visor da câmera. Toque para focar, toque longo para travar foco e exposição, arraste na horizontal para ajustar a exposição."
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                moved = false; longPressed = false; multiTouch = false; draggingEv = false; ignoreGesture = false
                postDelayed(longPressRunnable, longPressMs)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                removeCallbacks(longPressRunnable)
                if (draggingEv) { draggingEv = false; gestures?.onExposureDragEnd() }
            }
            MotionEvent.ACTION_MOVE -> {
                if (multiTouch || ignoreGesture || longPressed) return true
                val dx = event.x - downX
                val dy = event.y - downY
                if (!moved && hypot(dx, dy) > slop) {
                    moved = true
                    removeCallbacks(longPressRunnable)
                    val ux = CameraMath.userHorizontalDelta(dx, dy, orientation)
                    val uy = CameraMath.userHorizontalDelta(dy, -dx, orientation)
                    if (focusEnabled && abs(ux) > abs(uy)) {
                        draggingEv = true
                        gestures?.onExposureDragStart()
                    } else ignoreGesture = true
                }
                if (draggingEv) gestures?.onExposureDrag(CameraMath.userHorizontalDelta(dx, dy, orientation))
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                when {
                    draggingEv -> { draggingEv = false; gestures?.onExposureDragEnd() }
                    !moved && !multiTouch && !longPressed && focusEnabled -> {
                        performClick()
                        gestures?.onTap(event.x, event.y)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (draggingEv) { draggingEv = false; gestures?.onExposureDragEnd() }
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    // ---------------------------------------------------------------- API visual

    fun showFocusAt(x: Float, y: Float) {
        fadeAnimator?.cancel()
        ringX = x; ringY = y
        ringColor = Nova.TEXT
        ringAlpha = 1f
        locked = false
        ValueAnimator.ofFloat(1.35f, 1f).apply {
            duration = 220; interpolator = Nova.EASE
            addUpdateListener { ringScale = it.animatedValue as Float; invalidate() }
        }.start()
    }

    fun setFocusResult(success: Boolean, isLocked: Boolean) {
        if (ringX < 0) return
        locked = isLocked
        ringColor = when {
            isLocked -> Nova.LOCK
            success -> Nova.ACCENT_SOFT
            else -> Nova.TEXT_MUTED
        }
        invalidate()
        if (!isLocked && !draggingEv) scheduleFade(1400)
    }

    fun hideFocusRing() {
        fadeAnimator?.cancel()
        ringX = -1f; ringY = -1f; ringAlpha = 0f; locked = false; evVisible = false
        invalidate()
    }

    /** Régua de exposição ao lado do retículo (ou no centro, sem ponto de foco). */
    fun showExposure(fraction: Float, label: String, keep: Boolean) {
        fadeAnimator?.cancel()
        evFraction = fraction.coerceIn(0f, 1f)
        evText = label
        evVisible = true
        ringAlpha = 1f
        invalidate()
        if (!keep && !locked) scheduleFade(1600)
    }

    fun flash() {
        ValueAnimator.ofFloat(0.55f, 0f).apply {
            duration = 200
            addUpdateListener { flashAlpha = it.animatedValue as Float; invalidate() }
        }.start()
    }

    fun setBigCountdown(n: Int) { bigCountdown = n; invalidate() }

    private fun scheduleFade(delay: Long) {
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            startDelay = delay
            duration = 350
            addUpdateListener {
                ringAlpha = it.animatedValue as Float
                if (ringAlpha == 0f) evVisible = false
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Auxílio com defeito não pode fechar o app: desliga e registra para o Diagnóstico.
        try { drawAids(canvas) } catch (e: RuntimeException) {
            aid = null; horizon = null
            com.novacamera.s24.core.CrashLog.record(context, "visor (não fatal)", e)
        }
        if (showGrid) {
            for (i in 1..2) {
                val x = width * i / 3f
                val y = height * i / 3f
                canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            }
        }

        val hasRing = ringX >= 0 && ringAlpha > 0f
        if (hasRing || (evVisible && ringAlpha > 0f)) {
            val cx = if (ringX >= 0) ringX else width / 2f
            val cy = if (ringY >= 0) ringY else height / 2f
            val alpha = (ringAlpha * 255).toInt()
            canvas.save()
            canvas.rotate(CameraMath.iconRotationFor(orientation), cx, cy)
            val r = dp(34f) * ringScale
            if (ringX >= 0) {
                ringPaint.color = ringColor; ringPaint.alpha = alpha; ringPaint.strokeWidth = dp(1.4f)
                canvas.drawCircle(cx, cy, r, ringPaint)
                // Marcas curtas nos quatro quadrantes: leitura de "mira" sem poluir.
                linePaint.color = ringColor; linePaint.alpha = alpha; linePaint.strokeWidth = dp(1.4f)
                val t = dp(5f)
                canvas.drawLine(cx, cy - r, cx, cy - r + t, linePaint)
                canvas.drawLine(cx, cy + r, cx, cy + r - t, linePaint)
                canvas.drawLine(cx - r, cy, cx - r + t, cy, linePaint)
                canvas.drawLine(cx + r, cy, cx + r - t, cy, linePaint)
                if (locked) {
                    textPaint.textSize = dp(11f); textPaint.color = Nova.LOCK; textPaint.alpha = alpha
                    canvas.drawText("AE/AF", cx, cy - r - dp(10f), textPaint)
                }
            }
            if (evVisible) {
                val barX = cx + dp(34f) + dp(18f)
                val half = dp(34f)
                linePaint.color = Nova.TEXT; linePaint.alpha = (alpha * 0.55f).toInt(); linePaint.strokeWidth = dp(1.2f)
                canvas.drawLine(barX, cy - half, barX, cy + half, linePaint)
                val ky = cy + half - 2 * half * evFraction
                knobPaint.color = Nova.LOCK; knobPaint.alpha = alpha
                drawSun(canvas, barX, ky, knobPaint)
                evText?.let {
                    textPaint.textSize = dp(12f); textPaint.color = Nova.TEXT; textPaint.alpha = alpha
                    canvas.drawText(it, barX, cy - half - dp(10f), textPaint)
                }
            }
            canvas.restore()
        }

        if (bigCountdown > 0) {
            canvas.save()
            canvas.rotate(CameraMath.iconRotationFor(orientation), width / 2f, height / 2f)
            textPaint.textSize = dp(96f); textPaint.color = Nova.TEXT; textPaint.alpha = 230
            canvas.drawText(bigCountdown.toString(), width / 2f, height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
            canvas.restore()
        }

        if (flashAlpha > 0f) {
            flashPaint.alpha = (flashAlpha * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
        }
    }

    private fun drawSun(canvas: Canvas, x: Float, y: Float, paint: Paint) {
        canvas.drawCircle(x, y, dp(4.5f), paint)
        linePaint.color = paint.color; linePaint.alpha = paint.alpha; linePaint.strokeWidth = dp(1.3f)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
            canvas.drawLine(x + c * dp(6.8f), y + s * dp(6.8f), x + c * dp(9f), y + s * dp(9f), linePaint)
        }
    }
}
