package com.novacamera.s24.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * Obturador: anel fino + disco. Foto = disco claro; vídeo = disco vermelho que se transforma em
 * quadrado arredondado ao gravar. Um arco gira enquanto a foto está sendo processada.
 */
class ShutterButton(context: Context) : View(context) {
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
            kind == Kind.PHOTO -> "Capturar foto"
            recording -> "Parar gravação"
            else -> "Iniciar gravação"
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animatePress(1f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animatePress(0f)
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

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
        spinner.cancel()
        super.onDetachedFromWindow()
    }
}
