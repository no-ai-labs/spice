package io.github.noailabs.spice.lifecycle

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.AgentRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Agent lifecycle states
 */
enum class AgentState {
    CREATED,        // Agent instance created but not initialized
    INITIALIZING,   // Agent is being initialized
    READY,          // Agent is initialized and ready to process
    RUNNING,        // Agent is actively processing requests
    PAUSED,         // Agent is paused (not accepting new requests)
    STOPPING,       // Agent is shutting down
    STOPPED,        // Agent has been stopped
    FAILED          // Agent encountered fatal error
}

/**
 * Health status of an agent
 */
data class HealthStatus(
    val status: HealthLevel,
    val lastCheck: Instant = Instant.now(),
    val details: Map<String, Any> = emptyMap(),
    val message: String? = null
)

enum class HealthLevel {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN
}

/**
 * Lifecycle event
 */
data class LifecycleEvent(
    val agentId: String,
    val previousState: AgentState,
    val newState: AgentState,
    val timestamp: Instant = Instant.now(),
    val reason: String? = null
)

/**
 * Interface for lifecycle-aware agents
 */
interface LifecycleAware {
    /**
     * Called before initialization
     */
    suspend fun onBeforeInit() {}
    
    /**
     * Called after successful initialization
     */
    suspend fun onAfterInit() {}
    
    /**
     * Called when agent is started
     */
    suspend fun onStart() {}
    
    /**
     * Called when agent is paused
     */
    suspend fun onPause() {}
    
    /**
     * Called when agent is resumed
     */
    suspend fun onResume() {}
    
    /**
     * Called before stopping
     */
    suspend fun onBeforeStop() {}
    
    /**
     * Called after stopped
     */
    suspend fun onAfterStop() {}
    
    /**
     * Health check implementation
     */
    suspend fun checkHealth(): HealthStatus = HealthStatus(HealthLevel.HEALTHY)
}

/**
 * Manages agent lifecycle with proper state transitions
 */
class AgentLifecycleManager(
    private val agent: Agent,
    private val runtime: AgentRuntime,
    private val config: LifecycleConfig = LifecycleConfig()
) {
    private val _state = MutableStateFlow(AgentState.CREATED)
    val state: StateFlow<AgentState> = _state.asStateFlow()
    
    private val _health = MutableStateFlow(HealthStatus(HealthLevel.UNKNOWN))
    val health: StateFlow<HealthStatus> = _health.asStateFlow()
    
    private val activeRequests = AtomicInteger(0)
    private var lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var healthCheckJob: Job? = null
    
    private val listeners = mutableListOf<(LifecycleEvent) -> Unit>()
    
    /**
     * Initialize the agent
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_state.value != AgentState.CREATED) {
            return@withContext Result.failure(
                IllegalStateException("Agent must be in CREATED state to initialize")
            )
        }
        
        try {
            transitionTo(AgentState.INITIALIZING)
            
            // Lifecycle callback
            if (agent is LifecycleAware) {
                agent.onBeforeInit()
            }
            
            // Initialize with timeout
            withTimeout(config.initTimeoutMs) {
                agent.initialize(runtime)
            }
            
            // Start health monitoring
            startHealthMonitoring()
            
            // Lifecycle callback
            if (agent is LifecycleAware) {
                agent.onAfterInit()
            }
            
            transitionTo(AgentState.READY)
            return@withContext Result.success(Unit)
            
        } catch (e: Exception) {
            transitionTo(AgentState.FAILED, "Initialization failed: ${e.message}")
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Start the agent
     */
    suspend fun start(): Result<Unit> {
        if (_state.value != AgentState.READY) {
            return Result.failure(
                IllegalStateException("Agent must be in READY state to start")
            )
        }
        
        return try {
            if (agent is LifecycleAware) {
                agent.onStart()
            }
            transitionTo(AgentState.RUNNING)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Pause the agent
     */
    suspend fun pause(): Result<Unit> {
        if (_state.value != AgentState.RUNNING) {
            return Result.failure(
                IllegalStateException("Agent must be in RUNNING state to pause")
            )
        }
        
        return try {
            transitionTo(AgentState.PAUSED)
            if (agent is LifecycleAware) {
                agent.onPause()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Resume the agent
     */
    suspend fun resume(): Result<Unit> {
        if (_state.value != AgentState.PAUSED) {
            return Result.failure(
                IllegalStateException("Agent must be in PAUSED state to resume")
            )
        }
        
        return try {
            if (agent is LifecycleAware) {
                agent.onResume()
            }
            transitionTo(AgentState.RUNNING)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Stop the agent gracefully
     */
    suspend fun stop(gracePeriodMs: Long = config.shutdownGracePeriodMs): Result<Unit> {
        if (_state.value in setOf(AgentState.STOPPING, AgentState.STOPPED)) {
            return Result.success(Unit)
        }
        
        return try {
            transitionTo(AgentState.STOPPING)
            
            if (agent is LifecycleAware) {
                agent.onBeforeStop()
            }
            
            // Wait for active requests to complete
            val startTime = System.currentTimeMillis()
            while (activeRequests.get() > 0 && 
                   System.currentTimeMillis() - startTime < gracePeriodMs) {
                delay(100)
            }
            
            // Force stop if still have active requests
            if (activeRequests.get() > 0) {
                runtime.log(
                    io.github.noailabs.spice.LogLevel.WARN,
                    "Force stopping agent with ${activeRequests.get()} active requests"
                )
            }
            
            // Stop health monitoring
            healthCheckJob?.cancel()
            
            // Cleanup
            agent.cleanup()
            
            if (agent is LifecycleAware) {
                agent.onAfterStop()
            }
            
            transitionTo(AgentState.STOPPED)
            
            // Cancel lifecycle scope
            lifecycleScope.cancel()
            
            Result.success(Unit)
        } catch (e: Exception) {
            transitionTo(AgentState.FAILED, "Stop failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Force stop the agent immediately
     */
    suspend fun forceStop(): Result<Unit> {
        return stop(0)
    }
    
    /**
     * Restart the agent
     */
    suspend fun restart(): Result<Unit> {
        stop().getOrThrow()
        
        // Reset state
        _state.value = AgentState.CREATED
        activeRequests.set(0)
        
        // Create new lifecycle scope
        lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        return initialize().fold(
            onSuccess = { start() },
            onFailure = { Result.failure(it) }
        )
    }
    
    /**
     * Check if agent can accept requests
     */
    fun canAcceptRequests(): Boolean {
        return _state.value == AgentState.RUNNING && 
               _health.value.status != HealthLevel.UNHEALTHY
    }
    
    /**
     * Track request lifecycle
     */
    suspend fun <T> trackRequest(block: suspend () -> T): T {
        if (!canAcceptRequests()) {
            throw IllegalStateException("Agent cannot accept requests in state: ${_state.value}")
        }
        
        activeRequests.incrementAndGet()
        return try {
            block()
        } finally {
            activeRequests.decrementAndGet()
        }
    }
    
    /**
     * Add lifecycle event listener
     */
    fun addListener(listener: (LifecycleEvent) -> Unit) {
        listeners.add(listener)
    }
    
    /**
     * Remove lifecycle event listener
     */
    fun removeListener(listener: (LifecycleEvent) -> Unit) {
        listeners.remove(listener)
    }
    
    /**
     * Get current stats
     */
    fun getStats(): LifecycleStats {
        return LifecycleStats(
            currentState = _state.value,
            healthStatus = _health.value,
            activeRequests = activeRequests.get(),
            uptime = calculateUptime()
        )
    }
    
    private fun transitionTo(newState: AgentState, reason: String? = null) {
        val previousState = _state.value
        _state.value = newState
        
        val event = LifecycleEvent(
            agentId = agent.id,
            previousState = previousState,
            newState = newState,
            reason = reason
        )
        
        // Notify listeners
        listeners.forEach { it(event) }
        
        // Log transition
        runtime.log(
            io.github.noailabs.spice.LogLevel.INFO,
            "Agent state transition: $previousState -> $newState",
            mapOf("reason" to (reason ?: ""))
        )
    }
    
    private fun startHealthMonitoring() {
        healthCheckJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    val healthStatus = if (agent is LifecycleAware) {
                        agent.checkHealth()
                    } else {
                        // Basic health check
                        if (agent.isReady()) {
                            HealthStatus(HealthLevel.HEALTHY)
                        } else {
                            HealthStatus(HealthLevel.UNHEALTHY)
                        }
                    }
                    
                    _health.value = healthStatus
                    
                    // Auto-recovery if configured
                    if (config.autoRecover && 
                        healthStatus.status == HealthLevel.UNHEALTHY &&
                        _state.value == AgentState.RUNNING) {
                        
                        runtime.log(
                            io.github.noailabs.spice.LogLevel.WARN,
                            "Agent unhealthy, attempting auto-recovery"
                        )
                        
                        lifecycleScope.launch {
                            restart()
                        }
                    }
                    
                } catch (e: Exception) {
                    _health.value = HealthStatus(
                        status = HealthLevel.UNKNOWN,
                        message = "Health check failed: ${e.message}"
                    )
                }
                
                delay(config.healthCheckIntervalMs)
            }
        }
    }
    
    private var startTime: Instant? = null
    
    private fun calculateUptime(): Long {
        return if (_state.value == AgentState.RUNNING && startTime != null) {
            java.time.Duration.between(startTime, Instant.now()).toMillis()
        } else {
            0
        }
    }
}

/**
 * Lifecycle configuration
 */
data class LifecycleConfig(
    val initTimeoutMs: Long = 30_000,
    val shutdownGracePeriodMs: Long = 30_000,
    val healthCheckIntervalMs: Long = 10_000,
    val autoRecover: Boolean = false,
    val maxRestartAttempts: Int = 3,
    val restartDelayMs: Long = 5_000
)

/**
 * Lifecycle statistics
 */
data class LifecycleStats(
    val currentState: AgentState,
    val healthStatus: HealthStatus,
    val activeRequests: Int,
    val uptime: Long
)

/**
 * Base class for lifecycle-aware agents
 */
abstract class LifecycleAwareAgent(
    id: String,
    name: String,
    description: String,
    capabilities: List<String> = emptyList()
) : io.github.noailabs.spice.BaseAgent(id, name, description, capabilities), LifecycleAware {
    
    var lifecycleManager: AgentLifecycleManager? = null
        protected set
    
    override suspend fun initialize(runtime: AgentRuntime) {
        super.initialize(runtime)
        lifecycleManager = AgentLifecycleManager(this, runtime)
    }
    
    /**
     * Process comm with lifecycle tracking
     */
    override suspend fun processComm(
        comm: io.github.noailabs.spice.Comm,
        runtime: AgentRuntime
    ): io.github.noailabs.spice.Comm {
        val manager = lifecycleManager 
            ?: throw IllegalStateException("Agent not initialized with lifecycle manager")
        
        return manager.trackRequest {
            super.processComm(comm, runtime)
        }
    }
}