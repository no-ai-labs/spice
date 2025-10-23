package io.github.noailabs.spice

import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 🌶️ Core Agent interface of Spice Framework
 * Defines the basic contract for all Agent implementations
 *
 * @since 0.2.0 - processComm now returns SpiceResult<Comm> for type-safe error handling
 */
interface Agent : Identifiable {
    override val id: String
    val name: String
    val description: String
    val capabilities: List<String>

    /**
     * Process incoming comm and return response
     *
     * @return SpiceResult<Comm> - Success with response or Failure with error
     */
    suspend fun processComm(comm: Comm): SpiceResult<Comm>

    /**
     * Process with runtime context
     *
     * @return SpiceResult<Comm> - Success with response or Failure with error
     */
    suspend fun processComm(comm: Comm, runtime: AgentRuntime): SpiceResult<Comm> = processComm(comm)
    
    /**
     * Check if this Agent can handle the given comm
     */
    fun canHandle(comm: Comm): Boolean
    
    /**
     * Get Tools available to this Agent
     */
    fun getTools(): List<Tool>
    
    /**
     * Check if Agent is ready for operation
     */
    fun isReady(): Boolean
    
    /**
     * Get configuration
     */
    fun getConfig(): AgentConfig = AgentConfig()
    
    /**
     * Initialize agent with runtime
     */
    suspend fun initialize(runtime: AgentRuntime) {}
    
    /**
     * Cleanup resources
     */
    suspend fun cleanup() {}
    
    /**
     * Get VectorStore by name (if configured)
     */
    fun getVectorStore(name: String): VectorStore? = null
    
    /**
     * Get all VectorStores configured for this agent
     */
    fun getVectorStores(): Map<String, VectorStore> = emptyMap()
    
    /**
     * Get agent metrics
     */
    fun getMetrics(): AgentMetrics = AgentMetrics()
}

/**
 * 🔧 Base Agent implementation providing common functionality
 */
abstract class BaseAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    override val capabilities: List<String> = emptyList(),
    private val config: AgentConfig = AgentConfig()
) : Agent {
    
    private val _tools = mutableListOf<Tool>()
    private val _vectorStores = mutableMapOf<String, VectorStore>()
    private var runtime: AgentRuntime? = null
    private val metrics = AgentMetrics()
    
    fun addTool(tool: Tool) {
        _tools.add(tool)
    }
    
    fun addVectorStore(name: String, store: VectorStore) {
        _vectorStores[name] = store
    }
    
    override fun getTools(): List<Tool> = _tools.toList()
    
    override fun getVectorStore(name: String): VectorStore? = _vectorStores[name]
    
    override fun getVectorStores(): Map<String, VectorStore> = _vectorStores.toMap()
    
    override fun getConfig(): AgentConfig = config
    
    override fun getMetrics(): AgentMetrics = metrics
    
    override suspend fun initialize(runtime: AgentRuntime) {
        this.runtime = runtime

        // Call lifecycle callbacks if agent implements LifecycleAware
        if (this is io.github.noailabs.spice.lifecycle.LifecycleAware) {
            this.onBeforeInit()
        }

        runtime.log(LogLevel.INFO, "Agent initialized: $id")

        if (this is io.github.noailabs.spice.lifecycle.LifecycleAware) {
            this.onAfterInit()
        }
    }
    
    override suspend fun processComm(comm: Comm, runtime: AgentRuntime): SpiceResult<Comm> {
        this.runtime = runtime
        metrics.recordRequest()

        val startTime = System.currentTimeMillis()

        // ✅ Inject AgentContext into CoroutineContext for automatic propagation
        return withContext(runtime.context) {
            processComm(comm)
                .onSuccess {
                    metrics.recordSuccess(System.currentTimeMillis() - startTime)
                }
                .onFailure { error ->
                    metrics.recordError()
                    runtime.log(LogLevel.ERROR, "Error processing comm", mapOf(
                        "error" to error.message,
                        "error_code" to error.code,
                        "commId" to comm.id
                    ))
                }
        }
    }
    
    override fun canHandle(comm: Comm): Boolean {
        return when (comm.type) {
            CommType.TEXT, CommType.PROMPT, CommType.SYSTEM, 
            CommType.WORKFLOW_START, CommType.WORKFLOW_END -> true
            CommType.TOOL_CALL -> _tools.any { tool -> tool.name == comm.getToolName() }
            else -> false
        }
    }
    
    override fun isReady(): Boolean = true
    
    /**
     * Execute Tool by name
     */
    protected suspend fun executeTool(toolName: String, parameters: Map<String, Any>): SpiceResult<ToolResult> {
        val tool = _tools.find { tool -> tool.name == toolName }
            ?: return SpiceResult.success(ToolResult.error("Tool not found: $toolName"))

        metrics.recordToolCall(toolName)

        return try {
            if (tool.canExecute(parameters)) {
                val result = tool.execute(parameters)
                result.onSuccess { toolResult ->
                    if (toolResult.success) metrics.recordToolSuccess(toolName)
                    else metrics.recordToolError(toolName)
                }.onFailure {
                    metrics.recordToolError(toolName)
                }
                result
            } else {
                metrics.recordToolError(toolName)
                SpiceResult.success(ToolResult.error("Tool execution conditions not met: $toolName"))
            }
        } catch (e: Exception) {
            metrics.recordToolError(toolName)
            SpiceResult.success(ToolResult.error("Tool execution failed: ${e.message}"))
        }
    }
    
    /**
     * Log a message through runtime
     */
    protected fun log(level: LogLevel, message: String, data: Map<String, Any> = emptyMap()) {
        runtime?.log(level, "[$id] $message", data)
    }
    
    /**
     * Call another agent
     */
    protected suspend fun callAgent(agentId: String, comm: Comm): SpiceResult<Comm>? {
        return runtime?.callAgent(agentId, comm)
    }
    
    /**
     * Publish an event
     */
    protected suspend fun publishEvent(type: String, data: Map<String, Any> = emptyMap()) {
        runtime?.publishEvent(AgentEvent(type, id, data))
    }
    
    /**
     * Save state
     */
    protected suspend fun saveState(key: String, value: Any) {
        runtime?.saveState("$id:$key", value)
    }
    
    /**
     * Get state
     */
    protected suspend fun getState(key: String): Any? {
        return runtime?.getState("$id:$key")
    }
}

/**
 * 📊 Agent Metrics
 */
data class AgentMetrics(
    var totalRequests: Long = 0,
    var successfulRequests: Long = 0,
    var failedRequests: Long = 0,
    var totalResponseTimeMs: Long = 0,
    val toolMetrics: MutableMap<String, ToolMetrics> = mutableMapOf()
) {
    fun recordRequest() {
        totalRequests++
    }
    
    fun recordSuccess(responseTimeMs: Long) {
        successfulRequests++
        totalResponseTimeMs += responseTimeMs
    }
    
    fun recordError() {
        failedRequests++
    }
    
    fun recordToolCall(toolName: String) {
        toolMetrics.getOrPut(toolName) { ToolMetrics() }.totalCalls++
    }
    
    fun recordToolSuccess(toolName: String) {
        toolMetrics.getOrPut(toolName) { ToolMetrics() }.successfulCalls++
    }
    
    fun recordToolError(toolName: String) {
        toolMetrics.getOrPut(toolName) { ToolMetrics() }.failedCalls++
    }
    
    fun getAverageResponseTimeMs(): Double {
        return if (successfulRequests > 0) {
            totalResponseTimeMs.toDouble() / successfulRequests
        } else 0.0
    }
    
    fun getSuccessRate(): Double {
        return if (totalRequests > 0) {
            successfulRequests.toDouble() / totalRequests
        } else 0.0
    }
}

/**
 * Tool-specific metrics
 */
data class ToolMetrics(
    var totalCalls: Long = 0,
    var successfulCalls: Long = 0,
    var failedCalls: Long = 0
) 