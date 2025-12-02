package io.github.noailabs.spice.routing.gateway

/**
 * Workflow Handle - 워크플로우 실행 추상화
 *
 * GatewayAgent의 RoutingDecision을 실제 워크플로우 실행으로 변환하는 인터페이스.
 * Kai-core에서 구현하여 Spice StateMachineWorkflowEngine을 래핑.
 *
 * ## Responsibility
 *
 * - 워크플로우 시작/재개 추상화
 * - 입력 메시지 변환 (Map → SpiceMessage)
 * - 결과 변환 (SpiceResult → WorkflowResult)
 *
 * ## Implementation Example
 *
 * ```kotlin
 * @Component
 * class WorkflowHandleImpl(
 *     private val engine: StateMachineWorkflowEngine,
 *     private val graphRegistry: Map<String, Graph>
 * ) : WorkflowHandle {
 *
 *     override suspend fun start(workflowId: String, input: Map<String, Any?>): WorkflowResult {
 *         val graph = graphRegistry[workflowId]
 *             ?: return WorkflowResult.failure("Workflow not found: $workflowId")
 *
 *         val message = input.toSpiceMessage()
 *         return engine.execute(graph, message).toWorkflowResult()
 *     }
 *
 *     override suspend fun resume(workflowId: String, sessionId: String, input: Map<String, Any?>): WorkflowResult {
 *         val message = input.toSpiceMessage(correlationId = sessionId)
 *         return engine.resume(sessionId, message).toWorkflowResult()
 *     }
 * }
 * ```
 *
 * @since 1.5.0
 */
interface WorkflowHandle {

    /**
     * 새 워크플로우 시작
     *
     * @param workflowId 시작할 워크플로우 ID
     * @param input 입력 데이터 (text, userId, tenantId 등)
     * @return WorkflowResult (Success, Waiting, Failure)
     */
    suspend fun start(
        workflowId: String,
        input: Map<String, Any?>
    ): WorkflowResult

    /**
     * 워크플로우 재개 (HITL resume)
     *
     * @param workflowId 재개할 워크플로우 ID (검증용, 실제 resume은 sessionId 기반)
     * @param sessionId 세션 ID (checkpoint 조회용)
     * @param input 사용자 응답 데이터
     * @return WorkflowResult (Success, Waiting, Failure)
     */
    suspend fun resume(
        workflowId: String,
        sessionId: String,
        input: Map<String, Any?>
    ): WorkflowResult

    /**
     * 워크플로우 취소
     *
     * @param sessionId 세션 ID
     * @param reason 취소 사유
     * @return 취소 성공 여부
     */
    suspend fun cancel(
        sessionId: String,
        reason: String? = null
    ): Boolean = false  // 기본 구현: 미지원
}
