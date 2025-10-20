package io.github.noailabs.spice.lifecycle

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic Lifecycle tests with SpiceResult
 */
class LifecycleBasicTest {

    @Test
    fun `test agent lifecycle manager initialization`() = runBlocking {
        val agent = TestLifecycleAgent()
        val runtime = createTestRuntime()

        val manager = AgentLifecycleManager(agent, runtime)

        assertEquals(AgentState.CREATED, manager.state.value)

        val initResult = manager.initialize()
        assertTrue(initResult.isSuccess)
        assertEquals(AgentState.READY, manager.state.value)
    }

    @Test
    fun `test agent state transitions`() = runBlocking {
        val agent = TestLifecycleAgent()
        val runtime = createTestRuntime()
        val manager = AgentLifecycleManager(agent, runtime)

        // Initialize
        manager.initialize()
        assertEquals(AgentState.READY, manager.state.value)

        // Start
        val startResult = manager.start()
        assertTrue(startResult.isSuccess)
        assertEquals(AgentState.RUNNING, manager.state.value)

        // Pause
        val pauseResult = manager.pause()
        assertTrue(pauseResult.isSuccess)
        assertEquals(AgentState.PAUSED, manager.state.value)

        // Resume
        val resumeResult = manager.resume()
        assertTrue(resumeResult.isSuccess)
        assertEquals(AgentState.RUNNING, manager.state.value)

        // Stop
        val stopResult = manager.stop(1000)
        assertTrue(stopResult.isSuccess)
        assertEquals(AgentState.STOPPED, manager.state.value)
    }

    @Test
    fun `test lifecycle aware agent callbacks`() = runBlocking {
        val agent = TestLifecycleAgent()
        val runtime = createTestRuntime()

        agent.initialize(runtime)

        assertTrue(agent.initCallbackCalled)
    }

    private fun createTestRuntime(): AgentRuntime {
        return DefaultAgentRuntime(AgentContext.empty())
    }
}

/**
 * Test agent with lifecycle callbacks
 */
class TestLifecycleAgent : BaseAgent(
    id = "lifecycle-test",
    name = "Lifecycle Test Agent",
    description = "Agent for lifecycle testing"
), LifecycleAware {

    var initCallbackCalled = false
    var startCallbackCalled = false

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        return SpiceResult.success(
            comm.reply(content = "OK", from = id)
        )
    }

    override suspend fun onAfterInit() {
        initCallbackCalled = true
    }

    override suspend fun onStart() {
        startCallbackCalled = true
    }

    override suspend fun checkHealth(): HealthStatus {
        return HealthStatus(HealthLevel.HEALTHY)
    }
}
