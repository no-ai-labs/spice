# 🌶️ Spice Framework v0.2.0 Release Notes

**Release Date**: 2025-10-20
**Type**: Major Release (Breaking Changes)

## 🎯 Overview

Spice v0.2.0 introduces a comprehensive, type-safe error handling system that fundamentally improves how errors are handled throughout the framework. This is a **breaking change** that requires migration from existing code.

## ⚠️ Breaking Changes

### 1. Agent Interface Changes

**Before (v0.1.x)**:
```kotlin
interface Agent {
    suspend fun processComm(comm: Comm): Comm
    fun getTools(): List<Tool>
}
```

**After (v0.2.0)**:
```kotlin
interface Agent {
    suspend fun processComm(comm: Comm): SpiceResult<Comm>
    fun getTools(): List<Tool>
}
```

**Impact**: All `Agent.processComm()` calls now return `SpiceResult<Comm>` instead of `Comm`.

### 2. Tool Interface Changes

**Before (v0.1.x)**:
```kotlin
interface Tool {
    suspend fun execute(parameters: Map<String, Any>): ToolResult
}
```

**After (v0.2.0)**:
```kotlin
interface Tool {
    suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult>
}
```

**Impact**: All `Tool.execute()` calls now return `SpiceResult<ToolResult>`.

## ✨ New Features

### 1. SpiceResult<T> - Type-Safe Error Handling

A sealed class representing either success or failure:

```kotlin
sealed class SpiceResult<T> {
    data class Success<T>(val value: T) : SpiceResult<T>()
    data class Failure(val error: SpiceError) : SpiceResult<Nothing>()
}
```

**Key Features**:
- ✅ Railway-Oriented Programming pattern
- ✅ Composable error handling with `map`, `flatMap`, `recover`
- ✅ Flow integration with `asResult()` and `filterSuccesses()`
- ✅ Type-safe error propagation
- ✅ No more try-catch hell!

**Example**:
```kotlin
agent.processComm(comm)
    .map { it.content.uppercase() }
    .recover { error -> Comm.error(error.message) }
    .onSuccess { println("Success: $it") }
    .onFailure { logger.error("Failed: ${it.message}") }
```

### 2. SpiceError Hierarchy

11 typed error classes for different failure scenarios:

| Error Type | Use Case |
|-----------|----------|
| `AgentError` | Agent processing failures |
| `CommError` | Communication errors |
| `ToolError` | Tool execution failures |
| `ConfigurationError` | Invalid configuration |
| `ValidationError` | Input validation failures |
| `NetworkError` | HTTP/Network failures |
| `TimeoutError` | Operation timeouts |
| `AuthenticationError` | Auth/API key issues |
| `RateLimitError` | Rate limiting |
| `SerializationError` | JSON/data parsing |
| `UnknownError` | Unexpected errors |

**Example**:
```kotlin
SpiceError.networkError(
    message = "Failed to connect to API",
    statusCode = 503,
    endpoint = "/v1/chat/completions"
).withContext(
    "retry_attempt" to 3,
    "timeout_ms" to 30000
)
```

### 3. Comprehensive Testing Infrastructure

- **Kotest Integration**: Property-based testing for robust validation
- **Integration Tests**: Full agent workflow testing
- **100+ New Tests**: Covering all error scenarios

## 📚 Documentation

### New Documentation Sections

1. **Error Handling Overview** - Introduction to SpiceResult and SpiceError
2. **SpiceResult Guide** - Complete API reference with examples
3. **SpiceError Types** - All 11 error types explained
4. **Best Practices** - Advanced patterns (Circuit Breaker, Retry, etc.)
5. **Migration Guide** - Step-by-step migration from v0.1.x

### Updated Documentation

- All agent examples now use SpiceResult
- Error handling examples throughout
- Best practices for each feature

## 🔧 Migration Required

**All existing code using Spice v0.1.x needs to be updated.**

See the [Migration Guide](./MIGRATION_GUIDE_v0.2.0.md) for detailed instructions.

### Quick Migration Example

**Before**:
```kotlin
val agent = claudeAgent(apiKey = "...")
val response = agent.processComm(comm)
println(response.content)
```

**After**:
```kotlin
val agent = claudeAgent(apiKey = "...")
val result = agent.processComm(comm)

result
    .onSuccess { response -> println(response.content) }
    .onFailure { error -> println("Error: ${error.message}") }

// Or use getOrElse
val response = result.getOrElse(Comm.error("Failed"))
```

## 🎨 Design Decisions

### Why Breaking Change?

1. **Type Safety**: Errors are now explicit in function signatures
2. **Better DX**: Composable error handling is more ergonomic
3. **Production Ready**: Proper error handling is essential for production systems
4. **Future Proof**: Foundation for advanced features (retry, circuit breaker, etc.)

### Why Not Deprecation?

A gradual deprecation period would mean:
- Maintaining two error handling systems
- Confusing for users ("which one should I use?")
- Delayed adoption of better patterns

We chose a clean break for simplicity and clarity.

### Backwards Compatibility

While this is a breaking change, the migration is straightforward:
- ✅ All existing agents work (just return type changed)
- ✅ All existing tools work (just return type changed)
- ✅ Comm, Tool schemas unchanged
- ✅ Agent registration unchanged
- ⚠️ Only `processComm()` and `execute()` signatures changed

## 📊 Impact Analysis

### What's Affected

- ✅ Agent implementations
- ✅ Tool implementations
- ✅ Code calling `agent.processComm()`
- ✅ Code calling `tool.execute()`

### What's NOT Affected

- ✅ Agent creation (factory functions unchanged)
- ✅ Agent configuration
- ✅ Tool registration
- ✅ DSL builders
- ✅ CommHub
- ✅ Swarm patterns

### Estimated Migration Time

| Codebase Size | Est. Time |
|--------------|-----------|
| Small (<1000 LOC) | 30 min - 1 hour |
| Medium (1000-5000 LOC) | 2-4 hours |
| Large (>5000 LOC) | 1 day |

## 🧪 Testing

All tests passing:
- ✅ 18 SpiceResult tests
- ✅ 10 CommPropertyTest (property-based)
- ✅ 11 ToolPropertyTest (property-based)
- ✅ 14 GPTAgentIntegrationTest
- ✅ 16 ClaudeAgentIntegrationTest
- ✅ All existing unit tests updated

## 🚀 Upgrade Path

### 1. Update Dependencies

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.no-ai-labs:spice-core:0.2.0")
}
```

### 2. Follow Migration Guide

See [MIGRATION_GUIDE_v0.2.0.md](./MIGRATION_GUIDE_v0.2.0.md)

### 3. Update Tests

All tests need to handle `SpiceResult` instead of direct values.

### 4. Run Test Suite

```bash
./gradlew test
```

## 🙏 Acknowledgments

This release focuses on production-readiness and developer experience improvements based on real-world usage feedback.

## 📞 Support

- **Documentation**: https://no-ai-labs.github.io/spice/
- **Issues**: https://github.com/no-ai-labs/spice/issues
- **Migration Help**: See Migration Guide or open an issue

## 🔮 What's Next (v0.3.0)

- Configuration validation
- Runtime agent configuration checks
- Enhanced observability
- Performance optimizations
- AI-Powered Swarm Coordinator completion

---

**Full Changelog**: v0.1.2...v0.2.0
