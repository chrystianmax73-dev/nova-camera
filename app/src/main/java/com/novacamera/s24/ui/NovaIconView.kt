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
    enum class Icon {
        FLASH_OFF, FLASH_AUTO, FLASH_ON, TIMER_OFF, TIMER_3, TIMER_10, SETTINGS, FLIP, PAUSE, RESUME, CLOSE,
        MODE_PRO, MODE_VIDEO_PRO, MODE_NIGHT, MODE_SLOWMO, MODE_PANORAMA, MODE_TIMELAPSE, MODE_FOOD, MODE_PORTRAIT_VIDEO,
        TOOLS, MODES,
    }

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
        Icon.TOOLS -> "Ferramentas"
        Icon.MODES -> "Mais modos"
        else -> ""
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
            Icon.TOOLS -> for (y in listOf(8f, 16f)) for (x in listOf(6f, 12f, 18f)) canvas.drawCircle(x, y, 1.7f, fill)
            Icon.MODES -> for (y in listOf(4f, 13f)) for (x in listOf(4f, 13f)) { rect.set(x, y, x + 7f, y + 7f); canvas.drawRoundRect(rect, 2f, 2f, stroke) }
            Icon.MODE_PRO -> aperture(canvas)
            Icon.MODE_VIDEO_PRO -> {
                rect.set(3f, 7f, 15.5f, 17f); canvas.drawRoundRect(rect, 2.5f, 2.5f, stroke)
                path.reset(); path.moveTo(15.5f, 10.5f); path.lineTo(21f, 7.5f); path.lineTo(21f, 16.5f); path.lineTo(15.5f, 13.5f)
                canvas.drawPath(path, stroke)
                canvas.drawCircle(9.2f, 12f, 2.2f, stroke)
            }
            Icon.MODE_NIGHT -> {
                path.reset()
                val outer = Path().apply { addCircle(12f, 12f, 8.5f, Path.Direction.CW) }
                val cut = Path().apply { addCircle(16.5f, 8.5f, 7f, Path.Direction.CW) }
                path.op(outer, cut, Path.Op.DIFFERENCE)
                canvas.drawPath(path, stroke)
            }
            Icon.MODE_SLOWMO -> {
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0)
                    val len = if (i < 4) 2.6f else 1.2f
                    canvas.drawLine(12f + (9f - len) * cos(a).toFloat(), 12f + (9f - len) * sin(a).toFloat(),
                        12f + 9f * cos(a).toFloat(), 12f + 9f * sin(a).toFloat(), stroke)
                }
                canvas.drawCircle(12f, 12f, 2.6f, fill)
            }
            Icon.MODE_PANORAMA -> {
                path.reset()
                path.moveTo(2.5f, 7.5f); path.quadTo(12f, 4.5f, 21.5f, 7.5f)
                path.lineTo(21.5f, 16.5f); path.quadTo(12f, 19.5f, 2.5f, 16.5f); path.close()
                canvas.drawPath(path, stroke)
                path.reset(); path.moveTo(6f, 15f); path.lineTo(10f, 11f); path.lineTo(13f, 14f); path.lineTo(15f, 12f); path.lineTo(18f, 15f)
                canvas.drawPath(path, stroke)
            }
            Icon.MODE_TIMELAPSE -> {
                rect.set(3.5f, 3.5f, 20.5f, 20.5f)
                canvas.drawArc(rect, -60f, 300f, false, stroke)
                canvas.drawLine(12f, 12f, 12f, 7.5f, stroke); canvas.drawLine(12f, 12f, 15.5f, 14f, stroke)
                arrowHead(canvas, -60f, false)
            }
            Icon.MODE_FOOD -> {
                canvas.drawLine(8f, 3.5f, 8f, 20.5f, stroke)
                canvas.drawLine(5.5f, 3.5f, 5.5f, 8f, stroke); canvas.drawLine(10.5f, 3.5f, 10.5f, 8f, stroke)
                path.reset(); path.moveTo(5.5f, 8f); path.quadTo(8f, 11f, 10.5f, 8f); canvas.drawPath(path, stroke)
                path.reset(); path.moveTo(16.5f, 20.5f); path.lineTo(16.5f, 3.5f); path.quadTo(20.5f, 7f, 18.5f, 13f); path.lineTo(16.5f, 13f)
                canvas.drawPath(path, stroke)
            }
            Icon.MODE_PORTRAIT_VIDEO -> {
                canvas.drawCircle(10.5f, 8.5f, 3.6f, stroke)
                path.reset(); path.moveTo(3.5f, 20f); path.quadTo(4.5f, 13.5f, 10.5f, 13.5f); path.quadTo(13.5f, 13.5f, 15f, 15f)
                canvas.drawPath(path, stroke)
                path.reset(); path.moveTo(16f, 15.5f); path.lineTo(21f, 18.3f); path.lineTo(16f, 21f); path.close()
                canvas.drawPath(path, fill)
            }
        }
        canvas.restore()
    }

    /** Diafragma de seis lâminas (modo Pro). */
    private fun aperture(canvas: Canvas) {
        canvas.drawCircle(12f, 12f, 9f, stroke)
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0)
            val b = Math.toRadians(i * 60.0 + 62.0)
            canvas.drawLine(12f + 3.6f * cos(a).toFloat(), 12f + 3.6f * sin(a).toFloat(),
                12f + 9f * cos(b).toFloat(), 12f + 9f * sin(b).toFloat(), stroke)
        }
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
