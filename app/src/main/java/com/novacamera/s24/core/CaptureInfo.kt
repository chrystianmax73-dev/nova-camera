package com.novacamera.s24.core

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Linha de dados da captura para a assinatura, só com o que a própria câmera gravou no EXIF:
 * distância focal equivalente (35 mm), abertura, tempo de exposição e ISO. Campo ausente fica de
 * fora — nada é estimado nem atribuído a outro aparelho.
 */
object CaptureInfo {
    const val FOCAL_35 = "FocalLengthIn35mmFilm"
    const val F_NUMBER = "FNumber"
    const val EXPOSURE = "ExposureTime"
    const val ISO = "PhotographicSensitivity"

    fun line(exif: Map<String, String>): String? {
        val parts = listOfNotNull(
            exif[FOCAL_35]?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "${it.roundToInt()} mm" },
            rational(exif[F_NUMBER])?.takeIf { it > 0 }?.let { "f/" + String.format(Locale.US, "%.1f", it) },
            rational(exif[EXPOSURE])?.takeIf { it > 0 }?.let(::shutter),
            exif[ISO]?.split(',')?.firstOrNull()?.trim()?.toIntOrNull()?.takeIf { it > 0 }?.let { "ISO $it" },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString("   ")
    }

    /** "1/120 s" abaixo de meio segundo; acima, segundos com uma casa ("0,5 s", "2 s"). */
    fun shutter(seconds: Double): String =
        if (seconds < 0.5) "1/${(1.0 / seconds).roundToInt()} s"
        else String.format(Locale("pt", "BR"), if (seconds % 1.0 == 0.0) "%.0f s" else "%.1f s", seconds)

    /** EXIF grava racionais como "17/10" ou decimais como "1.7". */
    private fun rational(v: String?): Double? {
        if (v == null) return null
        val p = v.split('/')
        return if (p.size == 2) p[0].trim().toDoubleOrNull()?.let { n -> p[1].trim().toDoubleOrNull()?.takeIf { it != 0.0 }?.let { n / it } }
               else v.trim().toDoubleOrNull()
    }
}
