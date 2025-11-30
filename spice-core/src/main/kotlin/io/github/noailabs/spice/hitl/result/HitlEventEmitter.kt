package io.github.noailabs.spice.hitl.result

import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.ToolResultStatus
import io.github.noailabs.spice.error.SpiceResult

/**
 * Port Interface for HITL Event Emission
 *
 * This is a **Port Interface** following Clean Architecture principles.
 * - **Core** defines this interface (what it needs)
 * - **Infra** implements this interface (how it's done)
 *
 * The Core module never knows about EventBus, Webhook, or any specific
 * communication mechanism. It only knows that it can emit HITL requests
 * and wait for responses.
 *
 * **Implementation Examples:**
 * - `spice-infra`: EventBusHitlEventEmitter, WebhookHitlEventEmitter
 * - Custom: KafkaHitlEventEmitter, SQSHitlEventEmitter, etc.
 *
 * **Usage in HITL Tools:**
 * ```kotlin
 * class HitlRequestInputTool(
 *     private val emitter: HitlEventEmitter
 * ) : Tool {
 *     override suspend fun execute(params, context) {
 *         val metadata = HITLMetadata.forInput(...)
 *         emitter.emitHitlRequest(metadata)
 *         return ToolResult.waitingHitl(...)
 *     }
 * }
 * ```
 *
 * **Dependency Direction:**
 * ```
 * spice-infra → spice-core
 *     ↓              ↑
 * EventBusHitlEventEmitter implements HitlEventEmitter
 * ```
 *
 * @since 1.0.6
 */
interface HitlEventEmitter {

    /**
     * Emit a HITL request to external systems
     *
     * This method notifies external systems (UI, webhook, event bus) that
     * user input is required. The implementation determines how this
     * notification is delivered.
     *
     * @param metadata HITL request metadata including prompt, options, etc.
     * @return SpiceResult indicating success or failure of emission
     */
    suspend fun emitHitlRequest(metadata: HITLMetadata): SpiceResult<Unit>

    /**
     * Check if a HITL response has been received
     *
     * This method checks if a response for the given tool_call_id is available.
     * It does not wait or block - use [awaitHitlResponse] for blocking wait.
     *
     * @param toolCallId The tool_call_id to check
     * @return HITLResponseEvent if available, null otherwise
     */
    suspend fun checkHitlResponse(toolCallId: String): HITLResponseEvent?

    /**
     * Await HITL response with optional timeout
     *
     * This method waits for a HITL response. The implementation determines
     * how the waiting is performed (polling, event subscription, etc.).
     *
     * @param toolCallId The tool_call_id to wait for
     * @param timeoutMs Timeout in milliseconds (null for no timeout)
     * @return SpiceResult containing the response event or error
     */
    suspend fun awaitHitlResponse(
        toolCallId: String,
        timeoutMs: Long? = null
    ): SpiceResult<HITLResponseEvent>

    /**
     * Cancel a pending HITL request
     *
     * This method cancels a pending HITL request. Useful when the workflow
     * is cancelled or times out at a higher level.
     *
     * @param toolCallId The tool_call_id to cancel
     * @param reason Reason for cancellation
     * @return SpiceResult indicating success or failure
     */
    suspend fun cancelHitlRequest(
        toolCallId: String,
        reason: String = "Request cancelled"
    ): SpiceResult<Unit>
}

/**
 * No-op implementation for testing or when HITL is not configured
 *
 * This implementation:
 * - Accepts all emit requests (returns success)
 * - Always returns null for response checks
 * - Times out immediately on await
 * - Accepts all cancel requests
 */
object NoOpHitlEventEmitter : HitlEventEmitter {

    override suspend fun emitHitlRequest(metadata: HITLMetadata): SpiceResult<Unit> {
        return SpiceResult.success(Unit)
    }

    override suspend fun checkHitlResponse(toolCallId: String): HITLResponseEvent? {
        return null
    }

    override suspend fun awaitHitlResponse(
        toolCallId: String,
        timeoutMs: Long?
    ): SpiceResult<HITLResponseEvent> {
        return SpiceResult.success(
            HITLResponseEvent.timeout(toolCallId, "No HITL emitter configured")
        )
    }

    override suspend fun cancelHitlRequest(
        toolCallId: String,
        reason: String
    ): SpiceResult<Unit> {
        return SpiceResult.success(Unit)
    }
}

/**
 * Extension function to convert HITLResponseEvent to ToolResult
 *
 * This provides the 1:1 mapping from EventBus response to ToolResult:
 *
 * | HITLEventStatus | ToolResultStatus | Mapping |
 * |-----------------|------------------|---------|
 * | COMPLETED       | SUCCESS          | response.value as result |
 * | TIMEOUT         | TIMEOUT          | errorMessage as message |
 * | CANCELLED       | CANCELLED        | errorMessage as message |
 * | ERROR           | ERROR            | errorCode + errorMessage |
 */
fun HITLResponseEvent.toToolResult(): ToolResult {
    return when (status) {
        HITLEventStatus.COMPLETED -> {
            ToolResult(
                status = ToolResultStatus.SUCCESS,
                result = response?.let { resp ->
                    mapOf(
                        "value" to resp.value,
                        "selected_options" to resp.selectedOptions,
                        "response_type" to resp.responseType,
                        "timestamp" to resp.timestamp
                    )
                },
                message = "HITL response received",
                metadata = response?.metadata ?: emptyMap()
            )
        }
        HITLEventStatus.TIMEOUT -> {
            ToolResult.timeout(
                message = errorMessage ?: "HITL request timed out",
                metadata = mapOf("tool_call_id" to toolCallId)
            )
        }
        HITLEventStatus.CANCELLED -> {
            ToolResult.cancelled(
                message = errorMessage ?: "HITL request was cancelled",
                metadata = mapOf("tool_call_id" to toolCallId)
            )
        }
        HITLEventStatus.ERROR -> {
            ToolResult.error(
                error = errorMessage ?: "HITL request failed",
                errorCode = errorCode ?: "HITL_ERROR",
                metadata = mapOf("tool_call_id" to toolCallId)
            )
        }
    }
}

/**
 * Extension function to check if this emitter is the no-op implementation
 */
fun HitlEventEmitter.isNoOp(): Boolean = this === NoOpHitlEventEmitter
