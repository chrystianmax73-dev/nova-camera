package com.novacamera.s24.core

/**
 * Noturno na tele (3×/5×/10×): a lente nativa fica fixa mesmo no escuro. Sob a câmera lógica, com ISO
 * alto, o firmware trocava a 5× pela 3× ampliada (foto 0.29.4: 69 mm f/2.4 pedindo 5×) — perde ~40%
 * da resolução e nem ganha luz: para o mesmo enquadramento a pupila da 5× (18,6 mm f/3.4 ≈ 5,5 mm) é
 * maior que a da 3× (7,9 mm f/2.4 ≈ 3,3 mm).
 */
object NightLensPolicy {
    /** Abaixo disso é a principal: o Noturno 1× segue como está. */
    const val MIN_ZOOM = 2.5f

    fun pinNative(night: Boolean, front: Boolean, equivalentZoom: Float) = night && !front && equivalentZoom >= MIN_ZOOM

    /**
     * A tele não foca perto (e às vezes no escuro): foco que não fecha por [limitMs] devolve a escolha
     * ao firmware. Estados do CONTROL_AF_STATE: 2/4 focado; 1/3 procurando; 5/6 sem foco; 0 inativo (neutro).
     */
    class FocusWatch(private val limitMs: Long = 2500L) {
        private var since = -1L
        fun reset() { since = -1L }
        fun failed(nowMs: Long, afState: Int?): Boolean {
            when (afState) {
                2, 4 -> { since = -1L; return false }
                1, 3, 5, 6 -> if (since < 0) since = nowMs
                else -> return false
            }
            return nowMs - since >= limitMs
        }
    }
}
