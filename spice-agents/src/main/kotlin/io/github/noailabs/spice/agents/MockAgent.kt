package io.github.noailabs.spice.agents

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.AgentRuntime
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.error.SpiceResult
import java.util.concurrent.atomic.AtomicInteger

/**
 * 🧪 Mock Agent - Testing Agent
 *
 * Mock agent for testing that returns predefined responses.
 * Does not call external APIs.
 *
 * **Usage:**
 * ```kotlin
 * val agent = MockAgent(
 *     id = "test-agent",
 *     responses = listOf("Response 1", "Response 2", "Response 3")
 * )
 * ```
 *
 * @since 1.0.0
 * @author Spice Framework
 */
class MockAgent(
    override val id: String = "mock-agent",
    override val name: String = "Mock Agent",
    override val description: String = "Mock agent for testing",
    private val responses: List<String> = listOf("Mock response"),
    override val capabilities: List<String> = listOf("chat", "testing")
) : Agent {

    private val callCount = AtomicInteger(0)

    override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
        val index = callCount.getAndIncrement()
        val response = if (responses.isEmpty()) {
            "Mock response"
        } else {
            responses[index % responses.size]
        }

        val responseMessage = message.reply(
            content = response,
            from = id
        ).copy(
            data = message.data + mapOf(
                "mock" to true,
                "call_count" to (index + 1)
            )
        )

        return SpiceResult.success(responseMessage)
    }

    override suspend fun processMessage(
        message: SpiceMessage,
        runtime: AgentRuntime
    ): SpiceResult<SpiceMessage> {
        return processMessage(message)
    }

    override fun getTools(): List<Tool> = emptyList()

    override fun canHandle(message: SpiceMessage): Boolean = true

    override fun isReady(): Boolean = true

    /**
     * Reset call count (for testing)
     */
    fun reset() {
        callCount.set(0)
    }

    /**
     * Get current call count
     */
    fun getCallCount(): Int = callCount.get()
}
