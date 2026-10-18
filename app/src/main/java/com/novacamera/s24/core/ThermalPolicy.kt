package com.novacamera.s24.core

import java.util.Locale

/** Estado térmico e de energia lido do Android (sem atribuir a causa ao aplicativo). */
data class DeviceHeat(
    /** PowerManager.THERMAL_STATUS_* (0 = nenhum … 6 = desligamento). */
    val thermalStatus: Int,
    val batteryTempC: Float?,
    val charging: Boolean,
    val powerSave: Boolean,
    val batteryPercent: Int,
)

/**
 * Política conservadora: o aviso visível só aparece quando o próprio Android declara
 * aquecimento severo; estados leves/moderados apenas ajustam limites em silêncio.
 */
object ThermalPolicy {
    const val NONE = 0
    const val LIGHT = 1
    const val MODERATE = 2
    const val SEVERE = 3
    const val CRITICAL = 4

    fun statusName(status: Int) = when (status) {
        0 -> "nenhum"; 1 -> "leve"; 2 -> "moderado"; 3 -> "severo"; 4 -> "crítico"
        5 -> "emergência"; 6 -> "desligamento"; else -> "desconhecido"
    }

    fun showBadge(h: DeviceHeat) = h.thermalStatus >= SEVERE

    fun badgeText(h: DeviceHeat) = if (h.charging) "Aparelho quente · carregando" else "Aparelho quente"

    /** Evita 4K/60 fps quando o sistema pede economia ou declara aquecimento severo. */
    fun limitVideo(h: DeviceHeat) = h.thermalStatus >= SEVERE || h.powerSave

    /** Finaliza a gravação de forma íntegra antes que o sistema force o fechamento da câmera. */
    fun stopRecording(h: DeviceHeat) = h.thermalStatus >= CRITICAL

    fun describe(h: DeviceHeat): String = buildString {
        append("Estado térmico do Android: ${statusName(h.thermalStatus)} (${h.thermalStatus})")
        h.batteryTempC?.let { append(String.format(Locale.US, " · bateria %.1f °C", it)) }
        append(if (h.charging) " · carregando" else " · na bateria")
        if (h.powerSave) append(" · economia de energia ativa")
        if (h.charging && h.thermalStatus >= MODERATE)
            append("\nObservação: o carregamento aquece o aparelho; o estado térmico é do sistema inteiro, não só da câmera.")
    }
}
