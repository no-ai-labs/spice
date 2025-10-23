package io.github.noailabs.spice.context

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.CommRole
import io.github.noailabs.spice.CommType
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * 🎯 Comm Context Integration Tests
 *
 * @since 0.4.0
 */
class CommContextIntegrationTest {

    @Test
    fun `Comm should carry AgentContext`() = runTest {
        // Given
        val context = AgentContext.of(
            "tenantId" to "CHIC",
            "userId" to "user-123"
        )

        // When
        val comm = Comm(
            content = "Test message",
            from = "user",
            context = context
        )

        // Then
        assertNotNull(comm.context)
        assertEquals("CHIC", comm.context?.tenantId)
        assertEquals("user-123", comm.context?.userId)
    }

    @Test
    fun `Comm withContext should set context`() = runTest {
        // Given
        val comm = Comm(content = "Hello", from = "user")
        val context = AgentContext.of("tenantId" to "ACME")

        // When
        val withContext = comm.withContext(context)

        // Then
        assertEquals("ACME", withContext.context?.tenantId)
    }

    @Test
    fun `Comm withContextValues should merge values`() = runTest {
        // Given
        val existingContext = AgentContext.of("tenantId" to "EXISTING")
        val comm = Comm(
            content = "Test",
            from = "user",
            context = existingContext
        )

        // When
        val enriched = comm.withContextValues(
            "userId" to "user-new",
            "sessionId" to "sess-new"
        )

        // Then
        assertEquals("EXISTING", enriched.context?.tenantId)
        assertEquals("user-new", enriched.context?.userId)
        assertEquals("sess-new", enriched.context?.sessionId)
    }

    @Test
    fun `Comm withContextValues should create context if none exists`() = runTest {
        // Given
        val comm = Comm(content = "Test", from = "user")

        // When
        val withContext = comm.withContextValues(
            "tenantId" to "NEW",
            "userId" to "user-new"
        )

        // Then
        assertNotNull(withContext.context)
        assertEquals("NEW", withContext.context?.tenantId)
        assertEquals("user-new", withContext.context?.userId)
    }

    @Test
    fun `Comm getContextValue should check context first`() = runTest {
        // Given
        val context = AgentContext.of("key1" to "context-value")
        val comm = Comm(
            content = "Test",
            from = "user",
            data = mapOf("key1" to "data-value"),
            context = context
        )

        // When
        val value = comm.getContextValue("key1")

        // Then: Context takes precedence
        assertEquals("context-value", value)
    }

    @Test
    fun `Comm getContextValue should fallback to data`() = runTest {
        // Given
        val comm = Comm(
            content = "Test",
            from = "user",
            data = mapOf("key1" to "data-value")
        )

        // When
        val value = comm.getContextValue("key1")

        // Then: Should use data when no context
        assertEquals("data-value", value)
    }

    @Test
    fun `Comm getContextValue should return null when key not found`() = runTest {
        // Given
        val comm = Comm(content = "Test", from = "user")

        // When
        val value = comm.getContextValue("nonexistent")

        // Then
        assertNull(value)
    }

    @Test
    fun `Comm reply should preserve parent context`() = runTest {
        // Given
        val context = AgentContext.of(
            "tenantId" to "PARENT",
            "userId" to "user-parent"
        )
        val originalComm = Comm(
            content = "Original",
            from = "user",
            context = context
        )

        // When
        val reply = originalComm.reply(
            content = "Reply",
            from = "agent"
        )

        // Then: Context should be copied to reply
        assertNotNull(reply.context)
        assertEquals("PARENT", reply.context?.tenantId)
        assertEquals("user-parent", reply.context?.userId)
    }

    @Test
    fun `Comm forward should preserve context`() = runTest {
        // Given
        val context = AgentContext.of("tenantId" to "FORWARD")
        val originalComm = Comm(
            content = "Original",
            from = "agent1",
            context = context
        )

        // When
        val forwarded = originalComm.forward("agent2")

        // Then: Context should be preserved
        assertEquals("FORWARD", forwarded.context?.tenantId)
        assertEquals("agent2", forwarded.to)
    }

    @Test
    fun `Comm with multiple context operations`() = runTest {
        // Given
        val comm = Comm(content = "Start", from = "user")

        // When: Chain context operations
        val final = comm
            .withContextValues("tenantId" to "STEP1")
            .withContextValues("userId" to "STEP2")
            .withContextValues("sessionId" to "STEP3")

        // Then: All values should accumulate
        assertEquals("STEP1", final.context?.tenantId)
        assertEquals("STEP2", final.context?.userId)
        assertEquals("STEP3", final.context?.sessionId)
    }

    @Test
    fun `Comm context should be transient for serialization`() = runTest {
        // Given: Comm with context
        val context = AgentContext.of("tenantId" to "TEST")
        val comm = Comm(
            content = "Test",
            from = "user",
            context = context
        )

        // When: Serialize (simulate)
        // The @Transient annotation should prevent context from being serialized
        // This test just verifies the field is marked correctly
        val field = Comm::class.java.getDeclaredField("context")
        val transientAnnotation = field.annotations.any {
            it.annotationClass.simpleName == "Transient"
        }

        // Then: Should be marked transient
        assertTrue(transientAnnotation, "context field should be @Transient")
    }

    @Test
    fun `Comm with error should preserve context`() = runTest {
        // Given
        val context = AgentContext.of("tenantId" to "ERROR_TEST")
        val originalComm = Comm(
            content = "Request",
            from = "user",
            context = context
        )

        // When
        val errorComm = originalComm.error("Something went wrong", from = "system")

        // Then: Error response should have context
        assertEquals("ERROR_TEST", errorComm.context?.tenantId)
        assertEquals(CommType.ERROR, errorComm.type)
        assertEquals(CommRole.SYSTEM, errorComm.role)
    }

    @Test
    fun `Comm toolCall should preserve context`() = runTest {
        // Given
        val context = AgentContext.of(
            "tenantId" to "TOOL_TEST",
            "userId" to "user-tool"
        )
        val comm = Comm(
            content = "Execute tool",
            from = "agent",
            context = context
        )

        // When
        val toolCall = comm.toolCall(
            toolName = "my_tool",
            params = mapOf("param1" to "value1"),
            from = "agent"
        )

        // Then: Tool call should have context
        assertEquals("TOOL_TEST", toolCall.context?.tenantId)
        assertEquals(CommType.TOOL_CALL, toolCall.type)
    }

    @Test
    fun `Comm toolResult should preserve context`() = runTest {
        // Given
        val context = AgentContext.of("correlationId" to "tool-corr-123")
        val comm = Comm(
            content = "Tool call",
            from = "agent",
            context = context
        )

        // When
        val result = comm.toolResult("Tool executed", from = "system")

        // Then: Result should have context
        assertEquals("tool-corr-123", result.context?.correlationId)
        assertEquals(CommType.TOOL_RESULT, result.type)
    }
}
