package io.github.noailabs.spice.intelligence.spi

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.ClarificationRequest
import io.github.noailabs.spice.intelligence.PolicyHint

/**
 * Nano Validator SPI (Intelligence Layer v2)
 *
 * Fast Layer의 시멘틱 매칭 결과를 검증하는 경량 LLM 인터페이스.
 * GPT-5-nano 또는 동급의 작은 모델 사용.
 *
 * ## 역할
 * - Fast Layer 결과 검증 (0.65-0.85 구간)
 * - 모호한 케이스 Clarification 생성
 * - 간단한 Off-domain 탐지
 *
 * ## Nano Path 상태 전이
 * ```
 * [Nano Input] → [Validation]
 *                    ├─ OVERRIDE: canonical 확정 → DECISION 진행
 *                    ├─ CLARIFY: Clarification 필요 → HITL 재질문
 *                    ├─ DELEGATE: 복잡한 케이스 → Big LLM
 *                    └─ FALLBACK: Nano 실패 → Big LLM
 * ```
 *
 * ## Downstream 행동 테이블 (Phase 3 구현 계약)
 * ```
 * ┌─────────────┬──────────────────────────────────────────────────────────────┐
 * │ 상태        │ Downstream 행동                                              │
 * ├─────────────┼──────────────────────────────────────────────────────────────┤
 * │ OVERRIDE    │ • aiCanonical = result.canonical                             │
 * │             │ • routingSignal = NORMAL                                     │
 * │             │ • decisionSource = NANO_LLM                                  │
 * │             │ • DECISION 노드로 canonical 전달                             │
 * │             │ • 캐시 저장 (CacheLayer.NANO)                                │
 * ├─────────────┼──────────────────────────────────────────────────────────────┤
 * │ CLARIFY     │ • routingSignal = AMBIGUOUS                                  │
 * │             │ • clarificationRequest 생성                                  │
 * │             │ • HITL Selection 노드로 재질문 emit                          │
 * │             │ • session.clarifyAttempts++                                  │
 * │             │ • 캐시 저장 안 함 (모호한 상태)                              │
 * ├─────────────┼──────────────────────────────────────────────────────────────┤
 * │ DELEGATE    │ • OneShotReasoner (Stage3) 호출                              │
 * │             │ • Nano 결과는 Stage3 컨텍스트로 전달                         │
 * │             │ • enableBigLayer=false면 CLARIFY로 fallback                  │
 * │             │ • 캐시 저장 안 함 (위임)                                     │
 * ├─────────────┼──────────────────────────────────────────────────────────────┤
 * │ FALLBACK    │ • OneShotReasoner (Stage3) 호출 (Nano 오류 복구)             │
 * │             │ • enableBigLayer=false면 Fast Layer 결과로 fallback          │
 * │             │ • 로그: nano_fallback_reason 기록                            │
 * │             │ • 캐시 저장 안 함                                            │
 * └─────────────┴──────────────────────────────────────────────────────────────┘
 * ```
 *
 * ## Delegate 조건
 * - confidence < 0.5
 * - 복잡한 의도 감지
 * - Policy 위반 의심
 *
 * ## Clean Architecture
 * - spice-core: 이 인터페이스 정의 (포트)
 * - kai-core: GPT-5-nano 구현체 (어댑터)
 *
 * @since 2.0.0
 */
interface NanoValidator {

    /**
     * Fast Layer 결과 검증
     *
     * @param request 검증 요청
     * @return 검증 결과
     */
    suspend fun validate(request: NanoValidationRequest): SpiceResult<NanoValidationResult>

    /**
     * 모델 ID
     */
    val modelId: String

    /**
     * 최대 입력 토큰
     */
    val maxInputTokens: Int
        get() = 1024
}

/**
 * Nano 검증 요청
 *
 * @property utterance 사용자 발화
 * @property suggestedCanonical Fast Layer가 제안한 canonical
 * @property suggestedConfidence Fast Layer confidence
 * @property candidates 후보 목록 (상위 N개)
 * @property gap 1위-2위 격차
 * @property policyHints Policy-RAG 힌트
 * @property workflowId 워크플로우 ID
 *
 * @since 2.0.0
 */
data class NanoValidationRequest(
    val utterance: String,
    val suggestedCanonical: String?,
    val suggestedConfidence: Double,
    val candidates: List<ScoredCandidate>,
    val gap: Double,
    val policyHints: List<PolicyHint> = emptyList(),
    val workflowId: String? = null
)

/**
 * Nano 검증 결과
 *
 * ## 상태 정의
 * - OVERRIDE: canonical 확정 (검증 통과)
 * - CLARIFY: Clarification 필요 (모호함)
 * - DELEGATE: Big LLM 위임 (복잡함)
 * - FALLBACK: Nano 실패 (오류 또는 timeout)
 *
 * @property status 검증 상태
 * @property canonical 확정된 canonical (OVERRIDE 시)
 * @property confidence 최종 confidence
 * @property clarificationRequest Clarification 요청 (CLARIFY 시)
 * @property delegateReason 위임 사유 (DELEGATE 시)
 * @property reasoning 판단 근거
 * @property tokensUsed 사용된 토큰 수
 * @property latencyMs 처리 시간
 *
 * @since 2.0.0
 */
data class NanoValidationResult(
    val status: NanoValidationStatus,
    val canonical: String? = null,
    val confidence: Double,
    val clarificationRequest: ClarificationRequest? = null,
    val delegateReason: String? = null,
    val reasoning: String? = null,
    val tokensUsed: Int? = null,
    val latencyMs: Long = 0
) {
    init {
        require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
        when (status) {
            NanoValidationStatus.OVERRIDE -> require(canonical != null) { "OVERRIDE requires canonical" }
            NanoValidationStatus.CLARIFY -> require(clarificationRequest != null) { "CLARIFY requires clarificationRequest" }
            NanoValidationStatus.DELEGATE -> require(delegateReason != null) { "DELEGATE requires delegateReason" }
            NanoValidationStatus.FALLBACK -> {} // no specific requirement
        }
    }

    companion object {
        /**
         * OVERRIDE 결과 생성
         */
        fun override(
            canonical: String,
            confidence: Double,
            reasoning: String? = null,
            tokensUsed: Int? = null,
            latencyMs: Long = 0
        ) = NanoValidationResult(
            status = NanoValidationStatus.OVERRIDE,
            canonical = canonical,
            confidence = confidence,
            reasoning = reasoning,
            tokensUsed = tokensUsed,
            latencyMs = latencyMs
        )

        /**
         * CLARIFY 결과 생성
         */
        fun clarify(
            request: ClarificationRequest,
            confidence: Double = 0.5,
            reasoning: String? = null,
            tokensUsed: Int? = null,
            latencyMs: Long = 0
        ) = NanoValidationResult(
            status = NanoValidationStatus.CLARIFY,
            clarificationRequest = request,
            confidence = confidence,
            reasoning = reasoning,
            tokensUsed = tokensUsed,
            latencyMs = latencyMs
        )

        /**
         * DELEGATE 결과 생성
         */
        fun delegate(
            reason: String,
            confidence: Double = 0.3,
            reasoning: String? = null,
            tokensUsed: Int? = null,
            latencyMs: Long = 0
        ) = NanoValidationResult(
            status = NanoValidationStatus.DELEGATE,
            delegateReason = reason,
            confidence = confidence,
            reasoning = reasoning,
            tokensUsed = tokensUsed,
            latencyMs = latencyMs
        )

        /**
         * FALLBACK 결과 생성
         */
        fun fallback(
            reason: String,
            latencyMs: Long = 0
        ) = NanoValidationResult(
            status = NanoValidationStatus.FALLBACK,
            confidence = 0.0,
            delegateReason = reason,
            reasoning = "Nano fallback: $reason",
            latencyMs = latencyMs
        )
    }
}

/**
 * Nano 검증 상태
 *
 * @since 2.0.0
 */
enum class NanoValidationStatus {
    /**
     * Canonical 확정 (검증 통과)
     *
     * Fast Layer의 제안을 승인하거나 수정된 canonical 반환.
     * → DECISION 노드로 진행
     */
    OVERRIDE,

    /**
     * Clarification 필요 (모호함)
     *
     * 사용자에게 재질문 필요.
     * → HITL 재질문
     */
    CLARIFY,

    /**
     * Big LLM 위임 (복잡함)
     *
     * Nano로 처리하기 어려운 케이스.
     * 조건:
     * - confidence < 0.5
     * - 복잡한 의도 감지
     * - Policy 위반 의심
     * - Intent shift 의심
     *
     * → OneShotReasoner (Big LLM)
     */
    DELEGATE,

    /**
     * Nano 실패 (오류/timeout)
     *
     * Nano 처리 자체가 실패한 경우.
     * → Big LLM으로 fallback
     */
    FALLBACK
}

/**
 * Nano Validator 팩토리
 *
 * @since 2.0.0
 */
object NanoValidators {

    /**
     * 항상 OVERRIDE 반환 (테스트용)
     */
    fun alwaysOverride(canonical: String = "test-canonical"): NanoValidator = object : NanoValidator {
        override val modelId = "always-override-test"
        override suspend fun validate(request: NanoValidationRequest) = SpiceResult.success(
            NanoValidationResult.override(canonical, 0.9, "Test: always override")
        )
    }

    /**
     * 항상 Fast Layer 결과 승인 (테스트용)
     */
    fun passThrough(): NanoValidator = object : NanoValidator {
        override val modelId = "pass-through-test"
        override suspend fun validate(request: NanoValidationRequest) = SpiceResult.success(
            if (request.suggestedCanonical != null) {
                NanoValidationResult.override(
                    request.suggestedCanonical,
                    request.suggestedConfidence,
                    "Pass-through: accepting Fast Layer suggestion"
                )
            } else {
                NanoValidationResult.delegate(
                    "No canonical suggested",
                    0.3,
                    "Pass-through: no suggestion to validate"
                )
            }
        )
    }

    /**
     * 항상 DELEGATE 반환 (테스트용)
     */
    fun alwaysDelegate(): NanoValidator = object : NanoValidator {
        override val modelId = "always-delegate-test"
        override suspend fun validate(request: NanoValidationRequest) = SpiceResult.success(
            NanoValidationResult.delegate("Test: always delegate to Big LLM")
        )
    }
}
