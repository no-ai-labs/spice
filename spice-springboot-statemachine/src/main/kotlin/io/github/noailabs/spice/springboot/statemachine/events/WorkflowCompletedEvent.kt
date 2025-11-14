package io.github.noailabs.spice.springboot.statemachine.events

import io.github.noailabs.spice.ExecutionState
import kotlinx.datetime.Instant

/**
 * Emitted when a workflow reaches a terminal state (COMPLETED or FAILED).
 */
data class WorkflowCompletedEvent(
    val runId: String,
    val graphId: String,
    val finalState: ExecutionState,
    val timestamp: Instant,
    val metadata: Map<String, Any?> = emptyMap()
)
