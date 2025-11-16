package io.github.noailabs.spice.eventbus.impl

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.eventbus.*
import io.github.noailabs.spice.eventbus.dlq.DeadLetterQueue
import io.github.noailabs.spice.eventbus.dlq.RedisDeadLetterQueue
import io.github.noailabs.spice.eventbus.schema.SchemaRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.params.XAddParams
import redis.clients.jedis.params.XReadGroupParams
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

/**
 * 🚀 Redis Streams Event Bus Implementation
 *
 * Production-ready, distributed event bus using Redis Streams.
 * Supports consumer groups, event history, and automatic serialization.
 *
 * **Key Features:**
 * - **Distributed pub/sub**: Multiple instances can share event streams
 * - **Consumer groups**: Parallel processing with load balancing
 * - **Event history**: Redis Streams naturally persist messages
 * - **Schema enforcement**: Channels must have registered schemas
 * - **Automatic serialization**: Uses SchemaRegistry for all serialization
 * - **Dead letter queue**: Failed deserialization → DLQ
 * - **Event filtering**: Type-safe filtering with composable filters
 *
 * **Redis Data Structures:**
 * - Stream: `{namespace}:stream:{channelName}` - Event stream
 * - Consumer Group: `{namespace}:cg:{channelName}:{consumerGroup}` - Consumer group name
 * - Stats: Tracked via stream info commands
 *
 * **Limitations:**
 * - Requires Redis 5.0+ (for Streams support)
 * - Consumer must ACK messages manually
 * - Memory usage scales with history size (use MAXLEN for trimming)
 *
 * **Usage:**
 * ```kotlin
 * val jedisPool = JedisPool("localhost", 6379)
 * val registry = DefaultSchemaRegistry()
 * registry.register(MyEvent::class, "1.0.0", MyEvent.serializer())
 *
 * val eventBus = RedisStreamsEventBus(
 *     jedisPool = jedisPool,
 *     schemaRegistry = registry,
 *     namespace = "spice:events",
 *     consumerGroup = "my-service",
 *     consumerId = "instance-1"
 * )
 *
 * val channel = eventBus.channel<MyEvent>("my.events", "1.0.0")
 * eventBus.publish(channel, MyEvent("data"))
 *
 * eventBus.subscribe(channel).collect { typedEvent ->
 *     println(typedEvent.event)
 * }
 * ```
 *
 * @property jedisPool Redis connection pool
 * @property schemaRegistry Schema registry for serialization (required)
 * @property deadLetterQueue Dead letter queue for failed events
 * @property namespace Redis key namespace (default: "spice:eventbus")
 * @property consumerGroup Consumer group name for this instance
 * @property consumerId Unique consumer ID within the group
 * @property maxLen Maximum stream length (null = unlimited, or use MAXLEN for trimming)
 * @property blockMs Blocking timeout for XREADGROUP (default: 1000ms)
 * @property batchSize Number of messages to read per XREADGROUP call (default: 100)
 * @property consumerGroupStartPosition Starting position for new consumer groups (default: LAST_ENTRY for production, use "0-0" for tests)
 * @property pendingRecoveryEnabled Enable pending entry recovery (default: true)
 * @property pendingRecoveryIntervalMs Interval between pending recovery checks (default: 30000ms = 30s)
 * @property pendingIdleTimeMs Time before a pending message is considered stuck and eligible for recovery (default: 60000ms = 1min)
 * @property maxPendingRetries Maximum number of times to retry a pending message before sending to DLQ (default: 3)
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
class RedisStreamsEventBus(
    private val jedisPool: JedisPool,
    override val schemaRegistry: SchemaRegistry,
    private val deadLetterQueue: DeadLetterQueue = RedisDeadLetterQueue(jedisPool),
    private val namespace: String = "spice:eventbus",
    private val consumerGroup: String = "default-group",
    private val consumerId: String = "consumer-${System.currentTimeMillis()}",
    private val maxLen: Long? = 10000,
    private val blockMs: Int = 1000,
    private val batchSize: Int = 100,
    private val consumerGroupStartPosition: StreamEntryID = StreamEntryID.LAST_ENTRY,
    private val pendingRecoveryEnabled: Boolean = true,
    private val pendingRecoveryIntervalMs: Long = 30000,
    private val pendingIdleTimeMs: Long = 60000,
    private val maxPendingRetries: Int = 3,
    private val streamTrimmingEnabled: Boolean = true,
    private val streamTrimmingIntervalMs: Long = 60000, // Trim every minute
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
    private val onDLQWrite: ((EventEnvelope, String) -> Unit)? = null,
    private val dlqDispatcher: CoroutineDispatcher = Dispatchers.Default
) : EventBus {

    // Active subscriber jobs: channelName → Job
    private val subscriberJobs = ConcurrentHashMap<String, Job>()

    // Active subscriber flows: channelName → MutableSharedFlow<TypedEvent<*>>
    private val subscriberFlows = ConcurrentHashMap<String, MutableSharedFlow<TypedEvent<*>>>()

    // Consumer ready signals: channelName → CompletableDeferred<Unit>
    private val consumerReadySignals = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    // Pending recovery jobs: channelName → Job
    private val recoveryJobs = ConcurrentHashMap<String, Job>()

    // Stream trimming jobs: channelName → Job
    private val trimmingJobs = ConcurrentHashMap<String, Job>()

    // Shutdown flag
    private val isShutdown = AtomicBoolean(false)

    // Metrics
    private val publishedCount = AtomicLong(0)
    private val consumedCount = AtomicLong(0)
    private val errorCount = AtomicLong(0)

    // Coroutine scope for async DLQ operations
    private var dlqScope: CoroutineScope? = null
    private fun getDlqScope(): CoroutineScope {
        if (dlqScope == null) {
            dlqScope = CoroutineScope(dlqDispatcher + SupervisorJob())
        }
        return dlqScope!!
    }

    // Coroutine scope for subscriber management
    private val subscriberScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun <T : Any> channel(
        name: String,
        type: KClass<T>,
        version: String,
        config: ChannelConfig
    ): EventChannel<T> {
        // Validate schema is registered
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

        // Ensure consumer group exists for this stream
        ensureConsumerGroup(name, consumerGroup)

        return EventChannel(name, type, version, config)
    }

    override suspend fun <T : Any> publish(
        channel: EventChannel<T>,
        event: T,
        metadata: EventMetadata
    ): SpiceResult<String> {
        return try {
            // Get serializer from registry
            val serializer = schemaRegistry.getSerializer(channel.type, channel.version)
                ?: return SpiceResult.failure(
                    SpiceError.validationError(
                        "No serializer found for ${channel.type.qualifiedName}:${channel.version}"
                    )
                )

            // Serialize event
            val payload = json.encodeToString(serializer, event)

            // Create envelope
            val envelope = EventEnvelope(
                channelName = channel.name,
                eventType = channel.type.qualifiedName ?: channel.type.simpleName ?: "Unknown",
                schemaVersion = channel.version,
                payload = payload,
                metadata = metadata,
                timestamp = Clock.System.now()
            )

            // Publish to Redis Stream
            val streamKey = streamKey(channel.name)
            val messageId = jedisPool.resource.use { jedis ->
                val fields = mutableMapOf(
                    "id" to envelope.id,
                    "channelName" to envelope.channelName,
                    "eventType" to envelope.eventType,
                    "schemaVersion" to envelope.schemaVersion,
                    "payload" to envelope.payload,
                    "metadata" to json.encodeToString(EventMetadata.serializer(), envelope.metadata),
                    "timestamp" to envelope.timestamp.toString()
                )

                // Publish without synchronous trimming (trimming happens async in background job)
                jedis.xadd(streamKey, XAddParams.xAddParams(), fields)
            }

            publishedCount.incrementAndGet()
            SpiceResult.success(messageId.toString())

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

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> subscribe(
        channel: EventChannel<T>,
        filter: EventFilter<T>
    ): Flow<TypedEvent<T>> {
        if (isShutdown.get()) {
            return emptyFlow()
        }

        val streamKey = streamKey(channel.name)

        // Get or create shared flow for this channel
        // Use at least replay=1 to buffer events for late collectors (asynchronous consumer startup)
        val replaySize = if (channel.config.enableHistory) {
            channel.config.historySize
        } else {
            1  // Minimum buffer for late collectors
        }

        val sharedFlow = subscriberFlows.getOrPut(channel.name) {
            MutableSharedFlow<TypedEvent<*>>(
                replay = replaySize,
                extraBufferCapacity = 1000,
                onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
            )
        } as MutableSharedFlow<TypedEvent<T>>

        // Create ready signal for this channel if not exists
        val readySignal = consumerReadySignals.getOrPut(channel.name) {
            CompletableDeferred()
        }

        // Start background consumer if not already running
        subscriberJobs.computeIfAbsent(channel.name) { _ ->
            subscriberScope.launch {
                consumeStream(streamKey, consumerGroup, consumerId, channel, sharedFlow, readySignal)
            }
        }

        // Start pending recovery job if enabled and not already running
        // FIXME: XPENDING/XCLAIM recovery disabled due to Jedis API type inference issues
        // This is a PRODUCTION RISK - messages can be lost on consumer crashes!
        // See: https://github.com/redis/jedis/issues for proper API typing
        // Alternative: migrate to Lettuce or implement manual field extraction
        // if (pendingRecoveryEnabled) {
        //     recoveryJobs.computeIfAbsent(channel.name) { _ ->
        //         subscriberScope.launch {
        //             recoverPendingEntries(streamKey, consumerGroup, channel, sharedFlow)
        //         }
        //     }
        // }

        // Start async stream trimming job if enabled and not already running
        if (streamTrimmingEnabled && maxLen != null) {
            trimmingJobs.computeIfAbsent(channel.name) { _ ->
                subscriberScope.launch {
                    trimStream(streamKey, maxLen)
                }
            }
        }

        // Return filtered flow
        return sharedFlow.filter { typedEvent ->
            filter.matches(typedEvent)
        }
    }

    /**
     * Background coroutine that consumes messages from Redis Stream
     */
    private suspend fun <T : Any> consumeStream(
        streamKey: String,
        groupName: String,
        consumerId: String,
        channel: EventChannel<T>,
        sharedFlow: MutableSharedFlow<TypedEvent<T>>,
        readySignal: CompletableDeferred<Unit>
    ) {
        // Signal that consumer is ready before starting the read loop
        readySignal.complete(Unit)

        while (!isShutdown.get()) {
            try {
                val messages = jedisPool.resource.use { jedis ->
                    jedis.xreadGroup(
                        groupName,
                        consumerId,
                        XReadGroupParams.xReadGroupParams()
                            .count(batchSize)
                            .block(blockMs),
                        mapOf(streamKey to StreamEntryID.UNRECEIVED_ENTRY)
                    )
                }

                messages?.forEach { streamMessages ->
                    streamMessages.value.forEach { streamEntry ->
                        try {
                            // Deserialize envelope
                            val fields = streamEntry.fields
                            val envelope = EventEnvelope(
                                id = fields["id"] ?: streamEntry.id.toString(),
                                channelName = fields["channelName"] ?: channel.name,
                                eventType = fields["eventType"] ?: "",
                                schemaVersion = fields["schemaVersion"] ?: channel.version,
                                payload = fields["payload"] ?: "",
                                metadata = fields["metadata"]?.let {
                                    json.decodeFromString(EventMetadata.serializer(), it)
                                } ?: EventMetadata(),
                                timestamp = fields["timestamp"]?.let {
                                    kotlinx.datetime.Instant.parse(it)
                                } ?: Clock.System.now()
                            )

                            // Deserialize event
                            val serializer = schemaRegistry.getSerializer(channel.type, channel.version)
                                ?: throw IllegalStateException(
                                    "No serializer for ${channel.type.qualifiedName}:${channel.version}"
                                )

                            val event = json.decodeFromString(serializer, envelope.payload)

                            // Emit to flow
                            sharedFlow.emit(
                                TypedEvent(
                                    id = envelope.id,
                                    event = event,
                                    envelope = envelope,
                                    receivedAt = Clock.System.now()
                                )
                            )

                            consumedCount.incrementAndGet()

                            // ACK message
                            jedisPool.resource.use { jedis ->
                                jedis.xack(streamKey, groupName, streamEntry.id)
                            }

                        } catch (e: Exception) {
                            // Send to DLQ
                            errorCount.incrementAndGet()
                            val fields = streamEntry.fields
                            val envelope = EventEnvelope(
                                id = fields["id"] ?: streamEntry.id.toString(),
                                channelName = fields["channelName"] ?: channel.name,
                                eventType = fields["eventType"] ?: "",
                                schemaVersion = fields["schemaVersion"] ?: channel.version,
                                payload = fields["payload"] ?: "",
                                metadata = fields["metadata"]?.let {
                                    json.decodeFromString(EventMetadata.serializer(), it)
                                } ?: EventMetadata(),
                                timestamp = fields["timestamp"]?.let {
                                    kotlinx.datetime.Instant.parse(it)
                                } ?: Clock.System.now()
                            )

                            val reason = "Deserialization failed: ${e.message}"
                            getDlqScope().launch {
                                deadLetterQueue.send(envelope, reason, e)
                                onDLQWrite?.invoke(envelope, reason)
                            }

                            // ACK failed message (so it doesn't retry infinitely)
                            jedisPool.resource.use { jedis ->
                                jedis.xack(streamKey, groupName, streamEntry.id)
                            }
                        }
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isShutdown.get()) {
                    errorCount.incrementAndGet()
                    // Log error and continue
                    delay(1000) // Back off on error
                }
            }
        }
    }

    /**
     * STUB: Periodically recover pending (unacknowledged) entries from Redis Stream
     *
     * ⚠️ NOT IMPLEMENTED - PRODUCTION RISK
     *
     * Without this, messages that are delivered but not ACKed before a consumer
     * crashes will remain in Redis Streams' pending list forever, causing silent
     * data loss.
     *
     * Required implementation:
     * 1. Periodically call XPENDING to find idle entries
     * 2. Use XCLAIM to re-deliver entries that exceed pendingIdleTimeMs
     * 3. Check delivery count and route to DLQ after maxPendingRetries
     *
     * Blocked by: Jedis API type inference issues with StreamEntry field accessors
     * Alternative solutions:
     * - Migrate to Lettuce client (better Kotlin interop)
     * - Manual field extraction with explicit casts
     * - Wrapper functions to handle Jedis type ambiguity
     *
     * See: https://github.com/redis/jedis/issues
     */
    @Suppress("UNUSED_PARAMETER", "unused")
    private suspend fun <T : Any> recoverPendingEntries(
        streamKey: String,
        groupName: String,
        channel: EventChannel<T>,
        sharedFlow: MutableSharedFlow<TypedEvent<T>>
    ) {
        // TODO: Implement XPENDING/XCLAIM recovery
        // This is critical for production - messages WILL be lost without it
    }

    /**
     * Periodically trim Redis Stream to prevent unbounded growth
     *
     * Runs XTRIM asynchronously at configured interval to limit stream length.
     * Uses approximate trimming (~) for better performance.
     *
     * @param streamKey Redis stream key to trim
     * @param maxLength Maximum number of entries to keep (approximate)
     */
    private suspend fun trimStream(streamKey: String, maxLength: Long) {
        if (!streamTrimmingEnabled) return

        while (!isShutdown.get()) {
            try {
                delay(streamTrimmingIntervalMs)

                // Trim stream asynchronously
                jedisPool.resource.use { jedis ->
                    // Use XTRIM with MAXLEN ~ (approximate) for better performance
                    // This doesn't block and allows Redis to trim when convenient
                    jedis.xtrim(streamKey, maxLength, true) // true = approximate
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Log error but continue trimming
                // In production, this should be logged to monitoring system
            }
        }
    }

    override suspend fun getStats(): EventBusStats {
        val dlqStats = deadLetterQueue.getStats()

        // Get stream info from Redis
        val activeChannels = subscriberFlows.size
        val activeSubscribers = subscriberJobs.size

        return EventBusStats(
            published = publishedCount.get(),
            consumed = consumedCount.get(),
            pending = calculatePending(),
            errors = errorCount.get(),
            activeChannels = activeChannels,
            activeSubscribers = activeSubscribers,
            deadLetterMessages = dlqStats.totalMessages
        )
    }

    override suspend fun close() {
        isShutdown.set(true)

        // Cancel all subscriber jobs
        subscriberJobs.values.forEach { it.cancel() }
        subscriberJobs.clear()

        // Cancel all recovery jobs
        recoveryJobs.values.forEach { it.cancel() }
        recoveryJobs.clear()

        // Cancel all trimming jobs
        trimmingJobs.values.forEach { it.cancel() }
        trimmingJobs.clear()

        // Clear flows
        subscriberFlows.clear()

        // Clear ready signals
        consumerReadySignals.clear()

        // Cancel DLQ scope
        dlqScope?.cancel()

        // Cancel subscriber scope
        subscriberScope.cancel()
    }

    /**
     * Ensure consumer group exists for the stream
     */
    private fun ensureConsumerGroup(channelName: String, groupName: String) {
        val streamKey = streamKey(channelName)

        jedisPool.resource.use { jedis ->
            try {
                // Try to create consumer group
                // mkStream = true creates the stream if it doesn't exist
                jedis.xgroupCreate(streamKey, groupName, consumerGroupStartPosition, true)
            } catch (e: Exception) {
                // Group might already exist - ignore BUSYGROUP error
                if (!e.message?.contains("BUSYGROUP", ignoreCase = true).orFalse()) {
                    throw e
                }
            }
        }
    }

    /**
     * Calculate pending messages across all streams
     */
    private fun calculatePending(): Long {
        return try {
            jedisPool.resource.use { jedis ->
                subscriberFlows.keys.sumOf { channelName ->
                    val streamKey = streamKey(channelName)
                    try {
                        val pending = jedis.xpending(streamKey, consumerGroup)
                        pending?.total ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get stream key for a channel
     */
    private fun streamKey(channelName: String): String {
        return "$namespace:stream:$channelName"
    }

    /**
     * Get Dead Letter Queue for inspection
     */
    fun getDeadLetterQueue(): DeadLetterQueue = deadLetterQueue

    /**
     * Wait for consumer to be ready for a specific channel (useful for tests)
     *
     * @param channelName The channel name to wait for
     * @param timeoutMs Maximum time to wait in milliseconds (default: 5000ms)
     * @return true if consumer is ready, false if timeout
     */
    suspend fun awaitConsumerReady(channelName: String, timeoutMs: Long = 5000): Boolean {
        val signal = consumerReadySignals[channelName] ?: return false
        return try {
            withTimeout(timeoutMs) {
                signal.await()
                true
            }
        } catch (e: TimeoutCancellationException) {
            false
        }
    }
}

private fun Boolean?.orFalse(): Boolean = this ?: false
