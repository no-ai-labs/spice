package io.github.noailabs.spice.eventbus

import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * 📡 Unified Event Bus for Spice Framework 1.0.0-alpha-5
 *
 * Single event bus with typed channel support for type-safe pub/sub.
 * Replaces both legacy EventBus and ToolCallEventBus with unified architecture.
 *
 * **Key Features:**
 * - Typed channels for type-safe event publishing/subscription
 * - **Automatic serialization**: Uses SchemaRegistry for versioned serialization (no manual marshal/unmarshal!)
 * - Schema evolution via EventEnvelope with versioning
 * - Dead letter queue for malformed/failed events
 * - Unified connection pooling and lifecycle management
 * - Unified metrics and observability
 *
 * **Channel Types:**
 * - StandardChannels: Predefined channels (GRAPH_LIFECYCLE, TOOL_CALLS, etc.)
 * - Custom channels: User-defined typed channels
 *
 * **Automatic Serialization:**
 * EventBus uses SchemaRegistry to automatically serialize/deserialize events.
 * This ensures all publishers/subscribers use the same versioned schema, preventing mismatches.
 *
 * **Example Usage:**
 * ```kotlin
 * val eventBus = InMemoryEventBus(schemaRegistry = mySchemaRegistry)
 *
 * // Publish typed event (automatic serialization!)
 * eventBus.publish(
 *     StandardChannels.GRAPH_LIFECYCLE,
 *     GraphLifecycleEvent.Started(graphId, runId, message),
 *     EventMetadata(source = "graph-runner")
 * )
 *
 * // Subscribe to typed channel (automatic deserialization!)
 * eventBus.subscribe(StandardChannels.TOOL_CALLS)
 *     .collect { typedEvent ->
 *         // typedEvent.event is already deserialized to ToolCallEvent!
 *         when (val event = typedEvent.event) {
 *             is ToolCallEvent.Emitted -> handleEmitted(event)
 *             is ToolCallEvent.Completed -> handleCompleted(event)
 *             else -> {}
 *         }
 *     }
 *
 * // Create custom channel
 * val myChannel = eventBus.channel(
 *     "my.custom.channel",
 *     MyEvent::class,
 *     ChannelConfig(enableHistory = true, historySize = 1000)
 * )
 * eventBus.publish(myChannel, MyEvent("data"))
 * ```
 *
 * **Thread Safety:**
 * All implementations must be thread-safe for concurrent pub/sub operations.
 *
 * **Schema Evolution:**
 * Events are automatically versioned. Subscribers receive events even if schema version differs
 * (within same major version). Unknown versions go to dead letter queue.
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
interface EventBus {
    /**
     * Get or create a typed channel
     *
     * @param name Channel name (unique identifier)
     * @param type Event type class
     * @param config Channel configuration
     * @return Typed event channel
     */
    fun <T : Any> channel(
        name: String,
        type: KClass<T>,
        config: ChannelConfig = ChannelConfig.DEFAULT
    ): EventChannel<T>

    /**
     * Publish event to typed channel
     *
     * @param channel Typed event channel
     * @param event Event to publish
     * @param metadata Event metadata (traceId, userId, etc.)
     * @return SpiceResult with event ID
     */
    suspend fun <T : Any> publish(
        channel: EventChannel<T>,
        event: T,
        metadata: EventMetadata = EventMetadata.EMPTY
    ): SpiceResult<String>

    /**
     * Subscribe to typed channel
     *
     * Returns a Flow of typed events. Events can be filtered using EventFilter.
     *
     * @param channel Typed event channel
     * @param filter Optional event filter
     * @return Flow of typed events
     */
    fun <T : Any> subscribe(
        channel: EventChannel<T>,
        filter: EventFilter<T> = EventFilter.all()
    ): Flow<TypedEvent<T>>

    /**
     * Get event bus statistics
     *
     * @return EventBusStats with publish/consume metrics
     */
    suspend fun getStats(): EventBusStats

    /**
     * Close event bus and release resources
     *
     * Should be called during application shutdown.
     * Closes all channels, consumers, producers, and connection pools.
     */
    suspend fun close()
}

/**
 * Extension function to get typed channel
 */
inline fun <reified T : Any> EventBus.channel(
    name: String,
    config: ChannelConfig = ChannelConfig.DEFAULT
): EventChannel<T> = channel(name, T::class, config)

/**
 * Extension function to publish event with type inference
 */
suspend inline fun <reified T : Any> EventBus.publish(
    channel: EventChannel<T>,
    event: T,
    metadata: EventMetadata = EventMetadata.EMPTY
): SpiceResult<String> = publish(channel, event, metadata)

/**
 * Extension function to subscribe with type inference
 */
inline fun <reified T : Any> EventBus.subscribe(
    channel: EventChannel<T>,
    filter: EventFilter<T> = EventFilter.all()
): Flow<TypedEvent<T>> = subscribe(channel, filter)
