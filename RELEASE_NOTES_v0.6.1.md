# Spice Framework 0.6.1 Release Notes

**Released:** 2025-10-29

## 🎯 Patch Release: ExecutionContext Accessor API

Spice 0.6.1 adds convenient accessor functions for ExecutionContext, making it easier to access context from service layers without explicit parameter passing.

---

## ✨ What's New

### ExecutionContext Accessor Functions

**New APIs** for accessing ExecutionContext from anywhere in suspend code:

```kotlin
// Get current context (null-safe)
suspend fun myService() {
    val context = currentExecutionContext()
    val tenantId = context?.tenantId
}

// Require context (throws if missing)
suspend fun myService() {
    val context = requireExecutionContext()
    val tenantId = context.tenantId  // Safe!
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
- `getCurrentTenantId(): String?` - Get tenant ID directly
- `getCurrentUserId(): String?` - Get user ID directly
- `getCurrentCorrelationId(): String?` - Get correlation ID directly

### Service Layer Pattern

No more context parameter passing!

```kotlin
// OLD (explicit parameter)
suspend fun processOrder(orderId: String, context: ExecutionContext) {
    val tenantId = context.tenantId ?: error("No tenant")
    // ...
}

// NEW (implicit from coroutine)
suspend fun processOrder(orderId: String) {
    val tenantId = getCurrentTenantId() ?: error("No tenant")
    val userId = getCurrentUserId()
    
    // Use context
    orderRepository.findByTenant(orderId, tenantId)
}

// Controller sets context once
suspend fun handleRequest(request: OrderRequest) {
    withAgentContext(
        "tenantId" to request.tenantId,
        "userId" to request.userId
    ) {
        processOrder(request.orderId)  // Context auto-propagates!
    }
}
```

---

## 🔄 Enhanced Context Propagation

### withAgentContext DSL Enhancement

**Changed:** `withAgentContext` now sets **both** AgentContext and ExecutionContext

```kotlin
// 0.6.0 - Only set AgentContext
withAgentContext("tenantId" to "ACME") {
    currentAgentContext()      // ✅ Works
    currentExecutionContext()  // ❌ Was null
}

// 0.6.1 - Sets both!
withAgentContext("tenantId" to "ACME") {
    currentAgentContext()      // ✅ Works
    currentExecutionContext()  // ✅ Also works!
    getCurrentTenantId()       // ✅ Convenient accessor!
}
```

**Benefit:** Backward compatibility with 0.5.x DSL while supporting new 0.6.x patterns.

---

## 📚 Documentation Updates

### New Examples

- Service layer pattern without context parameters
- Nested coroutine context access
- Error handling with context accessors

### Updated Guides

- `api/execution-context.md` - Accessor Functions section
- `guides/execution-context-patterns.md` - Service layer patterns

---

## 🐛 Why This Matters

### Real-World Problem

In 0.6.0, service layer code couldn't access context without explicit parameters:

```kotlin
// ❌ Had to pass context everywhere
suspend fun processOrder(orderId: String, context: ExecutionContext) { }
suspend fun validateOrder(order: Order, context: ExecutionContext) { }
suspend fun saveOrder(order: Order, context: ExecutionContext) { }
```

### 0.6.1 Solution

```kotlin
// ✅ Context accessed implicitly
suspend fun processOrder(orderId: String) {
    val tenantId = getCurrentTenantId()  // Just works!
}
suspend fun validateOrder(order: Order) { }
suspend fun saveOrder(order: Order) { }
```

---

## 🧪 Testing

### New Test Suite

- `ExecutionContextAccessorTest` - 11 comprehensive tests
- Covers all accessor functions
- Validates DSL integration
- Tests error cases

All tests pass ✅

---

## 🔧 Implementation Details

### Coroutine Context Integration

ExecutionContext is a `CoroutineContext.Element`, so these accessors use standard Kotlin coroutine APIs:

```kotlin
suspend fun currentExecutionContext(): ExecutionContext? {
    return coroutineContext[ExecutionContext]
}
```

**Performance:** O(1) - Direct coroutine context lookup

---

## 📖 Migration from 0.6.0

### If you're already on 0.6.0:

**No breaking changes!** Just update and start using the new accessors:

```kotlin
// Before (0.6.0 - still works)
class MyNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val tenantId = ctx.context.tenantId
        return processWithTenant(tenantId)
    }
}

// After (0.6.1 - more flexible)
suspend fun processInService() {
    val tenantId = getCurrentTenantId()  // Can call from anywhere!
    // ...
}
```

### If you're on 0.5.x:

See [Migration Guide 0.5 → 0.6](/MIGRATION_GUIDE_v0.6.0.md) first, then enjoy the new accessors!

---

## 🎯 Use Cases

### 1. Service Layer (Most Common)

```kotlin
class OrderService {
    suspend fun createOrder(orderData: OrderData): Order {
        val tenantId = getCurrentTenantId() ?: error("No tenant")
        val userId = getCurrentUserId() ?: error("No user")
        
        // Create tenant-specific order
        return orderRepository.save(orderData, tenantId, userId)
    }
}
```

### 2. Repository Layer

```kotlin
class TenantAwareRepository {
    suspend fun findAll(): List<Entity> {
        val tenantId = getCurrentTenantId() ?: error("No tenant context")
        return database.query("SELECT * FROM entities WHERE tenant_id = ?", tenantId)
    }
}
```

### 3. Audit Logging

```kotlin
class AuditLogger {
    suspend fun log(action: String, details: Map<String, Any>) {
        val tenantId = getCurrentTenantId()
        val userId = getCurrentUserId()
        val correlationId = getCurrentCorrelationId()
        
        auditLog.write(
            action = action,
            details = details,
            tenantId = tenantId,
            userId = userId,
            correlationId = correlationId,
            timestamp = Instant.now()
        )
    }
}
```

### 4. Authorization Checks

```kotlin
class AuthorizationService {
    suspend fun requirePermission(permission: String) {
        val userId = getCurrentUserId() ?: throw UnauthorizedException("No user")
        val tenantId = getCurrentTenantId() ?: throw UnauthorizedException("No tenant")
        
        if (!hasPermission(userId, tenantId, permission)) {
            throw ForbiddenException("User lacks permission: $permission")
        }
    }
}
```

---

## 🔮 What's Next?

### Future (0.6.2+)

- Additional convenience accessors for custom keys
- Context scoping utilities
- Performance optimizations

---

## 📦 Get Started

### Update

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.no-ai-labs.spice-framework:spice-core:0.6.1")
}
```

### Resources

- [ExecutionContext API](/docs/api/execution-context)
- [ExecutionContext Patterns](/docs/guides/execution-context-patterns)
- [0.6.0 Release Notes](/RELEASE_NOTES_v0.6.0.md)

---

**Happy Building! 🌶️**

*- The Spice Team*

