---
sidebar_position: 4
---

# Troubleshooting Guide

Common issues and solutions when working with Spice Graph System.

## Build/Setup Issues

### Issue: "Cannot resolve spice-core:0.5.0"

**Symptoms:**
```
Could not find io.github.noailabs:spice-core:0.5.0
```

**Solutions:**

1. **Check Maven Central:**
```kotlin
repositories {
    mavenCentral()  // Make sure this is present
}
```

2. **Clear Gradle cache:**
```bash
./gradlew clean
./gradlew --refresh-dependencies
```

3. **Check version:**
```kotlin
// Make sure version is correct
dependencies {
    implementation("io.github.noailabs:spice-core:0.5.0")
}
```

### Issue: "Unresolved reference: graph"

**Symptoms:**
```kotlin
val graph = graph("my-graph") { }  // Unresolved reference: graph
```

**Solutions:**

1. **Add import:**
```kotlin
import io.github.noailabs.spice.graph.dsl.graph
```

2. **Check dependency:**
```kotlin
dependencies {
    implementation("io.github.noailabs:spice-core:0.5.0")
}
```

### Issue: "humanNode not found" or "decision not found"

**Symptoms:**
```kotlin
graph("my-graph") {
    human("review", "Approve?")  // ❌ Not found
    decision("route") { }          // ❌ Not found
}
```

**Solutions:**

This is a **0.4.4 → 0.5.0 migration issue**.

```kotlin
// ❌ Old (0.4.4)
human("review", "Approve?")
decision("route") { }

// ✅ New (0.5.0)
humanNode(
    id = "review",
    prompt = "Approve?",
    options = listOf(HumanOption("yes", "Yes"))
)

// decision() removed - use edge() with conditions
edge("from", "to") { result ->
    // Your condition
}
```

**See:** [Migration Guide](./migration-0.4-to-0.5.md)

## Runtime Issues

### Issue: "Graph must have at least one node"

**Symptoms:**
```
IllegalArgumentException: Graph must have at least one node
```

**Cause:**
```kotlin
// ❌ Empty graph
val graph = graph("empty") {
    // No nodes added!
}
```

**Solution:**
```kotlin
// ✅ Add at least one node
val graph = graph("valid") {
    agent("my-agent", myAgent)
    output("result") { it.state["my-agent"] }
}
```

### Issue: "Node not found: xyz"

**Symptoms:**
```
IllegalStateException: Node not found: xyz
```

**Cause:**
Edge references non-existent node.

```kotlin
// ❌ Wrong
graph("bad") {
    agent("a", agentA)
    edge("a", "xyz")  // xyz doesn't exist!
}
```

**Solution:**
```kotlin
// ✅ Correct
graph("good") {
    agent("a", agentA)
    agent("b", agentB)  // Define node first
    edge("a", "b")      // Then reference it
}
```

### Issue: Graph hangs or never completes

**Symptoms:**
- Execution never finishes
- No error thrown
- Infinite loop

**Cause 1: Cyclic graph**

```kotlin
// ❌ Infinite loop
graph("cycle") {
    agent("a", agentA)
    agent("b", agentB)
    edge("a", "b")
    edge("b", "a")  // Cycle!
}
```

**Solution:**
```kotlin
// ✅ Add termination condition
graph("conditional") {
    agent("a", agentA)
    agent("b", agentB)
    output("result") { it.state["b"] }

    edge("a", "b")
    edge("b", "a") { result ->
        // Only loop if condition met
        val iterations = result.metadata["iterations"] as? Int ?: 0
        iterations < 3
    }
    edge("b", "result") { result ->
        val iterations = result.metadata["iterations"] as? Int ?: 0
        iterations >= 3
    }
}
```

**Cause 2: No path to output**

```kotlin
// ❌ No edge to output
graph("stuck") {
    agent("a", agentA)
    agent("b", agentB)
    output("result") { it.state["b"] }

    edge("a", "b")
    // Missing: edge("b", "result")
}
```

**Solution:**
```kotlin
// ✅ Complete path
graph("complete") {
    agent("a", agentA)
    agent("b", agentB)
    output("result") { it.state["b"] }

    edge("a", "b")
    edge("b", "result")  // Add missing edge
}
```

**Debug with validation:**
```kotlin
import io.github.noailabs.spice.graph.GraphValidator

// Check for cycles
val cycles = GraphValidator.findCycles(graph)
if (cycles.isNotEmpty()) {
    println("Cycles found: $cycles")
}

// Check for unreachable nodes
val unreachable = GraphValidator.findUnreachableNodes(graph)
if (unreachable.isNotEmpty()) {
    println("Unreachable nodes: $unreachable")
}
```

### Issue: Result is null or unexpected

**Symptoms:**
```kotlin
val report = runner.run(graph, input).getOrThrow()
val result = report.result  // null or unexpected value
```

**Cause 1: Wrong output selector**

```kotlin
// ❌ Key doesn't exist
output("result") { it.state["nonexistent-key"] }
```

**Solution:**
```kotlin
// ✅ Use actual node ID
output("result") { it.state["my-agent"] }

// ✅ Or use _previous for last executed node
output("result") { it.state["_previous"] }
```

**Cause 2: Conditional routing issue**

```kotlin
// ❌ No matching edge
graph("routing") {
    agent("classifier", classifier)
    agent("handler", handler)
    output("result") { it.state["handler"] }

    edge("classifier", "handler") { result ->
        result.data == "specific-value"  // What if it's different?
    }
}
```

**Solution:**
```kotlin
// ✅ Add default route
graph("routing") {
    agent("classifier", classifier)
    agent("handler-a", handlerA)
    agent("handler-b", handlerB)  // Default handler
    output("result") { it.state["_previous"] }

    edge("classifier", "handler-a") { result ->
        result.data == "specific-value"
    }

    edge("classifier", "handler-b") { result ->
        // Default: everything else
        result.data != "specific-value"
    }

    edge("handler-a", "result")
    edge("handler-b", "result")
}
```

**Debug approach:**

```kotlin
val report = runner.run(graph, input).getOrThrow()

// Print all node outputs
report.nodeReports.forEach { nodeReport ->
    println("${nodeReport.nodeId}: ${nodeReport.output}")
}

// Check final state
println("Final state: ${report.result}")
```

## Checkpoint Issues

### Issue: "Checkpoint not found"

**Symptoms:**
```
NoSuchElementException: Checkpoint not found: checkpoint-123
```

**Causes:**
1. Checkpoint expired (TTL)
2. Checkpoint was deleted
3. Wrong checkpoint ID

**Solutions:**

1. **Check TTL:**
```kotlin
// Extend TTL in Redis
class RedisCheckpointStore(
    private val redisTemplate: RedisTemplate<String, String>
) : CheckpointStore {
    private val ttl = 7L  // Increase if needed

    override suspend fun save(checkpoint: Checkpoint): SpiceResult<String> {
        // ...
        redisTemplate.expire(key, ttl, TimeUnit.DAYS)
        // ...
    }
}
```

2. **Verify checkpoint exists:**
```kotlin
val exists = checkpointStore.load(checkpointId)
if (exists.isFailure) {
    println("Checkpoint not found or expired")
}
```

3. **List available checkpoints:**
```kotlin
val checkpoints = checkpointStore.listByGraph(graphId).getOrThrow()
println("Available checkpoints: ${checkpoints.map { it.id }}")
```

### Issue: "Cannot resume from checkpoint"

**Symptoms:**
```
IllegalStateException: Checkpoint is not waiting for human input
```

**Cause:**
Trying to resume with `resumeWithHumanResponse()` when checkpoint is not paused.

```kotlin
// ❌ Wrong method
runner.resumeWithHumanResponse(graph, checkpointId, response, store)
```

**Solution:**

Check checkpoint state first:

```kotlin
val checkpoint = store.load(checkpointId).getOrThrow()

when (checkpoint.executionState) {
    GraphExecutionState.WAITING_FOR_HUMAN -> {
        // ✅ Correct
        runner.resumeWithHumanResponse(graph, checkpointId, response, store)
    }
    GraphExecutionState.RUNNING -> {
        // ✅ Use regular resume
        runner.resume(graph, checkpointId, store)
    }
    else -> {
        println("Cannot resume from state: ${checkpoint.executionState}")
    }
}
```

## HITL (Human-in-the-Loop) Issues

### Issue: "humanNode is not a function"

**Symptoms:**
```
Unresolved reference: humanNode
```

**Cause:**
Using old API or missing import.

**Solution:**
```kotlin
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.nodes.HumanOption
import java.time.Duration

graph("my-graph") {
    humanNode(
        id = "review",
        prompt = "Please review",
        options = listOf(
            HumanOption("approve", "Approve"),
            HumanOption("reject", "Reject")
        ),
        timeout = Duration.ofHours(24)
    )
}
```

### Issue: "Human response timeout expired"

**Symptoms:**
```
IllegalStateException: Human response timeout expired at ...
```

**Cause:**
Response submitted after timeout.

**Solutions:**

1. **Increase timeout:**
```kotlin
humanNode(
    id = "review",
    prompt = "...",
    timeout = Duration.ofHours(48)  // Increase
)
```

2. **Handle expired checkpoints:**
```kotlin
try {
    runner.resumeWithHumanResponse(graph, checkpointId, response, store)
} catch (e: IllegalStateException) {
    if (e.message?.contains("timeout expired") == true) {
        // Handle timeout
        return ErrorResponse("Request expired, please restart")
    }
    throw e
}
```

3. **Check expiration before submitting:**
```kotlin
val checkpoint = store.load(checkpointId).getOrThrow()
val expiresAt = checkpoint.pendingInteraction?.expiresAt

if (expiresAt != null) {
    val expiryTime = Instant.parse(expiresAt)
    if (Instant.now().isAfter(expiryTime)) {
        return ErrorResponse("Request expired")
    }
}
```

### Issue: "Response validation failed"

**Symptoms:**
```
IllegalArgumentException: Human response failed validation
```

**Cause:**
Response doesn't match validator.

```kotlin
humanNode(
    id = "review",
    prompt = "...",
    validator = { response ->
        response.selectedOption in listOf("approve", "reject")
    }
)

// ❌ Submitting "maybe" will fail validation
val response = HumanResponse.choice("review", "maybe")
```

**Solution:**
```kotlin
// ✅ Match validator expectations
val response = HumanResponse.choice("review", "approve")
```

Or update validator:

```kotlin
humanNode(
    id = "review",
    prompt = "...",
    validator = { response ->
        response.selectedOption in listOf("approve", "reject", "maybe")
    }
)
```

## Error Handling Issues

### Issue: "Node execution failed but graph didn't fail"

**Symptoms:**
- Node fails but graph continues
- Expected error not propagated

**Cause:**
Middleware is handling error with SKIP or CONTINUE.

```kotlin
middleware(object : Middleware {
    override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
        return ErrorAction.SKIP  // Always skips errors!
    }
})
```

**Solution:**

Be selective about error handling:

```kotlin
middleware(object : Middleware {
    override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
        return when (err) {
            is OptionalOperationException -> ErrorAction.SKIP
            is RetryableException -> ErrorAction.RETRY
            else -> ErrorAction.PROPAGATE  // Let critical errors fail
        }
    }
})
```

### Issue: "Retry happens indefinitely"

**Symptoms:**
- Node keeps retrying
- Graph never completes or fails

**Cause:**
Middleware returns RETRY but DefaultGraphRunner has max retry limit (3).

**Solution:**

The default runner limits retries to 3. Check if issue is:

1. **Multiple middleware returning RETRY:**
```kotlin
// Each middleware can trigger retry, check all of them
middleware(retryMiddleware1)
middleware(retryMiddleware2)  // Both returning RETRY
```

2. **Cycle in graph with RETRY:**
```kotlin
// Combination of cycle + retry = infinite
edge("a", "b")
edge("b", "a")  // Cycle!
// + middleware returning RETRY
```

**Fix:**
```kotlin
class LimitedRetryMiddleware : Middleware {
    private val retryCount = ConcurrentHashMap<String, AtomicInteger>()

    override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
        val key = "${ctx.runId}-${ctx.graphId}"
        val count = retryCount.computeIfAbsent(key) { AtomicInteger(0) }

        return if (count.incrementAndGet() <= 3) {
            ErrorAction.RETRY
        } else {
            count.remove(key)
            ErrorAction.PROPAGATE
        }
    }
}
```

## Performance Issues

### Issue: "Graph execution is slow"

**Solutions:**

1. **Enable checkpointing** (for long-running graphs):
```kotlin
val config = CheckpointConfig(
    saveEveryNNodes = 5,  // Save progress
    saveOnError = true
)

runner.runWithCheckpoint(graph, input, store, config)
```

2. **Check middleware overhead:**
```kotlin
// Profile middleware
class ProfilingMiddleware : Middleware {
    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val start = System.currentTimeMillis()
        val result = next(req)
        val duration = System.currentTimeMillis() - start

        if (duration > 1000) {
            println("Slow node: ${req.nodeId} took ${duration}ms")
        }

        return result
    }
}
```

3. **Optimize agent/tool implementations:**
```kotlin
// Agent should be fast
class OptimizedAgent : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Use caching
        val cached = cache.get(comm.content)
        if (cached != null) {
            return SpiceResult.success(cached)
        }

        // Process...
        val result = process(comm)

        cache.put(comm.content, result)
        return SpiceResult.success(result)
    }
}
```

### Issue: "Memory issues with checkpoints"

**Symptoms:**
- OutOfMemoryError
- Redis memory limit exceeded

**Cause:**
Storing large objects in state.

```kotlin
// ❌ Large object in state
ctx.state["huge-data"] = loadHugeDataset()  // MB/GB of data
```

**Solution:**

1. **Store references, not data:**
```kotlin
// ✅ Store ID/reference
ctx.state["dataset-id"] = "dataset-123"

// Fetch data when needed
fun fetchData(id: String): Dataset {
    return dataService.load(id)
}
```

2. **Clean up state:**
```kotlin
class CleanupNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Remove large objects after use
        ctx.state.remove("large-object")

        return SpiceResult.success(NodeResult(data = "cleaned"))
    }
}
```

3. **Limit checkpoint retention:**
```kotlin
val config = CheckpointConfig(
    maxCheckpointsPerRun = 5  // Keep only last 5
)
```

## Context Propagation Issues

### Issue: "AgentContext is null in nodes"

**Symptoms:**
```kotlin
class MyNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val tenantId = ctx.agentContext?.tenantId  // null!
    }
}
```

**Cause:**
Not setting context before execution.

**Solution:**

```kotlin
import kotlinx.coroutines.withContext
import io.github.noailabs.spice.AgentContext

// ✅ Set context
val agentContext = AgentContext.of(
    "tenantId" to "tenant-123",
    "userId" to "user-456"
)

withContext(agentContext) {
    runner.run(graph, input)
}
```

**Spring Boot:**

```kotlin
@PostMapping("/execute")
suspend fun execute(
    @RequestHeader("X-Tenant-Id") tenantId: String,
    @RequestHeader("X-User-Id") userId: String,
    @RequestBody request: Request
): Response {
    val agentContext = AgentContext.of(
        "tenantId" to tenantId,
        "userId" to userId
    )

    return withContext(agentContext) {
        workflowService.execute(request)
    }
}
```

## Package/Import Issues

### Issue: "Cannot find io.github.noailabs.spice.graph.dsl.graph"

**Common packages:**

```kotlin
// Graph DSL
import io.github.noailabs.spice.graph.dsl.graph

// Core types
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.graph.Edge

// Runner
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.RunReport
import io.github.noailabs.spice.graph.runner.RunStatus
import io.github.noailabs.spice.graph.runner.NodeReport
import io.github.noailabs.spice.graph.runner.NodeStatus

// Middleware
import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.graph.middleware.ErrorAction
import io.github.noailabs.spice.graph.middleware.RunContext
import io.github.noailabs.spice.graph.middleware.NodeRequest
import io.github.noailabs.spice.graph.middleware.LoggingMiddleware
import io.github.noailabs.spice.graph.middleware.MetricsMiddleware

// HITL
import io.github.noailabs.spice.graph.nodes.HumanNode
import io.github.noailabs.spice.graph.nodes.HumanOption
import io.github.noailabs.spice.graph.nodes.HumanResponse
import io.github.noailabs.spice.graph.nodes.HumanInteraction
import io.github.noailabs.spice.graph.nodes.GraphExecutionState

// Checkpoint
import io.github.noailabs.spice.graph.checkpoint.Checkpoint
import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
import io.github.noailabs.spice.graph.checkpoint.CheckpointConfig
import io.github.noailabs.spice.graph.checkpoint.InMemoryCheckpointStore

// Validation
import io.github.noailabs.spice.graph.GraphValidator

// Built-in nodes
import io.github.noailabs.spice.graph.nodes.AgentNode
import io.github.noailabs.spice.graph.nodes.ToolNode
import io.github.noailabs.spice.graph.nodes.OutputNode
```

## Getting Help

If your issue isn't covered here:

1. **Check the documentation:**
   - [Quick Start](../getting-started/graph-quick-start.md)
   - [API Reference](../api/graph.md)
   - [Migration Guide](./migration-0.4-to-0.5.md)

2. **Enable debug logging:**
```kotlin
middleware(LoggingMiddleware())

// Or use structured logging
class DebugMiddleware : Middleware {
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        logger.debug("Node: ${req.nodeId}, Input: ${req.input}")
        val result = next(req)
        logger.debug("Node: ${req.nodeId}, Result: $result")
        return result
    }
}
```

3. **Validate your graph:**
```kotlin
val validation = GraphValidator.validate(graph)
if (validation.isFailure) {
    println("Validation failed: ${validation.error.message}")
}
```

4. **Create a minimal reproduction:**
```kotlin
val minimalGraph = graph("debug") {
    agent("test", testAgent)
    output("result") { it.state["test"] }
}

val result = DefaultGraphRunner().run(
    minimalGraph,
    mapOf("input" to "test")
)

println(result)
```

5. **Open an issue:**
   - GitHub: https://github.com/no-ai-labs/spice/issues
   - Include: Spice version, code snippet, error message, stack trace

## Quick Diagnostic Checklist

When something goes wrong:

- [ ] Is dependency correct? (`io.github.noailabs:spice-core:0.5.0`)
- [ ] Are imports correct?
- [ ] Does graph have at least one node?
- [ ] Do all edges reference existing nodes?
- [ ] Is there a path from entry to output?
- [ ] Are there any cycles?
- [ ] Is AgentContext set (if needed)?
- [ ] Are checkpoint IDs valid (if using checkpoints)?
- [ ] Are human responses validated (if using HITL)?
- [ ] Is error handling appropriate?

Run validation:
```kotlin
GraphValidator.validate(graph).getOrThrow()
```

## Related

- **[Design Patterns](./graph-patterns.md)** - Best practices
- **[Spring Boot Integration](./graph-spring-boot.md)** - Production setup
- **[API Reference](../api/graph.md)** - Complete API docs
