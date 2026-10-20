package com.novacamera.s24.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Ícone de QR desenhado (três marcadores e alguns módulos), sem imagem embutida. */
class QrGlyph(private val sizePx: Int, color: Int) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color; strokeWidth = sizePx / 11f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
    private val r = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds; val s = b.width().toFloat(); val u = s / 10f
        fun finder(x: Float, y: Float) {
            r.set(b.left + x + u * 0.5f, b.top + y + u * 0.5f, b.left + x + u * 3.5f, b.top + y + u * 3.5f)
            canvas.drawRoundRect(r, u * 0.8f, u * 0.8f, stroke)
            r.set(b.left + x + u * 1.5f, b.top + y + u * 1.5f, b.left + x + u * 2.5f, b.top + y + u * 2.5f)
            canvas.drawRect(r, fill)
        }
        finder(0f, 0f); finder(6 * u, 0f); finder(0f, 6 * u)
        for ((x, y) in listOf(6 to 6, 8 to 7, 7 to 9, 9 to 9, 5 to 8)) {
            r.set(b.left + x * u, b.top + y * u, b.left + (x + 1) * u, b.top + (y + 1) * u); canvas.drawRect(r, fill)
        }
    }

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx
    override fun setAlpha(alpha: Int) { stroke.alpha = alpha; fill.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { stroke.colorFilter = colorFilter; fill.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
