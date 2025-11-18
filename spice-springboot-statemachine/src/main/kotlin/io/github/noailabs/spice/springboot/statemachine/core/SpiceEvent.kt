package io.github.noailabs.spice.springboot.statemachine.core

/**
 * Events that drive the execution state machine.
 */
enum class SpiceEvent {
    START,
    COMPLETE,
    FAIL,
    PAUSE_FOR_HITL,
    RESUME,
    TIMEOUT,
    TOOL_ERROR,
    RETRY
}
