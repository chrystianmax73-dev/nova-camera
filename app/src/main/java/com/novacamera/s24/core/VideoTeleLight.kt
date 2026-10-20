package com.novacamera.s24.core

/**
 * Vídeo no zoom 5× com pouca luz (diagnóstico de 20/10 na 0.36.1: prévia 1× a ISO 1468 e 1/30 s; a 5× a imagem
 * ficava preta). A tele 5× é f/3,4 contra f/2,4 da 3×: precisa do dobro de ISO e bate no teto (3200) bem antes.
 * A Samsung troca da 3× para a 5× em ~5,0× (registro: 6→7 em 4,995×, 7→6 em 4,817×). Com pouca luz o vídeo fica
 * em [HOLD]× na 3× (recorte de ~2190 px de largura, sobra para Full HD; 2% de enquadramento a menos que 5×),
 * como o iPhone e a própria Samsung fazem na tele escura. De [UNTIL]× para cima segue a 5× (10× pela 3× daria
 * ~1090 px: pior que a tele escura). Histerese para não ficar trocando de lente.
 *
 * Pesquisa (20/10): a lente 5× recolhe mais fótons no mesmo enquadramento, mas sem ganho digital no vídeo
 * (efeitos de GL são incompatíveis com o HDR/HLG) o teto de ISO deixa a imagem escura; a 3× fica dentro do teto.
 */
object VideoTeleLight {
    const val HOLD = 4.9f
    const val UNTIL = 6.0f
    /** Quanto a 5× precisa a mais de ISO que a 3× no mesmo tempo de exposição (f/3,4 × f/2,4)². */
    const val TELE_ISO_FACTOR = 2.0f
    const val ENTER = 0.9f
    const val LEAVE = 0.55f
    /** Exposição já longa (o automático não tem mais para onde ir no tempo): ≥ 1/40 s. */
    const val LONG_EXPOSURE_NS = 25_000_000L

    class State {
        var holding = false; private set

        /**
         * [iso]/[exposureNs]: o que a lente ativa está usando agora; [onTele5]: a lente ativa é a 5×.
         * Devolve true quando muda.
         */
        fun update(iso: Int?, exposureNs: Long?, isoMax: Int, onTele5: Boolean): Boolean {
            if (iso == null || exposureNs == null || isoMax <= 0) return false
            val need = if (onTele5) iso.toFloat() else iso * TELE_ISO_FACTOR
            val next = when {
                !holding && need >= ENTER * isoMax && exposureNs >= LONG_EXPOSURE_NS -> true
                holding && need <= LEAVE * isoMax -> false
                else -> holding
            }
            if (next == holding) return false
            holding = next; return true
        }

        fun reset() { holding = false }
    }

    /** Zoom equivalente a pedir à câmera: no escuro, a faixa [HOLD]–[UNTIL) fica em [HOLD] (lente 3×). */
    fun zoom(requested: Float, holding: Boolean) = if (holding && requested >= HOLD && requested < UNTIL) HOLD else requested
}
