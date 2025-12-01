package io.github.noailabs.spice.intelligence.spi

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.CompositeDecision
import io.github.noailabs.spice.intelligence.GatingDecision

/**
 * Intelligence Resolver SPI (Intelligence Layer v2)
 *
 * Fast → Nano → Big LLM 계층을 통합하는 핵심 인터페이스.
 * 기존 SemanticMatcher, NanoReasoner를 조합하여 계층적 판단 수행.
 *
 * ## 파이프라인 구조
 * ```
 * [Cache Check]
 *      ↓ miss
 * [Parallel: Embedding + Policy-RAG]
 *      ↓
 * [Confidence Gating]
 *   ├─ ≥0.85 + domainRelevance ≥0.70 + gap ≥0.15 → FAST_PATH
 *   ├─ ≥0.65 → NANO_VALIDATION
 *   └─ <0.65 → BIG_LLM
 * ```
 *
 * ## 핵심 원칙 (오메가 수정)
 * - embedding score 단독 gating 금지!
 * - embedding score + domain relevance + gap + policy context 조합으로 판단
 *
 * @since 2.0.0
 */
interface IntelligenceResolver {

    /**
     * 발화 + 컨텍스트로 Intelligence 판단 수행
     *
     * @param utterance 사용자 발화
     * @param context 판단 컨텍스트
     * @return 복합 결정 결과
     */
    suspend fun resolve(
        utterance: String,
        context: IntelligenceContext
    ): SpiceResult<CompositeDecision>

    /**
     * Gating 결정만 수행 (캐시 미사용)
     *
     * @param semanticScore 시멘틱 매칭 점수 (top candidate)
     * @param secondScore 2위 후보 점수 (gap 계산용)
     * @param domainRelevance 도메인 관련성 점수
     * @return Gating 결정 (FAST_PATH, NANO_VALIDATION, BIG_LLM)
     */
    fun gate(
        semanticScore: Double,
        secondScore: Double,
        domainRelevance: Double
    ): GatingDecision
}

/**
 * Semantic 매칭 결과 (Gating 입력용)
 *
 * ## 오메가 지침
 * embedding score 단독 사용 금지!
 * 아래 모든 필드를 조합하여 Gating 판단 수행.
 *
 * @property topScore 1위 후보 점수
 * @property secondScore 2위 후보 점수
 * @property topCanonical 1위 후보 canonical
 * @property candidates 전체 후보 목록
 * @property optionCount 전체 옵션 수
 * @property domainRelevance 도메인 관련성 (별도 계산!)
 * @property domainRelevanceSource 도메인 관련성 계산 소스
 *
 * @since 2.0.0
 */
data class SemanticMatchResult(
    val topScore: Double,
    val secondScore: Double,
    val topCanonical: String?,
    val candidates: List<ScoredCandidate>,
    val optionCount: Int = 0,
    val domainRelevance: Double,
    val domainRelevanceSource: DomainRelevanceSource = DomainRelevanceSource.HEURISTIC
) {
    /**
     * 1위-2위 점수 격차
     */
    val gap: Double
        get() = topScore - secondScore

    /**
     * 후보가 있는지 여부
     */
    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()

    /**
     * 정확 매칭 여부 (topScore ≥ 0.85, gap ≥ 0.15, domainRelevance ≥ 0.70)
     */
    val isExactMatch: Boolean
        get() = topScore >= 0.85 && gap >= 0.15 && domainRelevance >= 0.70

    /**
     * 퍼지 매칭 여부 (0.65 ≤ topScore < 0.85)
     */
    val isFuzzyMatch: Boolean
        get() = topScore >= 0.65 && topScore < 0.85

    /**
     * 모호한 매칭 여부 (gap < 0.15)
     */
    val isAmbiguous: Boolean
        get() = gap < 0.15 && optionCount > 1

    /**
     * Off-domain 가능성 (domainRelevance < 0.5)
     */
    val isPossiblyOffDomain: Boolean
        get() = domainRelevance < 0.5

    /**
     * 확실한 Off-domain (domainRelevance < 0.3)
     */
    val isDefinitelyOffDomain: Boolean
        get() = domainRelevance < 0.3

    /**
     * Gating 입력으로 변환
     */
    fun toGatingInput() = GatingInput(
        topScore = topScore,
        secondScore = secondScore,
        gap = gap,
        domainRelevance = domainRelevance,
        optionCount = optionCount
    )

    companion object {
        val EMPTY = SemanticMatchResult(
            topScore = 0.0,
            secondScore = 0.0,
            topCanonical = null,
            candidates = emptyList(),
            optionCount = 0,
            domainRelevance = 0.0
        )
    }
}

/**
 * Gating 입력 데이터
 *
 * @property topScore 1위 점수
 * @property secondScore 2위 점수
 * @property gap 점수 격차
 * @property domainRelevance 도메인 관련성
 * @property optionCount 옵션 수
 *
 * @since 2.0.0
 */
data class GatingInput(
    val topScore: Double,
    val secondScore: Double,
    val gap: Double,
    val domainRelevance: Double,
    val optionCount: Int
)

/**
 * 도메인 관련성 계산 소스
 *
 * @since 2.0.0
 */
enum class DomainRelevanceSource {
    /** 휴리스틱 기반 (기본값) */
    HEURISTIC,

    /** Policy-RAG 기반 */
    POLICY_RAG,

    /** 별도 분류기 기반 */
    CLASSIFIER,

    /** LLM 기반 */
    LLM
}

/**
 * 점수가 매겨진 후보
 *
 * @property canonical canonical 값
 * @property score 점수 (0.0-1.0)
 * @property label 표시 라벨
 *
 * @since 2.0.0
 */
data class ScoredCandidate(
    val canonical: String,
    val score: Double,
    val label: String? = null
)

/**
 * 도메인 관련성 계산기 SPI
 *
 * ## 오메가 지침
 * - embedding score와 별개로 도메인 관련성 계산!
 * - Policy-RAG 컨텍스트 활용 가능
 * - 판단 로직 포함하지 않음 (점수만 제공)
 *
 * ## 입출력 스케일
 * - 입력: utterance (String), context (DomainRelevanceContext)
 * - 출력: DomainRelevanceResult.score (0.0 ~ 1.0)
 *   - 0.0 ~ 0.3: Hard off-domain (도메인 외 확실)
 *   - 0.3 ~ 0.5: Soft off-domain (도메인 외 가능성)
 *   - 0.5 ~ 0.7: 경계선 (추가 검증 필요)
 *   - 0.7 ~ 1.0: In-domain (도메인 내 확실)
 *
 * ## Fallback 정책 (계산 실패 시)
 * 계산 실패/타임아웃 시 Gating에 사용할 기본값:
 * - **DEFAULT_FALLBACK_SCORE = 0.5** (중립값)
 * - 실패해도 Gating이 완전히 막히지 않도록 중립 점수 반환
 * - 로그: domain_relevance_fallback = true, fallback_reason 기록
 *
 * ## 구현체 책임
 * - 구현체는 예외 발생 시 fallback 결과 반환해야 함
 * - 타임아웃 처리 책임은 구현체에 있음
 * - fallback() 헬퍼 메서드 제공
 *
 * ## Clean Architecture
 * - spice-core: 이 인터페이스 정의 (포트)
 * - kai-core: Policy-RAG/분류기 기반 구현체 (어댑터)
 *
 * @since 2.0.0
 */
interface DomainRelevanceCalculator {

    /**
     * 도메인 관련성 계산
     *
     * @param utterance 사용자 발화
     * @param context 컨텍스트
     * @return 도메인 관련성 결과 (0.0-1.0)
     */
    suspend fun calculate(
        utterance: String,
        context: DomainRelevanceContext
    ): DomainRelevanceResult

    /**
     * 계산기 ID
     */
    val id: String
        get() = this::class.simpleName ?: "unknown"

    /**
     * 계산 소스 타입
     */
    val sourceType: DomainRelevanceSource

    companion object {
        /**
         * Fallback 기본 점수 (계산 실패 시)
         *
         * 0.5 = 중립값. Gating에서 추가 검증 경로로 진행.
         */
        const val DEFAULT_FALLBACK_SCORE = 0.5

        /**
         * Fallback 결과 생성 헬퍼
         *
         * @param reason 실패 사유
         * @return 중립 점수의 Fallback 결과
         */
        fun fallback(reason: String) = DomainRelevanceResult(
            score = DEFAULT_FALLBACK_SCORE,
            source = DomainRelevanceSource.HEURISTIC,
            reasoning = "Fallback: $reason"
        )
    }
}

/**
 * 도메인 관련성 계산 컨텍스트
 *
 * @property workflowId 워크플로우 ID
 * @property tenantId 테넌트 ID
 * @property policyHints Policy-RAG 힌트 (있으면)
 * @property semanticScore 시멘틱 매칭 점수 (참고용)
 *
 * @since 2.0.0
 */
data class DomainRelevanceContext(
    val workflowId: String?,
    val tenantId: String,
    val policyHints: List<io.github.noailabs.spice.intelligence.PolicyHint> = emptyList(),
    val semanticScore: Double? = null
)

/**
 * 도메인 관련성 계산 결과
 *
 * @property score 관련성 점수 (0.0-1.0)
 * @property source 계산 소스
 * @property reasoning 판단 근거 (디버깅용)
 * @property policyMatchCount 매칭된 정책 수
 *
 * @since 2.0.0
 */
data class DomainRelevanceResult(
    val score: Double,
    val source: DomainRelevanceSource,
    val reasoning: String? = null,
    val policyMatchCount: Int = 0
) {
    init {
        require(score in 0.0..1.0) { "Score must be between 0.0 and 1.0, got: $score" }
    }

    /**
     * Hard off-domain 여부 (< 0.3)
     */
    val isHardOffDomain: Boolean
        get() = score < 0.3

    /**
     * Soft off-domain 여부 (0.3 ~ 0.5)
     */
    val isSoftOffDomain: Boolean
        get() = score in 0.3..0.5

    /**
     * 도메인 내 여부 (> 0.5)
     */
    val isInDomain: Boolean
        get() = score > 0.5

    companion object {
        /**
         * 휴리스틱 기반 기본값
         */
        fun heuristic(score: Double, reasoning: String? = null) = DomainRelevanceResult(
            score = score,
            source = DomainRelevanceSource.HEURISTIC,
            reasoning = reasoning
        )

        /**
         * Policy-RAG 기반
         */
        fun fromPolicyRAG(score: Double, policyMatchCount: Int, reasoning: String? = null) = DomainRelevanceResult(
            score = score,
            source = DomainRelevanceSource.POLICY_RAG,
            reasoning = reasoning,
            policyMatchCount = policyMatchCount
        )
    }
}
