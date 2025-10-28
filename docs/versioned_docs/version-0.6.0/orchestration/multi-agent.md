# Multi-Agent Systems

> **v0.4.0** - Complete guide to building multi-agent systems with automatic context propagation

Build complex, production-ready multi-agent applications with perfect tenant isolation, distributed tracing, and zero-boilerplate context management.

## Table of Contents

- [Overview](#overview)
- [Context Propagation in Multi-Agent Systems](#context-propagation-in-multi-agent-systems)
- [Agent Communication](#agent-communication)
- [Multi-Tenant Multi-Agent Systems](#multi-tenant-multi-agent-systems)
- [Context-Aware Tools](#context-aware-tools)
- [Service Layer Integration](#service-layer-integration)
- [Real-World Patterns](#real-world-patterns)
- [Best Practices](#best-practices)

## Overview

Multi-agent systems coordinate multiple specialized agents to solve complex problems. In **v0.4.0**, Spice Framework makes this dramatically simpler with **automatic context propagation**.

### Key Features

✅ **Automatic context propagation** - Set once, available everywhere
✅ **Perfect tenant isolation** - Zero risk of data leakage
✅ **Distributed tracing** - End-to-end request tracking
✅ **Zero boilerplate** - No manual parameter passing
✅ **Type-safe** - Compile-time safety for context values
✅ **Production-ready** - Thread-safe, performant, battle-tested

### Architecture

```
HTTP Request
    ↓ (withAgentContext)
AgentContext (as CoroutineContext.Element)
    ↓ (automatic propagation)
Coordinator Agent
    ├─→ Specialist Agent A (has context)
    ├─→ Specialist Agent B (has context)
    └─→ Specialist Agent C (has context)
            ↓ (coroutineContext[AgentContext])
        Tools (automatic injection)
            ↓ (service methods)
        Services & Repositories (automatic access)
```

## Context Propagation in Multi-Agent Systems

### The v0.4.0 Revolution

**Before (v0.3.0):**
```kotlin
// 😫 Manual context passing everywhere
val context = AgentContext.of("tenantId" to "CHIC", "userId" to "user-123")
val runtime1 = DefaultAgentRuntime(context = context)
val runtime2 = DefaultAgentRuntime(context = context)
val runtime3 = DefaultAgentRuntime(context = context)

agent1.processComm(comm, runtime1)
agent2.processComm(comm, runtime2)
agent3.processComm(comm, runtime3)
```

**After (v0.4.0):**
```kotlin
// ✅ Set once, propagates automatically!
withAgentContext("tenantId" to "CHIC", "userId" to "user-123") {
    agent1.processComm(comm)  // Has context
    agent2.processComm(comm)  // Has context
    agent3.processComm(comm)  // Has context
}
```

### How It Works

AgentContext extends `AbstractCoroutineContextElement`, which means:

1. **Automatic Propagation**: Context flows through all coroutine boundaries
2. **Thread-Safe**: Immutable design prevents concurrent modification
3. **Zero-Cost**: Built on Kotlin's CoroutineContext system
4. **Type-Safe**: Compile-time checking for context keys

```kotlin
// Under the hood
data class AgentContext(
    private val data: Map<String, Any> = emptyMap()
) : AbstractCoroutineContextElement(AgentContext) {
    companion object Key : CoroutineContext.Key<AgentContext>

    // Type-safe accessors
    val tenantId: String?
    val userId: String?
    val sessionId: String?
    // ...
}
```

## Agent Communication

### Basic Agent-to-Agent Communication

```kotlin
// Agent 1: Request Processor
val processorAgent = buildAgent {
    id = "processor"
    name = "Request Processor"

    handle { comm ->
        // Forward to validator with automatic context
        callAgent("validator", comm.forward("validator"))

        comm.reply("Processed", id)
    }
}

// Agent 2: Validator
val validatorAgent = buildAgent {
    id = "validator"
    name = "Request Validator"

    handle { comm ->
        // Access context automatically
        val tenantId = currentTenantId()  // ✅ From context!

        comm.reply("Validated for tenant $tenantId", id)
    }
}

// Usage
withAgentContext("tenantId" to "ACME") {
    processorAgent.processComm(
        Comm(content = "Request", from = "user")
    )
}
```

### Comm with Embedded Context

Messages can carry their own context:

```kotlin
// Create comm with context
val comm = Comm(
    content = "Process this",
    from = "user"
).withContextValues(
    "tenantId" to "CHIC",
    "userId" to "user-123",
    "sessionId" to "sess-456"
)

// Agent receives comm with context
val agent = buildAgent {
    id = "contextual-agent"

    handle { comm ->
        // Access context from comm or coroutineContext
        val tenantId = comm.getContextValue("tenantId")

        comm.reply("Processed for tenant $tenantId", id)
    }
}
```

## Multi-Tenant Multi-Agent Systems

### Complete Multi-Tenant Example

```kotlin
// Repository with automatic tenant scoping
class OrderRepository : BaseContextAwareService() {
    suspend fun findOrders(): List<Order> = withTenant { tenantId ->
        database.query("SELECT * FROM orders WHERE tenant_id = ?", tenantId)
    }

    suspend fun createOrder(items: List<String>): Order = withTenantAndUser { tenantId, userId ->
        Order(
            id = generateId(),
            tenantId = tenantId,
            userId = userId,
            items = items,
            createdAt = Instant.now()
        )
    }
}

// Validation Agent
val validationAgent = buildAgent {
    id = "validator"
    name = "Order Validator"

    contextAwareTool("validate_order") {
        description = "Validate order for tenant"
        param("items", "array", "Order items")

        execute { params, context ->
            val tenantId = context.tenantId ?: throw IllegalStateException("No tenant")
            val items = params["items"] as List<*>

            // Tenant-specific validation logic
            val maxItems = getTenantLimit(tenantId)

            if (items.size > maxItems) {
                "Error: Tenant $tenantId limit is $maxItems items"
            } else {
                "Valid for tenant $tenantId"
            }
        }
    }
}

// Order Processing Agent
val orderAgent = buildAgent {
    id = "order-processor"
    name = "Order Processor"

    val orderRepo = OrderRepository()

    contextAwareTool("create_order") {
        description = "Create new order"
        param("items", "array", "Order items")

        execute { params, context ->
            val items = (params["items"] as List<*>).map { it.toString() }

            // Service automatically gets tenant context!
            val order = orderRepo.createOrder(items)

            "Order ${order.id} created for tenant ${order.tenantId}"
        }
    }
}

// Coordinator Agent
val coordinatorAgent = buildAgent {
    id = "coordinator"
    name = "Order Coordinator"

    handle { comm ->
        // 1. Validate
        val validationResult = callAgent("validator", comm)

        if (validationResult.isSuccess) {
            // 2. Process
            val orderResult = callAgent("order-processor", comm)
            comm.reply("Order created: ${orderResult.content}", id)
        } else {
            comm.reply("Validation failed", id)
        }
    }
}

// Usage: Tenant A
withAgentContext(
    "tenantId" to "TENANT_A",
    "userId" to "user-a1",
    "correlationId" to "req-001"
) {
    coordinatorAgent.processComm(
        Comm(content = "Create order: laptop, mouse", from = "user-a1")
    )
}

// Usage: Tenant B (completely isolated!)
withAgentContext(
    "tenantId" to "TENANT_B",
    "userId" to "user-b1",
    "correlationId" to "req-002"
) {
    coordinatorAgent.processComm(
        Comm(content = "Create order: keyboard", from = "user-b1")
    )
}
```

### Tenant Isolation Guarantees

```kotlin
// ✅ SAFE: Each request is completely isolated
launch {
    withAgentContext("tenantId" to "ACME") {
        processRequest1()  // Only sees ACME data
    }
}

launch {
    withAgentContext("tenantId" to "GLOBEX") {
        processRequest2()  // Only sees GLOBEX data
    }
}

// ❌ IMPOSSIBLE: Cross-tenant data access
// Context is immutable and coroutine-scoped
```

## Context-Aware Tools

### Creating Context-Aware Tools

```kotlin
// Simple context-aware tool
val getTenantInfo = contextAwareTool("get_tenant_info") {
    description = "Get tenant information"

    execute { params, context ->
        val tenantId = context.tenantId ?: "default"
        val userId = context.userId ?: "unknown"

        "Tenant: $tenantId, User: $userId"
    }
}

// Tool with parameters
val policyLookup = contextAwareTool("policy_lookup") {
    description = "Look up policy by type"
    param("policyType", "string", "Policy type", required = true)

    execute { params, context ->
        val tenantId = context.tenantId ?: throw IllegalStateException("No tenant")
        val policyType = params["policyType"] as String

        // Access service with automatic context
        policyService.findByType(policyType)
    }
}

// Use in agent
buildAgent {
    id = "policy-agent"

    contextAwareTool("check_policy") {
        description = "Check policy status"
        param("policyId", "string", "Policy ID")

        execute { params, context ->
            // ✅ context available automatically!
            val tenantId = context.tenantId
            val policyId = params["policyId"] as String

            policyService.checkStatus(policyId)
        }
    }
}
```

### Simple Context Tool (Minimal DSL)

```kotlin
// For simple use cases
simpleContextTool("get_user", "Get current user") { params, context ->
    "User: ${context.userId}"
}

// In agent
buildAgent {
    id = "user-agent"

    simpleContextTool("whoami", "Get current user") { params, context ->
        "You are ${context.userId} in tenant ${context.tenantId}"
    }
}
```

## Service Layer Integration

### Context-Aware Services

```kotlin
// Base service with automatic context access
class PolicyService : BaseContextAwareService() {

    // Automatic tenant scoping
    suspend fun findByType(type: String): Policy = withTenant { tenantId ->
        repository.find(tenantId, type)
    }

    // Automatic tenant + user scoping
    suspend fun createPolicy(type: String): Policy = withTenantAndUser { tenantId, userId ->
        Policy(
            id = generateId(),
            tenantId = tenantId,
            type = type,
            createdBy = userId,
            createdAt = Instant.now()
        )
    }

    // Optional tenant with default
    suspend fun getGlobalPolicies(): List<Policy> = withTenantOrDefault("global") { tenantId ->
        repository.findAll(tenantId)
    }
}

// Usage in agent
buildAgent {
    id = "policy-agent"

    val policyService = PolicyService()

    contextAwareTool("create_policy") {
        description = "Create policy"
        param("type", "string", "Policy type")

        execute { params, context ->
            val type = params["type"] as String

            // ✅ Service gets tenant & user automatically!
            val policy = policyService.createPolicy(type)

            "Created policy ${policy.id}"
        }
    }
}
```

### Repository Pattern

```kotlin
class OrderRepository : BaseContextAwareService() {

    // Find all orders for current tenant
    suspend fun findAll(): List<Order> = withTenant { tenantId ->
        database.query(
            "SELECT * FROM orders WHERE tenant_id = ?",
            tenantId
        )
    }

    // Find orders for current tenant and user
    suspend fun findMyOrders(): List<Order> = withTenantAndUser { tenantId, userId ->
        database.query(
            "SELECT * FROM orders WHERE tenant_id = ? AND user_id = ?",
            tenantId, userId
        )
    }

    // Create order with audit trail
    suspend fun create(items: List<String>): Order = withTenantAndUser { tenantId, userId ->
        val context = getContext()
        val correlationId = context.correlationId ?: "none"

        Order(
            id = generateId(),
            tenantId = tenantId,
            userId = userId,
            items = items,
            correlationId = correlationId,
            createdAt = Instant.now()
        )
    }
}
```

## Real-World Patterns

### Pattern 1: E-Commerce Order Processing

```kotlin
// Services
class ProductCatalogService : BaseContextAwareService() {
    suspend fun validateProducts(productIds: List<String>): ValidationResult = withTenant { tenantId ->
        // Tenant-specific product catalog
        val products = productRepository.findByIds(tenantId, productIds)

        if (products.size != productIds.size) {
            ValidationResult.failure("Some products not found for tenant $tenantId")
        } else {
            ValidationResult.success()
        }
    }
}

class InventoryService : BaseContextAwareService() {
    suspend fun checkAvailability(productIds: List<String>): Map<String, Int> = withTenant { tenantId ->
        inventoryRepository.getStockLevels(tenantId, productIds)
    }
}

class OrderService : BaseContextAwareService() {
    suspend fun createOrder(items: List<OrderItem>): Order = withTenantAndUser { tenantId, userId ->
        Order(
            id = generateOrderId(),
            tenantId = tenantId,
            userId = userId,
            items = items,
            status = OrderStatus.PENDING,
            createdAt = Instant.now()
        )
    }
}

// Agents
val catalogAgent = buildAgent {
    id = "catalog-agent"
    name = "Product Catalog Agent"

    val catalogService = ProductCatalogService()

    contextAwareTool("validate_products") {
        description = "Validate product availability"
        param("productIds", "array", "Product IDs")

        execute { params, context ->
            val productIds = (params["productIds"] as List<*>).map { it.toString() }
            val result = catalogService.validateProducts(productIds)

            if (result.isValid) "Products validated" else "Invalid: ${result.error}"
        }
    }
}

val inventoryAgent = buildAgent {
    id = "inventory-agent"
    name = "Inventory Agent"

    val inventoryService = InventoryService()

    contextAwareTool("check_stock") {
        description = "Check stock availability"
        param("productIds", "array", "Product IDs")

        execute { params, context ->
            val productIds = (params["productIds"] as List<*>).map { it.toString() }
            val stock = inventoryService.checkAvailability(productIds)

            "Stock: ${stock.entries.joinToString { "${it.key}=${it.value}" }}"
        }
    }
}

val orderAgent = buildAgent {
    id = "order-agent"
    name = "Order Processing Agent"

    val orderService = OrderService()

    contextAwareTool("create_order") {
        description = "Create new order"
        param("items", "array", "Order items")

        execute { params, context ->
            val items = (params["items"] as List<*>).map { it.toString() }
            val orderItems = items.map { OrderItem(productId = it, quantity = 1) }

            val order = orderService.createOrder(orderItems)

            "Order ${order.id} created for tenant ${order.tenantId}"
        }
    }
}

// Coordinator
val ecommerceCoordinator = buildAgent {
    id = "ecommerce-coordinator"
    name = "E-Commerce Coordinator"

    handle { comm ->
        // All agents automatically share context!

        // 1. Validate products
        val catalogResult = callAgent("catalog-agent", comm)

        if (!catalogResult.content.contains("validated")) {
            return@handle comm.reply("Catalog validation failed", id)
        }

        // 2. Check inventory
        val inventoryResult = callAgent("inventory-agent", comm)

        if (!inventoryResult.content.contains("Stock")) {
            return@handle comm.reply("Inventory check failed", id)
        }

        // 3. Create order
        val orderResult = callAgent("order-agent", comm)

        comm.reply("Order created: ${orderResult.content}", id)
    }
}

// Usage
withAgentContext(
    "tenantId" to "ECOMMERCE_STORE_1",
    "userId" to "customer-12345",
    "sessionId" to "sess-67890",
    "correlationId" to "req-${UUID.randomUUID()}"
) {
    ecommerceCoordinator.processComm(
        Comm(
            content = "Create order: prod-A, prod-B, prod-C",
            from = "customer-12345"
        )
    )
}
```

### Pattern 2: Customer Support Multi-Agent

```kotlin
class TicketService : BaseContextAwareService() {
    suspend fun createTicket(subject: String, description: String): Ticket = withTenantAndUser { tenantId, userId ->
        Ticket(
            id = generateTicketId(),
            tenantId = tenantId,
            userId = userId,
            subject = subject,
            description = description,
            status = TicketStatus.OPEN,
            createdAt = Instant.now()
        )
    }

    suspend fun findMyTickets(): List<Ticket> = withTenantAndUser { tenantId, userId ->
        ticketRepository.findByUser(tenantId, userId)
    }
}

class KnowledgeBaseService : BaseContextAwareService() {
    suspend fun search(query: String): List<Article> = withTenant { tenantId ->
        // Tenant-specific knowledge base
        kbRepository.search(tenantId, query)
    }
}

// Tier 1 Support Agent
val tier1Agent = buildAgent {
    id = "tier1-support"
    name = "Tier 1 Support"
    llm = anthropic {
        model = "claude-3-5-haiku-20241022"
    }

    instructions = """
        You are a Tier 1 support agent.
        - Handle basic inquiries
        - Search knowledge base
        - Escalate complex issues to Tier 2
    """.trimIndent()

    val kbService = KnowledgeBaseService()

    contextAwareTool("search_kb") {
        description = "Search knowledge base"
        param("query", "string", "Search query")

        execute { params, context ->
            val query = params["query"] as String
            val articles = kbService.search(query)

            "Found ${articles.size} articles for tenant ${context.tenantId}"
        }
    }
}

// Tier 2 Support Agent
val tier2Agent = buildAgent {
    id = "tier2-support"
    name = "Tier 2 Support"
    llm = anthropic {
        model = "claude-3-5-sonnet-20241022"
    }

    instructions = """
        You are a Tier 2 support agent.
        - Handle complex technical issues
        - Create support tickets
        - Escalate to engineering if needed
    """.trimIndent()

    val ticketService = TicketService()

    contextAwareTool("create_ticket") {
        description = "Create support ticket"
        param("subject", "string", "Ticket subject")
        param("description", "string", "Issue description")

        execute { params, context ->
            val subject = params["subject"] as String
            val description = params["description"] as String

            val ticket = ticketService.createTicket(subject, description)

            "Ticket ${ticket.id} created for ${context.userId}"
        }
    }
}

// Support Coordinator
val supportCoordinator = buildAgent {
    id = "support-coordinator"
    name = "Support Coordinator"

    handle { comm ->
        // Route based on complexity
        val complexity = assessComplexity(comm.content)

        val result = when (complexity) {
            Complexity.BASIC -> callAgent("tier1-support", comm)
            Complexity.INTERMEDIATE -> callAgent("tier2-support", comm)
            Complexity.ADVANCED -> callAgent("tier2-support", comm)
        }

        comm.reply("Support response: ${result.content}", id)
    }
}

// Usage
withAgentContext(
    "tenantId" to "COMPANY_XYZ",
    "userId" to "support-customer-789",
    "sessionId" to "support-sess-123",
    "correlationId" to "support-${System.currentTimeMillis()}"
) {
    supportCoordinator.processComm(
        Comm(
            content = "I can't log in to my account",
            from = "support-customer-789"
        )
    )
}
```

### Pattern 3: Financial Transaction Processing

```kotlin
class AccountService : BaseContextAwareService() {
    suspend fun getBalance(accountId: String): BigDecimal = withTenantAndUser { tenantId, userId ->
        accountRepository.getBalance(tenantId, userId, accountId)
    }

    suspend fun validateAccount(accountId: String): Boolean = withTenant { tenantId ->
        accountRepository.exists(tenantId, accountId)
    }
}

class FraudDetectionService : BaseContextAwareService() {
    suspend fun checkTransaction(
        amount: BigDecimal,
        recipient: String
    ): FraudCheckResult = withTenantAndUser { tenantId, userId ->
        val context = getContext()
        val sessionId = context.sessionId

        // Check transaction patterns for this user
        fraudEngine.analyze(tenantId, userId, amount, recipient, sessionId)
    }
}

class TransactionService : BaseContextAwareService() {
    suspend fun executeTransfer(
        fromAccount: String,
        toAccount: String,
        amount: BigDecimal
    ): Transaction = withTenantAndUser { tenantId, userId ->
        val context = getContext()

        Transaction(
            id = generateTxId(),
            tenantId = tenantId,
            userId = userId,
            fromAccount = fromAccount,
            toAccount = toAccount,
            amount = amount,
            correlationId = context.correlationId ?: "none",
            timestamp = Instant.now()
        )
    }
}

// Agents
val validationAgent = buildAgent {
    id = "validator"
    val accountService = AccountService()

    contextAwareTool("validate_accounts") {
        param("fromAccount", "string", "Source account")
        param("toAccount", "string", "Destination account")

        execute { params, context ->
            val from = params["fromAccount"] as String
            val to = params["toAccount"] as String

            val fromValid = accountService.validateAccount(from)
            val toValid = accountService.validateAccount(to)

            if (fromValid && toValid) "Valid" else "Invalid accounts"
        }
    }
}

val fraudAgent = buildAgent {
    id = "fraud-detector"
    val fraudService = FraudDetectionService()

    contextAwareTool("check_fraud") {
        param("amount", "number", "Transaction amount")
        param("recipient", "string", "Recipient")

        execute { params, context ->
            val amount = BigDecimal(params["amount"].toString())
            val recipient = params["recipient"] as String

            val result = fraudService.checkTransaction(amount, recipient)

            if (result.isSafe) "Safe" else "Flagged: ${result.reason}"
        }
    }
}

val transactionAgent = buildAgent {
    id = "transaction-processor"
    val txService = TransactionService()

    contextAwareTool("execute_transfer") {
        param("from", "string", "From account")
        param("to", "string", "To account")
        param("amount", "number", "Amount")

        execute { params, context ->
            val from = params["from"] as String
            val to = params["to"] as String
            val amount = BigDecimal(params["amount"].toString())

            val tx = txService.executeTransfer(from, to, amount)

            "Transaction ${tx.id} completed"
        }
    }
}

// Financial Coordinator
val financialCoordinator = buildAgent {
    id = "financial-coordinator"

    handle { comm ->
        // 1. Validate
        val validationResult = callAgent("validator", comm)
        if (!validationResult.content.contains("Valid")) {
            return@handle comm.reply("Validation failed", id)
        }

        // 2. Fraud check
        val fraudResult = callAgent("fraud-detector", comm)
        if (!fraudResult.content.contains("Safe")) {
            return@handle comm.reply("Transaction flagged: ${fraudResult.content}", id)
        }

        // 3. Execute
        val txResult = callAgent("transaction-processor", comm)

        comm.reply("Transfer completed: ${txResult.content}", id)
    }
}

// Usage with full audit trail
withAgentContext(
    "tenantId" to "BANK_ABC",
    "userId" to "customer-456",
    "sessionId" to "banking-sess-789",
    "correlationId" to "tx-${UUID.randomUUID()}",
    "traceId" to "trace-${System.currentTimeMillis()}"
) {
    financialCoordinator.processComm(
        Comm(
            content = "Transfer $100 from acc-A to acc-B",
            from = "customer-456"
        )
    )
}
```

## Best Practices

### 1. Always Set Context at Entry Point

```kotlin
// ✅ GOOD: Set context at HTTP handler
@PostMapping("/api/orders")
suspend fun createOrder(@RequestBody request: OrderRequest) = withAgentContext(
    "tenantId" to request.tenantId,
    "userId" to getCurrentUser().id,
    "sessionId" to request.sessionId,
    "correlationId" to UUID.randomUUID().toString()
) {
    orderAgent.processComm(
        Comm(content = request.items.joinToString(), from = request.userId)
    )
}

// ❌ BAD: Forget to set context
@PostMapping("/api/orders")
suspend fun createOrder(@RequestBody request: OrderRequest) {
    // Context not set - agents and services will fail!
    orderAgent.processComm(...)
}
```

### 2. Use Type-Safe Accessors

```kotlin
// ✅ GOOD: Use type-safe accessors
val tenantId = context.tenantId  // String?
val userId = context.userId      // String?

// ❌ BAD: Manual string keys everywhere
val tenantId = context.get("tenantId")?.toString()
```

### 3. Validate Required Context

```kotlin
// ✅ GOOD: Validate early
suspend fun processOrder() = withTenantAndUser { tenantId, userId ->
    // Both guaranteed to be present
    orderService.create(tenantId, userId, items)
}

// ❌ BAD: Manual null checks everywhere
suspend fun processOrder() {
    val context = currentAgentContext()
    val tenantId = context?.tenantId ?: throw Exception("No tenant")
    val userId = context?.userId ?: throw Exception("No user")
    // ...
}
```

### 4. Use Context Extension for Enrichment

```kotlin
// ✅ GOOD: Register extensions for automatic enrichment
val tenantExtension = TenantContextExtension { tenantId ->
    mapOf(
        "features" to loadTenantFeatures(tenantId),
        "limits" to loadTenantLimits(tenantId)
    )
}

ContextExtensionRegistry.register(tenantExtension)

// Now all contexts automatically enriched!
```

### 5. Include Correlation IDs for Tracing

```kotlin
// ✅ GOOD: Always include correlation ID
withAgentContext(
    "tenantId" to tenantId,
    "userId" to userId,
    "correlationId" to UUID.randomUUID().toString(),  // ✅
    "traceId" to generateTraceId()                     // ✅
) {
    processRequest()
}

// ❌ BAD: No tracing information
withAgentContext("tenantId" to tenantId) {
    processRequest()  // Can't trace this request!
}
```

### 6. Document Context Requirements

```kotlin
/**
 * Process customer order
 *
 * **Required Context:**
 * - `tenantId` (String) - Customer's tenant ID
 * - `userId` (String) - User making the order
 * - `sessionId` (String) - Current session
 *
 * **Optional Context:**
 * - `correlationId` (String) - Request correlation ID
 * - `locale` (String) - User's locale
 */
suspend fun processOrder(items: List<String>) = withTenantAndUser { tenantId, userId ->
    // Implementation
}
```

### 7. Test with Different Tenant Contexts

```kotlin
@Test
fun `order processing should isolate tenants`() = runTest {
    // Test Tenant A
    withAgentContext("tenantId" to "TENANT_A") {
        val result = processOrder(listOf("item1"))
        assertEquals("TENANT_A", result.tenantId)
    }

    // Test Tenant B (isolated!)
    withAgentContext("tenantId" to "TENANT_B") {
        val result = processOrder(listOf("item2"))
        assertEquals("TENANT_B", result.tenantId)
    }
}
```

## Summary

Multi-agent systems in Spice Framework v0.4.0:

✅ **Zero boilerplate** - Set context once, available everywhere
✅ **Perfect isolation** - Tenant data never leaks
✅ **Type-safe** - Compile-time checking
✅ **Production-ready** - Thread-safe, performant
✅ **Easy testing** - Simple to test with different contexts
✅ **Full tracing** - End-to-end request tracking

## Next Steps

- [Swarm Documentation](./swarm.md) - Multi-agent coordination patterns
- [Context Propagation Guide](../advanced/context-propagation.md) - Deep dive into context system
- [Tool Patterns](../tools-extensions/tool-patterns.md) - Advanced tool patterns
- [API Reference](../api/dsl.md) - Complete API documentation
