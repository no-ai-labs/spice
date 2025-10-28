# Error Handling Best Practices

Advanced patterns and best practices for error handling in Spice Framework.

## Design Patterns

### 1. Fail Fast, Recover Gracefully

```kotlin
suspend fun processUserRequest(request: UserRequest): SpiceResult<Response> {
    // Fail fast on validation
    return validateRequest(request)
        .flatMap { validated ->
            // Continue processing
            processData(validated)
        }
        .flatMap { processed ->
            // Save results
            saveResults(processed)
        }
        .recover { error ->
            // Recover gracefully with fallback
            logger.error("Processing failed: ${error.message}")
            Response.fallback(error.message)
        }
}
```

### 2. Use Result Types in Public APIs

```kotlin
// ✅ Good - Clear contract
interface UserRepository {
    suspend fun findUser(id: String): SpiceResult<User>
    suspend fun saveUser(user: User): SpiceResult<Unit>
}

// ❌ Bad - Hidden errors
interface UserRepository {
    suspend fun findUser(id: String): User?  // Why null? Error? Not found?
    suspend fun saveUser(user: User): Unit   // Can throw!
}
```

### 3. Layer-Specific Error Handling

```kotlin
// Domain Layer - Business logic errors
fun validateOrder(order: Order): SpiceResult<Order> {
    return when {
        order.items.isEmpty() ->
            SpiceResult.failure(SpiceError.validationError(
                "Order must have at least one item",
                field = "items"
            ))
        order.total < 0 ->
            SpiceResult.failure(SpiceError.validationError(
                "Order total cannot be negative",
                field = "total"
            ))
        else -> SpiceResult.success(order)
    }
}

// Infrastructure Layer - Technical errors
suspend fun saveToDatabase(data: Data): SpiceResult<Unit> {
    return SpiceResult.catchingSuspend {
        database.insert(data)
    }.mapError { error ->
        when (error) {
            is SpiceError.SerializationError ->
                SpiceError.configError("Database schema mismatch", cause = error)
            else -> error
        }
    }
}

// Application Layer - Orchestration
suspend fun createOrder(request: CreateOrderRequest): SpiceResult<Order> {
    return validateOrderRequest(request)      // Domain validation
        .flatMap { buildOrder(it) }           // Domain logic
        .flatMap { saveToDatabase(it) }       // Infrastructure
        .recover { error ->                    // Application recovery
            logger.error("Order creation failed", error)
            notifyAdmin(error)
            Order.failed(error.message)
        }
}
```

## Error Recovery Strategies

### Circuit Breaker Pattern

```kotlin
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 60000
) {
    private var failures = 0
    private var lastFailureTime = 0L
    private var state = State.CLOSED

    enum class State { CLOSED, OPEN, HALF_OPEN }

    suspend fun <T> execute(
        operation: suspend () -> SpiceResult<T>
    ): SpiceResult<T> {
        when (state) {
            State.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime > resetTimeoutMs) {
                    state = State.HALF_OPEN
                } else {
                    return SpiceResult.failure(
                        SpiceError.networkError("Circuit breaker is OPEN")
                    )
                }
            }
            else -> { /* Continue */ }
        }

        return operation()
            .onSuccess {
                failures = 0
                state = State.CLOSED
            }
            .onFailure { error ->
                failures++
                lastFailureTime = System.currentTimeMillis()
                if (failures >= failureThreshold) {
                    state = State.OPEN
                }
            }
    }
}

// Usage
val circuitBreaker = CircuitBreaker()

suspend fun callExternalAPI(): SpiceResult<Data> {
    return circuitBreaker.execute {
        SpiceResult.catchingSuspend {
            httpClient.get("/api/data")
        }
    }
}
```

### Retry with Exponential Backoff

```kotlin
suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1000,
    maxDelayMs: Long = 10000,
    factor: Double = 2.0,
    operation: suspend () -> SpiceResult<T>
): SpiceResult<T> {
    var currentAttempt = 0
    var currentDelay = initialDelayMs

    while (currentAttempt < maxAttempts) {
        val result = operation()

        if (result.isSuccess) {
            return result
        }

        // Check if error is retryable
        val shouldRetry = (result as SpiceResult.Failure).error.let { error ->
            when (error) {
                is SpiceError.NetworkError -> error.statusCode in listOf(429, 503, 504)
                is SpiceError.TimeoutError -> true
                is SpiceError.RateLimitError -> true
                else -> false
            }
        }

        if (!shouldRetry || currentAttempt == maxAttempts - 1) {
            return result
        }

        delay(currentDelay)
        currentDelay = minOf((currentDelay * factor).toLong(), maxDelayMs)
        currentAttempt++
    }

    return SpiceResult.failure(
        SpiceError.timeoutError("Max retry attempts exceeded")
    )
}

// Usage
suspend fun fetchData(): SpiceResult<Data> {
    return retryWithBackoff {
        SpiceResult.catchingSuspend {
            apiClient.fetchData()
        }
    }
}
```

### Fallback Chain

```kotlin
suspend fun getUserData(userId: String): SpiceResult<UserData> {
    return fetchFromPrimaryAPI(userId)
        .recoverWith { error1 ->
            logger.warn("Primary API failed: ${error1.message}")
            fetchFromSecondaryAPI(userId)
        }
        .recoverWith { error2 ->
            logger.warn("Secondary API failed: ${error2.message}")
            fetchFromCache(userId)
        }
        .recoverWith { error3 ->
            logger.warn("Cache failed: ${error3.message}")
            SpiceResult.success(UserData.default(userId))
        }
}
```

## Testing Error Scenarios

### Unit Testing

```kotlin
class UserServiceTest : StringSpec({

    "should return validation error for empty email" {
        val service = UserService()

        val result = service.validateEmail("")

        result.isFailure shouldBe true
        val error = (result as SpiceResult.Failure).error
        error.shouldBeInstanceOf<SpiceError.ValidationError>()
        error.message shouldContain "email"
    }

    "should recover from network error with cache" {
        val apiClient = mockk<APIClient> {
            coEvery { fetchUser(any()) } returns
                SpiceResult.failure(SpiceError.networkError("Connection failed"))
        }
        val cache = mockk<Cache> {
            every { getUser(any()) } returns User(id = "123", name = "Cached")
        }

        val service = UserService(apiClient, cache)
        val result = service.getUser("123")

        result.isSuccess shouldBe true
        result.getOrNull()?.name shouldBe "Cached"
    }
})
```

### Property-Based Testing

```kotlin
class ErrorHandlingPropertyTest : StringSpec({

    "map should preserve failures" {
        checkAll(
            Arb.string(1..100),
            Arb.int()
        ) { errorMessage, value ->
            val error = SpiceError.validationError(errorMessage)
            val result: SpiceResult<Int> = SpiceResult.failure<Int>(error)
                .map { it * 2 }
                .map { it + value }

            result.isFailure shouldBe true
            (result as SpiceResult.Failure).error shouldBe error
        }
    }

    "recover should always produce success" {
        checkAll(Arb.string(1..100)) { errorMessage ->
            val error = SpiceError.validationError(errorMessage)
            val result: SpiceResult<String> = SpiceResult.failure<String>(error)
                .recover { "recovered" }

            result.isSuccess shouldBe true
            result.getOrNull() shouldBe "recovered"
        }
    }
})
```

## Logging and Monitoring

### Structured Logging

```kotlin
result
    .onSuccess { value ->
        logger.info(
            message = "Operation succeeded",
            tags = mapOf(
                "operation" to "fetch_user",
                "user_id" to userId,
                "duration_ms" to duration
            )
        )
    }
    .onFailure { error ->
        logger.error(
            message = "Operation failed: ${error.message}",
            exception = error.cause,
            tags = mapOf(
                "operation" to "fetch_user",
                "error_code" to error.code,
                "user_id" to userId,
                "duration_ms" to duration,
                "context" to error.context
            )
        )
    }
```

### Metrics Collection

```kotlin
suspend fun <T> measureAndTrack(
    operation: String,
    block: suspend () -> SpiceResult<T>
): SpiceResult<T> {
    val start = System.currentTimeMillis()

    return block()
        .onSuccess {
            val duration = System.currentTimeMillis() - start
            metrics.recordSuccess(operation, duration)
        }
        .onFailure { error ->
            val duration = System.currentTimeMillis() - start
            metrics.recordFailure(operation, error.code, duration)
        }
}

// Usage
suspend fun fetchUser(id: String): SpiceResult<User> {
    return measureAndTrack("fetch_user") {
        SpiceResult.catchingSuspend {
            apiClient.getUser(id)
        }
    }
}
```

## Error Context Enrichment

### Adding Request Context

```kotlin
class RequestContext(
    val requestId: String,
    val userId: String?,
    val traceId: String
)

fun <T> SpiceResult<T>.withRequestContext(
    context: RequestContext
): SpiceResult<T> {
    return this.mapError { error ->
        error.withContext(
            "request_id" to context.requestId,
            "user_id" to (context.userId ?: "anonymous"),
            "trace_id" to context.traceId
        )
    }
}

// Usage
suspend fun handleRequest(request: Request): SpiceResult<Response> {
    val context = RequestContext(
        requestId = UUID.randomUUID().toString(),
        userId = request.userId,
        traceId = request.headers["X-Trace-ID"] ?: ""
    )

    return processRequest(request)
        .withRequestContext(context)
}
```

### Error Aggregation

```kotlin
class ErrorAggregator {
    private val errors = mutableListOf<SpiceError>()

    fun add(error: SpiceError) {
        errors.add(error)
    }

    fun hasErrors(): Boolean = errors.isNotEmpty()

    fun toResult(): SpiceResult<Unit> {
        return if (errors.isEmpty()) {
            SpiceResult.success(Unit)
        } else {
            SpiceResult.failure(
                SpiceError.validationError(
                    message = "Multiple validation errors occurred",
                    context = mapOf(
                        "error_count" to errors.size,
                        "errors" to errors.map {
                            mapOf(
                                "code" to it.code,
                                "message" to it.message
                            )
                        }
                    )
                )
            )
        }
    }
}

// Usage
fun validateUser(user: User): SpiceResult<User> {
    val aggregator = ErrorAggregator()

    if (user.email.isBlank()) {
        aggregator.add(SpiceError.validationError("Email is required", field = "email"))
    }
    if (user.name.isBlank()) {
        aggregator.add(SpiceError.validationError("Name is required", field = "name"))
    }
    if (user.age < 0) {
        aggregator.add(SpiceError.validationError("Age must be positive", field = "age"))
    }

    return if (aggregator.hasErrors()) {
        aggregator.toResult().map { user }
    } else {
        SpiceResult.success(user)
    }
}
```

## Common Antipatterns

### ❌ Swallowing Errors

```kotlin
// Bad - Lost all error information
fun fetchData(): Data {
    return apiClient.fetch()
        .recover { Data.empty() }  // What went wrong?
        .getOrElse(Data.empty())
}

// Good - Preserve error context
fun fetchData(): SpiceResult<Data> {
    return apiClient.fetch()
        .recoverWith { error ->
            logger.error("Fetch failed: ${error.message}", error)
            metrics.recordError("fetch_data", error.code)

            // Try cache as fallback
            cache.get("data")
                ?.let { SpiceResult.success(it) }
                ?: SpiceResult.failure(error)  // Preserve original error
        }
}
```

### ❌ Generic Error Messages

```kotlin
// Bad
SpiceError.unknownError("Something went wrong")

// Good
SpiceError.networkError(
    message = "Failed to connect to authentication service",
    statusCode = 503,
    endpoint = "https://auth.example.com/token"
).withContext(
    "retry_attempt" to 3,
    "timeout_ms" to 30000
)
```

### ❌ Ignoring Error Types

```kotlin
// Bad - Treating all errors the same
result.recover { error -> defaultValue }

// Good - Handle different errors appropriately
result.recoverWith { error ->
    when (error) {
        is SpiceError.RateLimitError -> {
            delay(error.retryAfterMs ?: 1000)
            retryOperation()
        }
        is SpiceError.NetworkError -> {
            tryCache().recoverWith { SpiceResult.success(defaultValue) }
        }
        is SpiceError.AuthenticationError -> {
            // Don't retry auth errors
            SpiceResult.failure(error)
        }
        else -> {
            SpiceResult.success(defaultValue)
        }
    }
}
```

## Summary

**Key Takeaways:**

1. ✅ Use `SpiceResult<T>` for all operations that can fail
2. ✅ Choose specific error types with rich context
3. ✅ Recover at the appropriate layer
4. ✅ Log and monitor errors systematically
5. ✅ Test both success and failure paths
6. ✅ Implement retry and circuit breaker patterns
7. ✅ Preserve error chains with `cause`
8. ❌ Don't swallow errors without logging
9. ❌ Don't use generic error messages
10. ❌ Don't ignore error types in recovery

## Next Steps

- [SpiceResult Guide](./spice-result) - Complete SpiceResult API
- [SpiceError Types](./spice-error) - All error types reference
- [Testing Guide](../core-concepts/testing) - Testing strategies
