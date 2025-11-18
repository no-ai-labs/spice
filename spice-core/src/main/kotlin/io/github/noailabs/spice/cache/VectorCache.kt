package io.github.noailabs.spice.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.ScanParams
import kotlin.math.sqrt
import kotlin.time.Duration

/**
 * Calculate cosine similarity between two vectors
 */
private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
    if (a.size != b.size || a.isEmpty()) return 0.0
    var dot = 0.0
    var magA = 0.0
    var magB = 0.0

    for (i in a.indices) {
        dot += a[i] * b[i]
        magA += a[i] * a[i]
        magB += b[i] * b[i]
    }

    val denominator = sqrt(magA) * sqrt(magB)
    return if (denominator == 0.0) 0.0 else dot / denominator
}

data class VectorMatch(
    val key: String,
    val score: Double,
    val metadata: Map<String, Any> = emptyMap()
)

interface VectorCache {
    suspend fun put(key: String, vector: FloatArray, metadata: Map<String, Any> = emptyMap(), ttl: Duration)
    suspend fun query(vector: FloatArray, topK: Int = 5): List<VectorMatch>
    suspend fun invalidate(key: String)
}

class InMemoryVectorCache : VectorCache {
    private data class Entry(
        val vector: FloatArray,
        val metadata: Map<String, Any>,
        val expiresAt: Instant
    )

    private val entries = mutableMapOf<String, Entry>()
    private val mutex = Mutex()

    override suspend fun put(key: String, vector: FloatArray, metadata: Map<String, Any>, ttl: Duration) {
        mutex.withLock {
            entries[key] = Entry(vector, metadata, Clock.System.now() + ttl)
        }
    }

    override suspend fun query(vector: FloatArray, topK: Int): List<VectorMatch> = mutex.withLock {
        val now = Clock.System.now()

        // Clean up expired entries to prevent memory leak
        val expired = entries.filter { it.value.expiresAt <= now }.keys
        expired.forEach { entries.remove(it) }

        val scores = entries
            .filterValues { it.expiresAt > now }
            .map { (key, entry) ->
                VectorMatch(
                    key = key,
                    score = cosineSimilarity(vector, entry.vector),
                    metadata = entry.metadata
                )
            }
            .sortedByDescending { it.score }
            .take(topK)

        scores
    }

    override suspend fun invalidate(key: String) {
        mutex.withLock { entries.remove(key) }
    }
}

/**
 * Redis-backed vector cache using cosine similarity computed client-side.
 */
class RedisVectorCache(
    private val jedisPool: JedisPool,
    private val namespace: String = "spice:vector",
    private val json: Json = Json { ignoreUnknownKeys = true }
) : VectorCache {

    @Serializable
    private data class VectorRecord(
        val vector: List<Float>,
        val metadata: Map<String, String>,
        val expiresAt: Long
    )

    private fun key(id: String) = "$namespace:$id"

    override suspend fun put(key: String, vector: FloatArray, metadata: Map<String, Any>, ttl: Duration) {
        val record = VectorRecord(
            vector = vector.toList(),
            metadata = metadata.mapValues { it.value?.toString() ?: "" },
            expiresAt = Clock.System.now().toEpochMilliseconds() + ttl.inWholeMilliseconds
        )
        jedisPool.resource.use { jedis ->
            jedis.set(this.key(key), json.encodeToString(record))
            jedis.pexpire(this.key(key), ttl.inWholeMilliseconds)
        }
    }

    override suspend fun query(vector: FloatArray, topK: Int): List<VectorMatch> {
        val now = Clock.System.now().toEpochMilliseconds()
        return jedisPool.resource.use { jedis ->
            val params = ScanParams().match("$namespace:*").count(200)
            var cursor = ScanParams.SCAN_POINTER_START
            val matches = mutableListOf<VectorMatch>()
            do {
                val result = jedis.scan(cursor, params)
                result.result.forEach { redisKey ->
                    val payload = jedis.get(redisKey) ?: return@forEach
                    val record = runCatching { json.decodeFromString<VectorRecord>(payload) }.getOrNull()
                        ?: return@forEach
                    if (record.expiresAt <= now) {
                        jedis.del(redisKey)
                        return@forEach
                    }
                    val score = cosineSimilarity(vector, record.vector.toFloatArray())
                    matches += VectorMatch(
                        key = redisKey.removePrefix("$namespace:"),
                        score = score,
                        metadata = record.metadata
                    )
                }
                cursor = result.cursor
            } while (cursor != ScanParams.SCAN_POINTER_START)
            matches.sortedByDescending { it.score }.take(topK)
        }
    }

    override suspend fun invalidate(key: String) {
        jedisPool.resource.use { jedis ->
            jedis.del(this.key(key))
        }
    }
}
