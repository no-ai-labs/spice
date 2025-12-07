package io.github.noailabs.spice.input.spi

import io.github.noailabs.spice.input.*
import kotlinx.serialization.Serializable

/**
 * 패턴 마이너 SPI
 *
 * 입력 텍스트에서 특정 패턴을 감지하고 정규화된 값으로 변환.
 * 각 Miner는 지원하는 FieldType에 대해서만 동작.
 *
 * ## 구현 원칙
 * 1. **정규화만 담당**: 값 추출만, 제약 검증은 ValidatorChain에서
 * 2. **빠른 실패**: 패턴 미매칭 시 즉시 null 반환
 * 3. **신뢰도 정직**: 확신이 없으면 낮은 confidence 반환
 * 4. **부작용 금지**: 순수 함수로 구현
 *
 * ## 구현 예시
 * ```kotlin
 * class NumberPatternMiner : PatternMiner {
 *     override val id = "number-pattern"
 *     override val supportedTypes = setOf(FieldType.ENUM, FieldType.NUMBER)
 *     override val priority = 10
 *
 *     override suspend fun mine(input, spec, context): PartialResult? {
 *         val index = parseNumber(input) ?: return null
 *         return PartialResult(
 *             canonical = spec.enumOptions[index].id,
 *             confidence = 0.95,
 *             minerId = id
 *         )
 *     }
 * }
 * ```
 *
 * @since 1.7.0
 */
interface PatternMiner {
    /**
     * Miner 고유 ID
     * 로깅 및 디버깅에 사용
     */
    val id: String

    /**
     * 지원하는 필드 타입
     * 이 타입들에 대해서만 mine() 호출됨
     */
    val supportedTypes: Set<FieldType>

    /**
     * 실행 우선순위 (낮을수록 먼저 실행)
     * 동일 우선순위는 등록 순서
     */
    val priority: Int

    /**
     * 패턴 마이닝 실행
     *
     * @param input 원본 입력 텍스트
     * @param spec 필드 명세
     * @param context 정규화 컨텍스트
     * @return 매칭 결과 (null이면 다음 Miner로)
     */
    suspend fun mine(
        input: String,
        spec: InputFieldSpec,
        context: NormalizationContext
    ): PartialResult?

    /**
     * 이 Miner가 해당 스펙을 처리할 수 있는지 확인
     */
    fun supports(spec: InputFieldSpec): Boolean = spec.fieldType in supportedTypes
}

/**
 * Miner의 부분 결과
 *
 * 여러 Miner 결과가 ResultFusionLayer에서 병합되어 최종 NormalizationResult가 됨.
 *
 * @property canonical 정규화된 값
 * @property confidence 신뢰도 (0.0 ~ 1.0)
 * @property parsedValue 파싱된 구조화된 값 (선택적)
 * @property routingSignal 라우팅 신호 (기본 NORMAL)
 * @property reasoning 판단 이유
 * @property minerId 이 결과를 생성한 Miner ID
 *
 * @since 1.7.0
 */
@Serializable
data class PartialResult(
    val canonical: String,
    val confidence: Double,
    val parsedValue: ParsedValue? = null,
    val routingSignal: RoutingSignal = RoutingSignal.NORMAL,
    val reasoning: String? = null,
    val minerId: String
) {
    companion object {
        /**
         * 높은 신뢰도 결과 생성
         */
        fun highConfidence(
            canonical: String,
            minerId: String,
            parsedValue: ParsedValue? = null,
            reasoning: String? = null
        ) = PartialResult(
            canonical = canonical,
            confidence = 0.95,
            parsedValue = parsedValue,
            minerId = minerId,
            reasoning = reasoning
        )

        /**
         * 중간 신뢰도 결과 생성
         */
        fun mediumConfidence(
            canonical: String,
            minerId: String,
            parsedValue: ParsedValue? = null,
            reasoning: String? = null
        ) = PartialResult(
            canonical = canonical,
            confidence = 0.75,
            parsedValue = parsedValue,
            minerId = minerId,
            reasoning = reasoning
        )

        /**
         * 워크플로우 전환 결과 생성
         */
        fun switchWorkflow(
            canonical: String,
            minerId: String,
            newWorkflowId: String,
            reasoning: String? = null
        ) = PartialResult(
            canonical = canonical,
            confidence = 0.95,
            routingSignal = RoutingSignal.SWITCH_WORKFLOW,
            minerId = minerId,
            reasoning = reasoning ?: "Intent drift detected: switching to $newWorkflowId"
        )
    }

    /**
     * 높은 신뢰도인지 확인 (>=0.85)
     */
    val isHighConfidence: Boolean
        get() = confidence >= 0.85

    /**
     * 중간 신뢰도인지 확인 (0.65~0.85)
     */
    val isMediumConfidence: Boolean
        get() = confidence in 0.65..0.85

    /**
     * 낮은 신뢰도인지 확인 (<0.65)
     */
    val isLowConfidence: Boolean
        get() = confidence < 0.65

    /**
     * 정상 라우팅인지 확인
     */
    val isNormalRouting: Boolean
        get() = routingSignal == RoutingSignal.NORMAL
}
