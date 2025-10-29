package io.github.noailabs.spice

import io.github.noailabs.spice.dsl.withAgentContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExecutionContextAccessorTest {

    @Test
    fun `currentExecutionContext should return context from coroutine`() = runTest {
        // Given: ExecutionContext in coroutine
        val context = ExecutionContext.of(mapOf(
            "tenantId" to "tenant-123",
            "userId" to "user-456"
        ))

        // When: Access from coroutine
        val retrieved = withContext(context) {
            currentExecutionContext()
        }

        // Then: Should return context
        assertEquals("tenant-123", retrieved?.tenantId)
        assertEquals("user-456", retrieved?.userId)
    }

    @Test
    fun `currentExecutionContext should return null when not in scope`() = runTest {
        // When: No ExecutionContext
        val context = currentExecutionContext()

        // Then: Should be null
        assertNull(context)
    }

    @Test
    fun `requireExecutionContext should return context when present`() = runTest {
        // Given: ExecutionContext
        val context = ExecutionContext.of(mapOf("tenantId" to "test"))

        // When: Require context
        val retrieved = withContext(context) {
            requireExecutionContext()
        }

        // Then: Should return context
        assertEquals("test", retrieved.tenantId)
    }

    @Test
    fun `requireExecutionContext should throw when missing`() = runTest {
        // When/Then: Should throw
        try {
            requireExecutionContext()
            error("Should have thrown")
        } catch (e: IllegalStateException) {
            assertEquals("No ExecutionContext in coroutine scope", e.message)
        }
    }

    @Test
    fun `getCurrentTenantId should return tenant ID`() = runTest {
        // Given: Context with tenant
        val context = ExecutionContext.of(mapOf("tenantId" to "acme-corp"))

        // When: Get tenant ID
        val tenantId = withContext(context) {
            getCurrentTenantId()
        }

        // Then: Should return ID
        assertEquals("acme-corp", tenantId)
    }

    @Test
    fun `getCurrentTenantId should return null when not in scope`() = runTest {
        // When: No context
        val tenantId = getCurrentTenantId()

        // Then: Should be null
        assertNull(tenantId)
    }

    @Test
    fun `getCurrentUserId should return user ID`() = runTest {
        // Given: Context with user
        val context = ExecutionContext.of(mapOf("userId" to "user-789"))

        // When: Get user ID
        val userId = withContext(context) {
            getCurrentUserId()
        }

        // Then: Should return ID
        assertEquals("user-789", userId)
    }

    @Test
    fun `getCurrentCorrelationId should return correlation ID`() = runTest {
        // Given: Context with correlation
        val context = ExecutionContext.of(mapOf("correlationId" to "corr-abc"))

        // When: Get correlation ID
        val corrId = withContext(context) {
            getCurrentCorrelationId()
        }

        // Then: Should return ID
        assertEquals("corr-abc", corrId)
    }

    @Test
    fun `accessor functions work with withAgentContext DSL`() = runTest {
        // Given: Using existing DSL
        withAgentContext(
            "tenantId" to "dsl-tenant",
            "userId" to "dsl-user"
        ) {
            // When: Access via new functions
            val context = currentExecutionContext()
            val tenantId = getCurrentTenantId()
            val userId = getCurrentUserId()

            // Then: Should work
            assertEquals("dsl-tenant", context?.tenantId)
            assertEquals("dsl-tenant", tenantId)
            assertEquals("dsl-user", userId)
        }
    }

    @Test
    fun `accessor functions work in nested service calls`() = runTest {
        // Simulate service layer
        suspend fun serviceLayer() {
            val tenantId = getCurrentTenantId()
            assertEquals("nested-tenant", tenantId)
        }

        suspend fun controllerLayer() {
            serviceLayer()  // Service can access context!
        }

        // When: Call from context scope
        withAgentContext("tenantId" to "nested-tenant") {
            controllerLayer()
        }
    }

    @Test
    fun `accessor functions handle missing values gracefully`() = runTest {
        // Given: Context without tenant
        val context = ExecutionContext.of(mapOf("userId" to "user-only"))

        withContext(context) {
            // When: Access missing keys
            val tenantId = getCurrentTenantId()
            val userId = getCurrentUserId()
            val correlationId = getCurrentCorrelationId()

            // Then: Returns null for missing, value for present
            assertNull(tenantId)
            assertEquals("user-only", userId)
            assertNull(correlationId)
        }
    }
}

