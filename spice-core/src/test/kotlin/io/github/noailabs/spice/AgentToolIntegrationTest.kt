package io.github.noailabs.spice

import io.github.noailabs.spice.model.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test AgentTool integration with ToolRegistry
 */
class AgentToolIntegrationTest {
    
    @BeforeEach
    fun setup() {
        // Clear registry before each test
        ToolRegistry.clear()
    }
    
    @Test
    fun `test AgentTool registration preserves metadata`() {
        // Create AgentTool with metadata
        val testTool = agentTool("test-tool") {
            description("Test tool with metadata")
            
            parameters {
                string("input", "Test input")
            }
            
            tags("test", "sample", "metadata")
            metadata("version", "1.0")
            metadata("author", "Test Suite")
            
            implement { params ->
                ToolResult.success("Executed")
            }
        }
        
        // Register the tool
        val registeredTool = ToolRegistry.register(testTool)
        
        // Verify tool is registered
        assertNotNull(registeredTool)
        assertEquals("test-tool", registeredTool.name)
        
        // Verify it can be retrieved
        val retrieved = ToolRegistry.getTool("test-tool")
        assertNotNull(retrieved)
        assertEquals("test-tool", retrieved.name)
    }
    
    @Test
    fun `test search by tags`() {
        // Register multiple tools with different tags
        val tool1 = agentTool("tool1") {
            description("Tool 1")
            tags("math", "calculation")
            implement { ToolResult.success("") }
        }
        
        val tool2 = agentTool("tool2") {
            description("Tool 2")
            tags("data", "processing")
            implement { ToolResult.success("") }
        }
        
        val tool3 = agentTool("tool3") {
            description("Tool 3")
            tags("math", "statistics")
            implement { ToolResult.success("") }
        }
        
        ToolRegistry.register(tool1)
        ToolRegistry.register(tool2)
        ToolRegistry.register(tool3)
        
        // Search by tag
        val mathTools = ToolRegistry.getByTag("math")
        assertEquals(2, mathTools.size)
        assertTrue(mathTools.any { it.name == "tool1" })
        assertTrue(mathTools.any { it.name == "tool3" })
        
        val dataTools = ToolRegistry.getByTag("data")
        assertEquals(1, dataTools.size)
        assertEquals("tool2", dataTools.first().name)
    }
    
    @Test
    fun `test source differentiation`() {
        // Register AgentTool
        val agentTool = agentTool("agent-based") {
            description("Created via AgentTool")
            implement { ToolResult.success("") }
        }
        ToolRegistry.register(agentTool)
        
        // Register direct Tool
        val directTool = object : Tool {
            override val name = "direct-tool"
            override val description = "Direct implementation"
            override val schema = ToolSchema(name, description, emptyMap())
            override suspend fun execute(parameters: Map<String, Any>) = ToolResult.success("")
        }
        ToolRegistry.register(directTool)
        
        // Check sources
        val agentTools = ToolRegistry.getBySource("agent-tool")
        assertEquals(1, agentTools.size)
        assertEquals("agent-based", agentTools.first().name)
        
        val directTools = ToolRegistry.getBySource("direct")
        assertEquals(1, directTools.size)
        assertEquals("direct-tool", directTools.first().name)
    }
    
    @Test
    fun `test AgentTool execution after registration`() = runBlocking {
        // Create and register tool
        val calculator = agentTool("calc") {
            description("Simple calculator")
            
            parameters {
                number("a", "First number")
                number("b", "Second number")
                string("op", "Operation (+, -, *, /)")
            }
            
            implement { params ->
                val a = (params["a"] as? Number)?.toDouble() ?: 0.0
                val b = (params["b"] as? Number)?.toDouble() ?: 0.0
                val op = params["op"] as? String ?: "+"
                
                val result = when (op) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (b != 0.0) a / b else Double.NaN
                    else -> 0.0
                }
                
                ToolResult.success(result.toString())
            }
        }
        
        ToolRegistry.register(calculator)
        
        // Execute via registry
        val tool = ToolRegistry.getTool("calc")
        assertNotNull(tool)
        
        val result = tool.execute(mapOf(
            "a" to 10,
            "b" to 5,
            "op" to "+"
        ))
        
        assertTrue(result.success)
        assertEquals("15.0", result.result)
    }
    
    @Test
    fun `test namespace support with AgentTool`() {
        val tool = agentTool("namespaced-tool") {
            description("Tool in custom namespace")
            tags("custom")
            implement { ToolResult.success("") }
        }
        
        // Register in custom namespace
        ToolRegistry.register(tool, "custom")
        
        // Should not be found in global namespace
        val globalTool = ToolRegistry.getTool("namespaced-tool", "global")
        assertEquals(null, globalTool)
        
        // Should be found in custom namespace
        val customTool = ToolRegistry.getTool("namespaced-tool", "custom")
        assertNotNull(customTool)
        assertEquals("namespaced-tool", customTool.name)
        
        // Check namespace listing
        val customTools = ToolRegistry.getByNamespace("custom")
        assertEquals(1, customTools.size)
        assertEquals("namespaced-tool", customTools.first().name)
    }
    
    @Test
    fun `test getAgentTools returns metadata`() {
        // Register AgentTool with rich metadata
        val tool = agentTool("metadata-rich") {
            description("Tool with lots of metadata")
            tags("tag1", "tag2", "tag3")
            metadata("version", "2.1.0")
            metadata("license", "MIT")
            metadata("homepage", "https://example.com")
            implement { ToolResult.success("") }
        }
        
        ToolRegistry.register(tool)
        
        // Get AgentTools with metadata
        val agentTools = ToolRegistry.getAgentTools()
        assertEquals(1, agentTools.size)
        
        val (retrievedTool, metadata) = agentTools.first()
        assertEquals("metadata-rich", retrievedTool.name)
        
        // Check metadata
        val tags = metadata["tags"] as? List<*>
        assertNotNull(tags)
        assertEquals(3, tags.size)
        assertTrue(tags.contains("tag1"))
        
        val meta = metadata["metadata"] as? Map<*, *>
        assertNotNull(meta)
        assertEquals("2.1.0", meta["version"])
        assertEquals("MIT", meta["license"])
        assertEquals("kotlin-function", meta["implementationType"])
    }
} 