package io.github.noailabs.spice.graph

import io.github.noailabs.spice.*
import io.github.noailabs.spice.context.BaseContextAwareService
import io.github.noailabs.spice.dsl.contextAwareTool
import io.github.noailabs.spice.dsl.withAgentContext
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.RunStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 🎯 Graph + Context Integration Tests
 *
 * Tests that verify Graph system properly integrates with:
 * - AgentContext (multi-tenancy)
 * - contextAwareTool
 * - BaseContextAwareService
 * - Agent with context
 *
 * @since 0.5.0
 */
class GraphContextIntegrationTest {

    // Mock service layer
    class OrderRepository : BaseContextAwareService() {
        suspend fun findOrders(): List<Order> = withTenantAndUser { tenantId, userId ->
            listOf(
                Order("order-1", tenantId, userId, "Product A"),
                Order("order-2", tenantId, userId, "Product B")
            )
        }

        suspend fun createOrder(product: String): Order = withTenantAndUser { tenantId, userId ->
            Order(
                id = "order-${System.currentTimeMillis()}",
                tenantId = tenantId,
                userId = userId,
                product = product
            )
        }
    }

    data class Order(
        val id: String,
        val tenantId: String,
        val userId: String,
        val product: String
    )

    @Test
    fun `Graph with contextAwareTool should propagate AgentContext automatically`() = runTest {
        // Given: Repository and context-aware tool
        val orderRepo = OrderRepository()

        val lookupTool = contextAwareTool("lookup_orders") {
            description = "Look up user orders"

            execute { params, context ->
                // ✨ Context should be automatically available!
                assertNotNull(context.tenantId, "tenantId should be propagated")
                assertNotNull(context.userId, "userId should be propagated")

                val orders = orderRepo.findOrders()
                "Found ${orders.size} orders for tenant ${context.tenantId}"
            }
        }

        // Graph with tool node
        val orderGraph = graph("order-lookup") {
            tool("lookup", lookupTool)
        }

        // When: Execute graph with AgentContext
        val runner = DefaultGraphRunner()

        val result = withAgentContext("tenantId" to "ACME", "userId" to "user-123") {
            runner.run(
                graph = orderGraph,
                input = mapOf("action" to "lookup")
            )
        }.getOrThrow()

        // Then: Graph should complete successfully
        assertEquals(RunStatus.SUCCESS, result.status)
        assertTrue(result.result.toString().contains("ACME"))
        assertEquals(1, result.nodeReports.size)
    }

    @Test
    fun `Graph with Agent should propagate AgentContext through Comm`() = runTest {
        // Given: Agent that uses context
        val orderRepo = OrderRepository()

        val orderAgent = object : Agent {
            override val id = "order-agent"
            override val name = "Order Agent"
            override val description = "Handles orders"
            override val capabilities = listOf("orders")

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                // Agent can access context through comm
                val tenantId = comm.context?.tenantId
                assertNotNull(tenantId, "AgentContext should be in Comm")

                val orders = orderRepo.findOrders()
                return SpiceResult.success(
                    comm.reply("Found ${orders.size} orders for $tenantId", id)
                )
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        // Graph with agent node
        val orderGraph = graph("order-processing") {
            agent("process", orderAgent)
        }

        // When: Execute graph with AgentContext
        val runner = DefaultGraphRunner()

        val result = withAgentContext("tenantId" to "ACME", "userId" to "user-456") {
            runner.run(
                graph = orderGraph,
                input = mapOf("request" to "list orders")
            )
        }.getOrThrow()

        // Then: Graph should complete with context
        assertEquals(RunStatus.SUCCESS, result.status)
        assertTrue(result.result.toString().contains("ACME"))
    }

    @Test
    fun `Graph with multiple nodes should maintain AgentContext throughout execution`() = runTest {
        // Given: Multiple tools and agents
        val orderRepo = OrderRepository()

        val lookupTool = contextAwareTool("lookup") {
            execute { params, context ->
                val orders = orderRepo.findOrders()
                "tenant=${context.tenantId},count=${orders.size}"
            }
        }

        val createTool = contextAwareTool("create") {
            param("product", "string", "Product name")
            execute { params, context ->
                val product = params["product"]?.toString() ?: throw IllegalArgumentException("Missing 'product'")
                val order = orderRepo.createOrder(product)
                "created=${order.id},tenant=${context.tenantId}"
            }
        }

        val summaryAgent = object : Agent {
            override val id = "summary-agent"
            override val name = "Summary Agent"
            override val description = "Summarizes results"
            override val capabilities = listOf("summary")

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                val tenantId = comm.context?.tenantId
                return SpiceResult.success(
                    comm.reply("Summary for tenant $tenantId: ${comm.content}", id)
                )
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        // Graph with multiple nodes
        val multiNodeGraph = graph("multi-step-order") {
            tool("lookup-step", lookupTool)
            tool("create-step", createTool)
            agent("summary-step", summaryAgent)
        }

        // When: Execute graph with AgentContext
        val runner = DefaultGraphRunner()

        val result = withAgentContext(
            "tenantId" to "ACME",
            "userId" to "user-789",
            "correlationId" to "corr-123"
        ) {
            runner.run(
                graph = multiNodeGraph,
                input = mapOf(
                    "action" to "process",
                    "product" to "Widget"
                )
            )
        }.getOrThrow()

        // Then: All nodes should have received context
        assertEquals(RunStatus.SUCCESS, result.status)
        assertEquals(3, result.nodeReports.size)

        // Verify context was propagated (final result contains tenant info)
        assertTrue(result.result.toString().contains("ACME"))
    }

    @Test
    fun `Graph without AgentContext should still work`() = runTest {
        // Given: Simple graph
        val simpleTool = contextAwareTool("simple") {
            execute { params, context ->
                // Context might be null - that's OK!
                "processed"
            }
        }

        val simpleGraph = graph("simple-workflow") {
            tool("process", simpleTool)
        }

        // When: Execute without AgentContext
        val runner = DefaultGraphRunner()

        val result = runner.run(
            graph = simpleGraph,
            input = mapOf("data" to "test")
        ).getOrThrow()

        // Then: Should still work even without context
        assertEquals(RunStatus.SUCCESS, result.status)
        assertNotNull(result.result)
    }

    @Test
    fun `GraphRegistry should support graph registration and retrieval`() = runTest {
        // Given: A graph
        val myGraph = graph("registered-graph") {
            tool("step1", contextAwareTool("tool1") {
                execute { params, context -> "result" }
            })
        }

        // When: Register graph
        GraphRegistry.register(myGraph)

        // Then: Should be retrievable
        val retrieved = GraphRegistry.get("registered-graph")
        assertNotNull(retrieved)
        assertEquals("registered-graph", retrieved.id)

        // And: Should be in getAll
        assertTrue(GraphRegistry.getAll().any { it.id == "registered-graph" })

        // Cleanup
        GraphRegistry.unregister("registered-graph")
    }

    @Test
    fun `Graph with nested service calls should maintain context`() = runTest {
        // Given: Service that calls another service
        class OrderService : BaseContextAwareService() {
            private val repo = OrderRepository()

            suspend fun processOrder(product: String): String = withTenantAndUser { tenantId, userId ->
                // Nested call should still have context
                val existingOrders = repo.findOrders()
                val newOrder = repo.createOrder(product)
                "Processed for tenant=$tenantId, user=$userId, " +
                    "existing=${existingOrders.size}, new=${newOrder.id}"
            }
        }

        val orderService = OrderService()

        val processTool = contextAwareTool("process_order") {
            param("product", "string", "Product name")
            execute { params, context ->
                val product = params["product"]?.toString() ?: throw IllegalArgumentException("Missing 'product'")
                orderService.processOrder(product)
            }
        }

        val nestedGraph = graph("nested-service-graph") {
            tool("process", processTool)
        }

        // When: Execute with context
        val runner = DefaultGraphRunner()

        val result = withAgentContext("tenantId" to "ACME", "userId" to "user-nested") {
            runner.run(
                graph = nestedGraph,
                input = mapOf("product" to "Super Widget")
            )
        }.getOrThrow()

        // Then: Should succeed with all nested calls having context
        assertEquals(RunStatus.SUCCESS, result.status)
        assertTrue(result.result.toString().contains("ACME"))
        assertTrue(result.result.toString().contains("user-nested"))
    }
}
