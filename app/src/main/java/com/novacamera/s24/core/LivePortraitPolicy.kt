package com.novacamera.s24.core

/**
 * Prévia do Retrato (foto), não a foto final. Diagnóstico 0.26.13: com a profundidade já só quando a
 * cena muda, o que sobra dela na prévia é ~5% de um núcleo parado e ~15% logo após um toque.
 * - Pessoa ocupando parte da cena e nenhum toque: a segmentação já separa pessoa e fundo (o que o
 *   Google faz na câmera frontal); a profundidade gradual fica para a foto.
 * - Economia de energia ou bateria baixa fora do carregador: a prévia se atualiza com menos frequência.
 */
object LivePortraitPolicy {
    /** Fração mínima da cena coberta pela pessoa para dispensar a profundidade na prévia. */
    const val PERSON_ONLY_COVERAGE = 0.08f
    /** Para sair do modo só-pessoa (histerese: pessoa no limite do enquadramento não alterna a cada quadro). */
    const val PERSON_ONLY_EXIT = 0.05f
    const val LOW_BATTERY = 15
    /** Garantia de recálculo da profundidade com pouca energia (normal: [DepthRefreshPolicy.MAX_AGE_MS]). */
    const val LOW_POWER_MAX_AGE_MS = 60_000L

    fun personOnly(cinematic: Boolean, tap: Pair<Float, Float>?, personCoverage: Float, wasPersonOnly: Boolean = false): Boolean =
        !cinematic && tap == null && personCoverage >= if (wasPersonOnly) PERSON_ONLY_EXIT else PERSON_ONLY_COVERAGE

    fun lowPower(heat: DeviceHeat?): Boolean = heat != null &&
        (heat.powerSave || (!heat.charging && heat.batteryPercent in 0..LOW_BATTERY))
}
