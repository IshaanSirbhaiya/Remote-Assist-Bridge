package com.yourcompany.remoteassistbridge

class StateMachine(private val onStateChanged: (State) -> Unit) {

    enum class State {
        IDLE,
        START_PRESSED,
        SESSION_ACTIVE
    }

    private var currentState = State.IDLE

    fun onStartPressed() {
        currentState = State.START_PRESSED
        onStateChanged(currentState)
    }

    fun onConfirmPressed(): Boolean {
        currentState = State.SESSION_ACTIVE
        onStateChanged(currentState)
        return true
    }
    fun forceStop() {
        currentState = State.IDLE
        onStateChanged(currentState)
    }

    fun onStopPressed() {
        currentState = State.IDLE
        onStateChanged(currentState)
    }

    fun getCurrentState() = currentState
}