# Migration Guide: Spice 0.5.x → 0.6.0

## Overview

Spice 0.6.0 introduces **Option B context unification** - a major refactoring that simplifies context management by consolidating `AgentContext` and `NodeContext.metadata` into a single `ExecutionContext`.

### Key Benefits
- ✅ Single source of truth for execution context
- ✅ Compile-time safety with factory methods
- ✅ Immutable state (PersistentMap) prevents mutation bugs
- ✅ Metadata size policies (warn/fail/ignore)
- ✅ Better observability with metadata delta tracking

---

## Breaking Changes

### 1. NodeContext Structure

**Before (0.5.x):**
```kotlin
data class NodeContext(
    val graphId: String,
    val state: MutableMap<String, Any?>,
    val metadata: MutableMap<String, Any> = mutableMapOf(),
    val agentContext: AgentContext? = null
)
```

**After (0.6.0):**
```kotlin
data class NodeContext(
    val graphId: String,
    val state: PersistentMap<String, Any?>,  // Immutable!
    val context: ExecutionContext             // Unified!
)
```

**Migration:**
```kotlin
// OLD
val tenantId = ctx.agentContext?.tenantId
val userId = ctx.metadata["userId"]
ctx.state["key"] = "value"

// NEW
val tenantId = ctx.context.tenantId
val userId = ctx.context.userId
val newCtx = ctx.withState("key", "value")  // Returns new NodeContext
```

---

### 2. NodeResult Factory Pattern

**Before (0.5.x):**
```kotlin
NodeResult(
    data = result,
    metadata = ctx.metadata + mapOf("key" to "value")
)
```

**After (0.6.0):**
```kotlin
// Constructor is now private - use factories!
NodeResult.fromContext(
    ctx = ctx,
    data = result,
    additional = mapOf("key" to "value")
)

// Or explicit metadata
NodeResult.create(
    data = result,
    metadata = mapOf("key" to "value")
)
```

---

### 3. Comm.context Type

**Before (0.5.x):**
```kotlin
data class Comm(
    // ...
    val context: AgentContext? = null
)
```

**After (0.6.0):**
```kotlin
data class Comm(
    // ...
    val context: ExecutionContext? = null
)

// Backward compatibility bridge
fun Comm.withContext(context: AgentContext): Comm {
    return copy(context = context.toExecutionContext())
}
```

---

### 4. RunContext Unification

**Before (0.5.x):**
```kotlin
data class RunContext(
    val graphId: String,
    val runId: String,
    val agentContext: AgentContext?,
    val metadata: MutableMap<String, Any> = mutableMapOf()
)
```

**After (0.6.0):**
```kotlin
data class RunContext(
    val graphId: String,
    val runId: String,
    val context: ExecutionContext
)
```

**Migration (Middleware):**
```kotlin
// OLD
override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
    ctx.metadata["key"] = "value"
    val tenant = ctx.agentContext?.tenantId
    next()
}

// NEW
override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
    // Context is immutable - read only
    val tenant = ctx.context.tenantId
    next()
}
```

---

### 5. Immutable State Pattern

**Before (0.5.x):**
```kotlin
override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
    ctx.state["result"] = computeValue()  // Direct mutation
    ctx.state["count"] = (ctx.state["count"] as? Int ?: 0) + 1
    return SpiceResult.success(NodeResult(data = "done"))
}
```

**After (0.6.0):**
```kotlin
override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
    // State is read-only - return updates via metadata
    val value = computeValue()
    val count = (ctx.state["count"] as? Int ?: 0) + 1
    
    return SpiceResult.success(
        NodeResult.fromContext(
            ctx,
            data = "done",
            additional = mapOf(
                "result" to value,
                "count" to count
            )
        )
    )
    // GraphRunner will propagate metadata to state automatically
}
```

---

## New Features

### 1. ExecutionContext

Unified context for coroutine-level and graph-level metadata:

```kotlin
val context = ExecutionContext.of(mapOf(
    "tenantId" to "tenant-123",
    "userId" to "user-456",
    "customKey" to customValue
))

// Type-safe accessors
val tenantId: String? = context.tenantId
val userId: String? = context.userId
val custom: Any? = context.get("customKey")

// Immutable updates
val enriched = context
    .plus("sessionId", "sess-789")
    .plusAll(mapOf("key1" to "val1", "key2" to "val2"))
```

### 2. Metadata Size Policies

```kotlin
// Default: warn at 5KB, no hard limit
NodeResult.METADATA_WARN_THRESHOLD  // 5000

// Configure hard limit (optional)
NodeResult.HARD_LIMIT = 10_000

// Configure overflow behavior
NodeResult.onOverflow = NodeResult.OverflowPolicy.WARN  // Default
NodeResult.onOverflow = NodeResult.OverflowPolicy.FAIL
NodeResult.onOverflow = NodeResult.OverflowPolicy.IGNORE
```

### 3. Metadata Delta Tracking

`NodeReport` now includes metadata changes:

```kotlin
data class NodeReport(
    val nodeId: String,
    // ...
    val metadata: Map<String, Any>? = null,
    val metadataChanges: Map<String, Any>? = null  // NEW!
)

// See what each node added/modified
report.nodeReports.forEach { nodeReport ->
    println("Node ${nodeReport.nodeId}:")
    nodeReport.metadataChanges?.forEach { (key, value) ->
        println("  Changed: $key = $value")
    }
}
```

### 4. Metadata Validation

```kotlin
// Custom validator
class TenantValidator : MetadataValidator {
    override fun validate(metadata: Map<String, Any>): SpiceResult<Unit> {
        val tenantId = metadata["tenantId"] as? String
        return if (tenantId != null) {
            SpiceResult.success(Unit)
        } else {
            SpiceResult.failure(SpiceError.validationError("tenantId required"))
        }
    }
}

val runner = DefaultGraphRunner(metadataValidator = TenantValidator())
```

### 5. Immutable State Builders

```kotlin
// NodeContext builders
val updatedCtx = ctx
    .withState("key", "value")
    .withState(mapOf("k1" to "v1", "k2" to "v2"))
    .withContext(newExecutionContext)

// Create NodeContext
val nodeContext = NodeContext.create(
    graphId = "my-graph",
    state = mapOf("input" to "data"),
    context = ExecutionContext.of(mapOf("tenantId" to "tenant-123"))
)
```

---

## Migration Checklist

### Code Changes

- [ ] Replace `ctx.metadata["key"]` with `ctx.context.get("key")`
- [ ] Replace `ctx.agentContext?.tenantId` with `ctx.context.tenantId`
- [ ] Replace `NodeResult(...)` with `NodeResult.fromContext(ctx, ...)`
- [ ] Replace `ctx.state[key] = value` with returning metadata
- [ ] Replace `RunContext.agentContext` with `RunContext.context`
- [ ] Replace `Comm(context = AgentContext.of(...))` with `ExecutionContext.of(...)`

### Testing

- [ ] Update tests using `NodeContext(...)` to `NodeContext.create(...)`
- [ ] Update tests mutating `ctx.state` to use metadata pattern
- [ ] Update middleware tests accessing `ctx.agentContext`/`ctx.metadata`

### Documentation

- [ ] Update custom node implementations
- [ ] Update middleware examples
- [ ] Update graph usage examples

---

## Backward Compatibility

### AgentContext Bridge

For gradual migration, `AgentContext` can still be used with automatic conversion:

```kotlin
// Old code still works via bridge
val agentCtx = AgentContext.of("tenantId" to "test")
val comm = Comm(content = "test", from = "user", context = agentCtx)
// Automatically converts to ExecutionContext

// Convert back if needed
val execCtx: ExecutionContext = agentCtx.toExecutionContext()
val agentCtx2: AgentContext = execCtx.toAgentContext()
```

---

## Common Issues

### Issue 1: "Unresolved reference: metadata"

**Error:**
```
ctx.metadata["key"]  // ❌ metadata field removed
```

**Fix:**
```kotlin
ctx.context.get("key")  // ✅ Use ExecutionContext
```

### Issue 2: "No 'set' operator method"

**Error:**
```
ctx.state["key"] = value  // ❌ State is immutable
```

**Fix:**
```kotlin
// Return state updates via metadata
NodeResult.fromContext(ctx, data = result, additional = mapOf("key" to value))
```

### Issue 3: "Private constructor"

**Error:**
```
NodeResult(data = result, metadata = emptyMap())  // ❌ Constructor is private
```

**Fix:**
```kotlin
NodeResult.fromContext(ctx, data = result)  // ✅ Use factory
```

---

## Questions?

- [0.6.0 Release Notes](RELEASE_NOTES_v0.6.0.md)
- [ExecutionContext API](docs/docs/api/execution-context.md)
- [Graph Nodes Guide](docs/docs/orchestration/graph-nodes.md)
- [CLAUDE.md](CLAUDE.md) - Internal coding guide

---

## Timeline

- **0.5.4**: Last release with old API
- **0.6.0**: Breaking changes (this guide)
- **0.7.0+**: Old API completely removed

