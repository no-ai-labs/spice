package io.github.noailabs.spice.error

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 💡 Retry Hint
 *
 * Provides hints for retry policy override at the error level.
 * Used by RetryableError to suggest specific retry behavior.
 *
 * **Usage:**
 * - `retryAfterMs`: Specific delay before retry (from Retry-After header)
 * - `maxAttempts`: Override max retry attempts for this error
 * - `skipRetry`: Force skip retry even for retryable errors
 * - `reason`: Human-readable reason for the hint
 *
 * @since 1.0.4
 */
data class RetryHint(
    /**
     * Suggested delay before retry in milliseconds.
     * Typically from HTTP Retry-After header (429 responses).
     */
    val retryAfterMs: Long? = null,

    /**
     * Override maximum retry attempts for this specific error.
     * null = use policy default
     */
    val maxAttempts: Int? = null,

    /**
     * Force skip retry even if error is classified as retryable.
     */
    val skipRetry: Boolean = false,

    /**
     * Human-readable reason for the hint (for logging/debugging).
     */
    val reason: String? = null
) {
    companion object {
        /**
         * Create hint from Retry-After header value (seconds)
         */
        fun fromRetryAfterSeconds(seconds: Long, reason: String? = null): RetryHint =
            RetryHint(
                retryAfterMs = seconds * 1000,
                reason = reason ?: "Retry-After header: ${seconds}s"
            )

        /**
         * Create hint from Duration
         */
        fun fromDuration(duration: Duration, reason: String? = null): RetryHint =
            RetryHint(
                retryAfterMs = duration.inWholeMilliseconds,
                reason = reason
            )

        /**
         * Create hint to skip retry
         */
        fun skipRetry(reason: String): RetryHint =
            RetryHint(skipRetry = true, reason = reason)

        /**
         * Create hint with custom max attempts
         */
        fun withMaxAttempts(maxAttempts: Int, reason: String? = null): RetryHint =
            RetryHint(maxAttempts = maxAttempts, reason = reason)
    }

    /**
     * Get retry delay as Duration
     */
    fun getRetryAfterDuration(): Duration? = retryAfterMs?.milliseconds
}

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
        is RetryableError -> copy(context = context + pairs.toMap())
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

    /**
     * 🔄 Retryable errors (network failures, 5xx, timeout, 429)
     *
     * Errors that can be automatically retried by the framework.
     * Used by RetrySupervisor to determine retry behavior.
     *
     * **Retryable conditions:**
     * - HTTP 5xx (server errors)
     * - HTTP 408 (request timeout)
     * - HTTP 429 (rate limit) - uses Retry-After header if available
     * - Network exceptions (SocketException, ConnectException, etc.)
     * - Timeout exceptions
     *
     * **Non-retryable conditions:**
     * - HTTP 4xx (except 408, 429)
     * - Validation errors
     * - Authentication errors
     *
     * @property statusCode HTTP status code (if applicable)
     * @property errorCode Application-specific error code
     * @property retryHint Optional hint for retry policy override
     * @since 1.0.4
     */
    data class RetryableError(
        override val message: String,
        override val cause: Throwable? = null,
        val statusCode: Int? = null,
        val errorCode: String? = null,
        val retryHint: RetryHint? = null,
        override val context: Map<String, Any> = emptyMap()
    ) : SpiceError() {
        override val code: String = "RETRYABLE_ERROR"

        /**
         * Check if this error has a specific retry delay hint (e.g., from Retry-After header)
         */
        fun hasRetryAfterHint(): Boolean = retryHint?.retryAfterMs != null

        /**
         * Get suggested retry delay in milliseconds
         */
        fun getSuggestedRetryDelayMs(): Long? = retryHint?.retryAfterMs
    }

    companion object {
        /**
         * Create error from exception
         *
         * Network-related exceptions are converted to RetryableError for automatic retry support.
         * - SocketException, ConnectException, UnknownHostException → RetryableError
         * - SocketTimeoutException → RetryableError
         */
        fun fromException(throwable: Throwable): SpiceError {
            return when (throwable) {
                is SpiceException -> throwable.error

                // Network exceptions → RetryableError (automatic retry)
                is java.net.SocketException -> RetryableError(
                    message = "Socket error: ${throwable.message}",
                    cause = throwable,
                    context = mapOf("exception" to "SocketException")
                )
                is java.net.ConnectException -> RetryableError(
                    message = "Connection failed: ${throwable.message}",
                    cause = throwable,
                    context = mapOf("exception" to "ConnectException")
                )
                is java.net.UnknownHostException -> RetryableError(
                    message = "Unknown host: ${throwable.message}",
                    cause = throwable,
                    context = mapOf("exception" to "UnknownHostException")
                )
                is java.net.SocketTimeoutException -> RetryableError(
                    message = "Network timeout: ${throwable.message}",
                    cause = throwable,
                    context = mapOf("exception" to "SocketTimeoutException", "operation" to "network_call")
                )
                is java.io.IOException -> {
                    // IO exceptions are often transient network issues
                    val isRetryable = throwable.message?.let { msg ->
                        msg.contains("reset", ignoreCase = true) ||
                        msg.contains("refused", ignoreCase = true) ||
                        msg.contains("timeout", ignoreCase = true) ||
                        msg.contains("connection", ignoreCase = true)
                    } ?: false

                    if (isRetryable) {
                        RetryableError(
                            message = "IO error: ${throwable.message}",
                            cause = throwable,
                            context = mapOf("exception" to throwable::class.simpleName.orEmpty())
                        )
                    } else {
                        NetworkError(
                            message = "IO error: ${throwable.message}",
                            cause = throwable
                        )
                    }
                }

                // Non-retryable errors
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

        /**
         * Create retryable error (for automatic retry)
         *
         * Use this for errors that should be automatically retried by the framework.
         * Common use cases: HTTP 5xx, 429, network failures, timeouts.
         *
         * @param message Error message
         * @param statusCode HTTP status code (if applicable)
         * @param errorCode Application-specific error code
         * @param retryHint Optional hint for retry policy override
         * @param cause Original exception
         */
        fun retryableError(
            message: String,
            statusCode: Int? = null,
            errorCode: String? = null,
            retryHint: RetryHint? = null,
            cause: Throwable? = null
        ) = RetryableError(message, cause, statusCode, errorCode, retryHint)

        /**
         * Create retryable error from HTTP status code
         *
         * Convenience method for HTTP error responses.
         * Automatically extracts Retry-After header value if provided.
         *
         * @param statusCode HTTP status code
         * @param message Error message
         * @param retryAfterSeconds Retry-After header value in seconds (for 429)
         * @param cause Original exception
         */
        fun retryableHttpError(
            statusCode: Int,
            message: String,
            retryAfterSeconds: Long? = null,
            cause: Throwable? = null
        ): RetryableError {
            val hint = retryAfterSeconds?.let {
                RetryHint.fromRetryAfterSeconds(it, "HTTP $statusCode Retry-After")
            }
            return RetryableError(
                message = message,
                cause = cause,
                statusCode = statusCode,
                retryHint = hint,
                context = mapOf("statusCode" to statusCode)
            )
        }

        /**
         * Check if an error is retryable
         *
         * Returns true for:
         * - RetryableError
         * - NetworkError with 5xx status
         * - TimeoutError
         * - RateLimitError
         */
        fun isRetryable(error: SpiceError): Boolean = when (error) {
            is RetryableError -> !error.retryHint?.skipRetry.let { it == true }
            is NetworkError -> error.statusCode?.let { it >= 500 || it == 408 || it == 429 } ?: true
            is TimeoutError -> true
            is RateLimitError -> true
            else -> false
        }
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
