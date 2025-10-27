# Context-Aware Tools

Complete guide to building tools that automatically receive and use `AgentContext` for multi-tenant, user-scoped operations.

## Overview

Context-Aware Tools are a core feature introduced in Spice Framework v0.4.0 that enables automatic context injection into tool execution. Instead of manually passing `tenantId`, `userId`, and other context values, tools automatically receive them through Kotlin's coroutine context system.

**Key Benefits**:
- ✅ **Zero Boilerplate** - No manual context passing
- ✅ **Thread-Safe** - Automatic propagation through async operations
- ✅ **Type-Safe** - Compile-time access to context properties
- ✅ **Multi-Tenant Ready** - Built-in tenant isolation

## Quick Start

### Basic Context-Aware Tool

```kotlin
import io.github.noailabs.spice.dsl.*

val lookupTool = contextAwareTool("lookup_policy") {
    description = "Look up policy by type"
    param("policyType", "string", "Policy type", required = true)

    execute { params, context ->
        // Context automatically injected!
        val tenantId = context.tenantId ?: throw IllegalStateException("No tenant")
        val userId = context.userId ?: "system"
        val policyType = params["policyType"] as String

        // Use context for tenant-scoped operations
        policyRepository.findByType(tenantId, policyType)
    }
}

// Use with agent context
withAgentContext(
    "tenantId" to "ACME",
    "userId" to "user-123"
) {
    val result = lookupTool.execute(mapOf("policyType" to "auto"))
    println(result.getOrNull()?.result)
}
```

## Core Concepts

### AgentContext

`AgentContext` is an immutable data structure that flows through your entire application via Kotlin's `CoroutineContext`:

```kotlin
data class AgentContext(
    val tenantId: String?,
    val userId: String?,
    val sessionId: String?,
    val correlationId: String?,
    val data: Map<String, Any> = emptyMap()
) : AbstractCoroutineContextElement(AgentContext)
```

**Standard Properties**:
- `tenantId` - Tenant identifier for multi-tenant applications
- `userId` - User identifier for user-scoped operations
- `sessionId` - Session identifier for tracking
- `correlationId` - Correlation ID for distributed tracing

**Custom Properties**:
```kotlin
val customValue = context.get("customKey") as? String
```

### Setting Context

Use `withAgentContext` to set context at application boundaries:

```kotlin
// HTTP endpoint
@PostMapping("/api/orders")
suspend fun createOrder(@RequestBody request: OrderRequest) =
    withAgentContext(
        "tenantId" to request.tenantId,
        "userId" to getCurrentUser().id,
        "sessionId" to request.sessionId
    ) {
        // All operations within this block have context!
        agent.processComm(comm)
    }
```

**Context automatically propagates through**:
- Tool executions
- Agent processing
- Service calls
- Repository operations
- Async coroutines (`launch`, `async`)
- Nested function calls

## Creating Context-Aware Tools

### Method 1: `contextAwareTool` DSL

Full-featured DSL for complex tools:

```kotlin
val createOrderTool = contextAwareTool("create_order") {
    description = "Create a new order"

    // Define parameters using structured DSL
    parameters {
        string("customerId", "Customer ID", required = true)
        array("items", "Order items", required = true)
        number("discount", "Discount percentage", required = false)
    }

    // Optional: Add output validation
    validate {
        requireField("orderId")
        requireField("total")
        fieldType("total", FieldType.NUMBER)

        custom("total must be positive") { output ->
            val total = (output as? Map<*, *>)?.get("total") as? Number
            (total?.toDouble() ?: 0.0) > 0.0
        }
    }

    // Optional: Add caching
    cache {
        ttl = 300  // 5 minutes
        maxSize = 100
        keyBuilder = { params, context ->
            "${context.tenantId}:${params["customerId"]}"
        }
    }

    // Execute with automatic context injection
    execute { params, context ->
        val tenantId = context.tenantId!!
        val userId = context.userId!!
        val customerId = params["customerId"] as String
        val items = params["items"] as List<*>

        val order = orderService.createOrder(
            tenantId = tenantId,
            userId = userId,
            customerId = customerId,
            items = items.map { it.toString() }
        )

        mapOf(
            "orderId" to order.id,
            "total" to order.total,
            "status" to "created"
        )
    }
}
```

### Method 2: `simpleContextTool`

Lightweight syntax for simple tools:

```kotlin
val getCurrentTenant = simpleContextTool(
    name = "get_tenant",
    description = "Get current tenant ID"
) { params, context ->
    "Current tenant: ${context.tenantId}"
}
```

### Method 3: Builder Pattern

For programmatic tool creation:

```kotlin
val builder = ContextAwareToolBuilder("dynamic_tool")
builder.description = "Dynamically built tool"
builder.param("input", "string", "Input value")

builder.execute { params, context ->
    "Tenant ${context.tenantId} processed: ${params["input"]}"
}

val tool = builder.build()
```

## Integration with Agents

### CoreAgentBuilder Extensions

Add context-aware tools directly to agents:

```kotlin
val agent = buildAgent {
    id = "order-agent"
    name = "Order Processing Agent"

    // Add context-aware tool inline
    contextAwareTool("submit_order") {
        description = "Submit order for processing"
        param("orderId", "string", "Order ID")

        execute { params, context ->
            val tenantId = context.tenantId!!
            val orderId = params["orderId"] as String

            orderService.submit(tenantId, orderId)
        }
    }

    // Add simple context tool
    simpleContextTool("get_status", "Get order status") { params, context ->
        val orderId = params["orderId"] as String
        orderService.getStatus(context.tenantId!!, orderId)
    }
}
```

### Multi-Tool Agent

```kotlin
val multiToolAgent = buildAgent {
    id = "customer-agent"

    contextAwareTool("lookup_customer") {
        param("customerId", "string", "Customer ID")
        execute { params, context ->
            customerRepo.find(context.tenantId!!, params["customerId"] as String)
        }
    }

    contextAwareTool("create_ticket") {
        param("customerId", "string", "Customer ID")
        param("issue", "string", "Issue description")
        execute { params, context ->
            ticketService.create(
                tenantId = context.tenantId!!,
                customerId = params["customerId"] as String,
                issue = params["issue"] as String
            )
        }
    }

    contextAwareTool("send_notification") {
        param("customerId", "string", "Customer ID")
        param("message", "string", "Notification message")
        execute { params, context ->
            notificationService.send(
                tenantId = context.tenantId!!,
                userId = params["customerId"] as String,
                message = params["message"] as String
            )
        }
    }
}
```

## Service Layer Integration

### BaseContextAwareService

Create services that automatically use context:

```kotlin
class OrderService : BaseContextAwareService() {

    // Require tenant ID from context
    suspend fun findOrders() = withTenant { tenantId ->
        database.query(
            "SELECT * FROM orders WHERE tenant_id = ?",
            tenantId
        )
    }

    // Require both tenant and user
    suspend fun createOrder(items: List<String>) =
        withTenantAndUser { tenantId, userId ->
            Order(
                tenantId = tenantId,
                userId = userId,
                items = items,
                createdAt = Instant.now()
            ).also { order ->
                database.insert(order)
            }
        }

    // Optional: Direct context access
    suspend fun processOrder(orderId: String) {
        val context = getContext()
        val tenantId = context.tenantId ?: throw IllegalStateException("No tenant")

        // Process order...
    }
}
```

### Repository Pattern

```kotlin
class PolicyRepository : BaseContextAwareService() {

    suspend fun findByType(policyType: String) = withTenant { tenantId ->
        database.query<Policy>(
            """
            SELECT * FROM policies
            WHERE tenant_id = ? AND type = ?
            """,
            tenantId, policyType
        )
    }

    suspend fun findById(policyId: String) = withTenant { tenantId ->
        database.queryOne<Policy>(
            """
            SELECT * FROM policies
            WHERE tenant_id = ? AND id = ?
            """,
            tenantId, policyId
        )
    }

    suspend fun create(policy: Policy) = withTenantAndUser { tenantId, userId ->
        policy.copy(
            tenantId = tenantId,
            createdBy = userId,
            createdAt = Instant.now()
        ).also { newPolicy ->
            database.insert(newPolicy)
        }
    }
}
```

## Advanced Patterns

### Nested Context Enrichment

Enrich context as it flows through layers:

```kotlin
withAgentContext("tenantId" to "ACME") {
    // Base context: tenantId

    withEnrichedContext("userId" to "user-123") {
        // Enriched: tenantId + userId

        withEnrichedContext("sessionId" to "sess-456") {
            // Fully enriched: tenantId + userId + sessionId
            tool.execute(params)
        }
    }
}
```

### Context-Aware Validation

Use context in output validation:

```kotlin
contextAwareTool("submit_report") {
    validate {
        requireField("reportId")

        // Validate against context
        custom("tenant must match") { output, context ->
            val tenantId = (output as? Map<*, *>)?.get("tenantId") as? String
            tenantId == context?.tenantId
        }

        // Validate permissions based on user
        custom("user must have permission") { output, context ->
            val userId = context?.userId ?: return@custom false
            permissionService.hasPermission(userId, "submit_report")
        }
    }

    execute { params, context ->
        // Implementation
    }
}
```

### Context-Aware Caching

Cache per tenant:

```kotlin
contextAwareTool("expensive_lookup") {
    cache {
        ttl = 600
        maxSize = 1000

        // Tenant-aware cache keys
        keyBuilder = { params, context ->
            val tenantId = context.tenantId ?: "default"
            val lookupId = params["id"] as String
            "$tenantId:$lookupId"
        }
    }

    execute { params, context ->
        // Expensive operation...
    }
}
```

### Async Operations

Context propagates through async operations:

```kotlin
contextAwareTool("parallel_lookup") {
    execute { params, context ->
        val tenantId = context.tenantId!!

        // Launch multiple async operations
        coroutineScope {
            val customer = async {
                customerService.find(tenantId, params["customerId"] as String)
            }

            val orders = async {
                orderService.findByCustomer(tenantId, params["customerId"] as String)
            }

            val tickets = async {
                ticketService.findByCustomer(tenantId, params["customerId"] as String)
            }

            // All async operations have context!
            mapOf(
                "customer" to customer.await(),
                "orders" to orders.await(),
                "tickets" to tickets.await()
            )
        }
    }
}
```

## Error Handling

### Missing Context

Tools automatically fail if context is missing:

```kotlin
// Without context - ERROR!
val result = tool.execute(params)
// Returns: ToolResult.error("No AgentContext available")

// With context - Works!
withAgentContext("tenantId" to "ACME") {
    val result = tool.execute(params)  // ✅ Success
}
```

### Graceful Defaults

Handle optional context values:

```kotlin
execute { params, context ->
    val tenantId = context.tenantId ?: "default"
    val userId = context.userId ?: "system"

    // Proceed with defaults
    processRequest(tenantId, userId, params)
}
```

### Context Validation

Validate required context values:

```kotlin
execute { params, context ->
    require(context.tenantId != null) { "Tenant ID required" }
    require(context.userId != null) { "User ID required" }

    // Proceed with validated context
}
```

## Best Practices

### 1. Set Context at Boundaries

Set context once at application entry points:

```kotlin
// ✅ Good: Set at HTTP boundary
@PostMapping("/api/orders")
suspend fun createOrder(@RequestBody request: OrderRequest) =
    withAgentContext(
        "tenantId" to extractTenant(request),
        "userId" to extractUser(request)
    ) {
        orderAgent.processComm(comm)
    }

// ❌ Bad: Setting context deep in business logic
suspend fun processOrder(orderId: String) {
    withAgentContext("tenantId" to "ACME") {  // Too late!
        // ...
    }
}
```

### 2. Use Type-Safe Properties

Use standard properties instead of generic `get()`:

```kotlin
// ✅ Good: Type-safe
val tenantId = context.tenantId
val userId = context.userId

// ❌ Bad: Stringly-typed
val tenantId = context.get("tenantId") as? String
```

### 3. Fail Fast on Missing Context

Don't silently use defaults for critical operations:

```kotlin
// ✅ Good: Fail fast
execute { params, context ->
    val tenantId = context.tenantId
        ?: throw IllegalStateException("Tenant required for billing")

    billingService.charge(tenantId, amount)
}

// ❌ Bad: Silent default
execute { params, context ->
    val tenantId = context.tenantId ?: "default"
    billingService.charge(tenantId, amount)  // Dangerous!
}
```

### 4. Enrich, Don't Replace

Use `withEnrichedContext` to add context, not replace:

```kotlin
// ✅ Good: Enrich existing context
withAgentContext("tenantId" to "ACME") {
    withEnrichedContext("sessionId" to "sess-123") {
        // Has both tenantId and sessionId
    }
}

// ❌ Bad: Replace context
withAgentContext("tenantId" to "ACME") {
    withAgentContext("sessionId" to "sess-123") {
        // Lost tenantId!
    }
}
```

### 5. Document Context Requirements

Document what context your tools need:

```kotlin
/**
 * Lookup customer policy
 *
 * Required Context:
 * - tenantId: Tenant identifier (required)
 * - userId: User identifier (optional, defaults to "system")
 */
val lookupPolicy = contextAwareTool("lookup_policy") {
    // ...
}
```

## Testing

### Unit Testing Context-Aware Tools

```kotlin
@Test
fun `test context-aware tool execution`() = runTest {
    val tool = contextAwareTool("test_tool") {
        execute { params, context ->
            "Tenant: ${context.tenantId}, User: ${context.userId}"
        }
    }

    // Test with context
    val result = withAgentContext(
        "tenantId" to "TEST",
        "userId" to "user-1"
    ) {
        tool.execute(emptyMap())
    }

    assertTrue(result.isSuccess)
    val output = result.getOrNull()!!.result
    assertEquals("Tenant: TEST, User: user-1", output)
}

@Test
fun `test missing context error`() = runTest {
    val tool = contextAwareTool("test_tool") {
        execute { params, context ->
            context.tenantId!!  // Requires tenantId
        }
    }

    // Execute without context
    val result = tool.execute(emptyMap())

    assertTrue(result.isSuccess)
    val toolResult = result.getOrNull()!!
    assertFalse(toolResult.success)
    assertTrue(toolResult.error!!.contains("No AgentContext"))
}
```

### Integration Testing

```kotlin
@Test
fun `test multi-tenant isolation`() = runTest {
    val orderTool = contextAwareTool("get_orders") {
        execute { params, context ->
            orderService.findOrders()  // Uses context internally
        }
    }

    // Tenant A
    val ordersA = withAgentContext("tenantId" to "TENANT-A") {
        orderTool.execute(emptyMap())
    }

    // Tenant B
    val ordersB = withAgentContext("tenantId" to "TENANT-B") {
        orderTool.execute(emptyMap())
    }

    // Verify isolation
    assertNotEquals(ordersA, ordersB)
}
```

## See Also

- [Output Validation](./output-validation.md) - Validate tool outputs
- [Tool Caching](../performance/tool-caching.md) - Cache tool results
- [Context Propagation](../advanced/context-propagation.md) - Deep dive into context system
- [Multi-Tenancy](../security/multi-tenancy.md) - Multi-tenant architecture
- [Testing Guide](../testing/context-testing.md) - Test context-aware code

## Migration from v0.3.0

### Before v0.4.0

```kotlin
val tool = SimpleTool("lookup") { params ->
    val tenantId = params["tenantId"] as String  // Manual!
    val userId = params["userId"] as String      // Manual!

    policyRepo.find(tenantId, userId)
}

// Manually pass context
tool.execute(mapOf(
    "tenantId" to "ACME",
    "userId" to "user-123"
))
```

### After v0.4.0

```kotlin
val tool = contextAwareTool("lookup") {
    execute { params, context ->
        // Automatic context injection!
        val tenantId = context.tenantId!!
        val userId = context.userId!!

        policyRepo.find(tenantId, userId)
    }
}

// Set context once
withAgentContext(
    "tenantId" to "ACME",
    "userId" to "user-123"
) {
    tool.execute(emptyMap())  // No context in params!
}
```

**Benefits**:
- ✅ -50% less code
- ✅ Type-safe context access
- ✅ Impossible to forget context
- ✅ Automatic propagation through all layers
