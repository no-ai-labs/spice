package io.github.noailabs.spice.hitl.template

import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

private val logger = KotlinLogging.logger {}

/**
 * Caching decorator for HitlTemplateLoader
 *
 * Wraps any HitlTemplateLoader implementation with an in-memory cache layer.
 * Supports TTL-based expiration and negative caching (caching non-existent templates).
 *
 * **Usage:**
 * ```kotlin
 * val fileLoader = FileHitlTemplateLoader("/templates")
 * val cachedLoader = CachingHitlTemplateLoader(
 *     delegate = fileLoader,
 *     ttl = 1.hours,
 *     cacheNulls = true
 * )
 *
 * // Use with Registry (disable Registry's internal cache to avoid double caching)
 * val registry = HitlTemplateRegistry(
 *     loader = cachedLoader,
 *     enableCaching = false  // Important: prevent double caching
 * )
 * ```
 *
 * **Cache Key Strategy:**
 * - Format: `"{tenantId}:{templateId}"` or `"_default_:{templateId}"`
 * - Tenant isolation ensures no cross-tenant cache pollution
 *
 * **TTL Behavior:**
 * - Lazy eviction: expired entries are checked on access
 * - No background cleanup thread (memory-efficient for small caches)
 * - Optional periodic cleanup via [evictExpired]
 *
 * **maxSize Policy:**
 * - When maxSize is reached, oldest entries are evicted first (FIFO-like)
 * - Expired entries are evicted before non-expired ones
 * - `maxSize = 0` means unlimited (default)
 *
 * @param delegate The underlying loader to cache
 * @param ttl Time-to-live for cache entries (default: 1 hour)
 * @param cacheNulls Whether to cache negative lookups (default: true)
 * @param maxSize Maximum cache size, 0 = unlimited (default: 0)
 *
 * @since Spice 1.3.5
 */
class CachingHitlTemplateLoader(
    private val delegate: HitlTemplateLoader,
    private val ttl: Duration = 1.hours,
    private val cacheNulls: Boolean = true,
    private val maxSize: Int = 0
) : HitlTemplateLoader {

    /**
     * Cache entry with expiration timestamp
     */
    private data class CacheEntry(
        val template: HitlTemplate?,
        val expiresAt: Instant,
        val isNullEntry: Boolean = template == null
    ) {
        fun isExpired(now: Instant = Instant.now()): Boolean = now >= expiresAt
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Generate cache key from template ID and tenant ID
     */
    private fun cacheKey(id: String, tenantId: String?): String {
        val tenant = tenantId ?: "_default_"
        return "$tenant:$id"
    }

    override fun load(id: String, tenantId: String?): HitlTemplate? {
        val key = cacheKey(id, tenantId)
        val now = Instant.now()

        // Check cache
        cache[key]?.let { entry ->
            if (!entry.isExpired(now)) {
                logger.debug { "[CachingHitlTemplateLoader] Cache hit for '$key'" }
                return entry.template
            }
            // Expired, remove and proceed to load
            cache.remove(key)
            logger.debug { "[CachingHitlTemplateLoader] Cache expired for '$key'" }
        }

        // Load from delegate
        val template = delegate.load(id, tenantId)

        // Cache result (including nulls if enabled)
        if (template != null || cacheNulls) {
            enforceMaxSize()
            val expiresAt = now.plus(ttl.toJavaDuration())
            cache[key] = CacheEntry(
                template = template,
                expiresAt = expiresAt
            )
            logger.debug {
                "[CachingHitlTemplateLoader] Cached ${if (template == null) "null" else "template"} for '$key'"
            }
        }

        return template
    }

    override fun exists(id: String, tenantId: String?): Boolean {
        val key = cacheKey(id, tenantId)
        val now = Instant.now()

        // Check cache first (faster than loading full template)
        cache[key]?.let { entry ->
            if (!entry.isExpired(now)) {
                return entry.template != null
            }
        }

        // Fall back to delegate
        return delegate.exists(id, tenantId)
    }

    override fun listTemplateIds(tenantId: String?): List<String> {
        // Not cached - typically called infrequently and result may change
        return delegate.listTemplateIds(tenantId)
    }

    // ============================================================
    // Cache Management
    // ============================================================

    /**
     * Clear all cached entries
     */
    fun clearCache() {
        val size = cache.size
        cache.clear()
        logger.info { "[CachingHitlTemplateLoader] Cleared $size cache entries" }
    }

    /**
     * Clear cache entries for a specific tenant
     *
     * @param tenantId Tenant ID (null for default tenant)
     */
    fun clearCacheForTenant(tenantId: String?) {
        val prefix = "${tenantId ?: "_default_"}:"
        val removed = cache.keys.filter { it.startsWith(prefix) }
        removed.forEach { cache.remove(it) }
        logger.info { "[CachingHitlTemplateLoader] Cleared ${removed.size} entries for tenant '${tenantId ?: "default"}'" }
    }

    /**
     * Invalidate a specific cache entry
     *
     * @param id Template ID
     * @param tenantId Tenant ID (null for default tenant)
     */
    fun invalidate(id: String, tenantId: String? = null) {
        val key = cacheKey(id, tenantId)
        cache.remove(key)
        logger.debug { "[CachingHitlTemplateLoader] Invalidated cache for '$key'" }
    }

    /**
     * Evict all expired entries
     *
     * Call periodically if memory is a concern.
     * Not required for correctness (lazy eviction handles expiration on access).
     *
     * @return Number of entries evicted
     */
    fun evictExpired(): Int {
        val now = Instant.now()
        val expired = cache.entries.filter { it.value.isExpired(now) }.map { it.key }
        expired.forEach { cache.remove(it) }
        logger.debug { "[CachingHitlTemplateLoader] Evicted ${expired.size} expired entries" }
        return expired.size
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CachingStats = CachingStats(
        size = cache.size,
        ttlMillis = ttl.inWholeMilliseconds,
        cacheNulls = cacheNulls,
        maxSize = maxSize,
        nullEntryCount = cache.values.count { it.isNullEntry }
    )

    /**
     * Enforce max size by evicting oldest entries (FIFO-like)
     *
     * Expired entries are evicted first, then oldest by expiration time.
     */
    private fun enforceMaxSize() {
        if (maxSize <= 0 || cache.size < maxSize) return

        val now = Instant.now()
        // Sort: expired first (MIN_VALUE), then by expiration time (oldest first)
        val toEvict = cache.entries
            .sortedBy {
                if (it.value.isExpired(now)) Long.MIN_VALUE
                else it.value.expiresAt.toEpochMilli()
            }
            .take((cache.size - maxSize + 1).coerceAtLeast(1))
            .map { it.key }

        toEvict.forEach { cache.remove(it) }
        logger.debug { "[CachingHitlTemplateLoader] Evicted ${toEvict.size} entries to enforce maxSize=$maxSize" }
    }

    companion object {
        /**
         * Create a caching loader with default settings
         */
        fun wrap(delegate: HitlTemplateLoader): CachingHitlTemplateLoader =
            CachingHitlTemplateLoader(delegate)

        /**
         * Create a caching loader with custom TTL
         */
        fun withTtl(delegate: HitlTemplateLoader, ttl: Duration): CachingHitlTemplateLoader =
            CachingHitlTemplateLoader(delegate, ttl = ttl)

        /**
         * Create a caching loader without null caching
         *
         * Use when delegate is expected to have frequent additions
         * and negative lookups should be re-checked.
         */
        fun withoutNullCaching(delegate: HitlTemplateLoader): CachingHitlTemplateLoader =
            CachingHitlTemplateLoader(delegate, cacheNulls = false)
    }
}

/**
 * Cache statistics for monitoring
 *
 * @property size Current number of entries in cache
 * @property ttlMillis TTL in milliseconds
 * @property cacheNulls Whether null entries are cached
 * @property maxSize Maximum cache size (0 = unlimited)
 * @property nullEntryCount Number of null entries in cache
 */
data class CachingStats(
    val size: Int,
    val ttlMillis: Long,
    val cacheNulls: Boolean,
    val maxSize: Int,
    val nullEntryCount: Int
)
