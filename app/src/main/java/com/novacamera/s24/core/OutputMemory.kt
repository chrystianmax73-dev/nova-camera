package com.novacamera.s24.core

/** Cabe a foto de saída (pixels nativos) sem empurrar o sistema para pressão de memória? */
object OutputMemory {
    /** Folga sobre o tamanho da saída: cópias de codificação e o que o sistema usa no meio tempo. */
    const val MARGIN = 2.0

    fun fits(requiredBytes: Long, availBytes: Long, thresholdBytes: Long, lowMemory: Boolean): Boolean =
        !lowMemory && availBytes > 0 && availBytes - thresholdBytes >= (requiredBytes * MARGIN).toLong()
}
