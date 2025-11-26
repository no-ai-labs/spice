package io.github.noailabs.spice.graph

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult

/**
 * 🔷 Core Node abstraction for Spice Framework 1.0.0
 *
 * **BREAKING CHANGE from 0.x:**
 * - `run(ctx: NodeContext): SpiceResult<NodeResult>` → `run(message: SpiceMessage): SpiceResult<SpiceMessage>`
 * - Unified message format (no more Context/Result split)
 * - Direct message-to-message transformation
 * - Built-in state machine support
 *
 * **Architecture:**
 * ```
 * Input SpiceMessage → Node.run() → Output SpiceMessage
 *     (state: RUNNING)                    (state: RUNNING/WAITING/COMPLETED)
 * ```
 *
 * **Node Types:**
 * - **AgentNode**: Wraps Agent, processes with LLM
 * - **ToolNode**: Executes external tool calls
 * - **ToolNode (HITL)**: Pauses for human input (state → WAITING)
 * - **OutputNode**: Final output node (state → COMPLETED)
 * - **DecisionNode**: Conditional branching
 * - **MergeNode**: Merge multiple branches
 *
 * **Migration Example:**
 * ```kotlin
 * // OLD (0.x)
 * class MyNode : Node {
 *     override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
 *         val input = ctx.state["input"]
 *         return SpiceResult.success(
 *             NodeResult.fromContext(ctx, data = "result")
 *         )
 *     }
 * }
 *
 * // NEW (1.0)
 * class MyNode : Node {
 *     override val id = "my-node"
 *
 *     override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
 *         val input = message.content
 *         return SpiceResult.success(
 *             message.copy(content = "result")
 *         )
 *     }
 * }
 * ```
 *
 * @since 1.0.0
 */
interface Node {
    /**
     * Unique identifier for this node
     */
    val id: String

    /**
     * Execute node logic
     *
     * **Input Message:**
     * - Typically in RUNNING state
     * - Contains execution context in metadata
     * - May have tool calls from previous node
     *
     * **Output Message:**
     * - Must preserve correlationId (for message tracking)
     * - May change state (RUNNING → WAITING for HITL)
     * - May add tool calls
     * - May update metadata
     *
     * **State Transitions:**
     * - RUNNING → RUNNING (continue execution)
     * - RUNNING → WAITING (need human input)
     * - RUNNING → COMPLETED (final output)
     * - RUNNING → FAILED (error occurred)
     *
     * @param message Input message with execution state
     * @return SpiceResult with transformed message or error
     */
    suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage>
}

/**
 * 🎯 Base Node Implementation
 *
 * Provides common functionality for custom nodes:
 * - Automatic error handling
 * - Execution metrics
 * - State validation
 *
 * **Usage:**
 * ```kotlin
 * class TransformNode(override val id: String = "transform") : BaseNode() {
 *     override suspend fun execute(message: SpiceMessage): SpiceMessage {
 *         return message.copy(
 *             content = message.content.uppercase(),
 *             metadata = message.metadata + mapOf("transformed" to true)
 *         )
 *     }
 * }
 * ```
 *
 * @since 1.0.0
 */
abstract class BaseNode : Node {
    /**
     * Execution metrics
     */
    private var executionCount = 0L
    private var failureCount = 0L
    private var totalLatencyMs = 0L

    /**
     * Final run method with error handling and metrics
     */
    final override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        val startTime = System.currentTimeMillis()

        return try {
            // Validate input state
            if (message.state.isTerminal()) {
                return SpiceResult.failure(
                    io.github.noailabs.spice.error.SpiceError.executionError(
                        "Cannot execute node on terminal state: ${message.state}",
                        nodeId = id
                    )
                )
            }

            // Execute node logic
            val result = execute(message)

            // Record success
            executionCount++
            val latency = System.currentTimeMillis() - startTime
            totalLatencyMs += latency

            // Update graph context in output message
            val output = result.withGraphContext(
                graphId = message.graphId,
                nodeId = id,
                runId = message.runId
            )

            SpiceResult.success(output)
        } catch (e: Exception) {
            // Record failure
            failureCount++

            SpiceResult.failure(
                io.github.noailabs.spice.error.SpiceError.executionError(
                    "Node execution failed: ${e.message}",
                    nodeId = id,
                    cause = e
                )
            )
        }
    }

    /**
     * Execute node logic (to be implemented by subclasses)
     *
     * This method should focus on business logic only.
     * Error handling and metrics are handled by run() wrapper.
     *
     * @param message Input message
     * @return Transformed output message
     */
    protected abstract suspend fun execute(message: SpiceMessage): SpiceMessage

    /**
     * Get execution metrics
     */
    fun getMetrics(): NodeMetrics {
        return NodeMetrics(
            executionCount = executionCount,
            failureCount = failureCount,
            averageLatencyMs = if (executionCount > 0) totalLatencyMs.toDouble() / executionCount else 0.0
        )
    }
}

/**
 * 📊 Node Execution Metrics
 *
 * @property executionCount Total number of executions
 * @property failureCount Total number of failures
 * @property averageLatencyMs Average execution time in milliseconds
 */
data class NodeMetrics(
    val executionCount: Long,
    val failureCount: Long,
    val averageLatencyMs: Double
) {
    fun getSuccessRate(): Double {
        return if (executionCount > 0) {
            ((executionCount - failureCount).toDouble() / executionCount) * 100
        } else {
            0.0
        }
    }
}
