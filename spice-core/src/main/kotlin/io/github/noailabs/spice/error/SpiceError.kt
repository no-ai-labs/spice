package io.github.noailabs.spice.error


/**
 * 🚨 Spice Error Hierarchy
 *
 * Typed error system for Spice Framework providing structured error handling
 * with context, recovery hints, and proper error categorization.
 */
sealed class SpiceError {

    /**
     * Human-readable error message
     */
    abstract val message: String

    /**
     * Error code for programmatic handling
     */
    abstract val code: String

    /**
     * Optional cause (another error or exception)
     */
    open val cause: Throwable? = null

    /**
     * Contextual data for debugging
     */
    open val context: Map<String, Any> = emptyMap()

    /**
     * Timestamp when error occurred
     */
    val timestamp: Long = System.currentTimeMillis()

    /**
     * Convert to exception for throwing
     */
    fun toException(): SpiceException = SpiceException(this)

    /**
     * Add context to error
     */
    fun withContext(vararg pairs: Pair<String, Any>): SpiceError = when (this) {
        is AgentError -> copy(context = context + pairs.toMap())
        is ExecutionError -> copy(context = context + pairs.toMap())
        is ToolError -> copy(context = context + pairs.toMap())
        is ConfigurationError -> copy(context = context + pairs.toMap())
        is ValidationError -> copy(context = context + pairs.toMap())
        is NetworkError -> copy(context = context + pairs.toMap())
        is TimeoutError -> copy(context = context + pairs.toMap())
        is AuthenticationError -> copy(context = context + pairs.toMap())
        is RateLimitError -> copy(context = context + pairs.toMap())
        is SerializationError -> copy(context = context + pairs.toMap())
        is CheckpointError -> copy(context = context + pairs.toMap())
        is UnknownError -> copy(context = context + pairs.toMap())
        is ToolLookupError -> copy(context = context + pairs.toMap())
    }

    // =========================================
    // ERROR TYPES
    // =========================================

    /**
     * Agent-related errors (processing, communication, etc.)
     */
    data class AgentError(
        override val message: String,
        override val cause: Throwable? = null,
        val agentId: String? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "AGENT_ERROR"
    }

    /**
     * Graph/Node execution errors
     */
    data class ExecutionError(
        override val message: String,
        val graphId: String? = null,
        val nodeId: String? = null,
        override val cause: Throwable? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "EXECUTION_ERROR"
    }

    /**
     * Tool execution errors
     */
    data class ToolError(
        override val message: String,
        val toolName: String,
        override val cause: Throwable? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "TOOL_ERROR"
    }

    /**
     * Configuration errors (invalid config, missing required fields)
     */
    data class ConfigurationError(
        override val message: String,
        val field: String? = null,
        override val cause: Throwable? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "CONFIG_ERROR"
    }

    /**
     * Validation errors (invalid input, schema mismatch)
     */
    data class ValidationError(
        override val message: String,
        val field: String? = null,
        val expectedType: String? = null,
        val actualValue: Any? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "VALIDATION_ERROR"
    }

    /**
     * Network/HTTP errors
     */
    data class NetworkError(
        override val message: String,
        val statusCode: Int? = null,
        val endpoint: String? = null,
        override val cause: Throwable? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "NETWORK_ERROR"
    }

    /**
     * Timeout errors
     */
    data class TimeoutError(
        override val message: String,
        val timeoutMs: Long? = null,
        val operation: String? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "TIMEOUT_ERROR"
    }

    /**
     * Authentication/Authorization errors
     */
    data class AuthenticationError(
        override val message: String,
        val provider: String? = null,
        override val cause: Throwable? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "AUTH_ERROR"
    }

    /**
     * Rate limiting errors
     */
    data class RateLimitError(
        override val message: String,
        val retryAfterMs: Long? = null,
        val limitType: String? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "RATE_LIMIT_ERROR"
    }

    /**
     * Serialization/Deserialization errors
     */
    data class SerializationError(
        override val message: String,
        val format: String? = null,
        override val cause: Throwable? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "SERIALIZATION_ERROR"
    }

    /**
     * Checkpoint-related errors
     */
    data class CheckpointError(
        override val message: String,
        val checkpointId: String? = null,
        override val cause: Throwable? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "CHECKPOINT_ERROR"
    }

    /**
     * Unknown/Unexpected errors
     */
    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "UNKNOWN_ERROR"
    }

    /**
     * Tool lookup/resolution errors (dynamic tool selection failures)
     */
    data class ToolLookupError(
        override val message: String,
        val toolName: String? = null,
        val namespace: String? = null,
        val availableTools: List<String>? = null,
        override val cause: Throwable? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "TOOL_LOOKUP_ERROR"

        /**
         * Get detailed error message including available tools
         */
        fun toDetailedMessage(): String = buildString {
            append(message)
            if (!availableTools.isNullOrEmpty()) {
                append("\n\nAvailable tools in namespace '${namespace ?: "global"}':")
                availableTools.take(10).forEach { append("\n  - $it") }
                if (availableTools.size > 10) {
                    append("\n  ... and ${availableTools.size - 10} more")
                }
            }
        }
    }

    companion object {
        /**
         * Create error from exception
         */
        fun fromException(throwable: Throwable): SpiceError {
            return when (throwable) {
                is SpiceException -> throwable.error
                is java.net.UnknownHostException -> NetworkError(
                    message = "Unknown host: ${throwable.message}",
                    cause = throwable
                )
                is java.net.SocketTimeoutException -> TimeoutError(
                    message = "Network timeout: ${throwable.message}",
                    operation = "network_call"
                )
                is kotlinx.serialization.SerializationException -> SerializationError(
                    message = "Serialization failed: ${throwable.message}",
                    cause = throwable
                )
                is IllegalArgumentException -> ValidationError(
                    message = throwable.message ?: "Invalid argument",
                    context = mapOf("exception" to throwable::class.simpleName.orEmpty())
                )
                is IllegalStateException -> ConfigurationError(
                    message = throwable.message ?: "Invalid state",
                    cause = throwable
                )
                else -> UnknownError(
                    message = throwable.message ?: "Unknown error occurred",
                    cause = throwable,
                    context = mapOf("exception" to throwable::class.simpleName.orEmpty())
                )
            }
        }

        /**
         * Create agent error
         */
        fun agentError(message: String, agentId: String? = null, cause: Throwable? = null) =
            AgentError(message, cause, agentId)

        /**
         * Create comm error
         */

        /**
         * Create tool error
         */
        fun toolError(message: String, toolName: String, cause: Throwable? = null) =
            ToolError(message, toolName, cause)

        /**
         * Create config error
         */
        fun configError(message: String, field: String? = null, cause: Throwable? = null) =
            ConfigurationError(message, field, cause)

        /**
         * Create execution error
         */
        fun executionError(
            message: String,
            graphId: String? = null,
            nodeId: String? = null,
            cause: Throwable? = null
        ) = ExecutionError(message, graphId, nodeId, cause)

        /**
         * Create validation error
         */
        fun validationError(
            message: String,
            field: String? = null,
            expectedType: String? = null,
            actualValue: Any? = null,
            graphId: String? = null
        ) = ValidationError(message, field, expectedType, actualValue)

        /**
         * Create network error
         */
        fun networkError(
            message: String,
            statusCode: Int? = null,
            endpoint: String? = null,
            cause: Throwable? = null
        ) = NetworkError(message, statusCode, endpoint, cause)

        /**
         * Create timeout error
         */
        fun timeoutError(message: String, timeoutMs: Long? = null, operation: String? = null) =
            TimeoutError(message, timeoutMs, operation)

        /**
         * Create authentication error
         */
        fun authError(message: String, provider: String? = null, cause: Throwable? = null) =
            AuthenticationError(message, provider, cause)

        /**
         * Create rate limit error
         */
        fun rateLimitError(message: String, retryAfterMs: Long? = null, limitType: String? = null) =
            RateLimitError(message, retryAfterMs, limitType)

        /**
         * Create tool lookup error (dynamic tool resolution failure)
         */
        fun toolLookupError(
            message: String,
            toolName: String? = null,
            namespace: String? = null,
            availableTools: List<String>? = null,
            cause: Throwable? = null
        ) = ToolLookupError(message, toolName, namespace, availableTools, cause)
    }
}

/**
 * Custom exception wrapping SpiceError
 */
class SpiceException(
    val error: SpiceError
) : RuntimeException(error.message, error.cause) {

    /**
     * Error code for programmatic handling
     */
    val code: String = error.code

    /**
     * Error context
     */
    val context: Map<String, Any> = error.context

    override fun toString(): String =
        "SpiceException(code=$code, message=${error.message}, context=$context)"
}

/**
 * Extension: Run block and catch SpiceErrors
 */
inline fun <T> catchSpiceError(block: () -> T): SpiceResult<T> = try {
    SpiceResult.success(block())
} catch (e: SpiceException) {
    SpiceResult.failure(e.error)
} catch (e: Exception) {
    SpiceResult.failure(SpiceError.fromException(e))
}

/**
 * Extension: Run suspend block and catch SpiceErrors
 */
suspend inline fun <T> catchSpiceErrorSuspend(crossinline block: suspend () -> T): SpiceResult<T> = try {
    SpiceResult.success(block())
} catch (e: SpiceException) {
    SpiceResult.failure(e.error)
} catch (e: Exception) {
    SpiceResult.failure(SpiceError.fromException(e))
}
