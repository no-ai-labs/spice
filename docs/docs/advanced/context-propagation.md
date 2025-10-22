# Context Propagation

Complete guide to context propagation in distributed agent systems - ensuring data flows correctly across agents, tools, and async boundaries.

## Overview

Context propagation is **critical** for production systems. It enables:

- **Distributed tracing** - Track requests across multiple agents and services
- **Multi-tenancy** - Isolate data by tenant/customer
- **Security** - Propagate authentication and authorization
- **Observability** - Correlate logs, metrics, and traces
- **State management** - Maintain session and user context

In Spice Framework, context flows through three layers:

```
User Request
    ↓ (AgentContext)
SwarmAgent
    ↓ (propagates to)
Member Agents
    ↓ (ToolContext)
Tools
    ↓ (trace spans)
External Services
```

**Without proper context propagation:**
- ❌ Lost trace information
- ❌ Mixed tenant data
- ❌ Broken audit trails
- ❌ Security vulnerabilities
- ❌ Debugging nightmares

**With proper context propagation:**
- ✅ End-to-end tracing
- ✅ Perfect tenant isolation
- ✅ Complete audit logs
- ✅ Security compliance
- ✅ Easy debugging

## Context Types

### AgentContext

Runtime context for agent execution:

```kotlin
data class AgentContext(
    private val data: MutableMap<String, Any> = mutableMapOf()
) {
    operator fun get(key: String): Any?
    operator fun set(key: String, value: Any)
    fun <T> getAs(key: String): T?
    fun has(key: String): Boolean
    fun with(key: String, value: Any): AgentContext
    fun copy(): AgentContext
}
```

**Standard Keys:**

```kotlin
object ContextKeys {
    // Identity
    const val USER_ID = "userId"
    const val SESSION_ID = "sessionId"
    const val TENANT_ID = "tenantId"

    // Tracing
    const val TRACE_ID = "traceId"
    const val SPAN_ID = "spanId"
    const val CORRELATION_ID = "correlationId"
    const val REQUEST_ID = "requestId"

    // Localization
    const val LOCALE = "locale"
    const val TIMEZONE = "timezone"

    // Authorization
    const val PERMISSIONS = "permissions"
    const val ROLES = "roles"
    const val FEATURES = "features"

    // Metadata
    const val METADATA = "metadata"
    const val TAGS = "tags"
}
```

**Usage:**

```kotlin
val context = AgentContext.of(
    ContextKeys.USER_ID to "user-123",
    ContextKeys.TENANT_ID to "tenant-456",
    ContextKeys.TRACE_ID to "trace-789",
    ContextKeys.PERMISSIONS to listOf("read", "write")
)

// Access values
val userId = context.getAs<String>(ContextKeys.USER_ID)
val permissions = context.getAs<List<String>>(ContextKeys.PERMISSIONS)

// Add values
context[ContextKeys.CORRELATION_ID] = "corr-abc"

// Immutable update
val newContext = context.with(ContextKeys.SESSION_ID, "sess-xyz")
```

### ToolContext

Context for tool execution:

```kotlin
data class ToolContext(
    val agentId: String,
    val userId: String? = null,
    val tenantId: String? = null,
    val correlationId: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)
```

**Conversion from AgentContext:**

```kotlin
fun AgentContext.toToolContext(agentId: String): ToolContext {
    return ToolContext(
        agentId = agentId,
        userId = getAs<String>(ContextKeys.USER_ID),
        tenantId = getAs<String>(ContextKeys.TENANT_ID),
        correlationId = getAs<String>(ContextKeys.CORRELATION_ID),
        metadata = getAs<Map<String, Any>>(ContextKeys.METADATA) ?: emptyMap()
    )
}
```

### AgentRuntime

Complete runtime environment:

```kotlin
interface AgentRuntime {
    val context: AgentContext
    val scope: CoroutineScope

    suspend fun callAgent(agentId: String, comm: Comm): SpiceResult<Comm>
    suspend fun publishEvent(event: AgentEvent)
    suspend fun saveState(key: String, value: Any)
    suspend fun getState(key: String): Any?
    fun log(level: LogLevel, message: String, data: Map<String, Any> = emptyMap())
}
```

## Propagation Mechanisms

### Agent → Tool

Context automatically propagates from agent to tool:

```kotlin
class MyAgent : BaseAgent(...) {
    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        // Runtime has AgentContext
        val context = runtime.context

        // Convert to ToolContext for tool execution
        val toolContext = ToolContext(
            agentId = id,
            userId = context.getAs(ContextKeys.USER_ID),
            tenantId = context.getAs(ContextKeys.TENANT_ID),
            correlationId = context.getAs(ContextKeys.CORRELATION_ID)
        )

        // Execute tool with context
        val result = executeTool("my_tool", params, toolContext)

        return result.fold(
            onSuccess = { /* ... */ },
            onFailure = { /* ... */ }
        )
    }
}
```

**Automatic propagation in built-in agents:**

```kotlin
// BaseAgent automatically propagates context
class MyTool : BaseTool() {
    override suspend fun execute(
        parameters: Map<String, Any>,
        context: ToolContext  // ← Automatically receives context
    ): SpiceResult<ToolResult> {
        // Access tenant ID from context
        val tenantId = context.tenantId
            ?: return SpiceResult.success(ToolResult.error("Tenant ID required"))

        // Query tenant-specific data
        val data = database.query(tenantId, parameters)

        return SpiceResult.success(ToolResult.success(data))
    }
}
```

### Agent → Agent (Swarm)

Context propagates between agents in a swarm:

```kotlin
val swarm = buildSwarmAgent {
    name = "Processing Team"

    members {
        agent(agent1)
        agent(agent2)
        agent(agent3)
    }
}

// Create runtime with context
val runtime = DefaultAgentRuntime(
    context = AgentContext.of(
        ContextKeys.USER_ID to "user-123",
        ContextKeys.TENANT_ID to "tenant-456",
        ContextKeys.TRACE_ID to "trace-789"
    )
)

// Context automatically propagates to all member agents
swarm.processComm(comm, runtime)
```

**Swarm propagation flow:**

```
SwarmAgent.processComm(comm, runtime)
    ↓
runtime.context contains:
  - userId: "user-123"
  - tenantId: "tenant-456"
  - traceId: "trace-789"
    ↓
Coordinator selects strategy (PARALLEL, SEQUENTIAL, etc.)
    ↓
For each member agent:
  memberAgent.processComm(comm, runtime)  ← Same runtime!
    ↓
Member agent receives full context:
  - Can access runtime.context
  - Can use context for tool execution
  - Can log with context
```

**Implementation in SwarmAgent:**

```kotlin
// SwarmAgent automatically propagates runtime to all members
class SwarmAgent(...) : BaseAgent(...) {
    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        // Select strategy
        val strategy = selectStrategy(comm)

        // Execute with strategy - runtime propagates to all members
        return when (strategy) {
            SwarmStrategyType.PARALLEL -> executeParallel(comm, runtime)
            SwarmStrategyType.SEQUENTIAL -> executeSequential(comm, runtime)
            SwarmStrategyType.CONSENSUS -> executeConsensus(comm, runtime)
            // ... all receive the same runtime with context
        }
    }

    private suspend fun executeParallel(
        comm: Comm,
        runtime: AgentRuntime  // ← Context here
    ): SpiceResult<Comm> {
        val results = memberAgents.values.map { agent ->
            async {
                // Each member gets the same runtime with context
                agent.processComm(comm, runtime)  // ← Propagated here
            }
        }.awaitAll()

        // Aggregate results
        return aggregateResults(results)
    }
}
```

### Async Boundaries (Coroutines)

Context must be preserved across coroutine boundaries:

```kotlin
// ❌ BAD - Context lost in new coroutine
class BrokenAgent : BaseAgent(...) {
    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        val context = runtime.context

        // Context NOT automatically propagated to new coroutine
        GlobalScope.launch {
            // ❌ runtime.context not accessible here!
            val userId = context.getAs<String>(ContextKeys.USER_ID)  // Still works
            // But new coroutine context elements are lost
        }

        return SpiceResult.success(comm)
    }
}

// ✅ GOOD - Context preserved with proper scope
class WorkingAgent : BaseAgent(...) {
    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        // Use runtime.scope to preserve context
        runtime.scope.launch {
            // ✅ Context accessible through runtime
            val userId = runtime.context.getAs<String>(ContextKeys.USER_ID)

            // Perform async operation
            performBackgroundTask(userId)
        }

        return SpiceResult.success(comm)
    }
}
```

**Coroutine Context Elements:**

```kotlin
// Custom coroutine context element for tenant ID
class TenantIdContext(val tenantId: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TenantIdContext>
}

// Propagate through coroutine context
suspend fun processWithTenantContext(tenantId: String) {
    withContext(TenantIdContext(tenantId)) {
        // tenantId available in all child coroutines
        launch {
            val tenant = coroutineContext[TenantIdContext]?.tenantId
            queryTenantData(tenant!!)
        }
    }
}
```

### Thread Pools

Context must be explicitly propagated across threads:

```kotlin
class ThreadPoolAgent : BaseAgent(...) {
    private val executor = Executors.newFixedThreadPool(10)

    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> = withContext(Dispatchers.IO) {
        // Capture context before thread switch
        val context = runtime.context.copy()

        val future = CompletableFuture.supplyAsync({
            // ✅ Use captured context in thread pool
            val userId = context.getAs<String>(ContextKeys.USER_ID)

            // Log with context
            logger.info("Processing for user: $userId")

            // Perform work
            heavyComputation(userId)
        }, executor)

        val result = future.await()

        SpiceResult.success(comm.reply(
            content = result,
            from = id
        ))
    }
}
```

## Distributed Tracing

### OpenTelemetry Integration

Propagate trace context across agents and services:

```kotlin
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context

class TracedAgent : BaseAgent(...) {
    private val tracer = GlobalOpenTelemetry.getTracer("spice-framework")

    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        // Extract trace context from runtime
        val traceId = runtime.context.getAs<String>(ContextKeys.TRACE_ID)
        val parentSpanId = runtime.context.getAs<String>(ContextKeys.SPAN_ID)

        // Create span for this agent
        val span = tracer.spanBuilder("agent.process")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("agent.id", id)
            .setAttribute("agent.name", name)
            .setAttribute("comm.from", comm.from)
            .setAttribute("comm.type", comm.type.name)
            .apply {
                if (traceId != null) {
                    setAttribute("trace.id", traceId)
                }
                if (parentSpanId != null) {
                    setAttribute("parent.span.id", parentSpanId)
                }
            }
            .startSpan()

        return try {
            // Make span current
            Context.current().with(span).makeCurrent().use {
                // Process with tracing
                val result = processInternal(comm, runtime)

                // Set span status
                span.setStatus(StatusCode.OK)

                // Propagate span ID to runtime for child operations
                runtime.context[ContextKeys.SPAN_ID] = span.spanContext.spanId

                result
            }
        } catch (e: Exception) {
            span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
            span.recordException(e)
            SpiceResult.failure(SpiceError.from(e))
        } finally {
            span.end()
        }
    }
}
```

### Trace Propagation in Swarms

```kotlin
class TracedSwarmAgent(...) : SwarmAgent(...) {
    private val tracer = GlobalOpenTelemetry.getTracer("spice-swarm")

    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        // Create parent span for swarm operation
        val swarmSpan = tracer.spanBuilder("swarm.coordinate")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("swarm.name", name)
            .setAttribute("swarm.strategy", strategy.name)
            .setAttribute("swarm.member_count", memberAgents.size)
            .startSpan()

        return try {
            Context.current().with(swarmSpan).makeCurrent().use {
                // Propagate trace context to all members
                val traceContext = runtime.context.copy().apply {
                    this[ContextKeys.TRACE_ID] = swarmSpan.spanContext.traceId
                    this[ContextKeys.SPAN_ID] = swarmSpan.spanContext.spanId
                }

                val tracedRuntime = DefaultAgentRuntime(
                    context = traceContext,
                    scope = runtime.scope
                )

                // Execute with traced runtime
                val results = memberAgents.values.map { member ->
                    async {
                        // Each member creates child span automatically
                        member.processComm(comm, tracedRuntime)
                    }
                }.awaitAll()

                swarmSpan.setStatus(StatusCode.OK)
                swarmSpan.setAttribute("swarm.results_count", results.size)

                aggregateResults(results)
            }
        } catch (e: Exception) {
            swarmSpan.setStatus(StatusCode.ERROR, e.message ?: "")
            swarmSpan.recordException(e)
            SpiceResult.failure(SpiceError.from(e))
        } finally {
            swarmSpan.end()
        }
    }
}
```

### Tool Tracing

```kotlin
class TracedTool : BaseTool() {
    private val tracer = GlobalOpenTelemetry.getTracer("spice-tools")

    override suspend fun execute(
        parameters: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        val span = tracer.spanBuilder("tool.execute")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("tool.name", name)
            .setAttribute("tool.agent_id", context.agentId)
            .setAttribute("tool.tenant_id", context.tenantId ?: "")
            .setAttribute("tool.correlation_id", context.correlationId ?: "")
            .startSpan()

        return try {
            Context.current().with(span).makeCurrent().use {
                val result = performExecution(parameters)

                span.setStatus(StatusCode.OK)
                span.setAttribute("tool.result_length", result.length)

                SpiceResult.success(ToolResult.success(result))
            }
        } catch (e: Exception) {
            span.setStatus(StatusCode.ERROR, e.message ?: "")
            span.recordException(e)
            SpiceResult.success(ToolResult.error(e.message ?: "Execution failed"))
        } finally {
            span.end()
        }
    }
}
```

## Multi-Tenancy

### Tenant Isolation

Ensure complete tenant data isolation:

```kotlin
class MultiTenantAgent : BaseAgent(...) {
    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        // Extract tenant ID from context
        val tenantId = runtime.context.getAs<String>(ContextKeys.TENANT_ID)
            ?: return SpiceResult.failure(SpiceError(
                message = "Tenant ID required",
                code = "TENANT_MISSING"
            ))

        // Validate tenant permissions
        if (!hasPermission(tenantId, comm)) {
            return SpiceResult.failure(SpiceError(
                message = "Insufficient permissions for tenant: $tenantId",
                code = "PERMISSION_DENIED"
            ))
        }

        // All operations scoped to tenant
        val result = withTenantScope(tenantId) {
            processInternal(comm)
        }

        return result
    }

    private suspend fun <T> withTenantScope(
        tenantId: String,
        block: suspend () -> T
    ): T {
        // Set tenant context for all database operations
        TenantContext.set(tenantId)
        try {
            return block()
        } finally {
            TenantContext.clear()
        }
    }
}
```

### Tenant Context in Tools

```kotlin
class TenantAwareTool : BaseTool() {
    override suspend fun execute(
        parameters: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        val tenantId = context.tenantId
            ?: return SpiceResult.success(ToolResult.error(
                "Tenant ID required for this operation"
            ))

        // Query with tenant scope
        val data = database.query(
            sql = "SELECT * FROM users WHERE tenant_id = ?",
            params = listOf(tenantId)
        )

        return SpiceResult.success(ToolResult.success(
            result = data.toString(),
            metadata = mapOf(
                "tenant_id" to tenantId,
                "record_count" to data.size.toString()
            )
        ))
    }
}
```

### Tenant-Specific Rate Limiting

```kotlin
class RateLimitedAgent : BaseAgent(...) {
    private val rateLimiters = ConcurrentHashMap<String, RateLimiter>()

    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        val tenantId = runtime.context.getAs<String>(ContextKeys.TENANT_ID)
            ?: return SpiceResult.failure(SpiceError(
                message = "Tenant ID required",
                code = "TENANT_MISSING"
            ))

        // Get tenant-specific rate limiter
        val rateLimiter = rateLimiters.computeIfAbsent(tenantId) {
            createRateLimiter(tenantId)
        }

        // Check rate limit
        if (!rateLimiter.tryAcquire()) {
            return SpiceResult.failure(SpiceError(
                message = "Rate limit exceeded for tenant: $tenantId",
                code = "RATE_LIMIT_EXCEEDED"
            ))
        }

        // Process request
        return processInternal(comm, runtime)
    }

    private fun createRateLimiter(tenantId: String): RateLimiter {
        // Get tenant-specific limits from config
        val limits = getTenantLimits(tenantId)
        return RateLimiter.create(limits.requestsPerSecond)
    }
}
```

## Edge Cases & Troubleshooting

### Context Loss Detection

Detect when context is unexpectedly lost:

```kotlin
class ContextAwareAgent : BaseAgent(...) {
    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        // Validate required context
        val requiredKeys = listOf(
            ContextKeys.USER_ID,
            ContextKeys.TRACE_ID,
            ContextKeys.TENANT_ID
        )

        val missingKeys = requiredKeys.filter { key ->
            !runtime.context.has(key)
        }

        if (missingKeys.isNotEmpty()) {
            log(LogLevel.ERROR, "Context validation failed", mapOf(
                "missing_keys" to missingKeys.joinToString(","),
                "comm_id" to comm.id,
                "comm_from" to comm.from
            ))

            // Option 1: Fail fast
            return SpiceResult.failure(SpiceError(
                message = "Required context missing: ${missingKeys.joinToString(", ")}",
                code = "CONTEXT_INCOMPLETE"
            ))

            // Option 2: Use defaults (risky!)
            // fillMissingContext(runtime.context, missingKeys)
        }

        return processInternal(comm, runtime)
    }
}
```

### Context Size Monitoring

Monitor and limit context size:

```kotlin
class SizeAwareAgent : BaseAgent(...) {
    companion object {
        private const val MAX_CONTEXT_SIZE_BYTES = 10_000 // 10KB
    }

    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        // Estimate context size
        val contextSize = estimateContextSize(runtime.context)

        if (contextSize > MAX_CONTEXT_SIZE_BYTES) {
            log(LogLevel.WARN, "Context size exceeds limit", mapOf(
                "size_bytes" to contextSize,
                "limit_bytes" to MAX_CONTEXT_SIZE_BYTES
            ))

            // Option 1: Fail
            return SpiceResult.failure(SpiceError(
                message = "Context too large: ${contextSize}B > ${MAX_CONTEXT_SIZE_BYTES}B",
                code = "CONTEXT_TOO_LARGE"
            ))

            // Option 2: Trim (dangerous!)
            // trimContext(runtime.context)
        }

        return processInternal(comm, runtime)
    }

    private fun estimateContextSize(context: AgentContext): Int {
        // Rough estimation
        return context.toString().toByteArray().size
    }
}
```

### Context Debugging

Debug context propagation issues:

```kotlin
class DebugAgent : BaseAgent(...) {
    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        // Log full context at entry
        log(LogLevel.DEBUG, "Agent entry - context snapshot", mapOf(
            "agent_id" to id,
            "comm_id" to comm.id,
            "context_keys" to runtime.context.keys().joinToString(","),
            "context_snapshot" to runtime.context.toDebugString()
        ))

        // Process
        val result = processInternal(comm, runtime)

        // Log context at exit
        log(LogLevel.DEBUG, "Agent exit - context snapshot", mapOf(
            "agent_id" to id,
            "comm_id" to comm.id,
            "context_keys" to runtime.context.keys().joinToString(","),
            "context_changes" to detectContextChanges()
        ))

        return result
    }

    private fun AgentContext.toDebugString(): String {
        return buildString {
            append("AgentContext{")
            keys().forEach { key ->
                append("$key=${get(key)}, ")
            }
            append("}")
        }
    }
}
```

## Best Practices

### 1. Always Validate Context

```kotlin
// ✅ GOOD - Validate required context
fun validateContext(context: AgentContext): ValidationResult {
    val required = listOf(
        ContextKeys.USER_ID,
        ContextKeys.TENANT_ID,
        ContextKeys.TRACE_ID
    )

    val missing = required.filter { !context.has(it) }

    return if (missing.isEmpty()) {
        ValidationResult.success()
    } else {
        ValidationResult.error("Missing required context: ${missing.joinToString()}")
    }
}

// ❌ BAD - Assume context exists
fun processWithoutValidation(context: AgentContext) {
    val userId = context.getAs<String>(ContextKeys.USER_ID)!! // NPE risk!
}
```

### 2. Use Immutable Context Updates

```kotlin
// ✅ GOOD - Immutable updates
val newContext = context.with(ContextKeys.SPAN_ID, spanId)

// ❌ BAD - Mutable updates (thread-safety issues)
context[ContextKeys.SPAN_ID] = spanId // Concurrent modification risk
```

### 3. Limit Context Size

```kotlin
// ✅ GOOD - Store IDs, not full objects
context[ContextKeys.USER_ID] = "user-123"

// ❌ BAD - Store large objects
context["user_object"] = User(
    id = "user-123",
    profile = ProfileData(...), // Large object!
    history = TransactionHistory(...) // Even larger!
)
```

### 4. Clear Sensitive Data

```kotlin
// ✅ GOOD - Clear sensitive data after use
suspend fun processPayment(context: AgentContext) {
    val paymentToken = context.getAs<String>("payment_token")

    try {
        processPaymentWithToken(paymentToken)
    } finally {
        // Clear sensitive data
        context.remove("payment_token")
    }
}

// ❌ BAD - Leave sensitive data in context
suspend fun unsafePayment(context: AgentContext) {
    val paymentToken = context.getAs<String>("payment_token")
    processPaymentWithToken(paymentToken)
    // Token remains in context!
}
```

### 5. Document Context Requirements

```kotlin
/**
 * Processes user orders with tenant isolation.
 *
 * **Required Context:**
 * - `userId` (String) - User ID for order ownership
 * - `tenantId` (String) - Tenant ID for data isolation
 * - `traceId` (String) - Trace ID for distributed tracing
 *
 * **Optional Context:**
 * - `sessionId` (String) - Session ID for session tracking
 * - `locale` (String) - User locale for localization
 *
 * @throws SpiceError if required context is missing
 */
class OrderProcessingAgent : BaseAgent(...) {
    // Implementation
}
```

## Real-World Examples

### Example 1: E-Commerce Order Processing

Multi-tenant e-commerce system with full tracing:

```kotlin
class ECommerceOrderAgent : BaseAgent(
    id = "order-processor",
    name = "Order Processor",
    description = "Processes customer orders with tenant isolation"
) {
    private val tracer = GlobalOpenTelemetry.getTracer("ecommerce")

    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        // Validate context
        val tenantId = runtime.context.getAs<String>(ContextKeys.TENANT_ID)
            ?: return SpiceResult.failure(SpiceError(
                message = "Tenant ID required",
                code = "TENANT_MISSING"
            ))

        val userId = runtime.context.getAs<String>(ContextKeys.USER_ID)
            ?: return SpiceResult.failure(SpiceError(
                message = "User ID required",
                code = "USER_MISSING"
            ))

        // Create trace span
        val span = tracer.spanBuilder("process_order")
            .setAttribute("tenant.id", tenantId)
            .setAttribute("user.id", userId)
            .startSpan()

        return try {
            Context.current().with(span).makeCurrent().use {
                // Step 1: Validate order (with context)
                val toolContext = ToolContext(
                    agentId = id,
                    userId = userId,
                    tenantId = tenantId,
                    correlationId = runtime.context.getAs(ContextKeys.CORRELATION_ID)
                )

                val validation = executeTool("validate_order",
                    mapOf("order_data" to comm.content),
                    toolContext
                )

                if (validation.isFailure) {
                    span.setStatus(StatusCode.ERROR, "Validation failed")
                    return validation.mapSuccess { comm.error("Validation failed", from = id) }
                }

                // Step 2: Process payment (tenant-scoped)
                val payment = executeTool("process_payment",
                    mapOf("order_id" to extractOrderId(comm)),
                    toolContext
                )

                if (payment.isFailure) {
                    span.setStatus(StatusCode.ERROR, "Payment failed")
                    return payment.mapSuccess { comm.error("Payment failed", from = id) }
                }

                // Step 3: Create fulfillment (tenant-scoped)
                val fulfillment = executeTool("create_fulfillment",
                    mapOf("order_id" to extractOrderId(comm)),
                    toolContext
                )

                span.setStatus(StatusCode.OK)
                span.setAttribute("order.processed", true)

                SpiceResult.success(comm.reply(
                    content = "Order processed successfully",
                    from = id,
                    data = mapOf(
                        "tenant_id" to tenantId,
                        "user_id" to userId,
                        "trace_id" to span.spanContext.traceId
                    )
                ))
            }
        } catch (e: Exception) {
            span.setStatus(StatusCode.ERROR, e.message ?: "")
            span.recordException(e)
            SpiceResult.failure(SpiceError.from(e))
        } finally {
            span.end()
        }
    }
}
```

### Example 2: Multi-Agent Customer Support

Swarm-based customer support with context propagation:

```kotlin
val supportSwarm = buildSwarmAgent {
    name = "Customer Support Team"
    description = "Multi-agent customer support with full context tracking"

    swarmTools {
        // Tenant-aware ticket lookup
        tool("lookup_ticket", "Look up support ticket") {
            parameter("ticket_id", "string", required = true)

            execute { params ->
                val ticketId = params["ticket_id"] as String

                // Access context (automatically passed by Swarm)
                val toolContext = getCurrentToolContext()
                val tenantId = toolContext.tenantId
                    ?: return@execute SpiceResult.success(
                        ToolResult.error("Tenant ID required")
                    )

                // Tenant-scoped query
                val ticket = ticketDatabase.query(
                    tenantId = tenantId,
                    ticketId = ticketId
                )

                SpiceResult.success(ToolResult.success(
                    result = ticket.toString(),
                    metadata = mapOf(
                        "tenant_id" to tenantId,
                        "ticket_id" to ticketId
                    )
                ))
            }
        }
    }

    members {
        // Tier 1 Support
        agent(buildAgent {
            name = "Tier 1 Agent"
            llm = anthropic(...) { model = "claude-3-5-haiku-20241022" }
            instructions = "Handle basic customer inquiries"
        })

        // Tier 2 Support
        agent(buildAgent {
            name = "Tier 2 Agent"
            llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
            instructions = "Handle complex technical issues"
        })

        // Escalation Manager
        agent(buildAgent {
            name = "Escalation Manager"
            llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
            instructions = "Manage escalated issues"
        })
    }
}

// Usage with full context
suspend fun handleSupportRequest(
    customerId: String,
    tenantId: String,
    request: String
) {
    val runtime = DefaultAgentRuntime(
        context = AgentContext.of(
            ContextKeys.USER_ID to customerId,
            ContextKeys.TENANT_ID to tenantId,
            ContextKeys.TRACE_ID to UUID.randomUUID().toString(),
            ContextKeys.CORRELATION_ID to UUID.randomUUID().toString()
        )
    )

    // Context automatically propagates to all support agents
    val result = supportSwarm.processComm(
        comm = Comm(
            content = request,
            from = customerId
        ),
        runtime = runtime
    )

    result.fold(
        onSuccess = { response ->
            println("Support response: ${response.content}")
            println("Handled by: ${response.from}")
            println("Trace ID: ${response.data["trace_id"]}")
        },
        onFailure = { error ->
            println("Support failed: ${error.message}")
        }
    )
}
```

### Example 3: Microservices Integration

Agent calling external microservices with context propagation:

```kotlin
class MicroserviceIntegrationAgent : BaseAgent(...) {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        // Extract all context
        val traceId = runtime.context.getAs<String>(ContextKeys.TRACE_ID)
        val spanId = runtime.context.getAs<String>(ContextKeys.SPAN_ID)
        val tenantId = runtime.context.getAs<String>(ContextKeys.TENANT_ID)
        val correlationId = runtime.context.getAs<String>(ContextKeys.CORRELATION_ID)

        // Call external service with context headers
        val response = httpClient.post("https://api.example.com/process") {
            headers {
                // W3C Trace Context standard
                append("traceparent", "00-$traceId-$spanId-01")
                append("tracestate", "spice=tenant:$tenantId")

                // Custom headers
                append("X-Tenant-ID", tenantId ?: "")
                append("X-Correlation-ID", correlationId ?: "")
                append("X-User-ID", runtime.context.getAs<String>(ContextKeys.USER_ID) ?: "")
            }

            setBody(mapOf(
                "content" to comm.content,
                "metadata" to comm.data
            ))
        }

        // Extract response context
        val responseTraceId = response.headers["traceparent"]
            ?.split("-")?.getOrNull(1)

        // Update runtime context with response
        if (responseTraceId != null) {
            runtime.context[ContextKeys.TRACE_ID] = responseTraceId
        }

        return SpiceResult.success(comm.reply(
            content = response.bodyAsText(),
            from = id,
            data = mapOf(
                "trace_id" to (responseTraceId ?: traceId ?: ""),
                "service" to "external-api"
            )
        ))
    }
}
```

## Testing Context Propagation

### Unit Tests

```kotlin
class ContextPropagationTest {
    @Test
    fun `context should propagate from agent to tool`() = runTest {
        val agent = MyAgent()

        val context = AgentContext.of(
            ContextKeys.USER_ID to "test-user",
            ContextKeys.TENANT_ID to "test-tenant"
        )

        val runtime = DefaultAgentRuntime(context = context)

        val result = agent.processComm(
            Comm(content = "test", from = "test"),
            runtime
        )

        // Verify tool received context
        assertTrue(result.isSuccess)
        val response = (result as SpiceResult.Success).value
        assertEquals("test-tenant", response.data["tenant_id"])
    }

    @Test
    fun `missing context should fail gracefully`() = runTest {
        val agent = MyAgent()

        val context = AgentContext.of() // Empty context
        val runtime = DefaultAgentRuntime(context = context)

        val result = agent.processComm(
            Comm(content = "test", from = "test"),
            runtime
        )

        // Should fail with clear error
        assertTrue(result.isFailure)
        val error = (result as SpiceResult.Failure).error
        assertEquals("TENANT_MISSING", error.code)
    }
}
```

### Integration Tests

```kotlin
class SwarmContextPropagationTest {
    @Test
    fun `swarm should propagate context to all members`() = runTest {
        val receivedContexts = mutableListOf<ToolContext>()

        val swarm = buildSwarmAgent {
            name = "Test Swarm"

            swarmTools {
                tool("capture_context", "Captures tool context") {
                    execute { params ->
                        val ctx = getCurrentToolContext()
                        receivedContexts.add(ctx)
                        SpiceResult.success(ToolResult.success("captured"))
                    }
                }
            }

            quickSwarm {
                specialist("agent1", "Agent 1", "task")
                specialist("agent2", "Agent 2", "task")
                specialist("agent3", "Agent 3", "task")
            }
        }

        val runtime = DefaultAgentRuntime(
            context = AgentContext.of(
                ContextKeys.TENANT_ID to "test-tenant",
                ContextKeys.TRACE_ID to "test-trace"
            )
        )

        swarm.processComm(
            Comm(content = "test", from = "test"),
            runtime
        )

        // All members should receive same context
        assertEquals(3, receivedContexts.size)
        receivedContexts.forEach { ctx ->
            assertEquals("test-tenant", ctx.tenantId)
        }
    }
}
```

## Summary

Context propagation is **critical** for production systems. Key takeaways:

1. **Always validate context** - Check required keys at entry points
2. **Use AgentContext/ToolContext** - Standard context types for consistency
3. **Propagate through Swarms** - Runtime automatically propagates to members
4. **Preserve across async** - Use proper coroutine scopes and thread handling
5. **Integrate tracing** - OpenTelemetry for end-to-end visibility
6. **Enforce tenant isolation** - Never mix tenant data
7. **Monitor context size** - Keep context lean for performance
8. **Document requirements** - Clear context contracts in API docs
9. **Test propagation** - Verify context flows correctly
10. **Handle failures gracefully** - Clear errors for missing context

**Context propagation done right** enables:
- ✅ Complete observability
- ✅ Perfect tenant isolation
- ✅ Security compliance
- ✅ Easy debugging
- ✅ Production confidence

## Next Steps

- [Agent API](../api/agent) - Agent interface and runtime
- [Tool API](../api/tool) - Tool context and execution
- [Swarm Documentation](../orchestration/swarm) - Multi-agent coordination
- [Tool Patterns](../tools-extensions/tool-patterns) - Advanced tool patterns including tracing
