# SpiceError Types

Complete reference for all error types in the Spice Framework error hierarchy.

## Error Hierarchy

```kotlin
sealed class SpiceError {
    abstract val message: String
    abstract val code: String
    open val cause: Throwable? = null
    open val context: Map<String, Any> = emptyMap()
    val timestamp: Long
}
```

All errors include:
- **message**: Human-readable error description
- **code**: Machine-readable error code (e.g., "AGENT_ERROR")
- **cause**: Optional underlying exception
- **context**: Additional debugging information
- **timestamp**: When the error occurred

## Error Types

### AgentError

Errors related to agent operations and processing.

```kotlin
data class AgentError(
    override val message: String,
    override val cause: Throwable? = null,
    val agentId: String? = null,
    override val context: Map<String, Any> = emptyMap()
) : SpiceError()
```

**When to use:**
- Agent fails to process a communication
- Agent initialization fails
- Agent state is invalid

**Example:**
```kotlin
SpiceError.agentError(
    message = "Failed to process communication",
    agentId = "claude-assistant",
    cause = originalException
).withContext(
    "comm_id" to commId,
    "agent_state" to "processing"
)
```

### CommError

Errors related to communication (Comm) operations.

```kotlin
data class CommError(
    override val message: String,
    val comm: Comm? = null,
    override val cause: Throwable? = null,
    override val context: Map<String, Any> = emptyMap()
) : SpiceError()
```

**When to use:**
- Invalid communication format
- Communication routing fails
- Message delivery fails

**Example:**
```kotlin
SpiceError.commError(
    message = "Failed to deliver message",
    comm = originalComm
).withContext(
    "recipient" to comm.to,
    "retry_count" to 3
)
```

### ToolError

Errors related to tool execution.

```kotlin
data class ToolError(
    override val message: String,
    val toolName: String,
    override val cause: Throwable? = null,
    override val context: Map<String, Any> = emptyMap()
) : SpiceError()
```

**When to use:**
- Tool execution fails
- Tool not found
- Invalid tool parameters

**Example:**
```kotlin
SpiceError.toolError(
    message = "Tool execution failed",
    toolName = "calculator"
).withContext(
    "parameters" to params,
    "execution_time_ms" to executionTime
)
```

### ConfigurationError

Errors related to invalid or missing configuration.

```kotlin
data class ConfigurationError(
    override val message: String,
    val field: String? = null,
    override val cause: Throwable? = null,
    override val context: Map<String, Any> = emptyMap()
) : SpiceError()
```

**When to use:**
- Required configuration missing
- Invalid configuration value
- Configuration format error

**Example:**
```kotlin
SpiceError.configError(
    message = "API key is required",
    field = "apiKey"
).withContext(
    "provider" to "openai",
    "config_source" to "environment"
)
```

### ValidationError

Errors related to input validation.

```kotlin
data class ValidationError(
    override val message: String,
    val field: String? = null,
    val expectedType: String? = null,
    val actualValue: Any? = null,
    override val context: Map<String, Any> = emptyMap()
) : SpiceError()
```

**When to use:**
- Input doesn't match expected format
- Required field missing
- Type mismatch

**Example:**
```kotlin
SpiceError.validationError(
    message = "Invalid email format",
    field = "email",
    expectedType = "email",
    actualValue = userInput
).withContext(
    "validation_rule" to "RFC5322",
    "form_id" to "user_registration"
)
```

### NetworkError

Errors related to network operations.

```kotlin
data class NetworkError(
    override val message: String,
    val statusCode: Int? = null,
    val endpoint: String? = null,
    override val cause: Throwable? = null,
    override val context: Map<String, Any> = emptyMap()
) : SpiceError()
```

**When to use:**
- HTTP request fails
- Network connectivity issues
- API returns error status

**Example:**
```kotlin
SpiceError.networkError(
    message = "Failed to fetch user data",
    statusCode = 404,
    endpoint = "/api/users/123"
).withContext(
    "method" to "GET",
    "retry_attempt" to 2
)
```

### TimeoutError

Errors related to operation timeouts.

```kotlin
data class TimeoutError(
    override val message: String,
    val timeoutMs: Long? = null,
    val operation: String? = null,
    override val context: Map<String, Any> = emptyMap()
) : SpiceError()
```

**When to use:**
- Operation exceeds time limit
- Agent doesn't respond in time
- External service timeout

**Example:**
```kotlin
SpiceError.timeoutError(
    message = "Agent response timeout",
    timeoutMs = 30000,
    operation = "agent.processComm"
).withContext(
    "agent_id" to "claude",
    "comm_id" to commId
)
```

### AuthenticationError

Errors related to authentication and authorization.

```kotlin
data class AuthenticationError(
    override val message: String,
    val provider: String? = null,
    override val cause: Throwable? = null,
    override val context: Map<String, Any> = emptyMap()
) : SpiceError()
```

**When to use:**
- Invalid API key
- Expired token
- Insufficient permissions

**Example:**
```kotlin
SpiceError.authError(
    message = "Invalid API key",
    provider = "openai"
).withContext(
    "key_prefix" to apiKey.take(8),
    "endpoint" to "/v1/chat/completions"
)
```

### RateLimitError

Errors related to rate limiting.

```kotlin
data class RateLimitError(
    override val message: String,
    val retryAfterMs: Long? = null,
    val limitType: String? = null,
    override val context: Map<String, Any> = emptyMap()
) : SpiceError()
```

**When to use:**
- API rate limit exceeded
- Too many requests
- Quota exhausted

**Example:**
```kotlin
SpiceError.rateLimitError(
    message = "Rate limit exceeded",
    retryAfterMs = 60000,
    limitType = "requests_per_minute"
).withContext(
    "limit" to 60,
    "current_count" to 61,
    "window_start" to windowStart
)
```

### SerializationError

Errors related to serialization and deserialization.

```kotlin
data class SerializationError(
    override val message: String,
    val format: String? = null,
    override val cause: Throwable? = null,
    override val context: Map<String, Any> = emptyMap()
) : SpiceError()
```

**When to use:**
- JSON parsing fails
- Invalid data format
- Type conversion error

**Example:**
```kotlin
SpiceError.SerializationError(
    message = "Failed to parse JSON response",
    format = "json",
    cause = jsonException
).withContext(
    "response_preview" to response.take(100),
    "expected_schema" to "User"
)
```

### UnknownError

Fallback for unexpected errors.

```kotlin
data class UnknownError(
    override val message: String,
    override val cause: Throwable? = null,
    override val context: Map<String, Any> = emptyMap()
) : SpiceError()
```

**When to use:**
- Unexpected exception
- Error doesn't fit other categories
- Fallback error handling

**Example:**
```kotlin
SpiceError.UnknownError(
    message = "Unexpected error occurred",
    cause = exception
).withContext(
    "operation" to "data_processing",
    "exception_type" to exception::class.simpleName
)
```

## Working with Errors

### Adding Context

Add debugging information to any error:

```kotlin
val error = SpiceError.agentError("Processing failed")
    .withContext(
        "agent_id" to "claude",
        "input_length" to input.length,
        "timestamp" to System.currentTimeMillis()
    )
```

### Converting to Exception

Convert SpiceError to an exception for throwing:

```kotlin
val error = SpiceError.networkError("Connection failed")
throw error.toException()  // Throws SpiceException
```

### Mapping Exceptions

Convert standard exceptions to SpiceError:

```kotlin
val error = SpiceError.fromException(throwable)

// Automatically maps:
// - IllegalArgumentException → ValidationError
// - IllegalStateException → ConfigurationError
// - UnknownHostException → NetworkError
// - SocketTimeoutException → TimeoutError
// - SerializationException → SerializationError
// - Others → UnknownError
```

### Pattern Matching

Handle different error types:

```kotlin
when (val error = result.error) {
    is SpiceError.NetworkError -> {
        if (error.statusCode == 503) {
            // Service unavailable, retry
        }
    }
    is SpiceError.RateLimitError -> {
        delay(error.retryAfterMs ?: 1000)
        retry()
    }
    is SpiceError.AuthenticationError -> {
        // Re-authenticate
        refreshToken()
    }
    is SpiceError.ValidationError -> {
        // Show user-friendly error
        showError("Invalid ${error.field}: ${error.message}")
    }
    else -> {
        // Generic error handling
        logger.error("Error: ${error.message}", error.cause)
    }
}
```

## Error Recovery Strategies

### Network Errors

```kotlin
fun handleNetworkError(error: SpiceError.NetworkError): SpiceResult<Data> {
    return when (error.statusCode) {
        in 500..599 -> {
            // Server error - retry
            retryWithBackoff()
        }
        404 -> {
            // Not found - use default
            SpiceResult.success(Data.default())
        }
        401, 403 -> {
            // Auth error - fail fast
            SpiceResult.failure(error)
        }
        else -> {
            // Other errors - try cache
            loadFromCache().recoverWith { SpiceResult.failure(error) }
        }
    }
}
```

### Rate Limit Errors

```kotlin
suspend fun handleRateLimit(
    error: SpiceError.RateLimitError,
    operation: suspend () -> SpiceResult<T>
): SpiceResult<T> {
    delay(error.retryAfterMs ?: 1000)
    return operation()
}
```

### Validation Errors

```kotlin
fun handleValidationError(error: SpiceError.ValidationError): UserMessage {
    return UserMessage(
        field = error.field ?: "unknown",
        message = when (error.field) {
            "email" -> "Please enter a valid email address"
            "password" -> "Password must be at least 8 characters"
            else -> error.message
        }
    )
}
```

## Best Practices

### 1. Choose the Right Error Type

```kotlin
// ✅ Good - Specific error type
SpiceError.validationError(
    message = "Email is required",
    field = "email"
)

// ❌ Bad - Generic error
SpiceError.unknownError("Email is required")
```

### 2. Include Context

```kotlin
// ✅ Good - Rich context
SpiceError.toolError("Execution failed", "calculator")
    .withContext(
        "parameters" to params,
        "execution_time" to duration,
        "memory_used" to memoryUsed
    )

// ❌ Bad - No context
SpiceError.toolError("Execution failed", "calculator")
```

### 3. Preserve Cause

```kotlin
// ✅ Good - Preserves stack trace
SpiceError.networkError(
    message = "API call failed",
    cause = ioException
)

// ❌ Bad - Lost stack trace
SpiceError.networkError(
    message = "API call failed: ${ioException.message}"
)
```

### 4. Use Descriptive Messages

```kotlin
// ✅ Good - Clear and actionable
SpiceError.authError(
    message = "API key is invalid or expired. Please check your credentials.",
    provider = "openai"
)

// ❌ Bad - Vague
SpiceError.authError("Auth failed")
```

## Next Steps

- [SpiceResult Guide](./spice-result) - Learn how to use SpiceResult
- [Best Practices](./best-practices) - Advanced error handling patterns
