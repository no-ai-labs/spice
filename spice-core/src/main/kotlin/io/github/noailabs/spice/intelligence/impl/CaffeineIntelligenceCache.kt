package io.github.noailabs.spice.intelligence.impl

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.CompositeDecision
import io.github.noailabs.spice.intelligence.IntelligenceCacheConfig
import io.github.noailabs.spice.intelligence.spi.CacheStats
import io.github.noailabs.spice.intelligence.spi.IntelligenceCache
import io.github.noailabs.spice.intelligence.spi.IntelligenceCacheKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Caffeine 기반 L1 Intelligence Cache 구현체
 *
 * 실제 Caffeine 라이브러리 대신 ConcurrentHashMap 기반으로 구현.
 * Production 환경에서는 실제 Caffeine 사용 권장.
 *
 * ## 특징
 * - TTL 기반 만료
 * - LRU 기반 퇴거 (maxSize 초과 시)
 * - Thread-safe
 * - 통계 수집
 *
 * @property config 캐시 설정
 *
 * @since 2.0.0
 */
class CaffeineIntelligenceCache(
    private val config: IntelligenceCacheConfig = IntelligenceCacheConfig.DEFAULT
) : IntelligenceCache {

    private data class CacheEntry(
        val decision: CompositeDecision,
        val expiresAt: Long,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val evictions = AtomicLong(0)

    override suspend fun get(key: IntelligenceCacheKey): CompositeDecision? {
        if (!config.enabled || !config.l1Enabled) return null

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
                evictions.incrementAndGet()
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
        if (!config.enabled || !config.l1Enabled) {
            return SpiceResult.success(Unit)
        }

        val cacheKey = key.toShortKey()
        val effectiveTtl = ttl.coerceAtMost(config.l1Ttl)
        val expiresAt = System.currentTimeMillis() + effectiveTtl.inWholeMilliseconds

        // Max size 체크 및 퇴거
        if (cache.size >= config.l1MaxSize) {
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
        cache.entries.removeIf { (_, entry) ->
            // 실제 구현에서는 key에 workflowId 정보가 포함되어야 함
            // 현재는 모든 엔트리 검사
            val shouldRemove = entry.decision.newWorkflowId == workflowId
            if (shouldRemove) count++
            shouldRemove
        }
        return SpiceResult.success(count)
    }

    override suspend fun stats(): CacheStats {
        // 만료된 엔트리 정리
        cleanExpired()

        return CacheStats(
            hits = hits.get(),
            misses = misses.get(),
            size = cache.size.toLong(),
            evictions = evictions.get()
        )
    }

    /**
     * 캐시 전체 클리어
     */
    fun clear() {
        cache.clear()
        hits.set(0)
        misses.set(0)
        evictions.set(0)
    }

    /**
     * 현재 캐시 크기
     */
    fun size(): Int = cache.size

    private fun evictOldest() {
        // 가장 오래된 엔트리 제거 (LRU 근사)
        val oldest = cache.entries
            .minByOrNull { it.value.createdAt }
            ?.key

        if (oldest != null) {
            cache.remove(oldest)
            evictions.incrementAndGet()
        }
    }

    private fun cleanExpired() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { (_, entry) ->
            val expired = entry.expiresAt < now
            if (expired) evictions.incrementAndGet()
            expired
        }
    }

    companion object {
        /**
         * 기본 설정으로 생성
         */
        fun default(): CaffeineIntelligenceCache =
            CaffeineIntelligenceCache(IntelligenceCacheConfig.DEFAULT)

        /**
         * 테스트용 (작은 크기, 짧은 TTL)
         */
        fun forTest(
            ttl: Duration = 1.minutes,
            maxSize: Int = 100
        ): CaffeineIntelligenceCache = CaffeineIntelligenceCache(
            IntelligenceCacheConfig(
                l1Ttl = ttl,
                l1MaxSize = maxSize,
                l2Enabled = false
            )
        )

        /**
         * 비활성화된 캐시 (캐싱 안 함)
         */
        val DISABLED = CaffeineIntelligenceCache(
            IntelligenceCacheConfig(enabled = false)
        )
    }
}
