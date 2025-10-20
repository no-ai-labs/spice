package io.github.noailabs.spice.commhub

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic CommHub tests with SpiceResult
 */
class CommHubBasicTest {

    @Test
    fun `test in-memory backend basic operations`() = runBlocking {
        val backend = InMemoryCommBackend()

        // Send message
        val comm = Comm(
            from = "sender",
            to = "receiver",
            content = "Test message"
        )

        val result = backend.send(comm)
        assertTrue(result.success)

        // Get history
        val history = backend.getHistory("receiver")
        assertEquals(1, history.size)
        assertEquals("Test message", history[0].content)
    }

    @Test
    fun `test pluggable commhub agent registration`() = runBlocking {
        val backend = InMemoryCommBackend()
        val hub = PluggableCommHub(backend)

        val agent = TestCommAgent()
        hub.registerAgent(agent)

        val registeredAgent = hub.getAgent(agent.id)
        assertEquals(agent.id, registeredAgent?.id)

        hub.close()
    }

    @Test
    fun `test commhub message sending`() = runBlocking {
        val backend = InMemoryCommBackend()
        val hub = PluggableCommHub(backend)

        val agent = TestCommAgent()
        hub.registerAgent(agent)

        val comm = Comm(
            from = "user",
            to = agent.id,
            content = "Hello"
        )

        val result = hub.send(comm)
        assertTrue(result.success)

        // Give time for processing
        delay(100)

        hub.close()
    }

    @Test
    fun `test backend health check`() = runBlocking {
        val backend = InMemoryCommBackend()
        val health = backend.health()

        assertTrue(health.healthy)
        assertEquals(0, health.pendingMessages)
    }
}

/**
 * Test agent for CommHub testing
 */
class TestCommAgent : BaseAgent(
    id = "test-comm-agent",
    name = "Test Comm Agent",
    description = "Agent for CommHub testing"
) {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        return SpiceResult.success(
            comm.reply(
                content = "Received: ${comm.content}",
                from = id
            )
        )
    }
}
