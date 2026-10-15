package com.novacamera.s24.core

/** Seleção do motor na FOTO a partir da referência real; nenhuma calibração de outra câmera. */
object AutoPhotoPolicy {
    enum class Path { SINGLE, HDR, NIGHT }
    fun choose(iso: Int?, exposureNs: Long?, clipped: Float, deep: Float, hot: Boolean): Path = when {
        hot -> Path.SINGLE
        iso!=null && exposureNs!=null && iso>=1000 && exposureNs>=25_000_000L && deep>=0.08f -> Path.NIGHT
        clipped>=0.006f && deep>=0.04f -> Path.HDR
        else -> Path.SINGLE
    }
}
