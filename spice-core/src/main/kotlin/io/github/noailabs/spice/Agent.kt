package io.github.noailabs.spice

import io.github.noailabs.spice.error.SpiceResult

/**
 * 🌶️ Core Agent interface of Spice Framework 1.0.0
 *
 * **BREAKING CHANGE from 0.x:**
 * - `processComm(comm: Comm)` → `processMessage(message: SpiceMessage)`
 * - Unified message format (no more Comm/NodeResult/Context split)
 * - Built-in state machine support
 * - Native tool calls integration
 *
 * **Migration Example:**
 * ```kotlin
 * // OLD (0.x)
 * override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
 *     val input = comm.content
 *     return SpiceResult.success(comm.reply("Response", id))
 * }
 *
 * // NEW (1.0)
 * override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
 *     val input = message.content
 *     return SpiceResult.success(message.reply("Response", id))
 * }
 * ```
 *
 * @since 1.0.0
 */
interface Agent : Identifiable {
    override val id: String
    val name: String
    val description: String
    val capabilities: List<String>

    /**
     * Process incoming message and return response
     *
     * **Core method** - All agents must implement this.
     *
     * **State Machine:**
     * - Input message state: typically RUNNING
     * - Output message state: RUNNING (continue), WAITING (HITL), COMPLETED (done)
     *
     * **Tool Calls:**
     * - Use `message.withToolCall(toolCall)` to add tool calls
     * - Tool calls are automatically propagated to next nodes
     *
     * @param message Input message with execution context
     * @return SpiceResult with response message or error
     */
    suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage>

    /**
     * Process with runtime context
     * Optional override for agents that need runtime access
     *
     * @param message Input message
     * @param runtime Agent runtime (LLM client, vector store, etc.)
     * @return SpiceResult with response message or error
     */
    suspend fun processMessage(message: SpiceMessage, runtime: AgentRuntime): SpiceResult<SpiceMessage> {
        return processMessage(message)
    }

    /**
     * Check if this Agent can handle the given message
     * Used for dynamic agent routing
     *
     * @param message Input message
     * @return true if agent can process this message
     */
    fun canHandle(message: SpiceMessage): Boolean

    /**
     * Get Tools available to this Agent
     * Used for LLM tool calling integration
     *
     * @return List of tools this agent can use
     */
    fun getTools(): List<Tool>

    /**
     * Check if Agent is ready for operation
     * Called before graph execution starts
     *
     * @return true if agent is initialized and ready
     */
    fun isReady(): Boolean

    /**
     * Get agent configuration
     * @return AgentConfig with LLM settings, timeouts, etc.
     */
    fun getConfig(): AgentConfig = AgentConfig()

    /**
     * Initialize agent with runtime
     * Called once during agent setup
     *
     * @param runtime Agent runtime with LLM client, vector store, etc.
     */
    suspend fun initialize(runtime: AgentRuntime) {}

    /**
     * Cleanup resources
     * Called during shutdown or agent disposal
     */
    suspend fun cleanup() {}

    /**
     * Get VectorStore by name (if configured)
     * @param name Vector store identifier
     * @return VectorStore instance or null
     */
    fun getVectorStore(name: String): VectorStore? = null

    /**
     * Get all VectorStores configured for this agent
     * @return Map of store name to VectorStore instance
     */
    fun getVectorStores(): Map<String, VectorStore> = emptyMap()

    /**
     * Get agent metrics
     * @return AgentMetrics with performance stats
     */
    fun getMetrics(): AgentMetrics = AgentMetrics()
}

/**
 * 🔧 Base Agent implementation providing common functionality
 *
 * Provides default implementations for:
 * - Tool management
 * - Vector store management
 * - Metrics tracking
 * - Runtime lifecycle
 *
 * **Usage:**
 * ```kotlin
 * class MyAgent : BaseAgent(
 *     id = "my-agent",
 *     name = "My Agent",
 *     description = "Does amazing things",
 *     capabilities = listOf("text_generation", "tool_calling")
 * ) {
 *     override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
 *         // Your logic here
 *         return SpiceResult.success(message.reply("Done", id))
 *     }
 *
 *     override fun canHandle(message: SpiceMessage): Boolean {
 *         return message.content.isNotBlank()
 *     }
 * }
 * ```
 *
 * @since 1.0.0
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

    /**
     * Add a tool to this agent
     * @param tool Tool to add
     */
    fun addTool(tool: Tool) {
        _tools.add(tool)
    }

    /**
     * Add a vector store to this agent
     * @param name Store identifier
     * @param store VectorStore instance
     */
    fun addVectorStore(name: String, store: VectorStore) {
        _vectorStores[name] = store
    }

    override fun getTools(): List<Tool> = _tools.toList()

    override fun getVectorStore(name: String): VectorStore? = _vectorStores[name]

    override fun getVectorStores(): Map<String, VectorStore> = _vectorStores.toMap()

    override fun getConfig(): AgentConfig = config

    override fun isReady(): Boolean {
        return runtime != null
    }

    override suspend fun initialize(runtime: AgentRuntime) {
        this.runtime = runtime
    }

    override suspend fun cleanup() {
        _tools.clear()
        _vectorStores.clear()
        runtime = null
    }

    override fun getMetrics(): AgentMetrics = metrics

    /**
     * Get current runtime
     * @return AgentRuntime or null if not initialized
     */
    protected fun getRuntime(): AgentRuntime? = runtime

    /**
     * Record successful message processing
     */
    protected fun recordSuccess() {
        metrics.incrementSuccess()
    }

    /**
     * Record failed message processing
     */
    protected fun recordFailure() {
        metrics.incrementFailure()
    }

    /**
     * Default canHandle implementation
     * Override for custom logic
     */
    override fun canHandle(message: SpiceMessage): Boolean {
        return true  // Accept all messages by default
    }
}

/**
 * 📊 Agent Configuration
 *
 * Configuration options for agent behavior
 *
 * @property timeout Maximum execution time for agent
 * @property retryPolicy Retry policy for failed executions
 * @property llmConfig LLM-specific configuration (temperature, model, etc.)
 */
data class AgentConfig(
    val timeout: kotlin.time.Duration = kotlin.time.Duration.parse("30s"),
    val retryPolicy: RetryPolicy = RetryPolicy.default(),
    val llmConfig: Map<String, Any> = emptyMap()
)

/**
 * 🔄 Retry Policy
 *
 * @property maxRetries Maximum number of retries
 * @property backoffStrategy Backoff strategy (EXPONENTIAL, LINEAR, FIXED)
 * @property initialDelay Initial delay before first retry
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val backoffStrategy: BackoffStrategy = BackoffStrategy.EXPONENTIAL,
    val initialDelay: kotlin.time.Duration = kotlin.time.Duration.parse("1s")
) {
    companion object {
        fun default() = RetryPolicy()
        fun noRetry() = RetryPolicy(maxRetries = 0)
    }
}

/**
 * Backoff strategy for retries
 */
enum class BackoffStrategy {
    EXPONENTIAL,  // 1s, 2s, 4s, 8s...
    LINEAR,       // 1s, 2s, 3s, 4s...
    FIXED         // 1s, 1s, 1s, 1s...
}

/**
 * 📈 Agent Metrics
 *
 * Performance metrics for agent execution
 */
data class AgentMetrics(
    private var successCount: Long = 0,
    private var failureCount: Long = 0,
    private var totalLatencyMs: Long = 0
) {
    fun incrementSuccess() {
        successCount++
    }

    fun incrementFailure() {
        failureCount++
    }

    fun recordLatency(latencyMs: Long) {
        totalLatencyMs += latencyMs
    }

    fun getSuccessRate(): Double {
        val total = successCount + failureCount
        return if (total > 0) (successCount.toDouble() / total) * 100 else 0.0
    }

    fun getAverageLatency(): Double {
        return if (successCount > 0) totalLatencyMs.toDouble() / successCount else 0.0
    }
}

/**
 * 🏃 Agent Runtime
 *
 * Runtime dependencies provided to agents
 * Placeholder for 1.0.0 - will be expanded in future versions
 */
interface AgentRuntime {
    // LLM client, vector store, etc. will be added here
}
