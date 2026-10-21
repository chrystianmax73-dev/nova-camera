package com.novacamera.s24.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Aviso do código lido, junto do código no visor: ícone num círculo com o degradê do app, o essencial em
 * destaque (domínio, rede, número) e a ação embaixo, seta à direita. Vidro escuro com borda clara de 1 px.
 */
class CodePill(context: Context) : LinearLayout(context) {
    private val d = resources.displayMetrics.density
    private fun px(v: Float) = (v * d).toInt()
    private val iconView = ImageView(context)
    private val title = TextView(context).apply {
        setTextColor(Nova.TEXT); textSize = 15f; typeface = Nova.MEDIUM; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        maxWidth = (230 * resources.displayMetrics.density).toInt()
    }
    private val subtitle = TextView(context).apply { setTextColor(Nova.TEXT_MUTED); textSize = 12f; maxLines = 1 }

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(px(8f), px(8f), px(12f), px(8f))
        background = GradientDrawable().apply {
            cornerRadius = 28f * d; setColor(0xE0101828.toInt()); setStroke(px(1f), 0x33FFFFFF)
        }
        elevation = 12f * d
        val circle = FrameLayout(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF6FD3FF.toInt(), 0xFF2F7BFF.toInt())).apply { shape = GradientDrawable.OVAL }
            addView(iconView, FrameLayout.LayoutParams(px(20f), px(20f), Gravity.CENTER))
        }
        addView(circle, LayoutParams(px(38f), px(38f)))
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(title); addView(subtitle)
        }
        addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(12f); marginEnd = px(8f) })
        addView(ImageView(context).apply { setImageDrawable(LineIcon(LineIcon.Kind.CHEVRON, px(18f), Nova.TEXT_MUTED)) }, LayoutParams(px(18f), px(18f)))
    }

    fun setContent(kind: LineIcon.Kind, main: String, action: String, warn: Boolean = false) {
        iconView.setImageDrawable(LineIcon(kind, px(20f), 0xFFFFFFFF.toInt()))
        title.text = main
        subtitle.text = action
        subtitle.setTextColor(if (warn) Nova.WARN else Nova.TEXT_MUTED)
    }
}
