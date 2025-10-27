# Context API

Thread-safe context propagation for multi-tenant, multi-user agent systems in Spice Framework.

## Overview

The Context API provides **automatic context propagation** through coroutines, enabling:

- **Multi-Tenancy** - Perfect tenant isolation without manual passing
- **User Scoping** - Automatic user context in all operations
- **Request Tracking** - Correlation IDs and session tracking
- **Context Enrichment** - Runtime context augmentation via extensions
- **Type Safety** - Compile-time guarantees for context access
- **Zero Boilerplate** - Context automatically flows through async operations

**Key Features:**
- ✅ Automatic propagation through coroutines (no manual passing!)
- ✅ Immutable, thread-safe design
- ✅ Context-aware tools with automatic injection
- ✅ Service layer helpers (`withTenant`, `withTenantAndUser`)
- ✅ Extensible enrichment system
- ✅ Comm integration for message-level context

## Core Concept

AgentContext is a **Coroutine Context Element**, meaning it automatically propagates through all coroutine operations:

```kotlin
// Set context once at the boundary
withAgentContext("tenantId" to "ACME", "userId" to "user-123") {

    // Context automatically available in ALL nested operations!
    agent.processComm(comm)         // ✅ Has context
    repository.findOrders()         // ✅ Has context
    tool.execute(params)            // ✅ Has context

    launch {
        deeplyNestedFunction()      // ✅ Still has context
    }
}
```

**Before v0.4.0** (Manual passing):
```kotlin
// ❌ Error-prone manual passing
fun processOrder(orderId: String, tenantId: String, userId: String) {
    val order = orderRepo.find(orderId, tenantId)  // Pass tenantId
    val policy = policyRepo.find(order.policyId, tenantId)  // Pass again
    auditLog.record(order, tenantId, userId)  // Pass both
}
```

**v0.4.0+** (Automatic propagation):
```kotlin
// ✅ Clean! Context flows automatically
suspend fun processOrder(orderId: String) = withTenantAndUser { tenantId, userId ->
    val order = orderRepo.find(orderId)      // Gets tenantId automatically
    val policy = policyRepo.find(order.policyId)  // Gets tenantId automatically
    auditLog.record(order)                   // Gets both automatically
}
```

## AgentContext

### Creating Context

#### AgentContext.of()

Create context from key-value pairs:

```kotlin
// Basic context
val context = AgentContext.of(
    "tenantId" to "ACME",
    "userId" to "user-123"
)

// Rich context with all standard fields
val context = AgentContext.of(
    "tenantId" to "CORP_A",
    "userId" to "user-456",
    "sessionId" to "sess-789",
    "correlationId" to "req-${UUID.randomUUID()}",
    "permissions" to listOf("read", "write", "admin"),
    "features" to mapOf("premium" to true, "beta" to false)
)

// Empty context
val empty = AgentContext.empty()
```

#### AgentContext.builder()

Build context with fluent API:

```kotlin
val context = AgentContext.builder()
    .tenantId("ACME")
    .userId("user-123")
    .sessionId("sess-456")
    .correlationId("corr-789")
    .set("customKey", "customValue")
    .set("permissions", listOf("read", "write"))
    .build()
```

### Accessing Context Values

#### Type-Safe Properties

Standard context values accessible via properties:

```kotlin
val context = AgentContext.of(
    "tenantId" to "ACME",
    "userId" to "user-123",
    "sessionId" to "sess-456",
    "correlationId" to "corr-789"
)

// Type-safe property access
val tenantId: String? = context.tenantId
val userId: String? = context.userId
val sessionId: String? = context.sessionId
val correlationId: String? = context.correlationId

// Null-safe usage
val tenant = context.tenantId ?: "DEFAULT_TENANT"
```

**Available Properties:**
- `tenantId: String?` - Tenant identifier
- `userId: String?` - User identifier
- `sessionId: String?` - Session identifier
- `correlationId: String?` - Request correlation ID

#### get() / getAs()

Access custom context values:

```kotlin
val context = AgentContext.of(
    "tenantId" to "ACME",
    "permissions" to listOf("read", "write"),
    "config" to mapOf("theme" to "dark", "locale" to "en-US"),
    "metadata" to CustomMetadata(...)
)

// Get with type inference
val permissions: Any? = context.get("permissions")

// Get with explicit type
val permissions: List<String>? = context.getAs<List<String>>("permissions")
val config: Map<String, String>? = context.getAs<Map<String, String>>("config")
val metadata: CustomMetadata? = context.getAs<CustomMetadata>("metadata")

// Safe access with defaults
val locale = context.getAs<String>("locale") ?: "en-US"
```

#### has() / contains()

Check for key existence:

```kotlin
if (context.has("tenantId")) {
    val tenantId = context.tenantId!!
    processTenantSpecific(tenantId)
}

if ("permissions" in context) {
    val perms = context.getAs<List<String>>("permissions")!!
    checkPermissions(perms)
}
```

### Modifying Context

AgentContext is **immutable**. Modifications create new instances:

#### with() / set()

Add or update values:

```kotlin
val base = AgentContext.of("tenantId" to "ACME")

// Add single value
val withUser = base.with("userId", "user-123")

// Add multiple values (chainable)
val enriched = base
    .with("userId", "user-123")
    .with("sessionId", "sess-456")
    .with("correlationId", "corr-789")

// Builder-style set
val context = base
    .set("permissions", listOf("read", "write"))
    .set("features", mapOf("beta" to true))
```

#### without()

Remove values:

```kotlin
val context = AgentContext.of(
    "tenantId" to "ACME",
    "userId" to "user-123",
    "tempData" to "..."
)

// Remove temporary data
val cleaned = context.without("tempData")

// Remove multiple keys
val minimal = context
    .without("tempData")
    .without("cache")
```

#### merge()

Merge two contexts:

```kotlin
val baseContext = AgentContext.of(
    "tenantId" to "ACME",
    "userId" to "user-123"
)

val additionalContext = AgentContext.of(
    "sessionId" to "sess-456",
    "permissions" to listOf("read", "write")
)

// Merge (additionalContext values take precedence)
val merged = baseContext.merge(additionalContext)

// Result has all keys:
// tenantId, userId, sessionId, permissions
```

### Utility Methods

#### toMap()

Convert to map:

```kotlin
val context = AgentContext.of(
    "tenantId" to "ACME",
    "userId" to "user-123"
)

val map: Map<String, Any> = context.toMap()
// {"tenantId": "ACME", "userId": "user-123"}
```

#### isEmpty() / isNotEmpty()

Check if context has values:

```kotlin
val empty = AgentContext.empty()
println(empty.isEmpty())      // true
println(empty.isNotEmpty())   // false

val context = AgentContext.of("tenantId" to "ACME")
println(context.isEmpty())     // false
println(context.isNotEmpty())  // true
```

## Context DSL

### withAgentContext

Set context for a coroutine scope:

```kotlin
suspend fun withAgentContext(
    vararg pairs: Pair<String, Any>,
    block: suspend CoroutineScope.() -> T
): T
```

**Usage:**

```kotlin
// Set context for scope
withAgentContext(
    "tenantId" to "ACME",
    "userId" to "user-123"
) {
    // All operations in this scope have context
    val result = agent.processComm(comm)
    val orders = repository.findOrders()

    // Nested coroutines inherit context
    launch {
        processOrders()  // Still has context!
    }
}

// Context with AgentContext instance
val context = AgentContext.of("tenantId" to "ACME")
withAgentContext(context) {
    agent.processComm(comm)
}
```

**Multi-Tenant Example:**

```kotlin
// Process for Tenant A
val tenantAResult = withAgentContext(
    "tenantId" to "TENANT_A",
    "userId" to "user-a1"
) {
    agent.processComm(comm)
}

// Process for Tenant B (complete isolation!)
val tenantBResult = withAgentContext(
    "tenantId" to "TENANT_B",
    "userId" to "user-b1"
) {
    agent.processComm(comm)
}
```

### currentAgentContext

Get current context from coroutine:

```kotlin
suspend fun currentAgentContext(): AgentContext?
```

**Usage:**

```kotlin
suspend fun processOrder() {
    val context = currentAgentContext()

    if (context != null) {
        val tenantId = context.tenantId
        val userId = context.userId

        println("Processing for tenant: $tenantId by user: $userId")
    } else {
        throw IllegalStateException("No context available")
    }
}

// Call within context scope
withAgentContext("tenantId" to "ACME") {
    processOrder()  // Has context
}
```

**Require Context:**

```kotlin
suspend fun requireContext(): AgentContext {
    return currentAgentContext()
        ?: throw IllegalStateException("AgentContext required but not found")
}

suspend fun processWithContext() {
    val context = requireContext()  // Fails if no context
    // Safe to use context
}
```

### withEnrichedContext

Add values to existing context:

```kotlin
suspend fun withEnrichedContext(
    vararg pairs: Pair<String, Any>,
    block: suspend CoroutineScope.() -> T
): T
```

**Usage:**

```kotlin
withAgentContext("tenantId" to "ACME") {

    // Add session ID to existing context
    withEnrichedContext("sessionId" to "sess-123") {

        val context = currentAgentContext()!!
        println(context.tenantId)    // "ACME" (from outer)
        println(context.sessionId)   // "sess-123" (from inner)

        // Add correlation ID
        withEnrichedContext("correlationId" to "corr-456") {
            val ctx = currentAgentContext()!!
            println(ctx.tenantId)        // "ACME"
            println(ctx.sessionId)       // "sess-123"
            println(ctx.correlationId)   // "corr-456"
        }
    }
}
```

**Layered Context Example:**

```kotlin
// HTTP request handler
suspend fun handleRequest(request: HttpRequest) {
    // Set base context from request
    withAgentContext(
        "tenantId" to request.tenantId,
        "userId" to request.userId
    ) {
        // Enrich with session
        withEnrichedContext("sessionId" to request.sessionId) {

            // Enrich with correlation ID for this request
            withEnrichedContext("correlationId" to UUID.randomUUID().toString()) {

                // All nested operations have full context!
                processRequest(request)
            }
        }
    }
}
```

## Context-Aware Tools

### contextAwareTool

Create tools that automatically receive AgentContext:

```kotlin
fun contextAwareTool(
    name: String,
    builder: ContextAwareToolBuilder.() -> Unit
): Tool
```

**Usage:**

```kotlin
val lookupTool = contextAwareTool("lookup_policy") {
    description = "Look up policy by type"
    param("policyType", "string", "Policy type", required = true)

    execute { params, context ->
        // Context automatically injected!
        val tenantId = context.tenantId ?: throw IllegalStateException("No tenant")
        val policyType = params["policyType"] as String

        val policy = policyRepo.findByType(policyType, tenantId)
        "Found policy: ${policy.id} for tenant $tenantId"
    }
}

// Use tool within context
withAgentContext("tenantId" to "ACME") {
    val result = lookupTool.execute(mapOf("policyType" to "auto"))
    // Tool automatically got tenantId = "ACME"
}
```

**In Agent Builder:**

```kotlin
val agent = buildAgent {
    id = "policy-agent"
    name = "Policy Agent"

    contextAwareTool("lookup_policy") {
        description = "Look up policy by type"
        param("policyType", "string", "Policy type")

        execute { params, context ->
            val tenantId = context.tenantId ?: "default"
            val policyType = params["policyType"] as String

            // Service call with automatic tenant scoping
            val policy = policyRepo.findByType(policyType)
            "Policy: ${policy.id} for tenant $tenantId"
        }
    }

    contextAwareTool("create_policy") {
        description = "Create new policy"
        param("policyType", "string", "Policy type")
        param("premium", "boolean", "Premium policy")

        execute { params, context ->
            val tenantId = context.tenantId!!
            val userId = context.userId!!
            val policyType = params["policyType"] as String
            val premium = params["premium"] as Boolean

            val policy = policyRepo.create(policyType, premium, tenantId, userId)
            "Created policy ${policy.id}"
        }
    }
}
```

**Tool Builder DSL:**

```kotlin
contextAwareTool("tool_name") {
    // Tool metadata
    description = "Tool description"

    // Parameters
    param("param1", "string", "Description", required = true)
    param("param2", "number", "Description", required = false)

    // Execution with context
    execute { params, context ->
        // Access context
        val tenantId = context.tenantId
        val userId = context.userId

        // Access parameters
        val param1 = params["param1"] as String
        val param2 = (params["param2"] as? Number)?.toInt() ?: 0

        // Return result
        "Result: ..."
    }
}
```

### simpleContextTool

Quick context-aware tool creation:

```kotlin
fun simpleContextTool(
    name: String,
    description: String,
    execute: suspend (Map<String, Any>, AgentContext) -> String
): Tool
```

**Usage:**

```kotlin
// Simple tool with no parameters
val getTenantTool = simpleContextTool(
    name = "get_tenant",
    description = "Get current tenant ID"
) { params, context ->
    "Current tenant: ${context.tenantId}"
}

// Simple tool with parameters
val queryTool = simpleContextTool(
    name = "query_data",
    description = "Query tenant data"
) { params, context ->
    val query = params["query"] as String
    val tenantId = context.tenantId!!

    database.query(query, tenantId).toString()
}

// In agent
val agent = buildAgent {
    id = "simple-agent"

    simpleContextTool("get_user", "Get current user") { params, context ->
        "Current user: ${context.userId}"
    }

    simpleContextTool("list_permissions", "List user permissions") { params, context ->
        val userId = context.userId!!
        val permissions = permissionService.getPermissions(userId)
        permissions.joinToString(", ")
    }
}
```

## Service Layer Context Support

### BaseContextAwareService

Base class for context-aware services:

```kotlin
abstract class BaseContextAwareService {
    protected fun getContext(): AgentContext
    protected suspend fun <T> withTenant(block: suspend (tenantId: String) -> T): T
    protected suspend fun <T> withTenantAndUser(block: suspend (tenantId: String, userId: String) -> T): T
}
```

**Usage:**

```kotlin
class OrderRepository : BaseContextAwareService() {

    // Automatic tenant scoping
    suspend fun findOrders(): List<Order> = withTenant { tenantId ->
        database.query(
            "SELECT * FROM orders WHERE tenant_id = ?",
            tenantId
        ).map { /* map to Order */ }
    }

    // Automatic tenant + user scoping
    suspend fun createOrder(items: List<String>): Order = withTenantAndUser { tenantId, userId ->
        val order = Order(
            id = generateId(),
            tenantId = tenantId,
            userId = userId,
            items = items,
            createdAt = Instant.now()
        )

        database.insert(order)
        order
    }

    // Access raw context when needed
    suspend fun findWithFilters(filters: Map<String, Any>): List<Order> {
        val context = getContext()
        val tenantId = context.tenantId ?: throw IllegalStateException("No tenant")

        // Complex query with tenant scoping
        return database.queryWithFilters(tenantId, filters)
    }
}
```

**Multi-Layer Service Example:**

```kotlin
// Repository layer
class PolicyRepository : BaseContextAwareService() {
    suspend fun findByType(policyType: String): Policy = withTenant { tenantId ->
        database.findPolicy(policyType, tenantId)
    }

    suspend fun create(policyType: String): Policy = withTenantAndUser { tenantId, userId ->
        database.createPolicy(policyType, tenantId, userId)
    }
}

// Service layer
class OrderService : BaseContextAwareService() {
    private val orderRepo = OrderRepository()
    private val policyRepo = PolicyRepository()

    // Nested service calls - context flows through!
    suspend fun createOrderWithPolicy(items: List<String>, policyType: String): Order =
        withTenantAndUser { tenantId, userId ->

            // Find policy (automatically gets tenantId)
            val policy = policyRepo.findByType(policyType)

            // Validate policy is active
            if (!policy.active) {
                throw IllegalStateException("Policy not active")
            }

            // Create order (automatically gets tenantId + userId)
            val order = orderRepo.create(items)

            order
        }
}

// Usage in agent
val agent = buildAgent {
    id = "order-agent"
    val orderService = OrderService()

    contextAwareTool("create_order_with_policy") {
        description = "Create order with policy validation"
        param("items", "array", "Order items")
        param("policyType", "string", "Policy type")

        execute { params, context ->
            val items = (params["items"] as List<*>).map { it.toString() }
            val policyType = params["policyType"] as String

            // Service automatically gets context!
            val order = orderService.createOrderWithPolicy(items, policyType)
            "Order ${order.id} created with policy"
        }
    }
}
```

### Helper Methods

#### withTenant

Require tenant ID from context:

```kotlin
suspend fun <T> withTenant(block: suspend (tenantId: String) -> T): T
```

**Usage:**

```kotlin
class TenantScopedService : BaseContextAwareService() {

    suspend fun getTenantConfig(): Config = withTenant { tenantId ->
        configRepo.findByTenant(tenantId)
    }

    suspend fun updateTenantSettings(settings: Map<String, Any>) = withTenant { tenantId ->
        database.update("tenant_settings")
            .set(settings)
            .where("tenant_id = ?", tenantId)
            .execute()
    }
}

// Usage
withAgentContext("tenantId" to "ACME") {
    val service = TenantScopedService()
    val config = service.getTenantConfig()  // Gets "ACME" automatically
}
```

**Throws `IllegalStateException` if no tenantId in context:**

```kotlin
// ❌ Fails - no context
val service = TenantScopedService()
service.getTenantConfig()  // IllegalStateException!

// ✅ Works - has context
withAgentContext("tenantId" to "ACME") {
    service.getTenantConfig()  // OK
}
```

#### withTenantAndUser

Require both tenant ID and user ID:

```kotlin
suspend fun <T> withTenantAndUser(block: suspend (tenantId: String, userId: String) -> T): T
```

**Usage:**

```kotlin
class UserScopedService : BaseContextAwareService() {

    suspend fun getUserOrders(): List<Order> = withTenantAndUser { tenantId, userId ->
        database.query(
            "SELECT * FROM orders WHERE tenant_id = ? AND user_id = ?",
            tenantId, userId
        )
    }

    suspend fun createUserResource(resourceType: String) = withTenantAndUser { tenantId, userId ->
        val resource = Resource(
            id = generateId(),
            tenantId = tenantId,
            userId = userId,
            type = resourceType,
            createdAt = Instant.now()
        )

        database.insert(resource)
        auditLog.record("RESOURCE_CREATED", tenantId, userId, resource.id)

        resource
    }
}

// Usage
withAgentContext(
    "tenantId" to "ACME",
    "userId" to "user-123"
) {
    val service = UserScopedService()
    val orders = service.getUserOrders()  // Gets both automatically
}
```

#### getContext

Access raw context:

```kotlin
protected fun getContext(): AgentContext
```

**Usage:**

```kotlin
class AdvancedService : BaseContextAwareService() {

    suspend fun processWithMetadata() {
        val context = getContext()

        val tenantId = context.tenantId ?: "default"
        val userId = context.userId
        val correlationId = context.correlationId
        val permissions = context.getAs<List<String>>("permissions")

        // Use all context values
        process(tenantId, userId, correlationId, permissions)
    }
}
```

## Context Extension System

### ContextExtension

Interface for context enrichment:

```kotlin
interface ContextExtension {
    val key: String
    suspend fun enrich(context: AgentContext): AgentContext
}
```

**Built-in Extensions:**

#### TenantContextExtension

Enrich with tenant-specific data:

```kotlin
val tenantExtension = TenantContextExtension { tenantId ->
    // Fetch tenant configuration
    val config = tenantConfigRepo.find(tenantId)

    mapOf(
        "features" to config.features,
        "limits" to config.limits,
        "tier" to config.tier
    )
}

ContextExtensionRegistry.register(tenantExtension)
```

**Added Keys:**
- `tenant_config: Map<String, Any>` - Full tenant config
- `tenant_features: List<String>` - Enabled features
- `tenant_limits: Map<String, Any>` - Tenant limits
- `tenant_tier: String` - Subscription tier

#### UserContextExtension

Enrich with user-specific data:

```kotlin
val userExtension = UserContextExtension { userId ->
    val user = userRepo.find(userId)

    mapOf(
        "email" to user.email,
        "name" to user.name,
        "permissions" to user.permissions,
        "roles" to user.roles
    )
}

ContextExtensionRegistry.register(userExtension)
```

**Added Keys:**
- `user_profile: Map<String, Any>` - User profile
- `user_permissions: List<String>` - User permissions
- `user_roles: List<String>` - User roles

#### SessionContextExtension

Enrich with session data:

```kotlin
val sessionExtension = SessionContextExtension { sessionId ->
    val session = sessionStore.get(sessionId)

    mapOf(
        "startedAt" to session.startedAt,
        "expiresAt" to session.expiresAt,
        "deviceType" to session.deviceType,
        "ipAddress" to session.ipAddress
    )
}

ContextExtensionRegistry.register(sessionExtension)
```

**Added Keys:**
- `session_data: Map<String, Any>` - Full session data
- `session_metadata: Map<String, Any>` - Session metadata

### Custom Extensions

Create custom extensions:

```kotlin
class FeatureFlagExtension : ContextExtension {
    override val key = "feature_flags"

    override suspend fun enrich(context: AgentContext): AgentContext {
        val tenantId = context.tenantId ?: return context

        // Fetch feature flags for tenant
        val flags = featureFlagService.getFlags(tenantId)

        return context.with("featureFlags", flags)
    }
}

class AuditExtension : ContextExtension {
    override val key = "audit"

    override suspend fun enrich(context: AgentContext): AgentContext {
        // Add audit metadata
        return context
            .with("auditTimestamp", Instant.now())
            .with("auditSource", "spice-framework")
    }
}

// Register extensions
ContextExtensionRegistry.register(FeatureFlagExtension())
ContextExtensionRegistry.register(AuditExtension())
```

### ContextExtensionRegistry

Manage extensions globally:

```kotlin
object ContextExtensionRegistry {
    fun register(extension: ContextExtension)
    fun unregister(key: String)
    fun has(key: String): Boolean
    fun clear()
    suspend fun enrichContext(context: AgentContext): AgentContext
}
```

**Usage:**

```kotlin
// Register extensions at startup
fun initializeExtensions() {
    ContextExtensionRegistry.clear()

    ContextExtensionRegistry.register(TenantContextExtension { tenantId ->
        mapOf("config" to tenantConfig.get(tenantId))
    })

    ContextExtensionRegistry.register(UserContextExtension { userId ->
        mapOf("profile" to userService.getProfile(userId))
    })

    ContextExtensionRegistry.register(FeatureFlagExtension())
}

// Enrich context on demand
suspend fun handleRequest(request: HttpRequest) {
    val baseContext = AgentContext.of(
        "tenantId" to request.tenantId,
        "userId" to request.userId
    )

    // Enrich with all registered extensions
    val enrichedContext = ContextExtensionRegistry.enrichContext(baseContext)

    withAgentContext(enrichedContext) {
        // Context now includes tenant config, user profile, feature flags
        agent.processComm(request.toComm())
    }
}
```

## Comm Context Integration

### Comm Context Methods

Comms can carry AgentContext:

```kotlin
data class Comm(
    val content: String,
    val from: String,
    val to: String = "",
    // ... other fields
    @Transient  // Not serialized
    val context: AgentContext? = null
)
```

**Methods:**

#### withContext()

Set comm context:

```kotlin
val comm = Comm(content = "Hello", from = "user")

val contextualComm = comm.withContext(AgentContext.of(
    "tenantId" to "ACME",
    "userId" to "user-123"
))
```

#### withContextValues()

Add context values (merges with existing):

```kotlin
val comm = Comm(
    content = "Request",
    from = "user",
    context = AgentContext.of("tenantId" to "ACME")
)

val enriched = comm.withContextValues(
    "userId" to "user-123",
    "sessionId" to "sess-456"
)

// enriched.context has: tenantId, userId, sessionId
```

#### getContextValue()

Get value from context or data:

```kotlin
val comm = Comm(
    content = "Request",
    from = "user",
    data = mapOf("key1" to "data-value"),
    context = AgentContext.of("key2" to "context-value")
)

val value1 = comm.getContextValue("key1")  // "data-value" (from data)
val value2 = comm.getContextValue("key2")  // "context-value" (from context)

// Context takes precedence over data!
```

**Context Propagation in Comm Methods:**

All comm response methods preserve context:

```kotlin
val originalComm = Comm(
    content = "Request",
    from = "user",
    context = AgentContext.of("tenantId" to "ACME")
)

// reply() preserves context
val reply = originalComm.reply(
    content = "Response",
    from = "agent"
)
// reply.context == originalComm.context ✅

// forward() preserves context
val forwarded = originalComm.forward("another-agent")
// forwarded.context == originalComm.context ✅

// error() preserves context
val error = originalComm.error("Something failed", from = "system")
// error.context == originalComm.context ✅

// toolCall() preserves context
val toolCall = originalComm.toolCall(
    toolName = "my_tool",
    params = mapOf("param" to "value"),
    from = "agent"
)
// toolCall.context == originalComm.context ✅
```

## Real-World Examples

### Multi-Tenant SaaS Application

Complete multi-tenant system with context propagation:

```kotlin
// 1. Repository Layer
class PolicyRepository : BaseContextAwareService() {
    suspend fun findByType(policyType: String): Policy = withTenant { tenantId ->
        database.query(
            "SELECT * FROM policies WHERE tenant_id = ? AND type = ?",
            tenantId, policyType
        ).first()
    }

    suspend fun createPolicy(policyType: String): Policy = withTenantAndUser { tenantId, userId ->
        val policy = Policy(
            id = generateId(),
            tenantId = tenantId,
            type = policyType,
            createdBy = userId,
            createdAt = Instant.now()
        )

        database.insert(policy)
        auditLog.record("POLICY_CREATED", tenantId, userId, policy.id)

        policy
    }
}

class OrderRepository : BaseContextAwareService() {
    suspend fun findUserOrders(): List<Order> = withTenantAndUser { tenantId, userId ->
        database.query(
            "SELECT * FROM orders WHERE tenant_id = ? AND user_id = ?",
            tenantId, userId
        )
    }

    suspend fun create(items: List<String>): Order = withTenantAndUser { tenantId, userId ->
        Order(
            id = generateId(),
            tenantId = tenantId,
            userId = userId,
            items = items,
            createdAt = Instant.now()
        )
    }
}

// 2. Service Layer
class OrderService : BaseContextAwareService() {
    private val orderRepo = OrderRepository()
    private val policyRepo = PolicyRepository()

    suspend fun createOrderWithPolicy(
        items: List<String>,
        policyType: String
    ): Order = withTenantAndUser { tenantId, userId ->

        // Validate policy (automatic tenant scoping)
        val policy = policyRepo.findByType(policyType)
        if (!policy.active) {
            throw IllegalStateException("Policy $policyType not active for tenant $tenantId")
        }

        // Create order (automatic tenant + user scoping)
        val order = orderRepo.create(items)

        // Audit log automatically scoped
        auditLog.record("ORDER_CREATED", mapOf(
            "orderId" to order.id,
            "policyId" to policy.id,
            "items" to items.size
        ))

        order
    }
}

// 3. Agent Layer
val orderAgent = buildAgent {
    id = "order-processor"
    name = "Order Processing Agent"

    val orderService = OrderService()

    contextAwareTool("create_order") {
        description = "Create new order with policy validation"
        param("items", "array", "Order items")
        param("policyType", "string", "Policy type")

        execute { params, context ->
            val items = (params["items"] as List<*>).map { it.toString() }
            val policyType = params["policyType"] as String

            // Service call - context flows automatically!
            val order = orderService.createOrderWithPolicy(items, policyType)

            "Order ${order.id} created for tenant ${context.tenantId}"
        }
    }

    contextAwareTool("list_orders") {
        description = "List user orders"

        execute { params, context ->
            val orders = orderRepo.findUserOrders()
            "Found ${orders.size} orders for user ${context.userId}"
        }
    }
}

// 4. HTTP Handler
suspend fun handleOrderRequest(request: HttpRequest): HttpResponse {
    // Extract tenant/user from JWT or headers
    val tenantId = request.getHeader("X-Tenant-ID")
    val userId = extractUserFromJWT(request.getHeader("Authorization"))

    // Set context once at boundary
    withAgentContext(
        "tenantId" to tenantId,
        "userId" to userId,
        "correlationId" to UUID.randomUUID().toString()
    ) {
        // Process - context flows through all layers!
        val comm = Comm(
            content = request.body,
            from = userId
        )

        val result = orderAgent.processComm(comm)

        result.fold(
            onSuccess = { response ->
                HttpResponse.ok(response.content)
            },
            onFailure = { error ->
                HttpResponse.error(error.message)
            }
        )
    }
}

// 5. Multi-Tenant Isolation Test
@Test
fun `should isolate tenants completely`() = runTest {
    // Tenant A
    val tenantAResult = withAgentContext(
        "tenantId" to "TENANT_A",
        "userId" to "user-a1"
    ) {
        val comm = Comm(content = "create order: laptop", from = "user-a1")
        orderAgent.processComm(comm)
    }

    // Tenant B (completely isolated!)
    val tenantBResult = withAgentContext(
        "tenantId" to "TENANT_B",
        "userId" to "user-b1"
    ) {
        val comm = Comm(content = "create order: mouse", from = "user-b1")
        orderAgent.processComm(comm)
    }

    // Verify isolation
    assertTrue(tenantAResult.getOrNull()!!.content.contains("TENANT_A"))
    assertTrue(tenantBResult.getOrNull()!!.content.contains("TENANT_B"))
}
```

### Request Correlation and Tracing

Track requests across distributed operations:

```kotlin
// Audit service with correlation tracking
class AuditService : BaseContextAwareService() {
    val auditLog = mutableListOf<AuditEntry>()

    suspend fun logOperation(operation: String) = withTenantAndUser { tenantId, userId ->
        val context = getContext()
        val correlationId = context.correlationId ?: "unknown"

        val entry = AuditEntry(
            correlationId = correlationId,
            operation = operation,
            tenantId = tenantId,
            userId = userId,
            timestamp = Instant.now()
        )

        auditLog.add(entry)
        entry
    }
}

// Agent with audit logging
val agent = buildAgent {
    id = "audited-agent"
    val auditService = AuditService()

    contextAwareTool("create_resource") {
        description = "Create resource with full audit trail"
        param("resourceType", "string", "Resource type")

        execute { params, context ->
            val resourceType = params["resourceType"] as String

            // All audit logs share same correlation ID
            auditService.logOperation("VALIDATE_$resourceType")
            // ... validation

            auditService.logOperation("CREATE_$resourceType")
            // ... creation

            auditService.logOperation("NOTIFY_$resourceType")
            // ... notification

            "Resource created with audit trail"
        }
    }
}

// Usage with correlation ID
val correlationId = "req-${UUID.randomUUID()}"

withAgentContext(
    "tenantId" to "CORP",
    "userId" to "user-123",
    "correlationId" to correlationId
) {
    agent.processComm(Comm(
        content = "create resource: policy",
        from = "user-123"
    ))
}

// All audit logs have same correlation ID!
auditService.auditLog.forEach { entry ->
    println("[$entry.correlationId] $entry.operation by $entry.userId")
}
// Output:
// [req-xyz] VALIDATE_policy by user-123
// [req-xyz] CREATE_policy by user-123
// [req-xyz] NOTIFY_policy by user-123
```

### Context Extension Pipeline

Rich context enrichment:

```kotlin
// 1. Register extensions
fun setupExtensions() {
    ContextExtensionRegistry.clear()

    // Tenant config extension
    ContextExtensionRegistry.register(TenantContextExtension { tenantId ->
        val config = tenantConfigService.getConfig(tenantId)
        mapOf(
            "features" to config.enabledFeatures,
            "limits" to config.limits,
            "tier" to config.subscriptionTier
        )
    })

    // User permissions extension
    ContextExtensionRegistry.register(UserContextExtension { userId ->
        val user = userService.getUser(userId)
        mapOf(
            "permissions" to user.permissions,
            "roles" to user.roles,
            "email" to user.email
        )
    })

    // Feature flags extension
    ContextExtensionRegistry.register(object : ContextExtension {
        override val key = "feature_flags"

        override suspend fun enrich(context: AgentContext): AgentContext {
            val tenantId = context.tenantId ?: return context
            val flags = featureFlagService.getFlags(tenantId)
            return context.with("featureFlags", flags)
        }
    })
}

// 2. Use enriched context
suspend fun handleRequest(request: HttpRequest) {
    // Base context from request
    val baseContext = AgentContext.of(
        "tenantId" to request.tenantId,
        "userId" to request.userId,
        "sessionId" to request.sessionId
    )

    // Enrich with all extensions
    val enrichedContext = ContextExtensionRegistry.enrichContext(baseContext)

    withAgentContext(enrichedContext) {
        // Context now has:
        // - tenantId, userId, sessionId (base)
        // - tenant_features, tenant_limits, tenant_tier
        // - user_permissions, user_roles, user_email
        // - featureFlags

        val result = agent.processComm(request.toComm())

        // Agent and tools can access all enriched data!
        result
    }
}

// 3. Tool using enriched context
contextAwareTool("check_permission") {
    description = "Check if user has permission"
    param("permission", "string", "Permission to check")

    execute { params, context ->
        val permission = params["permission"] as String

        // Access enriched context
        val permissions = context.getAs<List<String>>("user_permissions") ?: emptyList()
        val features = context.getAs<List<String>>("tenant_features") ?: emptyList()

        val hasPermission = permission in permissions
        val hasFeature = "premium" in features

        when {
            !hasPermission -> "Permission denied: $permission"
            !hasFeature -> "Feature not available for tenant"
            else -> "Permission granted: $permission"
        }
    }
}
```

## Best Practices

### 1. Set Context at Boundaries

Set context once at system boundaries:

```kotlin
// ✅ Good - Set at HTTP boundary
suspend fun handleRequest(request: HttpRequest) {
    withAgentContext(
        "tenantId" to request.tenantId,
        "userId" to request.userId
    ) {
        // All nested operations have context
        processRequest(request)
    }
}

// ❌ Bad - Setting context deep in call stack
suspend fun processOrder(orderId: String, tenantId: String) {
    withAgentContext("tenantId" to tenantId) {
        // Too late! Should be set at boundary
    }
}
```

### 2. Use Service Layer Helpers

Leverage `withTenant` and `withTenantAndUser`:

```kotlin
// ✅ Good - Clean, automatic scoping
class OrderService : BaseContextAwareService() {
    suspend fun findOrders() = withTenant { tenantId ->
        database.query("SELECT * FROM orders WHERE tenant_id = ?", tenantId)
    }
}

// ❌ Bad - Manual context access
class OrderService {
    suspend fun findOrders(): List<Order> {
        val context = currentAgentContext()
        val tenantId = context?.tenantId ?: throw IllegalStateException("No tenant")
        return database.query("SELECT * FROM orders WHERE tenant_id = ?", tenantId)
    }
}
```

### 3. Enrich Context Progressively

Add context values as they become available:

```kotlin
// ✅ Good - Progressive enrichment
suspend fun handleRequest(request: HttpRequest) {
    // Base context from request
    withAgentContext(
        "tenantId" to request.tenantId,
        "userId" to request.userId
    ) {
        // Add session context
        withEnrichedContext("sessionId" to request.sessionId) {

            // Add correlation ID for this request
            withEnrichedContext("correlationId" to UUID.randomUUID().toString()) {

                processRequest(request)
            }
        }
    }
}
```

### 4. Use Context-Aware Tools

Always use `contextAwareTool` for multi-tenant systems:

```kotlin
// ✅ Good - Automatic context injection
contextAwareTool("lookup_data") {
    description = "Look up tenant data"
    execute { params, context ->
        val tenantId = context.tenantId!!
        dataService.lookup(tenantId)
    }
}

// ❌ Bad - Manual context passing
tool("lookup_data") {
    parameter("tenantId", "string", required = true)  // Manual!
    execute(fun(params: Map<String, Any>): String {
        val tenantId = params["tenantId"] as String
        dataService.lookup(tenantId)
    })
}
```

### 5. Validate Context Early

Fail fast if required context is missing:

```kotlin
// ✅ Good - Early validation
class OrderService : BaseContextAwareService() {
    suspend fun createOrder(items: List<String>): Order {
        // Fails immediately if no tenant/user
        return withTenantAndUser { tenantId, userId ->
            Order(
                tenantId = tenantId,
                userId = userId,
                items = items
            )
        }
    }
}

// ❌ Bad - Late validation
class OrderService {
    suspend fun createOrder(items: List<String>): Order {
        val order = Order(items = items)
        // ... lots of processing
        val context = currentAgentContext()
        val tenantId = context?.tenantId ?: throw IllegalStateException("No tenant")
        // Too late! Already did work
    }
}
```

### 6. Don't Serialize Context

Context is runtime state, not persistent data:

```kotlin
// ✅ Good - Context marked @Transient
data class Comm(
    val content: String,
    val from: String,
    @Transient  // Won't be serialized
    val context: AgentContext? = null
)

// ✅ Good - Reconstruct context on deserialization
suspend fun handleDeserializedComm(comm: Comm, request: HttpRequest) {
    // Context was lost in serialization, reconstruct it
    withAgentContext(
        "tenantId" to request.tenantId,
        "userId" to request.userId
    ) {
        agent.processComm(comm)
    }
}
```

### 7. Use Extensions for Cross-Cutting Concerns

Register extensions for data needed across all operations:

```kotlin
// ✅ Good - Extension for common data
fun setupCommonExtensions() {
    // Tenant config needed everywhere
    ContextExtensionRegistry.register(TenantContextExtension { tenantId ->
        mapOf("config" to tenantConfigService.getConfig(tenantId))
    })

    // User permissions needed for authorization
    ContextExtensionRegistry.register(UserContextExtension { userId ->
        mapOf("permissions" to permissionService.getPermissions(userId))
    })
}

// ❌ Bad - Fetching same data repeatedly
suspend fun checkPermission(permission: String) {
    val context = currentAgentContext()
    val userId = context?.userId ?: throw IllegalStateException("No user")
    val permissions = permissionService.getPermissions(userId)  // Repeated fetch!
    // ...
}
```

## Next Steps

- [Agent API](./agent) - Learn about agents that use context
- [Tool API](./tool) - Create context-aware tools
- [DSL API](./dsl) - Master the context DSL
- [Multi-Agent Systems](../orchestration/multi-agent) - Context in multi-agent workflows
- [Context Propagation Guide](../guides/context-propagation) - Deep dive into propagation
