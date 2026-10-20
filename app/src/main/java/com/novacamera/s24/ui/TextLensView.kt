package com.novacamera.s24.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Leitura de texto como a da Samsung: a prévia congela, uma faixa de luz varre a imagem enquanto o leitor
 * trabalha, e as linhas lidas acendem uma a uma (o resto escurece). Toque numa linha seleciona/desmarca;
 * [onSelection] avisa quantas estão marcadas. O quadro congelado está nas coordenadas da prévia (mesmo
 * tamanho desta View), então as caixas caem exatamente sobre as letras.
 */
@SuppressLint("ViewConstructor")
class TextLensView(context: Context) : View(context) {
    class Item(val text: String, val box: RectF) { var selected = false; var reveal = 0f }

    var onSelection: ((Int) -> Unit)? = null
    var onEmptyTap: (() -> Unit)? = null
    private var frame: Bitmap? = null
    private val items = ArrayList<Item>()
    private var scanning = false
    private var sweep = 0f
    private var sweepAnim: ValueAnimator? = null
    private var dim = 0f
    private val d = resources.displayMetrics.density

    private val framePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scrim = Paint().apply { color = 0xFF000000.toInt() }
    private val sweepPaint = Paint()
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.6f * d; color = 0xFFFFFFFF.toInt()
        setShadowLayer(6f * d, 0f, 0f, 0x8848BFFF.toInt())
    }
    private val selectedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x5548BFFF }
    private val clip = Path()
    private val tmp = RectF()

    fun begin(frozen: Bitmap) {
        frame?.recycle(); frame = frozen
        items.clear(); scanning = true
        ValueAnimator.ofFloat(0f, 0.35f).apply { duration = 250; addUpdateListener { dim = it.animatedValue as Float; invalidate() } }.start()
        sweepAnim?.cancel()
        sweepAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100; repeatCount = ValueAnimator.INFINITE; interpolator = DecelerateInterpolator()
            addUpdateListener { sweep = it.animatedValue as Float; invalidate() }
            start()
        }
        visibility = VISIBLE
    }

    /** Linhas em coordenadas desta View. Entram em sequência (35 ms de diferença), de cima para baixo. */
    fun showLines(lines: List<Pair<String, RectF>>) {
        scanning = false; sweepAnim?.cancel()
        items.clear()
        lines.forEach { (t, r) -> items += Item(t, RectF(r).apply { inset(-3f * d, -3f * d) }) }
        ValueAnimator.ofFloat(dim, 0.62f).apply { duration = 260; addUpdateListener { dim = it.animatedValue as Float; invalidate() } }.start()
        items.forEachIndexed { i, it ->
            ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = 35L * i; duration = 260; interpolator = OvershootInterpolator(1.2f)
                addUpdateListener { a -> it.reveal = a.animatedValue as Float; invalidate() }
                start()
            }
        }
        onSelection?.invoke(0)
    }

    fun selectAll(on: Boolean) { items.forEach { it.selected = on }; invalidate(); onSelection?.invoke(selectedCount()) }
    fun selectedCount() = items.count { it.selected }
    /** Texto marcado (ou tudo, se nada marcado), na ordem de leitura. */
    fun text(): String = (items.filter { it.selected }.ifEmpty { items }).joinToString("\n") { it.text }
    val lineCount get() = items.size

    fun end() {
        sweepAnim?.cancel(); scanning = false
        visibility = GONE
        frame?.recycle(); frame = null; items.clear()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val hit = items.lastOrNull { it.box.contains(event.x, event.y) }
        if (hit == null) { onEmptyTap?.invoke(); return true }
        hit.selected = !hit.selected
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        invalidate(); onSelection?.invoke(selectedCount())
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val f = frame ?: return
        tmp.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(f, null, tmp, framePaint)
        scrim.alpha = (255 * dim).toInt()
        // Escurece tudo menos as linhas lidas (as caixas recortam o escurecimento).
        canvas.save()
        clip.reset()
        items.forEach { if (it.reveal > 0.01f) clip.addRoundRect(it.box, 6f * d, 6f * d, Path.Direction.CW) }
        if (!clip.isEmpty) canvas.clipOutPath(clip)
        canvas.drawRect(tmp, scrim)
        canvas.restore()
        if (scanning) {
            val y = height * sweep
            val band = 120f * d
            sweepPaint.shader = LinearGradient(0f, y - band, 0f, y, intArrayOf(0x0048BFFF, 0x6648BFFF, 0xCCFFFFFF.toInt()),
                floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, y - band, width.toFloat(), y + 2f * d, sweepPaint)
        }
        items.forEach { it ->
            if (it.reveal <= 0.01f) return@forEach
            val r = it.box
            val k = 0.92f + 0.08f * it.reveal
            tmp.set(r.centerX() - r.width() / 2 * k, r.centerY() - r.height() / 2 * k, r.centerX() + r.width() / 2 * k, r.centerY() + r.height() / 2 * k)
            if (it.selected) canvas.drawRoundRect(tmp, 6f * d, 6f * d, selectedFill)
            box.alpha = (255 * it.reveal.coerceAtMost(1f)).toInt()
            box.color = if (it.selected) 0xFF84DCFF.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawRoundRect(tmp, 6f * d, 6f * d, box)
        }
    }
}
