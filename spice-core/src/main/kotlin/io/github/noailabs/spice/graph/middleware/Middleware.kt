package io.github.noailabs.spice.graph.middleware

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.ToolRegistry
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.error.ErrorReport
import io.github.noailabs.spice.error.ErrorReportAdapter
import io.github.noailabs.spice.graph.runner.ErrorAction
import io.github.noailabs.spice.state.ExecutionStateMachine
import io.github.noailabs.spice.validation.DeadLetterHandler
import io.github.noailabs.spice.validation.DeadLetterRecord
import io.github.noailabs.spice.validation.ValidationError

/**
 * 🔌 Middleware for Spice Framework 1.0.0
 *
 * Intercepts and augments graph execution with cross-cutting concerns.
 *
 * **BREAKING CHANGE from 0.x:**
 * - Works with SpiceMessage (not NodeContext/NodeResult)
 * - Simplified lifecycle: beforeNode, afterNode, onError
 * - ErrorAction returns SpiceMessage fallback instead of Any?
 *
 * **Middleware Pattern:**
 * ```
 * class LoggingMiddleware : Middleware {
 *     override suspend fun beforeNode(message: SpiceMessage): SpiceResult<SpiceMessage> {
 *         println("Executing node: ${message.nodeId}")
 *         return SpiceResult.success(message)
 *     }
 *
 *     override suspend fun afterNode(message: SpiceMessage): SpiceResult<SpiceMessage> {
 *         println("Node completed: ${message.nodeId}")
 *         return SpiceResult.success(message)
 *     }
 * }
 * ```
 *
 * @since 1.0.0
 */
interface Middleware {
    /**
     * Called before each node execution
     *
     * Can transform message before it reaches the node.
     * Return Failure to skip node execution and propagate error.
     *
     * @param message Input message for the node
     * @return Transformed message or error
     */
    suspend fun beforeNode(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return SpiceResult.success(message)
    }

    /**
     * Called after each node execution
     *
     * Can transform message after node completes.
     * Return Failure to treat node as failed.
     *
     * @param message Output message from the node
     * @return Transformed message or error
     */
    suspend fun afterNode(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return SpiceResult.success(message)
    }

    /**
     * Called when an error occurs during node execution
     *
     * Returns ErrorAction to control error handling.
     *
     * @param error The error that occurred
     * @param message The message being processed when error occurred
     * @return Action to take (Propagate, Skip, Retry, Fallback)
     */
    suspend fun onError(error: SpiceError, message: SpiceMessage): ErrorAction {
        return ErrorAction.Propagate
    }
}

/**
 * 🔗 Middleware Chain
 *
 * Composes multiple middleware into a single chain.
 *
 * **Usage:**
 * ```kotlin
 * val chain = MiddlewareChain(
 *     LoggingMiddleware(),
 *     ValidationMiddleware(),
 *     MetricsMiddleware()
 * )
 * ```
 *
 * @since 1.0.0
 */
class MiddlewareChain(
    private val middlewares: List<Middleware>
) : Middleware {
    constructor(vararg middlewares: Middleware) : this(middlewares.toList())

    override suspend fun beforeNode(message: SpiceMessage): SpiceResult<SpiceMessage> {
        var currentMessage = message
        for (middleware in middlewares) {
            val result = middleware.beforeNode(currentMessage)
            when (result) {
                is SpiceResult.Success -> currentMessage = result.value
                is SpiceResult.Failure -> return result
            }
        }
        return SpiceResult.success(currentMessage)
    }

    override suspend fun afterNode(message: SpiceMessage): SpiceResult<SpiceMessage> {
        var currentMessage = message
        for (middleware in middlewares) {
            val result = middleware.afterNode(currentMessage)
            when (result) {
                is SpiceResult.Success -> currentMessage = result.value
                is SpiceResult.Failure -> return result
            }
        }
        return SpiceResult.success(currentMessage)
    }

    override suspend fun onError(error: SpiceError, message: SpiceMessage): ErrorAction {
        for (middleware in middlewares) {
            val action = middleware.onError(error, message)
            if (action !is ErrorAction.Propagate) {
                return action
            }
        }
        return ErrorAction.Propagate
    }
}

/**
 * 📊 Abstract base middleware with lifecycle hooks
 *
 * Provides simplified hooks for common middleware patterns.
 *
 * **Usage:**
 * ```kotlin
 * class MyMiddleware : BaseMiddleware() {
 *     override suspend fun onBeforeExecution(message: SpiceMessage): SpiceMessage {
 *         return message.withMetadata(mapOf("middleware" to "applied"))
 *     }
 * }
 * ```
 *
 * @since 1.0.0
 */
abstract class BaseMiddleware : Middleware {
    /**
     * Hook: Called before node execution
     * Override to transform message before node
     */
    protected open suspend fun onBeforeExecution(message: SpiceMessage): SpiceMessage {
        return message
    }

    /**
     * Hook: Called after node execution
     * Override to transform message after node
     */
    protected open suspend fun onAfterExecution(message: SpiceMessage): SpiceMessage {
        return message
    }

    /**
     * Hook: Called on error
     * Override to customize error handling
     */
    protected open suspend fun onErrorOccurred(error: SpiceError, message: SpiceMessage): ErrorAction {
        return ErrorAction.Propagate
    }

    final override suspend fun beforeNode(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return SpiceResult.catching {
            onBeforeExecution(message)
        }
    }

    final override suspend fun afterNode(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return SpiceResult.catching {
            onAfterExecution(message)
        }
    }

    final override suspend fun onError(error: SpiceError, message: SpiceMessage): ErrorAction {
        return onErrorOccurred(error, message)
    }
}

/**
 * Middleware that enforces state transitions through ExecutionStateMachine.
 */
class StateTransitionMiddleware(
    private val stateMachine: ExecutionStateMachine = ExecutionStateMachine()
) : Middleware {
    override suspend fun beforeNode(message: SpiceMessage): SpiceResult<SpiceMessage> = SpiceResult.catching {
        stateMachine.ensureHistoryValid(message)
        if (message.state == ExecutionState.READY) {
            stateMachine.transition(
                message = message,
                target = ExecutionState.RUNNING,
                reason = "Node execution started",
                nodeId = message.nodeId
            )
        } else {
            message
        }
    }

    override suspend fun afterNode(message: SpiceMessage): SpiceResult<SpiceMessage> = SpiceResult.catching {
        stateMachine.ensureHistoryValid(message)
        message
    }
}

/**
 * Middleware that enforces ToolRegistry policies and emits ErrorReport tool calls on violations.
 */
class ToolPolicyMiddleware(
    private val registry: ToolRegistry,
    private val requiredTags: Set<String> = emptySet(),
    private val deadLetterHandler: DeadLetterHandler? = null
) : Middleware {
    override suspend fun beforeNode(message: SpiceMessage): SpiceResult<SpiceMessage> {
        val toolName = message.metadata["requestedTool"] as? String ?: return SpiceResult.success(message)
        val namespace = message.metadata["toolNamespace"] as? String ?: "global"
        val wrapper = registry.getWrapper(toolName, namespace)
            ?: return SpiceResult.failure(SpiceError.toolError("Tool not registered: $toolName", toolName))

        if (requiredTags.isNotEmpty() && !wrapper.tags.containsAll(requiredTags)) {
            val report = ErrorReport(
                code = "POLICY_VIOLATION",
                reason = "Tool $toolName missing required tags $requiredTags",
                recoverable = false,
                context = wrapper.metadata
            )
            val violationMessage = message.withToolCall(ErrorReportAdapter.toToolCall(report))
            deadLetterHandler?.handle(
                DeadLetterRecord(
                    payloadType = "ToolPolicyViolation",
                    payload = violationMessage,
                    errors = listOf(ValidationError("tool", report.reason))
                )
            )
            return SpiceResult.failure(SpiceError.toolError(report.reason, toolName))
        }

        return SpiceResult.success(
            message.withMetadata(
                mapOf(
                    "toolSource" to wrapper.source,
                    "toolTags" to wrapper.tags
                )
            )
        )
    }
}
