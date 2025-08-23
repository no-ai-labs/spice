package io.github.noailabs.spice.lifecycle

import io.github.noailabs.spice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

class AgentLifecycleTest {
    
    private lateinit var testAgent: TestLifecycleAgent
    private lateinit var runtime: TestAgentRuntime
    private lateinit var lifecycleManager: AgentLifecycleManager
    
    @BeforeEach
    fun setup() {
        testAgent = TestLifecycleAgent()
        runtime = TestAgentRuntime()
        lifecycleManager = AgentLifecycleManager(testAgent, runtime)
    }
    
    @AfterEach
    fun teardown() {
        runBlocking {
            lifecycleManager.forceStop()
        }
    }
    
    @Test
    fun `test agent lifecycle transitions`() = runTest {
        // Initial state
        assertEquals(AgentState.CREATED, lifecycleManager.state.value)
        
        // Initialize
        val initResult = lifecycleManager.initialize()
        assertTrue(initResult.isSuccess)
        assertEquals(AgentState.READY, lifecycleManager.state.value)
        assertTrue(testAgent.initCalled)
        
        // Start
        val startResult = lifecycleManager.start()
        assertTrue(startResult.isSuccess)
        assertEquals(AgentState.RUNNING, lifecycleManager.state.value)
        assertTrue(testAgent.startCalled)
        
        // Pause
        val pauseResult = lifecycleManager.pause()
        assertTrue(pauseResult.isSuccess)
        assertEquals(AgentState.PAUSED, lifecycleManager.state.value)
        assertTrue(testAgent.pauseCalled)
        
        // Resume
        val resumeResult = lifecycleManager.resume()
        assertTrue(resumeResult.isSuccess)
        assertEquals(AgentState.RUNNING, lifecycleManager.state.value)
        assertTrue(testAgent.resumeCalled)
        
        // Stop
        val stopResult = lifecycleManager.stop()
        assertTrue(stopResult.isSuccess)
        assertEquals(AgentState.STOPPED, lifecycleManager.state.value)
        assertTrue(testAgent.stopCalled)
    }
    
    @Test
    fun `test invalid state transitions`() = runTest {
        // Cannot start without initialization
        val startResult = lifecycleManager.start()
        assertTrue(startResult.isFailure)
        
        // Initialize first
        lifecycleManager.initialize().getOrThrow()
        
        // Cannot pause when not running
        val pauseResult = lifecycleManager.pause()
        assertTrue(pauseResult.isFailure)
        
        // Start then pause should work
        lifecycleManager.start().getOrThrow()
        val pauseResult2 = lifecycleManager.pause()
        assertTrue(pauseResult2.isSuccess)
    }
    
    @Test
    fun `test request tracking`() = runTest {
        lifecycleManager.initialize().getOrThrow()
        lifecycleManager.start().getOrThrow()
        
        val stats1 = lifecycleManager.getStats()
        assertEquals(0, stats1.activeRequests)
        
        // Start a request
        val job = launch {
            lifecycleManager.trackRequest<Unit> {
                delay(100)
            }
        }
        
        delay(50) // Let request start
        val stats2 = lifecycleManager.getStats()
        assertEquals(1, stats2.activeRequests)
        
        job.join() // Wait for completion
        val stats3 = lifecycleManager.getStats()
        assertEquals(0, stats3.activeRequests)
    }
    
    @Test
    fun `test health monitoring`() = runTest {
        lifecycleManager.initialize().getOrThrow()
        
        // Wait for health check
        delay(100)
        
        val health = lifecycleManager.health.value
        assertEquals(HealthLevel.HEALTHY, health.status)
        assertEquals("All systems operational", health.message)
    }
    
    @Test
    fun `test graceful shutdown with active requests`() = runTest {
        lifecycleManager.initialize().getOrThrow()
        lifecycleManager.start().getOrThrow()
        
        // Start a long-running request
        val requestJob = launch {
            lifecycleManager.trackRequest<Unit> {
                delay(500)
            }
        }
        
        delay(50) // Let request start
        
        // Stop with short grace period
        val stopJob = launch {
            lifecycleManager.stop(gracePeriodMs = 200)
        }
        
        delay(100) // During grace period
        assertEquals(AgentState.STOPPING, lifecycleManager.state.value)
        
        stopJob.join()
        assertEquals(AgentState.STOPPED, lifecycleManager.state.value)
        
        requestJob.cancel() // Clean up
    }
    
    @Test
    fun `test restart functionality`() = runTest {
        lifecycleManager.initialize().getOrThrow()
        lifecycleManager.start().getOrThrow()
        
        testAgent.processedCount = 5 // Simulate some work
        
        val restartResult = lifecycleManager.restart()
        assertTrue(restartResult.isSuccess)
        
        assertEquals(AgentState.RUNNING, lifecycleManager.state.value)
        assertEquals(0, testAgent.processedCount) // Should be reset
        assertEquals(2, testAgent.initCallCount) // Initialized twice
    }
}

/**
 * Test agent with lifecycle awareness
 */
class TestLifecycleAgent : BaseAgent(
    id = "test-agent",
    name = "Test Agent",
    description = "Agent for testing lifecycle"
), LifecycleAware {
    
    var initCalled = false
    var initCallCount = 0
    var startCalled = false
    var pauseCalled = false
    var resumeCalled = false
    var stopCalled = false
    var processedCount = 0
    
    override suspend fun onBeforeInit() {
        initCalled = true
        initCallCount++
    }
    
    override suspend fun onStart() {
        startCalled = true
    }
    
    override suspend fun onPause() {
        pauseCalled = true
    }
    
    override suspend fun onResume() {
        resumeCalled = true
    }
    
    override suspend fun onBeforeStop() {
        stopCalled = true
    }
    
    override suspend fun checkHealth(): HealthStatus {
        return HealthStatus(
            status = if (processedCount < 10) HealthLevel.HEALTHY else HealthLevel.DEGRADED,
            message = "All systems operational",
            details = mapOf("processedCount" to processedCount)
        )
    }
    
    override suspend fun processComm(comm: Comm): Comm {
        processedCount++
        return comm.reply("Processed by test agent", from = id)
    }
    
    override suspend fun cleanup() {
        processedCount = 0
    }
}

/**
 * Test runtime implementation
 */
class TestAgentRuntime : AgentRuntime {
    override val context = AgentContext()
    override val scope = CoroutineScope(Dispatchers.IO)
    
    override suspend fun callAgent(agentId: String, comm: Comm): Comm = 
        throw NotImplementedError("Not implemented for test")
    
    override suspend fun publishEvent(event: AgentEvent) {
        // No-op for testing
    }
    
    override fun log(level: LogLevel, message: String, data: Map<String, Any>) {
        println("[TEST] [$level] $message $data")
    }
    
    override suspend fun saveState(key: String, value: Any) {
        // No-op for testing
    }
    
    override suspend fun getState(key: String): Any? = null
}