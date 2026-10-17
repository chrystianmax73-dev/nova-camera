package com.novacamera.s24.core

/** Orçamento por sessão; um bind não renova tentativas e nunca acontece antes do Finalize. */
class VideoRecovery(private val maxRetries: Int = 2) {
    enum class Action { REBIND, FINALIZE, STOP, IGNORE }
    private var retries = 0
    private var awaitingFinalize = false
    var exhausted = false
        private set

    fun stalled(recording: Boolean): Action {
        if (awaitingFinalize || exhausted) return Action.IGNORE
        if (retries >= maxRetries) exhausted = true else retries++
        if (recording) {
            awaitingFinalize = true
            return Action.FINALIZE
        }
        return if (exhausted) Action.STOP else Action.REBIND
    }

    fun finalized(): Action {
        if (!awaitingFinalize) return Action.IGNORE
        awaitingFinalize = false
        return if (exhausted) Action.STOP else Action.REBIND
    }

    /** Só uma ação explícita do usuário inicia outro orçamento. */
    fun reset() { retries = 0; awaitingFinalize = false; exhausted = false }
}
