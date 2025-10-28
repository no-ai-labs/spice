# Error Handling

Spice Framework provides a comprehensive, type-safe error handling system that eliminates try-catch hell and makes error handling explicit and composable.

## Overview

The error handling system consists of two main components:

- **`SpiceResult<T>`** - A sealed class representing either success or failure
- **`SpiceError`** - A hierarchy of typed errors for different failure scenarios

## Why SpiceResult?

Traditional error handling in Kotlin has several problems:

```kotlin
// ❌ Problem 1: Exceptions are invisible in type signatures
fun processData(): String {
    throw Exception("Failed!")  // Caller has no idea this can fail
}

// ❌ Problem 2: Null doesn't provide error context
fun findUser(id: String): User? {
    return null  // Why did it fail? We don't know!
}

// ❌ Problem 3: Try-catch hell
try {
    val data = fetchData()
    try {
        val processed = processData(data)
        try {
            saveData(processed)
        } catch (e: IOException) {
            // Handle save error
        }
    } catch (e: ValidationException) {
        // Handle validation error
    }
} catch (e: NetworkException) {
    // Handle network error
}
```

SpiceResult solves all of these:

```kotlin
// ✅ Errors are explicit in the type signature
fun processData(): SpiceResult<String>

// ✅ Errors carry context
SpiceError.ValidationError(
    message = "Invalid email format",
    field = "email",
    expectedType = "email",
    actualValue = "not-an-email"
)

// ✅ Composable error handling
fetchData()
    .flatMap { processData(it) }
    .flatMap { saveData(it) }
    .recover { error -> /* Handle any error */ }
```

## Key Features

### 1. Railway-Oriented Programming

SpiceResult implements the Railway-Oriented Programming pattern, where operations either stay on the "success track" or switch to the "failure track":

```kotlin
SpiceResult.success(10)
    .map { it * 2 }              // Success: 20
    .flatMap { divide(it, 5) }   // Success: 4
    .map { it + 1 }              // Success: 5
    .getOrElse(0)                // Returns: 5

SpiceResult.success(10)
    .map { it * 2 }              // Success: 20
    .flatMap { divide(it, 0) }   // Failure: Division by zero
    .map { it + 1 }              // Skipped (still Failure)
    .getOrElse(0)                // Returns: 0 (default)
```

### 2. Typed Error Hierarchy

11 specialized error types provide context for different failure scenarios:

```kotlin
sealed class SpiceError {
    data class AgentError(...)
    data class CommError(...)
    data class ToolError(...)
    data class ConfigurationError(...)
    data class ValidationError(...)
    data class NetworkError(...)
    data class TimeoutError(...)
    data class AuthenticationError(...)
    data class RateLimitError(...)
    data class SerializationError(...)
    data class UnknownError(...)
}
```

### 3. Functional Operators

Powerful operators for transforming and recovering from errors:

- **`map`** - Transform success values
- **`flatMap`** - Chain operations that return Results
- **`recover`** - Recover from errors with a default value
- **`recoverWith`** - Recover from errors with another Result
- **`fold`** - Handle both success and failure cases
- **`onSuccess`** / **`onFailure`** - Side effects
- **`getOrElse`** - Get value or default
- **`getOrThrow`** - Get value or throw exception

### 4. Async Support

First-class support for coroutines and Flow:

```kotlin
// Catching suspend functions
SpiceResult.catchingSuspend {
    delay(100)
    fetchDataFromAPI()
}

// Flow integration
flow { emit(data) }
    .asResult()  // Convert to Flow<SpiceResult<T>>
    .filterSuccesses()  // Only emit successful values
```

## Quick Start

### Basic Usage

```kotlin
import io.github.noailabs.spice.error.*

// Create results
val success = SpiceResult.success("Hello")
val failure = SpiceResult.failure<String>(
    SpiceError.validationError("Invalid input")
)

// Check result
when (result) {
    is SpiceResult.Success -> println("Value: ${result.value}")
    is SpiceResult.Failure -> println("Error: ${result.error.message}")
}
```

### With Agents

```kotlin
val agent = claudeAgent(apiKey = "...")

agent.processComm(comm)
    .toResult()  // Convert Comm to SpiceResult
    .map { it.content.uppercase() }
    .recover { error -> "Fallback response" }
    .onSuccess { println("Success: $it") }
    .onFailure { error -> logger.error("Failed: ${error.message}") }
```

### Error Recovery

```kotlin
fun fetchUserData(userId: String): SpiceResult<User> {
    return SpiceResult.catching {
        apiClient.getUser(userId)
    }.recoverWith { error ->
        when (error) {
            is SpiceError.NetworkError -> {
                // Try cache
                cacheClient.getUser(userId)
                    ?.let { SpiceResult.success(it) }
                    ?: SpiceResult.failure(error)
            }
            is SpiceError.RateLimitError -> {
                // Wait and retry
                delay(error.retryAfterMs ?: 1000)
                fetchUserData(userId)
            }
            else -> SpiceResult.failure(error)
        }
    }
}
```

## Next Steps

- [SpiceResult Guide](./spice-result) - Detailed SpiceResult API
- [SpiceError Types](./spice-error) - All error types explained
- [Best Practices](./best-practices) - Error handling patterns
