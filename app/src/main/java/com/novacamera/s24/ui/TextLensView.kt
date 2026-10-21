package com.novacamera.s24.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.novacamera.s24.core.TextBlocks

/**
 * Texto ao vivo (20/10: "tem que ter cara de premium", referência iPhone). A prévia congela e escurece; uma
 * faixa de luz varre enquanto o leitor trabalha; o texto lido acende em blocos (linhas do mesmo parágrafo
 * juntas, sem caixas repetidas, sem bordas), cada bloco recebe um brilho que passa da esquerda para a direita.
 * Toque num bloco marca/desmarca (azul suave); toque e segure copia só ele. O quadro congelado está nas
 * coordenadas da prévia (mesmo tamanho desta View), então o destaque cai em cima das letras.
 */
@SuppressLint("ViewConstructor")
class TextLensView(context: Context) : View(context) {
    private class Item(val block: TextBlocks.Block) {
        val rects = block.lines.map { RectF(it.box.l, it.box.t, it.box.r, it.box.b) }
        val bounds = RectF(block.box.l, block.box.t, block.box.r, block.box.b)
        var selected = false
        var reveal = 0f
        var shine = -1f
    }

    var onSelection: ((Int) -> Unit)? = null
    var onCopyOne: ((String) -> Unit)? = null
    private var frame: Bitmap? = null
    private val items = ArrayList<Item>()
    private var scanning = false
    private var sweep = 0f
    private var sweepAnim: ValueAnimator? = null
    private var dim = 0f
    private val d = resources.displayMetrics.density

    private val framePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scrim = Paint().apply { color = 0xFF050A14.toInt() }
    private val sweepPaint = Paint()
    private val lift = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF }
    private val selectedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4D48BFFF }
    private val selectedEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * d; color = 0xCC84DCFF.toInt() }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shineMatrix = Matrix()
    private val shineShader = LinearGradient(0f, 0f, 1f, 0f, intArrayOf(0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val clip = Path()
    private val tmp = RectF()
    private val pad get() = 3f * d
    private val radius get() = 7f * d

    private var pressed: Item? = null
    private val longPress = Runnable {
        pressed?.let { onCopyOne?.invoke(it.block.text); performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) }
        pressed = null
    }

    fun begin(frozen: Bitmap) {
        frame?.recycle(); frame = frozen
        items.clear(); scanning = true
        ValueAnimator.ofFloat(0f, 0.38f).apply { duration = 260; addUpdateListener { dim = it.animatedValue as Float; invalidate() } }.start()
        sweepAnim?.cancel()
        sweepAnim = ValueAnimator.ofFloat(-0.1f, 1.1f).apply {
            duration = 1200; repeatCount = ValueAnimator.INFINITE; interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { sweep = it.animatedValue as Float; invalidate() }
            start()
        }
        visibility = VISIBLE
    }

    /** Linhas em coordenadas desta View; viram blocos e entram de cima para baixo. */
    fun showLines(lines: List<Pair<String, RectF>>) {
        scanning = false; sweepAnim?.cancel()
        items.clear()
        TextBlocks.group(lines.map { (t, r) -> TextBlocks.Line(t, TextBlocks.Rect(r.left, r.top, r.right, r.bottom)) }).forEach { items += Item(it) }
        ValueAnimator.ofFloat(dim, 0.6f).apply { duration = 280; addUpdateListener { dim = it.animatedValue as Float; invalidate() } }.start()
        items.forEachIndexed { i, it ->
            ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = 45L * i; duration = 240; interpolator = DecelerateInterpolator()
                addUpdateListener { a -> it.reveal = a.animatedValue as Float; invalidate() }
                start()
            }
            ValueAnimator.ofFloat(-0.4f, 1.4f).apply {
                startDelay = 45L * i + 120; duration = 650; interpolator = DecelerateInterpolator()
                addUpdateListener { a -> it.shine = a.animatedValue as Float; invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(animation: android.animation.Animator) { it.shine = -1f } })
                start()
            }
        }
        onSelection?.invoke(0)
    }

    fun selectAll(on: Boolean) { items.forEach { it.selected = on }; invalidate(); onSelection?.invoke(selectedCount()) }
    fun selectedCount() = items.count { it.selected }
    /** Texto marcado (ou tudo, se nada marcado), na ordem de leitura. */
    fun text(): String = (items.filter { it.selected }.ifEmpty { items }).joinToString("\n\n") { it.block.text }
    val blockCount get() = items.size

    fun end() {
        sweepAnim?.cancel(); scanning = false
        removeCallbacks(longPress)
        visibility = GONE
        frame?.recycle(); frame = null; items.clear()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = items.lastOrNull { tmp.set(it.bounds); tmp.inset(-6f * d, -6f * d); tmp.contains(event.x, event.y) }
                if (pressed != null) postDelayed(longPress, android.view.ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val hit = pressed ?: return true
                pressed = null
                hit.selected = !hit.selected
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                invalidate(); onSelection?.invoke(selectedCount())
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); pressed = null }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val f = frame ?: return
        tmp.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(f, null, tmp, framePaint)
        scrim.alpha = (255 * dim).toInt()
        // Escurece tudo menos as linhas lidas (recortes arredondados, sem borda).
        canvas.save()
        clip.reset()
        items.forEach { it -> if (it.reveal > 0.01f) it.rects.forEach { r ->
            tmp.set(r); tmp.inset(-pad * it.reveal, -pad * it.reveal); clip.addRoundRect(tmp, radius, radius, Path.Direction.CW) } }
        if (!clip.isEmpty) canvas.clipOutPath(clip)
        tmp.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRect(tmp, scrim)
        canvas.restore()
        if (scanning) {
            val y = height * sweep
            val band = 140f * d
            sweepPaint.shader = LinearGradient(0f, y - band, 0f, y, intArrayOf(0x0048BFFF, 0x3348BFFF, 0xAAE6F7FF.toInt()),
                floatArrayOf(0f, 0.8f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, y - band, width.toFloat(), y + 1.5f * d, sweepPaint)
        }
        items.forEach { it ->
            if (it.reveal <= 0.01f) return@forEach
            lift.alpha = (20 * it.reveal).toInt()
            it.rects.forEach { r -> tmp.set(r); tmp.inset(-pad, -pad); canvas.drawRoundRect(tmp, radius, radius, if (it.selected) selectedFill else lift) }
            if (it.selected) { tmp.set(it.bounds); tmp.inset(-pad - 2f * d, -pad - 2f * d); canvas.drawRoundRect(tmp, radius + 2f * d, radius + 2f * d, selectedEdge) }
            if (it.shine >= -0.4f) {
                // Brilho diagonal que atravessa o bloco uma vez ao aparecer.
                val b = it.bounds; val span = b.width() + b.height()
                shineMatrix.reset(); shineMatrix.setScale(span * 0.5f, 1f); shineMatrix.postRotate(-20f)
                shineMatrix.postTranslate(b.left - span * 0.25f + span * it.shine, b.top)
                shineShader.setLocalMatrix(shineMatrix); shinePaint.shader = shineShader
                canvas.save(); clip.reset()
                it.rects.forEach { r -> tmp.set(r); tmp.inset(-pad, -pad); clip.addRoundRect(tmp, radius, radius, Path.Direction.CW) }
                canvas.clipPath(clip); canvas.drawRect(b.left - pad, b.top - pad, b.right + pad, b.bottom + pad, shinePaint); canvas.restore()
            }
        }
    }
}
