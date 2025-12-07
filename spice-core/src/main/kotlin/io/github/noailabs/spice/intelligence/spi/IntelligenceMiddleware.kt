package io.github.noailabs.spice.intelligence.spi

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.intelligence.BlockReason
import io.github.noailabs.spice.intelligence.ClarificationRequest
import io.github.noailabs.spice.intelligence.CompositeDecision
import io.github.noailabs.spice.intelligence.IntelligenceSession
import io.github.noailabs.spice.intelligence.RoutingSignal

/**
 * Intelligence Middleware SPI (Intelligence Layer v2)
 *
 * DECISION 노드 이전에만 개입하는 AOP 미들웨어.
 * 기존 Middleware 인터페이스를 확장.
 *
 * ## 핵심 원칙: DECISION 노드는 Intelligence Layer 존재를 절대 모른다!
 * - canonical 있으면 → DECISION (canonical만 봄)
 * - intentShift면 → WorkflowSwitch (DECISION 우회)
 * - offDomain이면 → Block (DECISION 우회)
 *
 * ## 개입 시점
 * - beforeNode: DECISION 노드 진입 전
 * - 다른 노드는 pass-through
 *
 * ## Cross-cutting Concerns
 * 1. Semantic canonical override
 * 2. Ambiguous detection
 * 3. Off-domain / Intent-shift detection
 * 4. Workflow switch / Resume
 *
 * @since 2.0.0
 */
interface IntelligenceMiddleware : Middleware {

    /**
     * Intelligence Layer 처리
     *
     * @param message 입력 메시지
     * @param context Intelligence 컨텍스트
     * @return 처리 결과 (메시지 + 결정)
     */
    suspend fun process(
        message: SpiceMessage,
        context: IntelligenceContext
    ): SpiceResult<IntelligenceResult>

    /**
     * 이 미들웨어가 특정 노드에 개입해야 하는지 판단
     *
     * @param nodeId 노드 ID
     * @param nodeType 노드 타입 (예: "DecisionNode", "HitlSelectionNode")
     * @return 개입 여부
     */
    fun shouldIntercept(nodeId: String, nodeType: String): Boolean
}

/**
 * Intelligence 컨텍스트
 *
 * @property sessionId 세션 ID
 * @property tenantId 테넌트 ID
 * @property userId 사용자 ID
 * @property workflowId 워크플로우 ID
 * @property currentNodeId 현재 노드 ID
 * @property prevNodes 이전 노드 경로
 * @property routingOptions DECISION 노드의 라우팅 옵션들 (canonical → description)
 * @property session Intelligence 세션 상태
 * @property availableWorkflows Intent Shift 시 전환 가능한 워크플로우 목록
 *
 * @since 2.0.0
 */
data class IntelligenceContext(
    val sessionId: String,
    val tenantId: String,
    val userId: String,
    val workflowId: String?,
    val currentNodeId: String?,
    val prevNodes: List<String>,
    val routingOptions: List<RoutingOption>,
    val session: IntelligenceSession,
    val availableWorkflows: List<String> = emptyList()
) {
    /**
     * 라우팅 옵션 canonical 목록
     */
    val canonicals: List<String>
        get() = routingOptions.map { it.canonical }

    /**
     * 라우팅 옵션 텍스트 목록 (SemanticMatcher용)
     */
    val optionTexts: List<String>
        get() = routingOptions.map { it.description ?: it.canonical }

    companion object {
        /**
         * SpiceMessage에서 컨텍스트 생성
         */
        fun from(
            message: SpiceMessage,
            session: IntelligenceSession,
            routingOptions: List<RoutingOption> = emptyList(),
            availableWorkflows: List<String> = emptyList()
        ): IntelligenceContext {
            return IntelligenceContext(
                sessionId = session.sessionId,
                tenantId = session.tenantId,
                userId = session.userId,
                workflowId = message.graphId ?: session.currentWorkflowId,
                currentNodeId = message.nodeId ?: session.currentNodeId,
                prevNodes = session.decisionHistory.map { it.nodeId },
                routingOptions = routingOptions,
                session = session,
                availableWorkflows = availableWorkflows
            )
        }
    }
}

/**
 * 라우팅 옵션 (DECISION 노드의 분기 대상)
 *
 * @property canonical 분기 식별자 (DECISION이 보는 유일한 값)
 * @property description 사용자 친화적 설명 (SemanticMatcher용)
 * @property examples 예시 발화들 (임베딩 학습/매칭용)
 *
 * @since 2.0.0
 */
data class RoutingOption(
    val canonical: String,
    val description: String? = null,
    val examples: List<String> = emptyList()
) {
    companion object {
        fun simple(canonical: String) = RoutingOption(canonical)
        fun of(canonical: String, description: String) = RoutingOption(canonical, description)
        fun withExamples(canonical: String, description: String, examples: List<String>) =
            RoutingOption(canonical, description, examples)
    }
}

/**
 * Intelligence 처리 결과
 *
 * ## 핵심: DECISION 노드가 보는 건 "canonical" 뿐!
 * - Override → message.data["hitl.canonical"] 설정 → DECISION 진행
 * - SwitchWorkflow → DECISION 우회, 직접 워크플로우 전환
 * - Block → DECISION 우회, 즉시 종료
 *
 * @since 2.0.0
 */
sealed class IntelligenceResult {

    /**
     * 그대로 진행 (Intelligence 개입 없음)
     */
    data class PassThrough(
        val message: SpiceMessage
    ) : IntelligenceResult()

    /**
     * Canonical 덮어쓰기
     *
     * DECISION은 이 canonical만 봄 (Intelligence 존재 모름)
     */
    data class Override(
        val message: SpiceMessage,
        /** DECISION이 볼 유일한 값 */
        val canonical: String,
        val confidence: Double,
        val reasoning: String? = null
    ) : IntelligenceResult()

    /**
     * Clarification 필요
     *
     * HITL 재질문으로 라우팅
     */
    data class Clarify(
        val message: SpiceMessage,
        val clarificationRequest: ClarificationRequest
    ) : IntelligenceResult()

    /**
     * Intent Shift - 워크플로우 전환
     *
     * DECISION 우회, 직접 워크플로우 전환
     */
    data class SwitchWorkflow(
        val message: SpiceMessage,
        val newWorkflowId: String,
        val resumeNodeId: String?,
        val reasoning: String? = null
    ) : IntelligenceResult()

    /**
     * LLM 위임
     *
     * DECISION 우회, 상위 LLM으로 전달
     */
    data class DelegateToLLM(
        val message: SpiceMessage,
        val reason: String
    ) : IntelligenceResult()

    /**
     * Off-domain 차단
     *
     * DECISION 우회, 즉시 종료
     */
    data class Block(
        val message: SpiceMessage,
        val reason: BlockReason,
        val userMessage: String
    ) : IntelligenceResult()

    /**
     * 에스컬레이션 (상담원 연결)
     */
    data class Escalate(
        val message: SpiceMessage,
        val reason: String,
        val context: Map<String, Any> = emptyMap()
    ) : IntelligenceResult()

    companion object {
        /**
         * CompositeDecision에서 IntelligenceResult 생성
         *
         * ## RoutingSignal → IntelligenceResult 매핑
         * - NORMAL + aiCanonical → Override
         * - AMBIGUOUS → Clarify
         * - OFF_DOMAIN_HARD → Block (hard)
         * - OFF_DOMAIN_SOFT → Block (soft) or DelegateToLLM
         * - SWITCH_WORKFLOW → SwitchWorkflow
         * - DELEGATE_TO_LLM → DelegateToLLM
         * - ESCALATE → Escalate
         * - POLICY_BLOCK → Block (policy)
         */
        fun from(message: SpiceMessage, decision: CompositeDecision): IntelligenceResult {
            return when (decision.routingSignal) {
                RoutingSignal.NORMAL -> {
                    if (decision.aiCanonical != null) {
                        Override(
                            message = message,
                            canonical = decision.aiCanonical,
                            confidence = decision.confidence,
                            reasoning = decision.reasoning
                        )
                    } else {
                        PassThrough(message)
                    }
                }

                RoutingSignal.AMBIGUOUS -> {
                    if (decision.clarificationRequest != null) {
                        Clarify(message, decision.clarificationRequest)
                    } else {
                        DelegateToLLM(message, decision.reasoning ?: "Ambiguous, but no clarification request")
                    }
                }

                RoutingSignal.OFF_DOMAIN_HARD -> Block(
                    message = message,
                    reason = BlockReason.OFF_DOMAIN_HARD,
                    userMessage = decision.reasoning ?: "죄송합니다, 지금은 안내드릴 수 없습니다."
                )

                RoutingSignal.OFF_DOMAIN_SOFT -> Block(
                    message = message,
                    reason = BlockReason.OFF_DOMAIN_SOFT,
                    userMessage = decision.reasoning ?: "죄송합니다, 해당 요청은 처리할 수 없습니다."
                )

                RoutingSignal.SWITCH_WORKFLOW -> {
                    if (decision.newWorkflowId != null) {
                        SwitchWorkflow(
                            message = message,
                            newWorkflowId = decision.newWorkflowId,
                            resumeNodeId = decision.resumeNodeId,
                            reasoning = decision.reasoning
                        )
                    } else {
                        PassThrough(message) // fallback if no workflow specified
                    }
                }

                RoutingSignal.RESUME_WORKFLOW -> {
                    if (decision.newWorkflowId != null) {
                        SwitchWorkflow(
                            message = message,
                            newWorkflowId = decision.newWorkflowId,
                            resumeNodeId = decision.resumeNodeId,
                            reasoning = decision.reasoning
                        )
                    } else {
                        PassThrough(message)
                    }
                }

                RoutingSignal.DELEGATE_TO_LLM -> DelegateToLLM(
                    message = message,
                    reason = decision.reasoning ?: "Confidence too low"
                )

                RoutingSignal.ESCALATE -> Escalate(
                    message = message,
                    reason = decision.reasoning ?: "Escalation requested"
                )

                RoutingSignal.POLICY_BLOCK -> Block(
                    message = message,
                    reason = BlockReason.POLICY_VIOLATION,
                    userMessage = decision.reasoning ?: "정책에 따라 해당 요청을 처리할 수 없습니다."
                )
            }
        }
    }
}
