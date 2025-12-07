package io.github.noailabs.spice.routing.gateway

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Workflow Result - 워크플로우 실행 결과
 *
 * SpiceResult와 다른 점:
 * - WAITING 상태를 별도 타입으로 표현 (HITL 대기 중)
 * - HTTP 응답 변환에 최적화된 구조
 *
 * ## Result Types
 *
 * - [Success]: 워크플로우 완료 (COMPLETED)
 * - [Waiting]: HITL 대기 중 (WAITING)
 * - [Failure]: 실행 실패 (FAILED)
 *
 * ## Usage
 *
 * ```kotlin
 * val result = workflowHandle.start("cancel_booking", input)
 *
 * return when (result) {
 *     is WorkflowResult.Success -> ChatResponse(
 *         message = result.message ?: "완료되었습니다",
 *         state = "COMPLETED"
 *     )
 *     is WorkflowResult.Waiting -> ChatResponse(
 *         message = result.promptMessage ?: "응답을 기다리고 있습니다",
 *         state = "WAITING",
 *         checkpointId = result.checkpointId
 *     )
 *     is WorkflowResult.Failure -> ChatResponse(
 *         message = result.error,
 *         state = "FAILED"
 *     )
 * }
 * ```
 *
 * @since 1.5.0
 */
@Serializable
sealed class WorkflowResult {

    /**
     * 워크플로우 성공 완료
     *
     * @property data 결과 데이터 (워크플로우에서 수집된 정보)
     * @property message 사용자에게 보여줄 메시지
     * @property workflowId 완료된 워크플로우 ID
     */
    @Serializable
    data class Success(
        val data: Map<String, @Contextual Any?> = emptyMap(),
        val message: String? = null,
        val workflowId: String? = null
    ) : WorkflowResult()

    /**
     * HITL 대기 중
     *
     * @property checkpointId 체크포인트 ID (resume용)
     * @property promptMessage HITL 프롬프트 메시지
     * @property uiType UI 타입 (selection, input, confirmation 등)
     * @property options 선택지 (selection 타입일 때)
     * @property workflowId 현재 워크플로우 ID
     * @property nodeId 현재 노드 ID
     */
    @Serializable
    data class Waiting(
        val checkpointId: String,
        val promptMessage: String? = null,
        val uiType: String? = null,
        val options: List<String>? = null,
        val workflowId: String? = null,
        val nodeId: String? = null
    ) : WorkflowResult()

    /**
     * 워크플로우 실패
     *
     * @property error 에러 메시지
     * @property errorCode 에러 코드
     * @property cause 원인 예외 (직렬화 불가, transient)
     * @property workflowId 실패한 워크플로우 ID
     * @property nodeId 실패한 노드 ID
     */
    @Serializable
    data class Failure(
        val error: String,
        val errorCode: String? = null,
        @kotlinx.serialization.Transient
        val cause: Throwable? = null,
        val workflowId: String? = null,
        val nodeId: String? = null
    ) : WorkflowResult()

    /**
     * 성공 여부
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * 대기 중 여부
     */
    val isWaiting: Boolean
        get() = this is Waiting

    /**
     * 실패 여부
     */
    val isFailure: Boolean
        get() = this is Failure

    /**
     * 완료 여부 (성공 또는 실패)
     */
    val isTerminal: Boolean
        get() = this is Success || this is Failure

    companion object {
        /**
         * Success 생성
         */
        fun success(
            data: Map<String, Any?> = emptyMap(),
            message: String? = null,
            workflowId: String? = null
        ) = Success(data, message, workflowId)

        /**
         * Waiting 생성
         */
        fun waiting(
            checkpointId: String,
            promptMessage: String? = null,
            uiType: String? = null,
            options: List<String>? = null,
            workflowId: String? = null,
            nodeId: String? = null
        ) = Waiting(checkpointId, promptMessage, uiType, options, workflowId, nodeId)

        /**
         * Failure 생성
         */
        fun failure(
            error: String,
            errorCode: String? = null,
            cause: Throwable? = null,
            workflowId: String? = null,
            nodeId: String? = null
        ) = Failure(error, errorCode, cause, workflowId, nodeId)

        /**
         * Failure from exception
         */
        fun fromException(
            throwable: Throwable,
            workflowId: String? = null,
            nodeId: String? = null
        ) = Failure(
            error = throwable.message ?: "Unknown error",
            errorCode = throwable::class.simpleName,
            cause = throwable,
            workflowId = workflowId,
            nodeId = nodeId
        )
    }
}

/**
 * WorkflowResult 변환 확장 함수
 */
inline fun <R> WorkflowResult.fold(
    onSuccess: (WorkflowResult.Success) -> R,
    onWaiting: (WorkflowResult.Waiting) -> R,
    onFailure: (WorkflowResult.Failure) -> R
): R = when (this) {
    is WorkflowResult.Success -> onSuccess(this)
    is WorkflowResult.Waiting -> onWaiting(this)
    is WorkflowResult.Failure -> onFailure(this)
}

/**
 * 성공 시 변환
 */
inline fun <R> WorkflowResult.mapSuccess(transform: (WorkflowResult.Success) -> R): R? =
    if (this is WorkflowResult.Success) transform(this) else null

/**
 * Waiting 시 변환
 */
inline fun <R> WorkflowResult.mapWaiting(transform: (WorkflowResult.Waiting) -> R): R? =
    if (this is WorkflowResult.Waiting) transform(this) else null
