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
interface Agent {
    val id: String
    val name: String
    val description: String
    val capabilities: List<String>

    /**
     * Process a message and return a response
     *
     * @param message Input message
     * @return Response message or error
     */
    suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage>

    /**
     * Process a message with runtime context
     *
     * @param message Input message
     * @param runtime Agent runtime providing access to system services
     * @return Response message or error
     */
    suspend fun processMessage(message: SpiceMessage, runtime: AgentRuntime): SpiceResult<SpiceMessage> {
        return processMessage(message)
    }

    /**
     * Check if this agent can handle the given message
     *
     * @param message Message to check
     * @return True if agent can process this message
     */
    fun canHandle(message: SpiceMessage): Boolean = true

    /**
     * Get tools available to this agent
     *
     * @return List of tools
     */
    fun getTools(): List<Tool> = emptyList()

    /**
     * Check if agent is ready to process messages
     *
     * @return True if ready
     */
    fun isReady(): Boolean = true
}

/**
 * 🏗️ Base Agent implementation with common functionality
 *
 * Provides default implementations for tool management and readiness checks.
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

    /**
     * Add a tool to this agent
     */
    fun addTool(tool: Tool) {
        _tools.add(tool)
    }

    override fun getTools(): List<Tool> = _tools.toList()

    override fun canHandle(message: SpiceMessage): Boolean = true

    override fun isReady(): Boolean = true
}

/**
 * ⚙️ Agent Configuration
 *
 * @property timeout Maximum execution time
 * @property retryPolicy Retry policy for failed executions
 * @since 1.0.0
 */
data class AgentConfig(
    val timeout: kotlin.time.Duration? = null,
    val retryPolicy: RetryPolicy = RetryPolicy()
)

/**
 * 🔄 Retry Policy
 *
 * @property maxRetries Maximum number of retries
 * @property backoffMultiplier Multiplier for exponential backoff
 * @property initialDelay Initial delay before first retry
 * @since 1.0.0
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val backoffMultiplier: Double = 2.0,
    val initialDelay: kotlin.time.Duration = kotlin.time.Duration.parse("1s")
)

/**
 * 🏃 Agent Runtime
 *
 * Provides access to system services during agent execution.
 *
 * @since 1.0.0
 */
interface AgentRuntime {
    /**
     * Call another agent
     */
    suspend fun callAgent(agentId: String, message: SpiceMessage): SpiceResult<SpiceMessage>

    /**
     * Publish an event
     */
    suspend fun publishEvent(topic: String, message: SpiceMessage): SpiceResult<Unit>

    /**
     * Get system logger
     */
    fun getLogger(name: String): Any? = null
}
