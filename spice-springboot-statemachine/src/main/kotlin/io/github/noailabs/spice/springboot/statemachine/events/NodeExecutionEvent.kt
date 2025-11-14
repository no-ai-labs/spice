package io.github.noailabs.spice.springboot.statemachine.events

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import kotlinx.datetime.Instant

/**
 * Published whenever a state machine transition occurs.
 */
data class NodeExecutionEvent(
    val graphId: String,
    val nodeId: String?,
    val from: ExecutionState?,
    val to: ExecutionState,
    val event: SpiceEvent,
    val timestamp: Instant,
    val metadata: Map<String, Any?>
)
