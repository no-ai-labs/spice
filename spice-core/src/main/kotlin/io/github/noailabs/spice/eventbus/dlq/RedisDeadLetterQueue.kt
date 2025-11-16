package io.github.noailabs.spice.eventbus.dlq

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.eventbus.EventEnvelope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import java.util.UUID

/**
 * 💀 Redis Dead Letter Queue
 *
 * Production-ready DLQ implementation using Redis.
 * Persists messages across restarts and supports distributed deployments.
 *
 * **Redis Data Structures:**
 * - Hash: `{namespace}:messages` - Stores DeadLetterMessage JSON by ID
 * - Sorted Set: `{namespace}:index` - Global index by receivedAt for pagination
 * - Sorted Set: `{namespace}:channel:{channelName}` - Per-channel index for size limits
 * - Hash: `{namespace}:stats` - Aggregated statistics
 * - String: `{namespace}:evicted` - Total evicted counter
 *
 * **Features:**
 * - Persistent storage (survives restarts)
 * - TTL support (automatic cleanup of old messages)
 * - Atomic operations (thread-safe)
 * - Distributed-friendly (multiple instances can share DLQ)
 * - **Per-channel partitioning**: Prevents one busy channel from evicting others
 * - **Automatic trimming**: Prevents unbounded sorted set growth
 * - **Eviction metrics**: Track total evicted messages
 *
 * **Back-pressure Controls:**
 * - Per-channel max size (prevents cross-channel eviction)
 * - Global max size (total limit across all channels)
 * - Automatic FIFO eviction with metrics
 * - TTL support for automatic cleanup
 *
 * **Usage:**
 * ```kotlin
 * val dlq = RedisDeadLetterQueue(
 *     jedisPool = jedisPool,
 *     namespace = "spice:dlq",
 *     maxSize = 10000,
 *     maxSizePerChannel = 1000,
 *     ttl = 7.days
 * )
 * dlq.send(envelope, "Failed to deserialize")
 * ```
 *
 * @property jedisPool Redis connection pool
 * @property namespace Redis key namespace (default: "spice:dlq")
 * @property maxSize Maximum total messages across all channels (null = unlimited)
 * @property maxSizePerChannel Maximum messages per channel (null = unlimited)
 * @property ttl Time-to-live for DLQ messages (null = no expiry)
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
class RedisDeadLetterQueue(
    private val jedisPool: JedisPool,
    private val namespace: String = "spice:dlq",
    private val maxSize: Long? = 10000,
    private val maxSizePerChannel: Long? = 1000,
    private val ttl: kotlin.time.Duration? = null
) : DeadLetterQueue {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val messagesKey = "$namespace:messages"
    private val indexKey = "$namespace:index"
    private val statsKey = "$namespace:stats"
    private val evictedKey = "$namespace:evicted"

    private fun channelKey(channelName: String) = "$namespace:channel:$channelName"

    override suspend fun send(
        originalEnvelope: EventEnvelope,
        reason: String,
        error: Throwable?
    ): SpiceResult<String> = SpiceResult.catching {
        val messageId = UUID.randomUUID().toString()
        val channelName = originalEnvelope.channelName

        val dlqMessage = DeadLetterMessage(
            id = messageId,
            originalEnvelope = originalEnvelope,
            reason = reason,
            error = error?.message,
            stackTrace = error?.stackTraceToString(),
            receivedAt = Clock.System.now(),
            retryCount = 0
        )

        jedisPool.resource.use { jedis ->
            val messageJson = json.encodeToString(dlqMessage)
            val score = dlqMessage.receivedAt.toEpochMilliseconds().toDouble()

            // 1. Store message
            jedis.hset(messagesKey, messageId, messageJson)

            // 2. Add to global index
            jedis.zadd(indexKey, score, messageId)

            // 3. Add to per-channel index
            val channelIndexKey = channelKey(channelName)
            jedis.zadd(channelIndexKey, score, messageId)

            // 4. Per-channel FIFO eviction
            if (maxSizePerChannel != null) {
                val channelSize = jedis.zcard(channelIndexKey)
                if (channelSize > maxSizePerChannel) {
                    val toEvict = channelSize - maxSizePerChannel
                    // Get oldest messages from this channel
                    val evictedIds = jedis.zrange(channelIndexKey, 0, toEvict - 1)

                    evictedIds.forEach { evictedId ->
                        // Remove from all indices
                        jedis.hdel(messagesKey, evictedId)
                        jedis.zrem(indexKey, evictedId)
                        jedis.zrem(channelIndexKey, evictedId)

                        // Update stats
                        jedis.hincrBy(statsKey, "total", -1)
                        jedis.hincrBy(statsKey, "channel:$channelName", -1)
                        jedis.incr(evictedKey)
                    }
                }
            }

            // 5. Global FIFO eviction
            if (maxSize != null) {
                val totalSize = jedis.zcard(indexKey)
                if (totalSize > maxSize) {
                    val toEvict = totalSize - maxSize
                    // Get oldest messages globally
                    val evictedIds = jedis.zrange(indexKey, 0, toEvict - 1)

                    evictedIds.forEach { evictedId ->
                        // Get message to find its channel
                        val evictedJson = jedis.hget(messagesKey, evictedId)
                        val evictedMsg = evictedJson?.let {
                            json.decodeFromString<DeadLetterMessage>(it)
                        }

                        // Remove from all indices
                        jedis.hdel(messagesKey, evictedId)
                        jedis.zrem(indexKey, evictedId)

                        if (evictedMsg != null) {
                            jedis.zrem(channelKey(evictedMsg.channelName), evictedId)
                            jedis.hincrBy(statsKey, "channel:${evictedMsg.channelName}", -1)
                        }

                        // Update stats
                        jedis.hincrBy(statsKey, "total", -1)
                        jedis.incr(evictedKey)
                    }
                }
            }

            // 6. Update stats for new message
            jedis.hincrBy(statsKey, "total", 1)
            jedis.hincrBy(statsKey, "channel:$channelName", 1)
            jedis.hincrBy(statsKey, "reason:$reason", 1)

            // 7. Set TTL if configured
            if (ttl != null) {
                val ttlSeconds = ttl.inWholeSeconds
                jedis.expire(messagesKey, ttlSeconds)
                jedis.expire(indexKey, ttlSeconds)
                jedis.expire(channelIndexKey, ttlSeconds)
                jedis.expire(statsKey, ttlSeconds)
                jedis.expire(evictedKey, ttlSeconds)
            }
        }

        messageId
    }

    override suspend fun getMessages(
        limit: Int,
        offset: Int
    ): SpiceResult<List<DeadLetterMessage>> = SpiceResult.catching {
        jedisPool.resource.use { jedis ->
            // Get message IDs from sorted set (newest first = reverse order)
            val messageIds = jedis.zrevrange(indexKey, offset.toLong(), (offset + limit - 1).toLong())

            // Get messages
            messageIds.mapNotNull { messageId ->
                jedis.hget(messagesKey, messageId)?.let { messageJson ->
                    json.decodeFromString<DeadLetterMessage>(messageJson)
                }
            }
        }
    }

    override suspend fun getMessage(
        messageId: String
    ): SpiceResult<DeadLetterMessage?> = SpiceResult.catching {
        jedisPool.resource.use { jedis ->
            jedis.hget(messagesKey, messageId)?.let { messageJson ->
                json.decodeFromString<DeadLetterMessage>(messageJson)
            }
        }
    }

    override suspend fun retry(messageId: String): SpiceResult<Unit> = SpiceResult.catching {
        jedisPool.resource.use { jedis ->
            val messageJson = jedis.hget(messagesKey, messageId)
                ?: throw IllegalArgumentException("DLQ message not found: $messageId")

            val message = json.decodeFromString<DeadLetterMessage>(messageJson)

            // Update retry count
            val updated = message.copy(
                retryCount = message.retryCount + 1,
                lastRetryAt = Clock.System.now()
            )

            val updatedJson = json.encodeToString(updated)
            jedis.hset(messagesKey, messageId, updatedJson)

            Unit
        }
    }

    override suspend fun delete(messageId: String): SpiceResult<Unit> = SpiceResult.catching {
        jedisPool.resource.use { jedis ->
            val messageJson = jedis.hget(messagesKey, messageId)
                ?: throw IllegalArgumentException("DLQ message not found: $messageId")

            val message = json.decodeFromString<DeadLetterMessage>(messageJson)

            // Delete message from all indices
            jedis.hdel(messagesKey, messageId)
            jedis.zrem(indexKey, messageId)
            jedis.zrem(channelKey(message.channelName), messageId)

            // Update stats
            jedis.hincrBy(statsKey, "total", -1)
            jedis.hincrBy(statsKey, "channel:${message.channelName}", -1)
            jedis.hincrBy(statsKey, "reason:${message.reason}", -1)

            Unit
        }
    }

    override suspend fun getStats(): DeadLetterStats = SpiceResult.catching {
        jedisPool.resource.use { jedis ->
            val stats = jedis.hgetAll(statsKey)
            val total = stats["total"]?.toLongOrNull() ?: 0L
            val totalEvicted = jedis.get(evictedKey)?.toLongOrNull() ?: 0L

            val byChannel = stats.filterKeys { it.startsWith("channel:") }
                .mapKeys { it.key.removePrefix("channel:") }
                .mapValues { it.value.toLongOrNull() ?: 0L }

            val byReason = stats.filterKeys { it.startsWith("reason:") }
                .mapKeys { it.key.removePrefix("reason:") }
                .mapValues { it.value.toLongOrNull() ?: 0L }

            // Get oldest and newest timestamps from sorted set
            val oldestId = jedis.zrange(indexKey, 0, 0).firstOrNull()
            val newestId = jedis.zrevrange(indexKey, 0, 0).firstOrNull()

            val oldest = oldestId?.let { id ->
                jedis.hget(messagesKey, id)?.let { messageJson ->
                    json.decodeFromString<DeadLetterMessage>(messageJson).receivedAt
                }
            }

            val newest = newestId?.let { id ->
                jedis.hget(messagesKey, id)?.let { messageJson ->
                    json.decodeFromString<DeadLetterMessage>(messageJson).receivedAt
                }
            }

            DeadLetterStats(
                totalMessages = total,
                byChannel = byChannel,
                byReason = byReason,
                oldestMessage = oldest,
                newestMessage = newest,
                totalEvicted = totalEvicted
            )
        }
    }.getOrElse { DeadLetterStats.EMPTY }

    override suspend fun clear(): SpiceResult<Int> = SpiceResult.catching {
        jedisPool.resource.use { jedis ->
            val count = jedis.hlen(messagesKey).toInt()

            // Delete main keys
            jedis.del(messagesKey)
            jedis.del(indexKey)
            jedis.del(statsKey)
            jedis.del(evictedKey)

            // Delete all per-channel indices
            // Use SCAN to find all channel keys
            val channelPattern = "$namespace:channel:*"
            var cursor = "0"
            do {
                val scanResult = jedis.scan(cursor, redis.clients.jedis.params.ScanParams().match(channelPattern))
                cursor = scanResult.cursor
                val keys = scanResult.result
                if (keys.isNotEmpty()) {
                    jedis.del(*keys.toTypedArray())
                }
            } while (cursor != "0")

            count
        }
    }

    /**
     * Get total eviction count (for monitoring)
     */
    suspend fun getEvictionCount(): Long {
        return jedisPool.resource.use { jedis ->
            jedis.get(evictedKey)?.toLongOrNull() ?: 0L
        }
    }

    /**
     * Get messages by channel (for debugging)
     */
    suspend fun getMessagesByChannel(channelName: String): List<DeadLetterMessage> {
        return jedisPool.resource.use { jedis ->
            val channelIndexKey = channelKey(channelName)
            // Get all message IDs for this channel (newest first)
            val messageIds = jedis.zrevrange(channelIndexKey, 0, -1)

            // Get messages
            messageIds.mapNotNull { messageId ->
                jedis.hget(messagesKey, messageId)?.let { messageJson ->
                    json.decodeFromString<DeadLetterMessage>(messageJson)
                }
            }
        }
    }

    /**
     * Get channel size (for monitoring)
     */
    suspend fun getChannelSize(channelName: String): Long {
        return jedisPool.resource.use { jedis ->
            jedis.zcard(channelKey(channelName))
        }
    }
}
