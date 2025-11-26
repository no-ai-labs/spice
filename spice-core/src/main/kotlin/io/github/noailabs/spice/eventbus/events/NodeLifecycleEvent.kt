package io.github.noailabs.spice.eventbus.events

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 🔄 Node Lifecycle Events
 *
 * Strongly-typed events for node execution lifecycle within graphs.
 * Emitted by GraphRunner for each node execution.
 *
 * **Event Flow:**
 * ```
 * Started → Completed/Failed
 * ```
 *
 * **Usage:**
 * ```kotlin
 * // GraphRunner emits events
 * eventBus.publish(
 *     StandardChannels.NODE_LIFECYCLE,
 *     NodeLifecycleEvent.Started(graphId, nodeId, runId, message)
 * )
 *
 * // External systems subscribe
 * eventBus.subscribe(StandardChannels.NODE_LIFECYCLE)
 *     .collect { typedEvent ->
 *         when (val event = typedEvent.event) {
 *             is NodeLifecycleEvent.Started -> onNodeStarted(event)
 *             is NodeLifecycleEvent.Completed -> onNodeCompleted(event)
 *             is NodeLifecycleEvent.Failed -> onNodeFailed(event)
 *         }
 *     }
 * ```
 *
 * **Note:** Node events are high-volume and transient (history disabled by default).
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
@Serializable
sealed class NodeLifecycleEvent {
    abstract val graphId: String
    abstract val nodeId: String
    abstract val runId: String
    abstract val timestamp: Instant

    /**
     * Node execution started
     *
     * @property graphId Graph identifier
     * @property nodeId Node identifier
     * @property runId Execution run ID
     * @property timestamp Event timestamp
     * @property message Input message to node
     * @property nodeType Node type (e.g., "AgentNode", "ToolNode")
     * @property metadata Additional context
     */
    @Serializable
    data class Started(
        override val graphId: String,
        override val nodeId: String,
        override val runId: String,
        override val timestamp: Instant = Clock.System.now(),
        val message: SpiceMessage,
        val nodeType: String,
        val metadata: Map<String, String> = emptyMap()
    ) : NodeLifecycleEvent() {
        override fun toString(): String =
            "NodeLifecycleEvent.Started(graphId='$graphId', nodeId='$nodeId', type='$nodeType')"
    }

    /**
     * Node execution completed successfully
     *
     * @property graphId Graph identifier
     * @property nodeId Node identifier
     * @property runId Execution run ID
     * @property timestamp Event timestamp
     * @property result Output message from node
     * @property durationMs Execution duration in milliseconds
     * @property nodeType Node type
     * @property metadata Additional context
     */
    @Serializable
    data class Completed(
        override val graphId: String,
        override val nodeId: String,
        override val runId: String,
        override val timestamp: Instant = Clock.System.now(),
        val result: SpiceMessage,
        val durationMs: Long,
        val nodeType: String,
        val metadata: Map<String, String> = emptyMap()
    ) : NodeLifecycleEvent() {
        override fun toString(): String =
            "NodeLifecycleEvent.Completed(graphId='$graphId', nodeId='$nodeId', durationMs=$durationMs)"
    }

    /**
     * Node execution failed
     *
     * @property graphId Graph identifier
     * @property nodeId Node identifier
     * @property runId Execution run ID
     * @property timestamp Event timestamp
     * @property error Spice error that caused failure
     * @property durationMs Execution duration before failure
     * @property nodeType Node type
     * @property metadata Additional context
     */
    @Serializable
    data class Failed(
        override val graphId: String,
        override val nodeId: String,
        override val runId: String,
        override val timestamp: Instant = Clock.System.now(),
        @Contextual val error: SpiceError,
        val durationMs: Long,
        val nodeType: String,
        val metadata: Map<String, String> = emptyMap()
    ) : NodeLifecycleEvent() {
        override fun toString(): String =
            "NodeLifecycleEvent.Failed(graphId='$graphId', nodeId='$nodeId', error=${error.code})"
    }
}
