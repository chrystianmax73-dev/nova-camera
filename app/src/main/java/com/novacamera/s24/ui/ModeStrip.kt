package com.novacamera.s24.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Seletor de modos em texto: o item ativo fica centralizado em acento. Toque num rótulo ou
 * deslize sobre a faixa para trocar. Itens sem implementação real aparecem esmaecidos.
 */
class ModeStrip(context: Context) : View(context) {
    var labels: List<String> = listOf("FOTO")
        set(value) { field = value; invalidate() }
    var selectedIndex = 0
        set(value) {
            if (field == value) return
            field = value
            animateTo(value.toFloat())
            contentDescription = "Modo ${labels.getOrNull(value)}"
        }
    /** Índices sem implementação real neste aparelho. */
    var unavailable: Set<Int> = emptySet()
        set(value) { field = value; invalidate() }
    var onSelect: ((Int) -> Unit)? = null

    private var position = 0f
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Nova.MEDIUM
        letterSpacing = 0.08f
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Nova.ACCENT }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var swiped = false
    private var animator: ValueAnimator? = null

    init {
        isClickable = true
        contentDescription = "Seletor de modo"
    }

    /** Posiciona sem animar (ex.: ao restaurar o último modo). */
    fun jumpTo(index: Int) {
        animator?.cancel()
        selectedIndex = index
        animator?.cancel()
        position = index.toFloat()
        invalidate()
    }

    /** Espaçamento pela largura real dos rótulos: modos vizinhos ficam a uma distância visual constante. */
    private fun centers(): FloatArray {
        text.textSize = dp(13f)
        val gap = dp(26f)
        val out = FloatArray(labels.size)
        for (i in 1 until labels.size) {
            out[i] = out[i - 1] + text.measureText(labels[i - 1]) / 2f + gap + text.measureText(labels[i]) / 2f
        }
        return out
    }

    /** Posição contínua (índice fracionário) convertida para deslocamento em px. */
    private fun offsetFor(position: Float, c: FloatArray): Float {
        val i = position.toInt().coerceIn(0, c.size - 1)
        val next = (i + 1).coerceAtMost(c.size - 1)
        return c[i] + (c[next] - c[i]) * (position - i)
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(position, target).apply {
            duration = Nova.NORMAL; interpolator = Nova.EASE
            addUpdateListener { position = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f - dp(3f)
        val c = centers()
        val origin = offsetFor(position, c)
        val fade = dp(48f)
        labels.forEachIndexed { i, label ->
            val x = cx + c[i] - origin
            val half = text.measureText(label) / 2f
            // Esmaece perto das bordas em vez de cortar o rótulo.
            val edge = minOf(x - half, width - (x + half))
            val edgeAlpha = (edge / fade).coerceIn(0f, 1f)
            if (edgeAlpha <= 0f) return@forEachIndexed
            val closeness = (1f - abs(i - position)).coerceIn(0f, 1f)
            val base = if (i in unavailable) Nova.TEXT_DISABLED else Nova.TEXT
            text.color = if (closeness > 0.5f) Nova.ACCENT_SOFT else base
            val alpha = if (i in unavailable && closeness <= 0.5f) 110 else 255
            text.alpha = (alpha * edgeAlpha).toInt()
            canvas.drawText(label, x, cy - (text.descent() + text.ascent()) / 2f, text)
        }
        dot.alpha = 255
        canvas.drawCircle(cx, cy + dp(13f), dp(2f), dot)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; swiped = false }
            MotionEvent.ACTION_MOVE -> {
                if (!swiped && abs(event.x - downX) > slop * 2) {
                    swiped = true
                    val dir = if (event.x < downX) 1 else -1
                    choose((selectedIndex + dir).coerceIn(0, labels.size - 1))
                }
            }
            MotionEvent.ACTION_UP -> if (!swiped) {
                val c = centers()
                val origin = c[selectedIndex.coerceIn(0, c.size - 1)]
                val tapped = event.x - width / 2f + origin
                val idx = c.indices.minByOrNull { abs(c[it] - tapped) } ?: selectedIndex
                performClick()
                // Tocar no item já ativo também notifica (ex.: reabrir o painel MAIS).
                Haptics.tick(this)
                onSelect?.invoke(idx)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun choose(i: Int) {
        if (i == selectedIndex) return
        Haptics.tick(this)
        onSelect?.invoke(i)
    }
}
