package io.github.noailabs.spice.dsl

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for AgentBuilder DSL (buildAgent)
 *
 * Verifies:
 * - Basic agent creation with buildAgent { }
 * - Custom id, name, description
 * - Tool integration
 * - Custom message handler
 * - Vector store configuration
 * - Debug mode
 * - Default behavior (echo when no handler)
 */
class AgentBuilderTest {

    @Test
    fun `test buildAgent creates agent with default values`() = runTest {
        val agent = buildAgent {
            // Use all defaults
        }

        assertNotNull(agent.id)
        assertTrue(agent.id.startsWith("custom-agent-"))
        assertEquals("Custom Agent", agent.name)
        assertEquals("Custom agent built with DSL", agent.description)
        assertTrue(agent.capabilities.contains("chat"))
        assertTrue(agent.capabilities.contains("custom"))
    }

    @Test
    fun `test buildAgent with custom properties`() = runTest {
        val agent = buildAgent {
            id = "my-custom-agent"
            name = "My Custom Agent"
            description = "This is my custom agent"
        }

        assertEquals("my-custom-agent", agent.id)
        assertEquals("My Custom Agent", agent.name)
        assertEquals("This is my custom agent", agent.description)
    }

    @Test
    fun `test buildAgent with custom handler`() = runTest {
        val agent = buildAgent {
            id = "echo-agent"

            handle { message ->
                SpiceResult.success(
                    message.reply(
                        content = "Echo: ${message.content}",
                        from = id
                    )
                )
            }
        }

        val message = SpiceMessage.create("Hello", "user")
        val result = agent.processMessage(message).getOrThrow()

        assertEquals("Echo: Hello", result.content)
        assertEquals("echo-agent", result.from)
    }

    @Test
    fun `test buildAgent default handler echoes message`() = runTest {
        val agent = buildAgent {
            id = "default-agent"
        }

        val message = SpiceMessage.create("Test message", "user")
        val result = agent.processMessage(message).getOrThrow()

        assertTrue(result.content.contains("Custom Agent"))
        assertTrue(result.content.contains("Received: Test message"))
        assertEquals("default-agent", result.from)
    }

    @Test
    fun `test buildAgent with tools by name`() = runTest {
        val agent = buildAgent {
            id = "tool-agent"

            // Add tools by name (will be resolved from registry)
            tools("web_search", "calculator")
        }

        // Tools should be tracked (even if not resolved yet)
        assertNotNull(agent)
        assertEquals("tool-agent", agent.id)
    }

    @Test
    fun `test buildAgent with tool instances`() = runTest {
        val testTool = object : Tool {
            override val name = "test_tool"
            override val description = "A test tool"

            override suspend fun execute(
                params: Map<String, Any>,
                context: ToolContext
            ): SpiceResult<ToolResult> {
                return SpiceResult.success(ToolResult.success("Tool executed"))
            }
        }

        val agent = buildAgent {
            id = "tool-agent"
            tools(testTool)
        }

        val tools = agent.getTools()
        assertEquals(1, tools.size)
        assertEquals("test_tool", tools[0].name)
    }

    @Test
    fun `test buildAgent with globalTools`() = runTest {
        val agent = buildAgent {
            id = "global-tool-agent"

            globalTools("knowledge_base", "web_search")
        }

        assertNotNull(agent)
        assertEquals("global-tool-agent", agent.id)
    }

    @Test
    fun `test buildAgent with vector store configuration`() = runTest {
        val agent = buildAgent {
            id = "vector-agent"

            vectorStore("docs") {
                provider("qdrant")
                connection("localhost", 6333)
                collection("documents")
                embeddingModel("text-embedding-ada-002")
            }
        }

        assertNotNull(agent)
        assertEquals("vector-agent", agent.id)
    }

    @Test
    fun `test buildAgent with debug mode`() = runTest {
        val agent = buildAgent {
            id = "debug-agent"
            debugMode(true)
        }

        assertNotNull(agent)
        assertEquals("debug-agent", agent.id)
    }

    @Test
    fun `test buildAgent capabilities can be modified`() = runTest {
        val agent = buildAgent {
            id = "custom-caps-agent"
            capabilities.add("special-feature")
            capabilities.add("advanced-mode")
        }

        assertTrue(agent.capabilities.contains("chat"))
        assertTrue(agent.capabilities.contains("custom"))
        assertTrue(agent.capabilities.contains("special-feature"))
        assertTrue(agent.capabilities.contains("advanced-mode"))
    }

    @Test
    fun `test buildAgent handler can access agent id`() = runTest {
        val agent = buildAgent {
            id = "id-aware-agent"

            handle { message ->
                SpiceResult.success(
                    message.reply(
                        content = "Handled by: $id",
                        from = id
                    )
                )
            }
        }

        val message = SpiceMessage.create("Test", "user")
        val result = agent.processMessage(message).getOrThrow()

        assertEquals("Handled by: id-aware-agent", result.content)
        assertEquals("id-aware-agent", result.from)
    }

    @Test
    fun `test buildAgent canHandle always returns true`() = runTest {
        val agent = buildAgent {
            id = "test-agent"
        }

        val msg1 = SpiceMessage.create("Test 1", "user")
        val msg2 = SpiceMessage.create("Test 2", "assistant")

        assertTrue(agent.canHandle(msg1))
        assertTrue(agent.canHandle(msg2))
    }

    @Test
    fun `test buildAgent isReady always returns true`() = runTest {
        val agent = buildAgent {
            id = "test-agent"
        }

        assertTrue(agent.isReady())
    }

    @Test
    fun `test buildAgent processMessage with runtime`() = runTest {
        val agent = buildAgent {
            id = "runtime-agent"

            handle { message ->
                SpiceResult.success(
                    message.reply("With runtime", id)
                )
            }
        }

        val message = SpiceMessage.create("Test", "user")
        val mockRuntime = object : io.github.noailabs.spice.AgentRuntime {
            override suspend fun callAgent(agentId: String, message: SpiceMessage): SpiceResult<SpiceMessage> {
                return SpiceResult.success(message)
            }

            override suspend fun publishEvent(topic: String, message: SpiceMessage): SpiceResult<Unit> {
                return SpiceResult.success(Unit)
            }
        }

        val result = agent.processMessage(message, mockRuntime).getOrThrow()
        assertEquals("With runtime", result.content)
    }

    @Test
    fun `test buildAgent handler can return errors`() = runTest {
        val agent = buildAgent {
            id = "error-agent"

            handle { message ->
                if (message.content == "ERROR") {
                    SpiceResult.failure(
                        io.github.noailabs.spice.error.SpiceError.executionError("Intentional error")
                    )
                } else {
                    SpiceResult.success(message.reply("OK", id))
                }
            }
        }

        // Success case
        val okMessage = SpiceMessage.create("OK", "user")
        val okResult = agent.processMessage(okMessage)
        assertTrue(okResult.isSuccess)

        // Error case
        val errorMessage = SpiceMessage.create("ERROR", "user")
        val errorResult = agent.processMessage(errorMessage)
        assertTrue(errorResult.isFailure)
    }

    @Test
    fun `test buildAgent with complex handler logic`() = runTest {
        var callCount = 0

        val agent = buildAgent {
            id = "complex-agent"

            handle { message ->
                callCount++

                val response = when {
                    message.content.startsWith("HELLO") -> "Hi there!"
                    message.content.startsWith("COUNT") -> "Call count: $callCount"
                    else -> "Unknown command"
                }

                SpiceResult.success(
                    message.reply(response, id).copy(
                        data = message.data + mapOf("call_count" to callCount)
                    )
                )
            }
        }

        val r1 = agent.processMessage(SpiceMessage.create("HELLO", "user")).getOrThrow()
        assertEquals("Hi there!", r1.content)
        assertEquals(1, r1.data["call_count"])

        val r2 = agent.processMessage(SpiceMessage.create("COUNT", "user")).getOrThrow()
        assertEquals("Call count: 2", r2.content)
        assertEquals(2, r2.data["call_count"])

        val r3 = agent.processMessage(SpiceMessage.create("UNKNOWN", "user")).getOrThrow()
        assertEquals("Unknown command", r3.content)
        assertEquals(3, r3.data["call_count"])
    }

    @Test
    fun `test VectorStoreConfig builder`() = runTest {
        val config = VectorStoreConfig("test-store").apply {
            provider("qdrant")
            connection("localhost", 6333)
            collection("docs")
            embeddingModel("text-embedding-3-small")
        }

        assertEquals("test-store", config.name)
        assertEquals("qdrant", config.provider)
        assertEquals("localhost", config.host)
        assertEquals(6333, config.port)
        assertEquals("docs", config.collection)
        assertEquals("text-embedding-3-small", config.embeddingModel)
    }

    @Test
    fun `test VectorStoreConfig default values`() = runTest {
        val config = VectorStoreConfig("default-store")

        assertEquals("default-store", config.name)
        assertEquals("qdrant", config.provider)
        assertEquals("localhost", config.host)
        assertEquals(6333, config.port)
        assertEquals("documents", config.collection)
        assertEquals("text-embedding-ada-002", config.embeddingModel)
        assertNull(config.apiKey)
    }

    @Test
    fun `test multiple agents can be created independently`() = runTest {
        val agent1 = buildAgent {
            id = "agent-1"
            handle { msg -> SpiceResult.success(msg.reply("Agent 1", id)) }
        }

        val agent2 = buildAgent {
            id = "agent-2"
            handle { msg -> SpiceResult.success(msg.reply("Agent 2", id)) }
        }

        val message = SpiceMessage.create("Test", "user")

        val r1 = agent1.processMessage(message).getOrThrow()
        val r2 = agent2.processMessage(message).getOrThrow()

        assertEquals("Agent 1", r1.content)
        assertEquals("agent-1", r1.from)

        assertEquals("Agent 2", r2.content)
        assertEquals("agent-2", r2.from)
    }
}
