package io.github.spice

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*

/**
 * Additional tests to improve code coverage
 */
class CoreCoverageTest {
    
    @BeforeEach
    fun setup() {
        CommHub.reset()
    }
    
    @Test
    fun `test Comm extensions and metadata`() {
        val comm = Comm(content = "Test", from = "system", type = CommType.SYSTEM)
        
        // Test type checks
        assertTrue(comm.isSystem())
        assertFalse(comm.isError())
        assertFalse(comm.isTool())
        
        // Test metadata
        val withData = comm.withData("key1", "value1").withData("key2", "value2")
        assertEquals("value1", withData.data["key1"])
        assertEquals("value2", withData.data["key2"])
        
        // Test priority
        val urgent = comm.urgent()
        assertEquals(Priority.URGENT, urgent.priority)
        
        // Test TTL
        val expiring = comm.expires(1000)
        assertNotNull(expiring.ttl)
        assertFalse(expiring.isExpired())
    }
    
    @Test
    fun `test Comm error type`() {
        val errorComm = Comm(content = "Error", from = "agent").error("Test error", "agent")
        assertEquals(CommType.ERROR, errorComm.type)
        assertEquals("Test error", errorComm.content)
    }
    
    @Test
    fun `test date time tool operations`() = runBlocking {
        val tool = dateTimeTool()
        
        // Test now
        val nowResult = tool.execute(mapOf("operation" to "now"))
        assertTrue(nowResult.success)
        assertTrue(nowResult.result.isNotEmpty())
        
        // Test date
        val dateResult = tool.execute(mapOf("operation" to "date"))
        assertTrue(dateResult.success)
        assertTrue(dateResult.result.contains("-"))
        
        // Test timestamp
        val tsResult = tool.execute(mapOf("operation" to "timestamp"))
        assertTrue(tsResult.success)
        assertNotNull(tsResult.result.toLongOrNull())
    }
    
    @Test
    fun `test random tool basic operations`() = runBlocking {
        val tool = randomTool()
        
        // Test error case - no type specified
        val noTypeResult = tool.execute(emptyMap())
        assertFalse(noTypeResult.success)
        
        // Test invalid type - returns success with "Unknown type" message
        val invalidResult = tool.execute(mapOf("type" to "invalid"))
        assertTrue(invalidResult.success)
        assertEquals("Unknown type: invalid", invalidResult.result)
        
        // Test valid boolean type
        val boolResult = tool.execute(mapOf("type" to "boolean"))
        assertTrue(boolResult.success)
        assertTrue(boolResult.result == "true" || boolResult.result == "false")
    }
    
    @Test
    fun `test text processor operations`() = runBlocking {
        val tool = textProcessorTool()
        
        // Test lowercase
        val lowerResult = tool.execute(mapOf(
            "text" to "HELLO",
            "operation" to "lowercase"
        ))
        assertTrue(lowerResult.success)
        assertEquals("hello", lowerResult.result)
        
        // Test length
        val lengthResult = tool.execute(mapOf(
            "text" to "test",
            "operation" to "length"
        ))
        assertTrue(lengthResult.success)
        assertEquals("4", lengthResult.result)
        
        // Test uppercase
        val upperResult = tool.execute(mapOf(
            "text" to "hello",
            "operation" to "uppercase"
        ))
        assertTrue(upperResult.success)
        assertEquals("HELLO", upperResult.result)
        
        // Test sentiment
        val sentimentResult = tool.execute(mapOf(
            "text" to "I love this great framework",
            "operation" to "sentiment"
        ))
        assertTrue(sentimentResult.success)
        assertEquals("positive", sentimentResult.result)
        
        // Test unknown operation
        val unknownResult = tool.execute(mapOf(
            "text" to "test",
            "operation" to "unknown"
        ))
        assertTrue(unknownResult.success)
        assertEquals("Unknown operation: unknown", unknownResult.result)
    }
    
    @Test
    fun `test CommHub operations`() = runBlocking {
        val agent1 = SmartAgent("agent-1", "Agent 1")
        val agent2 = SmartAgent("agent-2", "Agent 2")
        
        // Register agents
        CommHub.register(agent1)
        CommHub.register(agent2)
        
        // Test listing
        val agents = CommHub.agents()
        assertTrue(agents.contains(agent1))
        assertTrue(agents.contains(agent2))
        
        // Test send
        val result = CommHub.send(Comm(
            content = "Test",
            from = "agent-1",
            to = "agent-2"
        ))
        assertTrue(result.success)
        
        // Test broadcast
        val broadcastResults = CommHub.broadcast(
            Comm(content = "Broadcast", from = "system"),
            listOf("agent-1", "agent-2")
        )
        assertEquals(2, broadcastResults.size)
        
        // Test history
        delay(100)
        val history = CommHub.history(10)
        assertTrue(history.isNotEmpty())
        
        // Test analytics
        val analytics = CommHub.getAnalytics()
        assertTrue(analytics.totalComms > 0)
        
        // Test status
        val status = CommHub.status()
        assertEquals(2, status.registeredAgents)
    }
    
    @Test
    fun `test Registry duplicate prevention`() {
        data class Item(override val id: String, val value: String) : Identifiable
        val registry = Registry<Item>("test")
        
        val item1 = Item("duplicate", "value1")
        val item2 = Item("duplicate", "value2")
        
        // First registration
        registry.register(item1)
        assertEquals("value1", registry.get("duplicate")?.value)
        
        // Second registration overwrites (no exception thrown)
        registry.register(item2)
        assertEquals("value2", registry.get("duplicate")?.value)
        
        // Test register with override flag
        val item3 = Item("duplicate", "value3")
        registry.register(item3, override = true)
        assertEquals("value3", registry.get("duplicate")?.value)
        
        // Size should still be 1
        assertEquals(1, registry.size())
    }
    
    @Test
    fun `test ToolRegistry operations`() {
        val tool = SimpleTool("test", "Test", emptyMap()) {
            ToolResult.success("OK")
        }
        
        val wrapper = ToolWrapper("test:tool", tool)
        ToolRegistry.register(wrapper)
        
        assertEquals(wrapper, ToolRegistry.get("test:tool"))
        assertTrue(ToolRegistry.getAll().contains(wrapper))
        
        ToolRegistry.clear()
        assertEquals(0, ToolRegistry.size())
    }
    
    @Test
    fun `test calculator tool error cases`() = runBlocking {
        val calc = calculatorTool()
        
        // No expression
        val noExprResult = calc.execute(emptyMap())
        assertFalse(noExprResult.success)
        
        // Invalid expression
        val invalidResult = calc.execute(mapOf("expression" to "invalid"))
        assertFalse(invalidResult.success)
    }
    
    @Test
    fun `test SmartAgent properties`() {
        // Test various SmartAgent properties
        val agent = SmartAgent(
            id = "prop-agent",
            name = "Property Agent",
            role = "tester",
            trustLevel = 0.9
        )
        
        assertEquals("prop-agent", agent.id)
        assertEquals("Property Agent", agent.name)
        assertEquals("tester", agent.role)
        assertEquals(0.9, agent.trustLevel)
        assertTrue(agent.active)
        
        // Test builder methods
        val updated = agent
            .trust(0.95)
            .addCapability("analyze", "report")
            .deactivate()
        
        assertEquals(0.95, updated.trustLevel)
        assertEquals(2, updated.capabilities.size)
        assertFalse(updated.active)
    }
    
    @Test
    fun `test SmartAgent capabilities`() {
        val agent = SmartAgent(
            id = "cap-agent",
            name = "Capability Agent",
            capabilities = setOf("analyze", "summarize")
        )
        
        assertEquals(2, agent.capabilities.size)
        assertTrue(agent.capabilities.contains("analyze"))
        assertTrue(agent.capabilities.contains("summarize"))
    }
}