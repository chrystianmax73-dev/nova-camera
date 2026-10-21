package com.novacamera.s24.core

/**
 * Quando a IA (redução de ruído NAFNet) entra sozinha, em segundo plano, depois que a foto já está
 * na galeria. Pedido do dono (19/10): toda foto em que ela possa ajudar — zoom (tele 3×/5×/10× ou
 * recorte digital a partir de ~1,3×) e pouca luz (ISO alto) —, nunca a foto boa de dia na 1×.
 * Dentro da foto, os blocos já limpos são pulados ([com.novacamera.s24.core.imaging.TileNoise]).
 * A IA tira ruído; não recupera foco nem inventa detalhe.
 */
object AiAutoPolicy {
    const val MIN_ZOOM = 1.3f
    const val MIN_ISO = 800
    /** Até Alta resolução (~49 MP); 200 MP fica de fora (tempo fora de proporção). */
    const val MAX_PIXELS = 52_000_000L

    fun wanted(enabled: Boolean, nightOrPortrait: Boolean, food: Boolean, zoom: Float, iso: Int?, pixels: Long): Boolean =
        enabled && !food && pixels in 1..MAX_PIXELS &&
            (nightOrPortrait || zoom >= MIN_ZOOM || (iso ?: 0) >= MIN_ISO)
}
