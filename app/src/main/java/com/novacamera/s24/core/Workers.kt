package com.novacamera.s24.core

/**
 * Quantos núcleos o processamento de foto pode usar ao mesmo tempo.
 *
 * O trabalho é o mesmo (mesmos pixels, mesma quantidade de contas); só termina antes. O consumo total
 * tende a ficar igual ou menor (o aparelho volta antes ao repouso), mas a potência de pico sobe: por
 * isso, com calor ou economia de energia, volta a um núcleo só, como era.
 */
object Workers {
    /** Teto: os núcleos grandes do S24 (os pequenos atrasariam o último bloco). */
    const val MAX = 4

    fun count(thermalStatus: Int, powerSave: Boolean, cores: Int): Int = when {
        powerSave || thermalStatus >= ThermalPolicy.SEVERE -> 1
        thermalStatus >= ThermalPolicy.MODERATE -> minOf(2, cores)
        else -> (cores - 2).coerceIn(1, MAX)
    }
}
