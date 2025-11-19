package io.github.noailabs.spice.springboot.statemachine.transformer

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph

/**
 * Interface for transforming SpiceMessages during graph execution.
 *
 * Transformers provide hooks at critical points in the execution lifecycle:
 * - Before graph execution starts (global context injection)
 * - Before each node execution (per-node context, tracing)
 * - After each node execution (result transformation, logging)
 * - After graph execution completes (cleanup, telemetry)
 *
 * **Use Cases:**
 * - Authentication context injection (logged-in/logged-out)
 * - Distributed tracing (span creation/propagation)
 * - Subgraph context management (super-graph → subgraph)
 * - Audit logging and telemetry
 * - Security: PII filtering, permission checks
 * - Error enrichment and recovery
 *
 * **Example - Auth Context Injection:**
 * ```kotlin
 * class AuthContextTransformer : MessageTransformer {
 *     override suspend fun beforeExecution(
 *         graph: Graph,
 *         message: SpiceMessage
 *     ): SpiceResult<SpiceMessage> {
 *         val isLoggedIn = detectAuth(message)
 *         return SpiceResult.success(
 *             message.withMetadata(mapOf("isLoggedIn" to isLoggedIn))
 *         )
 *     }
 * }
 * ```
 *
 * **Example - Distributed Tracing:**
 * ```kotlin
 * class TracingTransformer : MessageTransformer {
 *     override suspend fun beforeNode(
 *         graph: Graph,
 *         nodeId: String,
 *         message: SpiceMessage
 *     ): SpiceResult<SpiceMessage> {
 *         val span = tracer.startSpan("node:$nodeId")
 *         return SpiceResult.success(
 *             message.withMetadata(mapOf("spanId" to span.id))
 *         )
 *     }
 *
 *     override suspend fun afterNode(
 *         graph: Graph,
 *         nodeId: String,
 *         input: SpiceMessage,
 *         output: SpiceMessage
 *     ): SpiceResult<SpiceMessage> {
 *         val spanId = input.getMetadata<String>("spanId")
 *         tracer.endSpan(spanId)
 *         return SpiceResult.success(output)
 *     }
 * }
 * ```
 *
 * **Execution Order:**
 * ```
 * beforeExecution(graph, message)
 *   ↓
 * loop for each node:
 *   beforeNode(graph, nodeId, message)
 *     ↓
 *   [Node Execution]
 *     ↓
 *   afterNode(graph, nodeId, inputMessage, outputMessage)
 *   ↓
 * afterExecution(graph, inputMessage, outputMessage)
 * ```
 *
 * **Error Handling:**
 * - If any transformer returns `SpiceResult.Failure`, execution stops
 * - Use `SpiceResult.Success` with modified message to continue
 * - Transformers should be idempotent and side-effect free (except logging/telemetry)
 *
 * @since 1.0.0
 */
interface MessageTransformer {

    /**
     * Whether to continue graph execution if this transformer fails.
     *
     * - `false` (default): Stop execution on failure (for critical transformers like auth)
     * - `true`: Log warning and continue (for non-critical transformers like logging/metrics)
     *
     * **IMPORTANT:** The default is `false`, which stops graph execution on any failure.
     * If your transformer is non-critical (logging, metrics, tracing), you MUST override
     * this property to return `true`, otherwise a failure in your transformer will halt
     * the entire graph execution.
     *
     * **Examples:**
     * ```kotlin
     * // Critical transformer - use default (false)
     * class AuthContextTransformer : MessageTransformer {
     *     // continueOnFailure defaults to false - auth failure stops execution
     * }
     *
     * // Non-critical transformer - override to true
     * class LoggingTransformer : MessageTransformer {
     *     override val continueOnFailure: Boolean = true  // logging failure won't stop execution
     * }
     * ```
     *
     * **Built-in transformer defaults:**
     * - AuthContextTransformer: `false` - auth failure should stop execution
     * - SubgraphContextTransformer: `false` - depth limit exceeded should stop execution
     * - TracingTransformer: `true` - tracing failure shouldn't stop execution
     *
     * Note: This applies to beforeExecution, beforeNode, and afterNode hooks.
     * afterExecution always continues regardless of this setting (cleanup phase).
     */
    val continueOnFailure: Boolean
        get() = false

    /**
     * Transform message before graph execution starts.
     *
     * Called once at the beginning of graph execution.
     * Use for global context injection (auth, tenant, correlation IDs).
     *
     * @param graph The graph being executed
     * @param message The initial input message
     * @return Transformed message or failure
     */
    suspend fun beforeExecution(
        graph: Graph,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> = SpiceResult.success(message)

    /**
     * Transform message before node execution.
     *
     * Called before each node in the graph executes.
     * Use for node-specific context (tracing, permissions, preprocessing).
     *
     * @param graph The graph being executed
     * @param nodeId The ID of the node about to execute
     * @param message The message being passed to the node
     * @return Transformed message or failure
     */
    suspend fun beforeNode(
        graph: Graph,
        nodeId: String,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> = SpiceResult.success(message)

    /**
     * Transform message after node execution.
     *
     * Called after each node completes successfully.
     * Use for result transformation, logging, telemetry.
     *
     * @param graph The graph being executed
     * @param nodeId The ID of the node that just executed
     * @param input The message that was passed to the node
     * @param output The message returned by the node
     * @return Transformed output message or failure
     */
    suspend fun afterNode(
        graph: Graph,
        nodeId: String,
        input: SpiceMessage,
        output: SpiceMessage
    ): SpiceResult<SpiceMessage> = SpiceResult.success(output)

    /**
     * Transform message after graph execution completes.
     *
     * Called once when graph execution finishes (success or failure).
     * Use for cleanup, final telemetry, audit logging.
     *
     * Note: This is called even if execution failed, so check message state.
     *
     * @param graph The graph that was executed
     * @param input The original input message
     * @param output The final output message (may be in FAILED state)
     * @return Transformed output message or failure
     */
    suspend fun afterExecution(
        graph: Graph,
        input: SpiceMessage,
        output: SpiceMessage
    ): SpiceResult<SpiceMessage> = SpiceResult.success(output)

    /**
     * Handle errors that occur during transformation.
     *
     * Called if any transformer in the chain throws an exception.
     * Default behavior: wraps exception in SpiceResult.Failure.
     *
     * @param graph The graph being executed
     * @param message The message being transformed
     * @param error The exception that occurred
     * @return Error result (should be SpiceResult.Failure)
     */
    suspend fun onError(
        graph: Graph,
        message: SpiceMessage,
        error: Throwable
    ): SpiceResult<SpiceMessage> = SpiceResult.failure(
        io.github.noailabs.spice.error.SpiceError.executionError(
            "Transformer error: ${error.message}",
            cause = error
        )
    )
}
