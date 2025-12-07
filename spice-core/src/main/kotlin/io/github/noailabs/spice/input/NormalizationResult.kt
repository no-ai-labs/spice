package io.github.noailabs.spice.input

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 정규화 결과의 라우팅 신호
 *
 * 정규화 후 워크플로우 엔진이 취해야 할 행동을 지정.
 *
 * @since 1.7.0
 */
@Serializable
enum class RoutingSignal {
    /**
     * 정상 진행 - 다음 노드로 이동
     */
    NORMAL,

    /**
     * 워크플로우 전환 - Intent Drift 감지
     * metadata에 newWorkflowId 포함
     */
    SWITCH_WORKFLOW,

    /**
     * 재질문 필요 - 입력 불명확 또는 제약 위반
     * metadata에 clarificationRequest 포함
     */
    CLARIFY,

    /**
     * 상담사 전환 - 자동 처리 불가
     * metadata에 escalationReason 포함
     */
    ESCALATE,

    /**
     * 유효하지 않은 입력 - 처리 불가
     */
    INVALID
}

/**
 * 파싱된 값 - 도메인 무관 기본 타입
 *
 * 도메인별 확장(GuestCountValue, BookingIdValue 등)은 애플리케이션에서 별도 정의.
 * ParsedValue → DomainParsedValue 변환은 DomainPostProcessor에서 처리.
 *
 * @since 1.7.0
 */
@Serializable
sealed class ParsedValue {
    /**
     * 텍스트 값
     */
    @Serializable
    data class TextValue(val text: String) : ParsedValue()

    /**
     * 숫자 값 (정수/실수 공용)
     */
    @Serializable
    data class NumberValue(val value: Double) : ParsedValue() {
        fun toInt(): Int = value.toInt()
        fun toLong(): Long = value.toLong()
    }

    /**
     * Boolean 값
     */
    @Serializable
    data class BooleanValue(val value: Boolean) : ParsedValue()

    /**
     * 날짜 값 (ISO format: YYYY-MM-DD)
     */
    @Serializable
    data class DateValue(val date: String) : ParsedValue()

    /**
     * 날짜+시간 값 (ISO format: YYYY-MM-DDTHH:MM:SS)
     */
    @Serializable
    data class DateTimeValue(val datetime: String) : ParsedValue()

    /**
     * 단일 선택 값
     */
    @Serializable
    data class EnumValue(val selectedId: String) : ParsedValue()

    /**
     * 다중 선택 값
     */
    @Serializable
    data class MultiEnumValue(val selectedIds: List<String>) : ParsedValue() {
        /**
         * 쉼표 구분 문자열로 변환
         */
        fun toCanonical(): String = selectedIds.joinToString(",")
    }
}

/**
 * 정규화 결과
 *
 * PatternMiner 체인 → ResultFusionLayer → ValidatorChain을 거친 최종 결과.
 *
 * @property canonical 정규화된 값 (DECISION 노드가 사용)
 * @property confidence 신뢰도 (0.0 ~ 1.0)
 * @property routingSignal 라우팅 신호
 * @property fieldType 필드 타입
 * @property rawInput 원본 입력
 * @property parsedValue 파싱된 구조화된 값 (선택적)
 * @property minerUsed 사용된 Miner ID
 * @property reasoning 판단 이유 (디버깅용)
 * @property metadata 추가 메타데이터
 *
 * @since 1.7.0
 */
@Serializable
data class NormalizationResult(
    val canonical: String,
    val confidence: Double,
    val routingSignal: RoutingSignal,
    val fieldType: FieldType,
    val rawInput: String,
    val parsedValue: ParsedValue? = null,
    val minerUsed: String? = null,
    val reasoning: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(jsonString: String): NormalizationResult = json.decodeFromString(jsonString)

        /**
         * 정상 결과 생성
         */
        fun success(
            canonical: String,
            confidence: Double,
            fieldType: FieldType,
            rawInput: String,
            parsedValue: ParsedValue? = null,
            minerUsed: String? = null,
            reasoning: String? = null
        ) = NormalizationResult(
            canonical = canonical,
            confidence = confidence,
            routingSignal = RoutingSignal.NORMAL,
            fieldType = fieldType,
            rawInput = rawInput,
            parsedValue = parsedValue,
            minerUsed = minerUsed,
            reasoning = reasoning
        )

        /**
         * 재질문 필요 결과 생성
         */
        fun clarify(
            rawInput: String,
            fieldType: FieldType,
            reason: String,
            clarificationRequest: String? = null
        ) = NormalizationResult(
            canonical = rawInput,
            confidence = 0.0,
            routingSignal = RoutingSignal.CLARIFY,
            fieldType = fieldType,
            rawInput = rawInput,
            reasoning = reason,
            metadata = if (clarificationRequest != null) {
                mapOf("clarificationRequest" to clarificationRequest)
            } else emptyMap()
        )

        /**
         * 워크플로우 전환 결과 생성
         */
        fun switchWorkflow(
            rawInput: String,
            fieldType: FieldType,
            newWorkflowId: String,
            reason: String
        ) = NormalizationResult(
            canonical = rawInput,
            confidence = 0.0,
            routingSignal = RoutingSignal.SWITCH_WORKFLOW,
            fieldType = fieldType,
            rawInput = rawInput,
            reasoning = reason,
            metadata = mapOf("newWorkflowId" to newWorkflowId)
        )

        /**
         * 상담사 전환 결과 생성
         */
        fun escalate(
            rawInput: String,
            fieldType: FieldType,
            reason: String
        ) = NormalizationResult(
            canonical = rawInput,
            confidence = 0.0,
            routingSignal = RoutingSignal.ESCALATE,
            fieldType = fieldType,
            rawInput = rawInput,
            reasoning = reason
        )
    }

    fun toJson(): String = json.encodeToString(this)

    /**
     * 성공 여부 확인 (NORMAL 신호 + 신뢰도 > 0)
     */
    val isSuccess: Boolean
        get() = routingSignal == RoutingSignal.NORMAL && confidence > 0

    /**
     * 재질문 필요 여부
     */
    val needsClarification: Boolean
        get() = routingSignal == RoutingSignal.CLARIFY

    /**
     * 워크플로우 전환 필요 여부
     */
    val needsWorkflowSwitch: Boolean
        get() = routingSignal == RoutingSignal.SWITCH_WORKFLOW

    /**
     * 상담사 전환 필요 여부
     */
    val needsEscalation: Boolean
        get() = routingSignal == RoutingSignal.ESCALATE
}
