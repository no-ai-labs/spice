package io.github.noailabs.spice.idempotency

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.ScanParams
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

/**
 * Redis-backed IdempotencyStore implementation using simple key/value semantics.
 */
class RedisIdempotencyStore(
    private val jedisPool: JedisPool,
    private val namespace: String = "spice:idempotency",
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : IdempotencyStore {

    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val evictions = AtomicLong(0)

    private fun namespaced(key: String) = "$namespace:$key"

    override suspend fun get(key: String): SpiceMessage? = withContext(Dispatchers.IO) {
        jedisPool.resource.use { jedis ->
            val payload = jedis.get(namespaced(key))
            if (payload != null) {
                hits.incrementAndGet()
                json.decodeFromString(SpiceMessage.serializer(), payload)
            } else {
                misses.incrementAndGet()
                null
            }
        }
    }

    override suspend fun save(key: String, message: SpiceMessage, ttl: Duration): SpiceResult<Unit> =
        withContext(Dispatchers.IO) {
            SpiceResult.catching {
                jedisPool.resource.use { jedis ->
                    jedis.setex(
                        namespaced(key),
                        ttl.inWholeSeconds.toInt().coerceAtLeast(1),
                        json.encodeToString(SpiceMessage.serializer(), message)
                    )
                }
            }
        }

    override suspend fun delete(key: String): SpiceResult<Unit> = withContext(Dispatchers.IO) {
        SpiceResult.catching {
            jedisPool.resource.use { jedis ->
                jedis.del(namespaced(key))
            }
        }
    }

    override suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        jedisPool.resource.use { jedis ->
            jedis.exists(namespaced(key))
        }
    }

    override suspend fun clear(): SpiceResult<Unit> = withContext(Dispatchers.IO) {
        SpiceResult.catching {
            jedisPool.resource.use { jedis ->
                scanKeys(jedis).forEach { jedis.del(it) }
            }
            hits.set(0)
            misses.set(0)
            evictions.set(0)
        }
    }

    override suspend fun getStats(): IdempotencyStats = withContext(Dispatchers.IO) {
        val keys = jedisPool.resource.use { jedis ->
            scanKeys(jedis)
        }
        IdempotencyStats(
            hits = hits.get(),
            misses = misses.get(),
            evictions = evictions.get(),
            totalEntries = keys.size.toLong(),
            memoryUsageBytes = 0L // Redis tracks this separately; unknown here
        )
    }

    private fun scanKeys(jedis: redis.clients.jedis.Jedis): List<String> {
        val params = ScanParams().match("$namespace:*").count(100)
        val keys = mutableListOf<String>()
        var cursor = ScanParams.SCAN_POINTER_START
        do {
            val result = jedis.scan(cursor, params)
            keys.addAll(result.result)
            cursor = result.cursor
        } while (cursor != ScanParams.SCAN_POINTER_START)
        return keys
    }
}
