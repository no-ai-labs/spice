package io.github.noailabs.spice.eventbus.dlq

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.eventbus.EventEnvelope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import redis.clients.jedis.JedisPool
import java.util.UUID

private val logger = KotlinLogging.logger {}

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
 * @property onEviction Optional callback when messages are evicted (for monitoring)
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
class RedisDeadLetterQueue(
    private val jedisPool: JedisPool,
    private val namespace: String = "spice:dlq",
    private val maxSize: Long? = 10000,
    private val maxSizePerChannel: Long? = 1000,
    private val ttl: kotlin.time.Duration? = null,
    private val onEviction: ((channelName: String, evictedCount: Long) -> Unit)? = null
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

    /**
     * Lua script for atomic DLQ enqueue with trimming
     *
     * This script atomically:
     * 1. Adds message to hash and sorted sets
     * 2. Trims per-channel sorted set if needed
     * 3. Trims global sorted set if needed
     * 4. Updates stats
     * 5. Returns number of evicted messages
     *
     * KEYS[1] = messagesKey
     * KEYS[2] = indexKey
     * KEYS[3] = channelIndexKey
     * KEYS[4] = statsKey
     * KEYS[5] = evictedKey
     * ARGV[1] = messageId
     * ARGV[2] = messageJson
     * ARGV[3] = score (timestamp)
     * ARGV[4] = channelName
     * ARGV[5] = reason
     * ARGV[6] = maxSizePerChannel (or -1 if unlimited)
     * ARGV[7] = maxSize (or -1 if unlimited)
     */
    private val enqueueWithTrimScript = """
        local messagesKey = KEYS[1]
        local indexKey = KEYS[2]
        local channelIndexKey = KEYS[3]
        local statsKey = KEYS[4]
        local evictedKey = KEYS[5]

        local messageId = ARGV[1]
        local messageJson = ARGV[2]
        local score = tonumber(ARGV[3])
        local channelName = ARGV[4]
        local reason = ARGV[5]
        local maxSizePerChannel = tonumber(ARGV[6])
        local maxSize = tonumber(ARGV[7])

        local evictedCount = 0

        -- 1. Add message
        redis.call('HSET', messagesKey, messageId, messageJson)
        redis.call('ZADD', indexKey, score, messageId)
        redis.call('ZADD', channelIndexKey, score, messageId)

        -- 2. Per-channel trimming
        if maxSizePerChannel > 0 then
            local channelSize = redis.call('ZCARD', channelIndexKey)
            if channelSize > maxSizePerChannel then
                local toEvict = channelSize - maxSizePerChannel
                local evictedIds = redis.call('ZRANGE', channelIndexKey, 0, toEvict - 1)

                for _, evictedId in ipairs(evictedIds) do
                    redis.call('HDEL', messagesKey, evictedId)
                    redis.call('ZREM', indexKey, evictedId)
                    redis.call('ZREM', channelIndexKey, evictedId)
                    redis.call('HINCRBY', statsKey, 'total', -1)
                    redis.call('HINCRBY', statsKey, 'channel:' .. channelName, -1)
                    evictedCount = evictedCount + 1
                end
            end
        end

        -- 3. Global trimming
        if maxSize > 0 then
            local totalSize = redis.call('ZCARD', indexKey)
            if totalSize > maxSize then
                local toEvict = totalSize - maxSize
                local evictedIds = redis.call('ZRANGE', indexKey, 0, toEvict - 1)

                for _, evictedId in ipairs(evictedIds) do
                    -- Get message to find channel
                    local evictedJson = redis.call('HGET', messagesKey, evictedId)
                    if evictedJson then
                        -- Parse channel from JSON (simple pattern matching)
                        local evictedChannel = string.match(evictedJson, '"channelName":"([^"]+)"')
                        if evictedChannel then
                            redis.call('ZREM', '${namespace}:channel:' .. evictedChannel, evictedId)
                            redis.call('HINCRBY', statsKey, 'channel:' .. evictedChannel, -1)
                        end
                    end

                    redis.call('HDEL', messagesKey, evictedId)
                    redis.call('ZREM', indexKey, evictedId)
                    redis.call('HINCRBY', statsKey, 'total', -1)
                    evictedCount = evictedCount + 1
                end
            end
        end

        -- 4. Update stats for new message
        redis.call('HINCRBY', statsKey, 'total', 1)
        redis.call('HINCRBY', statsKey, 'channel:' .. channelName, 1)
        redis.call('HINCRBY', statsKey, 'reason:' .. reason, 1)

        -- 5. Update evicted counter
        if evictedCount > 0 then
            redis.call('INCRBY', evictedKey, evictedCount)
        end

        return evictedCount
    """.trimIndent()

    // Cache SHA1 hash of script for EVALSHA
    private var scriptSha: String? = null

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

        // Log DLQ write with structured context
        logger.warn {
            "DLQ: Message sent to dead letter queue [id=$messageId, channel=$channelName, reason=$reason, envelopeId=${originalEnvelope.id}]"
        }

        if (error != null) {
            logger.debug(error) {
                "DLQ: Error details for message $messageId in channel $channelName"
            }
        }

        jedisPool.resource.use { jedis ->
            val messageJson = json.encodeToString(dlqMessage)
            val score = dlqMessage.receivedAt.toEpochMilliseconds().toDouble()
            val channelIndexKey = channelKey(channelName)

            // Execute Lua script for atomic enqueue + trim
            val keys = arrayOf(
                messagesKey,
                indexKey,
                channelIndexKey,
                statsKey,
                evictedKey
            )

            val args = arrayOf(
                messageId,
                messageJson,
                score.toString(),
                channelName,
                reason,
                (maxSizePerChannel ?: -1).toString(),
                (maxSize ?: -1).toString()
            )

            // Try EVALSHA first (faster if script already loaded)
            val evictedCount = try {
                if (scriptSha != null) {
                    jedis.evalsha(scriptSha, keys.size, *keys, *args) as Long
                } else {
                    // First time: load script and cache SHA
                    val result = jedis.eval(enqueueWithTrimScript, keys.size, *keys, *args) as Long
                    scriptSha = jedis.scriptLoad(enqueueWithTrimScript)
                    result
                }
            } catch (e: redis.clients.jedis.exceptions.JedisNoScriptException) {
                // Script not in cache, reload
                val result = jedis.eval(enqueueWithTrimScript, keys.size, *keys, *args) as Long
                scriptSha = jedis.scriptLoad(enqueueWithTrimScript)
                result
            }

            // Set TTL if configured (on all keys)
            if (ttl != null) {
                val ttlSeconds = ttl.inWholeSeconds
                jedis.expire(messagesKey, ttlSeconds)
                jedis.expire(indexKey, ttlSeconds)
                jedis.expire(channelIndexKey, ttlSeconds)
                jedis.expire(statsKey, ttlSeconds)
                jedis.expire(evictedKey, ttlSeconds)
            }

            // Log and report evictions
            if (evictedCount > 0) {
                logger.warn {
                    "DLQ: Evicted $evictedCount message(s) from channel $channelName [maxSize=$maxSize, maxSizePerChannel=$maxSizePerChannel]"
                }

                // Invoke callback for monitoring integration
                onEviction?.invoke(channelName, evictedCount)

                // Log when evictions are excessive
                if (evictedCount > 100) {
                    logger.error {
                        "DLQ: Excessive evictions detected! $evictedCount messages evicted from $channelName. " +
                        "Consider increasing maxSize ($maxSize) or maxSizePerChannel ($maxSizePerChannel)."
                    }
                }
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

            logger.info {
                "DLQ: Retrying message [id=$messageId, channel=${message.channelName}, retryCount=${message.retryCount + 1}]"
            }

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

            logger.info {
                "DLQ: Deleting message [id=$messageId, channel=${message.channelName}, reason=${message.reason}]"
            }

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

            val result = DeadLetterStats(
                totalMessages = total,
                byChannel = byChannel,
                byReason = byReason,
                oldestMessage = oldest,
                newestMessage = newest,
                totalEvicted = totalEvicted
            )

            // Log summary for monitoring
            logger.debug {
                "DLQ Stats: total=$total, evicted=$totalEvicted, channels=${byChannel.size}, reasons=${byReason.size}"
            }

            result
        }
    }.getOrElse { DeadLetterStats.EMPTY }

    override suspend fun clear(): SpiceResult<Int> = SpiceResult.catching {
        jedisPool.resource.use { jedis ->
            val count = jedis.hlen(messagesKey).toInt()

            logger.warn {
                "DLQ: Clearing all messages from dead letter queue [count=$count, namespace=$namespace]"
            }

            // Delete main keys
            jedis.del(messagesKey)
            jedis.del(indexKey)
            jedis.del(statsKey)
            jedis.del(evictedKey)

            // Delete all per-channel indices
            // Use SCAN to find all channel keys
            val channelPattern = "$namespace:channel:*"
            var cursor = "0"
            var channelKeysDeleted = 0
            do {
                val scanResult = jedis.scan(cursor, redis.clients.jedis.params.ScanParams().match(channelPattern))
                cursor = scanResult.cursor
                val keys = scanResult.result
                if (keys.isNotEmpty()) {
                    jedis.del(*keys.toTypedArray())
                    channelKeysDeleted += keys.size
                }
            } while (cursor != "0")

            logger.info {
                "DLQ: Cleared $count messages and $channelKeysDeleted channel indices"
            }

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
