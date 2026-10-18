package com.novacamera.s24.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.novacamera.s24.core.CaptureMode
import com.novacamera.s24.core.ModeSupport
import com.novacamera.s24.ui.NovaIconView.Icon

/**
 * Painel MAIS: modos adicionais em grade. Cada célula mostra o estado real neste aparelho:
 * disponível, "em breve" (sem implementação ainda) ou "indisponível" (o hardware/fabricante não libera).
 */
class ModeGrid(context: Context) : LinearLayout(context) {
    var onPick: ((CaptureMode) -> Unit)? = null

    init {
        orientation = VERTICAL
        setPadding(dpi(12), dpi(16), dpi(12), dpi(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(26f)
            setColor(0xF5101B2C.toInt())
            setStroke(dpi(1), 0x22FFFFFF)
        }
        isClickable = true
    }

    fun bind(modes: List<Pair<CaptureMode, ModeSupport>>, current: CaptureMode) {
        removeAllViews()
        modes.chunked(4).forEach { rowModes ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            rowModes.forEach { (mode, support) -> row.addView(cell(mode, support, mode == current), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)) }
            repeat(4 - rowModes.size) { row.addView(android.view.View(context), LayoutParams(0, 1, 1f)) }
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(6) })
        }
    }

    private fun cell(mode: CaptureMode, support: ModeSupport, active: Boolean): LinearLayout {
        val inDevelopment = support.note.startsWith("Em desenvolvimento")
        val enabled = support.canCapture
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpi(8), 0, dpi(8))
            alpha = if (enabled) 1f else 0.42f
            addView(NovaIconView(context, iconFor(mode)).apply {
                tint = if (active) Nova.ACCENT_SOFT else Nova.TEXT
                isClickable = false
                background = Nova.pill(if (active) 0x3348BFFF else 0x1AFFFFFF, dp(22f))
            }, LayoutParams(dpi(44), dpi(44)))
            addView(TextView(context).apply {
                text = mode.label
                textSize = 11f; typeface = Nova.MEDIUM; letterSpacing = 0.04f
                gravity = Gravity.CENTER
                setTextColor(if (active) Nova.ACCENT_SOFT else Nova.TEXT)
                setPadding(0, dpi(6), 0, 0)
            })
            if (!enabled) addView(TextView(context).apply {
                text = if (inDevelopment) "em breve" else "indisponível"
                textSize = 9.5f; gravity = Gravity.CENTER; setTextColor(Nova.TEXT_MUTED)
            })
            contentDescription = mode.label + if (enabled) "" else ". ${support.note}"
            setOnClickListener { Haptics.tick(this); onPick?.invoke(mode) }
        }
    }

    private fun iconFor(mode: CaptureMode) = when (mode) {
        CaptureMode.PRO -> Icon.MODE_PRO
        CaptureMode.VIDEO_PRO -> Icon.MODE_VIDEO_PRO
        CaptureMode.NOITE -> Icon.MODE_NIGHT
        CaptureMode.CAMERA_LENTA -> Icon.MODE_SLOWMO
        CaptureMode.PANORAMA -> Icon.MODE_PANORAMA
        CaptureMode.TIMELAPSE -> Icon.MODE_TIMELAPSE
        CaptureMode.FOOD -> Icon.MODE_FOOD
        CaptureMode.VIDEO_RETRATO -> Icon.MODE_PORTRAIT_VIDEO
        else -> Icon.MODE_PRO
    }
}
