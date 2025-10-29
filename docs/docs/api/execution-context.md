# ExecutionContext API

**Added in:** 0.6.0

Unified execution context for Spice graphs - single source of truth for tenant, user, and custom metadata.

## Overview

`ExecutionContext` replaces the dual context system (`AgentContext` + `NodeContext.metadata`) with a single, consistent API:

- **Coroutine propagation** - Works as `CoroutineContext.Element`
- **Immutable** - All modifications return new instances
- **Type-safe** - Built-in accessors for common fields
- **Unified** - Same context used in `NodeContext`, `RunContext`, and `Comm`

## Core Structure

```kotlin
data class ExecutionContext(
    private val data: Map<String, Any> = emptyMap()
) : AbstractCoroutineContextElement(ExecutionContext)
```

## Creating Context

### From Map

```kotlin
val context = ExecutionContext.of(mapOf(
    "tenantId" to "tenant-123",
    "userId" to "user-456",
    "correlationId" to "corr-789",
    "customKey" to customValue
))
```

### From AgentContext (Migration)

```kotlin
val agentContext = AgentContext.of("tenantId" to "CHIC")
val execContext = agentContext.toExecutionContext()

// With additional fields
val enriched = agentContext.toExecutionContext(
    mapOf("sessionId" to "sess-123")
)
```

## Accessing Data

### Type-Safe Accessors

```kotlin
// Built-in accessors
val tenantId: String? = context.tenantId
val userId: String? = context.userId
val correlationId: String? = context.correlationId

// Generic access
val raw: Any? = context.get("customKey")
val typed: String? = context.getAs<String>("customKey")
```

### Convert to Map

```kotlin
val map: Map<String, Any> = context.toMap()
```

## Modifying Context

All modifications return **new instances** (immutable):

```kotlin
// Add single value
val updated = context.plus("sessionId", "sess-123")

// Add multiple values
val enriched = context.plusAll(mapOf(
    "key1" to "value1",
    "key2" to "value2"
))

// Chaining
val final = context
    .plus("sessionId", "sess-123")
    .plusAll(mapOf("region" to "us-west"))
```

## Usage in Graphs

### NodeContext

```kotlin
class MyNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Access context
        val tenantId = ctx.context.tenantId
        val customValue = ctx.context.get("customKey")
        
        // Return enriched context
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = result,
                additional = mapOf("nodeProcessed" to true)
            )
        )
    }
}
```

### Graph Invocation

```kotlin
val graph = graph("my-graph") {
    agent("processor", myAgent)
    output("result")
}

// Initialize with context via metadata
val result = runner.run(
    graph,
    mapOf(
        "input" to "data",
        "metadata" to mapOf(
            "tenantId" to "tenant-123",
            "userId" to "user-456"
        )
    )
).getOrThrow()
```

### Coroutine Propagation

### Accessor Functions

**Added in:** 0.6.0

Convenient functions to access ExecutionContext from anywhere in your code:

```kotlin
// Get current context (returns null if not in scope)
suspend fun myService() {
    val context = currentExecutionContext()
    val tenantId = context?.tenantId
}

// Require context (throws if not present)
suspend fun myService() {
    val context = requireExecutionContext()
    val tenantId = context.tenantId  // Safe - won't be null
}

// Direct accessors (most convenient!)
suspend fun myService() {
    val tenantId = getCurrentTenantId()
    val userId = getCurrentUserId()
    val correlationId = getCurrentCorrelationId()
}
```

**Available Functions:**
- `currentExecutionContext(): ExecutionContext?` - Get context or null
- `requireExecutionContext(): ExecutionContext` - Get context or throw
- `getCurrentTenantId(): String?` - Get tenant ID
- `getCurrentUserId(): String?` - Get user ID
- `getCurrentCorrelationId(): String?` - Get correlation ID

### Setting Context

```kotlin
// ExecutionContext auto-propagates through coroutines
withContext(ExecutionContext.of(mapOf("tenantId" to "CHIC"))) {
    // All graph/agent executions inherit context
    runner.run(graph, input)
    
    // Service layer can access it
    myService()  // getCurrentTenantId() works!
}

// Or use withAgentContext DSL (sets both AgentContext + ExecutionContext)
withAgentContext("tenantId" to "CHIC", "userId" to "user-123") {
    runner.run(graph, input)
    
    // Both work!
    val ctx1 = currentAgentContext()
    val ctx2 = currentExecutionContext()
}
```

### Service Layer Pattern

```kotlin
// Service doesn't need context parameter!
suspend fun processOrder(orderId: String) {
    val tenantId = getCurrentTenantId() ?: error("No tenant")
    val userId = getCurrentUserId()
    
    // Use context
    val order = orderRepository.findByTenant(orderId, tenantId)
    auditLog.log("Order processed", tenantId, userId)
}

// Called from controller with context
suspend fun handleRequest(request: OrderRequest) {
    withAgentContext(
        "tenantId" to request.tenantId,
        "userId" to request.userId
    ) {
        processOrder(request.orderId)  // Context propagates!
    }
}
```

## RunContext Integration

**Added in:** 0.6.0

`RunContext` now uses `ExecutionContext`:

```kotlin
data class RunContext(
    val graphId: String,
    val runId: String,
    val context: ExecutionContext  // Unified!
)
```

**Middleware usage:**
```kotlin
class MyMiddleware : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        val tenantId = ctx.context.tenantId
        println("Starting graph for tenant: $tenantId")
        next()
    }
}
```

## Comm Integration

**Breaking Change in:** 0.6.0

`Comm.context` now uses `ExecutionContext`:

```kotlin
val comm = Comm(
    content = "Hello",
    from = "user",
    context = ExecutionContext.of(mapOf("tenantId" to "tenant-123"))
)

// Backward compatibility bridge
val agentContext = AgentContext.of("tenantId" to "CHIC")
val comm2 = Comm(
    content = "Hello",
    from = "user",
    context = agentContext  // Automatically converts to ExecutionContext
)
```

## Migration from AgentContext

### Converting

```kotlin
// AgentContext → ExecutionContext
val agentCtx = AgentContext.of("tenantId" to "CHIC")
val execCtx = agentCtx.toExecutionContext()

// ExecutionContext → AgentContext (if needed)
val execCtx = ExecutionContext.of(mapOf("tenantId" to "CHIC"))
val agentCtx = execCtx.toAgentContext()
```

### Before/After

```kotlin
// 0.5.x
val agentCtx = AgentContext.of("tenantId" to "CHIC", "userId" to "user-123")
val comm = Comm(content = "test", from = "user", context = agentCtx)

class MyNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val tenant = ctx.agentContext?.tenantId
        val custom = ctx.metadata["customKey"]
        ctx.state["result"] = "value"
        return SpiceResult.success(NodeResult(data = "done"))
    }
}

// 0.6.0
val execCtx = ExecutionContext.of(mapOf("tenantId" to "CHIC", "userId" to "user-123"))
val comm = Comm(content = "test", from = "user", context = execCtx)

class MyNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val tenant = ctx.context.tenantId      // Unified!
        val custom = ctx.context.get("customKey")
        // State is immutable - return updates via metadata
        return SpiceResult.success(
            NodeResult.fromContext(ctx, data = "done", additional = mapOf("result" to "value"))
        )
    }
}
```

## Best Practices

### 1. Use Type-Safe Accessors

```kotlin
// ✅ Good
val tenantId = ctx.context.tenantId
val userId = ctx.context.userId

// ⚠️ Avoid (unless custom keys)
val tenantId = ctx.context.get("tenantId") as? String
```

### 2. Preserve Context in Nodes

```kotlin
// ✅ Good - preserves all existing context
NodeResult.fromContext(ctx, data = result, additional = mapOf("newKey" to "value"))

// ❌ Bad - loses context
NodeResult.create(data = result, metadata = mapOf("newKey" to "value"))
```

### 3. Immutable Updates

```kotlin
// ✅ Good - functional style
val updated = ctx
    .withState("key1", "value1")
    .withState("key2", "value2")
    .withContext(enrichedContext)

// ❌ Bad - won't compile (state is immutable)
ctx.state["key"] = "value"
```

## See Also

- [Migration Guide](/MIGRATION_GUIDE_v0.6.0.md)
- [Graph Nodes](/docs/orchestration/graph-nodes)
- [NodeResult API](/docs/api/graph#noderesult)

