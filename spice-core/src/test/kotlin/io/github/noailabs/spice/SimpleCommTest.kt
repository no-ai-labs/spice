package io.github.noailabs.spice

import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*

/**
 * 🧪 Simple Comm Test Suite
 * 
 * Basic tests for Comm functionality without complex dependencies
 */
class SimpleCommTest {
    
    @BeforeEach
    fun setup() {
        CommHub.reset()
        ToolRegistry.clear()
    }
    
    @Test
    fun `test basic Comm creation`() {
        val comm = Comm(
            content = "Hello World",
            from = "sender",
            to = "receiver"
        )
        
        assertNotNull(comm.id)
        assertEquals("Hello World", comm.content)
        assertEquals("sender", comm.from)
        assertEquals("receiver", comm.to)
        assertEquals(CommType.TEXT, comm.type)
        assertEquals(CommRole.USER, comm.role)
    }
    
    @Test
    fun `test Comm reply`() {
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
    fun `test simple Agent`() = runBlocking {
        val agent = buildAgent {
            id = "simple-agent"
            name = "Simple Agent"
            description = "A simple test agent"
            
            handle { comm ->
                comm.reply(
                    content = "Received: ${comm.content}",
                    from = id
                )
            }
        }
        
        val input = Comm(
            content = "Hello",
            from = "user",
            to = agent.id
        )
        
        val response = agent.processComm(input)
        
        assertEquals("Received: Hello", response.content)
        assertEquals("simple-agent", response.from)
        assertEquals("user", response.to)
    }
    
    @Test
    fun `test Comm metadata`() {
        val comm = Comm(
            content = "Test",
            from = "system"
        )
        
        val withData = comm
            .withData("key1", "value1")
            .withData("key2", "value2")
        
        assertEquals("value1", withData.data["key1"])
        assertEquals("value2", withData.data["key2"])
        
        // Test multiple data at once
        val withMultipleData = comm.withData(
            "a" to "1",
            "b" to "2"
        )
        
        assertEquals("1", withMultipleData.data["a"])
        assertEquals("2", withMultipleData.data["b"])
    }
    
    @Test
    fun `test Comm extension functions`() {
        val systemComm = Comm(
            content = "System message",
            from = "system",
            type = CommType.SYSTEM,
            role = CommRole.SYSTEM
        )
        
        assertTrue(systemComm.isSystem())
        assertFalse(systemComm.isError())
        assertFalse(systemComm.isTool())
        
        val errorComm = Comm(
            content = "Error occurred",
            from = "system",
            type = CommType.ERROR
        )
        
        assertTrue(errorComm.isError())
        
        val toolComm = Comm(
            content = "Tool call",
            from = "agent",
            type = CommType.TOOL_CALL,
            data = mapOf("tool_name" to "calculator")
        )
        
        assertTrue(toolComm.isTool())
        assertEquals("calculator", toolComm.getToolName())
    }
    
    @Test
    fun `test quick comm functions`() {
        val quick = quickComm(
            content = "Quick message",
            from = "user",
            to = "agent"
        )
        
        assertEquals("Quick message", quick.content)
        assertEquals("user", quick.from)
        assertEquals("agent", quick.to)
        
        val system = systemComm("System notification")
        assertEquals("system", system.from)
        assertEquals(CommType.SYSTEM, system.type)
        assertEquals(CommRole.SYSTEM, system.role)
        
        val error = errorComm("Something went wrong")
        assertEquals("system", error.from)
        assertEquals(CommType.ERROR, error.type)
        assertEquals(CommRole.SYSTEM, error.role)
    }
    
    @Test
    fun `test Comm priority and TTL`() {
        val urgentComm = Comm(
            content = "Urgent message",
            from = "user"
        ).urgent()
        
        assertEquals(Priority.URGENT, urgentComm.priority)
        
        val criticalComm = urgentComm.critical()
        assertEquals(Priority.CRITICAL, criticalComm.priority)
        
        val lowPriorityComm = urgentComm.lowPriority()
        assertEquals(Priority.LOW, lowPriorityComm.priority)
        
        // Test TTL
        val expiringComm = Comm(
            content = "Temporary",
            from = "system"
        ).expires(5000) // 5 seconds
        
        assertNotNull(expiringComm.ttl)
        assertNotNull(expiringComm.expiresAt)
        assertFalse(expiringComm.isExpired())
    }
}