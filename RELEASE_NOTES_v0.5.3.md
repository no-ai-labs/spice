# Spice Framework 0.5.3 Release Notes

## Critical Bug Fixes: NodeContext.metadata Propagation

This release addresses critical bugs in `NodeContext.metadata` propagation throughout the graph execution lifecycle. These fixes ensure that metadata provided at graph invocation is properly initialized, propagated, and accessible across all nodes and checkpoints.

---

## 🔥 What Was Broken

### Problem 1: GraphRunner didn't initialize NodeContext.metadata from input
**Impact**: When KAI-Core or other orchestrators passed metadata via `input["metadata"]`, GraphRunner completely ignored it. The first node in a graph would receive empty `NodeContext.metadata` even when metadata was provided.

**Affected Locations**:
- Normal graph execution (`run`, `runWithCheckpoint`)
- Checkpoint resume operations (`resume`, `resumeWithHumanResponse`)
- All 7 NodeContext instantiations in GraphRunner

### Problem 2: GraphRunner didn't propagate NodeResult.metadata to next node
**Impact**: Even if a node populated `NodeResult.metadata`, that metadata was lost. The next node's `NodeContext.metadata` would not receive the previous node's metadata, breaking metadata flow through multi-node graphs.

**Affected Locations**:
- Normal node execution loop (after each node runs)
- Checkpoint-based execution loop

### Problem 3: AgentNode overwrote input metadata with "none" defaults
**Impact**: AgentNode populated `NodeResult.metadata` with hardcoded defaults (`"tenantId" to "none"`, `"userId" to "none"`) that overwrote valid metadata from `NodeContext.metadata`. This meant that even when Problems 1 & 2 were fixed, the actual metadata values were replaced with "none".

---

## ✅ What Was Fixed

### Fix 1: GraphRunner now initializes NodeContext.metadata from input

**7 locations in GraphRunner.kt updated:**

1. **`run()` method** (line 106-110)
2. **`runWithCheckpoint()` method** (line 358-363)
3. **`resume()` from checkpoint - first occurrence** (line 394-399)
4. **`resume()` from checkpoint - second occurrence** (line 394-399, different path)
5. **`resumeWithHumanResponse()` method** (line 709-713)
6. **Checkpoint restoration** (multiple locations)

All `NodeContext` creations now include:
```kotlin
metadata = (input["metadata"] as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
```

Or when resuming from checkpoint:
```kotlin
metadata = checkpoint.metadata.toMutableMap()
```

### Fix 2: GraphRunner now propagates NodeResult.metadata to NodeContext

**2 execution loops updated:**

After each node executes, NodeResult.metadata is now merged into NodeContext.metadata:

```kotlin
// 🔥 Propagate NodeResult.metadata to NodeContext.metadata for next node
result.metadata.forEach { (key, value) ->
    nodeContext.metadata[key] = value
}
```

This ensures metadata flows forward through the graph execution.

### Fix 3: AgentNode now preserves input metadata

**AgentNode.kt metadata construction updated:**

Changed from map concatenation that overwrites:
```kotlin
// ❌ OLD: Right side overwrites left side
metadata = ctx.metadata + mapOf(
    "tenantId" to (ctx.agentContext?.tenantId ?: "none"),
    "userId" to (ctx.agentContext?.userId ?: "none")
)
```

To explicit merge that preserves:
```kotlin
// ✅ NEW: Preserves ctx.metadata, overlays agent data
metadata = buildMap {
    putAll(ctx.metadata)  // 🔥 Preserve input metadata
    put("agentId", agent.id)
    put("agentName", agent.name ?: "unknown")
    // Only override if agentContext provides values
    ctx.agentContext?.tenantId?.let { put("tenantId", it) }
    ctx.agentContext?.userId?.let { put("userId", it) }
}
```

**Priority order now:**
1. Start with `ctx.metadata` (from graph input or previous node)
2. Add agent-specific metadata (`agentId`, `agentName`)
3. Override with `agentContext` values if present

---

## 📊 How to Use

### Example: Passing metadata at graph invocation

```kotlin
val graph = graph("user-workflow") {
    agent("processor", myAgent)
    output("result") { ctx ->
        // Metadata is now accessible!
        "User: ${ctx.metadata["userId"]}, Tenant: ${ctx.metadata["tenantId"]}"
    }
}

val initialState = mapOf(
    "input" to "Process this task",
    "metadata" to mapOf(
        "tenantId" to "tenant-123",
        "userId" to "user-456",
        "sessionId" to "session-789"
    )
)

val report = runner.run(graph, initialState).getOrThrow()
// Output: "User: user-456, Tenant: tenant-123"
```

### Example: Metadata propagation across nodes

```kotlin
val graph = graph("metadata-flow") {
    agent("first", agent1)    // Receives initial metadata
    agent("second", agent2)   // Receives metadata from first + its own
    output("result") { ctx ->
        // All accumulated metadata available here
        ctx.metadata.toString()
    }
}

val report = runner.run(graph, mapOf(
    "input" to "Start",
    "metadata" to mapOf("source" to "api")
)).getOrThrow()
```

### Example: Metadata in checkpoints

```kotlin
// Metadata is saved in checkpoints
val report = runner.runWithCheckpoint(
    graph = myGraph,
    input = mapOf(
        "input" to "Task",
        "metadata" to mapOf("requestId" to "req-123")
    ),
    checkpointPolicy = CheckpointPolicy.SaveEveryNNodes(1)
)

// Metadata is restored when resuming
val resumedReport = runner.resume(
    checkpointId = checkpoint.id,
    humanResponse = HumanResponse.approve()
)
// resumedReport still has "requestId" in metadata
```

---

## 🔬 Testing

Added comprehensive test to verify end-to-end metadata flow:

```kotlin
@Test
fun `test graph input metadata accessible via output node`() = runTest {
    val graph = graph("metadata-output-test") {
        agent("agent", agent)
        output("result") { ctx ->
            "tenantId:${ctx.metadata["tenantId"]},userId:${ctx.metadata["userId"]}"
        }
    }

    val initialState = mapOf(
        "input" to "Test",
        "metadata" to mapOf(
            "tenantId" to "tenant-123",
            "userId" to "user-456"
        )
    )

    val report = runner.run(graph, initialState).getOrThrow()
    assertEquals("tenantId:tenant-123,userId:user-456", report.result)
}
```

**Result**: ✅ All 15 GraphIntegrationTest tests pass

---

## 🎯 Root Cause Analysis

These bugs occurred because:

1. **NodeContext.metadata was an afterthought**: The field existed in the data class but was never populated or used in GraphRunner.
2. **No initialization from input**: Unlike `state` and `agentContext`, which were initialized from input, `metadata` was left with its default empty map.
3. **No propagation between nodes**: Unlike `state` which is explicitly propagated via `_previous`, metadata had no propagation mechanism.
4. **AgentNode overwrote instead of merged**: The metadata construction used Kotlin's `+` operator which overwrites left-hand values with right-hand values for matching keys.

---

## 🛡️ Impact

**Severity**: 🔴 **Critical** - Metadata propagation is a core feature

**Who's Affected**:
- KAI-Core users relying on tenant/user/session context in graphs
- Any application passing metadata to graphs for multi-tenancy or request tracking
- Applications using checkpoints with context metadata

**Upgrade Priority**: 🔥 **Immediate** - This fixes fundamental context propagation

---

## 📦 Files Changed

- `spice-core/src/main/kotlin/io/github/noailabs/spice/graph/runner/GraphRunner.kt` - 7 locations fixed
- `spice-core/src/main/kotlin/io/github/noailabs/spice/graph/nodes/AgentNode.kt` - Metadata merge logic fixed
- `spice-core/src/test/kotlin/io/github/noailabs/spice/graph/GraphIntegrationTest.kt` - Test added

---

## 🔄 Migration Guide

**No breaking changes!** This is a pure bug fix.

If you were working around these bugs (e.g., passing metadata via `state` instead of `metadata`), you can now use the proper `metadata` field:

```kotlin
// Before (workaround)
val input = mapOf(
    "input" to "Task",
    "tenantId" to "tenant-123"  // ❌ Polluting state with metadata
)

// After (correct)
val input = mapOf(
    "input" to "Task",
    "metadata" to mapOf(
        "tenantId" to "tenant-123"  // ✅ Proper metadata field
    )
)
```

---

## 🙏 Credits

Bug discovered through integration testing with KAI-Core, where context propagation issues were reported consistently.

---

**Version**: 0.5.3
**Release Date**: 2025-10-28
**Previous Version**: 0.5.2
