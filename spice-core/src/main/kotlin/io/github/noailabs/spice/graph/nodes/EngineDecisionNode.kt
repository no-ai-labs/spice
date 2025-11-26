package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.routing.DecisionContext
import io.github.noailabs.spice.routing.DecisionEngine
import io.github.noailabs.spice.routing.DecisionLifecycleListener
import io.github.noailabs.spice.routing.DecisionResult
import org.slf4j.LoggerFactory

/**
 * A node that routes execution based on external DecisionEngine evaluation.
 *
 * Unlike [DecisionNode] which uses inline conditions, EngineDecisionNode delegates
 * routing decisions to an external [DecisionEngine]. This enables:
 * - ML/LLM-based routing decisions
 * - External service consultation
 * - Complex business logic encapsulation
 * - Easy testing via engine mocking
 *
 * ## Routing Mechanism
 *
 * The node stores the decision result in message data:
 * - `_decisionResult`: The resultId string (used for edge matching)
 * - `_decisionTarget`: The resolved target node ID
 * - `_decisionEngine`: The engine ID that made the decision
 * - `_decisionNodeId`: This node's ID
 * - Plus any metadata from the DecisionResult
 *
 * Edge matching is done by comparing `_decisionResult` with the resultId
 * specified in the DSL's `.on(result).to(target)` mapping.
 *
 * ## Usage
 *
 * ```kotlin
 * graph("approval-flow") {
 *     decisionNode("check-amount")
 *         .by(amountClassifier)
 *         .on(StandardResult.YES).to("auto-approve")
 *         .on(StandardResult.NO).to("reject")
 *         .on(DelegationResult.DELEGATE_TO_AGENT("supervisor")).to("supervisor-review")
 *         .otherwise("manual-review")
 *
 *     agent("auto-approve", autoApproveAgent)
 *     // ...
 * }
 * ```
 *
 * @property id Node identifier
 * @property engine The decision engine to invoke
 * @property resultToTargetMap Mapping from resultId to target node ID
 * @property fallbackTarget Target node when no mapping matches (optional)
 * @property lifecycleListener Listener for decision lifecycle events
 *
 * @since 1.0.7
 */
class EngineDecisionNode(
    override val id: String,
    private val engine: DecisionEngine,
    private val resultToTargetMap: Map<String, String>,
    private val fallbackTarget: String? = null,
    private val lifecycleListener: DecisionLifecycleListener = DecisionLifecycleListener.NOOP
) : Node {

    private val logger = LoggerFactory.getLogger(EngineDecisionNode::class.java)

    override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        logger.debug(
            "Evaluating engine decision node '{}' with engine '{}', {} mappings",
            id, engine.id, resultToTargetMap.size
        )

        val context = DecisionContext.from(engine, message).copy(nodeId = id)
        val startTime = System.currentTimeMillis()

        // Notify listener
        lifecycleListener.onDecisionStart(context)

        // Evaluate decision engine
        val result: DecisionResult
        try {
            result = when (val engineResult = engine.evaluate(message)) {
                is SpiceResult.Success -> engineResult.value
                is SpiceResult.Failure -> {
                    val durationMs = System.currentTimeMillis() - startTime
                    lifecycleListener.onDecisionError(context, engineResult.error, durationMs)
                    return SpiceResult.failure(engineResult.error)
                }
            }
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTime
            val error = SpiceError.executionError(
                "Decision engine '${engine.id}' threw exception: ${e.message}",
                nodeId = id,
                cause = e
            )
            lifecycleListener.onDecisionError(context, error, durationMs)
            return SpiceResult.failure(error)
        }

        val durationMs = System.currentTimeMillis() - startTime
        val resultId = result.resultId

        // Find target from mapping
        val target = resultToTargetMap[resultId]
            ?: handleFallback(context, resultId, durationMs)
            ?: return noTargetError(resultId, durationMs, context)

        logger.debug(
            "Decision '{}': engine='{}' result='{}' -> target='{}' ({}ms)",
            id, engine.id, resultId, target, durationMs
        )

        // Check if fallback was used
        val usedFallback = resultToTargetMap[resultId] == null
        if (usedFallback) {
            lifecycleListener.onDecisionFallback(context, resultId, target, durationMs)
        } else {
            lifecycleListener.onDecisionComplete(context, result, durationMs)
        }

        // Build output message with decision metadata
        val outputData = message.data + buildDecisionMetadata(result, target, usedFallback)

        return SpiceResult.success(
            message.copy(data = outputData).withGraphContext(
                graphId = message.graphId,
                nodeId = id,
                runId = message.runId
            )
        )
    }

    /**
     * Handle fallback when no mapping matches.
     * Returns fallback target if available, null otherwise.
     */
    private fun handleFallback(context: DecisionContext, resultId: String, durationMs: Long): String? {
        if (fallbackTarget != null) {
            logger.info(
                "Decision '{}': no mapping for result '{}', using fallback -> '{}'",
                id, resultId, fallbackTarget
            )
            return fallbackTarget
        }
        return null
    }

    /**
     * Build error result when no target found.
     */
    private suspend fun noTargetError(
        resultId: String,
        durationMs: Long,
        context: DecisionContext
    ): SpiceResult<SpiceMessage> {
        val availableResults = resultToTargetMap.keys.toList()
        val error = SpiceError.routingError(
            message = "No target mapping for decision result '$resultId' in node '$id'. " +
                    "Use .otherwise(target) to handle unmapped results.",
            engineId = engine.id,
            resultId = resultId,
            nodeId = id,
            availableTargets = availableResults
        )

        logger.error(
            "Decision '{}': no mapping for result '{}'. Available mappings: {}",
            id, resultId, availableResults
        )

        lifecycleListener.onDecisionError(context, error, durationMs)
        return SpiceResult.failure(error)
    }

    /**
     * Build decision metadata to add to message data.
     */
    private fun buildDecisionMetadata(
        result: DecisionResult,
        target: String,
        usedFallback: Boolean
    ): Map<String, Any> {
        val base = mapOf(
            "_decisionResult" to result.resultId,
            "_decisionTarget" to target,
            "_decisionEngine" to engine.id,
            "_decisionNodeId" to id,
            "_decisionDescription" to result.description,
            "_decisionUsedFallback" to usedFallback
        )

        // Add result metadata with prefix
        val resultMetadata = result.metadata.mapKeys { "_decision.${it.key}" }

        return base + resultMetadata
    }

    /**
     * Get engine ID for debugging.
     */
    val engineId: String get() = engine.id

    /**
     * Get available result mappings for debugging.
     */
    val availableMappings: Set<String> get() = resultToTargetMap.keys
}
