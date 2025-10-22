# Inline Functions & catchingSuspend

Understanding inline functions and avoiding common pitfalls with `catchingSuspend`.

## The Problem

You might encounter this confusing error when using `catchingSuspend`:

```kotlin
// ❌ COMPILE ERROR: 'return' is not allowed here
fun processData(): SpiceResult<Data> {
    return SpiceResult.catchingSuspend {
        if (condition) {
            return SpiceResult.failure(error) // ❌ Error here!
        }
        fetchData()
    }
}
```

**Error message:**
```
'return' is not allowed here
return is not allowed here for inlineable lambda parameter
```

## Why This Happens

### Inline Functions Explained

`catchingSuspend` is defined as an **inline function**:

```kotlin
// From SpiceResult.kt
suspend inline fun <T> catchingSuspend(
    crossinline block: suspend () -> T
): SpiceResult<T> = try {
    Success(block())
} catch (e: Exception) {
    Failure(SpiceError.fromException(e)) as SpiceResult<T>
}
```

**What `inline` means:**
- Function code is **copied directly** into the call site at compile time
- No function call overhead
- But: Changes how `return` behaves!

### Non-Local Returns

In regular (non-inline) functions, `return` exits the lambda:

```kotlin
// Regular function
fun example() {
    list.forEach { item ->
        if (item == 5) return@forEach  // Exits lambda only
    }
    println("This still prints")
}
```

In inline functions, `return` exits the **enclosing function**:

```kotlin
// Inline function (like forEach)
fun example() {
    list.forEach { item ->  // forEach is inline
        if (item == 5) return  // Exits example(), not just lambda!
    }
    println("This NEVER prints if item == 5")
}
```

### Why It Breaks try-catch

```kotlin
// What you write:
return SpiceResult.catchingSuspend {
    if (condition) return SpiceResult.failure(error)
    fetchData()
}

// What actually happens after inlining:
return try {
    if (condition) return SpiceResult.failure(error)  // Returns from outer function!
    Success(fetchData())  // Never reached
} catch (e: Exception) {
    Failure(SpiceError.fromException(e))
}
```

The `return` **escapes the try-catch block entirely**, defeating its purpose!

## Solutions

### Solution 1: Guard Clause (Recommended)

Move the condition **outside** the inline function:

```kotlin
// ✅ Good - Clean and readable
fun processData(): SpiceResult<Data> {
    if (condition) {
        return SpiceResult.failure(error)
    }

    return SpiceResult.catchingSuspend {
        fetchData()
    }
}
```

**Why this works:**
- No `return` inside the inline function
- Early return pattern is clear
- try-catch wraps only the actual operation

### Solution 2: Return Expression

Use an `if` expression that returns a `SpiceResult`:

```kotlin
// ✅ Good - Functional style
fun processData(): SpiceResult<Data> {
    return if (condition) {
        SpiceResult.failure(error)
    } else {
        SpiceResult.catchingSuspend {
            fetchData()
        }
    }
}
```

**Why this works:**
- No `return` inside the lambda
- The `if` expression evaluates to a `SpiceResult`

### Solution 3: flatMap Chain

Use `flatMap` for sequential validation:

```kotlin
// ✅ Good - Railway-oriented programming
fun processData(): SpiceResult<Data> {
    return validateInput()
        .flatMap { input ->
            SpiceResult.catchingSuspend {
                fetchData(input)
            }
        }
}

private fun validateInput(): SpiceResult<Input> {
    return if (condition) {
        SpiceResult.failure(error)
    } else {
        SpiceResult.success(input)
    }
}
```

**Why this works:**
- Validation is separate from exception handling
- Clean pipeline of operations
- Each step returns a `SpiceResult`

### Solution 4: Nested Result

Return a `SpiceResult` **inside** the lambda:

```kotlin
// ✅ Works but verbose
fun processData(): SpiceResult<Data> {
    return SpiceResult.catchingSuspend {
        if (condition) {
            throw IllegalArgumentException("Invalid input")
        }
        fetchData()
    }
}
```

**Why this works:**
- Throw an exception instead of returning
- `catchingSuspend` catches it and wraps in `SpiceResult`
- More verbose than guard clause

## Real-World Examples

### Example 1: Input Validation

```kotlin
// ❌ Bad - Won't compile
suspend fun fetchUser(id: String): SpiceResult<User> {
    return SpiceResult.catchingSuspend {
        if (id.isBlank()) {
            return SpiceResult.failure(  // ❌ Error!
                SpiceError.validationError("ID is required")
            )
        }
        apiClient.getUser(id)
    }
}

// ✅ Good - Guard clause
suspend fun fetchUser(id: String): SpiceResult<User> {
    if (id.isBlank()) {
        return SpiceResult.failure(
            SpiceError.validationError("ID is required")
        )
    }

    return SpiceResult.catchingSuspend {
        apiClient.getUser(id)
    }
}
```

### Example 2: Conditional API Call

```kotlin
// ❌ Bad - Won't compile
suspend fun getData(useCache: Boolean): SpiceResult<Data> {
    return SpiceResult.catchingSuspend {
        if (useCache) {
            val cached = cache.get()
            if (cached != null) {
                return SpiceResult.success(cached)  // ❌ Error!
            }
        }
        apiClient.fetchData()
    }
}

// ✅ Good - Early returns outside
suspend fun getData(useCache: Boolean): SpiceResult<Data> {
    if (useCache) {
        val cached = cache.get()
        if (cached != null) {
            return SpiceResult.success(cached)
        }
    }

    return SpiceResult.catchingSuspend {
        apiClient.fetchData()
    }
}
```

### Example 3: Multi-Step Validation

```kotlin
// ❌ Bad - Multiple returns won't work
suspend fun createOrder(order: Order): SpiceResult<Order> {
    return SpiceResult.catchingSuspend {
        if (order.items.isEmpty()) {
            return SpiceResult.failure(error1)  // ❌ Error!
        }
        if (order.total < 0) {
            return SpiceResult.failure(error2)  // ❌ Error!
        }
        if (!order.hasValidPayment()) {
            return SpiceResult.failure(error3)  // ❌ Error!
        }
        database.insertOrder(order)
    }
}

// ✅ Good - Validation before catchingSuspend
suspend fun createOrder(order: Order): SpiceResult<Order> {
    // Validate first
    if (order.items.isEmpty()) {
        return SpiceResult.failure(
            SpiceError.validationError("Order must have items")
        )
    }
    if (order.total < 0) {
        return SpiceResult.failure(
            SpiceError.validationError("Total cannot be negative")
        )
    }
    if (!order.hasValidPayment()) {
        return SpiceResult.failure(
            SpiceError.validationError("Invalid payment method")
        )
    }

    // Then catch exceptions
    return SpiceResult.catchingSuspend {
        database.insertOrder(order)
    }
}

// ✅ Better - Use validation helper
suspend fun createOrder(order: Order): SpiceResult<Order> {
    return validateOrder(order)
        .flatMap { validOrder ->
            SpiceResult.catchingSuspend {
                database.insertOrder(validOrder)
            }
        }
}

private fun validateOrder(order: Order): SpiceResult<Order> {
    return when {
        order.items.isEmpty() -> SpiceResult.failure(
            SpiceError.validationError("Order must have items")
        )
        order.total < 0 -> SpiceResult.failure(
            SpiceError.validationError("Total cannot be negative")
        )
        !order.hasValidPayment() -> SpiceResult.failure(
            SpiceError.validationError("Invalid payment method")
        )
        else -> SpiceResult.success(order)
    }
}
```

## catching vs catchingSuspend

Both functions are inline, so the same rules apply:

```kotlin
// Synchronous version
inline fun <T> catching(block: () -> T): SpiceResult<T>

// Asynchronous version
suspend inline fun <T> catchingSuspend(crossinline block: suspend () -> T): SpiceResult<T>
```

**When to use each:**

```kotlin
// ✅ Use catching for synchronous operations
val result = SpiceResult.catching {
    parseJson(jsonString)
}

// ✅ Use catchingSuspend for suspend operations
val result = SpiceResult.catchingSuspend {
    fetchFromAPI()
}

// ❌ Don't use catching for suspend functions
val result = SpiceResult.catching {
    fetchFromAPI()  // ❌ Won't compile - catching is not suspend
}

// ❌ Don't use catchingSuspend for sync operations (works but unnecessary)
val result = SpiceResult.catchingSuspend {
    parseJson(jsonString)  // ⚠️ Works but use catching instead
}
```

## Pattern: Validation + Exception Handling

The best pattern is to **separate validation from exception handling**:

```kotlin
// ✅ Excellent pattern
suspend fun processRequest(request: Request): SpiceResult<Response> {
    // Step 1: Validate (business logic errors)
    return validateRequest(request)
        .flatMap { validRequest ->
            // Step 2: Execute (technical errors)
            SpiceResult.catchingSuspend {
                executeRequest(validRequest)
            }
        }
        .mapError { error ->
            // Step 3: Enrich with context
            error.withContext(
                "request_id" to request.id,
                "user_id" to request.userId
            )
        }
}

private fun validateRequest(request: Request): SpiceResult<Request> {
    // Validation logic - returns specific errors
    return when {
        request.userId.isBlank() ->
            SpiceResult.failure(SpiceError.validationError("User ID required"))

        request.amount < 0 ->
            SpiceResult.failure(SpiceError.validationError("Amount must be positive"))

        else ->
            SpiceResult.success(request)
    }
}

private suspend fun executeRequest(request: Request): Response {
    // Actual operation - throws exceptions
    return apiClient.call(request)
}
```

**Why this pattern is excellent:**
1. **Clear separation** - Validation vs exception handling
2. **Type-safe** - Validation never throws
3. **Testable** - Easy to test validation separately
4. **Maintainable** - Each function has single responsibility
5. **Composable** - Easy to chain with `flatMap`

## Common Pitfalls

### Pitfall 1: Nested catchingSuspend

```kotlin
// ❌ Bad - Nested catchingSuspend
val result = SpiceResult.catchingSuspend {
    val user = SpiceResult.catchingSuspend {  // ❌ Unnecessary nesting
        fetchUser(id)
    }.getOrThrow()

    processUser(user)
}

// ✅ Good - Use flatMap
val result = SpiceResult.catchingSuspend {
    fetchUser(id)
}.flatMap { user ->
    SpiceResult.catchingSuspend {
        processUser(user)
    }
}
```

### Pitfall 2: Returning null Instead of Result

```kotlin
// ❌ Bad - Returns null, losing error information
suspend fun fetchUser(id: String): User? {
    return SpiceResult.catchingSuspend {
        apiClient.getUser(id)
    }.getOrNull()  // ❌ Lost error details!
}

// ✅ Good - Returns Result with error
suspend fun fetchUser(id: String): SpiceResult<User> {
    return SpiceResult.catchingSuspend {
        apiClient.getUser(id)
    }
}
```

### Pitfall 3: Catching Too Much

```kotlin
// ❌ Bad - Catches validation errors as exceptions
suspend fun createUser(email: String): SpiceResult<User> {
    return SpiceResult.catchingSuspend {
        // Business logic errors should not throw!
        if (!email.contains("@")) {
            throw IllegalArgumentException("Invalid email")  // ❌ Bad practice
        }
        database.insertUser(User(email))
    }
}

// ✅ Good - Validate explicitly
suspend fun createUser(email: String): SpiceResult<User> {
    if (!email.contains("@")) {
        return SpiceResult.failure(
            SpiceError.validationError("Invalid email format")
        )
    }

    return SpiceResult.catchingSuspend {
        database.insertUser(User(email))
    }
}
```

### Pitfall 4: Forgetting await() in Async

```kotlin
// ❌ Bad - Returns Deferred, not the actual result
suspend fun fetchData(): SpiceResult<Data> {
    return SpiceResult.catchingSuspend {
        coroutineScope {
            async {
                apiClient.fetch()
            }  // ❌ Returns Deferred<Data>, not Data!
        }
    }
}

// ✅ Good - await() the result
suspend fun fetchData(): SpiceResult<Data> {
    return SpiceResult.catchingSuspend {
        coroutineScope {
            async {
                apiClient.fetch()
            }.await()  // ✅ Returns Data
        }
    }
}
```

## Advanced: Custom Inline Result Functions

You can create your own inline functions that return `SpiceResult`:

```kotlin
// Custom inline function for retry logic
suspend inline fun <T> retryWithResult(
    maxAttempts: Int = 3,
    crossinline block: suspend () -> T
): SpiceResult<T> {
    var lastError: Throwable? = null

    repeat(maxAttempts) { attempt ->
        try {
            return SpiceResult.success(block())
        } catch (e: Exception) {
            lastError = e
            if (attempt < maxAttempts - 1) {
                delay(1000L * (attempt + 1))
            }
        }
    }

    return SpiceResult.failure(
        SpiceError.networkError(
            "Failed after $maxAttempts attempts",
            cause = lastError
        )
    )
}

// Usage - same return rules apply!
suspend fun fetchWithRetry(): SpiceResult<Data> {
    // ✅ Good - No return inside
    return retryWithResult {
        apiClient.fetch()
    }

    // ❌ Bad - Return inside inline function
    return retryWithResult {
        if (cache.hasData()) {
            return SpiceResult.success(cache.get())  // ❌ Won't work!
        }
        apiClient.fetch()
    }
}
```

## Testing Inline Functions

Inline functions work normally in tests:

```kotlin
@Test
fun `should catch exceptions`() = runTest {
    val result = SpiceResult.catchingSuspend {
        throw RuntimeException("Test error")
    }

    assertTrue(result.isFailure)
    assertEquals("Test error", (result as SpiceResult.Failure).error.message)
}

@Test
fun `should return success`() = runTest {
    val result = SpiceResult.catchingSuspend {
        "success"
    }

    assertTrue(result.isSuccess)
    assertEquals("success", result.getOrNull())
}

@Test
fun `validation should happen outside catchingSuspend`() = runTest {
    suspend fun process(value: Int): SpiceResult<String> {
        if (value < 0) {
            return SpiceResult.failure(
                SpiceError.validationError("Must be positive")
            )
        }

        return SpiceResult.catchingSuspend {
            "Processed: $value"
        }
    }

    // Test validation
    val invalid = process(-1)
    assertTrue(invalid.isFailure)

    // Test success
    val valid = process(10)
    assertTrue(valid.isSuccess)
    assertEquals("Processed: 10", valid.getOrNull())
}
```

## Summary

### Key Takeaways

1. ✅ **Inline functions** paste code at call site - changes how `return` works
2. ✅ **Never `return` inside** `catchingSuspend` or `catching` - breaks try-catch
3. ✅ **Use guard clauses** to return early before the inline function
4. ✅ **Separate validation** (business logic) from exception handling (technical errors)
5. ✅ **Use `flatMap`** to chain operations that return `SpiceResult`
6. ✅ **Choose `catching`** for sync, `catchingSuspend` for async

### Quick Reference

```kotlin
// ❌ DON'T: Return inside inline function
SpiceResult.catchingSuspend {
    if (condition) return SpiceResult.failure(error)  // ❌
    doWork()
}

// ✅ DO: Guard clause before inline function
if (condition) {
    return SpiceResult.failure(error)
}
return SpiceResult.catchingSuspend {
    doWork()
}

// ✅ DO: If expression
return if (condition) {
    SpiceResult.failure(error)
} else {
    SpiceResult.catchingSuspend { doWork() }
}

// ✅ DO: Validation chain
validateInput()
    .flatMap { input ->
        SpiceResult.catchingSuspend { doWork(input) }
    }
```

## Next Steps

- [SpiceResult Guide](./spice-result) - Complete SpiceResult API
- [Best Practices](./best-practices) - Advanced error handling patterns
- [Error Types](./spice-error) - All SpiceError types
