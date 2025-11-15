package io.github.noailabs.spice.event

import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.XAddParams
import redis.clients.jedis.params.XReadParams
import redis.clients.jedis.params.XReadGroupParams
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.exceptions.JedisDataException
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 🚌 Redis Streams Tool Call Event Bus
 *
 * Distributed event bus for tool call lifecycle events using Redis Streams.
 * Enables multi-instance, multi-agent orchestration across cluster nodes.
 *
 * **Architecture:**
 * ```
 * Agent A (Instance 1) → publish → Redis Stream
 *                                       ↓
 *                              Agent B (Instance 2) ← subscribe
 * ```
 *
 * **Features:**
 * - Distributed pub/sub across multiple instances
 * - Event persistence (events survive restarts)
 * - Consumer groups for load balancing and offset persistence
 * - Configurable starting position (new events only vs full replay)
 *
 * **Requirements:**
 * - Redis 5.0+ (for Redis Streams support)
 * - Proper Redis connection pooling configuration
 *
 * **Use Cases:**
 * - Multi-instance production deployments
 * - Cross-service agent orchestration
 * - Event sourcing and replay
 * - Persistent audit trail
 *
 * **Starting Position:**
 * - Default: "$" (only new events after startup)
 * - Full replay: "0-0" (all events from beginning)
 * - Consumer groups: Persistent offset, resumes from last acknowledged position
 *
 * @property jedisPool Redis connection pool
 * @property streamKey Redis stream key (default: "spice:toolcall:events")
 * @property consumerGroup Consumer group name (if null, no consumer group used)
 * @property consumerName Consumer name within group (defaults to UUID)
 * @property startFrom Starting stream ID ("$" for latest, "0-0" for beginning)
 * @property config Event bus configuration
 *
 * @since 1.0.0
 */
class RedisToolCallEventBus(
    private val jedisPool: JedisPool,
    private val streamKey: String = "spice:toolcall:events",
    private val consumerGroup: String? = null,
    private val consumerName: String = "consumer-${java.util.UUID.randomUUID()}",
    private val startFrom: String = "$",
    private val config: EventBusConfig = EventBusConfig.DEFAULT,
    private val pollInterval: Duration = 1.seconds,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "_type"
    }
) : ToolCallEventBus {

    // Local event flow for subscribers on this instance
    private val eventFlow = MutableSharedFlow<ToolCallEvent>(
        replay = 0,
        extraBufferCapacity = 100
    )

    // Event history (if enabled)
    private val history = mutableListOf<ToolCallEvent>()
    private val mutex = Mutex()

    // Background polling job
    private var pollingJob: Job? = null
    private var lastReadId: String? = null
    private var useConsumerGroup: Boolean = consumerGroup != null

    // Metrics
    private var publishCount = 0L
    private var subscriberCount = 0
    private var errors = 0L

    init {
        // Create consumer group if specified
        if (consumerGroup != null) {
            try {
                jedisPool.resource.use { jedis ->
                    try {
                        // Create group starting from specified position
                        jedis.xgroupCreate(streamKey, consumerGroup, StreamEntryID(startFrom), true)
                    } catch (e: JedisDataException) {
                        // Group already exists - that's fine
                        if (e.message?.contains("BUSYGROUP") != true) {
                            throw e
                        }
                    }
                }
            } catch (e: Exception) {
                // Log error but continue (group might already exist)
            }
        }

        // Start background polling to read from Redis and emit to local Flow
        startPolling()
    }

    override suspend fun publish(event: ToolCallEvent): SpiceResult<Unit> {
        return SpiceResult.catching {
            val payload = json.encodeToString<ToolCallEvent>(event)

            jedisPool.resource.use { jedis ->
                jedis.xadd(
                    streamKey,
                    mapOf(
                        "type" to event.typeName(),
                        "payload" to payload,
                        "toolCallId" to event.toolCall.id,
                        "runId" to (event.message.runId ?: "")
                    ),
                    XAddParams.xAddParams()
                )
            }

            // Store in history if enabled
            if (config.enableHistory) {
                mutex.withLock {
                    history.add(event)
                    if (history.size > config.historySize) {
                        history.removeAt(0)
                    }
                }
            }

            // Update metrics
            if (config.enableMetrics) {
                mutex.withLock {
                    publishCount++
                }
            }

            Unit
        }
    }

    override fun subscribe(): Flow<ToolCallEvent> {
        subscriberCount++
        return eventFlow
    }

    override fun subscribe(vararg eventTypes: KClass<out ToolCallEvent>): Flow<ToolCallEvent> {
        subscriberCount++
        return eventFlow.filter { event ->
            eventTypes.any { it.isInstance(event) }
        }
    }

    override fun subscribeToToolCall(toolCallId: String): Flow<ToolCallEvent> {
        subscriberCount++
        return eventFlow.filter { event ->
            event.toolCall.id == toolCallId
        }
    }

    override fun subscribeToRun(runId: String): Flow<ToolCallEvent> {
        subscriberCount++
        return eventFlow.filter { event ->
            when (event) {
                is ToolCallEvent.Emitted -> event.runId == runId
                else -> event.message.runId == runId
            }
        }
    }

    override suspend fun getHistory(limit: Int): SpiceResult<List<ToolCallEvent>> {
        return mutex.withLock {
            if (!config.enableHistory) {
                return@withLock SpiceResult.success(emptyList())
            }
            val events = history.takeLast(limit).reversed()
            SpiceResult.success(events)
        }
    }

    override suspend fun getToolCallHistory(toolCallId: String): SpiceResult<List<ToolCallEvent>> {
        return mutex.withLock {
            if (!config.enableHistory) {
                return@withLock SpiceResult.success(emptyList())
            }
            val events = history.filter { it.toolCall.id == toolCallId }
            SpiceResult.success(events)
        }
    }

    override suspend fun clearHistory(): SpiceResult<Unit> {
        return mutex.withLock {
            history.clear()
            SpiceResult.success(Unit)
        }
    }

    override suspend fun getSubscriberCount(): Int {
        return subscriberCount
    }

    /**
     * Start background polling from Redis Stream
     */
    private fun startPolling() {
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    pollEvents()
                } catch (e: Exception) {
                    // Log error but continue polling
                    delay(pollInterval)
                }
            }
        }
    }

    /**
     * Poll events from Redis Stream and emit to local Flow
     */
    private suspend fun pollEvents() {
        if (useConsumerGroup && consumerGroup != null) {
            pollEventsWithConsumerGroup()
        } else {
            pollEventsWithoutConsumerGroup()
        }
    }

    /**
     * Poll using consumer group (persistent offset)
     */
    private suspend fun pollEventsWithConsumerGroup() {
        val entries = jedisPool.resource.use { jedis ->
            try {
                jedis.xreadGroup(
                    consumerGroup,
                    consumerName,
                    XReadGroupParams.xReadGroupParams()
                        .count(100)
                        .block(pollInterval.inWholeMilliseconds.toInt()),
                    mapOf(streamKey to StreamEntryID.UNRECEIVED_ENTRY)
                )
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()

        for (streamEntries in entries) {
            for (entry in streamEntries.value) {
                when (val decoded = SpiceResult.catching {
                    val payload = entry.fields["payload"]
                        ?: error("Missing payload field in stream entry ${entry.id}")
                    json.decodeFromString<ToolCallEvent>(payload)
                }) {
                    is SpiceResult.Success -> {
                        // Emit to local Flow
                        eventFlow.emit(decoded.value)

                        // Acknowledge message
                        jedisPool.resource.use { jedis ->
                            jedis.xack(streamKey, consumerGroup, entry.id)
                        }
                    }
                    is SpiceResult.Failure -> {
                        // Record error metrics
                        if (config.enableMetrics) {
                            mutex.withLock { errors++ }
                        }

                        // Log error (using System.err until proper logger is added)
                        System.err.println(
                            "[RedisToolCallEventBus] Failed to decode event at ${entry.id}: " +
                            "${decoded.error.message} (type: ${decoded.error.code})"
                        )

                        // Don't ack - leave in pending list for manual inspection
                        // TODO: Add dead letter handler support
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            delay(pollInterval)
        }
    }

    /**
     * Poll without consumer group (ephemeral offset)
     */
    private suspend fun pollEventsWithoutConsumerGroup() {
        val startId = lastReadId ?: startFrom

        val entries = jedisPool.resource.use { jedis ->
            try {
                jedis.xread(
                    XReadParams.xReadParams()
                        .count(100)
                        .block(pollInterval.inWholeMilliseconds.toInt()),
                    mapOf(streamKey to StreamEntryID(startId))
                )
            } catch (e: JedisDataException) {
                null
            }
        } ?: emptyList()

        for (streamEntries in entries) {
            for (entry in streamEntries.value) {
                when (val decoded = SpiceResult.catching {
                    val payload = entry.fields["payload"]
                        ?: error("Missing payload field in stream entry ${entry.id}")
                    json.decodeFromString<ToolCallEvent>(payload)
                }) {
                    is SpiceResult.Success -> {
                        // Emit to local Flow
                        eventFlow.emit(decoded.value)

                        // Update last read ID (always advance even if decode fails later)
                        lastReadId = entry.id.toString()
                    }
                    is SpiceResult.Failure -> {
                        // Record error metrics
                        if (config.enableMetrics) {
                            mutex.withLock { errors++ }
                        }

                        // Log error
                        System.err.println(
                            "[RedisToolCallEventBus] Failed to decode event at ${entry.id}: " +
                            "${decoded.error.message} (type: ${decoded.error.code})"
                        )

                        // Advance offset to skip this malformed event
                        lastReadId = entry.id.toString()

                        // TODO: Add dead letter handler support
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            delay(pollInterval)
        }
    }

    /**
     * Close event bus and stop polling
     */
    suspend fun close() {
        pollingJob?.cancelAndJoin()
        scope.cancel()
    }

    companion object {
        /**
         * Get event type name
         */
        private fun ToolCallEvent.typeName(): String = when (this) {
            is ToolCallEvent.Emitted -> "EMITTED"
            is ToolCallEvent.Received -> "RECEIVED"
            is ToolCallEvent.Completed -> "COMPLETED"
            is ToolCallEvent.Failed -> "FAILED"
            is ToolCallEvent.Retrying -> "RETRYING"
            is ToolCallEvent.Cancelled -> "CANCELLED"
        }
    }
}
