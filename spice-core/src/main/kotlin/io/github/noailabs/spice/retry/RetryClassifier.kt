package io.github.noailabs.spice.retry

import io.github.noailabs.spice.error.SpiceError

/**
 * Retry Classifier
 *
 * Determines whether an error should be retried based on error type,
 * HTTP status code, and context.
 *
 * **Retryable Conditions:**
 * - SpiceError.RetryableError (explicit retry request)
 * - HTTP 5xx (server errors)
 * - HTTP 408 (Request Timeout)
 * - HTTP 429 (Too Many Requests)
 * - Network exceptions (SocketException, ConnectException, etc.)
 * - Timeout errors
 * - Rate limit errors
 *
 * **Non-Retryable Conditions:**
 * - HTTP 4xx (except 408, 429) - client errors
 * - Validation errors
 * - Authentication errors
 * - Serialization errors
 * - Configuration errors
 * - RetryHint.skipRetry = true
 *
 * @since 1.0.4
 */
interface RetryClassifier {

    /**
     * Classify whether an error should be retried.
     *
     * @param error The error to classify
     * @return Classification result with reason
     */
    fun classify(error: SpiceError): RetryClassification

    companion object {
        /**
         * Default classifier instance
         */
        val DEFAULT: RetryClassifier = DefaultRetryClassifier()

        /**
         * Classifier that never retries
         */
        val NEVER_RETRY: RetryClassifier = object : RetryClassifier {
            override fun classify(error: SpiceError) = RetryClassification(
                shouldRetry = false,
                reason = "Retry disabled"
            )
        }

        /**
         * Classifier that always retries (use with caution!)
         */
        val ALWAYS_RETRY: RetryClassifier = object : RetryClassifier {
            override fun classify(error: SpiceError) = RetryClassification(
                shouldRetry = true,
                reason = "Always retry enabled"
            )
        }
    }
}

/**
 * Result of error classification.
 *
 * @property shouldRetry Whether the error should be retried
 * @property reason Human-readable reason for the classification
 * @property suggestedDelayMs Suggested delay override (from Retry-After, etc.)
 */
data class RetryClassification(
    val shouldRetry: Boolean,
    val reason: String,
    val suggestedDelayMs: Long? = null
)

/**
 * Default implementation of RetryClassifier.
 */
internal class DefaultRetryClassifier : RetryClassifier {

    override fun classify(error: SpiceError): RetryClassification {
        return when (error) {
            // Explicit retryable error
            is SpiceError.RetryableError -> classifyRetryableError(error)

            // Network error - check status code
            is SpiceError.NetworkError -> classifyNetworkError(error)

            // Timeout - always retryable
            is SpiceError.TimeoutError -> RetryClassification(
                shouldRetry = true,
                reason = "Timeout errors are retryable"
            )

            // Rate limit - retryable with potential delay
            is SpiceError.RateLimitError -> RetryClassification(
                shouldRetry = true,
                reason = "Rate limit error (429)",
                suggestedDelayMs = error.retryAfterMs
            )

            // Non-retryable errors
            is SpiceError.ValidationError -> RetryClassification(
                shouldRetry = false,
                reason = "Validation errors are not retryable"
            )

            is SpiceError.AuthenticationError -> RetryClassification(
                shouldRetry = false,
                reason = "Authentication errors are not retryable"
            )

            is SpiceError.SerializationError -> RetryClassification(
                shouldRetry = false,
                reason = "Serialization errors are not retryable"
            )

            is SpiceError.ConfigurationError -> RetryClassification(
                shouldRetry = false,
                reason = "Configuration errors are not retryable"
            )

            is SpiceError.ToolLookupError -> RetryClassification(
                shouldRetry = false,
                reason = "Tool lookup errors are not retryable"
            )

            is SpiceError.RoutingError -> RetryClassification(
                shouldRetry = false,
                reason = "Routing errors are not retryable"
            )

            // Agent/Tool/Execution errors - check context for status code
            is SpiceError.AgentError -> classifyByContext(error, "AgentError")
            is SpiceError.ToolError -> classifyByContext(error, "ToolError")
            is SpiceError.ExecutionError -> classifyByContext(error, "ExecutionError")
            is SpiceError.CheckpointError -> classifyByContext(error, "CheckpointError")

            // Unknown - check context, default to not retryable
            is SpiceError.UnknownError -> classifyByContext(error, "UnknownError", defaultRetry = false)
        }
    }

    private fun classifyRetryableError(error: SpiceError.RetryableError): RetryClassification {
        // Check if skip retry is requested
        error.retryHint?.let { hint ->
            if (hint.skipRetry) {
                return RetryClassification(
                    shouldRetry = false,
                    reason = hint.reason ?: "Skip retry requested via RetryHint"
                )
            }
        }

        // Check status code if present
        error.statusCode?.let { code ->
            val shouldRetry = isRetryableStatusCode(code)
            if (!shouldRetry) {
                return RetryClassification(
                    shouldRetry = false,
                    reason = "HTTP $code is not retryable (client error)"
                )
            }
        }

        return RetryClassification(
            shouldRetry = true,
            reason = "Explicit RetryableError",
            suggestedDelayMs = error.retryHint?.retryAfterMs
        )
    }

    private fun classifyNetworkError(error: SpiceError.NetworkError): RetryClassification {
        val statusCode = error.statusCode

        // No status code = likely connection failure = retryable
        if (statusCode == null) {
            return RetryClassification(
                shouldRetry = true,
                reason = "Network error without status code (connection failure)"
            )
        }

        // Check if status code is retryable
        return if (isRetryableStatusCode(statusCode)) {
            RetryClassification(
                shouldRetry = true,
                reason = "HTTP $statusCode is retryable"
            )
        } else {
            RetryClassification(
                shouldRetry = false,
                reason = "HTTP $statusCode is not retryable"
            )
        }
    }

    private fun classifyByContext(
        error: SpiceError,
        errorType: String,
        defaultRetry: Boolean = false
    ): RetryClassification {
        // Check for status code in context
        val statusCode = error.context["statusCode"] as? Int

        if (statusCode != null) {
            return if (isRetryableStatusCode(statusCode)) {
                RetryClassification(
                    shouldRetry = true,
                    reason = "$errorType with HTTP $statusCode is retryable"
                )
            } else {
                RetryClassification(
                    shouldRetry = false,
                    reason = "$errorType with HTTP $statusCode is not retryable"
                )
            }
        }

        // Check for retryable flag in context
        val isRetryable = error.context["retryable"] as? Boolean
        if (isRetryable != null) {
            return RetryClassification(
                shouldRetry = isRetryable,
                reason = if (isRetryable) "$errorType marked as retryable" else "$errorType marked as non-retryable"
            )
        }

        // Default classification
        return RetryClassification(
            shouldRetry = defaultRetry,
            reason = "$errorType with no retry classification, default: ${if (defaultRetry) "retry" else "no retry"}"
        )
    }

    /**
     * Check if HTTP status code is retryable.
     *
     * Retryable: 408, 429, 5xx
     * Not retryable: other 4xx, 1xx, 2xx, 3xx
     */
    private fun isRetryableStatusCode(code: Int): Boolean {
        return when {
            code == 408 -> true  // Request Timeout
            code == 429 -> true  // Too Many Requests
            code in 500..599 -> true  // Server errors
            else -> false
        }
    }
}

/**
 * Builder for custom RetryClassifier.
 */
class RetryClassifierBuilder {
    private var baseClassifier: RetryClassifier = RetryClassifier.DEFAULT
    private val customRules = mutableListOf<(SpiceError) -> RetryClassification?>()

    /**
     * Set base classifier to delegate to
     */
    fun baseClassifier(classifier: RetryClassifier): RetryClassifierBuilder {
        baseClassifier = classifier
        return this
    }

    /**
     * Add a custom rule (checked before base classifier)
     */
    fun addRule(rule: (SpiceError) -> RetryClassification?): RetryClassifierBuilder {
        customRules.add(rule)
        return this
    }

    /**
     * Add a rule to always retry specific error code
     */
    fun alwaysRetry(errorCode: String, reason: String = "Custom rule: always retry"): RetryClassifierBuilder {
        return addRule { error ->
            if (error.code == errorCode) {
                RetryClassification(shouldRetry = true, reason = reason)
            } else {
                null
            }
        }
    }

    /**
     * Add a rule to never retry specific error code
     */
    fun neverRetry(errorCode: String, reason: String = "Custom rule: never retry"): RetryClassifierBuilder {
        return addRule { error ->
            if (error.code == errorCode) {
                RetryClassification(shouldRetry = false, reason = reason)
            } else {
                null
            }
        }
    }

    fun build(): RetryClassifier = object : RetryClassifier {
        override fun classify(error: SpiceError): RetryClassification {
            // Check custom rules first
            for (rule in customRules) {
                rule(error)?.let { return it }
            }
            // Delegate to base classifier
            return baseClassifier.classify(error)
        }
    }
}

/**
 * Extension to create classifier with custom rules
 */
fun RetryClassifier.withRules(block: RetryClassifierBuilder.() -> Unit): RetryClassifier {
    return RetryClassifierBuilder()
        .baseClassifier(this)
        .apply(block)
        .build()
}
