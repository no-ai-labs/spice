package io.github.noailabs.spice.event

import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.flow.Flow

/**
 * 🚌 Tool Call Event Bus (Spice 2.0)
 *
 * Pub/Sub event bus for tool call lifecycle events.
 * Enables multi-agent orchestration and event-driven architecture.
 *
 * **Architecture:**
 * ```
 * Agent A → publish(Emitted) → EventBus → Flow<ToolCallEvent>
 *                                             ↓
 *                                        Agent B (subscriber)
 * ```
 *
 * **Use Cases:**
 * - Multi-agent orchestration
 * - Event sourcing and replay
 * - Monitoring and observability
 * - Audit trail
 *
 * **Thread Safety:**
 * All implementations must be thread-safe for concurrent publish/subscribe.
 *
 * @since 2.0.0
 */
interface ToolCallEventBus {

    /**
     * Publish a tool call event
     *
     * Broadcasts the event to all subscribers.
     *
     * @param event The tool call event to publish
     * @return SpiceResult indicating success or failure
     */
    suspend fun publish(event: ToolCallEvent): SpiceResult<Unit>

    /**
     * Subscribe to all tool call events
     *
     * Returns a Flow that emits all tool call events as they are published.
     *
     * **Usage:**
     * ```kotlin
     * eventBus.subscribe().collect { event ->
     *     when (event) {
     *         is ToolCallEvent.Emitted -> handleEmitted(event)
     *         is ToolCallEvent.Completed -> handleCompleted(event)
     *         // ...
     *     }
     * }
     * ```
     *
     * @return Flow of all tool call events
     */
    fun subscribe(): Flow<ToolCallEvent>

    /**
     * Subscribe to specific event types
     *
     * Returns a Flow that only emits events of the specified types.
     *
     * **Usage:**
     * ```kotlin
     * eventBus.subscribe(ToolCallEvent.Emitted::class, ToolCallEvent.Completed::class)
     *     .collect { event ->
     *         // Only receives Emitted and Completed events
     *     }
     * ```
     *
     * @param eventTypes The event types to subscribe to
     * @return Flow of filtered tool call events
     */
    fun subscribe(vararg eventTypes: kotlin.reflect.KClass<out ToolCallEvent>): Flow<ToolCallEvent>

    /**
     * Subscribe to events for a specific tool call ID
     *
     * Useful for tracking the lifecycle of a single tool call.
     *
     * **Usage:**
     * ```kotlin
     * val toolCallId = "call_123"
     * eventBus.subscribeToToolCall(toolCallId).collect { event ->
     *     println("${event.typeName()}: ${event.toolCall.id}")
     * }
     * ```
     *
     * @param toolCallId The tool call ID to filter by
     * @return Flow of events for the specified tool call
     */
    fun subscribeToToolCall(toolCallId: String): Flow<ToolCallEvent>

    /**
     * Subscribe to events for a specific run
     *
     * Useful for tracking all tool calls in a specific graph execution run.
     *
     * @param runId The run ID to filter by
     * @return Flow of events for the specified run
     */
    fun subscribeToRun(runId: String): Flow<ToolCallEvent>

    /**
     * Get event history
     *
     * Returns all events that have been published (if history is enabled).
     *
     * @param limit Maximum number of events to return (most recent first)
     * @return SpiceResult with list of events
     */
    suspend fun getHistory(limit: Int = 100): SpiceResult<List<ToolCallEvent>>

    /**
     * Get event history for a specific tool call
     *
     * Returns all events related to a specific tool call ID.
     *
     * @param toolCallId The tool call ID
     * @return SpiceResult with list of events
     */
    suspend fun getToolCallHistory(toolCallId: String): SpiceResult<List<ToolCallEvent>>

    /**
     * Clear event history
     *
     * Removes all stored events. Only works if history is enabled.
     *
     * @return SpiceResult indicating success or failure
     */
    suspend fun clearHistory(): SpiceResult<Unit>

    /**
     * Get subscriber count
     *
     * Returns the number of active subscribers.
     *
     * @return Number of subscribers
     */
    suspend fun getSubscriberCount(): Int
}

/**
 * 📊 Event Bus Configuration
 *
 * Configuration for ToolCallEventBus implementations.
 *
 * @property enableHistory Whether to store event history
 * @property historySize Maximum number of events to store
 * @property enableMetrics Whether to collect metrics
 */
data class EventBusConfig(
    val enableHistory: Boolean = true,
    val historySize: Int = 1000,
    val enableMetrics: Boolean = true
) {
    companion object {
        val DEFAULT = EventBusConfig()
        val NO_HISTORY = EventBusConfig(enableHistory = false)
        val UNLIMITED_HISTORY = EventBusConfig(historySize = Int.MAX_VALUE)
    }
}
