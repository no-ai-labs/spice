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
 * **Automatic Serialization (ENFORCED):**
 * EventBus REQUIRES SchemaRegistry for all channels. When you create a channel,
 * the event type MUST be registered in the SchemaRegistry first. This prevents
 * schema mismatches and incompatible payloads at compile time.
 *
 * **Example Usage:**
 * ```kotlin
 * // 1. Register schema first (REQUIRED!)
 * val registry = DefaultSchemaRegistry()
 * registry.register(MyEvent::class, "1.0.0", MyEvent.serializer())
 *
 * // 2. Create EventBus with registry
 * val eventBus = InMemoryEventBus(schemaRegistry = registry)
 *
 * // 3. Create channel (fails if schema not registered!)
 * val myChannel = eventBus.channel(
 *     "my.custom.channel",
 *     MyEvent::class,
 *     version = "1.0.0",  // Must match registered version
 *     config = ChannelConfig(enableHistory = true)
 * )
 *
 * // 4. Publish (automatic serialization using registry!)
 * eventBus.publish(myChannel, MyEvent("data"))
 *
 * // 5. Subscribe (automatic deserialization using registry!)
 * eventBus.subscribe(myChannel).collect { typedEvent ->
 *     // typedEvent.event is already deserialized to MyEvent!
 *     println(typedEvent.event.data)
 * }
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
     * Get SchemaRegistry used by this EventBus
     *
     * All channels must have their schemas registered here.
     */
    val schemaRegistry: io.github.noailabs.spice.eventbus.schema.SchemaRegistry

    /**
     * Get or create a typed channel
     *
     * **IMPORTANT**: The event type MUST be registered in SchemaRegistry first!
     * This method will throw IllegalStateException if schema is not registered.
     *
     * @param name Channel name (unique identifier)
     * @param type Event type class
     * @param version Schema version (must be registered in SchemaRegistry)
     * @param config Channel configuration
     * @return Typed event channel
     * @throws IllegalStateException if schema not registered for this type/version
     */
    fun <T : Any> channel(
        name: String,
        type: KClass<T>,
        version: String,
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
 * Extension function to get typed channel with reified type
 */
inline fun <reified T : Any> EventBus.channel(
    name: String,
    version: String,
    config: ChannelConfig = ChannelConfig.DEFAULT
): EventChannel<T> = channel(name, T::class, version, config)

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
