# SpiceResult Guide

Complete guide to using `SpiceResult<T>` for type-safe error handling in Spice Framework.

## What is SpiceResult?

`SpiceResult<T>` is a sealed class that represents the outcome of an operation that can either succeed with a value of type `T` or fail with a `SpiceError`.

```kotlin
sealed class SpiceResult<T> {
    data class Success<T>(val value: T) : SpiceResult<T>()
    data class Failure(val error: SpiceError) : SpiceResult<Nothing>()
}
```

## Creating Results

### Success

```kotlin
// Simple success
val result = SpiceResult.success("Hello, World!")

// Success with complex type
val user = User(id = "123", name = "Alice")
val result = SpiceResult.success(user)
```

### Failure

```kotlin
// Simple failure
val result = SpiceResult.failure<String>(
    SpiceError.validationError("Invalid input")
)

// Failure with context
val result = SpiceResult.failure<User>(
    SpiceError.networkError(
        message = "Failed to fetch user",
        statusCode = 404,
        endpoint = "/api/users/123"
    )
)
```

### Catching Exceptions

```kotlin
// Synchronous
val result = SpiceResult.catching {
    parseJson(jsonString)  // May throw exception
}

// Asynchronous
val result = SpiceResult.catchingSuspend {
    delay(100)
    fetchFromAPI()  // May throw exception
}
```

## Checking Results

### Pattern Matching

```kotlin
when (result) {
    is SpiceResult.Success -> {
        println("Success: ${result.value}")
    }
    is SpiceResult.Failure -> {
        println("Error: ${result.error.message}")
        println("Code: ${result.error.code}")
    }
}
```

### Boolean Checks

```kotlin
if (result.isSuccess) {
    // Handle success
}

if (result.isFailure) {
    // Handle failure
}
```

## Extracting Values

### getOrNull()

Returns the value if success, null if failure:

```kotlin
val value: String? = result.getOrNull()
```

### getOrElse()

Returns the value if success, default if failure:

```kotlin
val value: String = result.getOrElse("default")

// With lambda
val value: String = result.getOrElse { error ->
    "Error: ${error.message}"
}
```

### getOrThrow()

Returns the value if success, throws if failure:

```kotlin
try {
    val value: String = result.getOrThrow()
} catch (e: SpiceException) {
    println("Error code: ${e.code}")
}
```

## Transforming Results

### map()

Transform the success value:

```kotlin
val result: SpiceResult<Int> = SpiceResult.success(5)

val doubled: SpiceResult<Int> = result.map { it * 2 }  // 10

val message: SpiceResult<String> = doubled.map { "Value: $it" }  // "Value: 10"
```

Map preserves failures:

```kotlin
val result: SpiceResult<Int> = SpiceResult.failure(error)

val doubled = result.map { it * 2 }  // Still Failure with same error
```

### flatMap()

Chain operations that return Results:

```kotlin
fun divide(a: Int, b: Int): SpiceResult<Int> {
    return if (b == 0) {
        SpiceResult.failure(SpiceError.validationError("Division by zero"))
    } else {
        SpiceResult.success(a / b)
    }
}

SpiceResult.success(20)
    .flatMap { divide(it, 2) }   // Success(10)
    .flatMap { divide(it, 5) }   // Success(2)
    .flatMap { divide(it, 0) }   // Failure
    .flatMap { divide(it, 1) }   // Skipped (still Failure)
```

### mapError()

Transform the error:

```kotlin
result.mapError { error ->
    SpiceError.agentError(
        message = "Agent failed: ${error.message}",
        agentId = "my-agent"
    )
}
```

## Recovering from Errors

### recover()

Provide a fallback value:

```kotlin
val result: SpiceResult<String> = fetchFromAPI()
    .recover { error -> "Cached data" }

// Now result is always Success
```

### recoverWith()

Provide a fallback Result:

```kotlin
val result: SpiceResult<User> = fetchFromPrimaryAPI()
    .recoverWith { error ->
        fetchFromBackupAPI()  // Returns SpiceResult<User>
    }
    .recoverWith { error ->
        fetchFromCache()  // Returns SpiceResult<User>
    }
```

### Conditional Recovery

```kotlin
result.recoverWith { error ->
    when (error) {
        is SpiceError.NetworkError -> {
            // Try cache
            cacheClient.get(key)
                ?.let { SpiceResult.success(it) }
                ?: SpiceResult.failure(error)
        }
        is SpiceError.RateLimitError -> {
            // Wait and retry
            delay(error.retryAfterMs ?: 1000)
            retryOperation()
        }
        else -> SpiceResult.failure(error)
    }
}
```

## Side Effects

### onSuccess()

Execute action if successful:

```kotlin
result
    .onSuccess { value ->
        logger.info("Operation succeeded: $value")
    }
    .onSuccess { value ->
        metrics.increment("success_count")
    }
```

### onFailure()

Execute action if failed:

```kotlin
result
    .onFailure { error ->
        logger.error("Operation failed: ${error.message}")
    }
    .onFailure { error ->
        metrics.increment("error_count")
    }
```

### Chaining Side Effects

```kotlin
result
    .onSuccess { logger.info("Success: $it") }
    .onFailure { logger.error("Failed: ${it.message}") }
    .map { it.uppercase() }
    .onSuccess { println("Final value: $it") }
```

## Folding Results

Use `fold()` to handle both cases and produce a single value:

```kotlin
val message: String = result.fold(
    onSuccess = { value -> "Success: $value" },
    onFailure = { error -> "Error: ${error.message}" }
)
```

## Choosing the Right Operator

Understanding when to use each operator is crucial for clean, maintainable code.

### Operators Comparison

| Operator | Input | Output | Use Case |
|----------|-------|--------|----------|
| `map` | `SpiceResult<T>` | `SpiceResult<R>` | Transform success value, keep Result |
| `flatMap` | `SpiceResult<T>` | `SpiceResult<R>` | Chain operations that return Results |
| `mapError` | `SpiceResult<T>` | `SpiceResult<T>` | Transform error, keep Result |
| `recover` | `SpiceResult<T>` | `SpiceResult<T>` | Provide fallback value, always succeeds |
| `recoverWith` | `SpiceResult<T>` | `SpiceResult<T>` | Provide fallback Result |
| `fold` | `SpiceResult<T>` | `R` | Extract final value, **exits Result context** |
| `onSuccess` / `onFailure` | `SpiceResult<T>` | `SpiceResult<T>` | Side effects, keep Result |

### When to Use `mapError`

Use `mapError` when you want to **transform an error but keep the Result context** for further chaining:

```kotlin
// ✅ Use mapError: Transform error and continue chaining
repository.fetchUser(id)
    .mapError { error ->
        // Transform low-level error to domain error
        DomainError.UserNotFound("User $id not found", error.cause)
    }
    .recover { error ->
        // Can still recover after mapping error
        User.guest()
    }

// ✅ Use mapError: Add context to errors
client.call()
    .onFailure { error ->
        logger.error { "API call failed: ${error.message}" }
    }
    .mapError { error ->
        // Wrap with more context
        ServiceError("OpenAI API failed", error.cause)
    }
```

### When to Use `fold`

Use `fold` when you need to **exit the Result context** and produce a final value:

```kotlin
// ✅ Use fold: Convert to HTTP response
fun handleRequest(id: String): ResponseEntity<UserDto> {
    return userService.getUser(id)
        .fold(
            onSuccess = { user -> ResponseEntity.ok(user.toDto()) },
            onFailure = { error ->
                ResponseEntity
                    .status(error.toHttpStatus())
                    .body(ErrorDto(error.message))
            }
        )
}

// ✅ Use fold: Final value needed, no more chaining
val embedding: Embedding = embeddingService.generate(text)
    .fold(
        onSuccess = { it },
        onFailure = { error ->
            logger.error { "Embedding failed: ${error.message}" }
            Embedding.ZERO  // Fallback value
        }
    )
// embedding is Embedding, not SpiceResult<Embedding>
```

### When to Use `recover` / `recoverWith`

Use `recover` when you want to **provide a fallback value** but keep the Result context:

```kotlin
// ✅ Use recover: Simple fallback value
val data: SpiceResult<Data> = fetchFromCache()
    .recover { error ->
        Data.default()  // Always returns Success after this
    }

// ✅ Use recoverWith: Fallback chain
val user: SpiceResult<User> = fetchFromPrimary()
    .recoverWith { fetchFromBackup() }
    .recoverWith { fetchFromCache() }
    .recoverWith { SpiceResult.success(User.guest()) }
```

### Common Patterns

#### Pattern 1: Error Transformation in Repository Layer

```kotlin
// Repository: Transform DB errors to domain errors
class UserRepository {
    fun findById(id: String): SpiceResult<User> {
        return database.query(id)
            .mapError { error ->
                // Keep Result context for service layer
                DomainError.DatabaseError("User query failed", error.cause)
            }
    }
}
```

#### Pattern 2: Final Value Extraction in Controller

```kotlin
// Controller: Extract final HTTP response
class UserController {
    fun getUser(id: String): ResponseEntity<*> {
        return userService.getUser(id)
            .fold(
                // Exit Result context here
                onSuccess = { ResponseEntity.ok(it) },
                onFailure = { ResponseEntity.status(500).body(it.message) }
            )
    }
}
```

#### Pattern 3: Logging + Error Transformation

```kotlin
// ✅ Good: Separate concerns
client.generateEmbedding(text)
    .onFailure { error ->
        // Side effect: logging
        logger.error { "Embedding generation failed: ${error.message}" }
    }
    .mapError { error ->
        // Transformation: wrap in domain error
        DomainError.EmbeddingError("Failed to generate embedding", error.cause)
    }

// ❌ Avoid: Mixing concerns
client.generateEmbedding(text)
    .fold(
        onSuccess = { it },
        onFailure = { error ->
            // Mixing logging and error handling
            logger.error { "Failed: ${error.message}" }
            throw DomainError.EmbeddingError("...", error.cause).toException()
        }
    )
```

#### Pattern 4: Multi-Layer Error Handling

```kotlin
// Layer 1: Infrastructure - Network errors
fun callAPI(): SpiceResult<Response> {
    return httpClient.get("/api")
        .mapError { error ->
            NetworkError("API call failed", error.cause)
        }
}

// Layer 2: Service - Business errors
fun processData(): SpiceResult<Data> {
    return callAPI()
        .map { response -> response.data }
        .mapError { error ->
            ServiceError("Data processing failed", error.cause)
        }
}

// Layer 3: Controller - HTTP responses
fun handleRequest(): ResponseEntity<*> {
    return processData()
        .fold(
            onSuccess = { data -> ResponseEntity.ok(data) },
            onFailure = { error -> ResponseEntity.status(500).build() }
        )
}
```

### Decision Tree

```
Need to transform the value?
├─ Yes → Use `map` (stays in Result context)
└─ No
   ├─ Need to transform the error?
   │  ├─ Yes → Use `mapError` (stays in Result context)
   │  └─ No
   │     ├─ Need a fallback value?
   │     │  ├─ Yes → Use `recover` or `recoverWith` (stays in Result context)
   │     │  └─ No
   │     │     ├─ Need final value (no more chaining)?
   │     │     │  ├─ Yes → Use `fold` (exits Result context)
   │     │     │  └─ No → Use `onSuccess` / `onFailure` (side effects)
```

### Key Principle

**Stay in Result context (`mapError`, `recover`) until you need a final value (`fold`)**

```kotlin
// ✅ Good: Stay in Result context through layers
repository.fetch()
    .mapError { /* domain error */ }      // Still SpiceResult
    .recover { /* fallback */ }           // Still SpiceResult
    .fold(                                // Exit to final value
        onSuccess = { it },
        onFailure = { throw it.toException() }
    )

// ❌ Bad: Exit too early
repository.fetch()
    .fold(                                // Exit too early!
        onSuccess = { it },
        onFailure = { throw DomainError(...).toException() }
    )
// Can't chain anymore after fold
```

## Working with Collections

### Collecting Results

```kotlin
val results: List<SpiceResult<User>> = userIds.map { id ->
    fetchUser(id)
}

// Get all successful values
val users: List<User> = results.mapNotNull { it.getOrNull() }

// Check if all succeeded
val allSucceeded: Boolean = results.all { it.isSuccess }

// Get first failure
val firstError: SpiceError? = results
    .firstOrNull { it.isFailure }
    ?.let { (it as SpiceResult.Failure).error }
```

### Combining Results

```kotlin
fun combineResults(
    result1: SpiceResult<String>,
    result2: SpiceResult<Int>
): SpiceResult<Pair<String, Int>> {
    return result1.flatMap { str ->
        result2.map { num ->
            str to num
        }
    }
}
```

## Flow Integration

### Convert Flow to Results

```kotlin
flow {
    emit(fetchData())
}.asResult()  // Flow<SpiceResult<Data>>
```

### Filter Successes

```kotlin
flow {
    emit(SpiceResult.success(1))
    emit(SpiceResult.failure(error))
    emit(SpiceResult.success(2))
}
    .filterSuccesses()  // Flow<Int> - only 1 and 2
```

### Unwrap Successes

```kotlin
flow {
    emit(SpiceResult.success(1))
    emit(SpiceResult.failure(error))  // Throws exception
    emit(SpiceResult.success(2))
}
    .unwrapSuccesses()  // Flow<Int> - throws on failure
```

## Real-World Examples

### API Call with Retry

```kotlin
suspend fun fetchWithRetry(
    url: String,
    maxRetries: Int = 3
): SpiceResult<String> {
    var attempts = 0

    while (attempts < maxRetries) {
        val result = SpiceResult.catchingSuspend {
            httpClient.get(url)
        }

        when {
            result.isSuccess -> return result
            attempts == maxRetries - 1 -> return result
            else -> {
                attempts++
                delay(1000L * attempts)  // Exponential backoff
            }
        }
    }

    return SpiceResult.failure(
        SpiceError.networkError("Max retries exceeded")
    )
}
```

### Data Pipeline

```kotlin
suspend fun processUserData(userId: String): SpiceResult<ProcessedData> {
    return fetchUser(userId)
        .flatMap { user -> validateUser(user) }
        .flatMap { user -> enrichUserData(user) }
        .map { enrichedUser -> processData(enrichedUser) }
        .recover { error ->
            logger.error("Pipeline failed: ${error.message}")
            ProcessedData.empty()
        }
}
```

### Agent Processing

```kotlin
suspend fun processWithAgent(input: String): SpiceResult<String> {
    return SpiceResult.catchingSuspend {
        val agent = claudeAgent(apiKey = getApiKey())
        val comm = Comm(content = input, from = "user")
        agent.processComm(comm)
    }
        .map { comm -> comm.content }
        .mapError { error ->
            when (error) {
                is SpiceError.NetworkError ->
                    SpiceError.agentError("Claude API unavailable", cause = error)
                is SpiceError.RateLimitError ->
                    SpiceError.agentError("Rate limit exceeded", cause = error)
                else -> error
            }
        }
        .onFailure { error ->
            metrics.recordError("agent_processing", error.code)
        }
}
```

## Best Practices

### 1. Always Use Result Types in Public APIs

```kotlin
// ✅ Good - Errors are explicit
suspend fun fetchData(): SpiceResult<Data>

// ❌ Bad - Errors are hidden
suspend fun fetchData(): Data  // Can throw!
```

### 2. Prefer flatMap for Chaining

```kotlin
// ✅ Good
fetchUser()
    .flatMap { processUser(it) }
    .flatMap { saveUser(it) }

// ❌ Bad - Loses error information
fetchUser()
    .map { processUser(it).getOrThrow() }  // May throw!
    .map { saveUser(it).getOrThrow() }
```

### 3. Use Specific Error Types

```kotlin
// ✅ Good - Specific error with context
SpiceError.validationError(
    message = "Invalid email",
    field = "email",
    expectedType = "email",
    actualValue = input
)

// ❌ Bad - Generic error
SpiceError.unknownError("Something went wrong")
```

### 4. Recover at the Right Level

```kotlin
// ✅ Good - Recover where you can handle it
fun fetchUserProfile(): SpiceResult<Profile> {
    return fetchFromAPI()
        .recoverWith { fetchFromCache() }
        .recoverWith { fetchDefault() }
}

// ❌ Bad - Don't swallow all errors
fun fetchUserProfile(): Profile {
    return fetchFromAPI()
        .recover { Profile.empty() }  // Lost error information!
        .getOrThrow()
}
```

## Next Steps

- [SpiceError Types](./spice-error) - Learn about all error types
- [Best Practices](./best-practices) - Advanced error handling patterns
