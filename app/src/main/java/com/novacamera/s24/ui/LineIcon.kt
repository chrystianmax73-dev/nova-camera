package com.novacamera.s24.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Ícones de traço (sem imagens embutidas) para as ações de QR e texto, no estilo dos ícones do app. */
class LineIcon(private val kind: Kind, private val sizePx: Int, color: Int) : Drawable() {
    enum class Kind { COPY, SELECT_ALL, SHARE, LINK, CLOSE, WIFI, PHONE, MAIL, TEXT, CHEVRON, PERSON, PET, FOOD, PLANT }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; this.color = color; strokeWidth = sizePx / 12f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
    private val r = RectF()
    private val path = Path()

    override fun draw(c: Canvas) {
        val b = bounds; val s = b.width().toFloat(); val x = b.left.toFloat(); val y = b.top.toFloat(); val u = s / 24f
        fun rr(l: Float, t: Float, rt: Float, bt: Float, rad: Float) { r.set(x + l * u, y + t * u, x + rt * u, y + bt * u); c.drawRoundRect(r, rad * u, rad * u, p) }
        fun line(a: Float, bb: Float, cc: Float, d: Float) = c.drawLine(x + a * u, y + bb * u, x + cc * u, y + d * u, p)
        when (kind) {
            Kind.COPY -> { rr(8f, 8f, 20f, 20f, 3f); path.reset(); path.moveTo(x + 4 * u, y + 15 * u); path.lineTo(x + 4 * u, y + 6 * u)
                path.quadTo(x + 4 * u, y + 4 * u, x + 6 * u, y + 4 * u); path.lineTo(x + 15 * u, y + 4 * u); c.drawPath(path, p) }
            Kind.SELECT_ALL -> { p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2.5f * u, 2.5f * u), 0f); rr(3f, 3f, 21f, 21f, 3f)
                p.pathEffect = null; line(8f, 12.5f, 11f, 15.5f); line(11f, 15.5f, 16.5f, 9f) }
            Kind.SHARE -> { line(12f, 3.5f, 12f, 15f); line(7.5f, 8f, 12f, 3.5f); line(16.5f, 8f, 12f, 3.5f)
                path.reset(); path.moveTo(x + 6 * u, y + 11 * u); path.lineTo(x + 5 * u, y + 11 * u); path.lineTo(x + 5 * u, y + 20.5f * u)
                path.lineTo(x + 19 * u, y + 20.5f * u); path.lineTo(x + 19 * u, y + 11 * u); path.lineTo(x + 18 * u, y + 11 * u); c.drawPath(path, p) }
            Kind.LINK -> { c.save(); c.rotate(-45f, b.exactCenterX(), b.exactCenterY())
                rr(2.5f, 8.5f, 13f, 15.5f, 3.5f); rr(11f, 8.5f, 21.5f, 15.5f, 3.5f); c.restore() }
            Kind.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Kind.WIFI -> { for ((i, rad) in listOf(4f, 8f, 12f).withIndex()) { r.set(x + (12 - rad) * u, y + (19 - rad) * u, x + (12 + rad) * u, y + (19 + rad) * u)
                if (i > 0) c.drawArc(r, 225f, 90f, false, p) }; c.drawCircle(x + 12 * u, y + 18.5f * u, 1.6f * u, fill) }
            Kind.PHONE -> { path.reset(); path.moveTo(x + 7 * u, y + 3.5f * u); path.lineTo(x + 10 * u, y + 8.5f * u); path.lineTo(x + 8 * u, y + 10.5f * u)
                path.quadTo(x + 10 * u, y + 14.5f * u, x + 13.5f * u, y + 16 * u); path.lineTo(x + 15.5f * u, y + 14 * u); path.lineTo(x + 20.5f * u, y + 17 * u)
                path.lineTo(x + 19 * u, y + 20.5f * u); path.quadTo(x + 5 * u, y + 19 * u, x + 3.5f * u, y + 5 * u); path.close(); c.drawPath(path, p) }
            Kind.MAIL -> { rr(3f, 5.5f, 21f, 18.5f, 2.5f); line(3.5f, 6.5f, 12f, 13f); line(20.5f, 6.5f, 12f, 13f) }
            Kind.TEXT -> { line(5f, 5f, 19f, 5f); line(12f, 5f, 12f, 19f); line(9f, 19f, 15f, 19f) }
            Kind.CHEVRON -> { line(9.5f, 6f, 15.5f, 12f); line(15.5f, 12f, 9.5f, 18f) }
            Kind.PERSON -> { c.drawCircle(x + 12 * u, y + 8 * u, 3.6f * u, p)
                r.set(x + 5 * u, y + 13.5f * u, x + 19 * u, y + 27.5f * u); c.drawArc(r, 180f, 180f, false, p) }
            Kind.PET -> { c.drawOval(x + 7.5f * u, y + 12.5f * u, x + 16.5f * u, y + 20 * u, p)
                for ((cx, cy) in listOf(5.5f to 10f, 9.5f to 6f, 14.5f to 6f, 18.5f to 10f)) c.drawCircle(x + cx * u, y + cy * u, 1.9f * u, fill) }
            Kind.FOOD -> { line(7f, 3.5f, 7f, 20.5f); line(4.5f, 3.5f, 4.5f, 8.5f); line(9.5f, 3.5f, 9.5f, 8.5f)
                path.reset(); path.moveTo(x + 4.5f * u, y + 8.5f * u); path.quadTo(x + 7f * u, y + 11f * u, x + 9.5f * u, y + 8.5f * u); c.drawPath(path, p)
                path.reset(); path.moveTo(x + 17 * u, y + 20.5f * u); path.lineTo(x + 17 * u, y + 3.5f * u)
                path.quadTo(x + 13 * u, y + 7 * u, x + 13.5f * u, y + 12.5f * u); path.lineTo(x + 17 * u, y + 12.5f * u); c.drawPath(path, p) }
            Kind.PLANT -> { path.reset(); path.moveTo(x + 5 * u, y + 19 * u); path.quadTo(x + 4 * u, y + 5 * u, x + 19 * u, y + 4.5f * u)
                path.quadTo(x + 20 * u, y + 19 * u, x + 5 * u, y + 19 * u); c.drawPath(path, p); line(5f, 19f, 14f, 10f) }
        }
    }

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx
    override fun setAlpha(alpha: Int) { p.alpha = alpha; fill.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter = colorFilter; fill.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
