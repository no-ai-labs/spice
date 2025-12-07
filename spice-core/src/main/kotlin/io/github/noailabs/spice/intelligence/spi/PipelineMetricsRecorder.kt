package io.github.noailabs.spice.intelligence.spi

import io.github.noailabs.spice.intelligence.*

/**
 * Pipeline Metrics Recorder SPI (Intelligence Layer v2)
 *
 * 파이프라인 실행 중 발생하는 메트릭 이벤트를 기록하는 인터페이스.
 *
 * ## 기록 포인트
 * ```
 * [Pipeline Start]
 *      ↓
 * [Cache Check] → recordCacheAccess()
 *      ↓
 * [Semantic Match] → recordSemanticMatch()
 *      ↓
 * [Domain Relevance] → recordDomainRelevance()
 *      ↓
 * [Policy-RAG] → recordPolicyRag()
 *      ↓
 * [Gating] → recordGating()
 *      ↓
 * [Nano/Big LLM] → recordLlmCall()
 *      ↓
 * [Pipeline End] → recordPipelineResult()
 * ```
 *
 * ## Clean Architecture
 * - spice-core: 이 인터페이스 + NoOp 구현
 * - kai-core/springboot: Micrometer 기반 구현
 *
 * @since 2.0.0
 */
interface PipelineMetricsRecorder {

    /**
     * 캐시 접근 기록
     */
    fun recordCacheAccess(event: CacheAccessEvent)

    /**
     * Semantic 매칭 결과 기록
     */
    fun recordSemanticMatch(event: SemanticMatchEvent)

    /**
     * Domain Relevance 계산 결과 기록
     */
    fun recordDomainRelevance(event: DomainRelevanceEvent)

    /**
     * Policy-RAG 검색 결과 기록
     */
    fun recordPolicyRag(event: PolicyRagEvent)

    /**
     * Gating 결정 기록
     */
    fun recordGating(event: GatingEvent)

    /**
     * LLM 호출 기록
     */
    fun recordLlmCall(event: LlmCallEvent)

    /**
     * 파이프라인 최종 결과 기록
     */
    fun recordPipelineResult(event: PipelineResultEvent)

    /**
     * Block 발생 기록
     */
    fun recordBlock(event: BlockEvent)

    /**
     * Intent Shift 기록
     */
    fun recordIntentShift(event: IntentShiftEvent)

    companion object {
        /**
         * NoOp 구현 (메트릭 비활성화)
         */
        val NO_OP: PipelineMetricsRecorder = NoOpPipelineMetricsRecorder
    }
}

/**
 * 캐시 접근 이벤트
 */
data class CacheAccessEvent(
    val tenantId: String,
    val workflowId: String?,
    val layer: CacheLayer,
    val hit: Boolean,
    val latencyMs: Long
)

/**
 * Semantic 매칭 이벤트
 */
data class SemanticMatchEvent(
    val tenantId: String,
    val workflowId: String?,
    val topScore: Double,
    val secondScore: Double,
    val gap: Double,
    val candidateCount: Int,
    val latencyMs: Long
)

/**
 * Domain Relevance 이벤트
 */
data class DomainRelevanceEvent(
    val tenantId: String,
    val workflowId: String?,
    val score: Double,
    val source: DomainRelevanceSource,
    val latencyMs: Long
)

/**
 * Policy-RAG 이벤트
 */
data class PolicyRagEvent(
    val tenantId: String,
    val workflowId: String?,
    val hitCount: Int,
    val success: Boolean,
    val fallback: Boolean,
    val latencyMs: Long
)

/**
 * Gating 이벤트
 */
data class GatingEvent(
    val tenantId: String,
    val workflowId: String?,
    val decision: GatingDecision,
    val topScore: Double,
    val domainRelevance: Double,
    val gap: Double
)

/**
 * LLM 호출 이벤트
 */
data class LlmCallEvent(
    val tenantId: String,
    val workflowId: String?,
    val tier: LlmTier,
    val status: LlmCallStatus,
    val tokensUsed: Int?,
    val latencyMs: Long
)

/**
 * LLM 티어
 */
enum class LlmTier {
    NANO,
    BIG
}

/**
 * LLM 호출 상태
 */
enum class LlmCallStatus {
    SUCCESS,
    FAILED,
    TIMEOUT,
    SKIPPED
}

/**
 * 파이프라인 결과 이벤트
 *
 * ## 대시보드 필수 태그
 * - tenantId: 테넌트 필터링
 * - workflowId: 워크플로우 필터링
 * - routingSignal: NORMAL/AMBIGUOUS/OFF_DOMAIN_HARD/etc.
 * - decisionSource: EMBEDDING/NANO/SLM/etc.
 * - cacheLayer: SEMANTIC/NANO/BIG (캐시 히트 시)
 */
data class PipelineResultEvent(
    val tenantId: String,
    val workflowId: String?,
    val routingSignal: RoutingSignal,
    val decisionSource: DecisionSource,
    val confidence: Double,
    val cacheHit: Boolean,
    val cacheLayer: CacheLayer?,  // 캐시 히트 시 어느 레이어인지
    val totalLatencyMs: Long
)

/**
 * Block 이벤트
 *
 * ## 대시보드 필수 태그
 * - tenantId, workflowId: 필터링
 * - reason: BlockReason enum 값
 * - isHardBlock: true면 완전 차단, false면 fallback 가능
 */
data class BlockEvent(
    val tenantId: String,
    val workflowId: String?,
    val nodeId: String?,  // 어느 노드에서 발생했는지
    val reason: BlockReason,
    val isHardBlock: Boolean
)

/**
 * Intent Shift 이벤트
 *
 * ## 대시보드 필수 태그
 * - tenantId: 테넌트 필터링
 * - fromWorkflow: 출발 워크플로우 (null이면 최초 진입)
 * - toWorkflow: 도착 워크플로우
 */
data class IntentShiftEvent(
    val tenantId: String,
    val fromWorkflow: String?,  // null이면 최초 진입
    val toWorkflow: String,
    val utterance: String?  // 어떤 발화로 전환되었는지 (디버깅용)
)

/**
 * NoOp 구현
 */
private object NoOpPipelineMetricsRecorder : PipelineMetricsRecorder {
    override fun recordCacheAccess(event: CacheAccessEvent) {}
    override fun recordSemanticMatch(event: SemanticMatchEvent) {}
    override fun recordDomainRelevance(event: DomainRelevanceEvent) {}
    override fun recordPolicyRag(event: PolicyRagEvent) {}
    override fun recordGating(event: GatingEvent) {}
    override fun recordLlmCall(event: LlmCallEvent) {}
    override fun recordPipelineResult(event: PipelineResultEvent) {}
    override fun recordBlock(event: BlockEvent) {}
    override fun recordIntentShift(event: IntentShiftEvent) {}
}
