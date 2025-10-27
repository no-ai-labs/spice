package io.github.noailabs.spice.performance

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 💾 CachedTool - Performance optimization through result caching
 *
 * Wraps any tool with intelligent caching to:
 * - Reduce expensive operations (DB queries, API calls, etc.)
 * - Improve response time (instant cache hits)
 * - Track cache hit rates and efficiency
 *
 * Features:
 * - TTL-based expiration
 * - LRU eviction when cache is full
 * - Context-aware cache keys
 * - Comprehensive metrics
 * - Custom key builders
 *
 * Usage:
 * ```kotlin
 * val expensiveTool = SimpleTool("expensive") { params ->
 *     // Expensive operation
 * }
 *
 * val cachedTool = CachedTool(
 *     delegate = expensiveTool,
 *     config = ToolCacheConfig(
 *         keyBuilder = { params, context ->
 *             "${context.tenantId}|${params["id"]}"
 *         },
 *         ttl = 300,
 *         maxSize = 1000
 *     )
 * )
 * ```
 */
class CachedTool(
    private val delegate: Tool,
    private val config: ToolCacheConfig = ToolCacheConfig()
) : Tool {

    private val cache = ConcurrentHashMap<String, CachedToolEntry>()
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val md = MessageDigest.getInstance("SHA-256")

    /**
     * Tool cache configuration
     */
    data class ToolCacheConfig(
        val maxSize: Int = 1000,                    // Maximum cache entries
        val ttl: Long = 3600,                        // Time-to-live in seconds
        val enableMetrics: Boolean = true,           // Collect metrics
        val keyBuilder: ((Map<String, Any>, AgentContext) -> String)? = null  // Custom key builder
    )

    /**
     * Cached entry with metadata
     */
    data class CachedToolEntry(
        val result: SpiceResult<ToolResult>,
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

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        // Get context from coroutine context or parameters
        val context = kotlin.coroutines.coroutineContext[AgentContext]
            ?: (parameters["__context"] as? AgentContext)

        // Generate cache key
        val cacheKey = generateCacheKey(parameters, context)

        // Check cache
        val cachedResult = checkCache(cacheKey)
        if (cachedResult != null) {
            cacheHits.incrementAndGet()
            cachedResult.recordHit()

            if (config.enableMetrics) {
                recordCacheMetrics(hit = true)
            }

            return cachedResult.result
        }

        // Cache miss - execute delegate
        cacheMisses.incrementAndGet()

        if (config.enableMetrics) {
            recordCacheMetrics(hit = false)
        }

        val result = delegate.execute(parameters)

        // Store in cache (only cache successes)
        if (result.isSuccess) {
            storeInCache(cacheKey, result)
        }

        return result
    }

    /**
     * Generate cache key from parameters and context
     */
    private fun generateCacheKey(parameters: Map<String, Any>, context: AgentContext?): String {
        // Use custom key builder if provided
        if (config.keyBuilder != null && context != null) {
            return config.keyBuilder.invoke(parameters, context)
        }

        // Default: hash all parameters (excluding internal __context)
        val paramsString = parameters
            .filterKeys { !it.startsWith("__") }  // Exclude internal params
            .entries
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}=${it.value}" }

        // Add context if available
        val contextString = context?.let { ctx ->
            // Use public properties instead of private data
            listOfNotNull(
                ctx.tenantId?.let { "tenantId=$it" },
                ctx.userId?.let { "userId=$it" },
                ctx.sessionId?.let { "sessionId=$it" }
            ).joinToString("|")
        } ?: ""

        return hashString("$paramsString::$contextString")
    }

    /**
     * Hash a string using SHA-256
     */
    private fun hashString(input: String): String {
        synchronized(md) {
            val bytes = md.digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Check cache for entry
     */
    private fun checkCache(key: String): CachedToolEntry? {
        val entry = cache[key] ?: return null

        // Check if expired
        if (entry.isExpired(config.ttl * 1000)) {
            cache.remove(key)
            return null
        }

        return entry
    }

    /**
     * Store result in cache
     */
    private fun storeInCache(key: String, result: SpiceResult<ToolResult>) {
        // Check cache size and evict if needed
        if (cache.size >= config.maxSize) {
            evictLRU()
        }

        val entry = CachedToolEntry(
            result = result,
            timestamp = System.currentTimeMillis()
        )

        cache[key] = entry
    }

    /**
     * Evict least recently used entry
     * Uses lastAccessed as primary criterion, and key as secondary criterion for deterministic behavior
     */
    private fun evictLRU() {
        val lruEntry = cache.entries.minWithOrNull(
            compareBy<Map.Entry<String, CachedToolEntry>> { it.value.lastAccessed.get() }
                .thenBy { it.key }
        )
        lruEntry?.let {
            cache.remove(it.key)
        }
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
    fun getCacheStats(): ToolCacheStats {
        val totalRequests = cacheHits.get() + cacheMisses.get()
        val hitRate = if (totalRequests > 0) {
            cacheHits.get().toDouble() / totalRequests
        } else {
            0.0
        }

        return ToolCacheStats(
            toolName = delegate.name,
            size = cache.size,
            maxSize = config.maxSize,
            hits = cacheHits.get(),
            misses = cacheMisses.get(),
            hitRate = hitRate,
            ttl = config.ttl
        )
    }

    /**
     * Cache metrics property (alias for getCacheStats())
     *
     * Provides convenient property-style access to cache statistics.
     *
     * Example:
     * ```kotlin
     * val tool = myTool.cached(ttl = 300)
     * println("Hit rate: ${tool.metrics.hitRate * 100}%")
     * ```
     */
    val metrics: ToolCacheStats
        get() = getCacheStats()

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
        val ttlMs = config.ttl * 1000
        val expiredKeys = cache.filterValues { it.isExpired(ttlMs) }.keys
        expiredKeys.forEach { cache.remove(it) }
    }

    // Delegate other Tool methods
    override val name: String get() = delegate.name
    override val description: String get() = delegate.description
    override val schema: ToolSchema get() = delegate.schema

    override fun canExecute(parameters: Map<String, Any>): Boolean = delegate.canExecute(parameters)
}

/**
 * Tool cache statistics
 */
data class ToolCacheStats(
    val toolName: String,
    val size: Int,
    val maxSize: Int,
    val hits: Long,
    val misses: Long,
    val hitRate: Double,
    val ttl: Long
) {
    override fun toString(): String {
        return """
            Tool Cache Statistics (${toolName}):
            - Size: $size / $maxSize
            - Hits: $hits
            - Misses: $misses
            - Hit Rate: ${"%.2f".format(hitRate * 100)}%
            - TTL: ${ttl}s
        """.trimIndent()
    }
}

/**
 * Extension function to wrap any tool with caching
 *
 * Example:
 * ```kotlin
 * val expensiveTool = SimpleTool("query") { ... }
 * val cached = expensiveTool.cached(
 *     keyBuilder = { params, context ->
 *         "${context.tenantId}|${params["id"]}"
 *     },
 *     ttl = 300,
 *     maxSize = 1000
 * )
 * ```
 */
fun Tool.cached(
    keyBuilder: ((Map<String, Any>, AgentContext) -> String)? = null,
    ttl: Long = 3600,
    maxSize: Int = 1000,
    enableMetrics: Boolean = true
): Tool {
    return if (this is CachedTool) {
        this // Already cached
    } else {
        CachedTool(
            this,
            CachedTool.ToolCacheConfig(
                maxSize = maxSize,
                ttl = ttl,
                enableMetrics = enableMetrics,
                keyBuilder = keyBuilder
            )
        )
    }
}

/**
 * DSL function to create a cached tool
 */
fun cachedTool(
    delegate: Tool,
    config: CachedTool.ToolCacheConfig = CachedTool.ToolCacheConfig()
): Tool = CachedTool(delegate, config)
