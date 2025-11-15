package io.github.noailabs.spice.agents

import io.github.noailabs.spice.SpiceMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for MockAgent
 *
 * Verifies:
 * - Response cycling behavior
 * - Call count tracking
 * - Metadata injection
 * - Reset functionality
 */
class MockAgentTest {

    @Test
    fun `test mock agent returns first response`() = runTest {
        val agent = MockAgent(
            id = "test-mock",
            responses = listOf("Response 1", "Response 2", "Response 3")
        )

        val message = SpiceMessage.create("Test", "user")
        val result = agent.processMessage(message)

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("Response 1", response.content)
        assertEquals("test-mock", response.from)
    }

    @Test
    fun `test mock agent cycles through responses`() = runTest {
        val agent = MockAgent(
            id = "test-mock",
            responses = listOf("First", "Second", "Third")
        )

        val message = SpiceMessage.create("Test", "user")

        // First call
        val r1 = agent.processMessage(message).getOrThrow()
        assertEquals("First", r1.content)

        // Second call
        val r2 = agent.processMessage(message).getOrThrow()
        assertEquals("Second", r2.content)

        // Third call
        val r3 = agent.processMessage(message).getOrThrow()
        assertEquals("Third", r3.content)

        // Fourth call - should cycle back to first
        val r4 = agent.processMessage(message).getOrThrow()
        assertEquals("First", r4.content)
    }

    @Test
    fun `test mock agent tracks call count`() = runTest {
        val agent = MockAgent(
            responses = listOf("Response")
        )

        assertEquals(0, agent.getCallCount())

        val message = SpiceMessage.create("Test", "user")
        agent.processMessage(message)
        assertEquals(1, agent.getCallCount())

        agent.processMessage(message)
        assertEquals(2, agent.getCallCount())

        agent.processMessage(message)
        assertEquals(3, agent.getCallCount())
    }

    @Test
    fun `test mock agent injects metadata`() = runTest {
        val agent = MockAgent(
            id = "test-mock",
            responses = listOf("Response")
        )

        val message = SpiceMessage.create("Test", "user")
        val result = agent.processMessage(message).getOrThrow()

        // Check injected metadata
        assertEquals(true, result.data["mock"])
        assertEquals(1, result.data["call_count"])

        // Second call
        val result2 = agent.processMessage(message).getOrThrow()
        assertEquals(2, result2.data["call_count"])
    }

    @Test
    fun `test mock agent reset functionality`() = runTest {
        val agent = MockAgent(
            responses = listOf("Response")
        )

        val message = SpiceMessage.create("Test", "user")
        agent.processMessage(message)
        agent.processMessage(message)

        assertEquals(2, agent.getCallCount())

        // Reset
        agent.reset()
        assertEquals(0, agent.getCallCount())

        // After reset, should start from first response again
        val result = agent.processMessage(message).getOrThrow()
        assertEquals("Response", result.content)
        assertEquals(1, result.data["call_count"])
    }

    @Test
    fun `test mock agent with single response`() = runTest {
        val agent = MockAgent(
            responses = listOf("Only one")
        )

        val message = SpiceMessage.create("Test", "user")

        // Should return same response multiple times
        repeat(5) {
            val result = agent.processMessage(message).getOrThrow()
            assertEquals("Only one", result.content)
        }
    }

    @Test
    fun `test mock agent default response`() = runTest {
        val agent = MockAgent()  // No responses specified

        val message = SpiceMessage.create("Test", "user")
        val result = agent.processMessage(message).getOrThrow()

        assertEquals("Mock response", result.content)
    }

    @Test
    fun `test mock agent canHandle always returns true`() = runTest {
        val agent = MockAgent()

        val message1 = SpiceMessage.create("Test 1", "user")
        val message2 = SpiceMessage.create("Test 2", "assistant")
        val message3 = SpiceMessage.create("Test 3", "system")

        assertTrue(agent.canHandle(message1))
        assertTrue(agent.canHandle(message2))
        assertTrue(agent.canHandle(message3))
    }

    @Test
    fun `test mock agent isReady always returns true`() = runTest {
        val agent = MockAgent()
        assertTrue(agent.isReady())
    }

    @Test
    fun `test mock agent preserves original message data`() = runTest {
        val agent = MockAgent(
            id = "test-mock",
            responses = listOf("Response")
        )

        val message = SpiceMessage.create("Test", "user").copy(
            data = mapOf("custom_key" to "custom_value")
        )

        val result = agent.processMessage(message).getOrThrow()

        // Original data should be preserved
        assertEquals("custom_value", result.data["custom_key"])

        // Mock metadata should be added
        assertEquals(true, result.data["mock"])
        assertEquals(1, result.data["call_count"])
    }

    @Test
    fun `test mock agent with empty response list uses default`() = runTest {
        val agent = MockAgent(
            responses = emptyList()
        )

        val message = SpiceMessage.create("Test", "user")
        val result = agent.processMessage(message).getOrThrow()

        // Should use default response "Mock response"
        assertEquals("Mock response", result.content)
    }

    @Test
    fun `test mock agent capabilities`() = runTest {
        val agent = MockAgent()

        assertTrue(agent.capabilities.contains("chat"))
        assertTrue(agent.capabilities.contains("testing"))
    }

    @Test
    fun `test mock agent processMessage with runtime`() = runTest {
        val agent = MockAgent(
            responses = listOf("With runtime")
        )

        val message = SpiceMessage.create("Test", "user")
        val mockRuntime = object : io.github.noailabs.spice.AgentRuntime {
            override suspend fun callAgent(agentId: String, message: SpiceMessage): io.github.noailabs.spice.error.SpiceResult<SpiceMessage> {
                return io.github.noailabs.spice.error.SpiceResult.success(message)
            }

            override suspend fun publishEvent(topic: String, message: SpiceMessage): io.github.noailabs.spice.error.SpiceResult<Unit> {
                return io.github.noailabs.spice.error.SpiceResult.success(Unit)
            }
        }

        // Test processMessage with runtime
        val result = agent.processMessage(message, mockRuntime).getOrThrow()
        assertEquals("With runtime", result.content)
    }
}
