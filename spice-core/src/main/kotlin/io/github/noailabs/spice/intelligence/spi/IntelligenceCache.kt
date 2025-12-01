package io.github.noailabs.spice.intelligence.spi

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.CacheLayer
import io.github.noailabs.spice.intelligence.CompositeDecision
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Intelligence Cache SPI (Intelligence Layer v2)
 *
 * CompositeDecision 캐싱 인터페이스.
 * 2-Level 캐시 구조 지원 (L1: In-Memory, L2: Redis).
 *
 * ## 캐시 키
 * hash(utterance + prevNodes + workflowId)
 *
 * ## Clean Architecture
 * - spice-core: 이 인터페이스 + InMemory 테스트 구현
 * - kai-core: Redis L2 캐시 구현
 *
 * @since 2.0.0
 */
interface IntelligenceCache {

    /**
     * 캐시 조회
     *
     * @param key 캐시 키
     * @return 캐시된 결정 또는 null
     */
    suspend fun get(key: IntelligenceCacheKey): CompositeDecision?

    /**
     * 캐시 저장
     *
     * @param key 캐시 키
     * @param decision 저장할 결정
     * @param ttl 유효 기간
     */
    suspend fun put(
        key: IntelligenceCacheKey,
        decision: CompositeDecision,
        ttl: Duration = 30.minutes
    ): SpiceResult<Unit>

    /**
     * 캐시 무효화
     *
     * @param key 무효화할 캐시 키
     */
    suspend fun invalidate(key: IntelligenceCacheKey): SpiceResult<Unit>

    /**
     * 워크플로우 관련 캐시 전체 무효화
     *
     * @param workflowId 워크플로우 ID
     * @return 무효화된 항목 수
     */
    suspend fun invalidateByWorkflow(workflowId: String): SpiceResult<Int>

    /**
     * 캐시 통계 조회
     */
    suspend fun stats(): CacheStats
}

/**
 * 캐시 키
 *
 * @property layer 캐시 레이어 (SEMANTIC, NANO, BIG, POLICY)
 * @property normalizedUtterance 정규화된 발화
 * @property prevNodesHash 이전 노드 경로 해시
 * @property workflowId 워크플로우 ID
 *
 * @since 2.0.0
 */
data class IntelligenceCacheKey(
    val layer: CacheLayer,
    val normalizedUtterance: String,
    val prevNodesHash: String,
    val workflowId: String?
) {
    /**
     * Redis 키 생성 (레이어 포함)
     */
    fun toRedisKey(tenantId: String? = null): String {
        val tenant = tenantId ?: "_default"
        val workflow = workflowId ?: "_global"
        return "spice:intel:${layer.key}:$tenant:$workflow:$prevNodesHash:${normalizedUtterance.hashCode()}"
    }

    /**
     * 해시 코드 기반 단축 키 (레이어 포함)
     */
    fun toShortKey(): String {
        val combined = "${layer.key}|$normalizedUtterance|$prevNodesHash|$workflowId"
        return combined.hashCode().toString(16)
    }

    companion object {
        /**
         * 요청에서 캐시 키 생성
         */
        fun from(request: OneShotRequest, layer: CacheLayer = CacheLayer.BIG): IntelligenceCacheKey {
            val normalized = normalizeUtterance(request.utterance)
            val prevNodesHash = request.prevNodes.joinToString("|").hashCode().toString()
            return IntelligenceCacheKey(layer, normalized, prevNodesHash, request.workflowId)
        }

        /**
         * 원시 값에서 캐시 키 생성
         */
        fun from(
            layer: CacheLayer,
            utterance: String,
            prevNodes: List<String>,
            workflowId: String?
        ): IntelligenceCacheKey {
            val normalized = normalizeUtterance(utterance)
            val prevNodesHash = prevNodes.joinToString("|").hashCode().toString()
            return IntelligenceCacheKey(layer, normalized, prevNodesHash, workflowId)
        }

        /**
         * 발화 정규화 (소문자, 공백 정리)
         */
        private fun normalizeUtterance(utterance: String): String =
            utterance.lowercase().trim().replace(Regex("\\s+"), " ")
    }
}

/**
 * 캐시 통계
 *
 * @since 2.0.0
 */
data class CacheStats(
    val hits: Long,
    val misses: Long,
    val size: Long,
    val evictions: Long
) {
    /**
     * 히트율 (0.0-1.0)
     */
    val hitRate: Double
        get() = if (hits + misses == 0L) 0.0 else hits.toDouble() / (hits + misses)

    companion object {
        val EMPTY = CacheStats(0, 0, 0, 0)
    }
}
