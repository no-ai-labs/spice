package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node

/**
 * 📤 Output Node for Spice Framework 1.0.0
 *
 * Produces the final output of graph execution and transitions to COMPLETED state.
 *
 * **BREAKING CHANGE from 0.x:**
 * - Transitions message to COMPLETED state
 * - Output selected from message.content or message.data
 * - No separate result extraction
 *
 * **Architecture:**
 * ```
 * Input Message (RUNNING)
 *   ↓
 * OutputNode.run()
 *   ↓
 * Output Message (COMPLETED)
 *   - Final result in content/data
 *   - State = COMPLETED
 *   - Graph execution ends
 * ```
 *
 * **Usage:**
 * ```kotlin
 * graph("workflow") {
 *     agent("process", myAgent)
 *     output("result") { message ->
 *         // Extract final result from message
 *         message.getData<String>("processed_result")
 *     }
 *     edge("process", "result")
 * }
 * ```
 *
 * **Map Output (1.2.0+):**
 * When selector returns a Map, the map is merged into message.data.
 * This is useful for subgraph EXIT nodes that need to export data to parent.
 *
 * ```kotlin
 * output("exit") { message ->
 *     mapOf(
 *         "confirmed" to message.getData<Boolean>("user_confirmed"),
 *         "exitCode" to "SUCCESS"
 *     )
 * }
 * // Result: message.data = {...existing..., confirmed: true, exitCode: "SUCCESS"}
 * ```
 *
 * @property selector Function to extract final output from message
 * @since 1.0.0
 */
class OutputNode(
    override val id: String,
    val selector: (SpiceMessage) -> Any? = { it.content }
) : Node {

    /**
     * Execute output node - transitions to COMPLETED state
     *
     * **Flow:**
     * 1. Extract final result using selector
     * 2. Transition message to COMPLETED state
     * 3. If output is Map: merge into message.data (1.2.0+)
     * 4. Set content (Map → JSON string, otherwise toString())
     * 5. Return completed message
     *
     * **State Transition:**
     * - RUNNING → COMPLETED (graph execution finished)
     *
     * @param message Input message with final result
     * @return SpiceResult with message in COMPLETED state
     */
    override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        // Extract final output
        val output = selector(message)

        // Transition to COMPLETED state
        val completedMessage = message.transitionTo(
            newState = ExecutionState.COMPLETED,
            reason = "Graph execution completed successfully",
            nodeId = id
        )

        // Handle output based on type (1.2.0+: Map merges into data)
        val (finalData, finalContent) = when (output) {
            is Map<*, *> -> {
                // Merge map output into message.data for SubgraphNode outputMapping
                @Suppress("UNCHECKED_CAST")
                val mapOutput = output.filterKeys { it != null }
                    .mapKeys { it.key.toString() } as Map<String, Any>
                val mergedData = completedMessage.data + mapOutput
                // 1.6.1: message 키가 있으면 content로 사용, 없으면 빈 문자열
                // (내부 데이터가 사용자에게 노출되는 버그 수정)
                val contentMessage = mapOutput["message"]?.toString() ?: ""
                mergedData to contentMessage
            }
            else -> {
                // Default behavior: output goes to content only
                completedMessage.data to (output?.toString() ?: "")
            }
        }

        // Set final result
        val result = completedMessage.copy(
            content = finalContent,
            data = finalData
        )

        // Mark as output in metadata
        val finalMessage = result.withMetadata(
            mapOf(
                "isOutput" to true,
                "outputNodeId" to id
            )
        )

        return SpiceResult.success(finalMessage)
    }
}
