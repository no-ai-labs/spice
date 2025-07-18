package io.github.spice

import io.github.spice.dsl.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*

/**
 * Basic tests for core framework functionality
 */
class BasicFrameworkTest {
    
    @BeforeEach
    fun setup() {
        AgentRegistry.clear()
        ToolRegistry.clear()
    }
    
    @Test
    fun `test Comm creation and properties`() {
        val comm = Comm(
            content = "Test message",
            from = "sender",
            to = "receiver"
        )
        
        assertNotNull(comm.id)
        assertEquals("Test message", comm.content)
        assertEquals("sender", comm.from)
        assertEquals("receiver", comm.to)
        assertEquals(CommType.TEXT, comm.type)
        assertTrue(comm.timestamp > 0)
    }
    
    @Test
    fun `test Comm reply functionality`() {
        val original = Comm(content = "Question", from = "user")
        val reply = original.reply("Answer", "agent")
        
        assertEquals("Answer", reply.content)
        assertEquals("agent", reply.from)
        assertEquals("user", reply.to)
        assertEquals(original.id, reply.parentId)
    }
    
    @Test
    fun `test basic Registry operations`() {
        data class TestItem(override val id: String, val value: String) : Identifiable
        
        val registry = Registry<TestItem>("test")
        val item = TestItem("item-1", "value1")
        
        // Register
        registry.register(item)
        
        // Get
        assertEquals(item, registry.get("item-1"))
        assertNull(registry.get("non-existent"))
        
        // Size
        assertEquals(1, registry.size())
        
        // Clear
        registry.clear()
        assertEquals(0, registry.size())
    }
    
    @Test
    fun `test ToolResult creation`() {
        val success = ToolResult.success("Result", mapOf("key" to "value"))
        assertTrue(success.success)
        assertEquals("Result", success.result)
        assertEquals("value", success.metadata["key"])
        
        val error = ToolResult.error("Error message")
        assertFalse(error.success)
        assertEquals("Error message", error.error)
    }
    
    @Test
    fun `test SimpleTool`() = runBlocking {
        val tool = SimpleTool(
            name = "test",
            description = "Test tool",
            parameterSchemas = mapOf(
                "input" to ParameterSchema("string", "Input", true)
            )
        ) { params ->
            ToolResult.success("Processed: ${params["input"]}")
        }
        
        assertEquals("test", tool.name)
        assertEquals("Test tool", tool.description)
        
        val result = tool.execute(mapOf("input" to "hello"))
        assertTrue(result.success)
        assertEquals("Processed: hello", result.result)
    }
    
    @Test
    fun `test ToolWrapper`() {
        val baseTool = SimpleTool("base", "Base tool", emptyMap()) {
            ToolResult.success("OK")
        }
        
        val wrapper = ToolWrapper("namespace:base", baseTool)
        
        assertEquals("namespace:base", wrapper.id)
        assertEquals("base", wrapper.name)
        assertEquals("Base tool", wrapper.description)
    }
    
    @Test
    fun `test calculator tool basic operation`() = runBlocking {
        val calc = calculatorTool()
        
        val result = calc.execute(mapOf("expression" to "2 + 2"))
        assertTrue(result.success)
        assertEquals("4.0", result.result)
    }
    
    @Test
    fun `test text processor tool`() = runBlocking {
        val textTool = textProcessorTool()
        
        val result = textTool.execute(mapOf(
            "text" to "hello",
            "operation" to "uppercase"
        ))
        assertTrue(result.success)
        assertEquals("HELLO", result.result)
    }
    
    @Test
    fun `test SmartAgent creation`() {
        val agent = SmartAgent(
            id = "smart-1",
            name = "Smart Agent",
            role = "assistant",
            capabilities = setOf("analyze", "summarize")
        )
        
        assertEquals("smart-1", agent.id)
        assertEquals("Smart Agent", agent.name)
        assertEquals("assistant", agent.role)
        assertTrue(agent.active)
        assertEquals(2, agent.capabilities.size)
    }
    
    @Test
    fun `test CommHub registration`() {
        val agent = SmartAgent("hub-agent", "Hub Agent")
        
        CommHub.register(agent)
        assertEquals(agent, CommHub.agent("hub-agent"))
        
        CommHub.unregister("hub-agent")
        assertNull(CommHub.agent("hub-agent"))
    }
}