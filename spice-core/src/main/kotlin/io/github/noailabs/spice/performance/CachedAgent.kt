package io.github.noailabs.spice.performance

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.observability.SpiceMetrics
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 💾 CachedAgent - Performance optimization through response caching
 *
 * Wraps any agent with intelligent caching to:
 * - Reduce LLM API costs by reusing responses
 * - Improve response time (instant cache hits)
 * - Track cache hit rates and efficiency
 *
 * Features:
 * - TTL-based expiration
 * - LRU eviction when cache is full
 * - Cache bypass option
 * - Comprehensive metrics
 *
 * Usage:
 * ```kotlin
 * val llmAgent = buildClaudeAgent { ... }
 * val cachedAgent = CachedAgent(
 *     delegate = llmAgent,
 *     cacheConfig = CacheConfig(maxSize = 500, ttlSeconds = 1800)
 * )
 *
 * // Or use extension function
 * val cached = llmAgent.cached()
 * ```
 */
class CachedAgent(
    private val delegate: Agent,
    private val cacheConfig: CacheConfig = CacheConfig()
) : Agent by delegate {

    private val cache = ConcurrentHashMap<CacheKey, CachedEntry>()
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val md = MessageDigest.getInstance("SHA-256")

    /**
     * Cache configuration
     */
    data class CacheConfig(
        val maxSize: Int = 1000,              // Maximum cache entries
        val ttlSeconds: Long = 3600,          // Time-to-live: 1 hour
        val enableMetrics: Boolean = true,     // Collect metrics
        val respectBypass: Boolean = true      // Honor bypass_cache flag
    )

    /**
     * Cache key for lookups
     */
    data class CacheKey(
        val contentHash: String,
        val contextHash: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CacheKey) return false
            return contentHash == other.contentHash && contextHash == other.contextHash
        }

        override fun hashCode(): Int {
            var result = contentHash.hashCode()
            result = 31 * result + contextHash.hashCode()
            return result
        }
    }

    /**
     * Cached entry with metadata
     */
    data class CachedEntry(
        val result: SpiceResult<Comm>,
        val timestamp: Long,
        val hitCount: AtomicLong = AtomicLong(0),
        val lastAccessed: AtomicLong = AtomicLong(System.currentTimeMillis())
    ) {
        fun isExpired(ttlMs: Long): Boolean {
            return System.currentTimeMillis() - timestamp > ttlMs
        }

        fun recordHit() {
            hitCount.incrementAndGet()
            lastAccessed.set(System.currentTimeMillis())
        }
    }

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Check bypass flag
        if (cacheConfig.respectBypass && comm.data["bypass_cache"] == "true") {
            return delegate.processComm(comm)
        }

        // Generate cache key
        val cacheKey = generateCacheKey(comm)

        // Check cache
        val cachedResult = checkCache(cacheKey)
        if (cachedResult != null) {
            cacheHits.incrementAndGet()
            cachedResult.recordHit()

            if (cacheConfig.enableMetrics) {
                recordCacheMetrics(hit = true)
            }

            return cachedResult.result
        }

        // Cache miss - execute delegate
        cacheMisses.incrementAndGet()

        if (cacheConfig.enableMetrics) {
            recordCacheMetrics(hit = false)
        }

        val result = delegate.processComm(comm)

        // Store in cache (only cache successes)
        if (result.isSuccess) {
            storeInCache(cacheKey, result)
        }

        return result
    }

    /**
     * Generate cache key from comm
     */
    private fun generateCacheKey(comm: Comm): CacheKey {
        // Hash content
        val contentHash = hashString(comm.content)

        // Hash context (from, type, relevant data)
        val contextData = "${comm.from}:${comm.type.name}:${comm.data.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }}"
        val contextHash = hashString(contextData)

        return CacheKey(contentHash, contextHash)
    }

    /**
     * Hash a string using SHA-256
     */
    private fun hashString(input: String): String {
        synchronized(md) {
            val bytes = md.digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }.take(16) // Use first 16 chars
        }
    }

    /**
     * Check cache for entry
     */
    private fun checkCache(key: CacheKey): CachedEntry? {
        val entry = cache[key] ?: return null

        // Check if expired
        if (entry.isExpired(cacheConfig.ttlSeconds * 1000)) {
            cache.remove(key)
            return null
        }

        return entry
    }

    /**
     * Store result in cache
     */
    private fun storeInCache(key: CacheKey, result: SpiceResult<Comm>) {
        // Check cache size and evict if needed
        if (cache.size >= cacheConfig.maxSize) {
            evictLRU()
        }

        val entry = CachedEntry(
            result = result,
            timestamp = System.currentTimeMillis()
        )

        cache[key] = entry
    }

    /**
     * Evict least recently used entry
     */
    private fun evictLRU() {
        val lruEntry = cache.entries.minByOrNull { it.value.lastAccessed.get() }
        lruEntry?.let { cache.remove(it.key) }
    }

    /**
     * Record cache metrics
     */
    private fun recordCacheMetrics(hit: Boolean) {
        // This would integrate with SpiceMetrics
        // For now, we track internally
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val totalRequests = cacheHits.get() + cacheMisses.get()
        val hitRate = if (totalRequests > 0) {
            cacheHits.get().toDouble() / totalRequests
        } else {
            0.0
        }

        return CacheStats(
            size = cache.size,
            maxSize = cacheConfig.maxSize,
            hits = cacheHits.get(),
            misses = cacheMisses.get(),
            hitRate = hitRate,
            ttlSeconds = cacheConfig.ttlSeconds
        )
    }

    /**
     * Clear cache
     */
    fun clearCache() {
        cache.clear()
        cacheHits.set(0)
        cacheMisses.set(0)
    }

    /**
     * Remove expired entries
     */
    fun cleanupExpired() {
        val ttlMs = cacheConfig.ttlSeconds * 1000
        val expiredKeys = cache.filterValues { it.isExpired(ttlMs) }.keys
        expiredKeys.forEach { cache.remove(it) }
    }

    override fun getTools(): List<Tool> = delegate.getTools()
    override fun canHandle(comm: Comm): Boolean = delegate.canHandle(comm)
    override fun isReady(): Boolean = delegate.isReady()

    override val id: String get() = delegate.id
    override val name: String get() = delegate.name
    override val description: String get() = delegate.description
    override val capabilities: List<String> get() = delegate.capabilities
}

/**
 * Cache statistics
 */
data class CacheStats(
    val size: Int,
    val maxSize: Int,
    val hits: Long,
    val misses: Long,
    val hitRate: Double,
    val ttlSeconds: Long
) {
    override fun toString(): String {
        return """
            Cache Statistics:
            - Size: $size / $maxSize
            - Hits: $hits
            - Misses: $misses
            - Hit Rate: ${"%.2f".format(hitRate * 100)}%
            - TTL: ${ttlSeconds}s
        """.trimIndent()
    }
}

/**
 * Extension function to wrap any agent with caching
 */
fun Agent.cached(config: CachedAgent.CacheConfig = CachedAgent.CacheConfig()): Agent {
    return if (this is CachedAgent) {
        this // Already cached
    } else {
        CachedAgent(this, config)
    }
}

/**
 * DSL function to create a cached agent
 */
fun cachedAgent(
    delegate: Agent,
    config: CachedAgent.CacheConfig = CachedAgent.CacheConfig()
): Agent = CachedAgent(delegate, config)
