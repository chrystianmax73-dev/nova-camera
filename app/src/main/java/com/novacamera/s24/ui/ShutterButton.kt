package com.novacamera.s24.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils

/**
 * Obturador: anel fino + disco. Foto = disco claro; vídeo = disco vermelho que se transforma em
 * quadrado arredondado ao gravar. Um arco gira enquanto a foto está sendo processada.
 */
class ShutterButton(context: Context) : View(context) {
    interface QuickListener {
        fun onHold(): Boolean
        fun onLock()
        fun onRelease()
        fun onCancel()
        /**
         * Arrastar o botão para cima aproxima, para baixo afasta (com ou sem gravação; soltar depois de
         * arrastar não fotografa). Positivo ao subir; a câmera aplica amortecimento e limites.
         */
        fun onZoom(deltaDp:Float) {}
        /** Começo de um arraste de zoom (o alvo é calculado a partir do zoom deste momento). */
        fun onZoomStart() {}
    }
    var quickListener: QuickListener? = null
    var quickEnabled = false
        set(value) { field = value; isLongClickable = value; updateDescription() }
    private val quickHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var quickTouch = false
    private var held = false
    private var holdAccepted=false
    private var zooming=false
    private var lastY=0f
    private var locked = false
    private var moved = false
    private var downX = 0f
    private var downY = 0f
    private val holdTask = Runnable {
        if (quickTouch && !moved && isEnabled && !busy && countdown == 0) {
            held = true // um toque longo recusado também não deve virar foto ao soltar
            holdAccepted=quickListener?.onHold()==true
        }
    }
    enum class Kind { PHOTO, VIDEO }

    var kind = Kind.PHOTO
        set(value) { if (field != value) { field = value; animateKind() } }
    var recording = false
        set(value) { if (field != value) { field = value; animateMorph(if (value) 1f else 0f) } }
    var busy = false
        set(value) {
            field = value
            if (value) spinner.start() else spinner.cancel()
            invalidate()
        }
    /** Contagem regressiva do temporizador exibida no centro (0 = nenhuma). */
    var countdown = 0
        set(value) { field = value; invalidate() }

    private var videoFraction = 0f   // 0 = foto, 1 = vídeo
    private var morph = 0f           // 0 = círculo, 1 = quadrado (gravando)
    private var press = 0f
    private var spinAngle = 0f
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Nova.ACCENT }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Nova.MEDIUM; color = Nova.BG }
    private val rect = RectF()

    private val spinner = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = null
        addUpdateListener { spinAngle = it.animatedValue as Float; invalidate() }
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Capturar foto"
    }

    private fun animateKind() {
        ValueAnimator.ofFloat(videoFraction, if (kind == Kind.VIDEO) 1f else 0f).apply {
            duration = Nova.NORMAL; interpolator = Nova.EASE
            addUpdateListener { videoFraction = it.animatedValue as Float; invalidate() }
        }.start()
        updateDescription()
    }

    private fun animateMorph(to: Float) {
        ValueAnimator.ofFloat(morph, to).apply {
            duration = Nova.NORMAL; interpolator = Nova.EASE
            addUpdateListener { morph = it.animatedValue as Float; invalidate() }
        }.start()
        updateDescription()
    }

    private fun updateDescription() {
        contentDescription = when {
            recording -> "Parar gravação"
            quickEnabled -> "Capturar foto; segure para gravar, arraste à direita para iniciar e bloquear, suba ou desça durante a pressão para zoom"
            kind == Kind.PHOTO -> "Capturar foto"
            else -> "Iniciar gravação"
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled && !quickTouch) return false
        if ((quickEnabled && event.actionMasked == MotionEvent.ACTION_DOWN) || quickTouch) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    quickTouch = true; held = false; locked = false; moved = false
                    holdAccepted=false;zooming=false
                    downX = event.x; downY = event.y;lastY=event.y
                    animatePress(1f)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    quickHandler.postDelayed(holdTask, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x-downX
                    val dy = event.y-downY
                    if(!held && !zooming && kotlin.math.abs(dy)>=dp(12f) && kotlin.math.abs(dy)>kotlin.math.abs(dx)) {
                        zooming=true; moved=true; quickHandler.removeCallbacks(holdTask); quickListener?.onZoomStart()
                    }
                    if(!held && zooming) {
                        quickListener?.onZoom((lastY-event.y)/resources.displayMetrics.density)
                        lastY=event.y
                        return true
                    }
                    if(!held && dx>=dp(64f) && dx>kotlin.math.abs(dy)) {
                        quickHandler.removeCallbacks(holdTask)
                        held=true;holdAccepted=quickListener?.onHold()==true
                    }
                    if (!held && maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) > ViewConfiguration.get(context).scaledTouchSlop) {
                        moved = true; quickHandler.removeCallbacks(holdTask)
                    }
                    if(held && holdAccepted) {
                        if(!zooming && kotlin.math.abs(dy)>=dp(12f) && kotlin.math.abs(dy)>kotlin.math.abs(dx)) { zooming=true; quickListener?.onZoomStart() }
                        if(zooming) {
                            quickListener?.onZoom((lastY-event.y)/resources.displayMetrics.density)
                        } else if(!locked && dx>=dp(64f)) { locked=true;quickListener?.onLock() }
                    }
                    lastY=event.y
                }
                MotionEvent.ACTION_UP -> {
                    quickHandler.removeCallbacks(holdTask)
                    quickTouch = false; animatePress(0f)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (held) quickListener?.onRelease() else if (!moved && isEnabled) performClick()
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> cancelQuickTouch()
            }
            return true
        }
        // Sem gravação rápida (ex.: modos de vídeo): o arraste vertical também controla o zoom.
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animatePress(1f); downX = event.x; downY = event.y; lastY = event.y; zooming = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x-downX; val dy = event.y-downY
                if (!zooming && kotlin.math.abs(dy) >= dp(12f) && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                    zooming = true
                    quickListener?.onZoomStart()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    cancelPendingInputEvents()
                }
                if (zooming) {
                    quickListener?.onZoom((lastY-event.y)/resources.displayMetrics.density)
                    lastY = event.y
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animatePress(0f)
                if (zooming) { zooming = false; isPressed = false; parent?.requestDisallowInterceptTouchEvent(false); return true }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun performLongClick(): Boolean {
        if (quickEnabled && isEnabled && !busy && countdown == 0 && quickListener?.onHold() == true) {
            quickListener?.onLock() // ação de acessibilidade não tem evento de soltar
            return true
        }
        return super.performLongClick()
    }

    private fun cancelQuickTouch() {
        quickHandler.removeCallbacks(holdTask)
        if (held) quickListener?.onCancel()
        quickTouch = false; held = false; locked = false;holdAccepted=false;zooming=false
        animatePress(0f)
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun animatePress(to: Float) {
        ValueAnimator.ofFloat(press, to).apply {
            duration = if (to > 0) 70 else Nova.FAST
            addUpdateListener { press = it.animatedValue as Float; invalidate() }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = minOf(width, height) / 2f - dp(2f)
        val enabledAlpha = if (isEnabled) 255 else 80

        ring.strokeWidth = dp(3f)
        ring.color = Nova.TEXT
        ring.alpha = enabledAlpha
        canvas.drawCircle(cx, cy, outer - dp(1.5f), ring)

        if (busy) {
            arc.strokeWidth = dp(3f)
            rect.set(cx - outer + dp(1.5f), cy - outer + dp(1.5f), cx + outer - dp(1.5f), cy + outer - dp(1.5f))
            canvas.drawArc(rect, spinAngle - 90f, 70f, false, arc)
        }

        val scale = 1f - 0.08f * press
        val photoColor = Nova.TEXT
        disc.color = ColorUtils.blendARGB(photoColor, Nova.REC, videoFraction)
        disc.alpha = enabledAlpha
        val circleR = (outer - dp(7f)) * scale
        val squareHalf = outer * 0.38f * scale
        val half = circleR + (squareHalf - circleR) * morph
        val corner = half * (1f - morph) + dp(7f) * morph
        rect.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawRoundRect(rect, corner, corner, disc)

        if (countdown > 0) {
            label.textSize = dp(26f)
            label.color = if (videoFraction > 0.5f) Color.WHITE else Nova.BG
            canvas.drawText(countdown.toString(), cx, cy - (label.descent() + label.ascent()) / 2f, label)
        }
    }

    override fun onDetachedFromWindow() {
        cancelQuickTouch()
        spinner.cancel()
        super.onDetachedFromWindow()
    }
}

