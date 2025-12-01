package io.github.noailabs.spice.intelligence.impl

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.CompositeDecision
import io.github.noailabs.spice.intelligence.spi.CacheStats
import io.github.noailabs.spice.intelligence.spi.IntelligenceCache
import io.github.noailabs.spice.intelligence.spi.IntelligenceCacheKey
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * InMemory Intelligence Cache (테스트/개발용)
 *
 * ConcurrentHashMap 기반의 간단한 TTL 캐시.
 * 프로덕션에서는 kai-core의 Redis 구현체 사용.
 *
 * ## 특징
 * - TTL 기반 만료
 * - LRU-like eviction (maxSize 초과 시)
 * - 캐시 통계 제공
 *
 * ## 사용 예시
 * ```kotlin
 * val cache = InMemoryIntelligenceCache(maxSize = 1000)
 *
 * val key = IntelligenceCacheKey.from("취소해줘", listOf("node1"), "workflow1")
 * cache.put(key, decision, ttl = 30.minutes)
 *
 * val cached = cache.get(key)
 * ```
 *
 * @since 2.0.0
 */
class InMemoryIntelligenceCache(
    private val maxSize: Int = 10_000,
    private val defaultTtl: Duration = 30.minutes
) : IntelligenceCache {

    private data class CacheEntry(
        val decision: CompositeDecision,
        val expiresAt: Instant,
        val createdAt: Instant = Clock.System.now()
    ) {
        fun isExpired(now: Instant = Clock.System.now()): Boolean =
            now >= expiresAt
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val evictions = AtomicLong(0)

    override suspend fun get(key: IntelligenceCacheKey): CompositeDecision? {
        val cacheKey = key.toShortKey()
        val entry = cache[cacheKey]

        return when {
            entry == null -> {
                misses.incrementAndGet()
                null
            }
            entry.isExpired() -> {
                cache.remove(cacheKey)
                misses.incrementAndGet()
                null
            }
            else -> {
                hits.incrementAndGet()
                entry.decision
            }
        }
    }

    override suspend fun put(
        key: IntelligenceCacheKey,
        decision: CompositeDecision,
        ttl: Duration
    ): SpiceResult<Unit> {
        val cacheKey = key.toShortKey()
        val now = Clock.System.now()
        val expiresAt = Instant.fromEpochMilliseconds(
            now.toEpochMilliseconds() + ttl.inWholeMilliseconds
        )

        // maxSize 초과 시 오래된 항목 제거
        if (cache.size >= maxSize) {
            evictOldest()
        }

        cache[cacheKey] = CacheEntry(decision, expiresAt)
        return SpiceResult.success(Unit)
    }

    override suspend fun invalidate(key: IntelligenceCacheKey): SpiceResult<Unit> {
        cache.remove(key.toShortKey())
        return SpiceResult.success(Unit)
    }

    override suspend fun invalidateByWorkflow(workflowId: String): SpiceResult<Int> {
        var count = 0
        cache.keys.filter { it.contains(workflowId) }.forEach { key ->
            cache.remove(key)
            count++
        }
        return SpiceResult.success(count)
    }

    override suspend fun stats(): CacheStats {
        // 만료된 항목 정리
        cleanupExpired()

        return CacheStats(
            hits = hits.get(),
            misses = misses.get(),
            size = cache.size.toLong(),
            evictions = evictions.get()
        )
    }

    /**
     * 캐시 초기화
     */
    fun reset() {
        cache.clear()
        hits.set(0)
        misses.set(0)
        evictions.set(0)
    }

    /**
     * 현재 캐시 크기
     */
    fun size(): Int = cache.size

    /**
     * 만료된 항목 정리
     */
    fun cleanupExpired() {
        val now = Clock.System.now()
        cache.entries.removeIf { it.value.isExpired(now) }
    }

    /**
     * 가장 오래된 항목 제거 (FIFO-like)
     */
    private fun evictOldest() {
        val oldest = cache.entries
            .minByOrNull { it.value.createdAt }
            ?.key

        oldest?.let {
            cache.remove(it)
            evictions.incrementAndGet()
        }
    }

    companion object {
        /**
         * No-op 캐시 (캐시 비활성화 테스트용)
         */
        val NO_OP = object : IntelligenceCache {
            override suspend fun get(key: IntelligenceCacheKey): CompositeDecision? = null
            override suspend fun put(key: IntelligenceCacheKey, decision: CompositeDecision, ttl: Duration) =
                SpiceResult.success(Unit)
            override suspend fun invalidate(key: IntelligenceCacheKey) = SpiceResult.success(Unit)
            override suspend fun invalidateByWorkflow(workflowId: String) = SpiceResult.success(0)
            override suspend fun stats() = CacheStats.EMPTY
        }
    }
}
