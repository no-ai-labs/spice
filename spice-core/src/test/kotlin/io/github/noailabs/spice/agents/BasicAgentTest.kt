package io.github.noailabs.spice.agents

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic Agent tests with SpiceResult
 */
class BasicAgentTest {

    @Test
    fun `test simple agent processes comm successfully`() = runBlocking {
        val agent = SimpleTestAgent()

        val comm = Comm(
            from = "user",
            to = agent.id,
            content = "Hello"
        )

        val result = agent.processComm(comm)

        assertTrue(result.isSuccess)
        result.fold(
            onSuccess = { response ->
                assertEquals("Echo: Hello", response.content)
            },
            onFailure = { error ->
                throw AssertionError("Should not fail: ${error.message}")
            }
        )
    }

    @Test
    fun `test agent can handle tools`() = runBlocking {
        val agent = SimpleTestAgent()
        val testTool = SimpleTool(
            name = "testTool",
            description = "Test tool",
            parameterSchemas = emptyMap()
        ) { params ->
            ToolResult.success("Tool executed")
        }
        agent.addTool(testTool)

        assertTrue(agent.getTools().isNotEmpty())
        assertEquals("testTool", agent.getTools().first().name)
    }

    @Test
    fun `test agent canHandle method`() = runBlocking {
        val agent = SimpleTestAgent()

        val textComm = Comm(from = "user", to = agent.id, content = "test")
        assertTrue(agent.canHandle(textComm))

        val systemComm = Comm(from = "user", to = agent.id, content = "test", type = CommType.SYSTEM)
        assertTrue(agent.canHandle(systemComm))
    }
}

/**
 * Simple test agent implementation
 */
class SimpleTestAgent : BaseAgent(
    id = "test-agent",
    name = "Test Agent",
    description = "Agent for testing"
) {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        return SpiceResult.success(
            comm.reply(
                content = "Echo: ${comm.content}",
                from = id
            )
        )
    }
}
