package com.novacamera.s24.core

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Localização na foto (opção "Salvar local", desligada por padrão). A tela da câmera alimenta [update] com
 * a última posição do próprio Android (sem internet do app); cada foto copia a posição **no disparo**
 * ([tagsAt], chamado quando o trabalho é criado), não a do fim do processamento. Posição velha
 * (> [MAX_AGE_MS]) ou imprecisa (> [MAX_ACCURACY_M]) não entra: melhor sem local do que local errado.
 */
object GeoTag {
    const val MAX_AGE_MS = 10 * 60_000L
    const val MAX_ACCURACY_M = 500f

    class Fix(val lat: Double, val lon: Double, val altitude: Double?, val accuracyM: Float, val utcMs: Long, val elapsedMs: Long)

    @Volatile var enabled = false
    @Volatile private var fix: Fix? = null

    fun update(f: Fix) {
        val old = fix
        // Uma posição mais nova só perde para a anterior se for bem pior e a anterior ainda for recente.
        if (old != null && f.accuracyM > old.accuracyM * 4 && f.elapsedMs - old.elapsedMs < 60_000L) return
        fix = f
    }

    fun clear() { fix = null }

    fun current(nowElapsedMs: Long): Fix? = fix?.takeIf {
        enabled && nowElapsedMs - it.elapsedMs in 0..MAX_AGE_MS && it.accuracyM <= MAX_ACCURACY_M
    }

    fun tagsAt(nowElapsedMs: Long): Map<String, String> = current(nowElapsedMs)?.let(::tags).orEmpty()

    /** Para o disparo: vazio sem tocar no relógio do Android quando a opção está desligada (testes de JVM). */
    fun tagsNow(): Map<String, String> = if (!enabled || fix == null) emptyMap() else tagsAt(android.os.SystemClock.elapsedRealtime())

    /** Tags GPS do EXIF (graus/minutos/segundos racionais, como o ExifInterface grava). */
    fun tags(f: Fix): Map<String, String> {
        val out = linkedMapOf(
            "GPSLatitude" to dms(f.lat), "GPSLatitudeRef" to if (f.lat >= 0) "N" else "S",
            "GPSLongitude" to dms(f.lon), "GPSLongitudeRef" to if (f.lon >= 0) "E" else "W",
        )
        f.altitude?.let {
            out["GPSAltitude"] = "${abs(it * 100).roundToLong()}/100"
            out["GPSAltitudeRef"] = if (it >= 0) "0" else "1"
        }
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = f.utcMs }
        out["GPSDateStamp"] = "%04d:%02d:%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        out["GPSTimeStamp"] = "${c.get(Calendar.HOUR_OF_DAY)}/1,${c.get(Calendar.MINUTE)}/1,${c.get(Calendar.SECOND)}/1"
        return out
    }

    private fun dms(value: Double): String {
        val v = abs(value)
        val d = v.toInt()
        val mFull = (v - d) * 60
        val m = mFull.toInt()
        val s = ((mFull - m) * 60 * 10_000).roundToLong()
        return "$d/1,$m/1,$s/10000"
    }
}
