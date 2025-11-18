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
        println("   Tenant: ${ctx.agentContext?.tenantId}")

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
        span.setTag("tenant.id", ctx.agentContext?.tenantId ?: "unknown")

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

## Built-in Middleware

### LoggingMiddleware

**Added in:** 0.5.0

Built-in logging middleware using SLF4J with emoji indicators.

```kotlin
import io.github.noailabs.spice.graph.middleware.LoggingMiddleware

val graph = graph("my-graph") {
    middleware(LoggingMiddleware())
    // ... nodes
}
```

**What it logs:**
- 📊 Graph start with graphId, runId, tenant info
- 🔹 Node execution start
- ✅ Node success
- ❌ Node failure with error details
- 📈 Graph finish with status and duration

**Configuration:**
```kotlin
// Uses SLF4J, configure via logback.xml or similar
<logger name="io.github.noailabs.spice.graph.middleware.LoggingMiddleware" level="DEBUG"/>
```

### MetricsMiddleware

**Added in:** 0.5.0

Built-in metrics collection middleware for performance monitoring.

```kotlin
import io.github.noailabs.spice.graph.middleware.MetricsMiddleware

val metricsMiddleware = MetricsMiddleware()

val graph = graph("my-graph") {
    middleware(metricsMiddleware)
    // ... nodes
}

// After execution, access metrics:
val metrics = metricsMiddleware.getMetrics()
println("Average execution time: ${metrics.averageExecutionTime}ms")
println("Total errors: ${metrics.errorCount}")
println("Node times: ${metrics.nodeExecutionTimes}")
```

**Collected Metrics:**
- Per-node execution times (thread-safe `ConcurrentHashMap`)
- Total execution count
- Error count with details
- Graph execution duration

**Thread Safety:**
MetricsMiddleware uses `ConcurrentHashMap` and `CopyOnWriteArrayList` for safe concurrent access.

## Advanced Patterns

### Pattern 1: Conditional Middleware

Apply middleware only to specific nodes:

```kotlin
class ConditionalMiddleware(
    val condition: (NodeRequest) -> Boolean,
    val wrapped: Middleware
) : Middleware {
    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        return if (condition(req)) {
            wrapped.onNode(req, next)
        } else {
            next(req)
        }
    }
}

// Usage: Apply auth only to "secure-*" nodes
val graph = graph("my-graph") {
    middleware(ConditionalMiddleware(
        condition = { req -> req.nodeId.startsWith("secure-") },
        wrapped = AuthMiddleware()
    ))
}
```

### Pattern 2: Authentication & Authorization

```kotlin
class AuthMiddleware(
    val authProvider: AuthProvider
) : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        val token = ctx.agentContext?.get("auth_token") as? String
        if (token == null || !authProvider.validateToken(token)) {
            throw SecurityException("Invalid or missing authentication token")
        }
        next()
    }

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val userId = req.context.agentContext?.userId
        val requiredPermission = getRequiredPermission(req.nodeId)

        if (!authProvider.hasPermission(userId, requiredPermission)) {
            return SpiceResult.failure(
                SpiceError.validationError("User lacks permission: $requiredPermission")
            )
        }

        return next(req)
    }

    private fun getRequiredPermission(nodeId: String): String {
        return when {
            nodeId.startsWith("admin-") -> "admin"
            nodeId.startsWith("write-") -> "write"
            else -> "read"
        }
    }
}
```

### Pattern 3: Result Caching

```kotlin
class CachingMiddleware(
    val cache: MutableMap<String, CacheEntry> = ConcurrentHashMap(),
    val ttlMs: Long = 60000,  // 1 minute default
    val keyBuilder: (NodeRequest) -> String = { req ->
        "${req.nodeId}:${req.input.hashCode()}"
    }
) : Middleware {
    data class CacheEntry(
        val result: NodeResult,
        val timestamp: Long = System.currentTimeMillis()
    )

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val key = keyBuilder(req)
        val cached = cache[key]

        // Check if cache is valid
        if (cached != null && System.currentTimeMillis() - cached.timestamp < ttlMs) {
            println("✨ Cache HIT for ${req.nodeId}")
            return SpiceResult.success(cached.result)
        }

        // Execute and cache
        return next(req).onSuccess { result ->
            cache[key] = CacheEntry(result)
            println("💾 Cache SET for ${req.nodeId}")
        }
    }

    fun clearCache() = cache.clear()
    fun getCacheSize() = cache.size
}
```

### Pattern 4: Rate Limiting

```kotlin
class RateLimitMiddleware(
    val maxRequestsPerSecond: Int = 10,
    val perNode: Boolean = true
) : Middleware {
    private data class RateLimiter(
        val requests: MutableList<Long> = Collections.synchronizedList(mutableListOf())
    )

    private val limiters = ConcurrentHashMap<String, RateLimiter>()

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val key = if (perNode) req.nodeId else "global"
        val limiter = limiters.computeIfAbsent(key) { RateLimiter() }

        val now = System.currentTimeMillis()
        val windowStart = now - 1000  // 1 second window

        // Clean old requests
        limiter.requests.removeIf { it < windowStart }

        // Check rate limit
        if (limiter.requests.size >= maxRequestsPerSecond) {
            return SpiceResult.failure(
                SpiceError.validationError("Rate limit exceeded for ${req.nodeId}")
            )
        }

        // Add current request
        limiter.requests.add(now)

        return next(req)
    }
}
```

### Pattern 5: Middleware Composition

Combine multiple middleware into one:

```kotlin
class CompositeMiddleware(
    private val middleware: List<Middleware>
) : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        executeChain(middleware, 0, ctx, next)
    }

    private suspend fun executeChain(
        chain: List<Middleware>,
        index: Int,
        ctx: RunContext,
        next: suspend () -> Unit
    ) {
        if (index >= chain.size) {
            next()
        } else {
            chain[index].onStart(ctx) {
                executeChain(chain, index + 1, ctx, next)
            }
        }
    }

    // Similar implementation for onNode, onError, onFinish...
}

// Usage
val graph = graph("my-graph") {
    middleware(CompositeMiddleware(listOf(
        LoggingMiddleware(),
        MetricsMiddleware(),
        CachingMiddleware()
    )))
}
```

### Pattern 6: Timeout Enforcement

```kotlin
class TimeoutMiddleware(
    val defaultTimeoutMs: Long = 30000,
    val timeoutPerNode: Map<String, Long> = emptyMap()
) : Middleware {
    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val timeout = timeoutPerNode[req.nodeId] ?: defaultTimeoutMs

        return try {
            withTimeout(timeout) {
                next(req)
            }
        } catch (e: TimeoutCancellationException) {
            SpiceResult.failure(
                SpiceError.validationError("Node ${req.nodeId} timed out after ${timeout}ms")
            )
        }
    }
}
```

## Real-World Use Cases

### Use Case 1: Production Monitoring Stack

```kotlin
val graph = graph("production-workflow") {
    // Full observability stack
    middleware(LoggingMiddleware())                    // Structured logging
    middleware(MetricsMiddleware())                     // Performance metrics
    middleware(TracingMiddleware())                     // Distributed tracing
    middleware(SmartRetryMiddleware())                  // Retry with backoff
    middleware(TimeoutMiddleware(defaultTimeoutMs = 10000))  // Prevent hanging

    agent("data-fetch", dataFetchAgent)
    agent("process", processAgent)
    agent("notify", notifyAgent)
}
```

### Use Case 2: Secure Multi-Tenant Application

```kotlin
val graph = graph("tenant-workflow") {
    // Security and isolation
    middleware(AuthMiddleware(authProvider))            // Verify JWT token
    middleware(TenantIsolationMiddleware())             // Ensure tenant isolation
    middleware(AuditMiddleware(auditLogger))            // Log all actions

    // Performance
    middleware(CachingMiddleware(ttlMs = 300000))       // 5-minute cache
    middleware(RateLimitMiddleware(maxRequestsPerSecond = 50))

    // Standard monitoring
    middleware(LoggingMiddleware())
    middleware(MetricsMiddleware())
}
```

### Use Case 3: Cost-Optimized Workflow

```kotlin
val graph = graph("cost-optimized-workflow") {
    // Aggressive caching to reduce API calls
    middleware(CachingMiddleware(ttlMs = 3600000))  // 1-hour cache

    // Rate limiting to control costs
    middleware(RateLimitMiddleware(maxRequestsPerSecond = 5))

    // Only apply expensive operations conditionally
    middleware(ConditionalMiddleware(
        condition = { req -> !req.nodeId.startsWith("cheap-") },
        wrapped = ExpensiveOperationMiddleware()
    ))

    // Metrics to track cost
    middleware(CostTrackingMiddleware())
}
```

### Use Case 4: Development & Testing

```kotlin
val graph = graph("dev-workflow") {
    // Verbose logging for debugging
    middleware(LoggingMiddleware())

    // Detailed metrics
    middleware(MetricsMiddleware())

    // Mock external services in test environment
    middleware(ConditionalMiddleware(
        condition = { _ -> System.getenv("ENV") == "test" },
        wrapped = MockMiddleware()
    ))

    // Inject delays for testing
    middleware(DelayMiddleware(delayMs = 100))
}
```

## Troubleshooting

### Issue 1: Middleware Not Executing

**Symptom:** Middleware hooks not being called.

**Causes & Solutions:**

1. **Middleware not added to graph:**
   ```kotlin
   // ❌ Wrong
   val middleware = LoggingMiddleware()
   val graph = graph("my-graph") { /* no middleware() call */ }

   // ✅ Correct
   val graph = graph("my-graph") {
       middleware(LoggingMiddleware())
   }
   ```

2. **Forgot to call `next()`:**
   ```kotlin
   // ❌ Wrong - chain broken
   override suspend fun onNode(req, next) {
       println("Before")
       // Missing next(req)!
       return SpiceResult.success(NodeResult.create(null))
   }

   // ✅ Correct
   override suspend fun onNode(req, next) {
       println("Before")
       return next(req)
   }
   ```

### Issue 2: Middleware Execution Order Confusion

**Symptom:** Middleware executing in unexpected order.

**Solution:** Remember the chain pattern:

```
onStart:  A → B → C → [Execution] → C → B → A (reversed in onFinish)
onNode:   A → B → C → [Node] → C → B → A
```

Middleware execute in order for `onStart` and `onNode` (before), but in **reverse order** for `onFinish`.

### Issue 3: State Not Shared Between Hooks

**Symptom:** Data stored in `onStart` not available in `onFinish`.

**Solution:** Use `RunContext.metadata`:

```kotlin
class MyMiddleware : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        ctx.metadata["startTime"] = System.currentTimeMillis()
        next()
    }

    override suspend fun onFinish(report: RunReport) {
        val startTime = report.context?.metadata?.get("startTime") as? Long
        println("Total duration: ${System.currentTimeMillis() - (startTime ?: 0)}ms")
    }
}
```

### Issue 4: Thread Safety Issues

**Symptom:** `ConcurrentModificationException` or race conditions.

**Solution:** Use thread-safe collections:

```kotlin
// ❌ Not thread-safe
private val metrics = mutableMapOf<String, Long>()

// ✅ Thread-safe
private val metrics = ConcurrentHashMap<String, Long>()
```

### Issue 5: Memory Leaks in Long-Running Applications

**Symptom:** Memory usage grows over time.

**Causes & Solutions:**

1. **Unbounded caches:**
   ```kotlin
   // ❌ Cache grows forever
   private val cache = ConcurrentHashMap<String, CacheEntry>()

   // ✅ Implement eviction policy
   private val cache = object : LinkedHashMap<String, CacheEntry>(100, 0.75f, true) {
       override fun removeEldestEntry(eldest: Map.Entry<String, CacheEntry>) = size > 100
   }
   ```

2. **Storing large objects in context:**
   ```kotlin
   // ❌ Storing large data
   ctx.metadata["largeData"] = hugeByteArray

   // ✅ Store references instead
   ctx.metadata["dataId"] = dataReference
   ```

### Issue 6: ErrorAction Not Working

**Symptom:** `ErrorAction.RETRY` or `SKIP` not working as expected.

**Common Issues:**

1. **Multiple middleware returning different actions:**
   ```kotlin
   // First non-PROPAGATE action wins
   // If middleware1 returns RETRY, middleware2's SKIP is ignored
   ```

2. **Retry limit reached:**
   ```kotlin
   // ErrorAction.RETRY only retries up to 3 times by default
   // After that, error propagates
   ```

3. **Wrong error type:**
   ```kotlin
   // Make sure you're handling the right error type
   override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
       return when (err) {
           is IOException -> ErrorAction.RETRY
           is ValidationException -> ErrorAction.SKIP
           else -> ErrorAction.PROPAGATE
       }
   }
   ```

## Performance Considerations

### 1. Middleware Overhead

Each middleware adds latency to node execution:

**Typical Overhead:**
- Empty middleware (just `next()`): ~0.1ms
- Logging middleware: ~0.5-1ms
- Metrics middleware: ~0.2-0.5ms
- Heavy middleware (tracing, etc.): ~2-5ms

**Best Practices:**
- Keep middleware logic lightweight
- Use async operations for I/O
- Cache expensive computations
- Profile middleware in production

### 2. Memory Usage

**High Memory Scenarios:**
- Storing all node results in middleware
- Unbounded caches
- Large trace/log buffers

**Solutions:**
```kotlin
// ❌ Stores all results (memory grows)
private val allResults = mutableListOf<NodeResult>()

// ✅ Store summaries only
private val resultSummaries = mutableListOf<ResultSummary>()

// ✅ Use bounded collections
private val recentResults = ArrayDeque<NodeResult>(maxSize = 100)
```

### 3. Concurrency

**Thread Safety:**
- Multiple graphs can execute concurrently
- Middleware instances are **shared** across executions
- Always use thread-safe collections

**Example:**
```kotlin
class MetricsMiddleware : Middleware {
    // ✅ Thread-safe
    private val metrics = ConcurrentHashMap<String, AtomicLong>()

    // ❌ NOT thread-safe
    // private val metrics = mutableMapOf<String, Long>()
}
```

### 4. I/O Operations

**Blocking I/O in Middleware:**
```kotlin
// ❌ Blocks the entire execution
override suspend fun onNode(req, next) {
    database.write(req.nodeId)  // Blocks!
    return next(req)
}

// ✅ Use async operations
override suspend fun onNode(req, next) {
    launch {
        database.writeAsync(req.nodeId)
    }
    return next(req)
}

// ✅ Or use buffering
override suspend fun onNode(req, next) {
    logBuffer.add(req.nodeId)  // Fast
    return next(req)
}
```

### 5. Conditional Execution

Apply middleware selectively to reduce overhead:

```kotlin
// ❌ All middleware for all nodes
middleware(ExpensiveMiddleware())

// ✅ Only for specific nodes
middleware(ConditionalMiddleware(
    condition = { req -> req.nodeId in criticalNodes },
    wrapped = ExpensiveMiddleware()
))
```

### 6. Metrics Collection Best Practices

```kotlin
class OptimizedMetricsMiddleware : Middleware {
    // Use primitive collections to reduce GC pressure
    private val executionTimes = LongArray(1000)
    private var index = 0

    // Sample metrics instead of recording everything
    override suspend fun onNode(req, next) {
        val shouldSample = Random.nextDouble() < 0.1  // 10% sampling
        val startTime = if (shouldSample) System.nanoTime() else 0

        val result = next(req)

        if (shouldSample && startTime > 0) {
            recordMetric(System.nanoTime() - startTime)
        }

        return result
    }
}
```

## Best Practices

### ✅ Do's

1. **Keep middleware focused** - One responsibility per middleware
2. **Chain properly** - Always call `next()` unless intentionally stopping execution
3. **Handle errors** - Catch exceptions in middleware to prevent cascading failures
4. **Use correlation IDs** - For distributed tracing across services
5. **Make middleware reusable** - Design for multiple graphs
6. **Use thread-safe collections** - For concurrent graph executions
7. **Implement cleanup** - Release resources in `onFinish`
8. **Profile in production** - Monitor middleware overhead
9. **Use sampling** - For high-frequency metrics collection
10. **Document side effects** - Make middleware behavior explicit

### ❌ Don'ts

1. **Don't block execution** - Async operations should use coroutines
2. **Don't mutate requests** - Unless that's the explicit purpose
3. **Don't skip calling next()** - Unless you have a good reason
4. **Don't store mutable state** - Use thread-safe collections
5. **Don't throw exceptions** - Return ErrorAction instead
6. **Don't store unbounded data** - Implement eviction policies
7. **Don't do heavy I/O** - Use async or buffering
8. **Don't forget error handling** - Middleware can fail too
9. **Don't leak resources** - Clean up in `onFinish`
10. **Don't apply all middleware everywhere** - Use conditional execution

## Next Steps

- Learn about [Checkpoint & Resume](./graph-checkpoint.md)
- Understand [Graph Validation](./graph-validation.md)
- Review [Error Handling](../error-handling/overview.md)

## Related

- [Graph System Overview](./graph-system.md)
- [Graph Nodes](./graph-nodes.md)
- [Advanced Context Propagation](../advanced/context-propagation.md)
