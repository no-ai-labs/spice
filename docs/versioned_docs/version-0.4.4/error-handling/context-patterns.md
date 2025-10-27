# Error Context Enrichment Patterns

Master the art of adding context to errors for better debugging and observability.

## Why Context Matters

Context transforms vague errors into actionable debugging information:

```kotlin
// ❌ Bad - No context
SpiceError.networkError("Request failed")

// ✅ Good - Rich context
SpiceError.networkError(
    message = "Request failed",
    statusCode = 503,
    endpoint = "/api/users/123"
).withContext(
    "retry_attempt" to "3",
    "timeout_ms" to "30000",
    "user_agent" to "Spice/1.0",
    "request_id" to "req-abc-123",
    "trace_id" to "trace-xyz-789"
)
```

**Benefits:**
- 🔍 **Faster debugging** - See exactly what went wrong
- 📊 **Better observability** - Context flows to logs and traces
- 🎯 **Root cause analysis** - Correlate errors across services
- 📈 **Metrics** - Group errors by context dimensions

---

## Basic Context Usage

### Adding Context with withContext()

```kotlin
val error = SpiceError.networkError("API call failed")
    .withContext(
        "endpoint" to "/api/data",
        "method" to "POST"
    )

// Access context
val endpoint = error.context["endpoint"] // "/api/data"
```

### Multiple withContext() Calls

Context is additive - each call adds more:

```kotlin
val error = SpiceError.networkError("Failed")
    .withContext("layer" to "repository")
    .withContext("operation" to "fetchUser")
    .withContext("user_id" to "123")

// context = {"layer": "repository", "operation": "fetchUser", "user_id": "123"}
```

### Context in Error Creation

```kotlin
// Via constructor
val error = SpiceError.NetworkError(
    message = "Connection timeout",
    statusCode = 504,
    endpoint = "/api/slow",
    context = mapOf(
        "timeout_ms" to "5000",
        "retry_count" to "3"
    )
)

// Via withContext
val error = SpiceError.networkError("Connection timeout")
    .withContext(
        "timeout_ms" to "5000",
        "retry_count" to "3"
    )
```

---

## Multi-Layer Context Pattern

The most powerful pattern: **add context at each layer** as errors bubble up.

### Layer 1: Infrastructure (Database/Network)

```kotlin
// Repository layer - Infrastructure concerns
class UserRepository(private val database: Database) {

    suspend fun findUser(id: String): SpiceResult<User> {
        return SpiceResult.catchingSuspend {
            database.query("SELECT * FROM users WHERE id = ?", id)
        }.mapError { error ->
            // Add infrastructure context
            error.withContext(
                "layer" to "infrastructure",
                "component" to "database",
                "operation" to "query",
                "table" to "users",
                "query_type" to "select",
                "user_id" to id
            )
        }
    }
}
```

### Layer 2: Domain (Business Logic)

```kotlin
// Service layer - Domain concerns
class UserService(private val repository: UserRepository) {

    suspend fun getUser(id: String): SpiceResult<User> {
        return repository.findUser(id)
            .mapError { error ->
                // Add domain context
                error.withContext(
                    "layer" to "domain",
                    "service" to "UserService",
                    "business_operation" to "getUser",
                    "entity_type" to "User"
                )
            }
    }

    suspend fun getUserWithPermissionCheck(
        id: String,
        requesterId: String
    ): SpiceResult<User> {
        return getUser(id)
            .flatMap { user ->
                if (user.canBeAccessedBy(requesterId)) {
                    SpiceResult.success(user)
                } else {
                    SpiceResult.failure(
                        SpiceError.authError("Access denied")
                            .withContext(
                                "layer" to "domain",
                                "check" to "permission",
                                "user_id" to id,
                                "requester_id" to requesterId,
                                "reason" to "insufficient_permissions"
                            )
                    )
                }
            }
    }
}
```

### Layer 3: Application (HTTP/API)

```kotlin
// Controller layer - Application concerns
class UserController(private val userService: UserService) {

    suspend fun handleGetUser(request: HttpRequest): HttpResponse {
        val userId = request.params["id"] ?: return badRequest()
        val requesterId = request.auth.userId

        return userService.getUserWithPermissionCheck(userId, requesterId)
            .mapError { error ->
                // Add application context
                error.withContext(
                    "layer" to "application",
                    "controller" to "UserController",
                    "endpoint" to "/api/users/:id",
                    "method" to "GET",
                    "request_id" to request.id,
                    "trace_id" to request.traceId,
                    "user_agent" to request.userAgent,
                    "ip_address" to request.ipAddress
                )
            }
            .fold(
                onSuccess = { user -> okResponse(user) },
                onFailure = { error ->
                    logger.error {
                        "Request failed: ${error.message}\n" +
                        "Context: ${error.context}"
                    }
                    errorResponse(error)
                }
            )
    }
}
```

### Complete Context Example

When an error occurs, you see the complete journey:

```json
{
  "code": "NETWORK_ERROR",
  "message": "Connection timeout",
  "timestamp": 1703001234567,
  "context": {
    // Infrastructure layer
    "layer": "infrastructure",
    "component": "database",
    "operation": "query",
    "table": "users",
    "query_type": "select",
    "user_id": "123",

    // Domain layer
    "service": "UserService",
    "business_operation": "getUser",
    "entity_type": "User",

    // Application layer
    "controller": "UserController",
    "endpoint": "/api/users/:id",
    "method": "GET",
    "request_id": "req-abc-123",
    "trace_id": "trace-xyz-789",
    "user_agent": "Mozilla/5.0...",
    "ip_address": "192.168.1.1"
  }
}
```

---

## Request Context Pattern

Track requests across your entire application:

### Request Context Class

```kotlin
data class RequestContext(
    val requestId: String = UUID.randomUUID().toString(),
    val traceId: String,
    val userId: String?,
    val sessionId: String?,
    val userAgent: String?,
    val ipAddress: String?,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toContextMap(): Map<String, String> = buildMap {
        put("request_id", requestId)
        put("trace_id", traceId)
        userId?.let { put("user_id", it) }
        sessionId?.let { put("session_id", it) }
        userAgent?.let { put("user_agent", it) }
        ipAddress?.let { put("ip_address", it) }
        put("timestamp", timestamp.toString())
    }
}
```

### Extension Function

```kotlin
fun <T> SpiceResult<T>.withRequestContext(
    context: RequestContext
): SpiceResult<T> {
    return this.mapError { error ->
        error.withContext(*context.toContextMap().toList().toTypedArray())
    }
}

// Usage
suspend fun handleRequest(request: HttpRequest): SpiceResult<Response> {
    val requestContext = RequestContext(
        traceId = request.headers["X-Trace-ID"] ?: UUID.randomUUID().toString(),
        userId = request.auth.userId,
        sessionId = request.cookies["session_id"],
        userAgent = request.headers["User-Agent"],
        ipAddress = request.remoteAddress
    )

    return processRequest(request)
        .withRequestContext(requestContext)
}
```

### Coroutine Context Integration

Use coroutine context to automatically propagate request context:

```kotlin
// Store request context in coroutine context
class RequestContextElement(
    val context: RequestContext
) : AbstractCoroutineContextElement(RequestContextElement) {
    companion object Key : CoroutineContext.Key<RequestContextElement>
}

// Extension to add request context to errors
suspend fun <T> SpiceResult<T>.withCurrentRequestContext(): SpiceResult<T> {
    val requestContext = coroutineContext[RequestContextElement]?.context
        ?: return this

    return this.mapError { error ->
        error.withContext(*requestContext.toContextMap().toList().toTypedArray())
    }
}

// Usage - context automatically propagated
suspend fun handleRequest(request: HttpRequest) = withContext(
    RequestContextElement(extractRequestContext(request))
) {
    // All operations in this scope automatically get request context
    userService.getUser(userId)
        .withCurrentRequestContext()  // Automatically adds context!
}
```

---

## Timing Context Pattern

Track operation duration and performance:

### Timing Wrapper

```kotlin
suspend fun <T> measureOperation(
    operation: String,
    block: suspend () -> SpiceResult<T>
): SpiceResult<T> {
    val startTime = System.currentTimeMillis()

    return block()
        .map { value ->
            val duration = System.currentTimeMillis() - startTime
            logger.debug { "$operation completed in ${duration}ms" }
            value
        }
        .mapError { error ->
            val duration = System.currentTimeMillis() - startTime
            error.withContext(
                "operation" to operation,
                "duration_ms" to duration.toString(),
                "started_at" to startTime.toString()
            )
        }
}

// Usage
suspend fun fetchUser(id: String): SpiceResult<User> {
    return measureOperation("fetchUser") {
        SpiceResult.catchingSuspend {
            apiClient.getUser(id)
        }
    }
}
```

### Performance Thresholds

```kotlin
suspend fun <T> measureOperationWithThreshold(
    operation: String,
    slowThresholdMs: Long = 1000,
    block: suspend () -> SpiceResult<T>
): SpiceResult<T> {
    val startTime = System.currentTimeMillis()

    return block()
        .mapError { error ->
            val duration = System.currentTimeMillis() - startTime
            val isSlow = duration > slowThresholdMs

            error.withContext(
                "operation" to operation,
                "duration_ms" to duration.toString(),
                "is_slow" to isSlow.toString(),
                "threshold_ms" to slowThresholdMs.toString()
            )
        }
        .onSuccess {
            val duration = System.currentTimeMillis() - startTime
            if (duration > slowThresholdMs) {
                logger.warn {
                    "Slow operation: $operation took ${duration}ms " +
                    "(threshold: ${slowThresholdMs}ms)"
                }
            }
        }
}
```

---

## Retry Context Pattern

Track retry attempts and their outcomes:

```kotlin
suspend fun <T> retryWithContext(
    maxAttempts: Int = 3,
    delayMs: Long = 1000,
    operation: suspend (attempt: Int) -> SpiceResult<T>
): SpiceResult<T> {
    var lastError: SpiceError? = null

    repeat(maxAttempts) { attempt ->
        val attemptNumber = attempt + 1

        val result = operation(attemptNumber)
            .mapError { error ->
                error.withContext(
                    "retry_attempt" to attemptNumber.toString(),
                    "max_attempts" to maxAttempts.toString(),
                    "is_final_attempt" to (attemptNumber == maxAttempts).toString()
                )
            }

        when {
            result.isSuccess -> return result
            attemptNumber < maxAttempts -> {
                lastError = (result as SpiceResult.Failure).error
                delay(delayMs * attemptNumber)  // Exponential backoff
            }
            else -> lastError = (result as SpiceResult.Failure).error
        }
    }

    return SpiceResult.failure(
        lastError!!.withContext(
            "retry_exhausted" to "true",
            "total_attempts" to maxAttempts.toString()
        )
    )
}

// Usage
suspend fun fetchWithRetry(url: String): SpiceResult<Data> {
    return retryWithContext(maxAttempts = 3) { attempt ->
        logger.info { "Fetching $url (attempt $attempt)" }

        SpiceResult.catchingSuspend {
            httpClient.get(url)
        }.mapError { error ->
            error.withContext(
                "url" to url,
                "attempt_number" to attempt.toString()
            )
        }
    }
}
```

---

## Integration with OpenTelemetry

Context flows seamlessly to distributed traces:

### Span Attributes from Context

```kotlin
fun <T> SpiceResult<T>.withSpanAttributes(span: Span): SpiceResult<T> {
    return this
        .onSuccess { value ->
            span.setStatus(StatusCode.OK)
        }
        .onFailure { error ->
            // Add error context as span attributes
            error.context.forEach { (key, value) ->
                span.setAttribute("error.$key", value)
            }

            span.setAttribute("error.code", error.code)
            span.setAttribute("error.message", error.message)
            span.setStatus(StatusCode.ERROR, error.message)

            // Record exception
            error.cause?.let { span.recordException(it) }
        }
}

// Usage with tracing
suspend fun fetchUser(id: String): SpiceResult<User> {
    return tracer.spanBuilder("fetchUser")
        .startSpan()
        .use { span ->
            span.setAttribute("user.id", id)

            repository.findUser(id)
                .withSpanAttributes(span)
                .mapError { error ->
                    error.withContext(
                        "span_id" to span.spanContext.spanId,
                        "trace_id" to span.spanContext.traceId
                    )
                }
        }
}
```

### Custom Context Propagation

```kotlin
// Propagate context to nested operations
suspend fun complexOperation(): SpiceResult<Result> {
    val operationId = UUID.randomUUID().toString()

    return step1()
        .mapError { error ->
            error.withContext(
                "operation_id" to operationId,
                "failed_step" to "step1"
            )
        }
        .flatMap { result1 ->
            step2(result1)
                .mapError { error ->
                    error.withContext(
                        "operation_id" to operationId,
                        "failed_step" to "step2",
                        "step1_result" to result1.toString()
                    )
                }
        }
        .flatMap { result2 ->
            step3(result2)
                .mapError { error ->
                    error.withContext(
                        "operation_id" to operationId,
                        "failed_step" to "step3",
                        "step2_result" to result2.toString()
                    )
                }
        }
}
```

---

## Structured Logging with Context

### Logging Extension

```kotlin
fun SpiceError.toStructuredLog(): Map<String, Any> = buildMap {
    put("error_code", code)
    put("error_message", message)
    put("timestamp", timestamp)

    cause?.let { put("cause", it.toString()) }

    // Flatten context into log
    context.forEach { (key, value) ->
        put("context_$key", value)
    }
}

// Usage
result.onFailure { error ->
    logger.error(error.toStructuredLog()) {
        "Operation failed: ${error.message}"
    }
}

// Output (JSON format):
// {
//   "message": "Operation failed: Connection timeout",
//   "error_code": "NETWORK_ERROR",
//   "error_message": "Connection timeout",
//   "timestamp": 1703001234567,
//   "context_layer": "infrastructure",
//   "context_operation": "query",
//   "context_request_id": "req-abc-123",
//   "context_trace_id": "trace-xyz-789"
// }
```

### Log Aggregation

Group errors by context dimensions:

```kotlin
class ErrorAggregator {
    private val errors = mutableListOf<SpiceError>()

    fun record(error: SpiceError) {
        errors.add(error)
    }

    fun analyzeByContext(key: String): Map<String, Int> {
        return errors
            .mapNotNull { it.context[key] }
            .groupingBy { it }
            .eachCount()
    }

    fun summary(): String {
        val byCode = errors.groupingBy { it.code }.eachCount()
        val byLayer = analyzeByContext("layer")
        val byOperation = analyzeByContext("operation")

        return """
            Total errors: ${errors.size}

            By error code:
            ${byCode.entries.joinToString("\n") { "  ${it.key}: ${it.value}" }}

            By layer:
            ${byLayer.entries.joinToString("\n") { "  ${it.key}: ${it.value}" }}

            By operation:
            ${byOperation.entries.joinToString("\n") { "  ${it.key}: ${it.value}" }}
        """.trimIndent()
    }
}

// Usage
val aggregator = ErrorAggregator()

// Record errors throughout application
results.forEach { result ->
    result.onFailure { error ->
        aggregator.record(error)
    }
}

// Analyze
println(aggregator.summary())
// Output:
// Total errors: 150
//
// By error code:
//   NETWORK_ERROR: 80
//   VALIDATION_ERROR: 50
//   TIMEOUT_ERROR: 20
//
// By layer:
//   infrastructure: 80
//   domain: 50
//   application: 20
//
// By operation:
//   fetchUser: 60
//   updateOrder: 40
//   processPayment: 30
```

---

## Best Practices

### 1. Consistent Context Keys

Use standard naming conventions:

```kotlin
// ✅ Good - Consistent naming
error.withContext(
    "user_id" to userId,           // snake_case
    "request_id" to requestId,
    "duration_ms" to duration,
    "retry_count" to retries
)

// ❌ Bad - Inconsistent naming
error.withContext(
    "userId" to userId,            // camelCase
    "request-id" to requestId,     // kebab-case
    "duration" to duration,        // missing unit
    "retries" to retries           // different terminology
)
```

### 2. Don't Duplicate Information

```kotlin
// ✅ Good - Unique information
SpiceError.networkError(
    message = "Connection timeout",
    statusCode = 504,
    endpoint = "/api/users"
).withContext(
    "retry_attempt" to "3",
    "timeout_ms" to "5000"
)

// ❌ Bad - Repeating statusCode and endpoint
SpiceError.networkError(
    message = "Connection timeout",
    statusCode = 504,
    endpoint = "/api/users"
).withContext(
    "status_code" to "504",        // Already in statusCode field!
    "endpoint" to "/api/users",    // Already in endpoint field!
    "retry_attempt" to "3"
)
```

### 3. Avoid Sensitive Data

```kotlin
// ❌ Bad - Sensitive data in context
error.withContext(
    "password" to userPassword,        // ❌ Never log passwords!
    "credit_card" to cardNumber,       // ❌ Never log PII!
    "api_key" to apiKey                // ❌ Never log secrets!
)

// ✅ Good - Safe contextual data
error.withContext(
    "user_id" to userId,               // ✅ ID is safe
    "card_last4" to cardNumber.takeLast(4),  // ✅ Partial data
    "api_key_prefix" to apiKey.take(8)       // ✅ Prefix only
)
```

### 4. Context Size Limits

Don't add unlimited data:

```kotlin
// ❌ Bad - Huge context
error.withContext(
    "request_body" to requestBody,     // Could be megabytes!
    "response" to response,            // Could be megabytes!
    "full_stack_trace" to stackTrace   // Already in cause!
)

// ✅ Good - Bounded context
error.withContext(
    "request_size" to requestBody.length.toString(),
    "response_size" to response.length.toString(),
    "request_preview" to requestBody.take(100),
    "response_preview" to response.take(100)
)
```

### 5. Use Helper Functions

```kotlin
// ✅ Good - Reusable context builders
fun buildDatabaseContext(
    table: String,
    operation: String,
    recordId: String
): Map<String, String> = mapOf(
    "layer" to "infrastructure",
    "component" to "database",
    "table" to table,
    "operation" to operation,
    "record_id" to recordId
)

// Usage
error.withContext(*buildDatabaseContext(
    table = "users",
    operation = "update",
    recordId = userId
).toList().toTypedArray())
```

---

## Summary

### Key Patterns

1. **Multi-Layer Context** - Add context at each architectural layer
2. **Request Context** - Track requests across entire flow
3. **Timing Context** - Measure operation duration
4. **Retry Context** - Track retry attempts
5. **Observability Integration** - Flow context to logs and traces

### Context Best Practices

✅ **DO:**
- Use consistent naming conventions (snake_case)
- Add context at every layer
- Track operation timing
- Include request/trace IDs
- Limit context size

❌ **DON'T:**
- Include sensitive data (passwords, PII, secrets)
- Duplicate existing error fields
- Add unlimited data
- Use inconsistent key names
- Forget to add context

### Quick Reference

```kotlin
// Basic context
error.withContext("key" to "value")

// Multi-layer pattern
repository.fetch()
    .mapError { it.withContext("layer" to "infrastructure") }
    .mapError { it.withContext("service" to "UserService") }
    .mapError { it.withContext("request_id" to requestId) }

// Request context extension
result.withRequestContext(requestContext)

// Timing wrapper
measureOperation("fetchUser") {
    SpiceResult.catchingSuspend { apiClient.getUser(id) }
}

// Retry with context
retryWithContext(maxAttempts = 3) { attempt ->
    fetchData(url)
}
```

## Next Steps

- [SpiceResult Guide](./spice-result) - Complete SpiceResult API
- [Best Practices](./best-practices) - Advanced error handling patterns
- [Observability](../observability/overview) - Integration with OpenTelemetry
- [Inline Functions](./inline-functions) - Understanding catchingSuspend
