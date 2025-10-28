# Multi-Tenancy

Build secure, scalable multi-tenant agent systems with automatic tenant isolation using Spice Framework's context propagation.

## Overview

Multi-tenancy allows a single application instance to serve multiple tenants (customers, organizations) with complete data isolation. Spice Framework provides first-class support for multi-tenant architectures through automatic context propagation.

**Key Features**:
- ✅ **Automatic Isolation** - Tenant ID propagates through all operations
- ✅ **Zero Overhead** - No manual tenant parameter passing
- ✅ **Type-Safe** - Compile-time tenant access
- ✅ **Secure by Default** - Impossible to forget tenant scoping

## Architecture Patterns

### Pattern 1: Shared Database, Tenant-Scoped Queries

All tenants share the same database, but queries are scoped by `tenant_id`:

```kotlin
class OrderRepository : BaseContextAwareService() {

    suspend fun findOrders() = withTenant { tenantId ->
        database.query<Order>(
            """
            SELECT * FROM orders
            WHERE tenant_id = ?
            ORDER BY created_at DESC
            """,
            tenantId
        )
    }

    suspend fun createOrder(order: Order) = withTenantAndUser { tenantId, userId ->
        order.copy(
            tenantId = tenantId,
            createdBy = userId,
            createdAt = Instant.now()
        ).also { newOrder ->
            database.insert(newOrder)
        }
    }
}
```

**Pros**:
- Simple to implement
- Cost-effective
- Easy to manage

**Cons**:
- Risk of data leakage if queries forget tenant filter
- Shared database performance limits
- Limited customization per tenant

### Pattern 2: Database-Per-Tenant

Each tenant has their own database:

```kotlin
class TenantDatabaseRouter : BaseContextAwareService() {

    private val dataSources = mutableMapOf<String, DataSource>()

    suspend fun getConnection() = withTenant { tenantId ->
        val dataSource = dataSources[tenantId]
            ?: throw IllegalStateException("No database for tenant $tenantId")

        dataSource.connection
    }
}

class OrderRepository(
    private val router: TenantDatabaseRouter
) : BaseContextAwareService() {

    suspend fun findOrders() = withTenant { tenantId ->
        val conn = router.getConnection()
        conn.query<Order>("SELECT * FROM orders")  // No tenant_id filter needed!
    }
}
```

**Pros**:
- Complete data isolation
- Per-tenant performance tuning
- Easy to migrate/backup individual tenants

**Cons**:
- More complex infrastructure
- Higher costs
- Schema migration challenges

### Pattern 3: Schema-Per-Tenant

One database, separate schema per tenant:

```kotlin
class SchemaRouter : BaseContextAwareService() {

    suspend fun getSchemaName() = withTenant { tenantId ->
        "tenant_$tenantId"
    }
}

class OrderRepository(
    private val schemaRouter: SchemaRouter
) : BaseContextAwareService() {

    suspend fun findOrders() = withTenant { tenantId ->
        val schema = schemaRouter.getSchemaName()
        database.query<Order>(
            "SELECT * FROM ${schema}.orders"
        )
    }
}
```

**Pros**:
- Good balance of isolation and cost
- Easier management than database-per-tenant
- Performance isolation

**Cons**:
- Database platform specific
- More complex than shared schema

## Implementing Multi-Tenancy

### Step 1: Extract Tenant from Request

```kotlin
@RestController
class OrderController(
    private val orderAgent: Agent
) {

    @PostMapping("/api/orders")
    suspend fun createOrder(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestHeader("Authorization") auth: String,
        @RequestBody request: CreateOrderRequest
    ): ResponseEntity<OrderResponse> =
        withAgentContext(
            "tenantId" to tenantId,
            "userId" to extractUserId(auth),
            "correlationId" to UUID.randomUUID().toString()
        ) {
            val comm = Comm(
                id = UUID.randomUUID().toString(),
                content = request.toJson(),
                direction = CommDirection.IN
            )

            val result = orderAgent.processComm(comm)

            result.fold(
                onSuccess = { ResponseEntity.ok(it.toOrderResponse()) },
                onFailure = { ResponseEntity.status(500).build() }
            )
        }
}
```

### Step 2: Create Tenant-Scoped Services

```kotlin
class CustomerService : BaseContextAwareService() {

    suspend fun findCustomer(customerId: String) = withTenant { tenantId ->
        database.queryOne<Customer>(
            """
            SELECT * FROM customers
            WHERE tenant_id = ? AND id = ?
            """,
            tenantId, customerId
        )
    }

    suspend fun createCustomer(customer: Customer) =
        withTenantAndUser { tenantId, userId ->
            customer.copy(
                tenantId = tenantId,
                createdBy = userId
            ).also { newCustomer ->
                database.insert(newCustomer)
            }
        }

    suspend fun updateCustomer(customerId: String, updates: CustomerUpdates) =
        withTenant { tenantId ->
            val existing = findCustomer(customerId)
                ?: throw NotFoundException("Customer not found")

            // Verify tenant ownership
            require(existing.tenantId == tenantId) {
                "Customer belongs to different tenant"
            }

            database.update(existing.copy(
                name = updates.name ?: existing.name,
                email = updates.email ?: existing.email
            ))
        }
}
```

### Step 3: Create Tenant-Aware Tools

```kotlin
val customerLookupTool = contextAwareTool("lookup_customer") {
    description = "Look up customer by ID"
    param("customerId", "string", "Customer ID", required = true)

    execute { params, context ->
        val tenantId = context.tenantId
            ?: throw IllegalStateException("Tenant required")

        val customerId = params["customerId"] as String

        customerService.findCustomer(customerId)
    }
}

val createCustomerTool = contextAwareTool("create_customer") {
    description = "Create new customer"

    parameters {
        string("name", "Customer name", required = true)
        string("email", "Customer email", required = true)
        string("phone", "Phone number", required = false)
    }

    execute { params, context ->
        val customer = Customer(
            id = UUID.randomUUID().toString(),
            name = params["name"] as String,
            email = params["email"] as String,
            phone = params["phone"] as? String
        )

        customerService.createCustomer(customer)
    }
}
```

## Security Considerations

### 1. Always Validate Tenant Ownership

```kotlin
suspend fun updateOrder(orderId: String, updates: OrderUpdates) =
    withTenant { tenantId ->
        val order = findOrder(orderId)
            ?: throw NotFoundException("Order not found")

        // CRITICAL: Verify tenant owns this resource
        require(order.tenantId == tenantId) {
            "Access denied: Order belongs to different tenant"
        }

        database.update(order.copy(status = updates.status))
    }
```

### 2. Prevent Tenant ID Tampering

```kotlin
// ❌ BAD: Trust user input
@PostMapping("/api/orders")
suspend fun createOrder(@RequestBody request: CreateOrderRequest) =
    withAgentContext("tenantId" to request.tenantId) {  // User controls this!
        // ...
    }

// ✅ GOOD: Extract from authentication
@PostMapping("/api/orders")
suspend fun createOrder(
    @AuthenticationPrincipal user: AuthenticatedUser,
    @RequestBody request: CreateOrderRequest
) = withAgentContext("tenantId" to user.tenantId) {  // Verified by auth system
    // ...
}
```

### 3. Use Row-Level Security (If Available)

PostgreSQL example:

```sql
-- Enable RLS
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;

-- Create policy
CREATE POLICY tenant_isolation ON orders
    USING (tenant_id = current_setting('app.current_tenant')::TEXT);

-- Set tenant in application
SET app.current_tenant = 'ACME';
```

Then in Kotlin:

```kotlin
suspend fun setTenant() = withTenant { tenantId ->
    database.execute("SET app.current_tenant = ?", tenantId)
}
```

### 4. Audit Tenant Access

```kotlin
class AuditService : BaseContextAwareService() {

    suspend fun logAccess(resource: String, action: String) =
        withTenantAndUser { tenantId, userId ->
            database.insert(AuditLog(
                timestamp = Instant.now(),
                tenantId = tenantId,
                userId = userId,
                resource = resource,
                action = action
            ))
        }
}

val secureTool = contextAwareTool("secure_operation") {
    execute { params, context ->
        auditService.logAccess("customer", "read")

        // Perform operation
    }
}
```

## Testing Multi-Tenant Code

### Test Tenant Isolation

```kotlin
@Test
fun `tenant A cannot access tenant B data`() = runTest {
    // Tenant A creates order
    val orderA = withAgentContext("tenantId" to "TENANT-A") {
        orderService.createOrder(Order(...))
    }

    // Tenant B tries to access tenant A's order
    val result = withAgentContext("tenantId" to "TENANT-B") {
        runCatching {
            orderService.findOrder(orderA.id)
        }
    }

    assertTrue(result.isFailure)
}
```

### Test Cross-Tenant Updates

```kotlin
@Test
fun `cannot update resource from different tenant`() = runTest {
    val order = withAgentContext("tenantId" to "TENANT-A") {
        orderService.createOrder(Order(...))
    }

    // Try to update from different tenant
    val result = withAgentContext("tenantId" to "TENANT-B") {
        runCatching {
            orderService.updateOrder(order.id, OrderUpdates(...))
        }
    }

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("different tenant"))
}
```

## Performance Optimization

### Cache Per-Tenant

```kotlin
val tenantConfigTool = contextAwareTool("get_tenant_config") {
    cache {
        ttl = 3600  // 1 hour
        maxSize = 1000

        // Tenant-specific cache keys
        keyBuilder = { params, context ->
            "config:${context.tenantId}"
        }
    }

    execute { params, context ->
        configService.getConfig(context.tenantId!!)
    }
}
```

### Connection Pooling Per-Tenant

```kotlin
class TenantAwareDataSource {
    private val pools = mutableMapOf<String, HikariDataSource>()

    fun getDataSource(tenantId: String): DataSource {
        return pools.getOrPut(tenantId) {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = "jdbc:postgresql://localhost/db_$tenantId"
                maximumPoolSize = 10
                minimumIdle = 2
            })
        }
    }
}
```

## Migration Strategies

### Adding Multi-Tenancy to Existing App

1. **Add tenant_id column** to all tables
2. **Update queries** to filter by tenant
3. **Migrate to context-aware services**
4. **Update tools** to use context

```kotlin
// Before
class OrderService {
    fun findOrders(): List<Order> {
        return database.query("SELECT * FROM orders")
    }
}

// After
class OrderService : BaseContextAwareService() {
    suspend fun findOrders() = withTenant { tenantId ->
        database.query(
            "SELECT * FROM orders WHERE tenant_id = ?",
            tenantId
        )
    }
}
```

## Best Practices

1. **Always use context-aware services** for data access
2. **Validate tenant ownership** before updates/deletes
3. **Never trust client-provided tenant IDs**
4. **Audit cross-tenant access attempts**
5. **Test tenant isolation thoroughly**
6. **Use database-level isolation** where possible
7. **Cache per-tenant** for better performance
8. **Monitor tenant-specific metrics**

## See Also

- [Context-Aware Tools](../dsl-guide/context-aware-tools.md) - Building tenant-aware tools
- [Context Testing](../testing/context-testing.md) - Testing multi-tenant code
- [Context Propagation](../advanced/context-propagation.md) - How context works
- [Production Examples](../examples/context-production.md) - Real-world examples
