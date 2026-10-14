package com.novacamera.s24.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Ícones desenhados em vetor (grade 24×24), traço uniforme e cantos arredondados. */
class NovaIconView(context: Context, icon: Icon) : View(context) {
    enum class Icon { FLASH_OFF, FLASH_AUTO, FLASH_ON, TIMER_OFF, TIMER_3, TIMER_10, SETTINGS, FLIP, PAUSE, RESUME, CLOSE }

    var icon: Icon = icon
        set(value) { field = value; contentDescription = describe(value); invalidate() }
    var tint: Int = Nova.TEXT
        set(value) { field = value; invalidate() }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Nova.CONDENSED
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rect = RectF()

    init {
        contentDescription = describe(icon)
        isClickable = true
        isFocusable = true
    }

    private fun describe(i: Icon) = when (i) {
        Icon.FLASH_OFF -> "Flash desligado"
        Icon.FLASH_AUTO -> "Flash automático"
        Icon.FLASH_ON -> "Flash ligado"
        Icon.TIMER_OFF -> "Temporizador desligado"
        Icon.TIMER_3 -> "Temporizador 3 segundos"
        Icon.TIMER_10 -> "Temporizador 10 segundos"
        Icon.SETTINGS -> "Configurações"
        Icon.FLIP -> "Alternar câmera frontal e traseira"
        Icon.PAUSE -> "Pausar gravação"
        Icon.RESUME -> "Retomar gravação"
        Icon.CLOSE -> "Fechar"
    }

    override fun onDraw(canvas: Canvas) {
        val s = dp(24f) / 24f
        canvas.save()
        canvas.translate(width / 2f - 12f * s, height / 2f - 12f * s)
        canvas.scale(s, s)
        val alpha = if (isEnabled) 255 else 90
        stroke.color = tint; stroke.alpha = alpha; stroke.strokeWidth = 1.7f
        fill.color = tint; fill.alpha = alpha
        text.color = tint; text.alpha = alpha
        when (icon) {
            Icon.FLASH_ON -> bolt(canvas, 0f)
            Icon.FLASH_OFF -> { bolt(canvas, 0f); canvas.drawLine(4f, 4f, 20f, 20f, stroke) }
            Icon.FLASH_AUTO -> {
                bolt(canvas, -2.5f)
                text.textSize = 8.5f
                canvas.drawText("A", 19.5f, 22f, text)
            }
            Icon.TIMER_OFF, Icon.TIMER_3, Icon.TIMER_10 -> {
                canvas.drawCircle(12f, 13f, 8f, stroke)
                canvas.drawLine(10f, 2.8f, 14f, 2.8f, stroke)
                canvas.drawLine(12f, 13f, 12f, 8.5f, stroke)
                when (icon) {
                    Icon.TIMER_OFF -> canvas.drawLine(4f, 4f, 20f, 21f, stroke)
                    else -> {
                        val label = if (icon == Icon.TIMER_3) "3" else "10"
                        fill.color = Nova.BG
                        canvas.drawCircle(19f, 19.5f, 5.2f, fill)
                        fill.color = tint
                        text.textSize = 7.5f
                        canvas.drawText(label, 19f, 22.2f, text)
                    }
                }
            }
            Icon.SETTINGS -> {
                stroke.strokeWidth = 2.6f
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0 + 22.5)
                    canvas.drawLine(12f + 7.4f * cos(a).toFloat(), 12f + 7.4f * sin(a).toFloat(),
                        12f + 9.4f * cos(a).toFloat(), 12f + 9.4f * sin(a).toFloat(), stroke)
                }
                stroke.strokeWidth = 1.7f
                canvas.drawCircle(12f, 12f, 6.6f, stroke)
                canvas.drawCircle(12f, 12f, 2.6f, stroke)
            }
            Icon.FLIP -> {
                rect.set(4.5f, 4.5f, 19.5f, 19.5f)
                canvas.drawArc(rect, 200f, 125f, false, stroke)
                arrowHead(canvas, 325f, true)
                canvas.drawArc(rect, 20f, 125f, false, stroke)
                arrowHead(canvas, 145f, true)
            }
            Icon.PAUSE -> {
                rect.set(7f, 5.5f, 10f, 18.5f); canvas.drawRoundRect(rect, 1.2f, 1.2f, fill)
                rect.set(14f, 5.5f, 17f, 18.5f); canvas.drawRoundRect(rect, 1.2f, 1.2f, fill)
            }
            Icon.RESUME -> { fill.color = Nova.REC; canvas.drawCircle(12f, 12f, 6.5f, fill) }
            Icon.CLOSE -> { canvas.drawLine(6f, 6f, 18f, 18f, stroke); canvas.drawLine(18f, 6f, 6f, 18f, stroke) }
        }
        canvas.restore()
    }

    private fun bolt(canvas: Canvas, dx: Float) {
        path.reset()
        path.moveTo(13.2f + dx, 2.5f)
        path.lineTo(5.5f + dx, 13.5f)
        path.lineTo(11.2f + dx, 13.5f)
        path.lineTo(10.4f + dx, 21.5f)
        path.lineTo(18.5f + dx, 10.2f)
        path.lineTo(12.8f + dx, 10.2f)
        path.close()
        canvas.drawPath(path, stroke)
    }

    /** Ponta de seta tangente ao círculo de raio 7.5 centrado em (12,12), no ângulo [deg]. */
    private fun arrowHead(canvas: Canvas, deg: Float, clockwise: Boolean) {
        val a = Math.toRadians(deg.toDouble())
        val x = 12f + 7.5f * cos(a).toFloat()
        val y = 12f + 7.5f * sin(a).toFloat()
        val tangent = a + if (clockwise) Math.PI / 2 else -Math.PI / 2
        for (spread in listOf(2.6, -2.6)) {
            val back = tangent + Math.PI + spread * 0.35
            canvas.drawLine(x, y, x + 3.2f * cos(back).toFloat(), y + 3.2f * sin(back).toFloat(), stroke)
        }
    }
}
