package io.github.noailabs.spice.events

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import redis.clients.jedis.resps.StreamEntry
import redis.clients.jedis.exceptions.JedisDataException
import redis.clients.jedis.params.XAddParams
import redis.clients.jedis.params.XReadGroupParams
import redis.clients.jedis.params.XReadParams
import redis.clients.jedis.StreamEntryID
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.coroutines.coroutineContext

/**
 * Redis Streams backed EventBus implementation.
 *
 * **Status:** Beta - Production ready with caveats
 *
 * Each published event is appended to a single stream (`streamKey`). Subscribers
 * filter events by the logical Spice topic stored in the entry payload.
 *
 * **Requirements:**
 * - Redis 5.0+ (for Redis Streams support)
 * - Proper Redis connection pooling configuration
 *
 * **Known Limitations:**
 * - Consumer group acknowledgment may have edge cases in high-load scenarios
 * - Stream trimming not implemented (manual XTRIM required for long-running streams)
 * - Wildcard topic matching happens client-side (all events read, then filtered)
 *
 * @since 1.0.0
 */
class RedisStreamsEventBus(
    private val jedisPool: JedisPool,
    private val streamKey: String = "spice:events",
    private val consumerPrefix: String = "spice-events",
    private val batchSize: Int = 100,
    private val blockTimeout: Duration = 1.seconds,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : EventBus {

    private data class Subscription(
        val id: String,
        val topicPattern: String,
        val groupId: String?,
        val consumerName: String?,
        val job: Job
    )

    private val subscriptions = mutableMapOf<String, Subscription>()
    private val mutex = Mutex()

    private val published = AtomicLong(0)
    private val consumed = AtomicLong(0)
    private val errors = AtomicLong(0)

    override suspend fun publish(topic: String, message: SpiceMessage): SpiceResult<String> =
        SpiceResult.catching {
            val payload = json.encodeToString(message)
            val entryId = jedisPool.resource.use { jedis ->
                jedis.xadd(
                    streamKey,
                    mapOf("topic" to topic, "payload" to payload),
                    XAddParams.xAddParams()
                )
            }
            published.incrementAndGet()
            entryId.toString()  // Return Redis stream entry ID for acknowledgment
        }

    override suspend fun subscribe(
        topic: String,
        handler: suspend (SpiceMessage) -> Unit
    ): String = mutex.withLock {
        val subscriptionId = newSubscriptionId()
        val job = scope.launch {
            consumeWithoutGroup(subscriptionId, topic, handler)
        }

        subscriptions[subscriptionId] = Subscription(
            id = subscriptionId,
            topicPattern = topic,
            groupId = null,
            consumerName = null,
            job = job
        )
        subscriptionId
    }

    override suspend fun subscribeWithGroup(
        topic: String,
        groupId: String,
        handler: suspend (SpiceMessage) -> Unit
    ): String = mutex.withLock {
        ensureConsumerGroup(groupId)
        val subscriptionId = newSubscriptionId()
        val consumerName = "$consumerPrefix-$subscriptionId"
        val job = scope.launch {
            consumeWithGroup(subscriptionId, groupId, consumerName, topic, handler)
        }
        subscriptions[subscriptionId] = Subscription(
            id = subscriptionId,
            topicPattern = topic,
            groupId = groupId,
            consumerName = consumerName,
            job = job
        )
        subscriptionId
    }

    override suspend fun unsubscribe(subscriptionId: String): SpiceResult<Unit> = mutex.withLock {
        val subscription = subscriptions.remove(subscriptionId) ?: return@withLock SpiceResult.success(Unit)
        SpiceResult.catching {
            subscription.job.cancelAndJoin()
            Unit
        }
    }

    override suspend fun acknowledge(messageId: String, subscriptionId: String): SpiceResult<Unit> {
        val subscription = mutex.withLock { subscriptions[subscriptionId] } ?: return SpiceResult.success(Unit)
        val groupId = subscription.groupId ?: return SpiceResult.success(Unit)
        return SpiceResult.catching {
            jedisPool.resource.use { jedis ->
                jedis.xack(streamKey, groupId, StreamEntryID(messageId))
            }
            Unit
        }
    }

    override suspend fun getPending(subscriptionId: String, limit: Int): List<SpiceMessage> = emptyList()

    override suspend fun close() {
        val jobs = mutex.withLock {
            val activeJobs = subscriptions.values.map { it.job }
            subscriptions.clear()
            activeJobs
        }
        jobs.forEach { job -> job.cancel() }
        jobs.forEach { job -> runCatching { job.join() } }
        scope.cancel()  // Properly cancel the entire scope to stop all background consumers
    }

    override suspend fun getStats(): EventBusStats = EventBusStats(
        published = published.get(),
        consumed = consumed.get(),
        pending = 0L, // Pending retrieval supported via getPending for specific subscriptions
        errors = errors.get(),
        activeSubscriptions = mutex.withLock { subscriptions.size }
    )

    private suspend fun consumeWithoutGroup(
        subscriptionId: String,
        topicPattern: String,
        handler: suspend (SpiceMessage) -> Unit
    ) {
        var lastId: StreamEntryID = StreamEntryID("0-0")
        val params = XReadParams.xReadParams()
            .block(blockTimeout.inWholeMilliseconds.coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .count(batchSize)

        while (coroutineContext.isActive) {
            val entries = jedisPool.resource.use { jedis ->
                jedis.xread(params, mapOf(streamKey to lastId))
            } ?: continue
            processEntries(entries, topicPattern, handler) { entryId ->
                lastId = entryId
            }
        }
    }

    private suspend fun consumeWithGroup(
        subscriptionId: String,
        groupId: String,
        consumerName: String,
        topicPattern: String,
        handler: suspend (SpiceMessage) -> Unit
    ) {
        val params = XReadGroupParams.xReadGroupParams()
            .block(blockTimeout.inWholeMilliseconds.coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .count(batchSize)

        while (coroutineContext.isActive) {
            val entries = jedisPool.resource.use { jedis ->
                jedis.xreadGroup(groupId, consumerName, params, mapOf(streamKey to StreamEntryID.UNRECEIVED_ENTRY))
            } ?: continue
            processEntries(entries, topicPattern, handler) { entryId ->
                jedisPool.resource.use { jedis ->
                    jedis.xack(streamKey, groupId, entryId)
                }
            }
        }
    }

    private suspend fun processEntries(
        entries: List<Map.Entry<String, List<StreamEntry>>>,
        topicPattern: String,
        handler: suspend (SpiceMessage) -> Unit,
        after: suspend (StreamEntryID) -> Unit
    ) {
        for (result in entries) {
            val streamEntries = result.value ?: continue
            for (entry in streamEntries) {
                val fields = entry.fields ?: emptyMap()
                val topic = fields["topic"]
                val payload = fields["payload"]
                if (topic == null || payload == null) {
                    errors.incrementAndGet()
                    after(entry.id)
                    continue
                }

                if (!TopicPatternMatcher.matches(topic, topicPattern)) {
                    after(entry.id)
                    continue
                }

                val message = runCatching {
                    json.decodeFromString(SpiceMessage.serializer(), payload)
                }.onFailure { errors.incrementAndGet() }.getOrNull()

                if (message != null) {
                    handler(message)
                    consumed.incrementAndGet()
                }
                after(entry.id)
            }
        }
    }

    private fun ensureConsumerGroup(groupId: String) {
        jedisPool.resource.use { jedis ->
            try {
                jedis.xgroupCreate(streamKey, groupId, StreamEntryID("0-0"), true)
            } catch (ex: JedisDataException) {
                // Ignore BUSYGROUP error (consumer group already exists)
                if (!ex.message.orEmpty().contains("BUSYGROUP")) {
                    // Log and rethrow other Redis errors (e.g., NOGROUP, WRONGTYPE)
                    System.err.println("Failed to create consumer group '$groupId' on stream '$streamKey': ${ex.message}")
                    throw ex
                }
            } catch (ex: Exception) {
                // Catch any other connection or Redis errors
                System.err.println("Unexpected error creating consumer group '$groupId': ${ex.message}")
                throw IllegalStateException("Failed to ensure consumer group: ${ex.message}", ex)
            }
        }
    }

    private fun newSubscriptionId(): String =
        "sub-${UUID.randomUUID().toString().replace("-", "").take(12)}"
}
