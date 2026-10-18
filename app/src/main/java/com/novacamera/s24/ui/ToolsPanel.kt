package com.novacamera.s24.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Painel de ferramentas (botão no canto superior direito): reúne os controles que antes ocupavam a
 * barra superior inteira. As ferramentas são os MESMOS botões de antes (mesma ação, mesmo estado);
 * aqui só ganham rótulo. Uma ferramenta que não se aplica ao modo/lente atual some do painel ([sync]).
 */
class ToolsPanel(context: Context) : LinearLayout(context) {
    private class Tool(val view: View, val cell: LinearLayout)
    private val tools = mutableListOf<Tool>()
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        setPadding(dpi(12), dpi(14), dpi(12), dpi(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(26f)
            setColor(0xF5101B2C.toInt())
            setStroke(dpi(1), 0x22FFFFFF)
        }
        isClickable = true
        addView(TextView(context).apply {
            text = "Ferramentas"
            textSize = 13f; typeface = Nova.MEDIUM; letterSpacing = 0.02f
            setTextColor(Nova.TEXT)
            setPadding(dpi(6), 0, 0, dpi(10))
        })
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** [view] continua controlado por quem o criou (texto, ícone, visibilidade, clique). */
    fun add(label: String, view: View) {
        val holder = FrameLayout(context).apply {
            background = Nova.pill(0x14FFFFFF, dp(16f))
            val size = if (view is NovaIconView) dpi(44) else FrameLayout.LayoutParams.WRAP_CONTENT
            addView(view, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        }
        val cell = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(4), 0, dpi(4), dpi(8))
            addView(holder, LayoutParams(LayoutParams.MATCH_PARENT, dpi(52)))
            addView(TextView(context).apply {
                text = label
                textSize = 11f; typeface = Nova.MEDIUM; letterSpacing = 0.02f
                gravity = Gravity.CENTER
                setTextColor(Nova.TEXT_MUTED)
                setPadding(0, dpi(5), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        }
        tools += Tool(view, cell)
    }

    /** Mostra só as ferramentas visíveis, em linhas de quatro, sem buracos. */
    fun sync() {
        grid.removeAllViews()
        tools.forEach { (it.cell.parent as? LinearLayout)?.removeView(it.cell) }
        tools.filter { it.view.visibility == View.VISIBLE }.chunked(4).forEach { row ->
            val line = LinearLayout(context).apply { orientation = HORIZONTAL }
            row.forEach { line.addView(it.cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)) }
            repeat(4 - row.size) { line.addView(View(context), LayoutParams(0, 1, 1f)) }
            grid.addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }
}
