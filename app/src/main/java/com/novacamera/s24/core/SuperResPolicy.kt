package com.novacamera.s24.core

/**
 * Quando a foto usa super-resolução (rajada na lente sem recorte, montada numa grade mais fina).
 *
 * Só no zoom digital sobre uma tele física (ex.: 10× na lente 5×). No Noturno, só segurando na mão:
 * a SR vive do tremor natural entre os quadros (no apoio eles caem no mesmo lugar e não há o que
 * ganhar; o Noturno apoiado usa quadros longos e a fusão comum). O limite de tremido por quadro do
 * Noturno já é calculado para o zoom final, então na lente sem recorte o borrão fica abaixo de 1 px.
 */
object SuperResPolicy {
    /** Zoom digital mínimo sobre a lente para valer a pena. */
    const val MIN_DIGITAL = 1.4f
    /** Lente física a partir desta ampliação conta como tele. */
    const val TELE_INTRINSIC = 2.5f
    /** Quadros mínimos no Noturno (com menos, a grade fina fica rala e vale mais a fusão comum). */
    const val NIGHT_MIN_FRAMES = 3

    enum class Kind { PHOTO, NIGHT, OTHER }

    /** Fator da super-resolução (= zoom digital sobre a lente) ou 1 quando não se aplica. */
    fun factor(
        enabled: Boolean, front: Boolean, vendorExtension: Boolean, kind: Kind,
        physicalTele: Boolean, lensIntrinsic: Float, zoomOnLens: Float,
        nightFrames: Int = 0, nightSteady: Boolean = false, nightManualPlan: Boolean = false,
    ): Float {
        if (!enabled || front || vendorExtension || !physicalTele) return 1f
        if (lensIntrinsic < TELE_INTRINSIC || zoomOnLens < MIN_DIGITAL) return 1f
        return when (kind) {
            Kind.PHOTO -> zoomOnLens
            // Noturno: só com plano de exposição próprio (tempo por quadro limitado pelo giroscópio),
            // na mão e com quadros suficientes.
            Kind.NIGHT -> if (nightManualPlan && !nightSteady && nightFrames >= NIGHT_MIN_FRAMES) zoomOnLens else 1f
            Kind.OTHER -> 1f
        }
    }
}
