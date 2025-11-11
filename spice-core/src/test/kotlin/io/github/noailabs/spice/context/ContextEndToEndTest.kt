package io.github.noailabs.spice.context

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * 🎯 End-to-End Context Integration Tests
 *
 * Real-world scenarios testing complete context flow from
 * HTTP request → Agent → Tool → Service → Database
 *
 * @since 0.4.0
 */
class ContextEndToEndTest {

    // Mock services

    class PolicyRepository : BaseContextAwareService() {
        suspend fun findByType(type: String): Policy = withTenant { tenantId ->
            Policy(
                id = "policy-${type}",
                tenantId = tenantId,
                type = type,
                active = true
            )
        }

        suspend fun createPolicy(type: String, userId: String): Policy = withTenantAndUser { tenantId, createdBy ->
            Policy(
                id = "policy-new-${System.currentTimeMillis()}",
                tenantId = tenantId,
                type = type,
                active = true,
                createdBy = createdBy
            )
        }
    }

    data class Policy(
        val id: String,
        val tenantId: String,
        val type: String,
        val active: Boolean,
        val createdBy: String? = null
    )

    class OrderRepository : BaseContextAwareService() {
        suspend fun findUserOrders(): List<Order> = withTenantAndUser { tenantId, userId ->
            listOf(
                Order("order-1", tenantId, userId, listOf("item1")),
                Order("order-2", tenantId, userId, listOf("item2"))
            )
        }

        suspend fun createOrder(items: List<String>): Order = withTenantAndUser { tenantId, userId ->
            Order(
                id = "order-${System.currentTimeMillis()}",
                tenantId = tenantId,
                userId = userId,
                items = items
            )
        }
    }

    data class Order(
        val id: String,
        val tenantId: String,
        val userId: String,
        val items: List<String>
    )

    // Tests

    /**
     * Scenario 1: Simple Agent → Tool → Service flow
     */
    @Test
    fun `E2E - agent with context-aware tool should access service layer`() = runTest {
        // Given: Repository and Agent
        val policyRepo = PolicyRepository()

        val agent = buildAgent {
            id = "policy-agent"
            name = "Policy Agent"

            contextAwareTool("lookup_policy") {
                description = "Look up policy by type"
                param("policyType", "string", "Policy type")

                execute { params, context ->
                    val policyType = params["policyType"]?.toString() ?: throw IllegalArgumentException("Missing 'policyType'")

                    // Service automatically gets context!
                    val policy = policyRepo.findByType(policyType)

                    "Found: ${policy.id} for tenant ${policy.tenantId}"
                }
            }
        }

        // When: Process comm with context
        val result = withAgentContext(
            "tenantId" to "CHIC",
            "userId" to "user-123"
        ) {
            val comm = Comm(content = "lookup auto policy", from = "user-123")
            val runtime = DefaultAgentRuntime(
                context = AgentContext.of(
                    "tenantId" to "CHIC",
                    "userId" to "user-123"
                )
            )
            agent.processComm(comm, runtime)
        }

        // Then: Should complete full flow
        assertTrue(result.isSuccess)
        val responseComm = result.getOrNull()!!
        assertTrue(responseComm.content.contains("tenant CHIC"))
    }

    /**
     * Scenario 2: Multi-tenant order processing
     */
    @Test
    fun `E2E - multi-tenant order processing with full context`() = runTest {
        // Given: Order service and agent
        val orderRepo = OrderRepository()

        val agent = buildAgent {
            id = "order-agent"

            contextAwareTool("create_order") {
                description = "Create new order"
                param("items", "array", "Order items")

                execute { params, context ->
                    val items = params["items"] as List<*>
                    val itemStrings = items.map { it.toString() }

                    // Create order with automatic tenant/user context
                    val order = orderRepo.createOrder(itemStrings)

                    "Order ${order.id} created for tenant ${order.tenantId} by ${order.userId}"
                }
            }

            contextAwareTool("list_orders") {
                description = "List user orders"

                execute { params, context ->
                    val orders = orderRepo.findUserOrders()
                    "Found ${orders.size} orders for tenant ${context.tenantId}"
                }
            }
        }

        // When: Process as Tenant A
        val tenantAResult = withAgentContext(
            "tenantId" to "TENANT_A",
            "userId" to "user-a1"
        ) {
            val comm = Comm(content = "create order: laptop, mouse", from = "user-a1")
            val runtime = DefaultAgentRuntime(
                context = AgentContext.of(
                    "tenantId" to "TENANT_A",
                    "userId" to "user-a1"
                )
            )
            agent.processComm(comm, runtime)
        }

        // When: Process as Tenant B
        val tenantBResult = withAgentContext(
            "tenantId" to "TENANT_B",
            "userId" to "user-b1"
        ) {
            val comm = Comm(content = "create order: keyboard", from = "user-b1")
            val runtime = DefaultAgentRuntime(
                context = AgentContext.of(
                    "tenantId" to "TENANT_B",
                    "userId" to "user-b1"
                )
            )
            agent.processComm(comm, runtime)
        }

        // Then: Both should succeed with correct tenant isolation
        assertTrue(tenantAResult.isSuccess)
        assertTrue(tenantBResult.isSuccess)

        val responseA = tenantAResult.getOrNull()!!
        val responseB = tenantBResult.getOrNull()!!

        assertTrue(responseA.content.contains("TENANT_A"))
        assertTrue(responseA.content.contains("user-a1"))
        assertTrue(responseB.content.contains("TENANT_B"))
        assertTrue(responseB.content.contains("user-b1"))
    }

    /**
     * Scenario 3: Context enrichment with extensions
     */
    @Test
    fun `E2E - context enrichment with extensions throughout flow`() = runTest {
        // Given: Register extensions
        ContextExtensionRegistry.clear()

        val tenantExtension = TenantContextExtension { tenantId ->
            mapOf(
                "features" to listOf("premium", "analytics"),
                "maxOrders" to 100
            )
        }

        ContextExtensionRegistry.register(tenantExtension)

        val policyRepo = PolicyRepository()

        val agent = buildAgent {
            id = "enriched-agent"

            contextAwareTool("check_features") {
                description = "Check tenant features"

                execute { params, context ->
                    val features = context.get("tenant_features") as? List<*>
                    "Features: ${features?.joinToString()}"
                }
            }
        }

        // When: Process with enrichment
        val baseContext = AgentContext.of("tenantId" to "PREMIUM")
        val enriched = ContextExtensionRegistry.enrichContext(baseContext)

        val result = withAgentContext(enriched) {
            val comm = Comm(content = "check my features", from = "user")
            val runtime = DefaultAgentRuntime(context = enriched)
            agent.processComm(comm, runtime)
        }

        // Then: Should access enriched context
        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        assertTrue(response.content.contains("premium") || response.content.contains("analytics"))
    }

    /**
     * Scenario 4: Comm context propagation
     */
    @Test
    fun `E2E - comm carries context through agent processing`() = runTest {
        // Given: Agent that uses comm context
        val agent = buildAgent {
            id = "comm-context-agent"

            simpleContextTool("echo_context", "Echo context") { params, context ->
                "Comm context: tenant=${context.tenantId}, user=${context.userId}"
            }
        }

        // When: Send comm with embedded context
        val comm = Comm(
            content = "echo",
            from = "user-comm"
        ).withContextValues(
            "tenantId" to "COMM_TENANT",
            "userId" to "user-comm"
        )

        val result = withAgentContext(
            "tenantId" to "COMM_TENANT",
            "userId" to "user-comm"
        ) {
            val runtime = DefaultAgentRuntime(
                context = AgentContext.of(
                    "tenantId" to "COMM_TENANT",
                    "userId" to "user-comm"
                )
            )
            agent.processComm(comm, runtime)
        }

        // Then: Should use comm context
        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        assertTrue(response.content.contains("COMM_TENANT"))
        assertTrue(response.content.contains("user-comm"))
    }

    /**
     * Scenario 5: Nested service calls with context
     */
    @Test
    fun `E2E - nested service calls share context`() = runTest {
        // Given: Services that call each other
        class CompositeService : BaseContextAwareService() {
            private val policyRepo = PolicyRepository()
            private val orderRepo = OrderRepository()

            suspend fun createOrderWithPolicy(items: List<String>, policyType: String): String = withTenantAndUser { tenantId, userId ->
                // First: Check policy
                val policy = policyRepo.findByType(policyType)

                // Then: Create order
                val order = orderRepo.createOrder(items)

                "Order ${order.id} created with policy ${policy.id} for tenant $tenantId"
            }
        }

        val compositeService = CompositeService()

        val agent = buildAgent {
            id = "composite-agent"

            contextAwareTool("create_order_with_policy") {
                description = "Create order with policy check"
                param("items", "array", "Order items")
                param("policyType", "string", "Policy type")

                execute { params, context ->
                    val items = (params["items"] as? List<*>)?.map { it.toString() } ?: throw IllegalArgumentException("Missing 'items'")
                    val policyType = params["policyType"]?.toString() ?: throw IllegalArgumentException("Missing 'policyType'")

                    compositeService.createOrderWithPolicy(items, policyType)
                }
            }
        }

        // When: Process composite operation
        val result = withAgentContext(
            "tenantId" to "COMPOSITE",
            "userId" to "user-comp"
        ) {
            val comm = Comm(content = "create order with premium policy", from = "user-comp")
            val runtime = DefaultAgentRuntime(
                context = AgentContext.of(
                    "tenantId" to "COMPOSITE",
                    "userId" to "user-comp"
                )
            )
            agent.processComm(comm, runtime)
        }

        // Then: Both nested operations should succeed with same context
        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        assertTrue(response.content.contains("COMPOSITE"))
        assertTrue(response.content.contains("policy"))
        assertTrue(response.content.contains("order"))
    }

    /**
     * Scenario 6: Error handling preserves context
     */
    @Test
    fun `E2E - error handling preserves context information`() = runTest {
        // Given: Service that can fail
        class FailingService : BaseContextAwareService() {
            suspend fun riskyOperation(): String = withTenantAndUser { tenantId, userId ->
                throw IllegalArgumentException("Operation failed for tenant $tenantId")
            }
        }

        val failingService = FailingService()

        val agent = buildAgent {
            id = "error-agent"

            contextAwareTool("risky_tool") {
                description = "Risky operation"

                execute { params, context ->
                    try {
                        failingService.riskyOperation()
                    } catch (e: Exception) {
                        // Error message includes context
                        "Error: ${e.message}"
                    }
                }
            }
        }

        // When: Process with context
        val result = withAgentContext(
            "tenantId" to "ERROR_TENANT",
            "userId" to "user-error"
        ) {
            val comm = Comm(content = "run risky operation", from = "user-error")
            val runtime = DefaultAgentRuntime(
                context = AgentContext.of(
                    "tenantId" to "ERROR_TENANT",
                    "userId" to "user-error"
                )
            )
            agent.processComm(comm, runtime)
        }

        // Then: Error should include tenant context
        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        assertTrue(response.content.contains("ERROR_TENANT"))
    }

    /**
     * Scenario 7: Correlation tracking across operations
     */
    @Test
    fun `E2E - correlation ID propagates through entire flow`() = runTest {
        // Given: Service that logs correlation
        class AuditService : BaseContextAwareService() {
            val auditLog = mutableListOf<String>()

            suspend fun logOperation(operation: String) = withTenantAndUser { tenantId, userId ->
                val context = getContext()
                val correlationId = context.correlationId ?: "none"
                val log = "[$correlationId] $operation by $userId in $tenantId"
                auditLog.add(log)
                log
            }
        }

        val auditService = AuditService()

        val agent = buildAgent {
            id = "audit-agent"

            contextAwareTool("create_resource") {
                description = "Create resource with audit"
                param("resourceType", "string", "Resource type")

                execute { params, context ->
                    val resourceType = params["resourceType"]?.toString() ?: throw IllegalArgumentException("Missing 'resourceType'")

                    // Multiple audit logs
                    auditService.logOperation("VALIDATE_$resourceType")
                    auditService.logOperation("CREATE_$resourceType")
                    auditService.logOperation("NOTIFY_$resourceType")

                    "Resource created with ${auditService.auditLog.size} audit logs"
                }
            }
        }

        // When: Process with correlation ID
        val correlationId = "req-${System.currentTimeMillis()}"

        val result = withAgentContext(
            "tenantId" to "AUDIT",
            "userId" to "user-audit",
            "correlationId" to correlationId
        ) {
            val comm = Comm(content = "create policy resource", from = "user-audit")
            val runtime = DefaultAgentRuntime(
                context = AgentContext.of(
                    "tenantId" to "AUDIT",
                    "userId" to "user-audit",
                    "correlationId" to correlationId
                )
            )
            agent.processComm(comm, runtime)
        }

        // Then: All audit logs should have same correlation ID
        assertTrue(result.isSuccess)
        assertEquals(3, auditService.auditLog.size)
        auditService.auditLog.forEach { log ->
            assertTrue(log.startsWith("[$correlationId]"))
            assertTrue(log.contains("AUDIT"))
            assertTrue(log.contains("user-audit"))
        }
    }

    /**
     * Scenario 8: Multiple agents with shared context
     */
    @Test
    fun `E2E - multiple agents share context in workflow`() = runTest {
        // Given: Two agents with shared context
        val policyRepo = PolicyRepository()
        val orderRepo = OrderRepository()

        val policyAgent = buildAgent {
            id = "policy-agent"
            contextAwareTool("validate_policy") {
                description = "Validate policy"
                execute { params, context ->
                    val policy = policyRepo.findByType("standard")
                    "Policy ${policy.id} valid for tenant ${policy.tenantId}"
                }
            }
        }

        val orderAgent = buildAgent {
            id = "order-agent"
            contextAwareTool("create_order") {
                description = "Create order"
                param("items", "array", "Order items")
                execute { params, context ->
                    val items = (params["items"] as List<*>).map { it.toString() }
                    val order = orderRepo.createOrder(items)
                    "Order ${order.id} for tenant ${order.tenantId}"
                }
            }
        }

        // When: Process with shared context
        val sharedContext = AgentContext.of(
            "tenantId" to "SHARED",
            "userId" to "user-shared"
        )

        val policyResult = withAgentContext(sharedContext) {
            val runtime = DefaultAgentRuntime(context = sharedContext)
            policyAgent.processComm(
                Comm(content = "validate", from = "user-shared"),
                runtime
            )
        }

        val orderResult = withAgentContext(sharedContext) {
            val runtime = DefaultAgentRuntime(context = sharedContext)
            orderAgent.processComm(
                Comm(content = "create order", from = "user-shared"),
                runtime
            )
        }

        // Then: Both should use shared tenant
        assertTrue(policyResult.isSuccess)
        assertTrue(orderResult.isSuccess)

        val policyResponse = policyResult.getOrNull()!!
        val orderResponse = orderResult.getOrNull()!!

        assertTrue(policyResponse.content.contains("SHARED"))
        assertTrue(orderResponse.content.contains("SHARED"))
    }
}
