package io.github.noailabs.spice.intelligence

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Intelligence Layer 설정 (v2)
 *
 * ## Confidence Gating (오메가 수정)
 * embedding score 단독 gating 금지!
 * → embedding score + domain relevance + gap + policy context 조합으로 판단
 *
 * ## Gating 로직
 * ```kotlin
 * when {
 *     semanticScore >= exactThreshold
 *         && domainRelevance >= domainRelevanceThreshold
 *         && gap >= gapThreshold → FAST_PATH
 *     semanticScore >= fuzzyThreshold → NANO_VALIDATION
 *     else → BIG_LLM
 * }
 * ```
 *
 * @property exactThreshold Fast path 임계값 (≥0.85 기본)
 * @property fuzzyThreshold Nano validation 임계값 (≥0.65 기본)
 * @property gapThreshold 1위-2위 격차 최소값 (≥0.15 기본)
 * @property domainRelevanceThreshold 도메인 관련성 임계값 (≥0.70 기본)
 * @property cacheConfig 캐시 설정
 * @property enableNano Nano LLM 활성화 여부
 * @property enableBigLLM Big LLM (One-Shot Reasoner) 활성화 여부
 * @property enablePolicyRAG Policy-RAG 활성화 여부
 * @property policyTopK Policy-RAG 검색 시 반환할 정책 수
 * @property maxClarifyAttempts 최대 Clarification 시도 횟수
 * @property loopGuardEnabled LoopGuard 활성화 여부
 * @property featureFlags Feature flag 설정
 *
 * @since 2.0.0
 */
data class IntelligenceConfig(
    // Semantic 임계값
    val exactThreshold: Double = 0.85,
    val fuzzyThreshold: Double = 0.65,
    val gapThreshold: Double = 0.15,

    // Domain relevance 임계값 (embedding score와 별개!)
    val domainRelevanceThreshold: Double = 0.70,

    // 캐시 설정 (v2: 구조화)
    val cacheConfig: IntelligenceCacheConfig = IntelligenceCacheConfig.DEFAULT,

    // 하위 호환용 (deprecated)
    @Deprecated("Use cacheConfig.l1Ttl instead", ReplaceWith("cacheConfig.l1Ttl"))
    val cacheTtl: Duration = 30.minutes,
    @Deprecated("Use cacheConfig.enabled instead", ReplaceWith("cacheConfig.enabled"))
    val cacheEnabled: Boolean = true,

    // 파이프라인 단계 활성화
    val enableNano: Boolean = true,
    val enableBigLLM: Boolean = true,
    val enablePolicyRAG: Boolean = true,

    // Policy-RAG 설정
    val policyTopK: Int = 3,
    val policyRagTimeoutMs: Long = 2000,        // Big LLM 경로용 (2초)
    val policyRagNanoTimeoutMs: Long = 500,     // Nano 경로용 (500ms, optional)

    // Nano LLM 타임아웃
    val nanoTimeoutMs: Long = 500,              // Nano validator 호출 타임아웃 (500ms)

    // Fallback 설정
    val fallbackConfig: FallbackConfig = FallbackConfig.DEFAULT,

    // Clarification/LoopGuard 설정
    val maxClarifyAttempts: Int = 3,
    val loopGuardEnabled: Boolean = true,

    // 메트릭 설정
    val metricsEnabled: Boolean = true,

    // Feature flags (v2)
    val featureFlags: IntelligenceFeatureFlags = IntelligenceFeatureFlags.DEFAULT
) {
    /**
     * 복합 Gating 판단
     *
     * embedding score 단독 사용 금지!
     *
     * ## Gating 로직 상세
     * ```
     * FAST_PATH 조건:
     *   semanticScore >= 0.85 (exactThreshold)
     *   AND domainRelevance >= 0.70 (domainRelevanceThreshold)
     *   AND gap >= 0.15 (gapThreshold)
     *
     * NANO_VALIDATION 조건:
     *   semanticScore >= 0.65 (fuzzyThreshold)
     *   AND enableNano = true
     *
     * BIG_LLM 조건:
     *   위 조건 모두 불충족
     * ```
     *
     * ## Domain Relevance Fallback (0.5) 시 동작
     * domainRelevance = 0.5 (fallback 중립값)일 때:
     * - 0.5 < 0.70 (domainRelevanceThreshold) → FAST_PATH 불가
     * - semanticScore >= 0.65 → NANO_VALIDATION
     * - semanticScore < 0.65 → BIG_LLM
     *
     * 즉, domain relevance 실패 시에도 Nano/Big 경로로 진행 가능.
     * FAST_PATH만 차단됨 (추가 검증 필요).
     */
    fun gate(
        semanticScore: Double,
        secondScore: Double,
        domainRelevance: Double
    ): GatingDecision {
        val gap = semanticScore - secondScore

        return when {
            // 조건 1: semantic 확실 + domain 확실 + gap 충분 → Fast Path
            semanticScore >= exactThreshold
                && domainRelevance >= domainRelevanceThreshold
                && gap >= gapThreshold -> GatingDecision.FAST_PATH

            // 조건 2: semantic 중간 → Nano 검증
            semanticScore >= fuzzyThreshold && enableNano -> GatingDecision.NANO_VALIDATION

            // 조건 3: 불확실 → Big LLM
            else -> if (enableBigLLM) GatingDecision.BIG_LLM else GatingDecision.NANO_VALIDATION
        }
    }

    companion object {
        /**
         * 기본 설정
         */
        val DEFAULT = IntelligenceConfig()

        /**
         * 엄격 모드 (더 많은 Clarification)
         */
        val STRICT = IntelligenceConfig(
            exactThreshold = 0.90,
            fuzzyThreshold = 0.70,
            gapThreshold = 0.20,
            domainRelevanceThreshold = 0.80
        )

        /**
         * 느슨한 모드 (자동 선택 우선)
         */
        val LENIENT = IntelligenceConfig(
            exactThreshold = 0.75,
            fuzzyThreshold = 0.55,
            gapThreshold = 0.10,
            domainRelevanceThreshold = 0.60
        )

        /**
         * 테스트 모드 (캐시/LLM 비활성화)
         */
        val TEST = IntelligenceConfig(
            cacheEnabled = false,
            enableNano = false,
            enableBigLLM = false,
            enablePolicyRAG = false,
            metricsEnabled = false
        )

        /**
         * Fast-only 모드 (Embedding만 사용)
         */
        val FAST_ONLY = IntelligenceConfig(
            enableNano = false,
            enableBigLLM = false,
            enablePolicyRAG = false
        )
    }
}

/**
 * Fallback 설정 (Big LLM/Nano 실패 시)
 *
 * ## Confidence 감소 규칙
 * Big LLM 실패 시 Fast Layer 결과를 반환하되, confidence를 감소시킴:
 * - `fallbackConfidence = originalScore * confidenceMultiplier`
 *
 * ## RoutingSignal 규칙
 * - score >= ambiguousThreshold → NORMAL (낮은 신뢰도지만 진행)
 * - score < ambiguousThreshold → AMBIGUOUS (재질문 유도)
 *
 * ## 예시
 * ```
 * originalScore = 0.75, multiplier = 0.8
 * → fallbackConfidence = 0.6
 * → 0.6 < 0.65 (ambiguousThreshold)
 * → routingSignal = AMBIGUOUS
 * ```
 *
 * @property confidenceMultiplier Fallback 시 confidence 곱셈 계수 (0.0-1.0)
 * @property ambiguousThreshold 이 값 미만이면 AMBIGUOUS 반환
 * @property minConfidence 최소 confidence (이 값 미만이면 무조건 AMBIGUOUS)
 *
 * @since 2.0.0
 */
data class FallbackConfig(
    val confidenceMultiplier: Double = 0.8,
    val ambiguousThreshold: Double = 0.65,
    val minConfidence: Double = 0.3
) {
    init {
        require(confidenceMultiplier in 0.0..1.0) { "confidenceMultiplier must be 0.0-1.0" }
        require(ambiguousThreshold in 0.0..1.0) { "ambiguousThreshold must be 0.0-1.0" }
        require(minConfidence in 0.0..1.0) { "minConfidence must be 0.0-1.0" }
    }

    /**
     * Fallback confidence 및 routingSignal 계산
     *
     * @param originalScore 원본 점수 (Fast Layer 결과)
     * @return (adjustedConfidence, isAmbiguous)
     */
    fun calculateFallback(originalScore: Double): FallbackDecisionResult {
        val adjustedConfidence = (originalScore * confidenceMultiplier).coerceAtLeast(minConfidence)
        val isAmbiguous = adjustedConfidence < ambiguousThreshold

        return FallbackDecisionResult(
            confidence = adjustedConfidence,
            isAmbiguous = isAmbiguous,
            originalScore = originalScore,
            multiplierApplied = confidenceMultiplier
        )
    }

    companion object {
        /**
         * 기본 설정
         * - 20% confidence 감소
         * - 0.65 미만이면 AMBIGUOUS
         */
        val DEFAULT = FallbackConfig()

        /**
         * 보수적 설정 (더 많은 AMBIGUOUS)
         */
        val CONSERVATIVE = FallbackConfig(
            confidenceMultiplier = 0.7,
            ambiguousThreshold = 0.70
        )

        /**
         * 관대한 설정 (대부분 NORMAL 유지)
         */
        val LENIENT = FallbackConfig(
            confidenceMultiplier = 0.9,
            ambiguousThreshold = 0.50
        )
    }
}

/**
 * Fallback 계산 결과 (Big LLM/Nano 실패 시)
 *
 * @property confidence 조정된 confidence
 * @property isAmbiguous AMBIGUOUS 여부
 * @property originalScore 원본 점수
 * @property multiplierApplied 적용된 배수
 *
 * @since 2.0.0
 */
data class FallbackDecisionResult(
    val confidence: Double,
    val isAmbiguous: Boolean,
    val originalScore: Double,
    val multiplierApplied: Double
) {
    /**
     * 해당하는 RoutingSignal 반환
     */
    fun toRoutingSignal(): RoutingSignal =
        if (isAmbiguous) RoutingSignal.AMBIGUOUS else RoutingSignal.NORMAL
}
