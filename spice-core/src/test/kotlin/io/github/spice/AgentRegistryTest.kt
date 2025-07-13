package io.github.spice

import io.github.spice.agents.OpenAIAgent
import io.github.spice.agents.AnthropicAgent
import io.github.spice.agents.VertexAgent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class AgentRegistryTest {

    private lateinit var registry: AgentRegistry

    @BeforeEach
    fun setUp() {
        registry = InMemoryAgentRegistry()
    }

    @Test
    fun `should register and retrieve agents`() {
        val agent = createTestAgent("test-agent", "Test Agent", listOf("text"))
        
        registry.register(agent)
        
        val retrieved = registry.get("test-agent")
        assertNotNull(retrieved)
        assertEquals("test-agent", retrieved?.id)
        assertEquals("Test Agent", retrieved?.name)
    }

    @Test
    fun `should prevent duplicate registration without override`() {
        val agent1 = createTestAgent("duplicate", "Agent 1", listOf("text"))
        val agent2 = createTestAgent("duplicate", "Agent 2", listOf("code"))
        
        registry.register(agent1)
        
        assertThrows<IllegalArgumentException> {
            registry.register(agent2)
        }
    }

    @Test
    fun `should allow duplicate registration with override`() {
        val agent1 = createTestAgent("duplicate", "Agent 1", listOf("text"))
        val agent2 = createTestAgent("duplicate", "Agent 2", listOf("code"))
        
        registry.register(agent1)
        registry.register(agent2, override = true)
        
        val retrieved = registry.get("duplicate")
        assertEquals("Agent 2", retrieved?.name)
        assertEquals(listOf("code"), retrieved?.capabilities)
    }

    @Test
    fun `should find agents by capability`() {
        val textAgent = createTestAgent("text-agent", "Text Agent", listOf("text", "summary"))
        val codeAgent = createTestAgent("code-agent", "Code Agent", listOf("code", "text"))
        val visionAgent = createTestAgent("vision-agent", "Vision Agent", listOf("vision", "image"))
        
        registry.register(textAgent)
        registry.register(codeAgent)
        registry.register(visionAgent)
        
        val textCapableAgents = registry.findByCapability("text")
        assertEquals(2, textCapableAgents.size)
        assertTrue(textCapableAgents.any { it.id == "text-agent" })
        assertTrue(textCapableAgents.any { it.id == "code-agent" })
        
        val visionCapableAgents = registry.findByCapability("vision")
        assertEquals(1, visionCapableAgents.size)
        assertEquals("vision-agent", visionCapableAgents.first().id)
    }

    @Test
    fun `should find agents by provider`() {
        val openAIAgent = createTestOpenAIAgent("openai-1", "OpenAI Agent 1")
        val anthropicAgent = createTestAnthropicAgent("anthropic-1", "Anthropic Agent 1")
        val vertexAgent = createTestVertexAgent("vertex-1", "Vertex Agent 1")
        
        registry.register(openAIAgent)
        registry.register(anthropicAgent)
        registry.register(vertexAgent)
        
        val openAIAgents = registry.findByProvider("OpenAI")
        assertEquals(1, openAIAgents.size)
        assertEquals("openai-1", openAIAgents.first().id)
        
        val anthropicAgents = registry.findByProvider("Anthropic")
        assertEquals(1, anthropicAgents.size)
        assertEquals("anthropic-1", anthropicAgents.first().id)
    }

    @Test
    fun `should find agents by tag`() {
        val wizardAgent = createTestWizardAgent("wizard-1", "Wizard Agent")
        val swarmAgent = createTestSwarmAgent("swarm-1", "Swarm Agent")
        val toolAgent = createTestToolAgent("tool-1", "Tool Agent")
        
        registry.register(wizardAgent)
        registry.register(swarmAgent)
        registry.register(toolAgent)
        
        val wizardAgents = registry.findByTag("wizard")
        assertEquals(1, wizardAgents.size)
        assertEquals("wizard-1", wizardAgents.first().id)
        
        val toolEnabledAgents = registry.findByTag("tool-enabled")
        assertEquals(1, toolEnabledAgents.size)
        assertEquals("tool-1", toolEnabledAgents.first().id)
    }

    @Test
    fun `should get or register agent`() {
        val agent = createTestAgent("lazy-agent", "Lazy Agent", listOf("text"))
        
        // First call should create the agent
        val retrieved1 = registry.getOrRegister("lazy-agent") { agent }
        assertEquals("lazy-agent", retrieved1.id)
        assertEquals(1, registry.getAll().size)
        
        // Second call should return existing agent
        val retrieved2 = registry.getOrRegister("lazy-agent") { 
            createTestAgent("lazy-agent", "Different Agent", listOf("code"))
        }
        assertEquals("Lazy Agent", retrieved2.name) // Should be original agent
        assertEquals(1, registry.getAll().size)
    }

    @Test
    fun `should freeze registry`() {
        val agent1 = createTestAgent("agent-1", "Agent 1", listOf("text"))
        val agent2 = createTestAgent("agent-2", "Agent 2", listOf("code"))
        
        registry.register(agent1)
        registry.freeze()
        
        // Should not allow new registrations
        assertThrows<IllegalStateException> {
            registry.register(agent2)
        }
        
        // Should not allow unregistration
        assertThrows<IllegalStateException> {
            registry.unregister("agent-1")
        }
        
        // Should still allow retrieval
        assertNotNull(registry.get("agent-1"))
    }

    @Test
    fun `should generate JSON representation`() {
        val agent = createTestAgent("json-agent", "JSON Agent", listOf("text", "json"))
        registry.register(agent)
        
        val json = registry.toJSON()
        assertTrue(json.contains("json-agent"))
        assertTrue(json.contains("JSON Agent"))
        assertTrue(json.contains("totalAgents"))
    }

    @Test
    fun `should provide agent descriptors`() {
        val agent = createTestAgent("desc-agent", "Description Agent", listOf("text"))
        registry.register(agent)
        
        val descriptor = registry.getDescriptor("desc-agent")
        assertNotNull(descriptor)
        assertEquals("desc-agent", descriptor?.id)
        assertEquals("Description Agent", descriptor?.name)
        assertEquals(listOf("text"), descriptor?.capabilities)
        assertNotNull(descriptor?.createdAt)
        
        val allDescriptors = registry.getAllDescriptors()
        assertEquals(1, allDescriptors.size)
    }

    @Test
    fun `should unregister agents`() {
        val agent = createTestAgent("temp-agent", "Temporary Agent", listOf("text"))
        registry.register(agent)
        
        assertTrue(registry.unregister("temp-agent"))
        assertNull(registry.get("temp-agent"))
        assertEquals(0, registry.getAll().size)
        
        // Should return false for non-existent agent
        assertFalse(registry.unregister("non-existent"))
    }

    // Helper methods for creating test agents
    private fun createTestAgent(id: String, name: String, capabilities: List<String>): Agent {
        return object : BaseAgent(id, name, "Test agent description", capabilities) {
            override suspend fun processMessage(message: Message): Message {
                return Message(content = "Test response", role = MessageRole.ASSISTANT)
            }
            
            override fun canHandle(message: Message): Boolean = true
            override fun isReady(): Boolean = true
        }
    }

    private fun createTestOpenAIAgent(id: String, name: String): Agent {
        return object : BaseAgent(id, name, "OpenAI test agent", listOf("text")) {
            override suspend fun processMessage(message: Message): Message {
                return Message(content = "OpenAI response", role = MessageRole.ASSISTANT)
            }
            
            override fun canHandle(message: Message): Boolean = true
            override fun isReady(): Boolean = true
        }
    }

    private fun createTestAnthropicAgent(id: String, name: String): Agent {
        return object : BaseAgent(id, name, "Anthropic test agent", listOf("text")) {
            override suspend fun processMessage(message: Message): Message {
                return Message(content = "Anthropic response", role = MessageRole.ASSISTANT)
            }
            
            override fun canHandle(message: Message): Boolean = true
            override fun isReady(): Boolean = true
        }
    }

    private fun createTestVertexAgent(id: String, name: String): Agent {
        return object : BaseAgent(id, name, "Vertex test agent", listOf("text")) {
            override suspend fun processMessage(message: Message): Message {
                return Message(content = "Vertex response", role = MessageRole.ASSISTANT)
            }
            
            override fun canHandle(message: Message): Boolean = true
            override fun isReady(): Boolean = true
        }
    }

    private fun createTestWizardAgent(id: String, name: String): Agent {
        return object : BaseAgent(id, name, "Wizard test agent", listOf("text")) {
            override suspend fun processMessage(message: Message): Message {
                return Message(content = "Wizard response", role = MessageRole.ASSISTANT)
            }
            
            override fun canHandle(message: Message): Boolean = true
            override fun isReady(): Boolean = true
        }
    }

    private fun createTestSwarmAgent(id: String, name: String): Agent {
        return object : BaseAgent(id, name, "Swarm test agent", listOf("text")) {
            override suspend fun processMessage(message: Message): Message {
                return Message(content = "Swarm response", role = MessageRole.ASSISTANT)
            }
            
            override fun canHandle(message: Message): Boolean = true
            override fun isReady(): Boolean = true
        }
    }

    private fun createTestToolAgent(id: String, name: String): Agent {
        return object : BaseAgent(id, name, "Tool test agent", listOf("text")) {
            override suspend fun processMessage(message: Message): Message {
                return Message(content = "Tool response", role = MessageRole.ASSISTANT)
            }
            
            override fun canHandle(message: Message): Boolean = true
            override fun isReady(): Boolean = true
            
            override fun getTools(): List<Tool> {
                return listOf(object : Tool {
                    override val name: String = "test-tool"
                    override val description: String = "Test tool"
                    override suspend fun execute(input: String): String = "Tool result"
                })
            }
        }
    }
} 