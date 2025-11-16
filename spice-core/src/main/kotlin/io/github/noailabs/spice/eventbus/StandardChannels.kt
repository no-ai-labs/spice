package io.github.noailabs.spice.eventbus

import io.github.noailabs.spice.event.ToolCallEvent
import io.github.noailabs.spice.eventbus.events.GraphLifecycleEvent
import io.github.noailabs.spice.eventbus.events.NodeLifecycleEvent
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * 📻 Standard Event Channels
 *
 * Predefined event channels for Spice Framework core events.
 * Using these standard channels ensures consistency and enables better tooling.
 *
 * **Available Channels:**
 * - `GRAPH_LIFECYCLE`: Graph execution lifecycle events (Started, Completed, Failed, Paused)
 * - `NODE_LIFECYCLE`: Node execution lifecycle events (Started, Completed, Failed)
 * - `TOOL_CALLS`: Tool call lifecycle events (Emitted, Received, Completed, Failed, etc.)
 * - `SYSTEM_EVENTS`: System-level events (errors, warnings, info)
 *
 * **Usage:**
 * ```kotlin
 * // Publish graph started event
 * eventBus.publish(
 *     StandardChannels.GRAPH_LIFECYCLE,
 *     GraphLifecycleEvent.Started(graphId, runId, message),
 *     EventMetadata(source = "graph-runner")
 * )
 *
 * // Subscribe to tool call events
 * eventBus.subscribe(StandardChannels.TOOL_CALLS)
 *     .collect { typedEvent ->
 *         when (val event = typedEvent.event) {
 *             is ToolCallEvent.Emitted -> handleEmitted(event)
 *             is ToolCallEvent.Completed -> handleCompleted(event)
 *             else -> {}
 *         }
 *     }
 * ```
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
object StandardChannels {
    /**
     * Graph lifecycle events channel
     *
     * Events: GraphLifecycleEvent.Started, Completed, Failed, Paused
     * History: Enabled (last 1000 events)
     * Use: Track graph execution lifecycle
     * Version: 1.0.0
     */
    val GRAPH_LIFECYCLE = EventChannel(
        name = "spice.graph.lifecycle",
        type = GraphLifecycleEvent::class,
        version = "1.0.0",
        config = ChannelConfig(
            enableHistory = true,
            historySize = 1000,
            enableDeadLetter = true
        )
    )

    /**
     * Node lifecycle events channel
     *
     * Events: NodeLifecycleEvent.Started, Completed, Failed
     * History: Disabled (high volume)
     * Use: Track node execution within graphs
     * Version: 1.0.0
     */
    val NODE_LIFECYCLE = EventChannel(
        name = "spice.node.lifecycle",
        type = NodeLifecycleEvent::class,
        version = "1.0.0",
        config = ChannelConfig(
            enableHistory = false,  // High volume, transient events
            enableDeadLetter = true
        )
    )

    /**
     * Tool call events channel
     *
     * Events: ToolCallEvent.Emitted, Received, Completed, Failed, Retrying, Cancelled
     * History: Enabled (last 10000 events)
     * Use: Track tool call lifecycle for debugging and analytics
     * Version: 1.0.0
     */
    val TOOL_CALLS = EventChannel(
        name = "spice.toolcalls",
        type = ToolCallEvent::class,
        version = "1.0.0",
        config = ChannelConfig(
            enableHistory = true,
            historySize = 10000,
            enableDeadLetter = true
        )
    )

    /**
     * System events channel
     *
     * Events: SystemEvent (errors, warnings, info)
     * History: Enabled (last 5000 events)
     * Use: System-level monitoring and alerts
     * Version: 1.0.0
     */
    val SYSTEM_EVENTS = EventChannel(
        name = "spice.system",
        type = SystemEvent::class,
        version = "1.0.0",
        config = ChannelConfig(
            enableHistory = true,
            historySize = 5000,
            enableDeadLetter = false  // System events shouldn't go to DLQ
        )
    )

    /**
     * All standard channels
     */
    val ALL = listOf(
        GRAPH_LIFECYCLE,
        NODE_LIFECYCLE,
        TOOL_CALLS,
        SYSTEM_EVENTS
    )
}

/**
 * 🔔 System Event
 *
 * System-level events for monitoring, alerts, and observability.
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
@Serializable
sealed class SystemEvent {
    abstract val message: String
    abstract val timestamp: Instant
    abstract val component: String

    @Serializable
    data class Error(
        override val message: String,
        override val timestamp: Instant = Clock.System.now(),
        override val component: String,
        val errorCode: String,
        val stackTrace: String? = null,
        val context: Map<String, String> = emptyMap()
    ) : SystemEvent()

    @Serializable
    data class Warning(
        override val message: String,
        override val timestamp: Instant = Clock.System.now(),
        override val component: String,
        val warningCode: String,
        val context: Map<String, String> = emptyMap()
    ) : SystemEvent()

    @Serializable
    data class Info(
        override val message: String,
        override val timestamp: Instant = Clock.System.now(),
        override val component: String,
        val infoCode: String,
        val context: Map<String, String> = emptyMap()
    ) : SystemEvent()
}
