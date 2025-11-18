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
     * 3. Embed final result in message.content
     * 4. Return completed message
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

        // Set final result in content
        val result = completedMessage.copy(
            content = output?.toString() ?: ""
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
