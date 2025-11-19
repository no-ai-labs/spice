package io.github.noailabs.spice.event

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.eventbus.EventEnvelope
import io.github.noailabs.spice.eventbus.EventMetadata
import io.github.noailabs.spice.eventbus.dlq.DeadLetterQueue
import io.github.noailabs.spice.eventbus.dlq.InMemoryDeadLetterQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.XAddParams
import redis.clients.jedis.params.XReadParams
import redis.clients.jedis.params.XReadGroupParams
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.exceptions.JedisDataException
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
 * @property deadLetterQueue Dead letter queue for failed events (default: InMemoryDeadLetterQueue)
 * @property onDLQWrite Optional callback when event is written to DLQ
 *
 * @since 1.0.0
 */
class RedisToolCallEventBus(
    private val jedisPool: JedisPool,
    private val streamKey: String = "spice:toolcall:events",
    private val consumerGroup: String? = null,
    private val consumerName: String = "consumer-${java.util.UUID.randomUUID()}",
    private val startFrom: String = "$",
    config: EventBusConfig = EventBusConfig.DEFAULT,
    private val pollInterval: Duration = 1.seconds,
    private val deadLetterQueue: DeadLetterQueue = InMemoryDeadLetterQueue(),
    private val onDLQWrite: ((String, String) -> Unit)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "_type"
    }
) : AbstractToolCallEventBus(config) {

    // Background polling job
    private var pollingJob: Job? = null
    private var lastReadId: String? = null
    private var useConsumerGroup: Boolean = consumerGroup != null

    // Additional metrics
    private var errors = 0L
    private var dlqCount = 0L

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

    /**
     * Publish event to Redis Stream.
     */
    override suspend fun doPublish(event: ToolCallEvent) {
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
    }

    /**
     * Start background polling from Redis Stream.
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
     * Poll events from Redis Stream and emit to local Flow.
     */
    private suspend fun pollEvents() {
        if (useConsumerGroup && consumerGroup != null) {
            pollEventsWithConsumerGroup()
        } else {
            pollEventsWithoutConsumerGroup()
        }
    }

    /**
     * Poll using consumer group (persistent offset).
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
                        emitToSubscribers(decoded.value)

                        // Acknowledge message
                        jedisPool.resource.use { jedis ->
                            jedis.xack(streamKey, consumerGroup, entry.id)
                        }
                    }
                    is SpiceResult.Failure -> {
                        handleDecodingError(entry, decoded.error)

                        // ACK the message to prevent infinite retries (already in DLQ)
                        jedisPool.resource.use { jedis ->
                            jedis.xack(streamKey, consumerGroup, entry.id)
                        }
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            delay(pollInterval)
        }
    }

    /**
     * Poll without consumer group (ephemeral offset).
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
                        emitToSubscribers(decoded.value)

                        // Update last read ID
                        lastReadId = entry.id.toString()
                    }
                    is SpiceResult.Failure -> {
                        handleDecodingError(entry, decoded.error)

                        // Advance offset to skip this malformed event
                        lastReadId = entry.id.toString()
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            delay(pollInterval)
        }
    }

    /**
     * Handle decoding errors by sending to DLQ.
     */
    private suspend fun handleDecodingError(
        entry: redis.clients.jedis.resps.StreamEntry,
        error: io.github.noailabs.spice.error.SpiceError
    ) {
        // Record error metrics
        if (config.enableMetrics) {
            mutex.withLock {
                errors++
                dlqCount++
            }
        }

        // Create EventEnvelope for DLQ from Redis stream entry
        val envelope = EventEnvelope(
            channelName = "redis.toolcall.events",
            eventType = "ToolCallEvent",
            schemaVersion = "1.0.0",
            payload = entry.fields["payload"] ?: "",
            metadata = EventMetadata(
                custom = mapOf(
                    "redis.streamKey" to streamKey,
                    "redis.entryId" to entry.id.toString(),
                    "redis.consumerGroup" to (consumerGroup ?: "none"),
                    "event.type" to (entry.fields["type"] ?: "unknown"),
                    "event.runId" to (entry.fields["runId"] ?: ""),
                    "toolCallId" to (entry.fields["toolCallId"] ?: "")
                )
            ),
            timestamp = Clock.System.now(),
            correlationId = entry.fields["toolCallId"]
        )

        // Send to DLQ
        val reason = "Deserialization failed: ${error.message} (type: ${error.code})"
        scope.launch {
            deadLetterQueue.send(envelope, reason, error.cause)
            onDLQWrite?.invoke(entry.fields["toolCallId"] ?: "unknown", reason)
        }

        // Log error
        System.err.println(
            "[RedisToolCallEventBus] Failed to decode event at ${entry.id}: " +
            "$reason -> Sent to DLQ"
        )
    }

    /**
     * Close event bus and stop polling.
     */
    suspend fun close() {
        pollingJob?.cancelAndJoin()
        scope.cancel()
    }

    companion object {
        /**
         * Get event type name.
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
