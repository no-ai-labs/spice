package io.github.noailabs.spice.routing.gateway

import kotlinx.serialization.Serializable

/**
 * Routing Decision - GatewayAgent의 최종 출력
 *
 * GatewayAgent가 SemanticDecision을 변환한 최종 라우팅 결정.
 * WorkflowHandle이 이 결정에 따라 워크플로우를 시작/재개.
 *
 * ## Decision Types
 *
 * - [ContinueWorkflow]: 현재 워크플로우 계속 (HITL resume 등)
 * - [SwitchWorkflow]: 새 워크플로우 시작
 * - [OffDomain]: 서비스 범위 외 (에러 응답 또는 상담사 이관)
 *
 * ## Usage in ChatController
 *
 * ```kotlin
 * val decision = gatewayAgent.handle(userInput, session)
 *
 * return when (decision) {
 *     is RoutingDecision.ContinueWorkflow ->
 *         workflowHandle.resume(decision.workflowId, session.id, input)
 *
 *     is RoutingDecision.SwitchWorkflow ->
 *         workflowHandle.start(decision.newWorkflowId, input)
 *
 *     is RoutingDecision.OffDomain ->
 *         handleOffDomain(decision)
 * }
 * ```
 *
 * @since 1.5.0
 */
@Serializable
sealed class RoutingDecision {

    /**
     * 현재 워크플로우 계속
     *
     * 사용 케이스:
     * - HITL 응답 후 resume
     * - 워크플로우 내 관련 입력
     *
     * @property workflowId 계속할 워크플로우 ID
     * @property resumeNodeId resume할 노드 ID (optional)
     */
    @Serializable
    data class ContinueWorkflow(
        val workflowId: String,
        val resumeNodeId: String? = null
    ) : RoutingDecision()

    /**
     * 새 워크플로우로 전환
     *
     * 사용 케이스:
     * - 새 대화 시작
     * - Intent shift 감지
     *
     * @property newWorkflowId 시작할 워크플로우 ID
     * @property startNodeId 시작 노드 ID (optional, null이면 entry node)
     * @property intentCode 분류된 intent 코드 (logging용)
     */
    @Serializable
    data class SwitchWorkflow(
        val newWorkflowId: String,
        val startNodeId: String? = null,
        val intentCode: String? = null
    ) : RoutingDecision()

    /**
     * 서비스 범위 외 (Off-Domain)
     *
     * 사용 케이스:
     * - Intent 분류 실패
     * - 매핑된 워크플로우 없음
     * - 도메인 외 질문
     *
     * @property type Off-domain 세부 유형
     * @property reason 사유 (디버깅용)
     * @property intentCode 분류된 intent 코드 (있는 경우)
     */
    @Serializable
    data class OffDomain(
        val type: OffDomainType,
        val reason: String? = null,
        val intentCode: String? = null
    ) : RoutingDecision() {

        /**
         * 상담사 이관이 필요한지 여부
         */
        val requiresEscalation: Boolean
            get() = type.requiresEscalation

        /**
         * 사용자에게 보여줄 메시지
         */
        val userMessage: String
            get() = type.defaultMessage
    }

    companion object {
        /**
         * SemanticDecision → RoutingDecision 변환
         *
         * @param decision SemanticDecision
         * @param currentWorkflowId 현재 활성 워크플로우 ID (NORMAL 신호 시 필요)
         * @return RoutingDecision
         */
        fun from(
            decision: SemanticDecision,
            currentWorkflowId: String?
        ): RoutingDecision = when {
            // SWITCH_WORKFLOW
            decision.needsSwitch ->
                SwitchWorkflow(
                    newWorkflowId = decision.nextWorkflowId!!,
                    startNodeId = decision.nextStartNode,
                    intentCode = decision.intentCode
                )

            // OFF_DOMAIN
            decision.isOffDomain ->
                OffDomain(
                    type = decision.offDomainType ?: OffDomainType.SEMANTIC_OUT_OF_SCOPE,
                    reason = decision.reason,
                    intentCode = decision.intentCode
                )

            // ESCALATE (상담사 이관)
            decision.needsEscalation ->
                OffDomain(
                    type = OffDomainType.INTENT_UNMAPPED,
                    reason = decision.reason,
                    intentCode = decision.intentCode
                )

            // NORMAL (현재 워크플로우 계속)
            currentWorkflowId != null ->
                ContinueWorkflow(
                    workflowId = currentWorkflowId,
                    resumeNodeId = decision.resumeNode
                )

            // currentWorkflowId 없음 → Off-domain으로 처리
            else ->
                OffDomain(
                    type = OffDomainType.SEMANTIC_OUT_OF_SCOPE,
                    reason = "No active workflow to continue"
                )
        }
    }
}
