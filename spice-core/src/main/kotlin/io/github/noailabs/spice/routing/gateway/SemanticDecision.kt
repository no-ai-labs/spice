package io.github.noailabs.spice.routing.gateway

import io.github.noailabs.spice.intelligence.RoutingSignal
import kotlinx.serialization.Serializable

/**
 * Semantic Decision - SemanticRouter의 출력
 *
 * RoutingSignal + 메타데이터를 담는 구조체.
 * GatewayAgent가 이를 RoutingDecision으로 변환.
 *
 * ## Signal별 필수 필드
 *
 * | Signal | 필수 필드 |
 * |--------|----------|
 * | NORMAL | - (현재 워크플로우 계속) |
 * | SWITCH_WORKFLOW | nextWorkflowId |
 * | OFF_DOMAIN_HARD/SOFT | offDomainType |
 * | AMBIGUOUS | clarificationNodeId |
 * | ESCALATE | - |
 *
 * ## Usage
 *
 * ```kotlin
 * // Intent 분류 성공 → 워크플로우 전환
 * SemanticDecision.switchWorkflow(
 *     workflowId = "cancel_booking",
 *     reason = "guest.cancel.booking intent matched"
 * )
 *
 * // Intent 분류 실패 → Off-domain
 * SemanticDecision.offDomain(
 *     type = OffDomainType.CLASSIFICATION_FAILED,
 *     reason = "No intent matched with confidence >= 0.6"
 * )
 * ```
 *
 * @property signal 라우팅 신호 (기존 RoutingSignal 재사용)
 * @property nextWorkflowId SWITCH_WORKFLOW 시 전환할 워크플로우 ID
 * @property nextStartNode SWITCH_WORKFLOW 시 시작 노드 (optional)
 * @property resumeNode NORMAL/AMBIGUOUS 시 resume할 노드 (optional)
 * @property offDomainType OFF_DOMAIN 시 세부 유형
 * @property reason 결정 사유 (디버깅/로깅용)
 * @property confidence 결정 신뢰도 (0.0-1.0)
 * @property intentCode 분류된 intent 코드 (있는 경우)
 *
 * @since 1.5.0
 */
@Serializable
data class SemanticDecision(
    val signal: RoutingSignal,
    val nextWorkflowId: String? = null,
    val nextStartNode: String? = null,
    val resumeNode: String? = null,
    val offDomainType: OffDomainType? = null,
    val reason: String? = null,
    val confidence: Double = 1.0,
    val intentCode: String? = null
) {
    /**
     * 워크플로우 전환이 필요한지 여부
     */
    val needsSwitch: Boolean
        get() = signal == RoutingSignal.SWITCH_WORKFLOW && nextWorkflowId != null

    /**
     * Off-domain 판정인지 여부
     */
    val isOffDomain: Boolean
        get() = signal in setOf(RoutingSignal.OFF_DOMAIN_HARD, RoutingSignal.OFF_DOMAIN_SOFT)

    /**
     * 상담사 이관이 필요한지 여부
     */
    val needsEscalation: Boolean
        get() = signal == RoutingSignal.ESCALATE ||
                (isOffDomain && offDomainType?.requiresEscalation == true)

    companion object {
        /**
         * 현재 워크플로우 계속 진행
         */
        fun continueWorkflow(
            resumeNode: String? = null,
            reason: String? = null
        ) = SemanticDecision(
            signal = RoutingSignal.NORMAL,
            resumeNode = resumeNode,
            reason = reason ?: "Continue current workflow"
        )

        /**
         * 다른 워크플로우로 전환
         */
        fun switchWorkflow(
            workflowId: String,
            startNode: String? = null,
            intentCode: String? = null,
            confidence: Double = 1.0,
            reason: String? = null
        ) = SemanticDecision(
            signal = RoutingSignal.SWITCH_WORKFLOW,
            nextWorkflowId = workflowId,
            nextStartNode = startNode,
            intentCode = intentCode,
            confidence = confidence,
            reason = reason ?: "Switch to workflow: $workflowId"
        )

        /**
         * Off-domain 판정
         */
        fun offDomain(
            type: OffDomainType,
            intentCode: String? = null,
            reason: String? = null
        ) = SemanticDecision(
            signal = if (type == OffDomainType.SEMANTIC_OUT_OF_SCOPE) {
                RoutingSignal.OFF_DOMAIN_HARD
            } else {
                RoutingSignal.OFF_DOMAIN_SOFT
            },
            offDomainType = type,
            intentCode = intentCode,
            reason = reason ?: "Off-domain: ${type.name}"
        )

        /**
         * 상담사 이관
         */
        fun escalate(
            reason: String,
            intentCode: String? = null
        ) = SemanticDecision(
            signal = RoutingSignal.ESCALATE,
            intentCode = intentCode,
            reason = reason
        )

        /**
         * Clarification 필요 (모호한 입력)
         */
        fun needsClarification(
            resumeNode: String,
            reason: String? = null
        ) = SemanticDecision(
            signal = RoutingSignal.AMBIGUOUS,
            resumeNode = resumeNode,
            reason = reason ?: "Clarification needed"
        )

        /**
         * Clarification 필요 (needsClarification의 단축 alias)
         *
         * ```kotlin
         * SemanticDecision.clarify("clarify_booking")
         * SemanticDecision.needsClarification("clarify_payment")
         * ```
         */
        fun clarify(
            resumeNode: String = "clarification_entry",
            reason: String? = null
        ) = needsClarification(
            resumeNode = resumeNode,
            reason = reason
        )
    }
}
