package com.novacamera.s24.core

import kotlin.math.hypot

/**
 * Quando a IA de detalhe (Real-ESRGAN com checagem de consistência, [com.novacamera.s24.core.imaging.DetailGate])
 * roda: só em foto ampliada digitalmente sobre uma tele (≥ [MIN_DIGITAL]× além da lente, lente ≥ [MIN_LENS]×),
 * porque aí a foto é maior que o que o sensor viu e reduzir à metade não perde detalhe real. Na principal o
 * 2× vem do recorte do sensor de 200 MP (detalhe real): fica fora. Fora também: Noturno, Retrato, Food
 * (tom e pele não mudam) e fotos acima de [MAX_PIXELS] (custo e memória).
 */
object DetailPolicy {
    const val MIN_DIGITAL = 1.8f
    const val MIN_LENS = 2.5f
    const val MAX_PIXELS = 16_500_000L
    /** Equivalente 35 mm da principal do S24 Ultra (~23 mm); erro de ±10% não muda a decisão. */
    const val MAIN_EQ_MM = 23f

    fun lensZoom(focalMm: Float?, sensorW: Float?, sensorH: Float?): Float? {
        if (focalMm == null || sensorW == null || sensorH == null || focalMm <= 0f || sensorW <= 0f || sensorH <= 0f) return null
        return focalMm * 43.27f / hypot(sensorW, sensorH) / MAIN_EQ_MM
    }

    /** Fator digital: o da super-resolução quando houve; senão zoom pedido ÷ zoom da lente. */
    fun digital(zoom: Float, srFactor: Float, lens: Float?): Float =
        if (srFactor > 1.05f) srFactor else lens?.let { zoom / it } ?: 1f

    fun eligible(kindAllowed: Boolean, zoom: Float, srFactor: Float, lens: Float?, pixels: Long): Boolean {
        if (!kindAllowed || pixels > MAX_PIXELS || pixels <= 0) return false
        if (srFactor > 1.05f) return srFactor >= MIN_DIGITAL // super-resolução só existe na tele
        return lens != null && lens >= MIN_LENS && zoom / lens >= MIN_DIGITAL
    }
}
