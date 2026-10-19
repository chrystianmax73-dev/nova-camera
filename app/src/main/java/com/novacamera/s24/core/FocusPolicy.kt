package com.novacamera.s24.core

import kotlin.math.max
import kotlin.math.min

/**
 * Decisões de foco que não dependem da câmera (testáveis):
 * - antes de uma rajada, garantir que TODOS os quadros saiam com o mesmo foco, no ponto certo;
 * - quando o zoom muda depois do toque, o ponto tocado deixa de existir na imagem.
 */
object FocusPolicy {
    /**
     * HOLD: a lente fica onde o AF contínuo do aparelho já tinha focado (foco manual na mesma distância)
     * durante a rajada — mantém a escolha de assunto da própria câmera em vez de impor o centro.
     */
    enum class Target { NONE, CENTER, POINT, HOLD }

    class BeforeBurst(val target: Target, val waitMs: Long)

    /** Rajada com mais quadros que isto na tele trava o foco antes (o AF contínuo pode "caçar" no meio). */
    const val TELE_FROM = 2.5f
    const val NIGHT_WAIT_MS = 1200L
    const val BURST_WAIT_MS = 700L
    /** Mudança de zoom (razão) a partir da qual o ponto tocado é descartado. */
    const val ZOOM_DROP_RATIO = 1.15f

    /**
     * @param userPoint ponto tocado ainda ativo (exposição medida nele).
     * @param afAtPoint o AF ainda está parado no ponto tocado (antes de voltar ao contínuo).
     * @param locked toque prolongado: foco e exposição travados pelo usuário — não mexer.
     * @param manualFocus foco manual do Pro — não mexer.
     */
    fun beforeBurst(
        night: Boolean, frames: Int, equivalentZoom: Float, superRes: Boolean,
        userPoint: Boolean, afAtPoint: Boolean, locked: Boolean, manualFocus: Boolean, vendorExtension: Boolean,
        /** AF contínuo parado e confirmado (PASSIVE_FOCUSED) e a lente aceita foco manual. Nunca no Noturno. */
        continuousSettled: Boolean = false,
    ): BeforeBurst {
        val none = BeforeBurst(Target.NONE, 0)
        if (locked || manualFocus || vendorExtension) return none
        val wait = if (night) NIGHT_WAIT_MS else BURST_WAIT_MS
        // Ponto escolhido: se o AF já voltou ao contínuo, foca de novo nele antes de disparar.
        if (userPoint) return if (afAtPoint) none else BeforeBurst(Target.POINT, wait)
        val needsLock = night || superRes || (frames > 1 && equivalentZoom >= TELE_FROM)
        if (!needsLock) return none
        // Foto/Retrato sem toque: o centro pode ser a parede atrás do assunto (foto real 19/10, 5×: caixa
        // fora de foco, frascos no foco). Se o AF contínuo já assentou num assunto, mantém a lente ali.
        return if (!night && continuousSettled) BeforeBurst(Target.HOLD, 0) else BeforeBurst(Target.CENTER, wait)
    }

    /** O enquadramento mudou o bastante para o ponto tocado não corresponder mais ao mesmo objeto. */
    fun zoomInvalidatesPoint(zoomAtTap: Float, zoomNow: Float): Boolean {
        if (zoomAtTap <= 0f || zoomNow <= 0f) return false
        return max(zoomAtTap, zoomNow) / min(zoomAtTap, zoomNow) >= ZOOM_DROP_RATIO
    }
}
