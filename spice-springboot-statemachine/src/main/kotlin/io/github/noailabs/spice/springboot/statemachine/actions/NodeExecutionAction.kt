package io.github.noailabs.spice.springboot.statemachine.actions

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.runner.GraphRunner
import io.github.noailabs.spice.springboot.statemachine.transformer.TransformerChain
import org.slf4j.LoggerFactory

/**
 * Executes a graph via the configured GraphRunner.
 *
 * Supports MessageTransformers for injecting context before/after node execution.
 *
 * @param graphRunner The underlying graph executor
 * @param transformerChain Optional transformer chain for beforeNode/afterNode hooks
 */
class NodeExecutionAction(
    private val graphRunner: GraphRunner,
    private val transformerChain: TransformerChain? = null
) {
    private val logger = LoggerFactory.getLogger(NodeExecutionAction::class.java)

    suspend fun execute(graph: Graph, message: SpiceMessage): SpiceResult<SpiceMessage> {
        val nodeId = message.nodeId ?: graph.entryPoint

        // Apply beforeNode transformers
        val transformedInput = if (transformerChain != null) {
            transformerChain.beforeNode(graph, nodeId, message)
        } else {
            SpiceResult.success(message)
        }

        val inputMessage = when (transformedInput) {
            is SpiceResult.Success -> transformedInput.value
            is SpiceResult.Failure -> {
                logger.error("Transformer chain failed in beforeNode for node $nodeId: ${transformedInput.error.message}")
                return transformedInput
            }
        }

        // Execute node
        val result = graphRunner.execute(graph, inputMessage)

        // Apply afterNode transformers (only if execution succeeded)
        val transformedOutput = when (result) {
            is SpiceResult.Success -> {
                if (transformerChain != null) {
                    transformerChain.afterNode(graph, nodeId, inputMessage, result.value)
                } else {
                    result
                }
            }
            is SpiceResult.Failure -> result
        }

        return transformedOutput
    }
}
