package io.github.noailabs.spice.eventbus.events

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 🔄 Graph Lifecycle Events
 *
 * Strongly-typed events for graph execution lifecycle.
 * Emitted by GraphRunner to track graph execution from start to completion/failure.
 *
 * **Event Flow:**
 * ```
 * Started → [Paused?] → Completed/Failed
 * ```
 *
 * **Usage:**
 * ```kotlin
 * // GraphRunner emits events
 * eventBus.publish(
 *     StandardChannels.GRAPH_LIFECYCLE,
 *     GraphLifecycleEvent.Started(graphId, runId, initialMessage)
 * )
 *
 * // External systems subscribe
 * eventBus.subscribe(StandardChannels.GRAPH_LIFECYCLE)
 *     .collect { typedEvent ->
 *         when (val event = typedEvent.event) {
 *             is GraphLifecycleEvent.Started -> onGraphStarted(event)
 *             is GraphLifecycleEvent.Completed -> onGraphCompleted(event)
 *             is GraphLifecycleEvent.Failed -> onGraphFailed(event)
 *             is GraphLifecycleEvent.Paused -> onGraphPaused(event)
 *         }
 *     }
 * ```
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
@Serializable
sealed class GraphLifecycleEvent {
    abstract val graphId: String
    abstract val runId: String
    abstract val timestamp: Instant

    /**
     * Graph execution started
     *
     * @property graphId Graph identifier
     * @property runId Execution run ID (unique per execution)
     * @property timestamp Event timestamp
     * @property message Initial input message
     * @property metadata Additional context
     */
    @Serializable
    data class Started(
        override val graphId: String,
        override val runId: String,
        override val timestamp: Instant = Clock.System.now(),
        val message: SpiceMessage,
        val metadata: Map<String, String> = emptyMap()
    ) : GraphLifecycleEvent() {
        override fun toString(): String =
            "GraphLifecycleEvent.Started(graphId='$graphId', runId='$runId')"
    }

    /**
     * Graph execution completed successfully
     *
     * @property graphId Graph identifier
     * @property runId Execution run ID
     * @property timestamp Event timestamp
     * @property result Final output message
     * @property durationMs Execution duration in milliseconds
     * @property nodesExecuted Number of nodes executed
     * @property metadata Additional context
     */
    @Serializable
    data class Completed(
        override val graphId: String,
        override val runId: String,
        override val timestamp: Instant = Clock.System.now(),
        val result: SpiceMessage,
        val durationMs: Long,
        val nodesExecuted: Int = 0,
        val metadata: Map<String, String> = emptyMap()
    ) : GraphLifecycleEvent() {
        override fun toString(): String =
            "GraphLifecycleEvent.Completed(graphId='$graphId', runId='$runId', durationMs=$durationMs)"
    }

    /**
     * Graph execution failed
     *
     * @property graphId Graph identifier
     * @property runId Execution run ID
     * @property timestamp Event timestamp
     * @property error Spice error that caused failure
     * @property durationMs Execution duration before failure
     * @property failedNodeId Node that failed (if applicable)
     * @property metadata Additional context
     */
    @Serializable
    data class Failed(
        override val graphId: String,
        override val runId: String,
        override val timestamp: Instant = Clock.System.now(),
        @Contextual val error: SpiceError,
        val durationMs: Long,
        val failedNodeId: String? = null,
        val metadata: Map<String, String> = emptyMap()
    ) : GraphLifecycleEvent() {
        override fun toString(): String =
            "GraphLifecycleEvent.Failed(graphId='$graphId', runId='$runId', error=${error.code})"
    }

    /**
     * Graph execution paused (HITL)
     *
     * Emitted when graph pauses for human input (HITL).
     *
     * @property graphId Graph identifier
     * @property runId Execution run ID
     * @property timestamp Event timestamp
     * @property nodeId Node where graph paused
     * @property reason Pause reason (e.g., "human_input_required")
     * @property checkpointId Checkpoint ID for resumption
     * @property metadata Additional context
     */
    @Serializable
    data class Paused(
        override val graphId: String,
        override val runId: String,
        override val timestamp: Instant = Clock.System.now(),
        val nodeId: String,
        val reason: String,
        val checkpointId: String? = null,
        val metadata: Map<String, String> = emptyMap()
    ) : GraphLifecycleEvent() {
        override fun toString(): String =
            "GraphLifecycleEvent.Paused(graphId='$graphId', runId='$runId', nodeId='$nodeId')"
    }
}
