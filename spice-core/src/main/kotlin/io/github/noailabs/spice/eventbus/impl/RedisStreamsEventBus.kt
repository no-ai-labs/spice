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

    // Channel configs: channelName → ChannelConfig (for per-channel retention)
    private val channelConfigs = ConcurrentHashMap<String, ChannelConfig>()

    // Global stream trimming job
    private var globalTrimmingJob: Job? = null

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

    init {
        // Start global stream trimming job if enabled
        if (streamTrimmingEnabled) {
            globalTrimmingJob = subscriberScope.launch {
                globalTrimLoop()
            }
        }
    }

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

        // Store channel config for global trimming loop
        channelConfigs[name] = config

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
        if (pendingRecoveryEnabled) {
            recoveryJobs.computeIfAbsent(channel.name) { _ ->
                subscriberScope.launch {
                    recoverPendingEntries(streamKey, consumerGroup, channel, sharedFlow)
                }
            }
        }

        // Note: Stream trimming now handled by global trimming loop (started in init)
        // This ensures all streams are trimmed regardless of subscription state

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
     * Periodically recover pending (unacknowledged) entries from Redis Stream
     *
     * This function prevents silent data loss by:
     * 1. Periodically calling XPENDING to find idle entries (messages delivered but not ACKed)
     * 2. Using XCLAIM to re-deliver entries that exceed pendingIdleTimeMs
     * 3. Checking delivery count and routing to DLQ after maxPendingRetries
     *
     * **Recovery Strategy**:
     * - Every `pendingRecoveryIntervalMs` (default: 30s), scan for pending messages
     * - Messages idle for > `pendingIdleTimeMs` (default: 60s) are eligible for recovery
     * - If delivery count >= `maxPendingRetries` (default: 3), route to DLQ
     * - Otherwise, XCLAIM to re-deliver to this consumer
     *
     * **Implementation Details**:
     * - Uses type-safe wrappers to handle Jedis API quirks
     * - Runs in background coroutine (started in subscribe(), cancelled in close())
     * - Handles deserialization failures gracefully (route to DLQ)
     * - Properly ACKs messages after DLQ routing to prevent infinite loops
     *
     * @since 1.0.0-alpha-6
     */
    private suspend fun <T : Any> recoverPendingEntries(
        streamKey: String,
        groupName: String,
        channel: EventChannel<T>,
        sharedFlow: MutableSharedFlow<TypedEvent<T>>
    ) {
        while (!isShutdown.get()) {
            try {
                delay(pendingRecoveryIntervalMs)

                // 1. Find idle pending entries using type-safe wrapper
                val pendingEntries = xpendingSafe(streamKey, groupName)

                // Filter entries that are idle and belong to any consumer
                val idleEntries = pendingEntries.filter { entry ->
                    entry.idleTime >= pendingIdleTimeMs
                }

                if (idleEntries.isEmpty()) {
                    continue
                }

                // 2. Process each idle entry
                for (entry in idleEntries) {
                    try {
                        // Check if exceeded max retries
                        if (entry.deliveryCount >= maxPendingRetries) {
                            // Route to DLQ
                            val claimedMessages = xclaimSafe(
                                streamKey = streamKey,
                                groupName = groupName,
                                consumerId = consumerId,
                                minIdleTime = pendingIdleTimeMs,
                                messageId = entry.id
                            )

                            claimedMessages.forEach { claimedEntry ->
                                try {
                                    // Extract envelope from claimed entry
                                    val envelope = EventEnvelope(
                                        id = claimedEntry.fields["id"] ?: claimedEntry.id,
                                        channelName = claimedEntry.fields["channelName"] ?: channel.name,
                                        eventType = claimedEntry.fields["eventType"] ?: "",
                                        schemaVersion = claimedEntry.fields["schemaVersion"] ?: channel.version,
                                        payload = claimedEntry.fields["payload"] ?: "",
                                        metadata = claimedEntry.fields["metadata"]?.let {
                                            json.decodeFromString(EventMetadata.serializer(), it)
                                        } ?: EventMetadata(),
                                        timestamp = claimedEntry.fields["timestamp"]?.let {
                                            kotlinx.datetime.Instant.parse(it)
                                        } ?: Clock.System.now()
                                    )

                                    // Send to DLQ
                                    val reason = "Max pending retries exceeded (${entry.deliveryCount} >= $maxPendingRetries)"
                                    getDlqScope().launch {
                                        deadLetterQueue.send(envelope, reason, null)
                                        onDLQWrite?.invoke(envelope, reason)
                                    }

                                    // ACK to prevent re-delivery
                                    jedisPool.resource.use { jedis ->
                                        jedis.xack(streamKey, groupName, StreamEntryID(claimedEntry.id))
                                    }

                                } catch (e: Exception) {
                                    errorCount.incrementAndGet()
                                    // Continue with next entry
                                }
                            }

                        } else {
                            // Re-deliver for retry
                            val claimedMessages = xclaimSafe(
                                streamKey = streamKey,
                                groupName = groupName,
                                consumerId = consumerId,
                                minIdleTime = pendingIdleTimeMs,
                                messageId = entry.id
                            )

                            claimedMessages.forEach { claimedEntry ->
                                try {
                                    // Deserialize and emit to flow (same logic as consumeStream)
                                    val envelope = EventEnvelope(
                                        id = claimedEntry.fields["id"] ?: claimedEntry.id,
                                        channelName = claimedEntry.fields["channelName"] ?: channel.name,
                                        eventType = claimedEntry.fields["eventType"] ?: "",
                                        schemaVersion = claimedEntry.fields["schemaVersion"] ?: channel.version,
                                        payload = claimedEntry.fields["payload"] ?: "",
                                        metadata = claimedEntry.fields["metadata"]?.let {
                                            json.decodeFromString(EventMetadata.serializer(), it)
                                        } ?: EventMetadata(),
                                        timestamp = claimedEntry.fields["timestamp"]?.let {
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
                                        jedis.xack(streamKey, groupName, StreamEntryID(claimedEntry.id))
                                    }

                                } catch (e: Exception) {
                                    // Deserialization failed - send to DLQ
                                    errorCount.incrementAndGet()
                                    val envelope = EventEnvelope(
                                        id = claimedEntry.fields["id"] ?: claimedEntry.id,
                                        channelName = claimedEntry.fields["channelName"] ?: channel.name,
                                        eventType = claimedEntry.fields["eventType"] ?: "",
                                        schemaVersion = claimedEntry.fields["schemaVersion"] ?: channel.version,
                                        payload = claimedEntry.fields["payload"] ?: "",
                                        metadata = claimedEntry.fields["metadata"]?.let {
                                            json.decodeFromString(EventMetadata.serializer(), it)
                                        } ?: EventMetadata(),
                                        timestamp = claimedEntry.fields["timestamp"]?.let {
                                            kotlinx.datetime.Instant.parse(it)
                                        } ?: Clock.System.now()
                                    )

                                    val reason = "Recovery deserialization failed: ${e.message}"
                                    getDlqScope().launch {
                                        deadLetterQueue.send(envelope, reason, e)
                                        onDLQWrite?.invoke(envelope, reason)
                                    }

                                    // ACK failed message
                                    jedisPool.resource.use { jedis ->
                                        jedis.xack(streamKey, groupName, StreamEntryID(claimedEntry.id))
                                    }
                                }
                            }
                        }

                    } catch (e: Exception) {
                        errorCount.incrementAndGet()
                        // Continue with next entry
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isShutdown.get()) {
                    errorCount.incrementAndGet()
                    // Log error and continue
                }
            }
        }
    }

    /**
     * Global trimming loop that scans all streams and trims them
     *
     * This runs independently of subscriptions, ensuring streams are trimmed even for:
     * - Publish-only channels (no active subscribers)
     * - Streams created before event bus started
     * - Channels that had subscribers but are currently idle
     *
     * For each stream:
     * 1. Extracts channel name from stream key
     * 2. Looks up channel config for per-channel maxLen
     * 3. Falls back to global maxLen if channel config not found
     * 4. Trims using XTRIM with approximate (~) for performance
     */
    private suspend fun globalTrimLoop() {
        if (!streamTrimmingEnabled) return

        while (!isShutdown.get()) {
            try {
                delay(streamTrimmingIntervalMs)

                // Scan for all streams matching our namespace pattern
                val streamPattern = "$namespace:stream:*"
                val streams = jedisPool.resource.use { jedis ->
                    val allKeys = mutableListOf<String>()
                    var cursor = "0"

                    // Use SCAN to find all matching stream keys
                    do {
                        val result = jedis.scan(cursor, redis.clients.jedis.params.ScanParams().match(streamPattern))
                        allKeys.addAll(result.result)
                        cursor = result.cursor
                    } while (cursor != "0")

                    allKeys
                }

                // Trim each stream
                streams.forEach { streamKey ->
                    try {
                        // Extract channel name from stream key: "namespace:stream:channelName" -> "channelName"
                        val channelName = streamKey.removePrefix("$namespace:stream:")

                        // Get max length: channel config > global default
                        val channelConfig = channelConfigs[channelName]
                        val effectiveMaxLen = when {
                            channelConfig?.maxLen != null && channelConfig.maxLen > 0 -> channelConfig.maxLen
                            maxLen != null && maxLen > 0 -> maxLen
                            else -> null // No trimming
                        }

                        // Trim if maxLen is configured
                        if (effectiveMaxLen != null) {
                            jedisPool.resource.use { jedis ->
                                // Use XTRIM with MAXLEN ~ (approximate) for better performance
                                jedis.xtrim(streamKey, effectiveMaxLen, true) // true = approximate
                            }
                        }
                    } catch (e: Exception) {
                        // Log error for this stream but continue with others
                        // In production, this should be logged to monitoring system
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Log error but continue trimming loop
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

    /**
     * Close the event bus and clean up all resources.
     *
     * **Resource Cleanup**:
     * - Cancels all subscriber jobs (consumer loops)
     * - Cancels all recovery jobs (pending entry recovery)
     * - Cancels global trimming job
     * - Cancels coroutine scopes (subscriber scope, DLQ scope)
     * - Clears all collections (flows, jobs, signals)
     * - Returns all Jedis connections to the pool
     *
     * **Shutdown Behavior**:
     * - Consumer loops may block for up to `blockMs` (default: 1 second) before cancellation takes effect
     * - In-flight DLQ writes are cancelled and may be lost
     * - JedisPool is NOT closed (it's an injected dependency - caller must close it)
     *
     * **Thread-Safety**: Safe to call multiple times (idempotent)
     */
    override suspend fun close() {
        isShutdown.set(true)

        // Cancel all subscriber jobs
        subscriberJobs.values.forEach { it.cancel() }
        subscriberJobs.clear()

        // Cancel all recovery jobs
        recoveryJobs.values.forEach { it.cancel() }
        recoveryJobs.clear()

        // Cancel global trimming job
        globalTrimmingJob?.cancel()
        globalTrimmingJob = null

        // Clear flows
        subscriberFlows.clear()

        // Clear ready signals
        consumerReadySignals.clear()

        // Cancel DLQ scope (in-flight writes may be lost)
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

    // ============================================================================
    // Type-Safe Jedis Wrappers for XPENDING/XCLAIM
    // ============================================================================

    /**
     * Type-safe wrapper for XPENDING result
     *
     * Jedis returns StreamPendingEntry with ambiguous field accessors that cause
     * Kotlin type inference issues. This data class provides a clean, type-safe API.
     */
    private data class PendingEntry(
        val id: String,
        val consumerName: String,
        val idleTime: Long,
        val deliveryCount: Long
    )

    /**
     * Type-safe wrapper for XCLAIM result
     *
     * Wraps StreamEntry with explicit field types to avoid Jedis API ambiguity.
     */
    private data class ClaimedEntry(
        val id: String,
        val fields: Map<String, String>
    )

    /**
     * Type-safe wrapper for Jedis XPENDING command
     *
     * Safely extracts pending entry data from Jedis API, handling type inference issues.
     *
     * @param streamKey Redis stream key
     * @param groupName Consumer group name
     * @return List of pending entries with type-safe accessors
     */
    private fun xpendingSafe(
        streamKey: String,
        groupName: String
    ): List<PendingEntry> {
        return try {
            jedisPool.resource.use { jedis ->
                // Get pending entries summary
                val pendingSummary: redis.clients.jedis.resps.StreamPendingSummary? =
                    jedis.xpending(streamKey, groupName)

                // If no pending entries, return empty list
                if (pendingSummary == null || pendingSummary.total == 0L) {
                    return@use emptyList<PendingEntry>()
                }

                // Get detailed pending entries with explicit type
                val detailedPending: List<redis.clients.jedis.resps.StreamPendingEntry>? =
                    jedis.xpending(
                        streamKey,
                        groupName,
                        redis.clients.jedis.params.XPendingParams()
                            .start(redis.clients.jedis.StreamEntryID.MINIMUM_ID)
                            .end(redis.clients.jedis.StreamEntryID.MAXIMUM_ID)
                            .count(100)
                    )

                detailedPending?.mapNotNull { entry: redis.clients.jedis.resps.StreamPendingEntry ->
                    try {
                        // Jedis 5.1.2 uses getDeliveredTimes() accessor
                        // (private field is "deliveredTimes", not "deliveryCount")
                        PendingEntry(
                            id = entry.id.toString(),
                            consumerName = entry.consumerName ?: "",
                            idleTime = entry.idleTime,
                            deliveryCount = entry.deliveredTimes  // Use official accessor
                        )
                    } catch (e: Exception) {
                        null  // Skip malformed entries
                    }
                } ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()  // Return empty on error (Redis down, group doesn't exist, etc.)
        }
    }

    /**
     * Type-safe wrapper for Jedis XCLAIM command
     *
     * Safely claims pending messages and extracts fields, handling Jedis type ambiguity.
     *
     * @param streamKey Redis stream key
     * @param groupName Consumer group name
     * @param consumerId Consumer ID claiming the message
     * @param minIdleTime Minimum idle time in milliseconds
     * @param messageId Message ID to claim
     * @return List of claimed entries with type-safe field accessors
     */
    private fun xclaimSafe(
        streamKey: String,
        groupName: String,
        consumerId: String,
        minIdleTime: Long,
        messageId: String
    ): List<ClaimedEntry> {
        return try {
            jedisPool.resource.use { jedis ->
                // XCLAIM with explicit type annotation
                val claimed: List<redis.clients.jedis.resps.StreamEntry>? = jedis.xclaim(
                    streamKey,
                    groupName,
                    consumerId,
                    minIdleTime,
                    redis.clients.jedis.params.XClaimParams(),
                    StreamEntryID(messageId)
                )

                claimed?.mapNotNull { entry: redis.clients.jedis.resps.StreamEntry ->
                    try {
                        // Safely extract fields with explicit type conversion
                        @Suppress("UNCHECKED_CAST")
                        val fields = (entry.fields as? Map<String, String>) ?: emptyMap()

                        ClaimedEntry(
                            id = entry.id.toString(),
                            fields = fields
                        )
                    } catch (e: Exception) {
                        null  // Skip malformed entries
                    }
                } ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()  // Return empty on error
        }
    }
}

private fun Boolean?.orFalse(): Boolean = this ?: false
