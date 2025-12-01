package io.github.noailabs.spice.intelligence.impl

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.intelligence.CompositeDecision
import io.github.noailabs.spice.intelligence.DecisionSource
import io.github.noailabs.spice.intelligence.RoutingSignal
import io.github.noailabs.spice.intelligence.spi.OneShotReasoner
import io.github.noailabs.spice.intelligence.spi.OneShotRequest

/**
 * InMemory One-Shot Reasoner (테스트용)
 *
 * 테스트/개발 환경에서 사용하는 stub 구현체.
 * 미리 설정된 응답을 반환하거나 기본 동작을 수행.
 *
 * ## 사용 예시
 * ```kotlin
 * val reasoner = InMemoryOneShotReasoner()
 *
 * // stub 응답 설정
 * reasoner.stubResponse("취소해줘", CompositeDecision.fastPath("cancel", 0.9, 0.8))
 *
 * // 또는 기본 응답 설정
 * reasoner.setDefaultResponse(CompositeDecision.offDomain("테스트 off-domain"))
 * ```
 *
 * @since 2.0.0
 */
class InMemoryOneShotReasoner(
    override val modelId: String = "in-memory-test",
    override val maxInputTokens: Int = 4096
) : OneShotReasoner {

    private val stubResponses = mutableMapOf<String, CompositeDecision>()
    private var defaultResponse: CompositeDecision? = null
    private var callCount = 0

    override suspend fun reason(request: OneShotRequest): SpiceResult<CompositeDecision> {
        callCount++

        // 1. stub 응답 확인
        val normalizedUtterance = request.utterance.lowercase().trim()
        stubResponses[normalizedUtterance]?.let {
            return SpiceResult.success(it.copy(latencyMs = 1))
        }

        // 2. 기본 응답 반환
        defaultResponse?.let {
            return SpiceResult.success(it.copy(latencyMs = 1))
        }

        // 3. 기본 동작: pass-through (canonical 없음)
        return SpiceResult.success(
            CompositeDecision(
                domainRelevance = 0.5,
                aiCanonical = null,
                confidence = 0.5,
                reasoning = "InMemoryOneShotReasoner: no stub configured",
                decisionSource = DecisionSource.REASONER,
                routingSignal = RoutingSignal.DELEGATE_TO_LLM,
                latencyMs = 1
            )
        )
    }

    /**
     * 특정 발화에 대한 stub 응답 설정
     */
    fun stubResponse(utterance: String, decision: CompositeDecision) {
        stubResponses[utterance.lowercase().trim()] = decision
    }

    /**
     * 기본 응답 설정 (stub이 없을 때 사용)
     */
    fun setDefaultResponse(decision: CompositeDecision) {
        defaultResponse = decision
    }

    /**
     * 모든 stub 초기화
     */
    fun reset() {
        stubResponses.clear()
        defaultResponse = null
        callCount = 0
    }

    /**
     * 호출 횟수 조회
     */
    fun getCallCount(): Int = callCount

    companion object {
        /**
         * 항상 Fast Path 반환하는 인스턴스
         */
        fun alwaysFastPath(canonical: String, confidence: Double = 0.9) =
            InMemoryOneShotReasoner().apply {
                setDefaultResponse(CompositeDecision.fastPath(canonical, confidence, 0.9))
            }

        /**
         * 항상 Off-domain 반환하는 인스턴스
         */
        fun alwaysOffDomain(reason: String = "Test off-domain") =
            InMemoryOneShotReasoner().apply {
                setDefaultResponse(CompositeDecision.offDomain(reason))
            }

        /**
         * 항상 실패하는 인스턴스 (에러 테스트용)
         */
        fun alwaysFails(errorMessage: String = "Test error") =
            object : OneShotReasoner {
                override val modelId = "always-fails"
                override suspend fun reason(request: OneShotRequest): SpiceResult<CompositeDecision> =
                    SpiceResult.failure(SpiceError.executionError(errorMessage))
            }
    }
}
