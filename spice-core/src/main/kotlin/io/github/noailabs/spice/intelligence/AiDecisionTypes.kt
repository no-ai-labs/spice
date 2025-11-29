package io.github.noailabs.spice.intelligence

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * AI 의사결정 결과
 *
 * DecisionEngine의 판단 결과를 담는 통합 타입.
 * sLM, Embedding, Nano LLM 등 다양한 출처의 결정을 하나의 타입으로 통합.
 *
 * ## 사용 예시
 * ```kotlin
 * val result = decisionEngine.decide(utterance, options, context)
 * when (result.type) {
 *     DecisionType.YES_NO -> println("Yes/No: ${result.yes}")
 *     DecisionType.OPTION_SELECTED -> println("Selected: ${result.optionId}")
 *     DecisionType.NEED_CLARIFICATION -> handleClarification(result.clarificationRequest!!)
 *     // ...
 * }
 * ```
 *
 * @property type 결정 유형
 * @property yes YES_NO 타입일 때 결과 (true=예, false=아니오)
 * @property optionIndex OPTION_SELECTED 타입일 때 선택된 옵션 인덱스 (0-based, 레거시 호환)
 * @property optionId OPTION_SELECTED 타입일 때 선택된 옵션 ID
 * @property selectionResult MULTI_OPTION_SELECTED 또는 QUANTITY_SELECTED 타입일 때 통합 선택 결과
 * @property clarificationRequest NEED_CLARIFICATION 타입일 때 Clarification 요청
 * @property newWorkflowId NEED_REORCHESTRATION 타입일 때 전환할 워크플로우 ID
 * @property confidence 결정 신뢰도 (0.0~1.0)
 * @property reasoning 결정 근거 (디버깅/로깅용)
 * @property decisionSource 결정 출처 (SLM, EMBEDDING, NANO 등)
 *
 * @since 1.1.0
 */
@Serializable
data class AiDecisionResult(
    val type: DecisionType,
    val yes: Boolean? = null,
    val optionIndex: Int? = null,
    val optionId: String? = null,
    val selectionResult: SelectionResult? = null,
    val clarificationRequest: ClarificationRequest? = null,
    val newWorkflowId: String? = null,
    val confidence: Double = 1.0,
    val reasoning: String? = null,
    val decisionSource: DecisionSource
) {
    /**
     * 결정이 확실한지 여부 (confidence >= 0.85)
     */
    val isCertain: Boolean
        get() = confidence >= 0.85

    /**
     * 결정이 모호한지 여부 (0.5 <= confidence < 0.65)
     */
    val isAmbiguous: Boolean
        get() = confidence in 0.5..<0.65

    /**
     * Clarification이 필요한지 여부
     */
    val needsClarification: Boolean
        get() = type == DecisionType.NEED_CLARIFICATION || isAmbiguous

    /**
     * 터미널 결정인지 여부 (더 이상 진행 불가)
     */
    val isTerminal: Boolean
        get() = type in setOf(
            DecisionType.DELEGATE_TO_LLM,
            DecisionType.NEED_REORCHESTRATION
        )

    companion object {
        /**
         * Yes 결정 생성
         */
        fun yes(
            confidence: Double = 0.9,
            source: DecisionSource = DecisionSource.SLM,
            reasoning: String? = null
        ) = AiDecisionResult(
            type = DecisionType.YES_NO,
            yes = true,
            confidence = confidence,
            decisionSource = source,
            reasoning = reasoning
        )

        /**
         * No 결정 생성
         */
        fun no(
            confidence: Double = 0.9,
            source: DecisionSource = DecisionSource.SLM,
            reasoning: String? = null
        ) = AiDecisionResult(
            type = DecisionType.YES_NO,
            yes = false,
            confidence = confidence,
            decisionSource = source,
            reasoning = reasoning
        )

        /**
         * 단일 옵션 선택 결정 생성
         */
        fun optionSelected(
            optionIndex: Int,
            optionId: String,
            confidence: Double = 0.85,
            source: DecisionSource = DecisionSource.EMBEDDING,
            reasoning: String? = null
        ) = AiDecisionResult(
            type = DecisionType.OPTION_SELECTED,
            optionIndex = optionIndex,
            optionId = optionId,
            confidence = confidence,
            decisionSource = source,
            reasoning = reasoning
        )

        /**
         * 다중 옵션 선택 결정 생성
         */
        fun multiOptionSelected(
            selectionResult: SelectionResult,
            confidence: Double = 0.85,
            source: DecisionSource = DecisionSource.EMBEDDING,
            reasoning: String? = null
        ) = AiDecisionResult(
            type = DecisionType.MULTI_OPTION_SELECTED,
            selectionResult = selectionResult,
            confidence = confidence,
            decisionSource = source,
            reasoning = reasoning
        )

        /**
         * 수량 선택 결정 생성
         */
        fun quantitySelected(
            selectionResult: SelectionResult,
            confidence: Double = 1.0,
            source: DecisionSource = DecisionSource.HITL,
            reasoning: String? = null
        ) = AiDecisionResult(
            type = DecisionType.QUANTITY_SELECTED,
            selectionResult = selectionResult,
            confidence = confidence,
            decisionSource = source,
            reasoning = reasoning
        )

        /**
         * Clarification 필요 결정 생성
         */
        fun needClarification(
            clarificationRequest: ClarificationRequest,
            confidence: Double = 0.5,
            source: DecisionSource = DecisionSource.EMBEDDING,
            reasoning: String? = null
        ) = AiDecisionResult(
            type = DecisionType.NEED_CLARIFICATION,
            clarificationRequest = clarificationRequest,
            confidence = confidence,
            decisionSource = source,
            reasoning = reasoning
        )

        /**
         * LLM 위임 결정 생성
         */
        fun delegateToLLM(
            reasoning: String? = null,
            confidence: Double = 0.3
        ) = AiDecisionResult(
            type = DecisionType.DELEGATE_TO_LLM,
            confidence = confidence,
            decisionSource = DecisionSource.FALLBACK,
            reasoning = reasoning ?: "Confidence too low, delegating to LLM"
        )

        /**
         * 워크플로우 전환 결정 생성
         */
        fun reorchestrate(
            newWorkflowId: String,
            reasoning: String? = null
        ) = AiDecisionResult(
            type = DecisionType.NEED_REORCHESTRATION,
            newWorkflowId = newWorkflowId,
            confidence = 1.0,
            decisionSource = DecisionSource.REANALYSIS,
            reasoning = reasoning
        )
    }
}

/**
 * 범용 선택 결과 - Multi-Select + Quantity 통합
 *
 * 핵심 통찰: 멀티셀렉과 수량선택을 하나로 통합하면 옵션 변경 문제 해결!
 *
 * ## 선택 유형 조합
 * | selectedIds 개수 | quantities | SelectionType |
 * |-----------------|------------|---------------|
 * | 0               | -          | NONE          |
 * | 1               | null       | SINGLE        |
 * | 1               | not null   | QUANTITY      |
 * | > 1             | null       | MULTI_SELECT  |
 * | > 1             | not null   | MULTI_QUANTITY|
 *
 * @property selectedIds 선택된 ID들
 * @property quantities ID별 수량 (null이면 수량 없음)
 * @property metadata 추가 정보
 *
 * @since 1.1.0
 */
@Serializable
data class SelectionResult(
    val selectedIds: List<String>,
    val quantities: Map<String, Int>? = null,
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * 선택 유형 자동 추론
     */
    val selectionType: SelectionType
        get() = when {
            selectedIds.isEmpty() -> SelectionType.NONE
            quantities == null && selectedIds.size == 1 -> SelectionType.SINGLE
            quantities == null && selectedIds.size > 1 -> SelectionType.MULTI_SELECT
            quantities != null && selectedIds.size == 1 -> SelectionType.QUANTITY
            quantities != null && selectedIds.size > 1 -> SelectionType.MULTI_QUANTITY
            else -> SelectionType.SINGLE
        }

    /**
     * 선택된 항목이 있는지 여부
     */
    val hasSelection: Boolean
        get() = selectedIds.isNotEmpty()

    /**
     * 첫 번째 선택 ID (단일 선택 호환)
     */
    val firstSelectedId: String?
        get() = selectedIds.firstOrNull()

    /**
     * 첫 번째 항목의 수량 (단일 수량 선택 호환)
     */
    val firstQuantity: Int?
        get() = firstSelectedId?.let { quantities?.get(it) }

    /**
     * 총 선택 개수
     */
    val totalCount: Int
        get() = quantities?.values?.sum() ?: selectedIds.size

    /**
     * 특정 ID의 수량 조회
     */
    fun quantityOf(id: String): Int = quantities?.get(id) ?: 0

    /**
     * ID가 선택되었는지 여부
     */
    fun isSelected(id: String): Boolean = id in selectedIds

    companion object {
        /**
         * 빈 선택 결과
         */
        val EMPTY = SelectionResult(emptyList())

        /**
         * 단일 선택
         */
        fun single(id: String) = SelectionResult(listOf(id))

        /**
         * 다중 선택
         */
        fun multiple(ids: List<String>) = SelectionResult(ids)

        /**
         * 단일 수량 선택
         */
        fun quantity(id: String, quantity: Int) = SelectionResult(
            selectedIds = listOf(id),
            quantities = mapOf(id to quantity)
        )

        /**
         * 다중 수량 선택 (옵션 변경용!)
         */
        fun multiQuantity(selections: Map<String, Int>) = SelectionResult(
            selectedIds = selections.keys.toList(),
            quantities = selections
        )

        /**
         * 기존 선택에서 변경 적용
         */
        fun fromChanges(
            current: Map<String, Int>,
            changes: Map<String, Int>
        ): SelectionResult {
            val merged = current.toMutableMap()
            changes.forEach { (id, qty) ->
                if (qty <= 0) merged.remove(id)
                else merged[id] = qty
            }
            return SelectionResult(
                selectedIds = merged.keys.toList(),
                quantities = merged
            )
        }
    }
}

/**
 * 선택 유형
 */
@Serializable
enum class SelectionType {
    /** 선택 없음 */
    NONE,

    /** 단일 선택 (기존 OPTION_SELECTED) */
    SINGLE,

    /** 다중 선택 (quantities = null) */
    MULTI_SELECT,

    /** 수량 선택 (단일 항목 + 수량) */
    QUANTITY,

    /** 다중 + 수량 (옵션 변경의 핵심!) */
    MULTI_QUANTITY
}

/**
 * Clarification 요청
 *
 * @property question 질문 문구 (최종 렌더링된)
 * @property options selection용 옵션
 * @property hitlType HITL 유형
 * @property strategy 질문 생성 전략
 * @property reason Clarification 이유
 * @property promptTemplate LLM 질문 생성용 템플릿
 * @property templateId 템플릿 ID (다국어/버전 관리)
 * @property metadata 추가 메타데이터
 *
 * @since 1.1.0
 */
@Serializable
data class ClarificationRequest(
    val question: String,
    val options: List<ClarificationOption> = emptyList(),
    val hitlType: HitlType = HitlType.SELECTION,
    val strategy: ClarificationStrategy = ClarificationStrategy.TEMPLATE,
    val reason: ClarificationReason = ClarificationReason.AMBIGUOUS,
    val promptTemplate: String? = null,
    val templateId: String? = null,
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * 옵션이 있는지 여부
     */
    val hasOptions: Boolean
        get() = options.isNotEmpty()

    /**
     * Free-text 입력인지 여부
     */
    val isFreeText: Boolean
        get() = hitlType == HitlType.FREE_TEXT || options.isEmpty()

    companion object {
        /**
         * Selection Clarification 생성
         */
        fun selection(
            question: String,
            options: List<ClarificationOption>,
            reason: ClarificationReason = ClarificationReason.AMBIGUOUS
        ) = ClarificationRequest(
            question = question,
            options = options,
            hitlType = HitlType.SELECTION,
            strategy = ClarificationStrategy.TEMPLATE,
            reason = reason
        )

        /**
         * Free-text Clarification 생성
         */
        fun freeText(
            question: String,
            reason: ClarificationReason = ClarificationReason.LOW_CONFIDENCE
        ) = ClarificationRequest(
            question = question,
            options = emptyList(),
            hitlType = HitlType.FREE_TEXT,
            strategy = ClarificationStrategy.TEMPLATE,
            reason = reason
        )

        /**
         * LLM 생성 Clarification 요청
         */
        fun llmGenerated(
            promptTemplate: String,
            templateId: String? = null,
            reason: ClarificationReason = ClarificationReason.AMBIGUOUS
        ) = ClarificationRequest(
            question = "", // LLM이 생성
            options = emptyList(),
            hitlType = HitlType.FREE_TEXT,
            strategy = ClarificationStrategy.LLM,
            reason = reason,
            promptTemplate = promptTemplate,
            templateId = templateId
        )
    }

    /**
     * 기본 템플릿 예시
     */
    object Templates {
        val RESERVATION_SAME_PLACE = """
            사용자가 "{{place_name}}" 숙소 예약에 대해 문의했습니다.
            해당 숙소에 {{candidate_count}}건의 예약이 있습니다.
            체크인 날짜를 기준으로 어느 예약인지 확인하는 질문을 생성하세요.
            후보: {{candidates_summary}}
        """.trimIndent()

        val RESERVATION_MULTI_PLACE = """
            사용자가 예약에 대해 문의했습니다.
            {{candidate_count}}개 숙소의 예약이 검색되었습니다.
            어느 숙소 예약인지 확인하는 질문을 생성하세요.
            후보: {{candidates_summary}}
        """.trimIndent()

        val OPTION_SELECTION = """
            사용자가 옵션 변경을 요청했습니다.
            현재 선택된 옵션: {{current_options}}
            선택 가능한 옵션: {{available_options}}
            어떤 옵션을 변경할지 확인하는 질문을 생성하세요.
        """.trimIndent()
    }
}

/**
 * Clarification 옵션
 */
@Serializable
data class ClarificationOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    companion object {
        fun simple(id: String, label: String) = ClarificationOption(id, label)

        fun withDescription(id: String, label: String, description: String) =
            ClarificationOption(id, label, description)
    }
}

/**
 * Clarification 전략
 */
@Serializable
enum class ClarificationStrategy {
    /** 템플릿 기반 질문 */
    TEMPLATE,

    /** LLM 생성 질문 */
    LLM,

    /** 템플릿 + LLM 폴백 */
    HYBRID
}
