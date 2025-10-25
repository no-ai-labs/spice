package io.github.noailabs.spice.context

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * 🎯 ContextAware Tool DSL Tests
 *
 * Comprehensive tests for context-aware tool creation and execution.
 * Tests cover:
 * - Basic contextAwareTool creation
 * - Automatic context injection
 * - Parameter validation
 * - Error handling
 * - Integration with CoreAgentBuilder
 * - SimpleContextTool variant
 *
 * @since 0.4.0
 */
class ContextAwareToolTest {

    /**
     * Test: contextAwareTool should create tool with context injection
     */
    @Test
    fun `contextAwareTool should inject AgentContext automatically`() = runTest {
        // Given: Context-aware tool
        val tool = contextAwareTool("tenant_lookup") {
            description = "Look up tenant info"
            param("action", "string", "Action to perform")

            execute { params, context ->
                // Context automatically injected!
                val tenantId = context.tenantId ?: "default"
                val userId = context.userId ?: "unknown"
                val action = params["action"] as String

                "Tenant: $tenantId, User: $userId, Action: $action"
            }
        }

        // When: Execute with AgentContext in coroutineContext
        val result = withAgentContext(
            "tenantId" to "CHIC",
            "userId" to "user-123"
        ) {
            tool.execute(mapOf("action" to "query"))
        }

        // Then: Context values should be injected
        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        assertTrue(toolResult.success)
        assertEquals("Tenant: CHIC, User: user-123, Action: query", toolResult.result)

        // And: Metadata should contain context info
        assertEquals("CHIC", toolResult.metadata["tenantId"])
        assertEquals("user-123", toolResult.metadata["userId"])
        assertEquals("true", toolResult.metadata["contextAware"])
    }

    /**
     * Test: contextAwareTool should handle missing context gracefully
     */
    @Test
    fun `contextAwareTool should return error when AgentContext is missing`() = runTest {
        // Given: Context-aware tool
        val tool = contextAwareTool("test_tool") {
            description = "Test tool"

            execute { params, context ->
                "Should not reach here"
            }
        }

        // When: Execute WITHOUT AgentContext
        val result = tool.execute(emptyMap())

        // Then: Should return error
        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        assertFalse(toolResult.success)
        assertTrue(toolResult.error.contains("No AgentContext available"))
    }

    /**
     * Test: contextAwareTool should validate parameters
     */
    @Test
    fun `contextAwareTool should have correct schema`() = runTest {
        // Given: Context-aware tool with parameters
        val tool = contextAwareTool("user_action") {
            description = "Perform user action"
            param("action", "string", "Action type", required = true)
            param("target", "string", "Target resource", required = false)

            execute { params, context ->
                "Action executed"
            }
        }

        // Then: Schema should be correct
        assertEquals("user_action", tool.name)
        assertEquals("Perform user action", tool.description)
        assertEquals(2, tool.schema.parameters.size)

        val actionParam = tool.schema.parameters["action"]!!
        assertEquals("string", actionParam.type)
        assertEquals("Action type", actionParam.description)
        assertTrue(actionParam.required)

        val targetParam = tool.schema.parameters["target"]!!
        assertEquals("string", targetParam.type)
        assertEquals("Target resource", targetParam.description)
        assertFalse(targetParam.required)
    }

    /**
     * Test: contextAwareTool should handle execution errors
     */
    @Test
    fun `contextAwareTool should catch and return execution errors`() = runTest {
        // Given: Context-aware tool that throws exception
        val tool = contextAwareTool("failing_tool") {
            description = "Tool that fails"

            execute { params, context ->
                throw IllegalArgumentException("Intentional failure")
            }
        }

        // When: Execute with context
        val result = withAgentContext("tenantId" to "test") {
            tool.execute(emptyMap())
        }

        // Then: Should return error result (not throw)
        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        assertFalse(toolResult.success)
        assertTrue(toolResult.error.contains("Tool execution failed"))
        assertTrue(toolResult.error.contains("Intentional failure"))
    }

    /**
     * Test: contextAwareTool should access all context values
     */
    @Test
    fun `contextAwareTool should access all AgentContext values`() = runTest {
        // Given: Context-aware tool accessing multiple context values
        val tool = contextAwareTool("context_reader") {
            description = "Read all context values"

            execute { params, context ->
                buildString {
                    append("tenantId: ${context.tenantId}, ")
                    append("userId: ${context.userId}, ")
                    append("sessionId: ${context.sessionId}, ")
                    append("correlationId: ${context.correlationId}, ")
                    append("custom: ${context.get("customKey")}")
                }
            }
        }

        // When: Execute with rich context
        val result = withAgentContext(
            "tenantId" to "CHIC",
            "userId" to "user-123",
            "sessionId" to "sess-456",
            "correlationId" to "corr-789",
            "customKey" to "customValue"
        ) {
            tool.execute(emptyMap())
        }

        // Then: All context values should be accessible
        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        val output = toolResult.result!!

        assertTrue(output.contains("tenantId: CHIC"))
        assertTrue(output.contains("userId: user-123"))
        assertTrue(output.contains("sessionId: sess-456"))
        assertTrue(output.contains("correlationId: corr-789"))
        assertTrue(output.contains("custom: customValue"))
    }

    /**
     * Test: simpleContextTool should provide minimal DSL
     */
    @Test
    fun `simpleContextTool should create tool with minimal DSL`() = runTest {
        // Given: Simple context tool
        val tool = simpleContextTool(
            name = "get_tenant",
            description = "Get current tenant"
        ) { params, context ->
            "Current tenant: ${context.tenantId}"
        }

        // When: Execute with context
        val result = withAgentContext("tenantId" to "ACME") {
            tool.execute(emptyMap())
        }

        // Then: Should work correctly
        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        assertEquals("Current tenant: ACME", toolResult.result)
        assertEquals("get_tenant", tool.name)
        assertEquals("Get current tenant", tool.description)
    }

    /**
     * Test: CoreAgentBuilder.contextAwareTool extension
     */
    @Test
    fun `CoreAgentBuilder contextAwareTool extension should work`() = runTest {
        // Given: Agent with context-aware tool
        val agent = buildAgent {
            id = "test-agent"
            name = "Test Agent"

            contextAwareTool("tenant_action") {
                description = "Perform tenant-scoped action"
                param("action", "string", "Action to perform")

                execute { params, context ->
                    val tenantId = context.tenantId ?: throw IllegalStateException("No tenant")
                    val action = params["action"] as String
                    "Tenant $tenantId: $action"
                }
            }
        }

        // Then: Agent should have the tool
        val tools = agent.getTools()
        assertEquals(1, tools.size)
        assertEquals("tenant_action", tools[0].name)
        assertEquals("Perform tenant-scoped action", tools[0].description)
    }

    /**
     * Test: CoreAgentBuilder.simpleContextTool extension
     */
    @Test
    fun `CoreAgentBuilder simpleContextTool extension should work`() = runTest {
        // Given: Agent with simple context tool
        val agent = buildAgent {
            id = "test-agent"

            simpleContextTool("get_user", "Get current user") { params, context ->
                "User: ${context.userId}"
            }
        }

        // Then: Agent should have the tool
        val tools = agent.getTools()
        assertEquals(1, tools.size)
        assertEquals("get_user", tools[0].name)
    }

    /**
     * Test: contextAwareTool should work with complex parameters
     */
    @Test
    fun `contextAwareTool should handle complex parameter types`() = runTest {
        // Given: Tool with complex parameters
        val tool = contextAwareTool("process_data") {
            description = "Process complex data"
            param("items", "array", "Items to process")
            param("config", "object", "Configuration")

            execute { params, context ->
                val items = params["items"] as List<*>
                val config = params["config"] as Map<*, *>
                val tenantId = context.tenantId

                "Tenant $tenantId processed ${items.size} items with config: $config"
            }
        }

        // When: Execute with complex parameters
        val result = withAgentContext("tenantId" to "CHIC") {
            tool.execute(
                mapOf(
                    "items" to listOf("item1", "item2", "item3"),
                    "config" to mapOf("mode" to "fast", "validate" to true)
                )
            )
        }

        // Then: Should process correctly
        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        val output = toolResult.result!!
        assertTrue(output.contains("Tenant CHIC"))
        assertTrue(output.contains("processed 3 items"))
    }

    /**
     * Test: contextAwareTool in nested coroutines
     */
    @Test
    fun `contextAwareTool should work in nested coroutines`() = runTest {
        // Given: Context-aware tool
        val tool = contextAwareTool("nested_tool") {
            description = "Test nested execution"

            execute { params, context ->
                "Tenant: ${context.tenantId}"
            }
        }

        // When: Execute in nested withAgentContext
        val result = withAgentContext("tenantId" to "OUTER") {
            withEnrichedContext("sessionId" to "sess-123") {
                tool.execute(emptyMap())
            }
        }

        // Then: Should access outer context
        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        assertTrue(toolResult.result!!.contains("Tenant: OUTER"))
    }

    /**
     * Test: Multiple context-aware tools in same agent
     */
    @Test
    fun `agent should support multiple context-aware tools`() = runTest {
        // Given: Agent with multiple context-aware tools
        val agent = buildAgent {
            id = "multi-tool-agent"

            contextAwareTool("tool1") {
                description = "First tool"
                execute { params, context ->
                    "Tool1: ${context.tenantId}"
                }
            }

            contextAwareTool("tool2") {
                description = "Second tool"
                execute { params, context ->
                    "Tool2: ${context.userId}"
                }
            }

            simpleContextTool("tool3", "Third tool") { params, context ->
                "Tool3: ${context.sessionId}"
            }
        }

        // Then: Agent should have all tools
        val tools = agent.getTools()
        assertEquals(3, tools.size)
        assertEquals(setOf("tool1", "tool2", "tool3"), tools.map { it.name }.toSet())
    }

    /**
     * Test: contextAwareTool should handle null context values
     */
    @Test
    fun `contextAwareTool should handle null context values gracefully`() = runTest {
        // Given: Tool that accesses potentially null context values
        val tool = contextAwareTool("nullable_context") {
            description = "Handle nullable values"

            execute { params, context ->
                val tenantId = context.tenantId ?: "DEFAULT_TENANT"
                val userId = context.userId ?: "DEFAULT_USER"
                "Tenant: $tenantId, User: $userId"
            }
        }

        // When: Execute with partial context (only tenantId)
        val result = withAgentContext("tenantId" to "CHIC") {
            tool.execute(emptyMap())
        }

        // Then: Should use defaults for missing values
        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        assertEquals("Tenant: CHIC, User: DEFAULT_USER", toolResult.result)
    }

    /**
     * Test: ContextAwareToolBuilder builder pattern
     */
    @Test
    fun `ContextAwareToolBuilder should support builder pattern`() = runTest {
        // Given: Manual builder usage
        val builder = ContextAwareToolBuilder("manual_tool")
        builder.description = "Manually built tool"
        builder.param("param1", "string", "First parameter")
        builder.param("param2", "number", "Second parameter", required = false)
        builder.execute { params, context ->
            "Built: ${context.tenantId}"
        }

        // When: Build and execute
        val tool = builder.build()
        val result = withAgentContext("tenantId" to "TEST") {
            tool.execute(mapOf("param1" to "value1"))
        }

        // Then: Should work correctly
        assertEquals("manual_tool", tool.name)
        assertEquals("Manually built tool", tool.description)
        assertTrue(result.isSuccess)
    }

    /**
     * Test: contextAwareTool should work with suspend functions
     */
    @Test
    fun `contextAwareTool should support suspend functions in execute block`() = runTest {
        // Given: Tool with async operations
        val tool = contextAwareTool("async_tool") {
            description = "Async operations"

            execute { params, context ->
                // Simulate async operation
                kotlinx.coroutines.delay(10)
                val tenantId = context.tenantId
                kotlinx.coroutines.delay(10)
                "Async result for $tenantId"
            }
        }

        // When: Execute
        val result = withAgentContext("tenantId" to "ASYNC") {
            tool.execute(emptyMap())
        }

        // Then: Should complete successfully
        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        assertEquals("Async result for ASYNC", toolResult.result)
    }

    /**
     * Test: parameters {} DSL block should work
     */
    @Test
    fun `parameters DSL block should create tool with correct schema`() = runTest {
        // Given: Tool with parameters DSL
        val tool = contextAwareTool("test-params-dsl") {
            description = "Test parameters DSL"

            parameters {
                string("name", "User name", required = true)
                number("age", "User age", required = false)
                boolean("active", "Is active", required = false)
                integer("count", "Count", required = false)
                array("tags", "Tags", required = false)
            }

            execute { params, _ ->
                "OK: ${params.keys.joinToString()}"
            }
        }

        // Then: Schema should have all parameters
        assertEquals(5, tool.schema.parameters.size)
        assertTrue(tool.schema.parameters.containsKey("name"))
        assertTrue(tool.schema.parameters.containsKey("age"))
        assertTrue(tool.schema.parameters.containsKey("active"))
        assertTrue(tool.schema.parameters.containsKey("count"))
        assertTrue(tool.schema.parameters.containsKey("tags"))

        // And: Required flags should be correct
        assertEquals(true, tool.schema.parameters["name"]?.required)
        assertEquals(false, tool.schema.parameters["age"]?.required)

        // And: Types should be correct
        assertEquals("string", tool.schema.parameters["name"]?.type)
        assertEquals("number", tool.schema.parameters["age"]?.type)
        assertEquals("boolean", tool.schema.parameters["active"]?.type)
        assertEquals("integer", tool.schema.parameters["count"]?.type)
        assertEquals("array", tool.schema.parameters["tags"]?.type)

        // When: Execute tool
        val result = withAgentContext("tenantId" to "TEST") {
            tool.execute(mapOf("name" to "John"))
        }

        // Then: Should execute successfully
        assertTrue(result.isSuccess)
    }

    /**
     * Test: parameters {} DSL mixed with param() should work
     */
    @Test
    fun `parameters DSL can be mixed with individual param calls`() = runTest {
        // Given: Tool using both DSL and individual param
        val tool = contextAwareTool("mixed-params") {
            parameters {
                string("email", "Email", required = true)
                number("age", "Age", required = false)
            }

            // Additional param using individual call
            param("country", "string", "Country", required = false)

            execute { params, _ -> "OK" }
        }

        // Then: Should have all 3 parameters
        assertEquals(3, tool.schema.parameters.size)
        assertTrue(tool.schema.parameters.containsKey("email"))
        assertTrue(tool.schema.parameters.containsKey("age"))
        assertTrue(tool.schema.parameters.containsKey("country"))
    }
}
