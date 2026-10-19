package com.novacamera.s24.core

/**
 * Energia da tela durante a gravação, sem tocar no vídeo gravado. A tela do S24 Ultra chega a 120 Hz,
 * mas o vídeo tem 30/60 quadros: o excedente é energia e calor. Sem toque, a tela escurece; o primeiro
 * toque devolve o brilho.
 */
object RecordingPowerPolicy {
    const val DIM_AFTER_MS = 20_000L
    /** Já aquecido (estado térmico moderado ou pior): escurece mais cedo. */
    const val DIM_AFTER_HOT_MS = 5_000L
    const val DIM_BRIGHTNESS = 0.15f
    const val REFRESH_HZ = 60f

    fun dimAfterMs(thermalStatus: Int) = if (thermalStatus >= ThermalPolicy.MODERATE) DIM_AFTER_HOT_MS else DIM_AFTER_MS

    fun shouldDim(recording: Boolean, dimmed: Boolean, idleMs: Long, thermalStatus: Int) =
        recording && !dimmed && idleMs >= dimAfterMs(thermalStatus)
}
