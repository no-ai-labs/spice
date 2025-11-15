# Immutable State Guide

**Added in:** 0.6.0

Complete guide to working with immutable state in Spice graphs.

## Overview

Spice 0.6.0 introduces **immutable state** using `PersistentMap` to prevent mutation bugs and improve debugging. This guide covers patterns and best practices.

## Why Immutable State?

### Problems with Mutable State (0.5.x)

```kotlin
// 0.5.x - Multiple nodes sharing mutable state
class NodeA : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        ctx.state["data"] = "A"  // ⚠️ Direct mutation
        return SpiceResult.success(NodeResult(data = "done"))
    }
}

class NodeB : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        ctx.state["data"] = "B"  // ⚠️ Overwrites NodeA's data!
        return SpiceResult.success(NodeResult(data = "done"))
    }
}
```

**Issues:**
- 🐛 Race conditions in parallel execution
- 🐛 Accidental data corruption
- 🐛 Hard to debug who changed what
- 🐛 No clear ownership of state

### Benefits of Immutable State (0.6.0)

```kotlin
// 0.6.0 - Explicit state updates
class NodeA : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Return state update via metadata
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = "done",
                additional = mapOf("data" to "A")
            )
        )
    }
}

class NodeB : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val previousData = ctx.state["data"]  // Read NodeA's data
        
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = "done",
                additional = mapOf("data" to "B")
            )
        )
    }
}
```

**Benefits:**
- ✅ No accidental mutations
- ✅ Clear data flow (metadata → state)
- ✅ Easy to track what changed (metadataChanges)
- ✅ Functional programming style

## Basic Patterns

### Pattern 1: Reading State

```kotlin
class ReaderNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Read from immutable state
        val input = ctx.state["input"] as? String ?: ""
        val count = ctx.state["count"] as? Int ?: 0
        val config = ctx.state["config"] as? Map<String, Any> ?: emptyMap()
        
        val result = processData(input, count, config)
        
        return SpiceResult.success(
            NodeResult.fromContext(ctx, data = result)
        )
    }
}
```

**Key Points:**
- ✅ State is read-only in nodes
- ✅ Use safe casts (`as?`) with defaults
- ✅ No compilation errors from reading

### Pattern 2: Updating State

```kotlin
class WriterNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val currentCount = (ctx.state["count"] as? Int) ?: 0
        val newCount = currentCount + 1
        
        // Return updates via metadata
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = "count updated",
                additional = mapOf(
                    "count" to newCount,
                    "lastUpdatedBy" to id,
                    "lastUpdatedAt" to Instant.now().toString()
                )
            )
        )
        // GraphRunner will:
        // 1. Merge metadata into ExecutionContext
        // 2. Put "count" in state for next node
    }
}
```

**How it works:**
1. Node returns metadata with state updates
2. GraphRunner extracts keys
3. Propagates to both ExecutionContext and state
4. Next node sees updated state

### Pattern 3: Bulk State Updates

```kotlin
class BulkUpdaterNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val results = processMultipleItems()
        
        // Build state updates
        val stateUpdates = results.mapIndexed { index, result ->
            "result_$index" to result
        }.toMap()
        
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = "processed ${results.size} items",
                additional = stateUpdates + mapOf(
                    "totalCount" to results.size,
                    "processedAt" to Instant.now()
                )
            )
        )
    }
}
```

## Advanced Patterns

### Pattern 4: Conditional State Updates

```kotlin
class ConditionalNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val input = ctx.state["input"] as? String ?: ""
        val shouldCache = ctx.context.getAs<Boolean>("cacheEnabled") ?: false
        
        val result = processInput(input)
        
        // Conditional state updates
        val updates = buildMap {
            put("result", result)
            put("processedAt", Instant.now())
            
            if (shouldCache) {
                put("cachedResult", result)
                put("cacheExpiry", Instant.now().plusSeconds(3600))
            }
        }
        
        return SpiceResult.success(
            NodeResult.fromContext(ctx, data = result, additional = updates)
        )
    }
}
```

### Pattern 5: State Transformation Pipelines

```kotlin
val graph = graph("data-pipeline") {
    // Stage 1: Load
    agent("loader", loaderAgent)  // Loads data
    
    // Stage 2: Transform
    node("transformer") { ctx ->
        val rawData = ctx.state["loader"] as? String ?: ""
        val transformed = transformData(rawData)
        
        NodeResult.fromContext(
            ctx,
            data = transformed,
            additional = mapOf(
                "transformedData" to transformed,
                "transformedAt" to Instant.now()
            )
        )
    }
    
    // Stage 3: Validate
    node("validator") { ctx ->
        val data = ctx.state["transformedData"] as? String ?: ""
        val isValid = validate(data)
        
        NodeResult.fromContext(
            ctx,
            data = if (isValid) data else null,
            additional = mapOf(
                "validationResult" to isValid,
                "validatedAt" to Instant.now()
            )
        )
    }
    
    // Stage 4: Output
    output("result") { ctx -> ctx.state["validator"] }
}
```

### Pattern 6: State Snapshots for Checkpoints

```kotlin
// State is automatically snapshotted for checkpoints
val runner = DefaultGraphRunner()

val result = runner.runWithCheckpoint(
    graph,
    input = mapOf("data" to largeDataset),
    store = checkpointStore,
    config = CheckpointConfig(saveEveryNNodes = 5)
)

// If failure occurs, resume from last checkpoint
// State is perfectly preserved (no mutation bugs!)
val resumed = runner.resume(graph, checkpointId, checkpointStore)
```

**Benefits:**
- ✅ Checkpoint state is immutable snapshot
- ✅ No risk of state corruption
- ✅ Safe parallel checkpoint reads
- ✅ Deterministic replay

## Integration with GraphRunner

### How State Propagation Works

```kotlin
// GraphRunner internal flow (simplified):
while (currentNodeId != null) {
    val node = graph.nodes[currentNodeId]
    val result = node.run(nodeContext)  // Node returns metadata
    
    // 1. Extract state updates from metadata
    val stateUpdates = mutableMapOf<String, Any?>(
        currentNodeId to result.data,
        "_previous" to result.data
    )
    result.metadata["_previousComm"]?.let { 
        stateUpdates["_previousComm"] = it 
    }
    
    // 2. Update ExecutionContext
    val enrichedContext = nodeContext.context.plusAll(result.metadata)
    
    // 3. Create new NodeContext (immutable!)
    nodeContext = nodeContext
        .withState(stateUpdates)
        .withContext(enrichedContext)
    
    // 4. Next node receives updated context
    currentNodeId = findNextNode(result)
}
```

**Key Points:**
- GraphRunner manages state propagation
- Nodes just return what to add
- Copy-on-Write ensures efficiency
- No mutation anywhere!

## Testing Immutable State

### Pattern 7: Verify State Isolation

```kotlin
@Test
fun `nodes should not mutate shared state`() = runTest {
    var stateSnapshot1: PersistentMap<String, Any?>? = null
    var stateSnapshot2: PersistentMap<String, Any?>? = null
    
    val node1 = object : Node {
        override val id = "node1"
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            stateSnapshot1 = ctx.state  // Capture state
            return SpiceResult.success(
                NodeResult.fromContext(
                    ctx,
                    data = "done",
                    additional = mapOf("node1Data" to "value1")
                )
            )
        }
    }
    
    val node2 = object : Node {
        override val id = "node2"
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            stateSnapshot2 = ctx.state  // Capture state
            return SpiceResult.success(
                NodeResult.fromContext(ctx, data = "done")
            )
        }
    }
    
    val graph = Graph(
        id = "isolation-test",
        nodes = mapOf("node1" to node1, "node2" to node2),
        edges = listOf(Edge("node1", "node2")),
        entryPoint = "node1"
    )
    
    val runner = DefaultGraphRunner()
    runner.run(graph, mapOf("input" to "test"))
    
    // Verify state evolved (not mutated)
    assertNotEquals(stateSnapshot1, stateSnapshot2)
    assertNotNull(stateSnapshot2?.get("node1"))  // node1 result is in state
}
```

## Common Use Cases

### Use Case 1: Counter/Accumulator

```kotlin
class CounterNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val count = (ctx.state["count"] as? Int) ?: 0
        val items = (ctx.state["items"] as? List<*>) ?: emptyList<Any>()
        
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = count + 1,
                additional = mapOf(
                    "count" to (count + 1),
                    "items" to (items + "new-item")
                )
            )
        )
    }
}
```

### Use Case 2: State Machine

```kotlin
class StateMachineNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val currentState = ctx.state["machineState"] as? String ?: "INIT"
        
        val nextState = when (currentState) {
            "INIT" -> "PROCESSING"
            "PROCESSING" -> "COMPLETE"
            else -> "ERROR"
        }
        
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = nextState,
                additional = mapOf(
                    "machineState" to nextState,
                    "stateChangedAt" to Instant.now()
                )
            )
        )
    }
}
```

### Use Case 3: Data Accumulation

```kotlin
class AccumulatorNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val accumulated = (ctx.state["accumulated"] as? List<*>) ?: emptyList<String>()
        val newValue = processInput(ctx.state["input"])
        
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = newValue,
                additional = mapOf(
                    "accumulated" to (accumulated + newValue),
                    "totalItems" to (accumulated.size + 1)
                )
            )
        )
    }
}
```

## Performance Considerations

### PersistentMap Efficiency

```kotlin
// ✅ Efficient: O(log n) updates with structural sharing
val ctx1 = ctx.withState("key1", "value1")  // Fast
val ctx2 = ctx1.withState("key2", "value2") // Fast
val ctx3 = ctx2.withState("key3", "value3") // Fast

// Memory: Shares structure, only small diff allocated
```

**PersistentMap properties:**
- Copy-on-Write: O(log n) instead of O(n)
- Structural sharing: Minimal memory overhead
- Thread-safe: No synchronization needed
- Fast reads: O(log n)

### Bulk Updates

```kotlin
// ✅ Prefer bulk updates for multiple keys
val updates = mapOf(
    "key1" to "value1",
    "key2" to "value2",
    "key3" to "value3"
)
val newCtx = ctx.withState(updates)  // Single operation

// ⚠️ Avoid chaining for many updates
val newCtx = ctx
    .withState("key1", "value1")
    .withState("key2", "value2")
    .withState("key3", "value3")
// Creates intermediate objects (still fast, but less optimal)
```

## Migration from Mutable State

### Before (0.5.x)

```kotlin
class OldStyleNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Direct mutation
        ctx.state["result"] = computeResult()
        ctx.state["count"] = (ctx.state["count"] as? Int ?: 0) + 1
        ctx.state["updatedAt"] = Instant.now()
        
        // Mutate collections
        val list = ctx.state["items"] as? MutableList<String> ?: mutableListOf()
        list.add("new-item")
        ctx.state["items"] = list
        
        return SpiceResult.success(NodeResult(data = "done"))
    }
}
```

### After (0.6.0)

```kotlin
class NewStyleNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val result = computeResult()
        val count = (ctx.state["count"] as? Int ?: 0) + 1
        
        // Build new collections
        val items = (ctx.state["items"] as? List<*>) ?: emptyList<String>()
        val newItems = items + "new-item"
        
        // Return all updates
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = "done",
                additional = mapOf(
                    "result" to result,
                    "count" to count,
                    "updatedAt" to Instant.now(),
                    "items" to newItems
                )
            )
        )
    }
}
```

## Best Practices

### ✅ DO: Return State Updates

```kotlin
// Compute new state
val newState = computeNewState(ctx.state["input"])

// Return via metadata
return SpiceResult.success(
    NodeResult.fromContext(
        ctx,
        data = result,
        additional = mapOf("newState" to newState)
    )
)
```

### ❌ DON'T: Try to Mutate

```kotlin
// Won't compile!
ctx.state["key"] = "value"  // ❌ No 'set' operator

// Don't work around it!
val mutableState = ctx.state.toMutableMap()  // ❌ Bad practice
mutableState["key"] = "value"
```

### ✅ DO: Use Builders for Complex Updates

```kotlin
override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
    val updates = buildMap {
        put("stage", "processing")
        put("startedAt", Instant.now())
        
        // Conditional updates
        if (shouldCache) {
            put("cached", true)
            put("cacheKey", generateCacheKey())
        }
        
        // Computed updates
        val metrics = computeMetrics()
        putAll(metrics)
    }
    
    return SpiceResult.success(
        NodeResult.fromContext(ctx, data = result, additional = updates)
    )
}
```

### ✅ DO: Handle Collections Immutably

```kotlin
// Lists
val currentList = ctx.state["items"] as? List<*> ?: emptyList<String>()
val newList = currentList + "new-item"  // Creates new list

// Maps
val currentMap = ctx.state["config"] as? Map<*, *> ?: emptyMap<String, Any>()
val newMap = currentMap + ("newKey" to "newValue")  // Creates new map

// Sets
val currentSet = ctx.state["tags"] as? Set<*> ?: emptySet<String>()
val newSet = currentSet + "new-tag"  // Creates new set

// Return all
return SpiceResult.success(
    NodeResult.fromContext(
        ctx,
        data = "updated",
        additional = mapOf(
            "items" to newList,
            "config" to newMap,
            "tags" to newSet
        )
    )
)
```

## Debugging Patterns

### Pattern 8: Track State Changes

```kotlin
// NodeReport includes metadata changes
val report = runner.run(graph, input).getOrThrow()

report.nodeReports.forEach { nodeReport ->
    println("Node: ${nodeReport.nodeId}")
    
    // See what state was added/modified
    nodeReport.metadataChanges?.forEach { (key, value) ->
        println("  State update: $key = $value")
    }
}
```

**Output example:**
```
Node: validator
  State update: validationResult = true
  State update: validatedAt = 2024-01-15T10:30:00Z

Node: processor
  State update: processedData = [processed content]
  State update: processedAt = 2024-01-15T10:30:01Z
```

### Pattern 9: State History Tracking

```kotlin
class HistoryTrackingNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val history = ctx.state["stateHistory"] as? List<*> ?: emptyList<Map<String, Any>>()
        
        val currentSnapshot = mapOf(
            "nodeId" to id,
            "timestamp" to Instant.now().toString(),
            "stateKeys" to ctx.state.keys.toList()
        )
        
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = "tracked",
                additional = mapOf(
                    "stateHistory" to (history + currentSnapshot)
                )
            )
        )
    }
}
```

## Performance Tips

### Tip 1: Avoid Large State Objects

```kotlin
// ❌ Bad: Storing large objects
additional = mapOf(
    "fullDataset" to millionRecordDataset  // Huge!
)

// ✅ Good: Store references
additional = mapOf(
    "datasetId" to datasetId,
    "datasetSize" to dataset.size,
    "datasetLocation" to s3Url
)
```

### Tip 2: Clean Up Temporary State

```kotlin
class CleanupNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Process using temporary keys
        val temp1 = ctx.state["temp1"]
        val temp2 = ctx.state["temp2"]
        
        val result = process(temp1, temp2)
        
        // Don't propagate temporary keys
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = result,
                additional = mapOf("finalResult" to result)
                // temp1, temp2 NOT included - cleaned up!
            )
        )
    }
}
```

### Tip 3: Use NodeContext.create for Tests

```kotlin
@Test
fun `test node with custom state`() = runTest {
    // Create test context
    val testContext = NodeContext.create(
        graphId = "test-graph",
        state = mapOf(
            "input" to "test data",
            "count" to 5,
            "config" to mapOf("debug" to true)
        ),
        context = ExecutionContext.of(mapOf(
            "tenantId" to "TEST"
        ))
    )
    
    // Run node
    val result = myNode.run(testContext).getOrThrow()
    
    // Verify
    assertEquals("expected", result.data)
}
```

## See Also

- [ExecutionContext Patterns](/docs/guides/execution-context-patterns)
- [Graph Nodes](/docs/orchestration/graph-nodes)
- [Release Guide](../roadmap/release-1-0-0)
- [NodeContext API](/docs/api/graph#nodecontext)
