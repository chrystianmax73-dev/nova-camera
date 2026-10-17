package com.novacamera.s24.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator

/** Tokens visuais da Nova Camera: identidade própria, escura, azul elétrico como acento. */
object Nova {
    const val BG = 0xFF070D19.toInt()
    const val SURFACE = 0xFF142338.toInt()
    const val SURFACE_GLASS = 0xB3101B2C.toInt()
    const val SCRIM = 0x8C000000.toInt()
    const val ACCENT = 0xFF48BFFF.toInt()
    const val ACCENT_SOFT = 0xFF84DCFF.toInt()
    const val TEXT = 0xFFEDF7FF.toInt()
    const val TEXT_MUTED = 0xFF8FA3B8.toInt()
    const val TEXT_DISABLED = 0x668FA3B8
    const val LOCK = 0xFFFFC94D.toInt()
    const val REC = 0xFFFF3B4E.toInt()
    const val WARN = 0xFFFFB547.toInt()

    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val CONDENSED: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    /** Curva padrão: saída rápida, chegada suave (transições de câmera não devem "quicar"). */
    val EASE = PathInterpolator(0.2f, 0f, 0f, 1f)
    const val FAST = 160L
    const val NORMAL = 260L

    fun pill(color: Int, radiusPx: Float) = GradientDrawable().apply {
        cornerRadius = radiusPx
        setColor(color)
    }
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(v: Float): Float = context.dp(v)
fun View.dpi(v: Int): Int = context.dpi(v)

/** Retorno tátil discreto, respeitando a preferência do usuário. */
object Haptics {
    var enabled = true
    fun tick(v: View) { if (enabled) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun tap(v: View) { if (enabled) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun confirm(v: View) {
        if (!enabled) return
        v.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }
    fun heavy(v: View) { if (enabled) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}
