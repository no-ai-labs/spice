# 📚 Documentation TODO

List of documentation improvements needed for Spice Framework.

## 🔴 High Priority

### 1. Swarm Strategies Deep Dive ⭐⭐⭐

**Location:** `docs/orchestration/swarm-strategies.md` (new file)

**What's missing:**
- Detailed explanation of how each strategy works internally
- Code flow diagrams for each strategy
- Performance characteristics and trade-offs
- Decision tree: when to use which strategy
- Real-world use cases with complete examples
- Comparison table of all strategies

**Content needed:**

```markdown
# Swarm Strategies Deep Dive

## PARALLEL Strategy
### How It Works
- Spawns coroutines for all agents simultaneously
- Uses `launch` with proper error handling
- Aggregates results after all complete
- Handles timeouts and failures

### When to Use
- Independent analyses (e.g., sentiment + keyword + summary)
- Data from multiple sources
- Tasks with no dependencies

### Performance
- Best for: 3-10 agents
- Latency: ~max(agent_times)
- Memory: O(n) where n = agent count

### Example: Multi-Source Research
[Complete code example with 5 agents]

## SEQUENTIAL Strategy
[Similar detailed breakdown]

## CONSENSUS Strategy
[Similar detailed breakdown]

## COMPETITION Strategy
[Similar detailed breakdown]

## HIERARCHICAL Strategy
[Similar detailed breakdown]

## Decision Matrix
[Table showing task type → best strategy]
```

---

### 2. catchingSuspend & Inline Functions Guide ⭐⭐

**Location:** `docs/error-handling/inline-functions.md` (new file)

**What's missing:**
- Explanation of inline functions and non-local returns
- Common pitfalls with `catchingSuspend`
- Correct patterns and antipatterns
- When to use `catching` vs `catchingSuspend`

**Content needed:**

```markdown
# Inline Functions & catchingSuspend

## The Non-Local Return Problem

### What's Wrong Here?

```kotlin
// ❌ COMPILE ERROR!
fun processData(): SpiceResult<Data> {
    return SpiceResult.catchingSuspend {
        if (condition) {
            return SpiceResult.failure(error) // ❌ Can't return from outer function!
        }
        fetchData()
    }
}
```

### Why This Happens
- `catchingSuspend` is an `inline` function
- Inline functions allow non-local returns
- But this breaks the try-catch wrapping

### Solutions

**Solution 1: Guard Clause**
```kotlin
fun processData(): SpiceResult<Data> {
    if (condition) {
        return SpiceResult.failure(error)
    }
    return SpiceResult.catchingSuspend {
        fetchData()
    }
}
```

**Solution 2: Return Expression**
```kotlin
fun processData(): SpiceResult<Data> {
    return if (condition) {
        SpiceResult.failure(error)
    } else {
        SpiceResult.catchingSuspend { fetchData() }
    }
}
```

**Solution 3: flatMap**
```kotlin
fun processData(): SpiceResult<Data> {
    return validateInput()
        .flatMap { input ->
            SpiceResult.catchingSuspend {
                fetchData(input)
            }
        }
}
```
```

---

### 3. Error Context Enrichment Patterns ⭐⭐

**Location:** `docs/error-handling/context-patterns.md` (new file)

**What's missing:**
- Deep dive on `withContext` usage
- Multi-layer context patterns
- Integration with observability
- Request tracing examples

**Content needed:**

```markdown
# Error Context Enrichment

## Why Context Matters

Context helps:
- Debug distributed systems
- Track requests across services
- Understand error origins
- Correlate logs and traces

## Context Chaining

### Single Layer
```kotlin
SpiceError.networkError("API failed")
    .withContext(
        "endpoint" to "/api/users",
        "method" to "GET"
    )
```

### Multi-Layer (Repository → Service → Controller)
```kotlin
// Layer 1: Repository
fun fetchUser(id: String): SpiceResult<User> {
    return database.query(id)
        .mapError { error ->
            error.withContext(
                "layer" to "repository",
                "operation" to "fetchUser",
                "user_id" to id
            )
        }
}

// Layer 2: Service
fun getUser(id: String): SpiceResult<User> {
    return repository.fetchUser(id)
        .mapError { error ->
            error.withContext(
                "layer" to "service",
                "service" to "UserService"
            )
        }
}

// Layer 3: Controller
fun handleGetUser(id: String): Response {
    return userService.getUser(id)
        .mapError { error ->
            error.withContext(
                "layer" to "controller",
                "request_id" to requestId,
                "user_agent" to userAgent
            )
        }
        .fold(
            onSuccess = { Response.ok(it) },
            onFailure = { error ->
                logger.error { "Full context: ${error.context}" }
                Response.error(error)
            }
        )
}
```

## Integration with OpenTelemetry
[Show how context flows to traces]
```

---

## 🟡 Medium Priority

### 4. Agent Lifecycle Guide

**Location:** `docs/core-concepts/agent-lifecycle.md` (new file)

**What's missing:**
- Detailed lifecycle stages
- State management
- Resource cleanup
- Coroutine scope management

---

### 5. Observability Patterns

**Location:** `docs/observability/patterns.md` (new file)

**What's missing:**
- Custom metrics creation
- Span attribute patterns
- Cost tracking implementation
- Performance profiling techniques

---

### 6. Vector Store Deep Dive

**Location:** `docs/tools-extensions/vector-stores-advanced.md` (new file)

**What's missing:**
- Chunking strategies
- Embedding optimization
- Hybrid search patterns
- Production deployment tips

---

### 7. Tool Creation Patterns

**Location:** `docs/tools-extensions/tool-patterns.md` (new file)

**What's missing:**
- Advanced tool patterns
- Stateful tools
- Tool composition
- Error handling in tools

---

## 🟢 Low Priority

### 8. Testing Guide

**Location:** `docs/testing/overview.md` (new file)

**What's missing:**
- Unit testing agents
- Integration testing swarms
- Mocking LLM responses
- Property-based testing

---

### 9. Performance Tuning

**Location:** `docs/advanced/performance.md` (new file)

**What's missing:**
- Caching strategies
- Batching patterns
- Connection pooling
- Memory optimization

---

### 10. Migration Guides

**Location:** `docs/migration/` (new directory)

**What's missing:**
- From v0.1.x to v0.2.x
- From legacy Message to Comm
- From other frameworks (LangChain, etc.)

---

## ✅ Recently Completed

- [x] **SpiceResult Operator Guide** - Added comprehensive "Choosing the Right Operator" section with decision tree and patterns
- [x] **Comm API Complete Reference** - Expanded from 18 lines to 750+ lines with full examples

---

## 📝 Quick Reference: Documentation Standards

When writing documentation:

1. **Start with Overview** - What is it? Why use it?
2. **Show Code First** - Examples before explanations
3. **Include Anti-Patterns** - Show ❌ bad and ✅ good
4. **Real-World Examples** - Not just toy examples
5. **Decision Trees** - Help users choose between options
6. **Link Related Docs** - Connect concepts together
7. **Keep Updated** - Update when code changes

---

## 🤝 Contributing

To claim a TODO item:
1. Create an issue linking to this TODO
2. PR with the new documentation
3. Update this file marking it complete
4. Add to "Recently Completed" section

---

**Last Updated:** 2024-10-22
**Maintainer:** @spice-team
