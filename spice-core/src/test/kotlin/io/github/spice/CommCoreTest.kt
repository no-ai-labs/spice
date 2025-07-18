package io.github.spice

import io.github.spice.dsl.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*

/**
 * 🧪 Comm Core Test Suite
 * 
 * Tests core Comm functionality and Agent integration
 */
class CommCoreTest {
    
    @BeforeEach
    fun setup() {
        CommHub.reset()
        // AgentRegistry.clear()
        ToolRegistry.clear()
    }
    
    @Test
    fun `test basic Comm creation and properties`() {
        val comm = Comm(
            content = "Hello Spice!",
            from = "test-user",
            to = "test-agent"
        )
        
        assertNotNull(comm.id)
        assertEquals("Hello Spice!", comm.content)
        assertEquals("test-user", comm.from)
        assertEquals("test-agent", comm.to)
        assertEquals(CommType.TEXT, comm.type)
        assertEquals(CommRole.USER, comm.role)
        assertTrue(comm.timestamp > 0)
    }
    
    @Test
    fun `test Comm reply functionality`() {
        val original = Comm(
            content = "Question",
            from = "user",
            to = "agent"
        )
        
        val reply = original.reply(
            content = "Answer",
            from = "agent"
        )
        
        assertEquals("Answer", reply.content)
        assertEquals("agent", reply.from)
        assertEquals("user", reply.to)
        assertEquals(original.id, reply.parentId)
        assertEquals(CommRole.ASSISTANT, reply.role)
    }
    
    @Test
    fun `test Comm builder DSL`() {
        val comm = comm("Test message") {
            from("sender")
            to("receiver")
            type(CommType.SYSTEM)
            role(CommRole.SYSTEM)
            data("key", "value")
            urgent()
        }
        
        assertEquals("Test message", comm.content)
        assertEquals("sender", comm.from)
        assertEquals("receiver", comm.to)
        assertEquals(CommType.SYSTEM, comm.type)
        assertEquals(CommRole.SYSTEM, comm.role)
        assertEquals("value", comm.data["key"])
        assertEquals(Priority.URGENT, comm.priority)
    }
    
    @Test
    fun `test Agent with Comm`() = runBlocking {
        val agent = buildAgent {
            id = "echo-agent"
            name = "Echo Agent"
            description = "Simple echo agent"
            
            handle { comm ->
                comm.reply(
                    content = "Echo: ${comm.content}",
                    from = id
                )
            }
        }
        
        val input = Comm(
            content = "Hello!",
            from = "user",
            to = agent.id
        )
        
        val response = agent.processComm(input)
        
        assertEquals("Echo: Hello!", response.content)
        assertEquals("echo-agent", response.from)
        assertEquals("user", response.to)
    }
    
    /* Disabled due to tool execution ambiguity
    @Test
    fun `test Agent with tools`() = runBlocking {
        val agent = buildAgent {
            id = "calculator"
            name = "Calculator Agent"
            description = "Agent with calculation tools"
            
            tool("add") {
                description("Add two numbers")
                parameter("a", "number", "First number")
                parameter("b", "number", "Second number")
                execute { params: Map<String, Any> ->
                    val a = (params["a"] as Number).toDouble()
                    val b = (params["b"] as Number).toDouble()
                    ToolResult.success((a + b).toString())
                }
            }
            
            handle { comm ->
                when {
                    comm.content.contains("add") -> {
                        // Extract numbers from content (simple parsing)
                        val numbers = comm.content.split(" ")
                            .mapNotNull { it.toDoubleOrNull() }
                            .take(2)
                        
                        if (numbers.size == 2) {
                            // Simulate tool execution
                            val sum = numbers[0] + numbers[1]
                            comm.reply(
                                content = "Result: $sum",
                                from = id
                            )
                        } else {
                            comm.error("Please provide two numbers", id)
                        }
                    }
                    else -> comm.reply("I can add numbers. Try: 'add 5 3'", id)
                }
            }
        }
        
        // Test calculation
        val calcComm = Comm(
            content = "add 10 20",
            from = "user",
            to = agent.id
        )
        
        val result = agent.processComm(calcComm)
        assertEquals("Result: 30.0", result.content)
    }
    */
    
    @Test
    fun `test Comm metadata and extensions`() {
        val comm = Comm(
            content = "Test",
            from = "system",
            type = CommType.ERROR
        )
        
        // Test extension functions
        assertTrue(comm.isSystem())
        assertTrue(comm.isError())
        assertFalse(comm.isTool())
        
        // Test metadata
        val withData = comm
            .withData("error_code", "500")
            .withData("error_message", "Internal error")
        
        assertEquals("500", withData.data["error_code"])
        assertEquals("Internal error", withData.data["error_message"])
    }
    
    @Test
    fun `test SmartAgent from SmartCore`() = runBlocking {
        val smart = smartAgent("smart-1") {
            name = "Smart Agent"
            role = "assistant"
            capability("math", "logic")
            trust(0.9)
            
            tool("greet") { params ->
                "Hello, ${params["name"] ?: "friend"}!"
            }
        }
        
        assertEquals("smart-1", smart.id)
        assertEquals("Smart Agent", smart.name)
        assertEquals("assistant", smart.role)
        assertTrue(smart.capabilities.contains("math"))
        assertEquals(0.9, smart.trustLevel)
        
        // Register with CommHub
        CommHub.register(smart)
        
        // Send comm
        val result = smart.send(Comm(
            content = "Test message",
            from = smart.id,
            to = "user"
        ))
        
        assertTrue(result.success)
    }
    
    @Test
    fun `test Comm TTL and expiration`() {
        val comm = Comm(
            content = "Temporary message",
            from = "system"
        ).expires(1000) // 1 second TTL
        
        assertNotNull(comm.ttl)
        assertNotNull(comm.expiresAt)
        assertFalse(comm.isExpired())
        
        // Test expiration check (would be true after TTL)
        val expired = comm.copy(
            expiresAt = System.currentTimeMillis() - 1000
        )
        assertTrue(expired.isExpired())
    }
}