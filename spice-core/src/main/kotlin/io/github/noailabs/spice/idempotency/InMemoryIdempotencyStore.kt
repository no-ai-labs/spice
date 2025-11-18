package io.github.noailabs.spice.idempotency

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * 💾 In-Memory Idempotency Store
 *
 * Fast, ephemeral implementation of IdempotencyStore using concurrent HashMap.
 * Suitable for testing, development, and single-instance deployments.
 *
 * **Characteristics:**
 * - **Performance:** O(1) get/set operations
 * - **Persistence:** None (data lost on restart)
 * - **Scalability:** Single-instance only (no distributed cache)
 * - **Thread Safety:** Yes (using Mutex)
 * - **Memory:** Bounded by maxEntries parameter
 *
 * **Use Cases:**
 * - Unit tests and integration tests
 * - Development environment
 * - Single-instance production (non-distributed)
 * - Prototype and POC
 *
 * **Limitations:**
 * - Data lost on process restart
 * - Not suitable for multi-instance deployments
 * - Memory-bound (not disk-backed)
 *
 * **Example Usage:**
 * ```kotlin
 * // Simple usage
 * val store = InMemoryIdempotencyStore()
 * store.save("key1", message, ttl = Duration.hours(1))
 * val cached = store.get("key1")
 *
 * // With configuration
 * val store = InMemoryIdempotencyStore(
 *     maxEntries = 10000,
 *     enableStats = true
 * )
 * ```
 *
 * **Eviction Policy:**
 * - TTL-based: Entries expire after specified duration
 * - Size-based: LRU eviction when maxEntries exceeded
 *
 * @param maxEntries Maximum number of cached entries (default: 1000)
 * @param enableStats Enable hit/miss statistics tracking (default: true)
 *
 * @author Spice Framework
 * @since 1.0.0
 */
class InMemoryIdempotencyStore(
    private val maxEntries: Int = 1000,
    private val enableStats: Boolean = true
) : IdempotencyStore {

    /**
     * Cache entry with TTL
     */
    private data class CacheEntry(
        val message: SpiceMessage,
        val expiresAt: Instant
    ) {
        fun isExpired(now: Instant = Clock.System.now()): Boolean {
            return now >= expiresAt
        }
    }

    // Thread-safe cache storage
    private val cache = mutableMapOf<String, CacheEntry>()
    private val mutex = Mutex()

    // Statistics tracking
    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L

    /**
     * Retrieve cached message by key
     * Returns null if key doesn't exist or entry has expired
     */
    override suspend fun get(key: String): SpiceMessage? = mutex.withLock {
        val entry = cache[key]

        if (entry == null) {
            if (enableStats) misses++
            return null
        }

        // Check if expired
        if (entry.isExpired()) {
            cache.remove(key)
            if (enableStats) {
                misses++
                evictions++
            }
            return null
        }

        // Cache hit
        if (enableStats) hits++
        return entry.message
    }

    /**
     * Store message with TTL
     * Evicts oldest entry if maxEntries exceeded (LRU)
     */
    override suspend fun save(key: String, message: SpiceMessage, ttl: Duration): SpiceResult<Unit> = mutex.withLock {
        SpiceResult.catching {
            // Check size limit
            if (cache.size >= maxEntries && !cache.containsKey(key)) {
                // Evict oldest entry (LRU)
                val oldestKey = cache.entries
                    .minByOrNull { it.value.expiresAt }
                    ?.key

                if (oldestKey != null) {
                    cache.remove(oldestKey)
                    if (enableStats) evictions++
                }
            }

            // Calculate expiration time
            val now = Clock.System.now()
            val expiresAt = now + ttl

            // Store entry
            cache[key] = CacheEntry(
                message = message,
                expiresAt = expiresAt
            )
        }
    }

    /**
     * Delete cached message
     */
    override suspend fun delete(key: String): SpiceResult<Unit> = mutex.withLock {
        SpiceResult.catching {
            cache.remove(key)
            Unit
        }
    }

    /**
     * Check if key exists and not expired
     */
    override suspend fun exists(key: String): Boolean = mutex.withLock {
        val entry = cache[key] ?: return false
        if (entry.isExpired()) {
            cache.remove(key)
            if (enableStats) evictions++
            return false
        }
        return true
    }

    /**
     * Clear all cached entries
     */
    override suspend fun clear(): SpiceResult<Unit> = mutex.withLock {
        SpiceResult.catching {
            cache.clear()
            if (enableStats) {
                hits = 0
                misses = 0
                evictions = 0
            }
        }
    }

    /**
     * Get cache statistics
     */
    override suspend fun getStats(): IdempotencyStats = mutex.withLock {
        // Clean up expired entries first
        cleanupExpired()

        // Calculate memory usage (rough estimate)
        val avgMessageSize = 500L  // bytes per message (estimate)
        val memoryUsage = cache.size * avgMessageSize

        return IdempotencyStats(
            hits = hits,
            misses = misses,
            evictions = evictions,
            totalEntries = cache.size.toLong(),
            memoryUsageBytes = memoryUsage
        )
    }

    /**
     * Remove all expired entries
     * Internal cleanup method
     */
    private fun cleanupExpired() {
        val now = Clock.System.now()
        val expiredKeys = cache.entries
            .filter { it.value.isExpired(now) }
            .map { it.key }

        expiredKeys.forEach { key ->
            cache.remove(key)
            if (enableStats) evictions++
        }
    }

    /**
     * Get current cache size
     * @return Number of entries in cache
     */
    fun size(): Int = cache.size

    /**
     * Get max capacity
     * @return Maximum number of entries
     */
    fun capacity(): Int = maxEntries

    /**
     * Check if cache is full
     * @return true if cache size >= maxEntries
     */
    fun isFull(): Boolean = cache.size >= maxEntries

    /**
     * Get all cached keys (for debugging)
     * @return Set of all keys in cache
     */
    suspend fun keys(): Set<String> = mutex.withLock {
        cleanupExpired()
        return cache.keys.toSet()
    }

    /**
     * Force eviction of expired entries
     * Useful for manual cleanup
     * @return Number of entries evicted
     */
    suspend fun evictExpired(): Int = mutex.withLock {
        val before = cache.size
        cleanupExpired()
        val evicted = before - cache.size
        return evicted
    }
}
