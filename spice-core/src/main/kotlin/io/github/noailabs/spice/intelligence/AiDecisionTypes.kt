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

// ============================================================================
// Intelligence Layer v2.0 Types
// ============================================================================

/**
 * 라우팅 신호 (Intelligence Layer v2)
 *
 * Intelligence Middleware가 DECISION 노드에 전달하는 신호.
 * DECISION 노드는 이 신호를 직접 보지 않음 - canonical만 봄.
 *
 * ## v2 Spec 매핑
 * - NORMAL: 정상 진행 → DECISION에 canonical 전달
 * - AMBIGUOUS: Clarification 필요 → HITL 재질문
 * - OFF_DOMAIN_HARD: 확실한 Off-domain → Hard Block
 * - OFF_DOMAIN_SOFT: 가능성 있는 Off-domain → Soft Block (LLM 위임 가능)
 * - SWITCH_WORKFLOW: Intent Shift → 워크플로우 전환
 * - RESUME_WORKFLOW: 이전 워크플로우 복귀
 * - DELEGATE_TO_LLM: 불확실 → 상위 LLM 위임
 * - ESCALATE: 상담원 연결
 * - POLICY_BLOCK: 정책 위반 차단
 *
 * @since 2.0.0
 */
@Serializable
enum class RoutingSignal {
    /** 정상 진행 - canonical 그대로 DECISION으로 */
    NORMAL,

    /** Clarification 필요 - HITL 재질문 (1위/2위 격차 부족) */
    AMBIGUOUS,

    /** 확실한 Off-domain - Hard Block (domainRelevance < 0.3) */
    OFF_DOMAIN_HARD,

    /** 가능성 있는 Off-domain - Soft Block (0.3 ≤ domainRelevance < 0.5) */
    OFF_DOMAIN_SOFT,

    /** 워크플로우 전환 - DECISION 우회, Intent Shift 감지 */
    SWITCH_WORKFLOW,

    /** 워크플로우 재개 - 이전 워크플로우로 복귀 */
    RESUME_WORKFLOW,

    /** LLM 위임 - DECISION 우회, 상위 LLM으로 */
    DELEGATE_TO_LLM,

    /** 에스컬레이션 - 상담원 연결 */
    ESCALATE,

    /** 정책 위반 차단 */
    POLICY_BLOCK;

    // === 하위 호환용 별칭 ===
    companion object {
        /** @deprecated Use NORMAL instead */
        @Deprecated("Use NORMAL", ReplaceWith("NORMAL"))
        val CONTINUE = NORMAL

        /** @deprecated Use AMBIGUOUS instead */
        @Deprecated("Use AMBIGUOUS", ReplaceWith("AMBIGUOUS"))
        val CLARIFY = AMBIGUOUS

        /** @deprecated Use OFF_DOMAIN_HARD instead */
        @Deprecated("Use OFF_DOMAIN_HARD or OFF_DOMAIN_SOFT", ReplaceWith("OFF_DOMAIN_HARD"))
        val BLOCK = OFF_DOMAIN_HARD
    }
}

/**
 * Gating 결정 (Intelligence Layer v2)
 *
 * Confidence 기반 파이프라인 분기 결정.
 * embedding score + domain relevance + gap + policy context 조합으로 판단.
 *
 * @since 2.0.0
 */
@Serializable
enum class GatingDecision {
    /** Fast Path - 즉시 canonical 결정 (≥0.85 + 조건 충족) */
    FAST_PATH,

    /** Nano LLM 검증 필요 (0.65-0.85) */
    NANO_VALIDATION,

    /** Big LLM 호출 필요 (<0.65) */
    BIG_LLM
}

/**
 * 차단 사유 (Intelligence Layer v2)
 *
 * ## 차단 유형
 * - Hard Block: 즉시 종료, 사용자 메시지 표시
 * - Soft Block: LLM 위임 또는 에스컬레이션 가능
 *
 * @since 2.0.0
 */
@Serializable
enum class BlockReason {
    // === Hard Block ===

    /** 확실한 Off-domain 발화 (domainRelevance < 0.3) */
    OFF_DOMAIN_HARD,

    /** 무의미한 발화 (hard nonsense) */
    NONSENSE,

    /** 금지된 콘텐츠 (욕설, 부적절 표현 등) */
    PROHIBITED_CONTENT,

    /** 정책 위반 (Policy-RAG에서 감지) */
    POLICY_VIOLATION,

    /** Rate limit 초과 */
    RATE_LIMIT_EXCEEDED,

    // === Soft Block (LLM 위임 가능) ===

    /** 가능성 있는 Off-domain (0.3 ≤ domainRelevance < 0.5) */
    OFF_DOMAIN_SOFT,

    // === Session Guard ===

    /** LoopGuard 트리거됨 (동일 질문 반복) */
    LOOP_GUARD_TRIGGERED,

    /** 최대 Clarification 시도 초과 */
    MAX_CLARIFICATION_EXCEEDED,

    /** Frustration 감지 (사용자 불만) */
    FRUSTRATION_DETECTED;

    /**
     * Hard Block 여부 (즉시 종료)
     */
    val isHardBlock: Boolean
        get() = this in setOf(
            OFF_DOMAIN_HARD,
            NONSENSE,
            PROHIBITED_CONTENT,
            POLICY_VIOLATION,
            RATE_LIMIT_EXCEEDED
        )

    /**
     * Soft Block 여부 (LLM 위임 또는 에스컬레이션 가능)
     */
    val isSoftBlock: Boolean
        get() = !isHardBlock

    companion object {
        /** @deprecated Use OFF_DOMAIN_HARD instead */
        @Deprecated("Use OFF_DOMAIN_HARD or OFF_DOMAIN_SOFT", ReplaceWith("OFF_DOMAIN_HARD"))
        val OFF_DOMAIN = OFF_DOMAIN_HARD
    }
}

/**
 * 정책 유형 (Policy-RAG용)
 *
 * PolicyStore가 반환하는 정책 힌트의 분류.
 * 주의: PolicyStore는 정보만 제공, 판단은 OneShotReasoner에서 수행!
 *
 * @since 2.0.0
 */
@Serializable
enum class PolicyType {
    /** 라우팅 정책 (어떤 워크플로우로 이동할지) */
    ROUTING,

    /** 비즈니스 규칙 */
    BUSINESS_RULE,

    /** 응답 가이드라인 */
    RESPONSE_GUIDELINE,

    /** 금지 사항 */
    PROHIBITION,

    /** 에스컬레이션 조건 */
    ESCALATION
}

/**
 * 정책 힌트 (Policy-RAG 검색 결과)
 *
 * PolicyStore.search()가 반환하는 정보.
 * 주의: 이 클래스는 정보만 담는다! 판단 필드 금지!
 *
 * @property policyId 정책 ID
 * @property content 정책 텍스트 (raw)
 * @property workflowId 관련 워크플로우 ID (optional)
 * @property score 검색 유사도 (판단 아님!)
 * @property policyType 정책 분류 (판단 아님!)
 * @property metadata 추가 메타데이터
 *
 * @since 2.0.0
 */
@Serializable
data class PolicyHint(
    val policyId: String,
    val content: String,
    val workflowId: String? = null,
    val score: Double,
    val policyType: PolicyType,
    val metadata: Map<String, @Contextual Any> = emptyMap()
)

/**
 * 복합 의사결정 (One-Shot Reasoner 출력)
 *
 * GPT-5.1급 모델이 한 번에 판단하는 모든 cross-cutting concern.
 * DECISION 노드는 이 구조체를 직접 보지 않음 - canonical만 봄.
 *
 * @property domainRelevance 도메인 관련성 (0.0-1.0)
 * @property isOffDomain Off-domain 여부
 * @property intentShift Intent 전환 감지 여부
 * @property newWorkflowId 전환할 워크플로우 ID (intentShift=true 시)
 * @property resumeNodeId Resume할 노드 ID (워크플로우 재개 시)
 * @property aiCanonical AI가 추론한 canonical 값
 * @property confidence 전체 신뢰도
 * @property reasoning 추론 근거 (디버깅용)
 * @property decisionSource 결정 출처
 * @property needsClarification Clarification 필요 여부
 * @property clarificationRequest Clarification 요청 (needsClarification=true 시)
 * @property routingSignal 라우팅 신호
 * @property tokensUsed 토큰 사용량 (optional)
 * @property latencyMs 처리 지연시간
 *
 * @since 2.0.0
 */
@Serializable
data class CompositeDecision(
    val domainRelevance: Double,
    val isOffDomain: Boolean = false,
    val intentShift: Boolean = false,
    val newWorkflowId: String? = null,
    val resumeNodeId: String? = null,
    val aiCanonical: String? = null,
    val confidence: Double,
    val reasoning: String? = null,
    val decisionSource: DecisionSource,
    val needsClarification: Boolean = false,
    val clarificationRequest: ClarificationRequest? = null,
    val routingSignal: RoutingSignal = RoutingSignal.NORMAL,
    val tokensUsed: TokenUsage? = null,
    val latencyMs: Long = 0
) {
    /**
     * DECISION으로 진행 가능한지 여부
     */
    val canProceedToDecision: Boolean
        get() = routingSignal == RoutingSignal.NORMAL && aiCanonical != null

    /**
     * 워크플로우 전환이 필요한지 여부
     */
    val needsWorkflowSwitch: Boolean
        get() = intentShift && newWorkflowId != null

    companion object {
        /**
         * Fast Path 결정 생성
         */
        fun fastPath(
            canonical: String,
            confidence: Double,
            domainRelevance: Double,
            reasoning: String? = null
        ) = CompositeDecision(
            domainRelevance = domainRelevance,
            aiCanonical = canonical,
            confidence = confidence,
            reasoning = reasoning,
            decisionSource = DecisionSource.EMBEDDING,
            routingSignal = RoutingSignal.NORMAL
        )

        /**
         * Hard Off-domain 결정 생성 (확실한 off-domain)
         */
        fun offDomainHard(
            reason: String,
            domainRelevance: Double = 0.0
        ) = CompositeDecision(
            domainRelevance = domainRelevance,
            isOffDomain = true,
            confidence = 1.0,
            reasoning = reason,
            decisionSource = DecisionSource.REASONER,
            routingSignal = RoutingSignal.OFF_DOMAIN_HARD
        )

        /**
         * Soft Off-domain 결정 생성 (가능성 있는 off-domain)
         */
        fun offDomainSoft(
            reason: String,
            domainRelevance: Double = 0.4
        ) = CompositeDecision(
            domainRelevance = domainRelevance,
            isOffDomain = true,
            confidence = 0.7,
            reasoning = reason,
            decisionSource = DecisionSource.REASONER,
            routingSignal = RoutingSignal.OFF_DOMAIN_SOFT
        )

        /**
         * Off-domain 결정 생성 (domainRelevance 기반 자동 판단)
         */
        fun offDomain(
            reason: String,
            domainRelevance: Double = 0.0
        ) = if (domainRelevance < 0.3) {
            offDomainHard(reason, domainRelevance)
        } else {
            offDomainSoft(reason, domainRelevance)
        }

        /**
         * Intent Shift 결정 생성
         */
        fun intentShift(
            newWorkflowId: String,
            resumeNodeId: String?,
            reason: String,
            confidence: Double = 0.9
        ) = CompositeDecision(
            domainRelevance = 0.3, // 현재 도메인과는 관련 낮음
            intentShift = true,
            newWorkflowId = newWorkflowId,
            resumeNodeId = resumeNodeId,
            confidence = confidence,
            reasoning = reason,
            decisionSource = DecisionSource.REASONER,
            routingSignal = RoutingSignal.SWITCH_WORKFLOW
        )

        /**
         * Clarification 필요 결정 생성 (Ambiguous)
         */
        fun needsClarification(
            request: ClarificationRequest,
            domainRelevance: Double,
            confidence: Double = 0.5
        ) = CompositeDecision(
            domainRelevance = domainRelevance,
            confidence = confidence,
            decisionSource = DecisionSource.EMBEDDING,
            needsClarification = true,
            clarificationRequest = request,
            routingSignal = RoutingSignal.AMBIGUOUS
        )

        /**
         * Pass-through 결정 생성 (Intelligence 개입 없음)
         */
        fun passThrough(
            reason: String,
            domainRelevance: Double = 0.5
        ) = CompositeDecision(
            domainRelevance = domainRelevance,
            confidence = 0.0,
            reasoning = reason,
            decisionSource = DecisionSource.FALLBACK,
            routingSignal = RoutingSignal.NORMAL
        )

        /**
         * LLM 위임 결정 생성
         */
        fun delegateToLLM(
            reason: String,
            domainRelevance: Double = 0.5,
            confidence: Double = 0.0
        ) = CompositeDecision(
            domainRelevance = domainRelevance,
            confidence = confidence,
            reasoning = reason,
            decisionSource = DecisionSource.EMBEDDING,
            routingSignal = RoutingSignal.DELEGATE_TO_LLM
        )
    }
}

/**
 * 토큰 사용량 (LLM 호출 추적용)
 *
 * @since 2.0.0
 */
@Serializable
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int
) {
    val totalTokens: Int
        get() = inputTokens + outputTokens
}
