package io.github.noailabs.spice.tool

import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.SpiceError
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Listener interface for tool execution lifecycle events.
 *
 * Implement this interface to hook into tool execution for observability,
 * metrics collection, alerting, or custom logging.
 *
 * **Lifecycle Flow:**
 * ```
 * onInvoke() → tool.execute() → onSuccess()/onFailure() → onComplete()
 * ```
 *
 * **Usage:**
 * ```kotlin
 * class MetricsListener : ToolLifecycleListener {
 *     override suspend fun onInvoke(context: ToolInvocationContext) {
 *         metrics.counter("tool.invocations").increment()
 *     }
 *
 *     override suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long) {
 *         metrics.timer("tool.duration").record(durationMs, TimeUnit.MILLISECONDS)
 *     }
 *
 *     override suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long) {
 *         metrics.counter("tool.failures").increment()
 *     }
 *
 *     override suspend fun onComplete(context: ToolInvocationContext) {
 *         // Cleanup or final logging
 *     }
 * }
 * ```
 *
 * @since 1.0.0
 */
interface ToolLifecycleListener {
    /**
     * Called before tool execution begins.
     *
     * @param context Invocation context with tool, params, and runtime info
     */
    suspend fun onInvoke(context: ToolInvocationContext)

    /**
     * Called when tool execution succeeds.
     *
     * This is called for both SpiceResult.Success with ToolResult.success=true
     * and SpiceResult.Success with ToolResult.success=false (tool returned error result).
     *
     * @param context Invocation context
     * @param result The tool result
     * @param durationMs Execution duration in milliseconds
     */
    suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long)

    /**
     * Called when tool execution fails with SpiceResult.Failure.
     *
     * @param context Invocation context
     * @param error The SpiceError that caused the failure
     * @param durationMs Execution duration in milliseconds
     */
    suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long)

    /**
     * Called after tool execution completes (success or failure).
     *
     * Always called in a finally block, even if exceptions occur.
     *
     * @param context Invocation context
     */
    suspend fun onComplete(context: ToolInvocationContext)
}

/**
 * Context provided to lifecycle listeners during tool invocation.
 *
 * Contains all relevant information about the tool execution for
 * metrics labeling, logging, and tracing.
 *
 * @property tool The tool being executed
 * @property toolContext The ToolContext passed to the tool (contains auth, tracing, graph context)
 * @property params Parameters passed to the tool
 * @property startTime When the invocation started
 * @property attemptNumber Current attempt number (1-based, for retries)
 * @since 1.0.0
 */
data class ToolInvocationContext(
    val tool: Tool,
    val toolContext: ToolContext,
    val params: Map<String, Any>,
    val startTime: Instant,
    val attemptNumber: Int = 1
) {
    /**
     * Tool name for metrics/logging
     */
    val toolName: String get() = tool.name

    /**
     * Graph ID from tool context
     */
    val graphId: String? get() = toolContext.graph.graphId

    /**
     * Run ID from tool context
     */
    val runId: String? get() = toolContext.graph.runId

    /**
     * Node ID from tool context
     */
    val nodeId: String? get() = toolContext.graph.nodeId

    /**
     * Correlation ID for distributed tracing
     */
    val correlationId: String? get() = toolContext.correlationId

    /**
     * Agent ID executing the tool
     */
    val agentId: String get() = toolContext.agentId

    /**
     * User ID from auth context
     */
    val userId: String? get() = toolContext.auth.userId

    /**
     * Tenant ID from auth context
     */
    val tenantId: String? get() = toolContext.auth.tenantId

    /**
     * Trace ID from tracing context
     */
    val traceId: String? get() = toolContext.tracing.traceId

    /**
     * Span ID from tracing context
     */
    val spanId: String? get() = toolContext.tracing.spanId

    companion object {
        /**
         * Create invocation context for a tool execution
         */
        fun create(
            tool: Tool,
            toolContext: ToolContext,
            params: Map<String, Any>,
            attemptNumber: Int = 1
        ): ToolInvocationContext {
            return ToolInvocationContext(
                tool = tool,
                toolContext = toolContext,
                params = params,
                startTime = Clock.System.now(),
                attemptNumber = attemptNumber
            )
        }
    }
}

/**
 * No-op implementation of ToolLifecycleListener.
 *
 * Used as default when no listeners are configured.
 */
object NoOpToolLifecycleListener : ToolLifecycleListener {
    override suspend fun onInvoke(context: ToolInvocationContext) {}
    override suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long) {}
    override suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long) {}
    override suspend fun onComplete(context: ToolInvocationContext) {}
}
