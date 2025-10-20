package io.github.noailabs.spice.commhub

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pluggable CommHub with backend support
 * Supports multiple backend implementations for different deployment scenarios
 */
class PluggableCommHub(
    private val backend: CommBackend,
    private val config: CommHubConfig = CommHubConfig()
) : AutoCloseable {
    
    private val agents = ConcurrentHashMap<String, Agent>()
    private val tools = ConcurrentHashMap<String, Tool>()
    private val middlewares = mutableListOf<CommMiddleware>()
    private val closed = AtomicBoolean(false)
    
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + 
        CoroutineName("CommHub-${config.name}")
    )
    
    // Metrics
    private val metrics = CommHubMetrics()
    
    init {
        // Start background tasks
        if (config.enableMetrics) {
            startMetricsCollection()
        }
    }
    
    /**
     * Register an agent
     */
    fun registerAgent(agent: Agent): Agent {
        ensureNotClosed()
        
        agents[agent.id] = agent
        
        // Start message processing for this agent
        scope.launch {
            processMessagesFor(agent)
        }
        
        metrics.agentRegistered()
        return agent
    }
    
    /**
     * Unregister an agent
     */
    suspend fun unregisterAgent(agentId: String) {
        ensureNotClosed()
        
        agents.remove(agentId)
        backend.clear(agentId)
        metrics.agentUnregistered()
    }
    
    /**
     * Get agent by ID
     */
    fun getAgent(id: String): Agent? = agents[id]
    
    /**
     * Get all agents
     */
    fun getAllAgents(): List<Agent> = agents.values.toList()
    
    /**
     * Register a tool
     */
    fun registerTool(tool: Tool): Tool {
        ensureNotClosed()
        
        tools[tool.name] = tool
        return tool
    }
    
    /**
     * Get tool by name
     */
    fun getTool(name: String): Tool? = tools[name]
    
    /**
     * Send a message
     */
    suspend fun send(comm: Comm): CommResult {
        ensureNotClosed()
        
        // Apply middlewares
        val processedComm = applyMiddlewares(comm)
        
        // Validate
        if (processedComm.to != null && !agents.containsKey(processedComm.to)) {
            return CommResult.failure("Unknown recipient: ${processedComm.to}")
        }
        
        // Send through backend
        val result = backend.send(processedComm)
        
        // Update metrics
        if (result.success) {
            metrics.messageSent()
        } else {
            metrics.messageFailed()
        }
        
        return result
    }
    
    /**
     * Broadcast to multiple agents
     */
    suspend fun broadcast(
        comm: Comm,
        recipients: List<String>? = null
    ): List<CommResult> {
        ensureNotClosed()
        
        val targetRecipients = recipients ?: agents.keys.filter { it != comm.from }
        
        return coroutineScope {
            targetRecipients.map { recipient ->
                async {
                    send(comm.copy(to = recipient))
                }
            }.awaitAll()
        }
    }
    
    /**
     * Subscribe to messages for an agent
     */
    fun subscribe(agentId: String): Flow<Comm> {
        ensureNotClosed()
        return backend.subscribe(agentId)
    }
    
    /**
     * Subscribe to messages matching a pattern
     */
    fun subscribePattern(pattern: String): Flow<Comm> {
        ensureNotClosed()
        return backend.subscribePattern(pattern)
    }
    
    /**
     * Get message history
     */
    suspend fun getHistory(
        agentId: String? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<Comm> {
        ensureNotClosed()
        return backend.getHistory(agentId, limit, offset)
    }
    
    /**
     * Add middleware
     */
    fun addMiddleware(middleware: CommMiddleware) {
        middlewares.add(middleware)
    }
    
    /**
     * Get hub status
     */
    suspend fun getStatus(): CommHubStatus {
        val backendHealth = backend.health()
        
        return CommHubStatus(
            name = config.name,
            healthy = backendHealth.healthy && !closed.get(),
            registeredAgents = agents.size,
            registeredTools = tools.size,
            metrics = metrics.snapshot(),
            backendHealth = backendHealth
        )
    }
    
    /**
     * Close the hub
     */
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runBlocking {
                // Cancel all coroutines
                scope.cancel()
                
                // Close backend
                backend.close()
            }
        }
    }
    
    private fun ensureNotClosed() {
        if (closed.get()) {
            throw IllegalStateException("CommHub is closed")
        }
    }
    
    private suspend fun processMessagesFor(agent: Agent) {
        backend.subscribe(agent.id)
            .catch { e ->
                // Log error
                metrics.messageError()
            }
            .collect { comm ->
                try {
                    // Create runtime for this agent
                    val runtime = createRuntime(agent)

                    // Process the message
                    val responseResult = withTimeout(config.processTimeoutMs) {
                        agent.processComm(comm, runtime)
                    }

                    // Handle response
                    responseResult.fold(
                        onSuccess = { response ->
                            // Send response if needed and it's different from the original
                            if (response.to != null && response != comm) {
                                send(response)
                            }
                            metrics.messageProcessed()
                        },
                        onFailure = { error ->
                            metrics.messageError()
                            // Send error response
                            if (comm.from != null) {
                                send(
                                    comm.error(
                                        "Processing failed: ${error.message}",
                                        agent.id
                                    )
                                )
                            }
                        }
                    )
                } catch (e: Exception) {
                    metrics.messageError()
                    
                    // Send error response
                    if (comm.from != null) {
                        send(
                            comm.error(
                                "Processing failed: ${e.message}",
                                agent.id
                            )
                        )
                    }
                }
            }
    }
    
    private fun createRuntime(agent: Agent): AgentRuntime {
        return object : AgentRuntime {
            override val context = AgentContext()
            override val scope = this@PluggableCommHub.scope
            
            override suspend fun callAgent(agentId: String, comm: Comm): SpiceResult<Comm> {
                val targetAgent = agents[agentId]
                    ?: return SpiceResult.failure(
                        io.github.noailabs.spice.error.SpiceError.AgentError(
                            message = "Agent not found: $agentId",
                            agentId = agentId
                        )
                    )
                return targetAgent.processComm(comm, this)
            }
            
            override suspend fun publishEvent(event: AgentEvent) {
                // Create event comm
                val eventComm = Comm(
                    from = agent.id,
                    to = null,
                    type = CommType.SYSTEM,
                    content = event.type
                ).withData("eventType", event.type)
                    .withData("eventData", event.data.toString())
                send(eventComm)
            }
            
            override fun log(level: LogLevel, message: String, data: Map<String, Any>) {
                // Implementation depends on logging framework
                println("[${level.name}] [${agent.id}] $message ${data.takeIf { it.isNotEmpty() } ?: ""}")
            }
            
            override suspend fun saveState(key: String, value: Any) {
                // Implementation depends on state store
            }
            
            override suspend fun getState(key: String): Any? {
                // Implementation depends on state store
                return null
            }
        }
    }
    
    private suspend fun applyMiddlewares(comm: Comm): Comm {
        return middlewares.fold(comm) { currentComm, middleware ->
            middleware.process(currentComm)
        }
    }
    
    private fun startMetricsCollection() {
        scope.launch {
            while (isActive) {
                delay(config.metricsIntervalMs)
                
                // Collect backend metrics
                val backendHealth = backend.health()
                metrics.updateBackendMetrics(
                    backendHealth.pendingMessages,
                    backendHealth.latencyMs ?: 0
                )
            }
        }
    }
}

/**
 * CommHub configuration
 */
data class CommHubConfig(
    val name: String = "default",
    val processTimeoutMs: Long = 30_000,
    val enableMetrics: Boolean = true,
    val metricsIntervalMs: Long = 10_000
)

/**
 * CommHub status
 */
data class CommHubStatus(
    val name: String,
    val healthy: Boolean,
    val registeredAgents: Int,
    val registeredTools: Int,
    val metrics: CommHubMetricsSnapshot,
    val backendHealth: BackendHealth
)

/**
 * Middleware interface
 */
interface CommMiddleware {
    suspend fun process(comm: Comm): Comm
}

/**
 * Metrics tracking
 */
class CommHubMetrics {
    private var agentsRegistered = 0L
    private var agentsUnregistered = 0L
    private var messagesSent = 0L
    private var messagesFailed = 0L
    private var messagesProcessed = 0L
    private var messageErrors = 0L
    private var lastBackendPendingMessages = 0
    private var lastBackendLatencyMs = 0L
    
    fun agentRegistered() { agentsRegistered++ }
    fun agentUnregistered() { agentsUnregistered++ }
    fun messageSent() { messagesSent++ }
    fun messageFailed() { messagesFailed++ }
    fun messageProcessed() { messagesProcessed++ }
    fun messageError() { messageErrors++ }
    
    fun updateBackendMetrics(pendingMessages: Int, latencyMs: Long) {
        lastBackendPendingMessages = pendingMessages
        lastBackendLatencyMs = latencyMs
    }
    
    fun snapshot(): CommHubMetricsSnapshot {
        return CommHubMetricsSnapshot(
            agentsRegistered = agentsRegistered,
            agentsUnregistered = agentsUnregistered,
            messagesSent = messagesSent,
            messagesFailed = messagesFailed,
            messagesProcessed = messagesProcessed,
            messageErrors = messageErrors,
            backendPendingMessages = lastBackendPendingMessages,
            backendLatencyMs = lastBackendLatencyMs
        )
    }
}

/**
 * Metrics snapshot
 */
data class CommHubMetricsSnapshot(
    val agentsRegistered: Long,
    val agentsUnregistered: Long,
    val messagesSent: Long,
    val messagesFailed: Long,
    val messagesProcessed: Long,
    val messageErrors: Long,
    val backendPendingMessages: Int,
    val backendLatencyMs: Long
)

/**
 * Factory for creating CommHub instances
 */
object CommHubFactory {
    private val backendFactories = mutableListOf<CommBackendFactory>(
        InMemoryBackendFactory()
    )
    
    /**
     * Register a backend factory
     */
    fun registerBackendFactory(factory: CommBackendFactory) {
        backendFactories.add(factory)
    }
    
    /**
     * Create a CommHub with the specified backend
     */
    fun create(
        backendType: String = "in-memory",
        backendConfig: Map<String, Any> = emptyMap(),
        hubConfig: CommHubConfig = CommHubConfig()
    ): PluggableCommHub {
        val factory = backendFactories.find { it.supports(backendType) }
            ?: throw IllegalArgumentException("Unsupported backend type: $backendType")
        
        val config = object : BackendConfig {
            override val name = backendType
            override val properties = backendConfig
        }
        
        val backend = factory.create(config)
        return PluggableCommHub(backend, hubConfig)
    }
}