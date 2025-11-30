package io.github.noailabs.spice.tools

import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.hitl.result.HITLMetadata
import io.github.noailabs.spice.hitl.result.HitlEventEmitter
import io.github.noailabs.spice.hitl.result.NoOpHitlEventEmitter
import io.github.noailabs.spice.toolspec.OAIToolCall

/**
 * HITL Request Input Tool
 *
 * This tool requests free-form text input from the user and returns
 * a WAITING_HITL status to pause graph execution until the user responds.
 *
 * **Key Design Decisions:**
 * - Returns `ToolResult.waitingHitl()` to signal HITL pause
 * - Generates stable `tool_call_id` using format: `hitl_{runId}_{nodeId}_{invocationIndex}`
 * - Same ID is generated on retry (idempotent), different ID for new invocations (loop-safe)
 * - Uses Port Interface pattern (HitlEventEmitter) for external notification
 *
 * **Usage in Graph DSL:**
 * ```kotlin
 * graph("form-flow") {
 *     hitlInput("get-name", "What is your name?")
 *     hitlInput("get-email", "What is your email?") {
 *         validation("email", "pattern" to "^[\\w.-]+@[\\w.-]+\\.\\w+\$")
 *     }
 *     output("result")
 *
 *     edge("get-name", "get-email")
 *     edge("get-email", "result")
 * }
 * ```
 *
 * **Parameter Mapping:**
 * The tool expects the following parameters from the message:
 * - `prompt` (required): The prompt message to display to the user
 * - `validation_rules` (optional): Validation rules for the input
 * - `timeout` (optional): Timeout in milliseconds
 * - `_hitl_invocation_index` (optional): Invocation index for loop-safe ID generation
 *
 * @property emitter Event emitter for notifying external systems
 * @since 1.0.6
 */
class HitlRequestInputTool(
    private val emitter: HitlEventEmitter = NoOpHitlEventEmitter,
    override val name: String = OAIToolCall.Companion.ToolNames.HITL_REQUEST_INPUT,
    override val description: String = "Request free-form text input from the user"
) : Tool {

    override suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        // Extract required parameters
        val prompt = params["prompt"] as? String
            ?: return SpiceResult.success(
                ToolResult.error(
                    error = "Missing required parameter: prompt",
                    errorCode = "MISSING_PARAM"
                )
            )

        // Extract optional parameters
        @Suppress("UNCHECKED_CAST")
        val validationRules = params["validation_rules"] as? Map<String, Any> ?: emptyMap()
        val timeout = (params["timeout"] as? Number)?.toLong()

        // Get invocation index for loop-safe ID generation
        // This value is passed from message.data and incremented per HITL invocation
        val invocationIndex = (params[HITLMetadata.INVOCATION_INDEX_KEY] as? Number)?.toInt() ?: 0

        // Get graph context (required for stable tool_call_id generation)
        val runId = context.graph.runId
        val nodeId = context.graph.nodeId
        val graphId = context.graph.graphId

        if (runId == null || nodeId == null) {
            return SpiceResult.success(
                ToolResult.error(
                    error = "HITL tool requires graph context (runId and nodeId)",
                    errorCode = "MISSING_CONTEXT"
                )
            )
        }

        // Generate stable tool_call_id (same ID on retry, different ID for new invocations)
        val toolCallId = HITLMetadata.generateToolCallId(runId, nodeId, invocationIndex)

        // Create HITL metadata
        val metadata = HITLMetadata.forInput(
            runId = runId,
            nodeId = nodeId,
            prompt = prompt,
            invocationIndex = invocationIndex,
            graphId = graphId,
            validationRules = validationRules,
            timeout = timeout,
            additionalMetadata = mapOf(
                "agent_id" to context.agentId,
                "correlation_id" to (context.correlationId ?: ""),
                "user_id" to (context.auth.userId ?: ""),
                "tenant_id" to (context.auth.tenantId ?: "")
            ).filterValues { it.isNotEmpty() }
        )

        // Emit HITL request to external systems (via Port Interface)
        val emitResult = emitter.emitHitlRequest(metadata)
        if (emitResult is SpiceResult.Failure) {
            return SpiceResult.success(
                ToolResult.error(
                    error = "Failed to emit HITL request: ${emitResult.error.message}",
                    errorCode = "EMIT_FAILED"
                )
            )
        }

        // Return WAITING_HITL result
        // This will cause GraphRunner to pause execution and save checkpoint
        return SpiceResult.success(
            ToolResult.waitingHitl(
                toolCallId = toolCallId,
                prompt = prompt,
                hitlType = HITLMetadata.TYPE_INPUT,
                metadata = mapOf(
                    "validation_rules" to validationRules,
                    "timeout" to (timeout ?: 0L),
                    "run_id" to runId,
                    "node_id" to nodeId,
                    "graph_id" to (graphId ?: ""),
                    // Store invocation index for next HITL request in loops
                    HITLMetadata.INVOCATION_INDEX_KEY to invocationIndex
                ).filterValues {
                    when (it) {
                        is String -> it.isNotEmpty()
                        is Number -> it.toLong() >= 0  // Allow 0 for invocation index
                        is Map<*, *> -> it.isNotEmpty()
                        else -> true
                    }
                }
            )
        )
    }

    companion object {
        /**
         * Create a HitlRequestInputTool with default settings
         */
        fun create(emitter: HitlEventEmitter = NoOpHitlEventEmitter): HitlRequestInputTool =
            HitlRequestInputTool(emitter)

        /**
         * Create parameters map for this tool
         *
         * Helper method for building tool parameters in DSL.
         *
         * @param prompt The prompt message to display
         * @param validationRules Optional validation rules
         * @param timeout Optional timeout in milliseconds
         */
        fun params(
            prompt: String,
            validationRules: Map<String, Any> = emptyMap(),
            timeout: Long? = null
        ): Map<String, Any> = buildMap {
            put("prompt", prompt)
            if (validationRules.isNotEmpty()) {
                put("validation_rules", validationRules)
            }
            if (timeout != null) {
                put("timeout", timeout)
            }
        }
    }
}
