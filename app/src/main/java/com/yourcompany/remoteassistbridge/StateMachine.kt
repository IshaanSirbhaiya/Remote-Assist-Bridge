package com.yourcompany.remoteassistbridge

class StateMachine(private val onStateChanged: (State) -> Unit) {

    enum class State {
        IDLE,
        START_PRESSED,
        SESSION_ACTIVE
    }

    private var currentState = State.IDLE

    fun onStartPressed() {
        if (currentState == State.IDLE) {
            currentState = State.START_PRESSED
            onStateChanged(currentState)
        }
    }

    fun onConfirmPressed(): Boolean {
        if (currentState == State.START_PRESSED) {
            currentState = State.SESSION_ACTIVE
            onStateChanged(currentState)
            return true
        }
        return false
    }

    fun onStopPressed() {
        if (currentState == State.SESSION_ACTIVE) {
            currentState = State.IDLE
            onStateChanged(currentState)
        }
    }

    fun getCurrentState() = currentState
}