package io.github.noailabs.spice.context

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * 🎯 Context DSL Tests
 *
 * Comprehensive tests for withAgentContext DSL and context propagation.
 * Tests cover:
 * - Basic withAgentContext usage
 * - Context propagation across coroutines
 * - Nested context scoping
 * - Context enrichment
 * - Helper accessor functions
 * - Error handling
 *
 * @since 0.4.0
 */
class ContextDSLTest {

    /**
     * Test: withAgentContext should set context for scope
     */
    @Test
    fun `withAgentContext should set AgentContext for entire scope`() = runTest {
        // Given: Context values
        val tenantId = "CHIC"
        val userId = "user-123"

        // When: Execute in withAgentContext scope
        val result = withAgentContext(
            "tenantId" to tenantId,
            "userId" to userId
        ) {
            // Then: Context should be accessible
            val context = currentAgentContext()
            assertNotNull(context)
            assertEquals(tenantId, context.tenantId)
            assertEquals(userId, context.userId)

            "Context set successfully"
        }

        // And: Result should be returned
        assertEquals("Context set successfully", result)
    }

    /**
     * Test: withAgentContext should propagate to child coroutines
     */
    @Test
    fun `withAgentContext should propagate context to child coroutines`() = runTest {
        // Given: Parent context
        withAgentContext(
            "tenantId" to "PARENT",
            "userId" to "user-parent"
        ) {
            // When: Launch child coroutine
            launch {
                // Then: Context should be available in child
                val context = currentAgentContext()
                assertNotNull(context)
                assertEquals("PARENT", context.tenantId)
                assertEquals("user-parent", context.userId)
            }

            // When: Launch async child coroutine
            val deferred = async {
                // Then: Context should be available in async child
                val context = currentAgentContext()
                assertNotNull(context)
                assertEquals("PARENT", context.tenantId)
            }

            deferred.await()
        }
    }

    /**
     * Test: withAgentContext with existing AgentContext instance
     */
    @Test
    fun `withAgentContext should accept AgentContext instance`() = runTest {
        // Given: Existing AgentContext
        val existingContext = AgentContext.of(
            "tenantId" to "EXISTING",
            "userId" to "user-existing",
            "sessionId" to "sess-123"
        )

        // When: Use withAgentContext with instance
        withAgentContext(existingContext) {
            // Then: Context should be set
            val context = currentAgentContext()
            assertNotNull(context)
            assertEquals("EXISTING", context.tenantId)
            assertEquals("user-existing", context.userId)
            assertEquals("sess-123", context.sessionId)
        }
    }

    /**
     * Test: withEnrichedContext should merge with existing context
     */
    @Test
    fun `withEnrichedContext should merge with parent context`() = runTest {
        // Given: Parent context
        withAgentContext("tenantId" to "PARENT", "userId" to "user-parent") {
            // When: Enrich with additional values
            withEnrichedContext("sessionId" to "sess-123", "correlationId" to "corr-456") {
                // Then: Should have all values
                val context = currentAgentContext()
                assertNotNull(context)
                assertEquals("PARENT", context.tenantId)        // From parent
                assertEquals("user-parent", context.userId)      // From parent
                assertEquals("sess-123", context.sessionId)      // Enriched
                assertEquals("corr-456", context.correlationId)  // Enriched
            }
        }
    }

    /**
     * Test: withEnrichedContext should work without parent context
     */
    @Test
    fun `withEnrichedContext should create context if no parent exists`() = runTest {
        // When: Use withEnrichedContext without parent
        withEnrichedContext("tenantId" to "NEW", "userId" to "user-new") {
            // Then: Should create new context
            val context = currentAgentContext()
            assertNotNull(context)
            assertEquals("NEW", context.tenantId)
            assertEquals("user-new", context.userId)
        }
    }

    /**
     * Test: Nested withAgentContext scopes
     */
    @Test
    fun `nested withAgentContext should override parent values`() = runTest {
        // Given: Outer context
        withAgentContext("tenantId" to "OUTER", "userId" to "user-outer") {
            // When: Inner context with same keys
            withAgentContext("tenantId" to "INNER", "sessionId" to "sess-inner") {
                // Then: Inner values should override
                val context = currentAgentContext()
                assertNotNull(context)
                assertEquals("INNER", context.tenantId)  // Overridden
                assertEquals("sess-inner", context.sessionId)  // New value
                // Note: userId is NOT inherited in nested withAgentContext
            }

            // And: Outer context should be restored
            val outerContext = currentAgentContext()
            assertEquals("OUTER", outerContext?.tenantId)
            assertEquals("user-outer", outerContext?.userId)
        }
    }

    /**
     * Test: currentAgentContext should return null outside scope
     */
    @Test
    fun `currentAgentContext should return null when no context is set`() = runTest {
        // When: Access context outside withAgentContext
        val context = currentAgentContext()

        // Then: Should be null
        assertNull(context)
    }

    /**
     * Test: requireAgentContext should throw when no context
     */
    @Test
    fun `requireAgentContext should throw IllegalStateException when no context`() = runTest {
        // When/Then: Should throw
        assertFailsWith<IllegalStateException> {
            requireAgentContext()
        }
    }

    /**
     * Test: requireAgentContext should return context when available
     */
    @Test
    fun `requireAgentContext should return context when available`() = runTest {
        // Given: Context set
        withAgentContext("tenantId" to "REQUIRED") {
            // When: Require context
            val context = requireAgentContext()

            // Then: Should return context (not throw)
            assertNotNull(context)
            assertEquals("REQUIRED", context.tenantId)
        }
    }

    /**
     * Test: currentTenantId helper
     */
    @Test
    fun `currentTenantId should return tenant ID from context`() = runTest {
        // Given: Context with tenantId
        withAgentContext("tenantId" to "TENANT_123") {
            // When: Get current tenant ID
            val tenantId = currentTenantId()

            // Then: Should return correct value
            assertEquals("TENANT_123", tenantId)
        }
    }

    /**
     * Test: currentUserId helper
     */
    @Test
    fun `currentUserId should return user ID from context`() = runTest {
        // Given: Context with userId
        withAgentContext("userId" to "user-456") {
            // When: Get current user ID
            val userId = currentUserId()

            // Then: Should return correct value
            assertEquals("user-456", userId)
        }
    }

    /**
     * Test: currentSessionId helper
     */
    @Test
    fun `currentSessionId should return session ID from context`() = runTest {
        // Given: Context with sessionId
        withAgentContext("sessionId" to "sess-789") {
            // When: Get current session ID
            val sessionId = currentSessionId()

            // Then: Should return correct value
            assertEquals("sess-789", sessionId)
        }
    }

    /**
     * Test: currentCorrelationId helper
     */
    @Test
    fun `currentCorrelationId should return correlation ID from context`() = runTest {
        // Given: Context with correlationId
        withAgentContext("correlationId" to "corr-abc") {
            // When: Get current correlation ID
            val correlationId = currentCorrelationId()

            // Then: Should return correct value
            assertEquals("corr-abc", correlationId)
        }
    }

    /**
     * Test: Helper functions should return null when value not present
     */
    @Test
    fun `helper functions should return null when value not in context`() = runTest {
        // Given: Context without specific values
        withAgentContext("tenantId" to "ONLY_TENANT") {
            // When/Then: Missing values should return null
            assertNull(currentUserId())
            assertNull(currentSessionId())
            assertNull(currentCorrelationId())

            // But present value should work
            assertEquals("ONLY_TENANT", currentTenantId())
        }
    }

    /**
     * Test: Helper functions should return null when no context
     */
    @Test
    fun `helper functions should return null when no context is set`() = runTest {
        // When/Then: Should all return null
        assertNull(currentTenantId())
        assertNull(currentUserId())
        assertNull(currentSessionId())
        assertNull(currentCorrelationId())
    }

    /**
     * Test: withContextIfAvailable should execute when context present
     */
    @Test
    fun `withContextIfAvailable should execute block when context is present`() = runTest {
        // Given: Context set
        withAgentContext("tenantId" to "TEST") {
            // When: Use withContextIfAvailable
            val result = withContextIfAvailable { context ->
                "Tenant: ${context.tenantId}"
            }

            // Then: Should execute and return result
            assertNotNull(result)
            assertEquals("Tenant: TEST", result)
        }
    }

    /**
     * Test: withContextIfAvailable should return null when no context
     */
    @Test
    fun `withContextIfAvailable should return null when context is not present`() = runTest {
        // When: Use withContextIfAvailable without context
        val result = withContextIfAvailable { context ->
            "Should not execute"
        }

        // Then: Should return null
        assertNull(result)
    }

    /**
     * Test: withContextKey should execute when key present
     */
    @Test
    fun `withContextKey should execute block when key is present in context`() = runTest {
        // Given: Context with specific key
        withAgentContext("customKey" to "customValue") {
            // When: Access specific key
            val result = withContextKey("customKey") { value ->
                "Value: $value"
            }

            // Then: Should execute and return result
            assertNotNull(result)
            assertEquals("Value: customValue", result)
        }
    }

    /**
     * Test: withContextKey should return null when key not present
     */
    @Test
    fun `withContextKey should return null when key is not in context`() = runTest {
        // Given: Context without specific key
        withAgentContext("otherKey" to "otherValue") {
            // When: Access missing key
            val result = withContextKey("customKey") { value ->
                "Should not execute"
            }

            // Then: Should return null
            assertNull(result)
        }
    }

    /**
     * Test: Complex nested enrichment scenario
     */
    @Test
    fun `complex nested enrichment should work correctly`() = runTest {
        // Given: Base context
        withAgentContext("tenantId" to "BASE") {
            assertEquals("BASE", currentTenantId())
            assertNull(currentUserId())
            assertNull(currentSessionId())

            // When: First enrichment
            withEnrichedContext("userId" to "user-1") {
                assertEquals("BASE", currentTenantId())    // Inherited
                assertEquals("user-1", currentUserId())    // Added
                assertNull(currentSessionId())             // Not yet

                // When: Second enrichment
                withEnrichedContext("sessionId" to "sess-1") {
                    assertEquals("BASE", currentTenantId())     // Still inherited
                    assertEquals("user-1", currentUserId())     // Still inherited
                    assertEquals("sess-1", currentSessionId())  // Added

                    // When: Override tenant
                    withEnrichedContext("tenantId" to "OVERRIDE") {
                        assertEquals("OVERRIDE", currentTenantId()) // Overridden
                        assertEquals("user-1", currentUserId())     // Still inherited
                        assertEquals("sess-1", currentSessionId())  // Still inherited
                    }

                    // Then: Should restore after override scope
                    assertEquals("BASE", currentTenantId())
                }
            }
        }
    }

    /**
     * Test: Context propagation across multiple async operations
     */
    @Test
    fun `context should propagate across multiple async operations`() = runTest {
        // Given: Parent context
        withAgentContext(
            "tenantId" to "ASYNC_TEST",
            "userId" to "user-async"
        ) {
            // When: Multiple parallel async operations
            val results = (1..5).map { i ->
                async {
                    kotlinx.coroutines.delay(10)
                    val context = currentAgentContext()
                    "Task $i: ${context?.tenantId}"
                }
            }.map { it.await() }

            // Then: All should have context
            results.forEach { result ->
                assertTrue(result.contains("ASYNC_TEST"))
            }
        }
    }

    /**
     * Test: Context with custom values
     */
    @Test
    fun `withAgentContext should support custom key-value pairs`() = runTest {
        // Given: Custom context values
        withAgentContext(
            "customString" to "value",
            "customNumber" to 42,
            "customBoolean" to true,
            "customList" to listOf("a", "b", "c"),
            "customMap" to mapOf("key" to "value")
        ) {
            // When: Access custom values
            val context = requireAgentContext()

            // Then: Should retrieve correctly
            assertEquals("value", context.get("customString"))
            assertEquals(42, context.get("customNumber"))
            assertEquals(true, context.get("customBoolean"))
            assertEquals(listOf("a", "b", "c"), context.get("customList"))
            assertEquals(mapOf("key" to "value"), context.get("customMap"))
        }
    }

    /**
     * Test: Empty context
     */
    @Test
    fun `withAgentContext should work with empty context`() = runTest {
        // When: Create empty context
        withAgentContext() {
            // Then: Context should exist but be empty
            val context = currentAgentContext()
            assertNotNull(context)
            assertNull(context.tenantId)
            assertNull(context.userId)
        }
    }

    /**
     * Test: Context isolation between parallel scopes
     */
    @Test
    fun `parallel withAgentContext scopes should be isolated`() = runTest {
        // When: Run multiple parallel scopes
        val job1 = async {
            withAgentContext("tenantId" to "TENANT_A") {
                kotlinx.coroutines.delay(50)
                currentTenantId()
            }
        }

        val job2 = async {
            withAgentContext("tenantId" to "TENANT_B") {
                kotlinx.coroutines.delay(50)
                currentTenantId()
            }
        }

        val job3 = async {
            withAgentContext("tenantId" to "TENANT_C") {
                kotlinx.coroutines.delay(50)
                currentTenantId()
            }
        }

        // Then: Each should maintain its own context
        assertEquals("TENANT_A", job1.await())
        assertEquals("TENANT_B", job2.await())
        assertEquals("TENANT_C", job3.await())
    }

    /**
     * Test: Exception handling preserves context
     */
    @Test
    fun `context should be preserved when exception is thrown`() = runTest {
        // Given: Context set
        withAgentContext("tenantId" to "EXCEPTION_TEST") {
            try {
                // When: Exception thrown in child scope
                withEnrichedContext("sessionId" to "sess-fail") {
                    throw IllegalStateException("Test exception")
                }
            } catch (e: IllegalStateException) {
                // Then: Parent context should still be available
                assertEquals("EXCEPTION_TEST", currentTenantId())
                assertNull(currentSessionId()) // Child enrichment was lost
            }
        }
    }

    /**
     * Test: Return value propagation
     */
    @Test
    fun `withAgentContext should properly return block result`() = runTest {
        // When: Return complex object
        data class Result(val tenant: String, val count: Int)

        val result = withAgentContext("tenantId" to "RETURN_TEST") {
            Result(
                tenant = currentTenantId()!!,
                count = 42
            )
        }

        // Then: Should return correctly
        assertEquals("RETURN_TEST", result.tenant)
        assertEquals(42, result.count)
    }
}
