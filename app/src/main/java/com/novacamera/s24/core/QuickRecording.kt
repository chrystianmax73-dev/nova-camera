package com.novacamera.s24.core

/** Intenção do gesto separada do bind assíncrono: soltar antes de ficar pronto nunca inicia vídeo. */
class QuickRecording {
    enum class Action { NONE, START, STOP, RESTORE }
    private enum class State { IDLE, PREPARING, PREPARING_LOCKED, HELD, LOCKED, STOPPING }
    private var state = State.IDLE
    val active get() = state != State.IDLE
    val locked get() = state == State.PREPARING_LOCKED || state == State.LOCKED

    fun hold(): Boolean {
        if (active) return false
        state = State.PREPARING
        return true
    }
    fun lock() {
        state = when (state) { State.PREPARING -> State.PREPARING_LOCKED; State.HELD -> State.LOCKED; else -> state }
    }
    fun ready(): Action = when (state) {
        State.PREPARING -> { state = State.HELD; Action.START }
        State.PREPARING_LOCKED -> { state = State.LOCKED; Action.START }
        else -> Action.NONE
    }
    fun release(): Action = when (state) {
        State.PREPARING -> { state = State.IDLE; Action.RESTORE }
        State.HELD -> { state = State.STOPPING; Action.STOP }
        else -> Action.NONE
    }
    fun interrupt(): Action = when (state) {
        State.PREPARING, State.PREPARING_LOCKED -> { state = State.IDLE; Action.RESTORE }
        State.HELD, State.LOCKED -> { state = State.STOPPING; Action.STOP }
        else -> Action.NONE
    }
    fun finished() { state = State.IDLE }
}
