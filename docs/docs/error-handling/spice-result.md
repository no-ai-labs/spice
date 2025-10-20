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
