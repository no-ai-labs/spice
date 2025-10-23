package io.github.noailabs.spice.context

import io.github.noailabs.spice.dsl.currentAgentContext
import io.github.noailabs.spice.dsl.withAgentContext
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * 🎯 ContextAwareService Tests
 *
 * Comprehensive tests for service layer context support.
 * Tests cover:
 * - ContextAwareService interface
 * - BaseContextAwareService helper methods
 * - withTenant, withUser, withSession patterns
 * - withTenantAndUser combination
 * - Default value handling
 * - Error cases
 * - Real-world service patterns
 *
 * @since 0.4.0
 */
class ContextAwareServiceTest {

    // Test service implementations

    /**
     * Simple service using ContextAwareService interface
     */
    class SimpleService : ContextAwareService {
        suspend fun getTenantInfo(): String {
            val context = getContext()
            return "Tenant: ${context.tenantId}"
        }

        suspend fun getTenantInfoOrNull(): String? {
            val context = getContextOrNull()
            return context?.tenantId?.let { "Tenant: $it" }
        }
    }

    /**
     * Repository using BaseContextAwareService
     */
    class PolicyRepository : BaseContextAwareService() {
        suspend fun findByType(policyType: String): String = withTenant { tenantId ->
            "Policy[$policyType] for tenant[$tenantId]"
        }

        suspend fun findByUser(): String = withUser { userId ->
            "Policies for user[$userId]"
        }

        suspend fun findBySession(): String = withSession { sessionId ->
            "Policies for session[$sessionId]"
        }

        suspend fun create(policyType: String): String = withTenantAndUser { tenantId, userId ->
            "Created $policyType for tenant[$tenantId] by user[$userId]"
        }

        suspend fun findWithDefault(policyType: String): String = withTenantOrDefault("global") { tenantId ->
            "Policy[$policyType] for tenant[$tenantId]"
        }

        suspend fun findUserWithDefault(): String = withUserOrDefault("anonymous") { userId ->
            "Policies for user[$userId]"
        }
    }

    /**
     * Complex service with multiple operations
     */
    class OrderService : BaseContextAwareService() {
        data class Order(
            val id: String,
            val tenantId: String,
            val userId: String,
            val items: List<String>
        )

        suspend fun createOrder(items: List<String>): Order = withTenantAndUser { tenantId, userId ->
            Order(
                id = "order-${System.currentTimeMillis()}",
                tenantId = tenantId,
                userId = userId,
                items = items
            )
        }

        suspend fun listOrders(): List<Order> = withTenant { tenantId ->
            // Simulate database query
            listOf(
                Order("order-1", tenantId, "user-1", listOf("item1")),
                Order("order-2", tenantId, "user-2", listOf("item2"))
            )
        }

        suspend fun getUserOrders(): List<Order> = withTenantAndUser { tenantId, userId ->
            listOf(
                Order("order-1", tenantId, userId, listOf("item1"))
            )
        }
    }

    // Tests

    /**
     * Test: ContextAwareService.getContext should return context
     */
    @Test
    fun `ContextAwareService getContext should return AgentContext`() = runTest {
        // Given: Service and context
        val service = SimpleService()

        // When: Call service method with context
        val result = withAgentContext("tenantId" to "TEST_TENANT") {
            service.getTenantInfo()
        }

        // Then: Should access context
        assertEquals("Tenant: TEST_TENANT", result)
    }

    /**
     * Test: ContextAwareService.getContext should throw when no context
     */
    @Test
    fun `ContextAwareService getContext should throw when no context available`() = runTest {
        // Given: Service without context
        val service = SimpleService()

        // When/Then: Should throw
        assertFailsWith<IllegalStateException> {
            service.getTenantInfo()
        }
    }

    /**
     * Test: ContextAwareService.getContextOrNull should return null when no context
     */
    @Test
    fun `ContextAwareService getContextOrNull should return null when no context`() = runTest {
        // Given: Service without context
        val service = SimpleService()

        // When: Call method using getContextOrNull
        val result = service.getTenantInfoOrNull()

        // Then: Should return null (not throw)
        assertNull(result)
    }

    /**
     * Test: BaseContextAwareService.withTenant
     */
    @Test
    fun `withTenant should provide tenantId from context`() = runTest {
        // Given: Repository and context
        val repository = PolicyRepository()

        // When: Call method using withTenant
        val result = withAgentContext("tenantId" to "CHIC") {
            repository.findByType("auto")
        }

        // Then: Should access tenantId
        assertEquals("Policy[auto] for tenant[CHIC]", result)
    }

    /**
     * Test: withTenant should throw when tenantId missing
     */
    @Test
    fun `withTenant should throw when tenantId not in context`() = runTest {
        // Given: Repository and context without tenantId
        val repository = PolicyRepository()

        // When/Then: Should throw
        assertFailsWith<IllegalStateException> {
            withAgentContext("userId" to "user-123") {
                repository.findByType("auto")
            }
        }
    }

    /**
     * Test: BaseContextAwareService.withUser
     */
    @Test
    fun `withUser should provide userId from context`() = runTest {
        // Given: Repository and context
        val repository = PolicyRepository()

        // When: Call method using withUser
        val result = withAgentContext("userId" to "user-456") {
            repository.findByUser()
        }

        // Then: Should access userId
        assertEquals("Policies for user[user-456]", result)
    }

    /**
     * Test: withUser should throw when userId missing
     */
    @Test
    fun `withUser should throw when userId not in context`() = runTest {
        // Given: Repository and context without userId
        val repository = PolicyRepository()

        // When/Then: Should throw
        assertFailsWith<IllegalStateException> {
            withAgentContext("tenantId" to "CHIC") {
                repository.findByUser()
            }
        }
    }

    /**
     * Test: BaseContextAwareService.withSession
     */
    @Test
    fun `withSession should provide sessionId from context`() = runTest {
        // Given: Repository and context
        val repository = PolicyRepository()

        // When: Call method using withSession
        val result = withAgentContext("sessionId" to "sess-789") {
            repository.findBySession()
        }

        // Then: Should access sessionId
        assertEquals("Policies for session[sess-789]", result)
    }

    /**
     * Test: BaseContextAwareService.withTenantAndUser
     */
    @Test
    fun `withTenantAndUser should provide both tenantId and userId`() = runTest {
        // Given: Repository and context
        val repository = PolicyRepository()

        // When: Call method using withTenantAndUser
        val result = withAgentContext(
            "tenantId" to "ACME",
            "userId" to "user-999"
        ) {
            repository.create("premium")
        }

        // Then: Should access both values
        assertEquals("Created premium for tenant[ACME] by user[user-999]", result)
    }

    /**
     * Test: withTenantAndUser should throw when tenantId missing
     */
    @Test
    fun `withTenantAndUser should throw when tenantId missing`() = runTest {
        // Given: Repository and context without tenantId
        val repository = PolicyRepository()

        // When/Then: Should throw
        assertFailsWith<IllegalStateException> {
            withAgentContext("userId" to "user-123") {
                repository.create("premium")
            }
        }
    }

    /**
     * Test: withTenantAndUser should throw when userId missing
     */
    @Test
    fun `withTenantAndUser should throw when userId missing`() = runTest {
        // Given: Repository and context without userId
        val repository = PolicyRepository()

        // When/Then: Should throw
        assertFailsWith<IllegalStateException> {
            withAgentContext("tenantId" to "CHIC") {
                repository.create("premium")
            }
        }
    }

    /**
     * Test: BaseContextAwareService.withTenantOrDefault
     */
    @Test
    fun `withTenantOrDefault should use tenant from context when present`() = runTest {
        // Given: Repository and context with tenantId
        val repository = PolicyRepository()

        // When: Call method using withTenantOrDefault
        val result = withAgentContext("tenantId" to "EXPLICIT") {
            repository.findWithDefault("auto")
        }

        // Then: Should use explicit tenant
        assertEquals("Policy[auto] for tenant[EXPLICIT]", result)
    }

    /**
     * Test: withTenantOrDefault should use default when tenant missing
     */
    @Test
    fun `withTenantOrDefault should use default when tenantId not in context`() = runTest {
        // Given: Repository and context without tenantId
        val repository = PolicyRepository()

        // When: Call method using withTenantOrDefault
        val result = withAgentContext("userId" to "user-123") {
            repository.findWithDefault("auto")
        }

        // Then: Should use default
        assertEquals("Policy[auto] for tenant[global]", result)
    }

    /**
     * Test: BaseContextAwareService.withUserOrDefault
     */
    @Test
    fun `withUserOrDefault should use user from context when present`() = runTest {
        // Given: Repository and context with userId
        val repository = PolicyRepository()

        // When: Call method using withUserOrDefault
        val result = withAgentContext("userId" to "user-explicit") {
            repository.findUserWithDefault()
        }

        // Then: Should use explicit user
        assertEquals("Policies for user[user-explicit]", result)
    }

    /**
     * Test: withUserOrDefault should use default when user missing
     */
    @Test
    fun `withUserOrDefault should use default when userId not in context`() = runTest {
        // Given: Repository and context without userId
        val repository = PolicyRepository()

        // When: Call method using withUserOrDefault
        val result = withAgentContext("tenantId" to "CHIC") {
            repository.findUserWithDefault()
        }

        // Then: Should use default
        assertEquals("Policies for user[anonymous]", result)
    }

    /**
     * Test: Complex service - createOrder
     */
    @Test
    fun `OrderService should create order with context values`() = runTest {
        // Given: Service and context
        val service = OrderService()

        // When: Create order
        val order = withAgentContext(
            "tenantId" to "ECOMMERCE",
            "userId" to "customer-123"
        ) {
            service.createOrder(listOf("laptop", "mouse", "keyboard"))
        }

        // Then: Should populate from context
        assertEquals("ECOMMERCE", order.tenantId)
        assertEquals("customer-123", order.userId)
        assertEquals(3, order.items.size)
        assertTrue(order.id.startsWith("order-"))
    }

    /**
     * Test: Complex service - listOrders
     */
    @Test
    fun `OrderService should list orders for tenant`() = runTest {
        // Given: Service and context
        val service = OrderService()

        // When: List orders
        val orders = withAgentContext("tenantId" to "SHOP") {
            service.listOrders()
        }

        // Then: Should query by tenant
        assertEquals(2, orders.size)
        assertTrue(orders.all { it.tenantId == "SHOP" })
    }

    /**
     * Test: Complex service - getUserOrders
     */
    @Test
    fun `OrderService should list orders for tenant and user`() = runTest {
        // Given: Service and context
        val service = OrderService()

        // When: List user orders
        val orders = withAgentContext(
            "tenantId" to "SHOP",
            "userId" to "user-specific"
        ) {
            service.getUserOrders()
        }

        // Then: Should filter by tenant and user
        assertEquals(1, orders.size)
        assertEquals("SHOP", orders[0].tenantId)
        assertEquals("user-specific", orders[0].userId)
    }

    /**
     * Test: Service can access underlying getContext
     */
    @Test
    fun `BaseContextAwareService can access getContext directly`() = runTest {
        // Given: Custom service accessing context directly
        class CustomService : BaseContextAwareService() {
            suspend fun getFullContext(): Map<String, Any?> {
                val context = getContext()
                return mapOf(
                    "tenantId" to context.tenantId,
                    "userId" to context.userId,
                    "sessionId" to context.sessionId,
                    "custom" to context.get("customKey")
                )
            }
        }

        val service = CustomService()

        // When: Access context
        val contextMap = withAgentContext(
            "tenantId" to "FULL",
            "userId" to "user-full",
            "sessionId" to "sess-full",
            "customKey" to "customValue"
        ) {
            service.getFullContext()
        }

        // Then: Should access all values
        assertEquals("FULL", contextMap["tenantId"])
        assertEquals("user-full", contextMap["userId"])
        assertEquals("sess-full", contextMap["sessionId"])
        assertEquals("customValue", contextMap["custom"])
    }

    /**
     * Test: Multiple service calls share context
     */
    @Test
    fun `multiple service calls should share same context`() = runTest {
        // Given: Multiple services
        val policyRepo = PolicyRepository()
        val orderService = OrderService()

        // When: Call multiple services in same context
        withAgentContext(
            "tenantId" to "SHARED",
            "userId" to "user-shared"
        ) {
            val policy = policyRepo.findByType("standard")
            val order = orderService.createOrder(listOf("item1"))

            // Then: Both should use same context
            assertTrue(policy.contains("tenant[SHARED]"))
            assertEquals("SHARED", order.tenantId)
            assertEquals("user-shared", order.userId)
        }
    }

    /**
     * Test: Nested service calls
     */
    @Test
    fun `nested service calls should work correctly`() = runTest {
        // Given: Service that calls another service
        class CompositeService : BaseContextAwareService() {
            private val policyRepo = PolicyRepository()

            suspend fun createOrderWithPolicy(items: List<String>): Pair<String, String> = withTenantAndUser { tenantId, userId ->
                // Inner service call
                val policy = policyRepo.findByType("standard")
                val order = "Order for $tenantId by $userId"
                Pair(order, policy)
            }
        }

        val service = CompositeService()

        // When: Call composite service
        val (order, policy) = withAgentContext(
            "tenantId" to "COMPOSITE",
            "userId" to "user-comp"
        ) {
            service.createOrderWithPolicy(listOf("item1"))
        }

        // Then: Both operations should succeed
        assertTrue(order.contains("COMPOSITE"))
        assertTrue(order.contains("user-comp"))
        assertTrue(policy.contains("tenant[COMPOSITE]"))
    }

    /**
     * Test: Service with custom default values
     */
    @Test
    fun `service can define custom default values`() = runTest {
        // Given: Service with custom defaults
        class CustomDefaultService : BaseContextAwareService() {
            suspend fun findWithCustomDefault(): String = withTenantOrDefault("MY_DEFAULT") { tenantId ->
                "Tenant: $tenantId"
            }
        }

        val service = CustomDefaultService()

        // When: Call without context
        val result = withAgentContext("userId" to "irrelevant") {
            service.findWithCustomDefault()
        }

        // Then: Should use custom default
        assertEquals("Tenant: MY_DEFAULT", result)
    }

    /**
     * Test: Service error messages
     */
    @Test
    fun `service should provide clear error messages`() = runTest {
        // Given: Repository
        val repository = PolicyRepository()

        // When/Then: Error message should be clear
        val exception = assertFailsWith<IllegalStateException> {
            withAgentContext("userId" to "user-123") {
                repository.findByType("auto")
            }
        }

        assertTrue(exception.message!!.contains("tenantId"))
        assertTrue(exception.message!!.contains("context"))
    }

    /**
     * Test: Service with correlation tracking
     */
    @Test
    fun `service can track correlation across operations`() = runTest {
        // Given: Service tracking correlation
        class AuditService : BaseContextAwareService() {
            suspend fun logOperation(operation: String): String = withTenantAndUser { tenantId, userId ->
                val context = getContext()
                val correlationId = context.correlationId ?: "none"
                "[$correlationId] $operation by $userId in $tenantId"
            }
        }

        val service = AuditService()

        // When: Log with correlation
        val log = withAgentContext(
            "tenantId" to "AUDIT",
            "userId" to "user-audit",
            "correlationId" to "req-12345"
        ) {
            service.logOperation("CREATE_ORDER")
        }

        // Then: Should include correlation
        assertEquals("[req-12345] CREATE_ORDER by user-audit in AUDIT", log)
    }
}
