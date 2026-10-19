package com.novacamera.s24.core

/**
 * Noturno automático na FOTO (como o da Samsung): escuro de verdade — a exposição automática já está
 * no tempo longo e com ganho alto — a FOTO captura e processa com o motor do Noturno, sem mudar o
 * Noturno. Medido em 19/10: FOTO 5× no escuro saía com 8 quadros a ISO 2056 1/24 s ampliados para
 * 49 MP, enquanto o modo Noturno na mesma cena fica nítido.
 */
object AutoNightPolicy {
    /** O automático já não encurta o tempo: abaixo disso a cena ainda tem luz para a FOTO comum. */
    const val MIN_EXPOSURE_NS = 25_000_000L
    const val MIN_ISO_MAIN = 2400
    /** Tele (3×/5×/10×): abertura menor, o ruído sobe antes. */
    const val MIN_ISO_TELE = 800
    const val TELE_FROM = 2.5f

    fun wanted(enabled: Boolean, iso: Int?, exposureNs: Long?, zoom: Float, hot: Boolean): Boolean {
        if (!enabled || hot || iso == null || exposureNs == null) return false
        val minIso = if (zoom >= TELE_FROM) MIN_ISO_TELE else MIN_ISO_MAIN
        return exposureNs >= MIN_EXPOSURE_NS && iso >= minIso
    }
}
