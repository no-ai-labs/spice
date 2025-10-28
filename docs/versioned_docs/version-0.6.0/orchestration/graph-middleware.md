# Graph Middleware

**Added in:** 0.5.0

Middleware provides powerful hooks to intercept and augment graph execution at various lifecycle points. Inspired by Microsoft Agent Framework's middleware system.

## Overview

Middleware allows you to:
- **Collect metrics** on node execution
- **Add logging** and tracing
- **Handle errors** globally with retry/skip logic
- **Transform requests** before node execution
- **Enforce policies** (rate limiting, auth, etc.)
- **React to lifecycle events**

## Middleware Interface

```kotlin
interface Middleware {
    /**
     * Called once at the start of graph execution.
     */
    suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        next()
    }

    /**
     * Called before/after each node execution.
     * Chain pattern allows middleware to wrap node execution.
     */
    suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        return next(req)
    }

    /**
     * Called when an error occurs during execution.
     * Returns an ErrorAction to control error handling.
     */
    suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
        return ErrorAction.PROPAGATE
    }

    /**
     * Called once at the end of graph execution (success or failure).
     */
    suspend fun onFinish(report: RunReport) {
        // Default: no-op
    }
}
```

## Context Objects

### RunContext

Available in `onStart`, `onError`, and `onFinish`:

```kotlin
data class RunContext(
    val graphId: String,
    val runId: String,
    val agentContext: AgentContext?,
    val metadata: MutableMap<String, Any> = mutableMapOf()
)
```

### NodeRequest

Available in `onNode`:

```kotlin
data class NodeRequest(
    val nodeId: String,
    val input: Any?,
    val context: RunContext
)
```

## Lifecycle Hooks

### onStart Hook

Called once before graph execution begins.

```kotlin
class LoggingMiddleware : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        println("📊 Starting graph execution: ${ctx.graphId}")
        println("   Run ID: ${ctx.runId}")
        println("   Tenant: ${ctx.context.tenantId}")

        next()  // Continue to next middleware or graph execution
    }
}
```

### onNode Hook

Called before and after each node execution. Uses chain pattern.

```kotlin
class MetricsMiddleware : Middleware {
    private val metrics = mutableMapOf<String, Long>()

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val startTime = System.currentTimeMillis()

        // Execute node
        val result = next(req)

        val duration = System.currentTimeMillis() - startTime
        metrics[req.nodeId] = duration

        println("⏱️  Node '${req.nodeId}' took ${duration}ms")

        return result
    }
}
```

### onError Hook

Called when a node fails. Return `ErrorAction` to control behavior.

```kotlin
class RetryMiddleware : Middleware {
    override suspend fun onError(
        err: Throwable,
        ctx: RunContext
    ): ErrorAction {
        return when {
            err.message?.contains("transient") == true -> {
                println("🔄 Retrying due to transient error")
                ErrorAction.RETRY
            }
            err.message?.contains("optional") == true -> {
                println("⏭️  Skipping optional node")
                ErrorAction.SKIP
            }
            else -> {
                println("❌ Propagating error: ${err.message}")
                ErrorAction.PROPAGATE
            }
        }
    }
}
```

**Error Actions:**
- `PROPAGATE`: Default - throw the error
- `RETRY`: Retry the node (up to 3 times)
- `SKIP`: Skip the node and continue to next
- `CONTINUE`: Same as SKIP

### onFinish Hook

Called once after graph execution completes (success or failure).

```kotlin
class ReportingMiddleware : Middleware {
    override suspend fun onFinish(report: RunReport) {
        println("📈 Graph execution finished")
        println("   Status: ${report.status}")
        println("   Duration: ${report.duration}")
        println("   Nodes: ${report.nodeReports.size}")

        if (report.error != null) {
            println("   Error: ${report.error.message}")
        }

        report.nodeReports.forEach { node ->
            println("   - ${node.nodeId}: ${node.status} (${node.duration})")
        }
    }
}
```

## Middleware Examples

### Example 1: Comprehensive Logging

```kotlin
class ComprehensiveLoggingMiddleware : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        log.info("Graph ${ctx.graphId} started (run: ${ctx.runId})")
        next()
    }

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        log.debug("Executing node: ${req.nodeId}")
        val result = next(req)
        when (result) {
            is SpiceResult.Success -> log.debug("Node ${req.nodeId} succeeded")
            is SpiceResult.Failure -> log.error("Node ${req.nodeId} failed: ${result.error.message}")
        }
        return result
    }

    override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
        log.error("Error in graph ${ctx.graphId}: ${err.message}", err)
        return ErrorAction.PROPAGATE
    }

    override suspend fun onFinish(report: RunReport) {
        log.info("Graph ${report.graphId} finished with status ${report.status}")
    }
}
```

### Example 2: Performance Metrics

```kotlin
class PerformanceMetricsMiddleware : Middleware {
    data class NodeMetrics(
        var executionCount: Int = 0,
        var totalDuration: Long = 0,
        var failures: Int = 0
    )

    private val nodeMetrics = ConcurrentHashMap<String, NodeMetrics>()

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val startTime = System.currentTimeMillis()
        val result = next(req)
        val duration = System.currentTimeMillis() - startTime

        val metrics = nodeMetrics.computeIfAbsent(req.nodeId) { NodeMetrics() }
        metrics.executionCount++
        metrics.totalDuration += duration

        if (result is SpiceResult.Failure) {
            metrics.failures++
        }

        return result
    }

    fun getMetrics(): Map<String, NodeMetrics> = nodeMetrics.toMap()

    fun getAverageDuration(nodeId: String): Double {
        val metrics = nodeMetrics[nodeId] ?: return 0.0
        return metrics.totalDuration.toDouble() / metrics.executionCount
    }
}
```

### Example 3: Distributed Tracing

```kotlin
class TracingMiddleware : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        val span = tracer.startSpan("graph-execution")
        span.setTag("graph.id", ctx.graphId)
        span.setTag("run.id", ctx.runId)
        span.setTag("tenant.id", ctx.context.tenantId ?: "unknown")

        try {
            next()
        } finally {
            span.finish()
        }
    }

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val span = tracer.startSpan("node-execution")
        span.setTag("node.id", req.nodeId)

        return try {
            val result = next(req)
            span.setTag("node.status", if (result.isSuccess) "success" else "failure")
            result
        } finally {
            span.finish()
        }
    }
}
```

### Example 4: Smart Retry with Backoff

```kotlin
class SmartRetryMiddleware : Middleware {
    private val retryState = ConcurrentHashMap<String, Int>()

    override suspend fun onError(
        err: Throwable,
        ctx: RunContext
    ): ErrorAction {
        val nodeId = err.stackTrace.firstOrNull()?.className ?: "unknown"
        val retryCount = retryState.getOrPut(nodeId) { 0 }

        return when {
            retryCount >= 3 -> {
                retryState.remove(nodeId)
                ErrorAction.PROPAGATE
            }
            isRetryableError(err) -> {
                retryState[nodeId] = retryCount + 1
                val backoffMs = (1000L * (1 shl retryCount))  // Exponential backoff
                delay(backoffMs)
                ErrorAction.RETRY
            }
            else -> ErrorAction.PROPAGATE
        }
    }

    private fun isRetryableError(err: Throwable): Boolean {
        return err.message?.contains("timeout") == true ||
               err.message?.contains("connection") == true ||
               err is java.io.IOException
    }
}
```

### Example 5: Request Transformation

```kotlin
class RequestEnrichmentMiddleware : Middleware {
    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        // Enrich request with additional metadata
        val enrichedContext = req.context.copy(
            metadata = req.context.metadata.toMutableMap().apply {
                put("enriched_at", System.currentTimeMillis())
                put("enriched_by", "middleware")
            }
        )

        val enrichedRequest = req.copy(context = enrichedContext)
        return next(enrichedRequest)
    }
}
```

## Middleware Chain

Multiple middleware are executed in order:

```kotlin
val graph = Graph(
    id = "my-graph",
    nodes = nodes,
    edges = edges,
    entryPoint = "start",
    middleware = listOf(
        LoggingMiddleware(),       // Executes first
        MetricsMiddleware(),        // Then this
        RetryMiddleware(),          // Then this
        TracingMiddleware()         // Last
    )
)
```

**Execution Order:**

```
onStart:
  Logging → Metrics → Retry → Tracing → [Graph Execution]

onNode (for each node):
  Logging → Metrics → Retry → Tracing → [Node Execution] → Tracing → Retry → Metrics → Logging

onError (if error occurs):
  All middleware consulted, first non-PROPAGATE action wins

onFinish:
  Tracing → Retry → Metrics → Logging
```

## Usage

### With DSL

```kotlin
val graph = Graph(
    id = "monitored-workflow",
    nodes = mapOf(/* ... */),
    edges = listOf(/* ... */),
    entryPoint = "start",
    middleware = listOf(
        LoggingMiddleware(),
        MetricsMiddleware(),
        RetryMiddleware()
    )
)
```

### Testing Middleware

```kotlin
@Test
fun `test middleware execution order`() = runTest {
    val executionLog = mutableListOf<String>()

    val middleware1 = object : Middleware {
        override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
            executionLog.add("middleware1-start")
            next()
        }
    }

    val middleware2 = object : Middleware {
        override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
            executionLog.add("middleware2-start")
            next()
        }
    }

    val graph = Graph(
        id = "test",
        nodes = mapOf("output" to OutputNode("output")),
        edges = emptyList(),
        entryPoint = "output",
        middleware = listOf(middleware1, middleware2)
    )

    runner.run(graph, emptyMap())

    assertEquals(listOf("middleware1-start", "middleware2-start"), executionLog)
}
```

## Best Practices

### ✅ Do's

1. **Keep middleware focused** - One responsibility per middleware
2. **Chain properly** - Always call `next()` unless intentionally stopping execution
3. **Handle errors** - Catch exceptions in middleware to prevent cascading failures
4. **Use correlation IDs** - For distributed tracing across services
5. **Make middleware reusable** - Design for multiple graphs

### ❌ Don'ts

1. **Don't block execution** - Async operations should use coroutines
2. **Don't mutate requests** - Unless that's the explicit purpose
3. **Don't skip calling next()** - Unless you have a good reason
4. **Don't store mutable state** - Use thread-safe collections
5. **Don't throw exceptions** - Return ErrorAction instead

## Next Steps

- Learn about [Checkpoint & Resume](./graph-checkpoint.md)
- Understand [Graph Validation](./graph-validation.md)
- Review [Error Handling](../error-handling/overview.md)

## Related

- [Graph System Overview](./graph-system.md)
- [Graph Nodes](./graph-nodes.md)
- [Advanced Context Propagation](../advanced/context-propagation.md)
