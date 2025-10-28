---
sidebar_position: 3
---

# Graph Design Patterns

Common patterns and best practices for building production-ready Graph workflows.

## Error Handling Patterns

### Pattern 1: Retry on Transient Errors

**When to use:** Network failures, timeouts, temporary service unavailability

```kotlin
class TransientErrorMiddleware : Middleware {
    override suspend fun onError(
        err: Throwable,
        ctx: RunContext
    ): ErrorAction {
        return when (err) {
            is SocketTimeoutException,
            is ConnectException,
            is IOException -> {
                log.warn("Transient error detected, retrying: ${err.message}")
                ErrorAction.RETRY
            }
            is HttpException -> {
                if (err.code in 500..599) {
                    log.warn("Server error, retrying: ${err.code}")
                    ErrorAction.RETRY
                } else {
                    ErrorAction.PROPAGATE
                }
            }
            else -> ErrorAction.PROPAGATE
        }
    }
}
```

**Use cases:**
- API calls that might timeout
- Database connection issues
- Temporary service outages

### Pattern 2: Skip Optional Steps

**When to use:** Non-critical operations that can fail gracefully

```kotlin
val graph = graph("optional-enrichment") {
    agent("core-processor", coreAgent)

    // Optional enrichment - can skip if fails
    agent("enrichment", enrichmentAgent)

    agent("final-processor", finalAgent)

    output("result") { it.state["final-processor"] }

    middleware(object : Middleware {
        override suspend fun onError(
            err: Throwable,
            ctx: RunContext
        ): ErrorAction {
            // Check if error is from optional enrichment node
            return if (err.message?.contains("enrichment") == true) {
                log.info("Enrichment failed, skipping")
                ErrorAction.SKIP
            } else {
                ErrorAction.PROPAGATE
            }
        }
    })
}
```

**Use cases:**
- Analytics/telemetry operations
- Optional data enrichment
- Non-critical notifications

### Pattern 3: Fallback Values

**When to use:** Need to continue with default values on failure

```kotlin
class FallbackMiddleware : Middleware {
    override suspend fun onError(
        err: Throwable,
        ctx: RunContext
    ): ErrorAction {
        return when {
            err.message?.contains("recommendation") == true -> {
                // Use default recommendations on failure
                log.info("Recommendation service failed, using defaults")
                ErrorAction.CONTINUE(
                    result = listOf("default-item-1", "default-item-2")
                )
            }
            err.message?.contains("translation") == true -> {
                // Fall back to original language
                ErrorAction.CONTINUE(
                    result = ctx.metadata["original_text"]
                )
            }
            else -> ErrorAction.PROPAGATE
        }
    }
}
```

**Use cases:**
- Recommendation systems (fall back to defaults)
- Translation services (use original text)
- Personalization (use generic content)

### Pattern 4: Circuit Breaker

**When to use:** Protect against cascading failures

```kotlin
class CircuitBreakerMiddleware : Middleware {
    private val failureCount = AtomicInteger(0)
    private val threshold = 5
    private val resetTime = Duration.ofMinutes(1)
    private var lastFailureTime: Instant? = null

    override suspend fun onError(
        err: Throwable,
        ctx: RunContext
    ): ErrorAction {
        val failures = failureCount.incrementAndGet()
        lastFailureTime = Instant.now()

        return if (failures >= threshold) {
            log.error("Circuit breaker opened after $failures failures")
            ErrorAction.SKIP  // Skip all nodes until reset
        } else {
            ErrorAction.RETRY
        }
    }

    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        // Reset circuit breaker if enough time has passed
        lastFailureTime?.let { lastFailure ->
            if (Duration.between(lastFailure, Instant.now()) > resetTime) {
                failureCount.set(0)
                log.info("Circuit breaker reset")
            }
        }
        next()
    }
}
```

**Use cases:**
- External API calls
- Database operations
- Microservice communication

## ErrorAction Decision Tree

```
Is the error recoverable?
├─ Yes
│  └─ Can we retry immediately?
│     ├─ Yes → ErrorAction.RETRY
│     └─ No → ErrorAction.SKIP or CONTINUE with fallback
└─ No
   └─ Is this step optional?
      ├─ Yes → ErrorAction.SKIP
      └─ No → ErrorAction.PROPAGATE
```

**Quick Reference:**

| Scenario | ErrorAction | Example |
|----------|-------------|---------|
| Network timeout | `RETRY` | API call failed |
| Service unavailable (5xx) | `RETRY` | Backend service down |
| Invalid input (4xx) | `PROPAGATE` | Bad request data |
| Optional analytics failed | `SKIP` | Tracking pixel error |
| Recommendation service down | `CONTINUE(defaults)` | Use default products |
| Critical validation failed | `PROPAGATE` | Business rule violation |

## Workflow Patterns

### Pattern 1: Linear Pipeline

**Use case:** Sequential processing with clear dependencies

```kotlin
val pipeline = graph("data-pipeline") {
    agent("extract", extractorAgent)
    agent("transform", transformerAgent)
    agent("validate", validatorAgent)
    agent("load", loaderAgent)

    output("result") { it.state["load"] }

    middleware(LoggingMiddleware())
    middleware(MetricsMiddleware())
}
```

**Characteristics:**
- Simple to understand
- Clear execution order
- Easy to debug

### Pattern 2: Conditional Branching

**Use case:** Different paths based on input or intermediate results

```kotlin
val workflow = graph("conditional-routing") {
    agent("classifier", classifierAgent)

    // Route A: High confidence
    agent("auto-handler", autoHandlerAgent)

    // Route B: Low confidence
    agent("manual-handler", manualHandlerAgent)

    output("result") { it.state["_previous"] }

    edge("classifier", "auto-handler") { result ->
        (result.data as? Classification)?.confidence ?: 0.0 > 0.8
    }

    edge("classifier", "manual-handler") { result ->
        (result.data as? Classification)?.confidence ?: 0.0 <= 0.8
    }

    edge("auto-handler", "result")
    edge("manual-handler", "result")
}
```

**Characteristics:**
- Dynamic routing
- Different processing paths
- Converges to single output

### Pattern 3: Parallel Fan-Out / Fan-In

**Use case:** Execute multiple independent operations, then combine results

```kotlin
val workflow = graph("parallel-processing") {
    agent("splitter", splitterAgent)

    // Parallel branches
    agent("process-a", processorA)
    agent("process-b", processorB)
    agent("process-c", processorC)

    // Aggregator
    agent("aggregator", aggregatorAgent)

    output("result") { it.state["aggregator"] }

    // Fan-out
    edge("splitter", "process-a")
    edge("splitter", "process-b")
    edge("splitter", "process-c")

    // Fan-in (all must complete before aggregator)
    edge("process-a", "aggregator") { result ->
        // Check if all parallel branches completed
        val ctx = result.metadata["context"] as? NodeContext
        ctx?.state?.containsKey("process-b") == true &&
        ctx?.state?.containsKey("process-c") == true
    }
}
```

**Note:** Current Graph system executes sequentially. For true parallelism, use custom node:

```kotlin
class ParallelNode(
    override val id: String,
    private val agents: List<Agent>
) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        return SpiceResult.catching {
            // Execute agents in parallel
            val results = agents.map { agent ->
                async {
                    agent.processComm(
                        Comm(content = ctx.state["input"].toString())
                    )
                }
            }.awaitAll()

            NodeResult(
                data = results,
                metadata = ctx.metadata  // 🔥 Always preserve metadata!
            )
        }
    }
}
```

### Pattern 4: Loop/Iteration

**Use case:** Retry until condition met or max iterations reached

```kotlin
val workflow = graph("iterative-refinement") {
    agent("generator", generatorAgent)
    agent("validator", validatorAgent)
    agent("refiner", refinerAgent)

    output("result") { ctx ->
        if (ctx.state.containsKey("_iteration_count") &&
            ctx.state["_iteration_count"] as Int >= 3) {
            // Max iterations reached, return best attempt
            ctx.state["generator"]
        } else {
            ctx.state["_previous"]
        }
    }

    // Loop back if validation fails
    edge("validator", "refiner") { result ->
        val isValid = result.data as? Boolean ?: false
        !isValid
    }

    edge("refiner", "generator") { result ->
        // Increment iteration count
        true
    }

    // Exit loop if validation passes
    edge("validator", "result") { result ->
        val isValid = result.data as? Boolean ?: false
        isValid
    }
}
```

**Better approach with custom node:**

```kotlin
class IterativeNode(
    override val id: String,
    private val generator: Agent,
    private val validator: (Any?) -> Boolean,
    private val maxIterations: Int = 3
) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        return SpiceResult.catching {
            var result: Any? = null
            var iteration = 0

            while (iteration < maxIterations) {
                val generated = generator.processComm(
                    Comm(content = ctx.state["input"].toString())
                ).getOrThrow()

                result = generated.content

                if (validator(result)) {
                    break
                }

                iteration++
            }

            NodeResult(
                data = result,
                metadata = ctx.metadata + mapOf("iterations" to iteration)  // 🔥 Preserve existing metadata!
            )
        }
    }
}
```

### Pattern 5: Human-in-the-Loop (HITL)

**Use case:** Require human approval or input during workflow

```kotlin
val approvalFlow = graph("approval-workflow") {
    agent("draft", draftAgent)

    humanNode(
        id = "review",
        prompt = "Please review the draft",
        options = listOf(
            HumanOption("approve", "Approve"),
            HumanOption("reject", "Reject"),
            HumanOption("revise", "Request Changes")
        ),
        timeout = Duration.ofHours(24),
        validator = { response ->
            response.selectedOption in listOf("approve", "reject", "revise")
        }
    )

    agent("publisher", publisherAgent)
    agent("revisor", revisorAgent)

    output("result") { it.state["_previous"] }

    edge("draft", "review")

    edge("review", "publisher") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }

    edge("review", "revisor") { result ->
        (result.data as? HumanResponse)?.selectedOption == "revise"
    }

    edge("revisor", "draft")  // Loop back for revision
}
```

**With checkpoint management:**

```kotlin
@Service
class ApprovalService(
    private val approvalFlow: Graph,
    private val graphRunner: DefaultGraphRunner,
    private val checkpointStore: CheckpointStore
) {
    suspend fun startApproval(draft: Draft): String {
        val report = graphRunner.runWithCheckpoint(
            graph = approvalFlow,
            input = mapOf("draft" to draft),
            store = checkpointStore
        ).getOrThrow()

        return when (report.status) {
            RunStatus.PAUSED -> {
                // Return checkpoint ID to user
                report.checkpointId!!
            }
            else -> throw IllegalStateException("Expected PAUSED status")
        }
    }

    suspend fun submitReview(
        checkpointId: String,
        decision: String,
        comments: String?
    ): ApprovalResult {
        val response = HumanResponse(
            nodeId = "review",
            selectedOption = decision,
            text = comments
        )

        val report = graphRunner.resumeWithHumanResponse(
            graph = approvalFlow,
            checkpointId = checkpointId,
            response = response,
            store = checkpointStore
        ).getOrThrow()

        return ApprovalResult(
            status = report.status,
            result = report.result
        )
    }
}
```

## State Management Patterns

### Pattern 1: Accumulator

**Use case:** Collect results from multiple nodes

```kotlin
class AccumulatorNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        return SpiceResult.catching {
            // Get or create accumulator
            val accumulated = ctx.state.getOrPut("_accumulated") {
                mutableListOf<Any>()
            } as MutableList<Any>

            // Add current result
            ctx.state["_previous"]?.let { accumulated.add(it) }

            NodeResult(
                data = accumulated.toList(),
                metadata = ctx.metadata  // 🔥 Always preserve metadata!
            )
        }
    }
}
```

### Pattern 2: Context Enrichment

**Use case:** Add metadata that flows through workflow

```kotlin
class EnrichmentMiddleware : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        // Enrich context at start
        ctx.metadata["startTime"] = System.currentTimeMillis()
        ctx.metadata["requestId"] = UUID.randomUUID().toString()
        ctx.metadata["environment"] = System.getenv("ENV") ?: "dev"

        next()
    }

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        // Add node-specific metadata
        val enrichedReq = req.copy(
            context = req.context.copy(
                metadata = req.context.metadata.apply {
                    put("nodeStartTime", System.currentTimeMillis())
                }
            )
        )

        return next(enrichedReq)
    }
}
```

### Pattern 3: State Versioning

**Use case:** Track state changes for debugging/auditing

```kotlin
class VersionedStateNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        return SpiceResult.catching {
            // Get version history
            val history = ctx.state.getOrPut("_state_history") {
                mutableListOf<StateSnapshot>()
            } as MutableList<StateSnapshot>

            // Save current state snapshot
            history.add(
                StateSnapshot(
                    nodeId = id,
                    timestamp = Instant.now(),
                    state = ctx.state.toMap()
                )
            )

            // Process normally
            val result = processData(ctx.state["input"])

            NodeResult(
                data = result,
                metadata = ctx.metadata  // 🔥 Always preserve metadata!
            )
        }
    }
}

data class StateSnapshot(
    val nodeId: String,
    val timestamp: Instant,
    val state: Map<String, Any?>
)
```

## Observability Patterns

### Pattern 1: Distributed Tracing

```kotlin
class TracingMiddleware(
    private val tracer: Tracer
) : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        val span = tracer.buildSpan("graph-execution")
            .withTag("graph.id", ctx.graphId)
            .withTag("run.id", ctx.runId)
            .withTag("tenant.id", ctx.agentContext?.tenantId ?: "unknown")
            .start()

        ctx.metadata["trace-span"] = span

        try {
            tracer.scopeManager().activate(span).use {
                next()
            }
        } finally {
            span.finish()
        }
    }

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val parentSpan = req.context.metadata["trace-span"] as? Span

        val span = tracer.buildSpan("node-execution")
            .asChildOf(parentSpan)
            .withTag("node.id", req.nodeId)
            .start()

        return try {
            tracer.scopeManager().activate(span).use {
                val result = next(req)
                span.setTag("status", if (result.isSuccess) "success" else "failure")
                result
            }
        } finally {
            span.finish()
        }
    }
}
```

### Pattern 2: Structured Logging

```kotlin
class StructuredLoggingMiddleware : Middleware {
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val startTime = System.currentTimeMillis()

        MDC.put("graphId", req.context.graphId)
        MDC.put("runId", req.context.runId)
        MDC.put("nodeId", req.nodeId)
        MDC.put("tenantId", req.context.agentContext?.tenantId ?: "unknown")

        logger.info("Node execution started")

        return try {
            val result = next(req)
            val duration = System.currentTimeMillis() - startTime

            MDC.put("duration", duration.toString())
            MDC.put("status", if (result.isSuccess) "success" else "failure")

            logger.info("Node execution completed")

            result
        } finally {
            MDC.clear()
        }
    }
}
```

### Pattern 3: Metrics Collection

```kotlin
class MetricsMiddleware(
    private val meterRegistry: MeterRegistry
) : Middleware {
    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val timer = Timer.start(meterRegistry)

        return try {
            val result = next(req)

            timer.stop(
                Timer.builder("graph.node.duration")
                    .tag("nodeId", req.nodeId)
                    .tag("graphId", req.context.graphId)
                    .tag("status", if (result.isSuccess) "success" else "failure")
                    .register(meterRegistry)
            )

            // Count executions
            meterRegistry.counter(
                "graph.node.executions",
                "nodeId", req.nodeId,
                "status", if (result.isSuccess) "success" else "failure"
            ).increment()

            result
        } catch (e: Exception) {
            timer.stop(
                Timer.builder("graph.node.duration")
                    .tag("nodeId", req.nodeId)
                    .tag("status", "error")
                    .register(meterRegistry)
            )
            throw e
        }
    }
}
```

## Testing Patterns

### Pattern 1: Mock Agents

```kotlin
@Test
fun `test workflow with mock agents`() = runTest {
    val mockAgent = object : Agent {
        override val id = "mock"
        override val name = "Mock Agent"
        override val description = "Test agent"
        override val capabilities = emptyList<String>()

        override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
            return SpiceResult.success(
                comm.reply("mocked response", id)
            )
        }

        override fun canHandle(comm: Comm) = true
        override fun getTools() = emptyList<Tool>()
        override fun isReady() = true
    }

    val graph = graph("test") {
        agent("mock-agent", mockAgent)
        output("result") { it.state["mock-agent"] }
    }

    val runner = DefaultGraphRunner()
    val report = runner.run(graph, mapOf("input" to "test")).getOrThrow()

    assertEquals(RunStatus.SUCCESS, report.status)
}
```

### Pattern 2: Spy Middleware

```kotlin
@Test
fun `test execution order with spy middleware`() = runTest {
    val executionLog = mutableListOf<String>()

    val spyMiddleware = object : Middleware {
        override suspend fun onNode(
            req: NodeRequest,
            next: suspend (NodeRequest) -> SpiceResult<NodeResult>
        ): SpiceResult<NodeResult> {
            executionLog.add("before-${req.nodeId}")
            val result = next(req)
            executionLog.add("after-${req.nodeId}")
            return result
        }
    }

    val graph = graph("test") {
        middleware(spyMiddleware)
        agent("step1", agent1)
        agent("step2", agent2)
        output("result") { it.state["step2"] }
    }

    runner.run(graph, emptyMap())

    assertEquals(
        listOf("before-step1", "after-step1", "before-step2", "after-step2"),
        executionLog
    )
}
```

## Anti-Patterns (What NOT to Do)

### ❌ Anti-Pattern 1: Cyclic Graphs

```kotlin
// ❌ WRONG - Creates infinite loop
val badGraph = graph("cyclic") {
    agent("a", agentA)
    agent("b", agentB)

    edge("a", "b")
    edge("b", "a")  // Cycle!
}
```

**Solution:** Use iteration limit or condition to break cycle

```kotlin
// ✅ CORRECT
val goodGraph = graph("conditional") {
    agent("a", agentA)
    agent("b", agentB)
    output("result") { it.state["b"] }

    edge("a", "b")
    edge("b", "a") { result ->
        // Only loop if under limit
        val iterations = result.metadata["iterations"] as? Int ?: 0
        iterations < 3
    }
    edge("b", "result") { result ->
        val iterations = result.metadata["iterations"] as? Int ?: 0
        iterations >= 3
    }
}
```

### ❌ Anti-Pattern 2: Shared Mutable State

```kotlin
// ❌ WRONG - Shared mutable state
var sharedCounter = 0

val badAgent = object : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        sharedCounter++  // Race condition!
        return SpiceResult.success(comm)
    }
}
```

**Solution:** Use NodeContext state

```kotlin
// ✅ CORRECT
val goodAgent = object : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Use context state instead
        val counter = (comm.metadata["counter"] as? Int ?: 0) + 1
        return SpiceResult.success(
            comm.copy(metadata = comm.metadata + ("counter" to counter))
        )
    }
}
```

### ❌ Anti-Pattern 3: Ignoring Errors

```kotlin
// ❌ WRONG - Swallow all errors
val badMiddleware = object : Middleware {
    override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
        return ErrorAction.SKIP  // Always skip!
    }
}
```

**Solution:** Handle errors appropriately

```kotlin
// ✅ CORRECT
val goodMiddleware = object : Middleware {
    override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
        return when (err) {
            is OptionalOperationException -> ErrorAction.SKIP
            is RetryableException -> ErrorAction.RETRY
            else -> {
                log.error("Critical error", err)
                ErrorAction.PROPAGATE
            }
        }
    }
}
```

## Best Practices Checklist

### Design
- [ ] Use meaningful node IDs
- [ ] Keep nodes focused (single responsibility)
- [ ] Design for idempotency
- [ ] Validate graph structure before deployment
- [ ] Document complex routing logic

### Error Handling
- [ ] Choose appropriate ErrorAction for each error type
- [ ] Implement circuit breaker for external dependencies
- [ ] Log all errors with context
- [ ] Use fallback values where appropriate
- [ ] Set reasonable retry limits

### State Management
- [ ] Use NodeContext for shared state
- [ ] Avoid shared mutable state
- [ ] Clean up large objects from state
- [ ] Version critical state changes

### Observability
- [ ] Add structured logging
- [ ] Implement distributed tracing
- [ ] Collect metrics (duration, success rate)
- [ ] Add correlation IDs

### Testing
- [ ] Test each node independently
- [ ] Test error scenarios
- [ ] Test conditional routing
- [ ] Test with realistic data volumes
- [ ] Integration test full workflow

## Next Steps

- **[Migration Guide](./migration-0.4-to-0.5.md)** - Migrate from 0.4.4
- **[Spring Boot Integration](./graph-spring-boot.md)** - Production setup
- **[Troubleshooting](./troubleshooting.md)** - Common issues
- **[API Reference](../api/graph.md)** - Complete API docs
