# ExecutionContext Patterns & Best Practices

**Added in:** 0.6.0

Complete guide to using `ExecutionContext` effectively in production applications.

## Overview

`ExecutionContext` is the unified context system in Spice 0.6.0 that replaces the dual `AgentContext` + `NodeContext.metadata` approach. This guide covers practical patterns and best practices.

## Core Patterns

### Pattern 1: Multi-Tenant Graph Execution

Execute graphs with tenant isolation:

```kotlin
import io.github.noailabs.spice.ExecutionContext
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner

suspend fun processOrder(orderId: String, tenantId: String, userId: String) {
    val graph = graph("order-processor") {
        agent("validator", validatorAgent)
        agent("processor", processorAgent)
        output("result")
    }

    val runner = DefaultGraphRunner()
    
    // Initialize with execution context
    val result = runner.run(
        graph,
        mapOf(
            "input" to orderId,
            "metadata" to mapOf(
                "tenantId" to tenantId,
                "userId" to userId,
                "correlationId" to UUID.randomUUID().toString(),
                "timestamp" to Instant.now().toString()
            )
        )
    ).getOrThrow()

    return result.result
}
```

**Key Points:**
- ✅ Context flows through entire graph automatically
- ✅ Every node can access `ctx.context.tenantId`
- ✅ No manual context passing needed
- ✅ Type-safe accessors (`tenantId`, `userId`, `correlationId`)

### Pattern 2: Coroutine-Level Context

Use coroutine context for cross-cutting concerns:

```kotlin
import io.github.noailabs.spice.dsl.withAgentContext
import kotlinx.coroutines.withContext

// Set context once for entire scope
suspend fun handleRequest(request: Request) {
    withAgentContext(
        "tenantId" to request.tenantId,
        "userId" to request.userId,
        "requestId" to UUID.randomUUID().toString()
    ) {
        // All nested operations inherit context
        val result1 = agent1.processComm(comm1)  // Has context!
        val result2 = agent2.processComm(comm2)  // Has context!
        val graphResult = runner.run(graph, input)  // Has context!
        
        // Even custom services can access it
        val context = coroutineContext[ExecutionContext]
        auditLog.log("Request processed", context?.tenantId)
    }
}
```

**Benefits:**
- ✅ Set once, use everywhere
- ✅ No context parameter passing
- ✅ Automatically propagates to graphs, agents, tools
- ✅ Works with suspend functions

### Pattern 3: Context Enrichment

Add context as workflow progresses:

```kotlin
class EnrichmentNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val userId = ctx.context.userId
        
        // Load user profile
        val userProfile = userService.getProfile(userId)
        
        // Enrich context for downstream nodes
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = "user-loaded",
                additional = mapOf(
                    "userName" to userProfile.name,
                    "userRole" to userProfile.role,
                    "userRegion" to userProfile.region,
                    "enrichedAt" to Instant.now().toString()
                )
            )
        )
    }
}

// Later nodes automatically get enriched context
class ProcessingNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val userName = ctx.context.getAs<String>("userName")
        val userRole = ctx.context.getAs<String>("userRole")
        
        println("Processing for $userName with role $userRole")
        
        return SpiceResult.success(
            NodeResult.fromContext(ctx, data = "processed")
        )
    }
}
```

**Pattern:**
1. Early node loads data
2. Enriches context with additional fields
3. Later nodes access enriched data
4. Context accumulates throughout graph

### Pattern 4: Context-Aware Tools

Tools receive ExecutionContext automatically:

```kotlin
contextAwareTool("database_query") {
    description = "Query tenant-specific database"
    
    parameter("query", "string", "SQL query")
    
    execute { params, context ->
        // Context available in tool execution
        val tenantId = context.tenantId ?: throw IllegalStateException("No tenant")
        val userId = context.userId
        
        // Use tenant-specific connection
        val connection = connectionPool.getConnection(tenantId)
        val results = connection.execute(params["query"] as String)
        
        // Log with context
        logger.info("Query executed", mapOf(
            "tenantId" to tenantId,
            "userId" to userId,
            "query" to params["query"]
        ))
        
        ToolResult.success(results.toString())
    }
}
```

**Automatic Context Propagation:**
- Graph → Node → Tool (all automatic)
- No manual context passing
- Tools get `ToolContext` with all ExecutionContext data

### Pattern 5: Custom Context Keys

Add domain-specific context:

```kotlin
// Initialize graph with custom keys
val result = runner.run(
    graph,
    mapOf(
        "input" to orderData,
        "metadata" to mapOf(
            // Standard keys
            "tenantId" to "ACME",
            "userId" to "user-123",
            
            // Custom domain keys
            "businessUnit" to "SALES",
            "region" to "US-WEST",
            "priority" to "HIGH",
            "retryPolicy" to "exponential",
            "maxRetries" to 3
        )
    )
)

// Access in nodes
class PolicyNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val priority = ctx.context.getAs<String>("priority")
        val maxRetries = ctx.context.getAs<Int>("maxRetries")
        
        when (priority) {
            "HIGH" -> processImmediately()
            "LOW" -> queueForLater()
            else -> processNormal()
        }
        
        return SpiceResult.success(
            NodeResult.fromContext(ctx, data = "processed")
        )
    }
}
```

**Best Practices:**
- ✅ Use meaningful key names
- ✅ Document custom keys in your domain model
- ✅ Use `getAs<T>` for type-safe access
- ✅ Provide defaults for optional keys

## Advanced Patterns

### Pattern 6: Context Validation

Ensure required context exists:

```kotlin
class RequiredContextValidator : MetadataValidator {
    override fun validate(metadata: Map<String, Any>): SpiceResult<Unit> {
        val required = setOf("tenantId", "userId", "correlationId")
        val missing = required.filter { !metadata.containsKey(it) }
        
        return if (missing.isEmpty()) {
            SpiceResult.success(Unit)
        } else {
            SpiceResult.failure(
                SpiceError.validationError(
                    "Missing required context keys: ${missing.joinToString()}"
                )
            )
        }
    }
}

// Use with runner
val runner = DefaultGraphRunner(
    metadataValidator = RequiredContextValidator()
)
```

**Use Cases:**
- Enforce tenant ID presence
- Validate correlation ID exists
- Check authorization context
- Prevent missing critical data

### Pattern 7: Context Hierarchy

Build layered context:

```kotlin
// Application-level context
val appContext = ExecutionContext.of(mapOf(
    "environment" to "production",
    "version" to "1.0.0",
    "region" to "us-west-1"
))

// Request-level context (adds to app context)
suspend fun handleRequest(request: Request) = withContext(appContext) {
    withAgentContext(
        "tenantId" to request.tenantId,
        "userId" to request.userId,
        "requestId" to UUID.randomUUID().toString()
    ) {
        // Graph inherits both layers
        runner.run(graph, input)
        
        // All nodes have access to:
        // - environment (app-level)
        // - tenantId (request-level)
        // - version (app-level)
        // - userId (request-level)
    }
}
```

**Pattern:**
- Base context (app/service level)
- Enriched context (request level)
- All layers accessible in nodes

### Pattern 8: Context-Driven Routing

Use context to drive graph flow:

```kotlin
val graph = graph("context-router") {
    agent("processor", processorAgent)
    
    // Route based on context
    edge("processor", "premium-path") { result ->
        val ctx = (result.metadata["_executionContext"] as? ExecutionContext)
        ctx?.getAs<String>("tier") == "PREMIUM"
    }
    
    edge("processor", "standard-path") { result ->
        val ctx = (result.metadata["_executionContext"] as? ExecutionContext)
        ctx?.getAs<String>("tier") != "PREMIUM"
    }
    
    agent("premium-handler", premiumAgent)
    agent("standard-handler", standardAgent)
}

// Initialize with routing context
val result = runner.run(
    graph,
    mapOf(
        "input" to data,
        "metadata" to mapOf(
            "tenantId" to "ACME",
            "tier" to "PREMIUM"  // Drives routing!
        )
    )
)
```

## Production Patterns

### Pattern 9: Logging & Observability

Consistent logging with context:

```kotlin
class AuditMiddleware : Middleware {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        val tenantId = ctx.context.tenantId
        val userId = ctx.context.userId
        val correlationId = ctx.context.correlationId
        
        MDC.put("tenantId", tenantId)
        MDC.put("userId", userId)
        MDC.put("correlationId", correlationId)
        MDC.put("graphId", ctx.graphId)
        MDC.put("runId", ctx.runId)
        
        try {
            logger.info("Graph execution started")
            next()
            logger.info("Graph execution completed")
        } finally {
            MDC.clear()
        }
    }
    
    override suspend fun onNode(
        req: NodeRequest, 
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        logger.info("Node executing: ${req.nodeId}")
        val result = next(req)
        
        when (result) {
            is SpiceResult.Success -> logger.info("Node succeeded: ${req.nodeId}")
            is SpiceResult.Failure -> logger.error("Node failed: ${req.nodeId}", result.error.toException())
        }
        
        return result
    }
}
```

**Benefits:**
- ✅ All logs tagged with tenant/user
- ✅ Correlation ID for request tracing
- ✅ MDC automatically cleared
- ✅ Works with ELK/Datadog/etc.

### Pattern 10: Error Context Preservation

Preserve context in error scenarios:

```kotlin
class ErrorHandlingNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        return SpiceResult.catching {
            val result = riskyOperation()
            
            NodeResult.fromContext(ctx, data = result)
        }.recoverWith { error ->
            // Even in error, preserve context for debugging
            logger.error(
                "Operation failed",
                mapOf(
                    "tenantId" to ctx.context.tenantId,
                    "userId" to ctx.context.userId,
                    "correlationId" to ctx.context.correlationId,
                    "error" to error.message
                )
            )
            
            // Return error with full context
            SpiceResult.success(
                NodeResult.fromContext(
                    ctx,
                    data = null,
                    additional = mapOf(
                        "error" to error.message,
                        "errorType" to error::class.simpleName,
                        "failedAt" to Instant.now().toString()
                    )
                )
            )
        }
    }
}
```

**Key Points:**
- ✅ Context preserved in error paths
- ✅ Debugging info includes tenant/user
- ✅ Error metadata tracked
- ✅ Downstream nodes know about failure

## Common Pitfalls & Solutions

### Pitfall 1: Forgetting to Preserve Context

❌ **Wrong:**
```kotlin
return SpiceResult.success(
    NodeResult.create(
        data = result,
        metadata = mapOf("myKey" to "value")  // ❌ Lost all context!
    )
)
```

✅ **Correct:**
```kotlin
return SpiceResult.success(
    NodeResult.fromContext(
        ctx,
        data = result,
        additional = mapOf("myKey" to "value")  // ✅ Preserves context!
    )
)
```

### Pitfall 2: Mutating State

❌ **Wrong (0.6.0 won't compile):**
```kotlin
override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
    ctx.state["result"] = computeValue()  // ❌ State is immutable!
    return SpiceResult.success(NodeResult.fromContext(ctx, data = "done"))
}
```

✅ **Correct:**
```kotlin
override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
    val value = computeValue()
    
    // Return state updates via metadata
    return SpiceResult.success(
        NodeResult.fromContext(
            ctx,
            data = "done",
            additional = mapOf("result" to value)  // GraphRunner propagates to state
        )
    )
}
```

### Pitfall 3: Not Using Type-Safe Accessors

❌ **Avoid:**
```kotlin
val tenantId = ctx.context.get("tenantId") as? String  // Verbose
val userId = ctx.context.toMap()["userId"] as? String  // Unsafe
```

✅ **Prefer:**
```kotlin
val tenantId = ctx.context.tenantId  // Type-safe!
val userId = ctx.context.userId      // Built-in accessor
val custom = ctx.context.getAs<String>("customKey")  // Generic type-safe
```

### Pitfall 4: Metadata Size Explosion

❌ **Wrong:**
```kotlin
// Adding large objects to context
NodeResult.fromContext(
    ctx,
    data = result,
    additional = mapOf(
        "fullDocument" to largePdfBytes,  // ❌ Huge!
        "entireDataset" to millionRecords  // ❌ Will exceed size limit
    )
)
```

✅ **Correct:**
```kotlin
// Store references, not data
NodeResult.fromContext(
    ctx,
    data = result,
    additional = mapOf(
        "documentId" to documentId,        // ✅ Small reference
        "documentUrl" to s3Url,            // ✅ URL reference
        "recordCount" to 1_000_000         // ✅ Metadata only
    )
)
```

**Size Policies:**
```kotlin
// Default: warn at 5KB
NodeResult.METADATA_WARN_THRESHOLD  // 5000

// Configure hard limit if needed
NodeResult.HARD_LIMIT = 10_000
NodeResult.onOverflow = NodeResult.OverflowPolicy.FAIL
```

## Testing Patterns

### Pattern 11: Testing with Context

```kotlin
@Test
fun `should process with tenant context`() = runTest {
    // Given: Graph and context
    val graph = graph("test-graph") {
        agent("processor", testAgent)
        output("result")
    }
    
    val runner = DefaultGraphRunner()
    
    // When: Execute with context
    val result = withAgentContext(
        "tenantId" to "TEST_TENANT",
        "userId" to "test-user"
    ) {
        runner.run(
            graph,
            mapOf(
                "input" to "test data",
                "metadata" to mapOf(
                    "testMode" to true,
                    "mockServices" to true
                )
            )
        )
    }.getOrThrow()
    
    // Then: Verify result
    assertEquals("expected", result.result)
}
```

### Pattern 12: Mocking Context-Aware Services

```kotlin
class MockTenantService : TenantService {
    override suspend fun getData(key: String): String {
        val context = coroutineContext[ExecutionContext]
        val tenantId = context?.tenantId
        
        return when (tenantId) {
            "TENANT_A" -> "data-for-A"
            "TENANT_B" -> "data-for-B"
            else -> "default-data"
        }
    }
}
```

## Migration Patterns

### Pattern 13: Gradual Migration from AgentContext

```kotlin
// Step 1: Bridge usage (backward compatible)
val agentCtx = AgentContext.of("tenantId" to "ACME")
val comm = Comm(content = "test", from = "user", context = agentCtx)
// Automatically converts to ExecutionContext internally

// Step 2: Direct ExecutionContext usage (recommended)
val execCtx = ExecutionContext.of(mapOf("tenantId" to "ACME"))
val comm2 = Comm(content = "test", from = "user", context = execCtx)

// Step 3: Convert existing AgentContext
val converted = agentCtx.toExecutionContext(
    additionalFields = mapOf("newKey" to "value")
)
```

### Pattern 14: Converting Legacy Nodes

**Before (0.5.x):**
```kotlin
class LegacyNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val tenant = ctx.agentContext?.tenantId
        val custom = ctx.metadata["customKey"]
        ctx.state["result"] = "value"
        
        return SpiceResult.success(
            NodeResult(
                data = "done",
                metadata = ctx.metadata + mapOf("processed" to true)
            )
        )
    }
}
```

**After (0.6.0):**
```kotlin
class ModernNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val tenant = ctx.context.tenantId       // Unified!
        val custom = ctx.context.get("customKey")
        
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = "done",
                additional = mapOf(
                    "result" to "value",        // State update
                    "processed" to true         // Metadata
                )
            )
        )
    }
}
```

## Performance Patterns

### Pattern 15: Efficient Context Updates

```kotlin
// ✅ Efficient: Single update with all changes
val enriched = ctx.context.plusAll(mapOf(
    "key1" to "value1",
    "key2" to "value2",
    "key3" to "value3"
))

// ❌ Less efficient: Multiple chained updates
val enriched = ctx.context
    .plus("key1", "value1")
    .plus("key2", "value2")
    .plus("key3", "value3")
// Still works, but creates intermediate objects
```

### Pattern 16: Context Cleanup

Remove temporary context after use:

```kotlin
class TemporaryContextNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Process with temporary context
        val result = processWithTempContext(ctx.context.plus("temp", "value"))
        
        // Don't propagate temporary keys
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,  // Original context (no "temp" key)
                data = result,
                additional = mapOf("processedAt" to Instant.now())
            )
        )
    }
}
```

## Observability Patterns

### Pattern 17: Metadata Delta Tracking

```kotlin
// NodeReport includes metadata changes
report.nodeReports.forEach { nodeReport ->
    println("Node: ${nodeReport.nodeId}")
    
    // See what changed
    nodeReport.metadataChanges?.forEach { (key, value) ->
        println("  Added/Modified: $key = $value")
    }
    
    // Full metadata state
    nodeReport.metadata?.let { fullMeta ->
        println("  Full context keys: ${fullMeta.keys}")
    }
}
```

**Use Cases:**
- Debug where context keys were added
- Track context growth over execution
- Identify which nodes enrich context

### Pattern 18: Context Monitoring

```kotlin
class ContextMonitoringMiddleware : Middleware {
    override suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        val beforeSize = req.context.context.toMap().size
        
        val result = next(req)
        
        if (result is SpiceResult.Success) {
            val afterSize = result.value.metadata.size
            val growth = afterSize - beforeSize
            
            if (growth > 10) {
                logger.warn(
                    "Context grew by $growth keys in node ${req.nodeId}",
                    mapOf("before" to beforeSize, "after" to afterSize)
                )
            }
        }
        
        return result
    }
}
```

## Security Patterns

### Pattern 19: Tenant Isolation Verification

```kotlin
class TenantIsolationMiddleware : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        val tenantId = ctx.context.tenantId
            ?: throw SecurityException("No tenant ID - execution blocked")
        
        // Verify tenant is active
        if (!tenantRegistry.isActive(tenantId)) {
            throw SecurityException("Tenant $tenantId is inactive")
        }
        
        next()
    }
}
```

### Pattern 20: Context Sanitization

```kotlin
class ContextSanitizer : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Remove sensitive keys before external API call
        val sanitizedCtx = ctx.context.toMap()
            .filterKeys { it !in setOf("internalToken", "secretKey") }
        
        val apiResult = externalApi.call(
            data = ctx.state["input"],
            context = ExecutionContext.of(sanitizedCtx)
        )
        
        return SpiceResult.success(
            NodeResult.fromContext(ctx, data = apiResult)
        )
    }
}
```

## See Also

- [ExecutionContext API](/docs/api/execution-context)
- [Release Guide](../roadmap/release-1-0-0)
- [Graph Nodes](/docs/orchestration/graph-nodes)
- [Context Propagation](/docs/advanced/context-propagation)
