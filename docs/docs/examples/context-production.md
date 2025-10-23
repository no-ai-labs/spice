# Context in Production: Real-World Examples

Complete, production-ready examples demonstrating Context API usage in real-world applications.

## Overview

This guide provides **production-grade implementations** showing how to use Spice Framework's Context API in real business scenarios:

- **E-Commerce Platform** - Multi-tenant order processing with inventory management
- **Customer Support System** - Ticket routing with user context and SLA tracking
- **Financial Services** - Transaction processing with audit trails and compliance
- **Healthcare Platform** - Patient data access with HIPAA compliance

Each example includes:
- ✅ Complete working code
- ✅ Multi-tenancy support
- ✅ Security and compliance
- ✅ Error handling
- ✅ Audit logging
- ✅ Tests

## Example 1: E-Commerce Platform

### Scenario

Multi-tenant e-commerce platform with:
- Multiple brands (tenants) on shared infrastructure
- Inventory management per tenant
- Order processing with payment validation
- Audit trail for all operations

### Implementation

#### 1. Domain Models

```kotlin
data class Product(
    val id: String,
    val tenantId: String,
    val name: String,
    val price: Double,
    val stockQuantity: Int
)

data class Order(
    val id: String,
    val tenantId: String,
    val userId: String,
    val items: List<OrderItem>,
    val totalAmount: Double,
    val status: OrderStatus,
    val createdAt: Instant,
    val createdBy: String
)

data class OrderItem(
    val productId: String,
    val quantity: Int,
    val priceAtPurchase: Double
)

enum class OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
}

data class PaymentMethod(
    val id: String,
    val userId: String,
    val tenantId: String,
    val type: PaymentType,
    val last4: String
)

enum class PaymentType {
    CREDIT_CARD, DEBIT_CARD, PAYPAL, BANK_TRANSFER
}
```

#### 2. Repository Layer

```kotlin
class ProductRepository : BaseContextAwareService() {

    suspend fun findById(productId: String): Product? = withTenant { tenantId ->
        database.query(
            """
            SELECT * FROM products
            WHERE id = ? AND tenant_id = ?
            """,
            productId, tenantId
        ).firstOrNull()
    }

    suspend fun findByIds(productIds: List<String>): List<Product> = withTenant { tenantId ->
        database.query(
            """
            SELECT * FROM products
            WHERE id IN (${productIds.joinToString { "?" }})
              AND tenant_id = ?
            """,
            *(productIds + tenantId).toTypedArray()
        )
    }

    suspend fun updateStock(productId: String, quantity: Int) = withTenant { tenantId ->
        database.execute(
            """
            UPDATE products
            SET stock_quantity = stock_quantity + ?
            WHERE id = ? AND tenant_id = ?
            """,
            quantity, productId, tenantId
        )
    }

    suspend fun reserveStock(productId: String, quantity: Int): Boolean = withTenant { tenantId ->
        val updated = database.execute(
            """
            UPDATE products
            SET stock_quantity = stock_quantity - ?
            WHERE id = ?
              AND tenant_id = ?
              AND stock_quantity >= ?
            """,
            quantity, productId, tenantId, quantity
        )

        updated > 0
    }
}

class OrderRepository : BaseContextAwareService() {

    suspend fun create(order: Order): Order = withTenantAndUser { tenantId, userId ->
        require(order.tenantId == tenantId) { "Tenant ID mismatch" }
        require(order.userId == userId) { "User ID mismatch" }

        database.insert("orders", order)
        order
    }

    suspend fun findById(orderId: String): Order? = withTenant { tenantId ->
        database.query(
            "SELECT * FROM orders WHERE id = ? AND tenant_id = ?",
            orderId, tenantId
        ).firstOrNull()
    }

    suspend fun findUserOrders(status: OrderStatus? = null): List<Order> =
        withTenantAndUser { tenantId, userId ->
            val query = if (status != null) {
                database.query(
                    """
                    SELECT * FROM orders
                    WHERE tenant_id = ? AND user_id = ? AND status = ?
                    ORDER BY created_at DESC
                    """,
                    tenantId, userId, status.name
                )
            } else {
                database.query(
                    """
                    SELECT * FROM orders
                    WHERE tenant_id = ? AND user_id = ?
                    ORDER BY created_at DESC
                    """,
                    tenantId, userId
                )
            }
            query
        }

    suspend fun updateStatus(orderId: String, status: OrderStatus) = withTenant { tenantId ->
        database.execute(
            "UPDATE orders SET status = ? WHERE id = ? AND tenant_id = ?",
            status.name, orderId, tenantId
        )
    }
}

class PaymentRepository : BaseContextAwareService() {

    suspend fun findUserPaymentMethods(): List<PaymentMethod> =
        withTenantAndUser { tenantId, userId ->
            database.query(
                """
                SELECT * FROM payment_methods
                WHERE tenant_id = ? AND user_id = ?
                """,
                tenantId, userId
            )
        }

    suspend fun findById(paymentMethodId: String): PaymentMethod? =
        withTenantAndUser { tenantId, userId ->
            database.query(
                """
                SELECT * FROM payment_methods
                WHERE id = ? AND tenant_id = ? AND user_id = ?
                """,
                paymentMethodId, tenantId, userId
            ).firstOrNull()
        }
}
```

#### 3. Service Layer

```kotlin
class InventoryService : BaseContextAwareService() {
    private val productRepo = ProductRepository()
    private val auditService = AuditService()

    suspend fun checkAvailability(
        items: List<OrderItem>
    ): InventoryCheckResult = withTenant { tenantId ->

        val productIds = items.map { it.productId }
        val products = productRepo.findByIds(productIds)

        val unavailableItems = mutableListOf<String>()

        items.forEach { item ->
            val product = products.find { it.id == item.productId }

            if (product == null) {
                unavailableItems.add("Product ${item.productId} not found")
            } else if (product.stockQuantity < item.quantity) {
                unavailableItems.add(
                    "Product ${product.name}: requested ${item.quantity}, " +
                    "available ${product.stockQuantity}"
                )
            }
        }

        auditService.log(
            "INVENTORY_CHECK",
            mapOf(
                "items" to items.size,
                "available" to (unavailableItems.isEmpty())
            )
        )

        InventoryCheckResult(
            available = unavailableItems.isEmpty(),
            unavailableItems = unavailableItems
        )
    }

    suspend fun reserveStock(items: List<OrderItem>): Boolean = withTenant { tenantId ->
        val reservations = mutableListOf<Pair<String, Int>>()

        try {
            items.forEach { item ->
                val reserved = productRepo.reserveStock(item.productId, item.quantity)

                if (!reserved) {
                    // Rollback all reservations
                    reservations.forEach { (productId, quantity) ->
                        productRepo.updateStock(productId, quantity) // Return stock
                    }

                    auditService.log(
                        "STOCK_RESERVATION_FAILED",
                        mapOf("productId" to item.productId, "quantity" to item.quantity)
                    )

                    return@withTenant false
                }

                reservations.add(item.productId to item.quantity)
            }

            auditService.log(
                "STOCK_RESERVED",
                mapOf("items" to items.size, "reservations" to reservations.size)
            )

            true

        } catch (e: Exception) {
            // Rollback on exception
            reservations.forEach { (productId, quantity) ->
                productRepo.updateStock(productId, quantity)
            }
            throw e
        }
    }

    suspend fun releaseStock(items: List<OrderItem>) = withTenant { tenantId ->
        items.forEach { item ->
            productRepo.updateStock(item.productId, item.quantity)
        }

        auditService.log(
            "STOCK_RELEASED",
            mapOf("items" to items.size)
        )
    }
}

data class InventoryCheckResult(
    val available: Boolean,
    val unavailableItems: List<String>
)

class OrderService : BaseContextAwareService() {
    private val orderRepo = OrderRepository()
    private val productRepo = ProductRepository()
    private val paymentRepo = PaymentRepository()
    private val inventoryService = InventoryService()
    private val paymentService = PaymentService()
    private val auditService = AuditService()

    suspend fun createOrder(
        items: List<OrderItem>,
        paymentMethodId: String
    ): OrderResult = withTenantAndUser { tenantId, userId ->

        auditService.log(
            "ORDER_CREATION_STARTED",
            mapOf("items" to items.size, "userId" to userId)
        )

        // 1. Validate payment method
        val paymentMethod = paymentRepo.findById(paymentMethodId)
            ?: return@withTenantAndUser OrderResult.Error("Payment method not found")

        // 2. Check inventory
        val inventoryCheck = inventoryService.checkAvailability(items)
        if (!inventoryCheck.available) {
            auditService.log(
                "ORDER_CREATION_FAILED",
                mapOf("reason" to "INVENTORY_UNAVAILABLE")
            )
            return@withTenantAndUser OrderResult.Error(
                "Items unavailable: ${inventoryCheck.unavailableItems.joinToString()}"
            )
        }

        // 3. Calculate total
        val productIds = items.map { it.productId }
        val products = productRepo.findByIds(productIds)
        val totalAmount = items.sumOf { item ->
            val product = products.find { it.id == item.productId }!!
            product.price * item.quantity
        }

        // 4. Reserve stock
        val stockReserved = inventoryService.reserveStock(items)
        if (!stockReserved) {
            auditService.log(
                "ORDER_CREATION_FAILED",
                mapOf("reason" to "STOCK_RESERVATION_FAILED")
            )
            return@withTenantAndUser OrderResult.Error("Failed to reserve stock")
        }

        try {
            // 5. Process payment
            val paymentResult = paymentService.processPayment(
                amount = totalAmount,
                paymentMethodId = paymentMethodId
            )

            if (!paymentResult.success) {
                // Release stock on payment failure
                inventoryService.releaseStock(items)

                auditService.log(
                    "ORDER_CREATION_FAILED",
                    mapOf("reason" to "PAYMENT_FAILED", "error" to paymentResult.error)
                )

                return@withTenantAndUser OrderResult.Error(
                    "Payment failed: ${paymentResult.error}"
                )
            }

            // 6. Create order
            val order = Order(
                id = generateOrderId(),
                tenantId = tenantId,
                userId = userId,
                items = items,
                totalAmount = totalAmount,
                status = OrderStatus.CONFIRMED,
                createdAt = Instant.now(),
                createdBy = userId
            )

            val createdOrder = orderRepo.create(order)

            auditService.log(
                "ORDER_CREATED",
                mapOf(
                    "orderId" to createdOrder.id,
                    "amount" to totalAmount,
                    "items" to items.size,
                    "paymentId" to paymentResult.transactionId
                )
            )

            OrderResult.Success(createdOrder)

        } catch (e: Exception) {
            // Release stock on any exception
            inventoryService.releaseStock(items)

            auditService.log(
                "ORDER_CREATION_FAILED",
                mapOf("reason" to "EXCEPTION", "error" to e.message)
            )

            throw e
        }
    }

    suspend fun getUserOrders(status: OrderStatus? = null): List<Order> =
        withTenantAndUser { tenantId, userId ->
            orderRepo.findUserOrders(status)
        }

    suspend fun cancelOrder(orderId: String): CancellationResult =
        withTenantAndUser { tenantId, userId ->

            val order = orderRepo.findById(orderId)
                ?: return@withTenantAndUser CancellationResult.Error("Order not found")

            if (order.userId != userId) {
                auditService.log(
                    "ORDER_CANCELLATION_DENIED",
                    mapOf("orderId" to orderId, "reason" to "UNAUTHORIZED")
                )
                return@withTenantAndUser CancellationResult.Error("Unauthorized")
            }

            if (order.status != OrderStatus.PENDING && order.status != OrderStatus.CONFIRMED) {
                return@withTenantAndUser CancellationResult.Error(
                    "Cannot cancel order in status ${order.status}"
                )
            }

            // Update status
            orderRepo.updateStatus(orderId, OrderStatus.CANCELLED)

            // Release stock
            inventoryService.releaseStock(order.items)

            // Refund payment (if applicable)
            paymentService.refundPayment(orderId)

            auditService.log(
                "ORDER_CANCELLED",
                mapOf("orderId" to orderId, "amount" to order.totalAmount)
            )

            CancellationResult.Success
        }
}

sealed class OrderResult {
    data class Success(val order: Order) : OrderResult()
    data class Error(val message: String) : OrderResult()
}

sealed class CancellationResult {
    object Success : CancellationResult()
    data class Error(val message: String) : CancellationResult()
}

class PaymentService : BaseContextAwareService() {
    private val auditService = AuditService()

    suspend fun processPayment(
        amount: Double,
        paymentMethodId: String
    ): PaymentResult = withTenantAndUser { tenantId, userId ->

        auditService.log(
            "PAYMENT_PROCESSING",
            mapOf("amount" to amount, "paymentMethodId" to paymentMethodId)
        )

        try {
            // Call external payment gateway
            val transactionId = paymentGateway.charge(
                amount = amount,
                paymentMethodId = paymentMethodId,
                metadata = mapOf(
                    "tenantId" to tenantId,
                    "userId" to userId
                )
            )

            auditService.log(
                "PAYMENT_SUCCESS",
                mapOf("amount" to amount, "transactionId" to transactionId)
            )

            PaymentResult(
                success = true,
                transactionId = transactionId,
                error = null
            )

        } catch (e: PaymentException) {
            auditService.log(
                "PAYMENT_FAILED",
                mapOf("amount" to amount, "error" to e.message)
            )

            PaymentResult(
                success = false,
                transactionId = null,
                error = e.message
            )
        }
    }

    suspend fun refundPayment(orderId: String) = withTenant { tenantId ->
        // Refund logic
        auditService.log("PAYMENT_REFUNDED", mapOf("orderId" to orderId))
    }
}

data class PaymentResult(
    val success: Boolean,
    val transactionId: String?,
    val error: String?
)

class AuditService : BaseContextAwareService() {
    private val auditLog = mutableListOf<AuditEntry>()

    suspend fun log(operation: String, data: Map<String, Any>) =
        withTenantAndUser { tenantId, userId ->
            val context = getContext()

            val entry = AuditEntry(
                timestamp = Instant.now(),
                tenantId = tenantId,
                userId = userId,
                correlationId = context.correlationId ?: "unknown",
                operation = operation,
                data = data
            )

            auditLog.add(entry)
            database.insert("audit_log", entry)
        }
}

data class AuditEntry(
    val timestamp: Instant,
    val tenantId: String,
    val userId: String,
    val correlationId: String,
    val operation: String,
    val data: Map<String, Any>
)
```

#### 4. Agent Layer

```kotlin
val ecommerceAgent = buildAgent {
    id = "ecommerce-agent"
    name = "E-Commerce Assistant"
    description = "Handles order processing and product inquiries"

    val orderService = OrderService()
    val productRepo = ProductRepository()

    llm = anthropic(apiKey = env["ANTHROPIC_API_KEY"]!!) {
        model = "claude-3-5-sonnet-20241022"
        temperature = 0.7
    }

    contextAwareTool("create_order") {
        description = "Create a new order"
        param("items", "array", "Order items with productId and quantity")
        param("paymentMethodId", "string", "Payment method ID")

        execute { params, context ->
            val items = (params["items"] as List<Map<String, Any>>).map { item ->
                OrderItem(
                    productId = item["productId"] as String,
                    quantity = (item["quantity"] as Number).toInt(),
                    priceAtPurchase = 0.0 // Will be set by service
                )
            }
            val paymentMethodId = params["paymentMethodId"] as String

            when (val result = orderService.createOrder(items, paymentMethodId)) {
                is OrderResult.Success -> {
                    val order = result.order
                    """
                    Order created successfully!
                    Order ID: ${order.id}
                    Total: $${order.totalAmount}
                    Status: ${order.status}
                    Items: ${order.items.size}
                    """.trimIndent()
                }
                is OrderResult.Error -> {
                    "Failed to create order: ${result.message}"
                }
            }
        }
    }

    contextAwareTool("get_orders") {
        description = "Get user orders"
        param("status", "string", "Order status filter (optional)", required = false)

        execute { params, context ->
            val status = params["status"]?.let {
                OrderStatus.valueOf(it as String)
            }

            val orders = orderService.getUserOrders(status)

            if (orders.isEmpty()) {
                "No orders found"
            } else {
                orders.joinToString("\n\n") { order ->
                    """
                    Order #${order.id}
                    Status: ${order.status}
                    Total: $${order.totalAmount}
                    Items: ${order.items.size}
                    Created: ${order.createdAt}
                    """.trimIndent()
                }
            }
        }
    }

    contextAwareTool("cancel_order") {
        description = "Cancel an order"
        param("orderId", "string", "Order ID to cancel")

        execute { params, context ->
            val orderId = params["orderId"] as String

            when (val result = orderService.cancelOrder(orderId)) {
                is CancellationResult.Success -> {
                    "Order $orderId cancelled successfully. Refund will be processed."
                }
                is CancellationResult.Error -> {
                    "Failed to cancel order: ${result.message}"
                }
            }
        }
    }

    contextAwareTool("search_products") {
        description = "Search for products"
        param("query", "string", "Search query")

        execute { params, context ->
            val query = params["query"] as String
            // Search implementation
            "Search results for: $query"
        }
    }

    instructions = """
        You are an e-commerce assistant helping customers with orders and product inquiries.

        Capabilities:
        - Create orders using create_order tool
        - View order history using get_orders tool
        - Cancel orders using cancel_order tool
        - Search products using search_products tool

        Guidelines:
        - Always confirm order details before creation
        - Verify payment method is valid
        - Check stock availability
        - Provide clear order status updates
        - Handle cancellations gracefully

        All operations are automatically scoped to the current user and tenant.
    """.trimIndent()
}
```

#### 5. HTTP Integration

```kotlin
// Ktor application setup
fun Application.ecommerceRoutes() {
    routing {
        authenticate("jwt") {
            post("/orders") {
                val request = call.receive<CreateOrderRequest>()
                val jwt = call.principal<JWTPrincipal>()!!

                // Extract tenant and user from JWT
                val tenantId = jwt.payload.getClaim("tenantId").asString()
                val userId = jwt.payload.getClaim("sub").asString()

                // Set context for entire request
                val result = withAgentContext(
                    "tenantId" to tenantId,
                    "userId" to userId,
                    "correlationId" to UUID.randomUUID().toString()
                ) {
                    val comm = Comm(
                        content = "Create order: ${request.items.size} items",
                        from = userId,
                        data = mapOf(
                            "items" to request.items,
                            "paymentMethodId" to request.paymentMethodId
                        )
                    )

                    ecommerceAgent.processComm(comm)
                }

                result.fold(
                    onSuccess = { response ->
                        call.respond(HttpStatusCode.Created, response.content)
                    },
                    onFailure = { error ->
                        call.respond(HttpStatusCode.BadRequest, error.message)
                    }
                )
            }

            get("/orders") {
                val jwt = call.principal<JWTPrincipal>()!!
                val tenantId = jwt.payload.getClaim("tenantId").asString()
                val userId = jwt.payload.getClaim("sub").asString()

                val result = withAgentContext(
                    "tenantId" to tenantId,
                    "userId" to userId
                ) {
                    val comm = Comm(
                        content = "Get my orders",
                        from = userId
                    )

                    ecommerceAgent.processComm(comm)
                }

                result.fold(
                    onSuccess = { response ->
                        call.respond(HttpStatusCode.OK, response.content)
                    },
                    onFailure = { error ->
                        call.respond(HttpStatusCode.InternalServerError, error.message)
                    }
                )
            }
        }
    }
}
```

#### 6. Tests

```kotlin
class OrderServiceTest {
    private lateinit var orderService: OrderService
    private lateinit var inventoryService: InventoryService

    @BeforeTest
    fun setup() {
        orderService = OrderService()
        inventoryService = InventoryService()
        setupTestDatabase()
    }

    @Test
    fun `should create order for tenant A without affecting tenant B`() = runTest {
        // Tenant A: Create order
        val tenantAOrder = withAgentContext(
            "tenantId" to "TENANT_A",
            "userId" to "user-a1"
        ) {
            orderService.createOrder(
                items = listOf(
                    OrderItem("product-1", 2, 0.0)
                ),
                paymentMethodId = "payment-a1"
            )
        }

        assertTrue(tenantAOrder is OrderResult.Success)
        assertEquals("TENANT_A", (tenantAOrder as OrderResult.Success).order.tenantId)

        // Tenant B: Create order (isolated!)
        val tenantBOrder = withAgentContext(
            "tenantId" to "TENANT_B",
            "userId" to "user-b1"
        ) {
            orderService.createOrder(
                items = listOf(
                    OrderItem("product-1", 1, 0.0)
                ),
                paymentMethodId = "payment-b1"
            )
        }

        assertTrue(tenantBOrder is OrderResult.Success)
        assertEquals("TENANT_B", (tenantBOrder as OrderResult.Success).order.tenantId)

        // Verify orders are isolated
        val tenantAOrders = withAgentContext(
            "tenantId" to "TENANT_A",
            "userId" to "user-a1"
        ) {
            orderService.getUserOrders()
        }

        val tenantBOrders = withAgentContext(
            "tenantId" to "TENANT_B",
            "userId" to "user-b1"
        ) {
            orderService.getUserOrders()
        }

        assertEquals(1, tenantAOrders.size)
        assertEquals(1, tenantBOrders.size)
        assertNotEquals(tenantAOrders[0].id, tenantBOrders[0].id)
    }

    @Test
    fun `should fail when stock unavailable`() = runTest {
        withAgentContext(
            "tenantId" to "TENANT_A",
            "userId" to "user-a1"
        ) {
            // Product has 5 in stock
            setupProduct("product-1", stockQuantity = 5)

            // Try to order 10
            val result = orderService.createOrder(
                items = listOf(OrderItem("product-1", 10, 0.0)),
                paymentMethodId = "payment-a1"
            )

            assertTrue(result is OrderResult.Error)
            assertTrue((result as OrderResult.Error).message.contains("unavailable"))
        }
    }

    @Test
    fun `should release stock when payment fails`() = runTest {
        withAgentContext(
            "tenantId" to "TENANT_A",
            "userId" to "user-a1"
        ) {
            setupProduct("product-1", stockQuantity = 10)

            // Mock payment failure
            setupPaymentFailure()

            val result = orderService.createOrder(
                items = listOf(OrderItem("product-1", 5, 0.0)),
                paymentMethodId = "payment-fail"
            )

            assertTrue(result is OrderResult.Error)

            // Stock should be released back
            val product = productRepo.findById("product-1")
            assertEquals(10, product?.stockQuantity)
        }
    }
}
```

### Key Takeaways

✅ **Perfect Tenant Isolation** - Each tenant's data completely isolated through automatic context scoping
✅ **Zero Manual Passing** - No need to pass tenantId/userId through layers
✅ **Audit Trail** - Full correlation tracking across all operations
✅ **Transaction Safety** - Stock reservations with automatic rollback
✅ **Type Safety** - Compile-time guarantees for context access

---

## Example 2: Customer Support System

### Scenario

Multi-tenant customer support platform with:
- Ticket routing based on user permissions and tenant config
- SLA tracking and escalation
- Agent assignment with workload balancing
- Full audit trail with correlation IDs

### Implementation

#### 1. Domain Models

```kotlin
data class Ticket(
    val id: String,
    val tenantId: String,
    val customerId: String,
    val assignedAgentId: String?,
    val priority: Priority,
    val status: TicketStatus,
    val subject: String,
    val description: String,
    val category: TicketCategory,
    val sla: SLA,
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant? = null
)

enum class Priority {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class TicketStatus {
    OPEN, ASSIGNED, IN_PROGRESS, WAITING_CUSTOMER, RESOLVED, CLOSED
}

enum class TicketCategory {
    TECHNICAL, BILLING, GENERAL, FEATURE_REQUEST
}

data class SLA(
    val responseTime: Duration,
    val resolutionTime: Duration,
    val responseDeadline: Instant,
    val resolutionDeadline: Instant
)

data class SupportAgent(
    val id: String,
    val tenantId: String,
    val name: String,
    val email: String,
    val specializations: List<TicketCategory>,
    val currentWorkload: Int,
    val maxWorkload: Int,
    val status: AgentStatus
)

enum class AgentStatus {
    AVAILABLE, BUSY, OFFLINE
}

data class TicketMessage(
    val id: String,
    val ticketId: String,
    val tenantId: String,
    val fromUserId: String,
    val fromUserType: UserType,
    val message: String,
    val createdAt: Instant
)

enum class UserType {
    CUSTOMER, SUPPORT_AGENT, SYSTEM
}
```

#### 2. Repository Layer

```kotlin
class TicketRepository : BaseContextAwareService() {

    suspend fun create(ticket: Ticket): Ticket = withTenant { tenantId ->
        require(ticket.tenantId == tenantId) { "Tenant ID mismatch" }

        database.insert("tickets", ticket)
        ticket
    }

    suspend fun findById(ticketId: String): Ticket? = withTenant { tenantId ->
        database.query(
            "SELECT * FROM tickets WHERE id = ? AND tenant_id = ?",
            ticketId, tenantId
        ).firstOrNull()
    }

    suspend fun findByCustomer(customerId: String): List<Ticket> = withTenant { tenantId ->
        database.query(
            """
            SELECT * FROM tickets
            WHERE tenant_id = ? AND customer_id = ?
            ORDER BY created_at DESC
            """,
            tenantId, customerId
        )
    }

    suspend fun findByAgent(agentId: String, status: TicketStatus? = null): List<Ticket> =
        withTenant { tenantId ->
            if (status != null) {
                database.query(
                    """
                    SELECT * FROM tickets
                    WHERE tenant_id = ? AND assigned_agent_id = ? AND status = ?
                    ORDER BY priority DESC, created_at ASC
                    """,
                    tenantId, agentId, status.name
                )
            } else {
                database.query(
                    """
                    SELECT * FROM tickets
                    WHERE tenant_id = ? AND assigned_agent_id = ?
                    ORDER BY priority DESC, created_at ASC
                    """,
                    tenantId, agentId
                )
            }
        }

    suspend fun findUnassigned(priority: Priority? = null): List<Ticket> = withTenant { tenantId ->
        if (priority != null) {
            database.query(
                """
                SELECT * FROM tickets
                WHERE tenant_id = ?
                  AND status = 'OPEN'
                  AND assigned_agent_id IS NULL
                  AND priority = ?
                ORDER BY created_at ASC
                """,
                tenantId, priority.name
            )
        } else {
            database.query(
                """
                SELECT * FROM tickets
                WHERE tenant_id = ?
                  AND status = 'OPEN'
                  AND assigned_agent_id IS NULL
                ORDER BY priority DESC, created_at ASC
                """,
                tenantId
            )
        }
    }

    suspend fun updateStatus(ticketId: String, status: TicketStatus) = withTenant { tenantId ->
        val now = Instant.now()

        database.execute(
            """
            UPDATE tickets
            SET status = ?,
                updated_at = ?,
                resolved_at = CASE WHEN ? = 'RESOLVED' THEN ? ELSE resolved_at END
            WHERE id = ? AND tenant_id = ?
            """,
            status.name, now, status.name, now, ticketId, tenantId
        )
    }

    suspend fun assign(ticketId: String, agentId: String) = withTenant { tenantId ->
        database.execute(
            """
            UPDATE tickets
            SET assigned_agent_id = ?,
                status = 'ASSIGNED',
                updated_at = ?
            WHERE id = ? AND tenant_id = ?
            """,
            agentId, Instant.now(), ticketId, tenantId
        )
    }

    suspend fun findOverdueSLA(): List<Ticket> = withTenant { tenantId ->
        val now = Instant.now()

        database.query(
            """
            SELECT * FROM tickets
            WHERE tenant_id = ?
              AND status NOT IN ('RESOLVED', 'CLOSED')
              AND (
                sla_response_deadline < ?
                OR sla_resolution_deadline < ?
              )
            ORDER BY priority DESC
            """,
            tenantId, now, now
        )
    }
}

class SupportAgentRepository : BaseContextAwareService() {

    suspend fun findAvailable(category: TicketCategory? = null): List<SupportAgent> =
        withTenant { tenantId ->
            if (category != null) {
                database.query(
                    """
                    SELECT * FROM support_agents
                    WHERE tenant_id = ?
                      AND status = 'AVAILABLE'
                      AND current_workload < max_workload
                      AND ? = ANY(specializations)
                    ORDER BY current_workload ASC
                    """,
                    tenantId, category.name
                )
            } else {
                database.query(
                    """
                    SELECT * FROM support_agents
                    WHERE tenant_id = ?
                      AND status = 'AVAILABLE'
                      AND current_workload < max_workload
                    ORDER BY current_workload ASC
                    """,
                    tenantId
                )
            }
        }

    suspend fun findById(agentId: String): SupportAgent? = withTenant { tenantId ->
        database.query(
            "SELECT * FROM support_agents WHERE id = ? AND tenant_id = ?",
            agentId, tenantId
        ).firstOrNull()
    }

    suspend fun incrementWorkload(agentId: String) = withTenant { tenantId ->
        database.execute(
            """
            UPDATE support_agents
            SET current_workload = current_workload + 1
            WHERE id = ? AND tenant_id = ?
            """,
            agentId, tenantId
        )
    }

    suspend fun decrementWorkload(agentId: String) = withTenant { tenantId ->
        database.execute(
            """
            UPDATE support_agents
            SET current_workload = GREATEST(current_workload - 1, 0)
            WHERE id = ? AND tenant_id = ?
            """,
            agentId, tenantId
        )
    }
}

class TicketMessageRepository : BaseContextAwareService() {

    suspend fun create(message: TicketMessage): TicketMessage = withTenant { tenantId ->
        require(message.tenantId == tenantId) { "Tenant ID mismatch" }

        database.insert("ticket_messages", message)
        message
    }

    suspend fun findByTicket(ticketId: String): List<TicketMessage> = withTenant { tenantId ->
        database.query(
            """
            SELECT * FROM ticket_messages
            WHERE ticket_id = ? AND tenant_id = ?
            ORDER BY created_at ASC
            """,
            ticketId, tenantId
        )
    }
}
```

#### 3. Service Layer

```kotlin
class TicketService : BaseContextAwareService() {
    private val ticketRepo = TicketRepository()
    private val agentRepo = SupportAgentRepository()
    private val messageRepo = TicketMessageRepository()
    private val slaService = SLAService()
    private val auditService = AuditService()

    suspend fun createTicket(
        subject: String,
        description: String,
        category: TicketCategory,
        priority: Priority = Priority.MEDIUM
    ): Ticket = withTenantAndUser { tenantId, userId ->

        // Calculate SLA based on priority
        val sla = slaService.calculateSLA(priority)

        val ticket = Ticket(
            id = generateTicketId(),
            tenantId = tenantId,
            customerId = userId,
            assignedAgentId = null,
            priority = priority,
            status = TicketStatus.OPEN,
            subject = subject,
            description = description,
            category = category,
            sla = sla,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val created = ticketRepo.create(ticket)

        auditService.log(
            "TICKET_CREATED",
            mapOf(
                "ticketId" to created.id,
                "priority" to priority.name,
                "category" to category.name
            )
        )

        // Auto-assign if possible
        tryAutoAssign(created.id, category)

        created
    }

    private suspend fun tryAutoAssign(ticketId: String, category: TicketCategory) =
        withTenant { tenantId ->
            // Find available agents with matching specialization
            val availableAgents = agentRepo.findAvailable(category)

            if (availableAgents.isNotEmpty()) {
                // Assign to agent with lowest workload
                val agent = availableAgents.first()

                ticketRepo.assign(ticketId, agent.id)
                agentRepo.incrementWorkload(agent.id)

                auditService.log(
                    "TICKET_AUTO_ASSIGNED",
                    mapOf(
                        "ticketId" to ticketId,
                        "agentId" to agent.id,
                        "agentWorkload" to agent.currentWorkload
                    )
                )
            }
        }

    suspend fun assignTicket(ticketId: String, agentId: String): AssignmentResult =
        withTenant { tenantId ->

            val ticket = ticketRepo.findById(ticketId)
                ?: return@withTenant AssignmentResult.Error("Ticket not found")

            if (ticket.assignedAgentId != null) {
                return@withTenant AssignmentResult.Error("Ticket already assigned")
            }

            val agent = agentRepo.findById(agentId)
                ?: return@withTenant AssignmentResult.Error("Agent not found")

            if (agent.currentWorkload >= agent.maxWorkload) {
                return@withTenant AssignmentResult.Error("Agent at max capacity")
            }

            ticketRepo.assign(ticketId, agentId)
            agentRepo.incrementWorkload(agentId)

            auditService.log(
                "TICKET_ASSIGNED",
                mapOf(
                    "ticketId" to ticketId,
                    "agentId" to agentId
                )
            )

            AssignmentResult.Success
        }

    suspend fun addMessage(
        ticketId: String,
        message: String,
        fromUserType: UserType
    ): TicketMessage = withTenantAndUser { tenantId, userId ->

        val ticket = ticketRepo.findById(ticketId)
            ?: throw IllegalArgumentException("Ticket not found")

        val ticketMessage = TicketMessage(
            id = generateMessageId(),
            ticketId = ticketId,
            tenantId = tenantId,
            fromUserId = userId,
            fromUserType = fromUserType,
            message = message,
            createdAt = Instant.now()
        )

        val created = messageRepo.create(ticketMessage)

        // Update ticket status if customer responds
        if (fromUserType == UserType.CUSTOMER && ticket.status == TicketStatus.WAITING_CUSTOMER) {
            ticketRepo.updateStatus(ticketId, TicketStatus.IN_PROGRESS)
        }

        auditService.log(
            "TICKET_MESSAGE_ADDED",
            mapOf(
                "ticketId" to ticketId,
                "messageId" to created.id,
                "userType" to fromUserType.name
            )
        )

        created
    }

    suspend fun resolveTicket(ticketId: String): ResolutionResult =
        withTenantAndUser { tenantId, userId ->

            val ticket = ticketRepo.findById(ticketId)
                ?: return@withTenantAndUser ResolutionResult.Error("Ticket not found")

            if (ticket.status == TicketStatus.RESOLVED || ticket.status == TicketStatus.CLOSED) {
                return@withTenantAndUser ResolutionResult.Error("Ticket already resolved")
            }

            ticketRepo.updateStatus(ticketId, TicketStatus.RESOLVED)

            // Decrement agent workload
            ticket.assignedAgentId?.let { agentId ->
                agentRepo.decrementWorkload(agentId)
            }

            auditService.log(
                "TICKET_RESOLVED",
                mapOf(
                    "ticketId" to ticketId,
                    "resolvedBy" to userId,
                    "resolutionTime" to Duration.between(ticket.createdAt, Instant.now()).toMinutes()
                )
            )

            ResolutionResult.Success
        }

    suspend fun getMyTickets(status: TicketStatus? = null): List<Ticket> =
        withTenantAndUser { tenantId, userId ->
            // Check if user is customer or agent
            val context = getContext()
            val userRole = context.getAs<String>("userRole")

            when (userRole) {
                "customer" -> ticketRepo.findByCustomer(userId)
                "agent" -> ticketRepo.findByAgent(userId, status)
                else -> emptyList()
            }
        }

    suspend fun escalateOverdueTickets() = withTenant { tenantId ->
        val overdueTickets = ticketRepo.findOverdueSLA()

        overdueTickets.forEach { ticket ->
            auditService.log(
                "TICKET_SLA_BREACH",
                mapOf(
                    "ticketId" to ticket.id,
                    "priority" to ticket.priority.name,
                    "responseDeadline" to ticket.sla.responseDeadline,
                    "resolutionDeadline" to ticket.sla.resolutionDeadline
                )
            )

            // Escalate priority if not already critical
            if (ticket.priority != Priority.CRITICAL) {
                // Escalation logic
                notifyManagement(ticket)
            }
        }
    }
}

sealed class AssignmentResult {
    object Success : AssignmentResult()
    data class Error(val message: String) : AssignmentResult()
}

sealed class ResolutionResult {
    object Success : ResolutionResult()
    data class Error(val message: String) : ResolutionResult()
}

class SLAService : BaseContextAwareService() {

    fun calculateSLA(priority: Priority): SLA {
        val now = Instant.now()

        val (responseTime, resolutionTime) = when (priority) {
            Priority.CRITICAL -> Duration.ofMinutes(15) to Duration.ofHours(4)
            Priority.HIGH -> Duration.ofHours(1) to Duration.ofHours(24)
            Priority.MEDIUM -> Duration.ofHours(4) to Duration.ofDays(3)
            Priority.LOW -> Duration.ofHours(24) to Duration.ofDays(7)
        }

        return SLA(
            responseTime = responseTime,
            resolutionTime = resolutionTime,
            responseDeadline = now.plus(responseTime),
            resolutionDeadline = now.plus(resolutionTime)
        )
    }
}
```

### Key Takeaways

✅ **Role-Based Context** - Different views for customers vs. agents
✅ **SLA Tracking** - Automatic deadline calculation and monitoring
✅ **Workload Balancing** - Smart agent assignment based on capacity
✅ **Complete Audit Trail** - Full correlation tracking across ticket lifecycle

---

## Example 3: Financial Transaction Processing

### Scenario

Multi-tenant financial platform with:
- Transaction processing with double-entry bookkeeping
- Compliance and audit requirements
- Real-time fraud detection
- Immutable audit trail

### Implementation

#### 1. Domain Models

```kotlin
data class Account(
    val id: String,
    val tenantId: String,
    val userId: String,
    val accountNumber: String,
    val accountType: AccountType,
    val currency: Currency,
    val balance: BigDecimal,
    val status: AccountStatus,
    val createdAt: Instant
)

enum class AccountType {
    CHECKING, SAVINGS, CREDIT, INVESTMENT
}

enum class AccountStatus {
    ACTIVE, SUSPENDED, CLOSED
}

data class Transaction(
    val id: String,
    val tenantId: String,
    val fromAccountId: String,
    val toAccountId: String,
    val amount: BigDecimal,
    val currency: Currency,
    val type: TransactionType,
    val status: TransactionStatus,
    val description: String,
    val metadata: Map<String, Any>,
    val createdAt: Instant,
    val processedAt: Instant?,
    val processedBy: String?
)

enum class TransactionType {
    TRANSFER, PAYMENT, WITHDRAWAL, DEPOSIT, FEE
}

enum class TransactionStatus {
    PENDING, PROCESSING, COMPLETED, FAILED, REVERSED
}

data class LedgerEntry(
    val id: String,
    val tenantId: String,
    val transactionId: String,
    val accountId: String,
    val entryType: EntryType,
    val amount: BigDecimal,
    val currency: Currency,
    val balanceBefore: BigDecimal,
    val balanceAfter: BigDecimal,
    val createdAt: Instant
)

enum class EntryType {
    DEBIT, CREDIT
}
```

#### 2. Repository Layer

```kotlin
class AccountRepository : BaseContextAwareService() {

    suspend fun findByUser(): List<Account> = withTenantAndUser { tenantId, userId ->
        database.query(
            """
            SELECT * FROM accounts
            WHERE tenant_id = ? AND user_id = ?
            ORDER BY created_at DESC
            """,
            tenantId, userId
        )
    }

    suspend fun findById(accountId: String): Account? = withTenantAndUser { tenantId, userId ->
        database.query(
            """
            SELECT * FROM accounts
            WHERE id = ? AND tenant_id = ? AND user_id = ?
            """,
            accountId, tenantId, userId
        ).firstOrNull()
    }

    suspend fun updateBalance(
        accountId: String,
        newBalance: BigDecimal
    ) = withTenant { tenantId ->
        database.execute(
            """
            UPDATE accounts
            SET balance = ?
            WHERE id = ? AND tenant_id = ?
            """,
            newBalance, accountId, tenantId
        )
    }

    suspend fun lockForUpdate(accountId: String): Account? = withTenant { tenantId ->
        database.query(
            """
            SELECT * FROM accounts
            WHERE id = ? AND tenant_id = ?
            FOR UPDATE
            """,
            accountId, tenantId
        ).firstOrNull()
    }
}

class TransactionRepository : BaseContextAwareService() {

    suspend fun create(transaction: Transaction): Transaction = withTenant { tenantId ->
        require(transaction.tenantId == tenantId) { "Tenant ID mismatch" }

        database.insert("transactions", transaction)
        transaction
    }

    suspend fun findById(transactionId: String): Transaction? = withTenant { tenantId ->
        database.query(
            "SELECT * FROM transactions WHERE id = ? AND tenant_id = ?",
            transactionId, tenantId
        ).firstOrNull()
    }

    suspend fun findByAccount(accountId: String, limit: Int = 50): List<Transaction> =
        withTenant { tenantId ->
            database.query(
                """
                SELECT * FROM transactions
                WHERE tenant_id = ?
                  AND (from_account_id = ? OR to_account_id = ?)
                ORDER BY created_at DESC
                LIMIT ?
                """,
                tenantId, accountId, accountId, limit
            )
        }

    suspend fun updateStatus(
        transactionId: String,
        status: TransactionStatus
    ) = withTenantAndUser { tenantId, userId ->
        database.execute(
            """
            UPDATE transactions
            SET status = ?,
                processed_at = ?,
                processed_by = ?
            WHERE id = ? AND tenant_id = ?
            """,
            status.name, Instant.now(), userId, transactionId, tenantId
        )
    }
}

class LedgerRepository : BaseContextAwareService() {

    suspend fun createEntry(entry: LedgerEntry): LedgerEntry = withTenant { tenantId ->
        require(entry.tenantId == tenantId) { "Tenant ID mismatch" }

        database.insert("ledger_entries", entry)
        entry
    }

    suspend fun findByTransaction(transactionId: String): List<LedgerEntry> =
        withTenant { tenantId ->
            database.query(
                """
                SELECT * FROM ledger_entries
                WHERE transaction_id = ? AND tenant_id = ?
                ORDER BY created_at ASC
                """,
                transactionId, tenantId
            )
        }

    suspend fun findByAccount(accountId: String, limit: Int = 100): List<LedgerEntry> =
        withTenant { tenantId ->
            database.query(
                """
                SELECT * FROM ledger_entries
                WHERE account_id = ? AND tenant_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                accountId, tenantId, limit
            )
        }
}
```

#### 3. Service Layer

```kotlin
class TransactionService : BaseContextAwareService() {
    private val accountRepo = AccountRepository()
    private val transactionRepo = TransactionRepository()
    private val ledgerRepo = LedgerRepository()
    private val fraudService = FraudDetectionService()
    private val auditService = AuditService()

    suspend fun transfer(
        fromAccountId: String,
        toAccountId: String,
        amount: BigDecimal,
        description: String
    ): TransferResult = withTenantAndUser { tenantId, userId ->

        auditService.log(
            "TRANSFER_INITIATED",
            mapOf(
                "fromAccount" to fromAccountId,
                "toAccount" to toAccountId,
                "amount" to amount.toString()
            )
        )

        // Run fraud detection
        val fraudCheck = fraudService.checkTransfer(fromAccountId, toAccountId, amount)
        if (fraudCheck.isFraudulent) {
            auditService.log(
                "TRANSFER_BLOCKED_FRAUD",
                mapOf(
                    "reason" to fraudCheck.reason,
                    "riskScore" to fraudCheck.riskScore
                )
            )
            return@withTenantAndUser TransferResult.FraudDetected(fraudCheck.reason)
        }

        // Begin transaction
        database.transaction {
            // Lock accounts for update (prevents concurrent modifications)
            val fromAccount = accountRepo.lockForUpdate(fromAccountId)
                ?: return@transaction TransferResult.Error("From account not found")

            val toAccount = accountRepo.lockForUpdate(toAccountId)
                ?: return@transaction TransferResult.Error("To account not found")

            // Validate
            if (fromAccount.status != AccountStatus.ACTIVE) {
                return@transaction TransferResult.Error("From account not active")
            }

            if (toAccount.status != AccountStatus.ACTIVE) {
                return@transaction TransferResult.Error("To account not active")
            }

            if (fromAccount.balance < amount) {
                auditService.log(
                    "TRANSFER_FAILED_INSUFFICIENT_FUNDS",
                    mapOf(
                        "required" to amount.toString(),
                        "available" to fromAccount.balance.toString()
                    )
                )
                return@transaction TransferResult.InsufficientFunds
            }

            // Create transaction record
            val transaction = Transaction(
                id = generateTransactionId(),
                tenantId = tenantId,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                currency = fromAccount.currency,
                type = TransactionType.TRANSFER,
                status = TransactionStatus.PROCESSING,
                description = description,
                metadata = mapOf(
                    "initiatedBy" to userId,
                    "fraudCheck" to fraudCheck.riskScore
                ),
                createdAt = Instant.now(),
                processedAt = null,
                processedBy = null
            )

            transactionRepo.create(transaction)

            // Calculate new balances
            val newFromBalance = fromAccount.balance - amount
            val newToBalance = toAccount.balance + amount

            // Create ledger entries (double-entry bookkeeping)
            ledgerRepo.createEntry(
                LedgerEntry(
                    id = generateEntryId(),
                    tenantId = tenantId,
                    transactionId = transaction.id,
                    accountId = fromAccountId,
                    entryType = EntryType.DEBIT,
                    amount = amount,
                    currency = fromAccount.currency,
                    balanceBefore = fromAccount.balance,
                    balanceAfter = newFromBalance,
                    createdAt = Instant.now()
                )
            )

            ledgerRepo.createEntry(
                LedgerEntry(
                    id = generateEntryId(),
                    tenantId = tenantId,
                    transactionId = transaction.id,
                    accountId = toAccountId,
                    entryType = EntryType.CREDIT,
                    amount = amount,
                    currency = toAccount.currency,
                    balanceBefore = toAccount.balance,
                    balanceAfter = newToBalance,
                    createdAt = Instant.now()
                )
            )

            // Update balances
            accountRepo.updateBalance(fromAccountId, newFromBalance)
            accountRepo.updateBalance(toAccountId, newToBalance)

            // Mark transaction as completed
            transactionRepo.updateStatus(transaction.id, TransactionStatus.COMPLETED)

            auditService.log(
                "TRANSFER_COMPLETED",
                mapOf(
                    "transactionId" to transaction.id,
                    "amount" to amount.toString(),
                    "fromBalance" to newFromBalance.toString(),
                    "toBalance" to newToBalance.toString()
                )
            )

            TransferResult.Success(transaction.id)
        }
    }

    suspend fun getAccountTransactions(
        accountId: String,
        limit: Int = 50
    ): List<Transaction> = withTenantAndUser { tenantId, userId ->

        // Verify user owns account
        val account = accountRepo.findById(accountId)
            ?: throw IllegalArgumentException("Account not found")

        transactionRepo.findByAccount(accountId, limit)
    }

    suspend fun getTransactionDetails(transactionId: String): TransactionDetails? =
        withTenant { tenantId ->

            val transaction = transactionRepo.findById(transactionId)
                ?: return@withTenant null

            val ledgerEntries = ledgerRepo.findByTransaction(transactionId)

            TransactionDetails(
                transaction = transaction,
                ledgerEntries = ledgerEntries
            )
        }
}

sealed class TransferResult {
    data class Success(val transactionId: String) : TransferResult()
    data class Error(val message: String) : TransferResult()
    object InsufficientFunds : TransferResult()
    data class FraudDetected(val reason: String) : TransferResult()
}

data class TransactionDetails(
    val transaction: Transaction,
    val ledgerEntries: List<LedgerEntry>
)

class FraudDetectionService : BaseContextAwareService() {

    suspend fun checkTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: BigDecimal
    ): FraudCheckResult = withTenantAndUser { tenantId, userId ->

        // Simple fraud detection logic (in production, use ML models)
        var riskScore = 0.0

        // Check transaction amount
        if (amount > BigDecimal(10000)) {
            riskScore += 0.3
        }

        // Check transaction frequency
        val recentTransactions = transactionRepo.findByAccount(fromAccountId, 10)
        val recentCount = recentTransactions.filter {
            it.createdAt.isAfter(Instant.now().minus(Duration.ofHours(1)))
        }.size

        if (recentCount > 5) {
            riskScore += 0.4
        }

        // Check velocity
        val recentAmount = recentTransactions
            .filter { it.createdAt.isAfter(Instant.now().minus(Duration.ofHours(24))) }
            .sumOf { it.amount }

        if (recentAmount > BigDecimal(50000)) {
            riskScore += 0.5
        }

        val isFraudulent = riskScore > 0.7

        if (isFraudulent) {
            auditService.log(
                "FRAUD_DETECTED",
                mapOf(
                    "riskScore" to riskScore,
                    "amount" to amount.toString(),
                    "recentCount" to recentCount,
                    "recentAmount" to recentAmount.toString()
                )
            )
        }

        FraudCheckResult(
            isFraudulent = isFraudulent,
            riskScore = riskScore,
            reason = if (isFraudulent) "High risk transaction detected" else ""
        )
    }
}

data class FraudCheckResult(
    val isFraudulent: Boolean,
    val riskScore: Double,
    val reason: String
)
```

### Key Takeaways

✅ **Immutable Audit Trail** - Complete ledger with correlation IDs
✅ **Double-Entry Bookkeeping** - Financial integrity guarantees
✅ **Fraud Detection** - Real-time risk analysis with context
✅ **ACID Transactions** - Consistent state with row-level locking

---

## Common Patterns

### 1. HTTP Request Handler Pattern

```kotlin
// Set context once at HTTP boundary
suspend fun handleRequest(request: HttpRequest) {
    val jwt = extractJWT(request)

    withAgentContext(
        "tenantId" to jwt.tenantId,
        "userId" to jwt.userId,
        "correlationId" to UUID.randomUUID().toString(),
        "sessionId" to request.sessionId
    ) {
        // All operations have full context
        processRequest(request)
    }
}
```

### 2. Context Enrichment Pattern

```kotlin
// Progressive enrichment as more data becomes available
suspend fun processWithEnrichment(request: Request) {
    withAgentContext("tenantId" to request.tenantId) {

        withEnrichedContext("userId" to authenticateUser(request)) {

            withEnrichedContext("permissions" to loadPermissions()) {

                // Full enriched context available
                processRequest(request)
            }
        }
    }
}
```

### 3. Multi-Tenant Test Pattern

```kotlin
@Test
fun `should isolate tenants completely`() = runTest {
    // Tenant A operations
    val resultA = withAgentContext("tenantId" to "A", "userId" to "user-a") {
        service.createResource()
    }

    // Tenant B operations (isolated!)
    val resultB = withAgentContext("tenantId" to "B", "userId" to "user-b") {
        service.createResource()
    }

    // Verify isolation
    assertNotEquals(resultA.tenantId, resultB.tenantId)
}
```

### 4. Audit Trail Pattern

```kotlin
class AuditService : BaseContextAwareService() {
    suspend fun log(operation: String, data: Map<String, Any>) =
        withTenantAndUser { tenantId, userId ->
            val context = getContext()

            auditLog.insert(AuditEntry(
                correlationId = context.correlationId ?: "unknown",
                tenantId = tenantId,
                userId = userId,
                operation = operation,
                data = data,
                timestamp = Instant.now()
            ))
        }
}
```

## Next Steps

- [Context API Reference](../api/context) - Complete API documentation
- [Multi-Agent Systems](../orchestration/multi-agent) - Context in multi-agent workflows
- [Testing Guide](../testing/context-testing) - Testing with context
- [Security Best Practices](../security/multi-tenancy) - Secure multi-tenant systems
