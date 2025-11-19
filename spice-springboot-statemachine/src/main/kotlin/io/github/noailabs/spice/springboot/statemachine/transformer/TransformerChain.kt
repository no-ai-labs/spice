package io.github.noailabs.spice.springboot.statemachine.transformer

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import org.slf4j.LoggerFactory

/**
 * Executes a chain of MessageTransformers in sequence.
 *
 * Transformers are executed in order, with each transformer receiving
 * the output of the previous transformer. If any transformer returns
 * a failure, the chain stops and returns that failure.
 *
 * **Example:**
 * ```kotlin
 * val chain = TransformerChain(listOf(
 *     AuthContextTransformer(),
 *     TracingTransformer(),
 *     TenantIsolationTransformer()
 * ))
 *
 * val result = chain.beforeExecution(graph, message)
 * ```
 *
 * @property transformers List of transformers to execute in order
 */
class TransformerChain(
    private val transformers: List<MessageTransformer>
) {
    private val logger = LoggerFactory.getLogger(TransformerChain::class.java)

    /**
     * Execute beforeExecution on all transformers in sequence.
     */
    suspend fun beforeExecution(
        graph: Graph,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        if (transformers.isEmpty()) {
            return SpiceResult.success(message)
        }

        var current = message

        for (transformer in transformers) {
            val result = try {
                transformer.beforeExecution(graph, current)
            } catch (e: Exception) {
                logger.error(
                    "Transformer ${transformer::class.simpleName} threw exception in beforeExecution",
                    e
                )
                if (transformer.continueOnFailure) {
                    logger.warn("Continuing despite transformer exception (continueOnFailure=true)")
                    continue
                }
                return transformer.onError(graph, current, e)
            }

            when (result) {
                is SpiceResult.Success -> {
                    current = result.value
                }
                is SpiceResult.Failure -> {
                    if (transformer.continueOnFailure) {
                        logger.warn(
                            "Transformer ${transformer::class.simpleName} failed in beforeExecution but continuing: ${result.error.message}"
                        )
                        // Continue with current message (don't update)
                    } else {
                        logger.warn(
                            "Transformer ${transformer::class.simpleName} failed in beforeExecution: ${result.error.message}"
                        )
                        return result
                    }
                }
            }
        }

        return SpiceResult.success(current)
    }

    /**
     * Execute beforeNode on all transformers in sequence.
     */
    suspend fun beforeNode(
        graph: Graph,
        nodeId: String,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        if (transformers.isEmpty()) {
            return SpiceResult.success(message)
        }

        var current = message

        for (transformer in transformers) {
            val result = try {
                transformer.beforeNode(graph, nodeId, current)
            } catch (e: Exception) {
                logger.error(
                    "Transformer ${transformer::class.simpleName} threw exception in beforeNode for node $nodeId",
                    e
                )
                return transformer.onError(graph, current, e)
            }

            when (result) {
                is SpiceResult.Success -> {
                    current = result.value
                }
                is SpiceResult.Failure -> {
                    if (transformer.continueOnFailure) {
                        logger.warn(
                            "Transformer ${transformer::class.simpleName} failed in beforeNode for node $nodeId but continuing: ${result.error.message}"
                        )
                        // Continue with current message (don't update)
                    } else {
                        logger.warn(
                            "Transformer ${transformer::class.simpleName} failed in beforeNode for node $nodeId: ${result.error.message}"
                        )
                        return result
                    }
                }
            }
        }

        return SpiceResult.success(current)
    }

    /**
     * Execute afterNode on all transformers in sequence.
     */
    suspend fun afterNode(
        graph: Graph,
        nodeId: String,
        input: SpiceMessage,
        output: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        if (transformers.isEmpty()) {
            return SpiceResult.success(output)
        }

        var current = output

        for (transformer in transformers) {
            val result = try {
                transformer.afterNode(graph, nodeId, input, current)
            } catch (e: Exception) {
                logger.error(
                    "Transformer ${transformer::class.simpleName} threw exception in afterNode for node $nodeId",
                    e
                )
                return transformer.onError(graph, current, e)
            }

            when (result) {
                is SpiceResult.Success -> {
                    current = result.value
                }
                is SpiceResult.Failure -> {
                    if (transformer.continueOnFailure) {
                        logger.warn(
                            "Transformer ${transformer::class.simpleName} failed in afterNode for node $nodeId but continuing: ${result.error.message}"
                        )
                        // Continue with current message (don't update)
                    } else {
                        logger.warn(
                            "Transformer ${transformer::class.simpleName} failed in afterNode for node $nodeId: ${result.error.message}"
                        )
                        return result
                    }
                }
            }
        }

        return SpiceResult.success(current)
    }

    /**
     * Execute afterExecution on all transformers in sequence.
     */
    suspend fun afterExecution(
        graph: Graph,
        input: SpiceMessage,
        output: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        if (transformers.isEmpty()) {
            return SpiceResult.success(output)
        }

        var current = output

        for (transformer in transformers) {
            val result = try {
                transformer.afterExecution(graph, input, current)
            } catch (e: Exception) {
                logger.error(
                    "Transformer ${transformer::class.simpleName} threw exception in afterExecution",
                    e
                )
                // Don't call onError here - we're already in cleanup phase
                // Just log and continue
                continue
            }

            when (result) {
                is SpiceResult.Success -> {
                    current = result.value
                }
                is SpiceResult.Failure -> {
                    logger.warn(
                        "Transformer ${transformer::class.simpleName} failed in afterExecution: ${result.error.message}"
                    )
                    // Don't stop execution in cleanup phase
                    continue
                }
            }
        }

        return SpiceResult.success(current)
    }

    /**
     * Check if the chain has any transformers.
     */
    fun isEmpty(): Boolean = transformers.isEmpty()

    /**
     * Get the number of transformers in the chain.
     */
    fun size(): Int = transformers.size
}

/**
 * Extension function to create a TransformerChain from a list.
 */
fun List<MessageTransformer>.toChain(): TransformerChain = TransformerChain(this)
