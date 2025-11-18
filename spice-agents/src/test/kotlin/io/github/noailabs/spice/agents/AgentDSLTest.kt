package io.github.noailabs.spice.agents

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.SpiceMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for Agent Factory DSL functions
 *
 * Verifies:
 * - gptAgent() factory function
 * - claudeAgent() factory function
 * - mockGPTAgent() factory function
 * - mockClaudeAgent() factory function
 * - Default parameter values
 * - Agent properties (id, name, description, capabilities)
 */
class AgentDSLTest {

    @Test
    fun `test gptAgent creates GPTAgent instance`() = runTest {
        val agent = gptAgent(
            apiKey = "sk-test",
            model = "gpt-4"
        )

        assertIs<GPTAgent>(agent)
        assertEquals("gpt-gpt-4", agent.id)
        assertEquals("GPT Agent (gpt-4)", agent.name)
        assertTrue(agent.description.contains("gpt-4"))
    }

    @Test
    fun `test gptAgent with custom parameters`() = runTest {
        val agent = gptAgent(
            apiKey = "sk-test",
            model = "gpt-4-turbo",
            systemPrompt = "You are helpful",
            temperature = 0.9,
            maxTokens = 2000,
            id = "custom-gpt",
            name = "Custom GPT",
            description = "Custom description",
            baseUrl = "https://custom.api.com",
            organizationId = "org-123"
        )

        assertEquals("custom-gpt", agent.id)
        assertEquals("Custom GPT", agent.name)
        assertEquals("Custom description", agent.description)
    }

    @Test
    fun `test gptAgent default values`() = runTest {
        val agent = gptAgent(apiKey = "sk-test")

        assertIs<GPTAgent>(agent)
        assertEquals("gpt-gpt-4", agent.id)
        assertTrue(agent.capabilities.contains("chat"))
        assertTrue(agent.capabilities.contains("completion"))
        assertTrue(agent.capabilities.contains("tools"))
    }

    @Test
    fun `test claudeAgent creates ClaudeAgent instance`() = runTest {
        val agent = claudeAgent(
            apiKey = "sk-ant-test",
            model = "claude-3-5-sonnet-20241022"
        )

        assertIs<ClaudeAgent>(agent)
        assertEquals("claude-claude-3-5-sonnet-20241022", agent.id)
        assertEquals("Claude Agent (claude-3-5-sonnet-20241022)", agent.name)
        assertTrue(agent.description.contains("claude-3-5-sonnet-20241022"))
    }

    @Test
    fun `test claudeAgent with custom parameters`() = runTest {
        val agent = claudeAgent(
            apiKey = "sk-ant-test",
            model = "claude-3-opus-20240229",
            systemPrompt = "You are helpful",
            temperature = 0.8,
            maxTokens = 4000,
            id = "custom-claude",
            name = "Custom Claude",
            description = "Custom description",
            baseUrl = "https://custom.anthropic.com"
        )

        assertEquals("custom-claude", agent.id)
        assertEquals("Custom Claude", agent.name)
        assertEquals("Custom description", agent.description)
    }

    @Test
    fun `test claudeAgent default values`() = runTest {
        val agent = claudeAgent(apiKey = "sk-ant-test")

        assertIs<ClaudeAgent>(agent)
        assertEquals("claude-claude-3-5-sonnet-20241022", agent.id)
        assertTrue(agent.capabilities.contains("chat"))
        assertTrue(agent.capabilities.contains("completion"))
        assertTrue(agent.capabilities.contains("tools"))
        assertTrue(agent.capabilities.contains("long-context"))
    }

    @Test
    fun `test mockGPTAgent creates MockAgent instance`() = runTest {
        val agent = mockGPTAgent(
            responses = listOf("Mock 1", "Mock 2")
        )

        assertIs<MockAgent>(agent)

        val message = SpiceMessage.create("Test", "user")
        val r1 = agent.processMessage(message).getOrThrow()
        val r2 = agent.processMessage(message).getOrThrow()

        assertEquals("Mock 1", r1.content)
        assertEquals("Mock 2", r2.content)
    }

    @Test
    fun `test mockGPTAgent with custom id and name`() = runTest {
        val agent = mockGPTAgent(
            responses = listOf("Response"),
            id = "custom-mock-gpt",
            name = "Custom Mock GPT"
        )

        assertEquals("custom-mock-gpt", agent.id)
        assertEquals("Custom Mock GPT", agent.name)
    }

    @Test
    fun `test mockGPTAgent default values`() = runTest {
        val agent = mockGPTAgent()

        assertEquals("mock-gpt", agent.id)
        assertEquals("Mock GPT Agent", agent.name)

        val message = SpiceMessage.create("Test", "user")
        val result = agent.processMessage(message).getOrThrow()
        assertEquals("Mock GPT response", result.content)
    }

    @Test
    fun `test mockClaudeAgent creates MockAgent instance`() = runTest {
        val agent = mockClaudeAgent(
            responses = listOf("Claude 1", "Claude 2")
        )

        assertIs<MockAgent>(agent)

        val message = SpiceMessage.create("Test", "user")
        val r1 = agent.processMessage(message).getOrThrow()
        val r2 = agent.processMessage(message).getOrThrow()

        assertEquals("Claude 1", r1.content)
        assertEquals("Claude 2", r2.content)
    }

    @Test
    fun `test mockClaudeAgent with custom id and name`() = runTest {
        val agent = mockClaudeAgent(
            responses = listOf("Response"),
            id = "custom-mock-claude",
            name = "Custom Mock Claude"
        )

        assertEquals("custom-mock-claude", agent.id)
        assertEquals("Custom Mock Claude", agent.name)
    }

    @Test
    fun `test mockClaudeAgent default values`() = runTest {
        val agent = mockClaudeAgent()

        assertEquals("mock-claude", agent.id)
        assertEquals("Mock Claude Agent", agent.name)

        val message = SpiceMessage.create("Test", "user")
        val result = agent.processMessage(message).getOrThrow()
        assertEquals("Mock Claude response", result.content)
    }

    @Test
    fun `test all factory functions return Agent interface`() = runTest {
        val agents: List<Agent> = listOf(
            gptAgent(apiKey = "sk-test"),
            claudeAgent(apiKey = "sk-ant-test"),
            mockGPTAgent(),
            mockClaudeAgent()
        )

        // All should be valid Agent instances
        agents.forEach { agent ->
            assertNotNull(agent.id)
            assertNotNull(agent.name)
            assertNotNull(agent.description)
            assertTrue(agent.capabilities.isNotEmpty())
            assertTrue(agent.isReady())
        }
    }

    @Test
    fun `test mock agents can be used interchangeably`() = runTest {
        val mockGPT = mockGPTAgent(responses = listOf("GPT"))
        val mockClaude = mockClaudeAgent(responses = listOf("Claude"))

        val message = SpiceMessage.create("Test", "user")

        val gptResult = mockGPT.processMessage(message).getOrThrow()
        val claudeResult = mockClaude.processMessage(message).getOrThrow()

        assertEquals("GPT", gptResult.content)
        assertEquals("Claude", claudeResult.content)

        // Both should have same mock metadata
        assertEquals(true, gptResult.data["mock"])
        assertEquals(true, claudeResult.data["mock"])
    }

    @Test
    fun `test gptAgent isReady when apiKey provided`() = runTest {
        val agent = gptAgent(apiKey = "sk-test")
        assertTrue(agent.isReady())
    }

    @Test
    fun `test gptAgent isNotReady when apiKey is blank`() = runTest {
        val agent = gptAgent(apiKey = "")
        assertFalse(agent.isReady())
    }

    @Test
    fun `test claudeAgent isReady when apiKey provided`() = runTest {
        val agent = claudeAgent(apiKey = "sk-ant-test")
        assertTrue(agent.isReady())
    }

    @Test
    fun `test claudeAgent isNotReady when apiKey is blank`() = runTest {
        val agent = claudeAgent(apiKey = "")
        assertFalse(agent.isReady())
    }

    @Test
    fun `test nullable maxTokens parameter`() = runTest {
        // GPT allows null maxTokens
        val gptWithNull = gptAgent(apiKey = "sk-test", maxTokens = null)
        assertNotNull(gptWithNull)

        val gptWithValue = gptAgent(apiKey = "sk-test", maxTokens = 2000)
        assertNotNull(gptWithValue)

        // Claude allows null maxTokens
        val claudeWithNull = claudeAgent(apiKey = "sk-ant-test", maxTokens = null)
        assertNotNull(claudeWithNull)

        val claudeWithValue = claudeAgent(apiKey = "sk-ant-test", maxTokens = 4000)
        assertNotNull(claudeWithValue)
    }

    @Test
    fun `test factory functions with empty tools list`() = runTest {
        val gpt = gptAgent(apiKey = "sk-test", tools = emptyList())
        val claude = claudeAgent(apiKey = "sk-ant-test", tools = emptyList())

        assertEquals(emptyList(), gpt.getTools())
        assertEquals(emptyList(), claude.getTools())
    }
}
