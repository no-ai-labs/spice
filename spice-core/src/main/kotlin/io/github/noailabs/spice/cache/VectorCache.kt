package io.github.noailabs.spice.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.sqrt
import kotlin.time.Duration

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
}
