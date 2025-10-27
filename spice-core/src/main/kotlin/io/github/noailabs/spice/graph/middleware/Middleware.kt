package io.github.noailabs.spice.graph.middleware

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.graph.runner.RunReport

/**
 * Middleware interface for intercepting and augmenting graph execution.
 * Inspired by Microsoft Agent Framework middleware system.
 */
interface Middleware {
    /**
     * Called once at the start of graph execution.
     */
    suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        next()
    }

    /**
     * Called before/after each node execution.
     * Chain pattern allows middleware to wrap node execution.
     */
    suspend fun onNode(req: NodeRequest, next: suspend (NodeRequest) -> NodeResult): NodeResult {
        return next(req)
    }

    /**
     * Called when an error occurs during execution.
     * Returns an ErrorAction to control error handling.
     */
    suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
        return ErrorAction.PROPAGATE
    }

    /**
     * Called once at the end of graph execution (success or failure).
     */
    suspend fun onFinish(report: RunReport) {
        // Default: no-op
    }
}

/**
 * Context for the overall graph execution.
 */
data class RunContext(
    val graphId: String,
    val runId: String,
    val agentContext: AgentContext?,
    val metadata: MutableMap<String, Any> = mutableMapOf()
)

/**
 * Request to execute a node.
 */
data class NodeRequest(
    val nodeId: String,
    val input: Any?,
    val context: RunContext
)

/**
 * Action to take when an error occurs.
 */
enum class ErrorAction {
    /**
     * Propagate the error up the stack (default behavior).
     */
    PROPAGATE,

    /**
     * Retry the current node execution.
     */
    RETRY,

    /**
     * Skip the current node and continue to the next.
     */
    SKIP,

    /**
     * Handle the error and continue with provided result.
     */
    CONTINUE
}
