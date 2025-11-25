package io.github.noailabs.spice.springboot.statemachine.events

import kotlinx.datetime.Instant

/**
 * Emitted when a workflow is resumed from a HITL checkpoint.
 *
 * This event is published before the actual resume execution begins,
 * allowing listeners to track workflow resumption for monitoring, logging,
 * or analytics purposes.
 *
 * @property runId The unique run identifier for this workflow execution
 * @property graphId The graph/workflow definition identifier
 * @property nodeId The node ID where the workflow was paused (HumanNode)
 * @property timestamp When the resume was initiated
 * @property metadata Additional context (e.g., checkpoint age, user info)
 *
 * @since 1.0.5
 */
data class WorkflowResumedEvent(
    val runId: String,
    val graphId: String,
    val nodeId: String?,
    val timestamp: Instant,
    val metadata: Map<String, Any?> = emptyMap()
)
