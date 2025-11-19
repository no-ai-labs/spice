package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node
import org.slf4j.LoggerFactory

/**
 * A node that routes execution to different branches based on conditions.
 *
 * DecisionNode evaluates branches in order and sets metadata indicating
 * which branch was selected. The GraphRunner then uses edge conditions
 * to route to the appropriate next node.
 *
 * **Example Usage:**
 * ```kotlin
 * graph("workflow") {
 *     decision("route_by_payment") {
 *         branch("has_payment", "payment_handler")
 *             .whenData("paymentMethod") { it != null }
 *         branch("no_payment", "default_handler")
 *             .otherwise()
 *     }
 *
 *     agent("payment_handler", paymentAgent)
 *     agent("default_handler", defaultAgent)
 * }
 * ```
 *
 * The decision node will:
 * 1. Evaluate branches in order
 * 2. Set `data["_selectedBranch"]` to the selected branch target
 * 3. Automatically generated edges use this to route
 *
 * @since 1.0.0
 */
class DecisionNode(
    override val id: String,
    private val branches: List<DecisionBranch>
) : Node {

    private val logger = LoggerFactory.getLogger(DecisionNode::class.java)

    override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        logger.debug("Evaluating decision node '{}' with {} branches", id, branches.size)

        // Evaluate branches in order
        for (branch in branches) {
            try {
                if (branch.condition(message)) {
                    logger.debug(
                        "Decision '{}': selected branch '{}' -> target '{}'",
                        id, branch.name, branch.target
                    )

                    return SpiceResult.success(
                        message.copy(
                            data = message.data + mapOf(
                                "_selectedBranch" to branch.target,
                                "_decisionNodeId" to id,
                                "_branchName" to branch.name
                            )
                        ).withGraphContext(
                            graphId = message.graphId,
                            nodeId = id,
                            runId = message.runId
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error("Error evaluating branch '{}' in decision '{}': {}", branch.name, id, e.message)
                return SpiceResult.failure(
                    SpiceError.executionError(
                        "Decision branch evaluation failed: ${e.message}",
                        cause = e
                    )
                )
            }
        }

        // No branch matched
        logger.warn("Decision '{}': no branch matched", id)
        return SpiceResult.failure(
            SpiceError.executionError(
                "No decision branch matched in node '$id'. " +
                "Consider adding an otherwise() branch as fallback."
            )
        )
    }
}

/**
 * Represents a single branch in a DecisionNode.
 *
 * @property name Human-readable name for the branch (for logging/debugging)
 * @property target The node ID to route to if this branch is selected
 * @property condition Function that evaluates whether this branch should be taken
 */
data class DecisionBranch(
    val name: String,
    val target: String,
    val condition: (SpiceMessage) -> Boolean
)
