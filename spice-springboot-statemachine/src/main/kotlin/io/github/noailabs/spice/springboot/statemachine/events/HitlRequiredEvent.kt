package io.github.noailabs.spice.springboot.statemachine.events

/**
 * Fired when a graph enters the WAITING state and requires human approval/input.
 */
data class HitlRequiredEvent(
    val checkpointId: String,
    val graphId: String,
    val nodeId: String?,
    val options: Map<String, Any?> = emptyMap()
)
