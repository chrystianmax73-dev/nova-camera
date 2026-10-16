package com.novacamera.s24.core

/** Seleção do motor na FOTO a partir da referência real; nenhuma calibração de outra câmera. */
object AutoPhotoPolicy {
    enum class Path { SINGLE, HDR, NIGHT, ZOOM, MERGE }

    /**
     * Tele a partir daqui (3×, 5× e 10×): abertura menor, ruído sobe cedo. Era 4,5× até a 0.15; o
     * frasco a 3× com ISO 2218 saiu num quadro só, com ruído visível no rótulo.
     */
    const val ZOOM_FROM = 2.5f
    const val ZOOM_MIN_ISO = 250
    const val ZOOM_FRAMES = 3

    /**
     * Luz média (interior, fim de tarde) na principal: até a 0.16 saía um quadro só, com o ruído do
     * ISO. Como no HDR+ do Pixel, quadros iguais alinhados e somados reduzem o ruído (≈ √N) sem
     * suavizar textura; com movimento, a fusão rejeita por pixel e fica o quadro de referência.
     */
    const val MERGE_MIN_ISO = 200
    const val MERGE_FRAMES = 4

    /**
     * [allowFusion] falso (HDR desligado nos ajustes): só a redução de ruído da tele com mesma
     * exposição — nem exposições diferentes (HDR) nem o caminho de pouca luz da principal.
     */
    fun choose(iso: Int?, exposureNs: Long?, clipped: Float, deep: Float, hot: Boolean, zoom: Float = 1f, allowFusion: Boolean = true): Path = when {
        hot -> Path.SINGLE
        allowFusion && iso!=null && exposureNs!=null && iso>=1000 && exposureNs>=25_000_000L && deep>=0.08f -> Path.NIGHT
        allowFusion && clipped>=0.006f && deep>=0.04f -> Path.HDR
        // Tele com ganho alto: 3 quadros alinhados reduzem o ruído sem apagar textura nem letras.
        zoom >= ZOOM_FROM && iso != null && iso >= ZOOM_MIN_ISO -> Path.ZOOM
        iso != null && iso >= MERGE_MIN_ISO -> Path.MERGE
        else -> Path.SINGLE
    }
}
