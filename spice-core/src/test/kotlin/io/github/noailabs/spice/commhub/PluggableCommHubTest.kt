package io.github.noailabs.spice.commhub

import io.github.noailabs.spice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.util.Collections

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class PluggableCommHubTest {
    
    private lateinit var hub: PluggableCommHub
    private lateinit var testAgent1: TestAgent
    private lateinit var testAgent2: TestAgent
    
    @BeforeEach
    fun setup() {
        // Create fresh hub for each test to avoid state pollution
        hub = CommHubFactory.create()
        // Create fresh agents for each test
        testAgent1 = TestAgent("agent1", "Agent 1")
        testAgent2 = TestAgent("agent2", "Agent 2")
    }
    
    @AfterEach
    fun teardown() = runBlocking {
        // Wait for any pending operations
        delay(100)
        hub.close()
        // Give time for cleanup
        delay(200)
    }
    
    @Test
    fun `test agent registration and retrieval`() = runTest {
        hub.registerAgent(testAgent1)
        hub.registerAgent(testAgent2)
        
        assertEquals(testAgent1, hub.getAgent("agent1"))
        assertEquals(testAgent2, hub.getAgent("agent2"))
        assertEquals(2, hub.getAllAgents().size)
    }
    
    @Test
    fun `test message sending between agents`() = runTest {
        hub.registerAgent(testAgent1)
        hub.registerAgent(testAgent2)
        
        // Give agents time to initialize and start processing
        delay(200)
        
        val comm = Comm(
            from = "agent1",
            to = "agent2",
            content = "Hello from agent1"
        )
        
        val result = hub.send(comm)
        assertTrue(result.success)
        
        // Wait for message to be processed
        // Use a polling approach with timeout
        val maxWaitTime = 2000L
        val pollInterval = 100L
        var waited = 0L
        
        while (testAgent2.receivedMessages.isEmpty() && waited < maxWaitTime) {
            delay(pollInterval)
            waited += pollInterval
        }
        
        // Check that agent2 received the message
        assertEquals(1, testAgent2.receivedMessages.size)
        assertEquals("Hello from agent1", testAgent2.receivedMessages.first().content)
    }
    
    @Test
    fun `test broadcast functionality`() = runTest {
        hub.registerAgent(testAgent1)
        hub.registerAgent(testAgent2)
        val testAgent3 = TestAgent("agent3", "Agent 3")
        hub.registerAgent(testAgent3)
        
        // Give agents time to initialize
        delay(200)
        
        val comm = Comm(
            from = "agent1",
            content = "Broadcast message"
        )
        
        val results = hub.broadcast(comm)
        assertEquals(2, results.size) // Should send to agent2 and agent3
        assertTrue(results.all { it.success })
        
        // Wait for messages to be processed with polling
        val maxWaitTime = 2000L
        val pollInterval = 100L
        var waited = 0L
        
        while ((testAgent2.receivedMessages.isEmpty() || testAgent3.receivedMessages.isEmpty()) && waited < maxWaitTime) {
            delay(pollInterval)
            waited += pollInterval
            println("Waiting for broadcast messages... agent2: ${testAgent2.receivedMessages.size}, agent3: ${testAgent3.receivedMessages.size}")
        }
        
        // Check recipients
        assertEquals(1, testAgent2.receivedMessages.size, "Agent2 should have received 1 message")
        assertEquals(1, testAgent3.receivedMessages.size, "Agent3 should have received 1 message")
        assertEquals("Broadcast message", testAgent2.receivedMessages.first().content)
        assertEquals("Broadcast message", testAgent3.receivedMessages.first().content)
        
        // agent1 should have received responses from agent2 and agent3
        // Wait for responses
        waited = 0L
        while (testAgent1.receivedMessages.size < 2 && waited < maxWaitTime) {
            delay(pollInterval)
            waited += pollInterval
        }
        
        // Verify agent1 received the responses
        assertEquals(2, testAgent1.receivedMessages.size, "Agent1 should have received 2 responses")
        assertTrue(testAgent1.receivedMessages.all { it.content.startsWith("Response from") })
    }
    
    @Test
    fun `test message history`() = runTest {
        hub.registerAgent(testAgent1)
        hub.registerAgent(testAgent2)
        
        // Clear any previous history first
        delay(100)
        
        // Send multiple messages
        val sentMessages = mutableListOf<Comm>()
        repeat(5) { i ->
            val comm = Comm(
                from = "agent1",
                to = "agent2",
                content = "Message $i"
            )
            sentMessages.add(comm)
            hub.send(comm)
        }
        
        // Wait for messages to be stored
        delay(100)
        
        // Check history - should contain at least our 5 messages
        val history = hub.getHistory()
        assertTrue(history.size >= 5, "History size should be at least 5, but was ${history.size}")
        
        val agent1History = hub.getHistory(agentId = "agent1")
        val ourMessages = agent1History.filter { comm ->
            sentMessages.any { sent -> sent.content == comm.content }
        }
        assertEquals(5, ourMessages.size)
        assertTrue(ourMessages.all { it.from == "agent1" })
    }
    
    @Test
    fun `test subscription functionality`() = runTest {
        hub.registerAgent(testAgent1)
        
        // Give time to set up
        delay(100)
        
        val messages = mutableListOf<Comm>()
        val subscription = launch {
            hub.subscribe("agent1").take(3).collect { comm ->
                messages.add(comm)
            }
        }
        
        // Give subscription time to start
        delay(100)
        
        // Send messages
        repeat(3) { i ->
            hub.send(Comm(
                from = "external",
                to = "agent1",
                content = "Subscription test $i"
            ))
            delay(50) // Small delay between messages
        }
        
        // Wait for collection to complete
        subscription.join()
        
        assertEquals(3, messages.size)
    }
    
    @Test
    fun `test pattern subscription`() = runTest {
        hub.registerAgent(testAgent1)
        hub.registerAgent(testAgent2)
        val testAgent3 = TestAgent("service-1", "Service 1")
        hub.registerAgent(testAgent3)
        
        // Give time to set up
        delay(100)
        
        val messages = mutableListOf<Comm>()
        val subscription = launch {
            hub.subscribePattern("service-.*").take(1).collect { comm ->
                messages.add(comm)
            }
        }
        
        // Give subscription time to start
        delay(100)
        
        // Send messages
        hub.send(Comm(from = "test", to = "agent1", content = "To agent"))
        delay(50)
        hub.send(Comm(from = "test", to = "service-1", content = "To service"))
        
        // Wait for collection
        subscription.join()
        
        assertEquals(1, messages.size)
        assertEquals("service-1", messages.first().to)
    }
    
    @Test
    fun `test middleware functionality`() = runTest {
        hub.registerAgent(testAgent1)
        
        // Give time to set up
        delay(200)
        
        // Add middleware that adds metadata
        hub.addMiddleware(object : CommMiddleware {
            override suspend fun process(comm: Comm): Comm {
                return comm.withData("processed", "true")
            }
        })
        
        val comm = Comm(
            from = "test",
            to = "agent1",
            content = "Test middleware"
        )
        
        hub.send(comm)
        
        // Wait for message to be processed with polling
        val maxWaitTime = 2000L
        val pollInterval = 100L
        var waited = 0L
        
        while (testAgent1.receivedMessages.isEmpty() && waited < maxWaitTime) {
            delay(pollInterval)
            waited += pollInterval
        }
        
        assertTrue(testAgent1.receivedMessages.isNotEmpty(), "Agent should have received messages")
        val received = testAgent1.receivedMessages.first()
        assertEquals("true", received.data["processed"])
    }
    
    @Test
    fun `test hub status and metrics`() = runTest {
        // Fresh hub with clean metrics
        val testHub = CommHubFactory.create()
        // Create agents that don't send responses to avoid timing issues
        val localAgent1 = object : BaseAgent("local1", "Local Agent 1", "Test agent") {
            override suspend fun processComm(comm: Comm): Comm {
                // Just process without sending response
                return comm
            }
        }
        val localAgent2 = object : BaseAgent("local2", "Local Agent 2", "Test agent") {
            override suspend fun processComm(comm: Comm): Comm {
                // Just process without sending response
                return comm
            }
        }
        
        try {
            testHub.registerAgent(localAgent1)
            testHub.registerAgent(localAgent2)
            
            // Give time for initialization
            delay(100)
            
            // Send some messages
            repeat(5) {
                testHub.send(Comm(from = "local1", to = "local2", content = "Test $it"))
            }
            
            // Wait a bit for metrics to update
            delay(200)
            
            val status = testHub.getStatus()
            assertTrue(status.healthy)
            assertEquals(2, status.registeredAgents)
            // We expect exactly 5 messages sent (no responses)
            assertEquals(5, status.metrics.messagesSent)
        } finally {
            testHub.close()
        }
    }
    
    @Test
    fun `test error handling`() = runTest {
        hub.registerAgent(testAgent1)
        
        // Send to non-existent agent
        val result = hub.send(Comm(
            from = "agent1",
            to = "non-existent",
            content = "Test"
        ))
        
        assertFalse(result.success)
        assertTrue(result.error?.contains("Unknown recipient") == true)
    }
}

/**
 * Simple test agent
 */
class TestAgent(
    override val id: String,
    override val name: String
) : BaseAgent(id, name, "Test agent") {
    
    val receivedMessages = Collections.synchronizedList(mutableListOf<Comm>())
    val processedMessages = Collections.synchronizedList(mutableListOf<Comm>())
    
    override suspend fun processComm(comm: Comm): Comm {
        println("[$id] Processing comm: ${comm.content}")
        receivedMessages.add(comm)
        println("[$id] receivedMessages.size = ${receivedMessages.size}")
        
        // Don't send a response if this is already a response (to avoid infinite loop)
        if (comm.content.startsWith("Response from")) {
            return comm
        }
        
        val response = comm.reply("Response from $id", from = id)
        processedMessages.add(response)
        
        return response
    }
    
    override fun canHandle(comm: Comm): Boolean = true
}

/**
 * Test different backend configurations
 */
class CommHubBackendTest {
    
    @Test
    fun `test in-memory backend configuration`() = runTest {
        val hub = CommHubFactory.create(
            backendType = "in-memory",
            backendConfig = mapOf(
                "channelCapacity" to 100,
                "maxHistorySize" to 1000
            )
        )
        
        val status = hub.getStatus()
        assertTrue(status.healthy)
        // Check that it's an in-memory backend by verifying the name
        assertEquals("default", status.name)
        
        hub.close()
    }
    
    @Test
    fun `test backend factory registration`() {
        val customFactory = object : CommBackendFactory {
            override fun create(config: BackendConfig): CommBackend {
                return InMemoryCommBackend()
            }
            
            override fun supports(type: String): Boolean {
                return type == "custom"
            }
        }
        
        CommHubFactory.registerBackendFactory(customFactory)
        
        val hub = CommHubFactory.create(backendType = "custom")
        assertNotNull(hub)
        
        hub.close()
    }
}