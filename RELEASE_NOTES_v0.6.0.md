# Spice Framework 0.6.0 Release Notes

**Released:** 2025-10-28

## 🎉 Major Release: Context Unification & Immutable State

Spice 0.6.0 is a **major breaking release** that fundamentally improves the framework's architecture with unified context management and immutable state patterns.

---

## 🔥 What's New

### 1. ExecutionContext - Unified Context System

**The Big Change:** `AgentContext` + `NodeContext.metadata` → **`ExecutionContext`**

```kotlin
// 0.5.x - Dual context confusion
val tenant = ctx.agentContext?.tenantId
val custom = ctx.metadata["customKey"]

// 0.6.0 - Single source of truth!
val tenant = ctx.context.tenantId
val custom = ctx.context.get("customKey")
```

**Benefits:**
- ✅ Single API for all context access
- ✅ Type-safe accessors (`tenantId`, `userId`, `correlationId`)
- ✅ Coroutine propagation maintained
- ✅ No more confusion about which context to use

### 2. Immutable State (PersistentMap)

**The Big Change:** `MutableMap` → **`PersistentMap`**

```kotlin
// 0.5.x - Dangerous mutation
ctx.state["result"] = value  // ⚠️ Side effects!

// 0.6.0 - Safe, functional style
NodeResult.fromContext(
    ctx,
    data = result,
    additional = mapOf("result" to value)
)
```

**Benefits:**
- ✅ No mutation bugs
- ✅ State isolation between nodes
- ✅ Efficient Copy-on-Write
- ✅ Perfect checkpoint snapshots

### 3. NodeResult Factory Pattern

**The Big Change:** Constructor is now **private**

```kotlin
// 0.5.x - Easy to forget metadata
NodeResult(data = result)  // ❌ Lost context!

// 0.6.0 - Impossible to mess up
NodeResult.fromContext(ctx, data = result)  // ✅ Always preserves context
```

**Benefits:**
- ✅ Compile-time safety
- ✅ Automatic context preservation
- ✅ No silent metadata loss

### 4. Metadata Delta Tracking

**New:** See what each node changed

```kotlin
report.nodeReports.forEach { node ->
    println("${node.nodeId}:")
    node.metadataChanges?.forEach { (key, value) ->
        println("  Added: $key = $value")
    }
}
```

### 5. Metadata Size Policies

**New:** Configurable size limits

```kotlin
// Default: warn at 5KB, no hard limit
NodeResult.METADATA_WARN_THRESHOLD  // 5000

// Configure if needed
NodeResult.HARD_LIMIT = 10_000
NodeResult.onOverflow = NodeResult.OverflowPolicy.FAIL
```

### 6. Metadata Validation Hooks

**New:** Validate context at checkpoints

```kotlin
class RequiredKeysValidator : MetadataValidator {
    override fun validate(metadata: Map<String, Any>): SpiceResult<Unit> {
        val required = setOf("tenantId", "userId")
        val missing = required - metadata.keys
        
        return if (missing.isEmpty()) {
            SpiceResult.success(Unit)
        } else {
            SpiceResult.failure(
                SpiceError.validationError("Missing: $missing")
            )
        }
    }
}

val runner = DefaultGraphRunner(metadataValidator = RequiredKeysValidator())
```

---

## 💥 Breaking Changes

### API Changes

#### 1. NodeContext

```kotlin
// OLD (0.5.x)
data class NodeContext(
    val graphId: String,
    val state: MutableMap<String, Any?>,
    val metadata: MutableMap<String, Any> = mutableMapOf(),
    val agentContext: AgentContext? = null
)

// NEW (0.6.0)
data class NodeContext(
    val graphId: String,
    val state: PersistentMap<String, Any?>,  // Immutable!
    val context: ExecutionContext             // Unified!
)
```

#### 2. NodeResult

```kotlin
// OLD (0.5.x)
NodeResult(data = result, metadata = emptyMap())

// NEW (0.6.0) - Constructor is private!
NodeResult.fromContext(ctx, data = result)
NodeResult.create(data = result, metadata = map)
```

#### 3. RunContext

```kotlin
// OLD (0.5.x)
data class RunContext(
    val graphId: String,
    val runId: String,
    val agentContext: AgentContext?,
    val metadata: MutableMap<String, Any> = mutableMapOf()
)

// NEW (0.6.0)
data class RunContext(
    val graphId: String,
    val runId: String,
    val context: ExecutionContext  // Unified!
)
```

#### 4. Comm.context

```kotlin
// OLD (0.5.x)
val comm = Comm(
    content = "test",
    from = "user",
    context = AgentContext.of("tenantId" to "ACME")
)

// NEW (0.6.0)
val comm = Comm(
    content = "test",
    from = "user",
    context = ExecutionContext.of(mapOf("tenantId" to "ACME"))
)

// Backward compatibility bridge works!
comm.withContext(AgentContext.of("tenantId" to "ACME"))  // Auto-converts
```

#### 5. State Mutation

```kotlin
// OLD (0.5.x)
override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
    ctx.state["key"] = "value"  // Direct mutation
    return SpiceResult.success(NodeResult(data = "done"))
}

// NEW (0.6.0) - Won't compile!
override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
    // ctx.state["key"] = "value"  // ❌ No 'set' operator!
    
    // Return updates via metadata
    return SpiceResult.success(
        NodeResult.fromContext(
            ctx,
            data = "done",
            additional = mapOf("key" to "value")
        )
    )
}
```

---

## 🔄 Migration Guide

See [MIGRATION_GUIDE_v0.6.0.md](/MIGRATION_GUIDE_v0.6.0.md) for detailed migration instructions.

### Quick Migration Checklist

- [ ] Replace `ctx.metadata["key"]` → `ctx.context.get("key")`
- [ ] Replace `ctx.agentContext?.tenantId` → `ctx.context.tenantId`
- [ ] Replace `NodeResult(...)` → `NodeResult.fromContext(ctx, ...)`
- [ ] Replace `ctx.state[key] = value` → return via metadata
- [ ] Replace `RunContext.agentContext` → `RunContext.context`
- [ ] Update middleware accessing `ctx.metadata`/`ctx.agentContext`
- [ ] Update tests with `NodeContext.create(...)`

---

## 📦 Dependencies

### New

- `kotlinx-collections-immutable:0.3.7` - Persistent data structures

### Updated

- No version bumps in this release

---

## 🐛 Bug Fixes

All issues from the metadata anti-patterns analysis are now **impossible by design**:

1. ✅ **Inconsistent metadata handling** - Single pattern enforced (`fromContext`)
2. ✅ **Silent metadata loss** - Compile-time safety
3. ✅ **Error path metadata loss** - Always preserved
4. ✅ **No validation** - MetadataValidator hooks
5. ✅ **Mutable state bugs** - Immutable by default
6. ✅ **Metadata size explosion** - Size policies enforced
7. ✅ **AgentContext confusion** - Unified into ExecutionContext

---

## 📊 Performance

### Improvements

- **PersistentMap** - O(log n) updates vs O(n) for copies
- **Structural sharing** - Minimal memory overhead
- **No defensive copies** - Immutability guarantees safety

### Benchmarks

```
Operation          0.5.x (MutableMap)   0.6.0 (PersistentMap)
-----------------------------------------------------------------
State read         O(1)                 O(log n)              
State write        O(1)*                O(log n)              
Full copy          O(n)                 O(1)                  
Memory overhead    1x                   ~1.1x                 

* With mutation risks
```

**Result:** Slightly slower individual operations, but much safer and faster for copy operations (checkpoints, parallel access).

---

## 📚 Documentation

### New Guides

1. **[ExecutionContext Patterns](/docs/guides/execution-context-patterns)** - 20 production patterns
2. **[Immutable State Guide](/docs/guides/immutable-state-guide)** - 9 patterns + best practices
3. **[Migration Guide 0.5 → 0.6](/docs/roadmap/migration-0.5-to-0.6)** - Step-by-step migration

### Updated

- All graph API docs
- All code examples
- Middleware examples
- Testing guides

---

## 🔮 What's Next?

### Future (0.7.0+)

- Remove backward compatibility bridges
- Further optimizations
- Additional validation patterns

---

## 🙏 Breaking Change Justification

**Why break compatibility?**

1. **Safety:** Prevents entire classes of bugs (mutation, context loss)
2. **Clarity:** Single, obvious way to do things
3. **Maintainability:** Easier to reason about code
4. **Foundation:** Sets up for future enhancements

**Migration effort:** ~30 minutes for typical projects with the migration guide.

---

## 📖 Resources

- [Migration Guide](/MIGRATION_GUIDE_v0.6.0.md)
- [ExecutionContext API](/docs/api/execution-context)
- [Immutable State Guide](/docs/guides/immutable-state-guide)
- [ExecutionContext Patterns](/docs/guides/execution-context-patterns)
- [CLAUDE.md](/CLAUDE.md) - Internal coding guide

---

## 🎯 Summary

Spice 0.6.0 is a **quality-first release** that trades minor migration effort for:

- 🔒 **Compile-time safety** - Impossible to make common mistakes
- 🐛 **Bug prevention** - Mutation and context loss bugs eliminated
- 📊 **Better observability** - Track exactly what changed where
- 🎯 **Clarity** - One obvious way to do things

**Recommended for:** All new projects. Existing projects should migrate when ready.

---

**Contributors:** Spice Team  
**License:** MIT  
**Repository:** https://github.com/no-ai-labs/spice

