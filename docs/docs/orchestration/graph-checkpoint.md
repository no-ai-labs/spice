# Checkpoint & Resume

**Added in:** 0.5.0

The Checkpoint system enables resilient, long-running workflows by saving execution state at regular intervals. Resume from failures without re-executing completed nodes.

## Overview

Checkpointing provides:
- **Save execution state** during graph execution
- **Resume from failure** points
- **Automatic cleanup** on success
- **Configurable triggers** (time, node count, error)
- **Context preservation** including AgentContext

## Quick Start

```kotlin
val store = InMemoryCheckpointStore()
val config = CheckpointConfig(
    saveEveryNNodes = 5,
    saveOnError = true
)

// Run with checkpointing
val result = runner.runWithCheckpoint(
    graph = graph,
    input = input,
    store = store,
    config = config
)

// If it fails, resume
if (result.isFailure) {
    val checkpoints = store.listByGraph(graph.id).getOrThrow()
    val latestCheckpoint = checkpoints.first()

    val resumeResult = runner.resume(graph, latestCheckpoint.id, store)
}
```

## Checkpoint Configuration

```kotlin
data class CheckpointConfig(
    val saveEveryNNodes: Int? = null,        // Save every N nodes
    val saveEveryNSeconds: Long? = null,     // Save every N seconds
    val maxCheckpointsPerRun: Int = 10,      // Max checkpoints to keep
    val saveOnError: Boolean = true          // Save on error
)
```

### Configuration Strategies

**1. Node-based Checkpointing**
```kotlin
val config = CheckpointConfig(
    saveEveryNNodes = 5  // Checkpoint after every 5 nodes
)
```

**2. Time-based Checkpointing**
```kotlin
val config = CheckpointConfig(
    saveEveryNSeconds = 60  // Checkpoint every minute
)
```

**3. Error-only Checkpointing**
```kotlin
val config = CheckpointConfig(
    saveOnError = true  // Only save when errors occur
)
```

**4. Combined Strategy**
```kotlin
val config = CheckpointConfig(
    saveEveryNNodes = 10,
    saveEveryNSeconds = 300,  // 5 minutes
    saveOnError = true
)
```

## Checkpoint Data

```kotlin
data class Checkpoint(
    val id: String,
    val runId: String,
    val graphId: String,
    val currentNodeId: String,
    val state: Map<String, Any?>,
    val agentContext: AgentContext? = null,
    val timestamp: Instant = Instant.now(),
    val metadata: Map<String, Any> = emptyMap()
)
```

## CheckpointStore Interface

Implement custom storage backends:

```kotlin
interface CheckpointStore {
    suspend fun save(checkpoint: Checkpoint): SpiceResult<String>
    suspend fun load(checkpointId: String): SpiceResult<Checkpoint>
    suspend fun listByRun(runId: String): SpiceResult<List<Checkpoint>>
    suspend fun listByGraph(graphId: String): SpiceResult<List<Checkpoint>>
    suspend fun delete(checkpointId: String): SpiceResult<Unit>
    suspend fun deleteByRun(runId: String): SpiceResult<Unit>
}
```

### Built-in Stores

#### InMemoryCheckpointStore

For testing and single-process applications:

```kotlin
val store = InMemoryCheckpointStore()

// Store operations
store.save(checkpoint)
store.load(checkpointId)
store.listByGraph(graphId)
store.delete(checkpointId)

// Utility methods
store.clear()  // Clear all checkpoints
store.size()   // Get count
```

**Note**: Checkpoints are lost when process terminates. Use custom store for persistence.

## Custom Checkpoint Stores

### Example: Redis Store

```kotlin
class RedisCheckpointStore(
    private val redis: RedisClient
) : CheckpointStore {
    override suspend fun save(checkpoint: Checkpoint): SpiceResult<String> {
        return SpiceResult.catching {
            val json = Json.encodeToString(checkpoint)
            redis.set("checkpoint:${checkpoint.id}", json)
            checkpoint.id
        }
    }

    override suspend fun load(checkpointId: String): SpiceResult<Checkpoint> {
        return SpiceResult.catching {
            val json = redis.get("checkpoint:$checkpointId")
                ?: throw NoSuchElementException("Checkpoint not found")
            Json.decodeFromString(json)
        }
    }

    // ... other methods
}
```

### Example: Database Store

```kotlin
class DatabaseCheckpointStore(
    private val database: Database
) : CheckpointStore {
    override suspend fun save(checkpoint: Checkpoint): SpiceResult<String> {
        return SpiceResult.catching {
            database.transaction {
                CheckpointTable.insert {
                    it[id] = checkpoint.id
                    it[runId] = checkpoint.runId
                    it[graphId] = checkpoint.graphId
                    it[currentNodeId] = checkpoint.currentNodeId
                    it[state] = Json.encodeToString(checkpoint.state)
                    it[timestamp] = checkpoint.timestamp
                }
            }
            checkpoint.id
        }
    }

    // ... other methods
}
```

## Usage Examples

### Example 1: Long-Running Workflow

```kotlin
val longWorkflow = graph("data-processing") {
    // 20 nodes total
    repeat(20) { i ->
        agent("step-$i", processingAgent)
    }
    output("result") { it.state["step-19"] }
}

val config = CheckpointConfig(
    saveEveryNNodes = 5  // Checkpoint after every 5 nodes
)

val result = runner.runWithCheckpoint(
    graph = longWorkflow,
    input = input,
    store = InMemoryCheckpointStore(),
    config = config
)
```

### Example 2: Resume After Failure

```kotlin
val store = InMemoryCheckpointStore()

// First attempt (fails at node 15/20)
val result1 = runner.runWithCheckpoint(
    graph = workflow,
    input = input,
    store = store,
    config = CheckpointConfig(saveEveryNNodes = 5, saveOnError = true)
)

if (result1.isFailure) {
    println("Workflow failed. Finding last checkpoint...")

    // Get checkpoints for this run
    val checkpoints = store.listByGraph(workflow.id).getOrThrow()
    val latestCheckpoint = checkpoints.first()

    println("Resuming from node: ${latestCheckpoint.currentNodeId}")

    // Resume execution
    val result2 = runner.resume(
        graph = workflow,
        checkpointId = latestCheckpoint.id,
        store = store
    )

    println("Resume result: ${result2.isSuccess}")
}
```

### Example 3: Context Preservation

```kotlin
// Set tenant context
val agentContext = AgentContext.of(
    "tenantId" to "tenant-123",
    "userId" to "user-456",
    "sessionId" to "session-789"
)

val store = InMemoryCheckpointStore()

// Run with context
withContext(agentContext) {
    runner.runWithCheckpoint(
        graph = graph,
        input = input,
        store = store,
        config = CheckpointConfig(saveEveryNNodes = 5)
    )
}

// Later: Resume without context (restored from checkpoint)
val checkpoints = store.listByGraph(graph.id).getOrThrow()
val result = runner.resume(graph, checkpoints.first().id, store)

// Context is automatically restored!
```

### Example 4: Manual Checkpoint Management

```kotlin
val store = InMemoryCheckpointStore()

// Run with checkpointing
runner.runWithCheckpoint(graph, input, store, config)

// List all checkpoints for a graph
val checkpoints = store.listByGraph("my-graph").getOrThrow()
checkpoints.forEach { checkpoint ->
    println("Checkpoint: ${checkpoint.id}")
    println("  Node: ${checkpoint.currentNodeId}")
    println("  Time: ${checkpoint.timestamp}")
    println("  State keys: ${checkpoint.state.keys}")
}

// Delete old checkpoints
checkpoints.drop(5).forEach { checkpoint ->
    store.delete(checkpoint.id)
}
```

## Checkpoint Lifecycle

```
1. Graph Execution Starts
   ↓
2. Node Executes Successfully
   ↓
3. Check if checkpoint needed:
   - Every N nodes?
   - Every N seconds elapsed?
   ↓
4. Save Checkpoint:
   - Current node ID
   - Full state
   - AgentContext
   - Timestamp
   ↓
5. Continue to Next Node
   ↓
6. (If error) Save Error Checkpoint (if configured)
   ↓
7. Graph Completes Successfully
   ↓
8. Clean Up Checkpoints (automatic)
```

## Resume Behavior

When resuming from a checkpoint:
1. **Load checkpoint** with full state
2. **Restore AgentContext** from checkpoint
3. **Skip to next node** after checkpoint (don't re-execute)
4. **Continue execution** normally
5. **Preserve run ID** from original execution

```kotlin
// Original run: Executes nodes 1-10, fails at 11
// Checkpoint saved at node 10

// Resume: Starts at node 11, continues 11-20
val result = runner.resume(graph, checkpointId, store)
```

## Best Practices

### ✅ Do's

1. **Checkpoint frequently** in long-running workflows
2. **Use persistent stores** for production (Redis, DB)
3. **Clean up old checkpoints** periodically
4. **Test resume scenarios** thoroughly
5. **Log checkpoint IDs** for debugging
6. **Include metadata** for context

### ❌ Don'ts

1. **Don't checkpoint too frequently** - impacts performance
2. **Don't store large objects** in state - use references
3. **Don't rely on in-memory store** in production
4. **Don't forget to handle resume failures**
5. **Don't modify checkpoint data** manually

## Error Scenarios

### Scenario 1: Checkpoint Save Fails

```kotlin
// Checkpoint save failure doesn't stop execution
// Error is logged, execution continues
```

### Scenario 2: Resume with Invalid ID

```kotlin
val result = runner.resume(graph, "invalid-id", store)
// Returns SpiceResult.Failure with CheckpointError
```

### Scenario 3: State Corruption

```kotlin
// Graph structure changed after checkpoint was saved
// Resume may fail if nodes don't exist
// Validation catches this early
```

## Performance Considerations

**Checkpoint Overhead:**
- **Node-based**: Low overhead, predictable
- **Time-based**: Medium overhead, depends on execution speed
- **Every node**: High overhead, not recommended

**Storage Size:**
- State map size matters
- AgentContext is small (~100 bytes)
- Use compression for large states

**Recommended Settings:**
- **Short workflows** (under 10 nodes): No checkpointing or error-only
- **Medium workflows** (10-50 nodes): Every 10-20 nodes
- **Long workflows** (over 50 nodes): Every 20-30 nodes + time-based

## Next Steps

- Understand [Graph Validation](./graph-validation.md)
- Explore [Graph Middleware](./graph-middleware.md)
- See [Production Patterns](../examples/production-patterns.md)

## Related

- [Graph System Overview](./graph-system.md)
- [Error Handling](../error-handling/overview.md)
- [Context Propagation](../core-concepts/agent-context.md)
