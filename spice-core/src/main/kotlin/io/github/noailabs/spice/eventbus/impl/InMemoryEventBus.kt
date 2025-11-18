package io.github.noailabs.spice.eventbus.impl

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.eventbus.*
import io.github.noailabs.spice.eventbus.dlq.DeadLetterQueue
import io.github.noailabs.spice.eventbus.dlq.InMemoryDeadLetterQueue
import io.github.noailabs.spice.eventbus.schema.SchemaRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

/**
 * 📦 In-Memory Event Bus Implementation
 *
 * Thread-safe, in-memory event bus with automatic serialization via SchemaRegistry.
 * Suitable for testing, development, and single-instance deployments.
 *
 * **Key Features:**
 * - **Schema enforcement**: Channels must have registered schemas
 * - **Automatic serialization**: Uses SchemaRegistry for all serialization/deserialization
 * - **Type safety**: Compile-time type checking for events
 * - **Dead letter queue**: Failed deserialization → DLQ (with observability!)
 * - **Event history**: Configurable replay buffer per channel
 * - **Event filtering**: Type-safe filtering with composable filters
 *
 * **Observability:**
 * - DLQ metrics tracked automatically
 * - Optional DLQ callback for monitoring/alerting
 * - Stats exposed via getStats()
 *
 * **Limitations:**
 * - Not distributed (single JVM only)
 * - Data lost on restart (no persistence)
 * - Limited by heap memory
 *
 * **Usage:**
 * ```kotlin
 * // 1. Register schemas
 * val registry = DefaultSchemaRegistry()
 * registry.register(MyEvent::class, "1.0.0", MyEvent.serializer())
 *
 * // 2. Create EventBus with DLQ callback
 * val eventBus = InMemoryEventBus(
 *     schemaRegistry = registry,
 *     deadLetterQueue = InMemoryDeadLetterQueue(),
 *     onDLQWrite = { envelope, reason ->
 *         logger.warn("Event sent to DLQ: ${envelope.id}, reason: $reason")
 *     }
 * )
 *
 * // 3. Create channel (enforces schema registration!)
 * val channel = eventBus.channel<MyEvent>(
 *     name = "my.events",
 *     version = "1.0.0"
 * )
 *
 * // 4. Publish (automatic serialization!)
 * eventBus.publish(channel, MyEvent("data"))
 *
 * // 5. Subscribe (automatic deserialization!)
 * eventBus.subscribe(channel).collect { typedEvent ->
 *     println(typedEvent.event)  // Already deserialized!
 * }
 *
 * // 6. Monitor DLQ
 * val stats = eventBus.getStats()
 * println("DLQ messages: ${stats.deadLetterMessages}")
 * ```
 *
 * @property schemaRegistry Schema registry for serialization (required)
 * @property deadLetterQueue Dead letter queue for failed events
 * @property json JSON serializer configuration
 * @property onDLQWrite Optional callback when events are sent to DLQ
 * @property dlqDispatcher Dispatcher for DLQ background operations (default: Dispatchers.Default, use Dispatchers.Unconfined for tests)
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
class InMemoryEventBus(
    override val schemaRegistry: SchemaRegistry,
    private val deadLetterQueue: DeadLetterQueue = InMemoryDeadLetterQueue(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
    private val onDLQWrite: ((EventEnvelope, String) -> Unit)? = null,
    private val dlqDispatcher: CoroutineDispatcher = Dispatchers.Default
) : EventBus {

    // Channel flows: channelName → MutableSharedFlow<EventEnvelope>
    private val channelFlows = ConcurrentHashMap<String, MutableSharedFlow<EventEnvelope>>()

    // Channel metadata
    private val channelConfigs = ConcurrentHashMap<String, ChannelConfig>()

    // Metrics
    private val publishedCount = AtomicLong(0)
    private val consumedCount = AtomicLong(0)
    private val errorCount = AtomicLong(0)

    // Mutex for channel creation
    private val channelMutex = Mutex()

    // Coroutine scope for async DLQ operations (lazy to avoid creating unused scopes in tests)
    private var dlqScope: CoroutineScope? = null
    private fun getDlqScope(): CoroutineScope {
        if (dlqScope == null) {
            dlqScope = CoroutineScope(dlqDispatcher + SupervisorJob())
        }
        return dlqScope!!
    }

    override fun <T : Any> channel(
        name: String,
        type: KClass<T>,
        version: String,
        config: ChannelConfig
    ): EventChannel<T> {
        // 1. Validate schema is registered (ENFORCED!)
        require(schemaRegistry.isRegistered(type, version)) {
            """
            Schema not registered: ${type.qualifiedName}:$version

            Register it first:
            schemaRegistry.register(
                type = ${type.simpleName}::class,
                version = "$version",
                serializer = ${type.simpleName}.serializer()
            )
            """.trimIndent()
        }

        // 2. Get or create channel flow
        channelFlows.getOrPut(name) {
            MutableSharedFlow(
                replay = if (config.enableHistory) config.historySize else 0,
                extraBufferCapacity = 1000,
                onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
            )
        }

        // 3. Store config
        channelConfigs[name] = config

        return EventChannel(name, type, version, config)
    }

    override suspend fun <T : Any> publish(
        channel: EventChannel<T>,
        event: T,
        metadata: EventMetadata
    ): SpiceResult<String> {
        return try {
            // 1. Get serializer from registry (ENFORCED!)
            val serializer = schemaRegistry.getSerializer(channel.type, channel.version)
                ?: return SpiceResult.failure(
                    SpiceError.validationError(
                        "No serializer found for ${channel.type.qualifiedName}:${channel.version}. " +
                        "Did you forget to register the schema?"
                    )
                )

            // 2. Serialize event
            val payload = json.encodeToString(serializer, event)

            // 3. Create envelope
            val envelope = EventEnvelope(
                channelName = channel.name,
                eventType = channel.type.qualifiedName ?: channel.type.simpleName ?: "Unknown",
                schemaVersion = channel.version,
                payload = payload,
                metadata = metadata,
                timestamp = Clock.System.now()
            )

            // 4. Get channel flow
            val flow = channelFlows[channel.name]
                ?: return SpiceResult.failure(
                    SpiceError.validationError("Channel not found: ${channel.name}")
                )

            // 5. Emit to flow
            flow.emit(envelope)

            // 6. Update metrics
            publishedCount.incrementAndGet()

            SpiceResult.success(envelope.id)

        } catch (e: Exception) {
            errorCount.incrementAndGet()
            SpiceResult.failure(
                SpiceError.executionError(
                    "Failed to publish event to channel ${channel.name}: ${e.message}",
                    cause = e
                )
            )
        }
    }

    override fun <T : Any> subscribe(
        channel: EventChannel<T>,
        filter: EventFilter<T>
    ): Flow<TypedEvent<T>> {
        val flow = channelFlows[channel.name] ?: return emptyFlow()

        return flow
            .mapNotNull { envelope ->
                try {
                    // 1. Get serializer from registry
                    val serializer = schemaRegistry.getSerializer(channel.type, channel.version)
                        ?: throw IllegalStateException(
                            "No serializer for ${channel.type.qualifiedName}:${channel.version}"
                        )

                    // 2. Deserialize event
                    val event = json.decodeFromString(serializer, envelope.payload)

                    // 3. Update metrics
                    consumedCount.incrementAndGet()

                    // 4. Wrap in TypedEvent
                    TypedEvent(
                        id = envelope.id,
                        event = event,
                        envelope = envelope,
                        receivedAt = Clock.System.now()
                    )

                } catch (e: Exception) {
                    // Send to DLQ (async, fire-and-forget)
                    errorCount.incrementAndGet()
                    val reason = "Deserialization failed: ${e.message}"

                    getDlqScope().launch {
                        deadLetterQueue.send(
                            originalEnvelope = envelope,
                            reason = reason,
                            error = e
                        )

                        // Invoke callback for observability
                        onDLQWrite?.invoke(envelope, reason)
                    }
                    null // Filter out failed events
                }
            }
            .filter { typedEvent ->
                // Apply user filter
                filter.matches(typedEvent)
            }
    }

    override suspend fun getStats(): EventBusStats {
        val dlqStats = deadLetterQueue.getStats()

        return EventBusStats(
            published = publishedCount.get(),
            consumed = consumedCount.get(),
            pending = calculatePending(),
            errors = errorCount.get(),
            activeChannels = channelFlows.size,
            activeSubscribers = calculateActiveSubscribers(),
            deadLetterMessages = dlqStats.totalMessages
        )
    }

    override suspend fun close() {
        // Cancel DLQ scope to prevent uncompleted coroutines (only if it was created)
        dlqScope?.cancel()

        // Complete all flows
        channelFlows.values.forEach { flow ->
            // MutableSharedFlow doesn't have completion, just clear
        }
        channelFlows.clear()
        channelConfigs.clear()
    }

    /**
     * Calculate pending messages across all channels
     */
    private fun calculatePending(): Long {
        return channelFlows.values.sumOf { flow ->
            flow.replayCache.size.toLong()
        }
    }

    /**
     * Calculate active subscribers (approximate)
     */
    private fun calculateActiveSubscribers(): Int {
        return channelFlows.values.sumOf { flow ->
            flow.subscriptionCount.value
        }
    }

    /**
     * Get channel configuration (for debugging)
     */
    fun getChannelConfig(channelName: String): ChannelConfig? {
        return channelConfigs[channelName]
    }

    /**
     * Get all active channels (for debugging)
     */
    fun getActiveChannels(): Set<String> {
        return channelFlows.keys.toSet()
    }

    /**
     * Clear all channels (for testing)
     */
    suspend fun clearAllChannels() = channelMutex.withLock {
        channelFlows.clear()
        channelConfigs.clear()
        publishedCount.set(0)
        consumedCount.set(0)
        errorCount.set(0)
    }

    /**
     * Get Dead Letter Queue for inspection
     *
     * Allows operators to inspect DLQ contents without diving into internals.
     *
     * **Usage:**
     * ```kotlin
     * val dlqMessages = eventBus.getDeadLetterQueue().getMessages().getOrNull()
     * dlqMessages?.forEach { msg ->
     *     println("DLQ: ${msg.id}, reason: ${msg.reason}")
     * }
     * ```
     */
    fun getDeadLetterQueue(): DeadLetterQueue = deadLetterQueue
}
