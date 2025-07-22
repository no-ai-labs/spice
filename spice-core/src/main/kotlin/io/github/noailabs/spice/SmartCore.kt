package io.github.noailabs.spice

import kotlinx.coroutines.*
import kotlinx.serialization.*
import java.util.*
import java.time.Instant

/**
 * 🚀 Smart Core - Next Generation Agent System
 * 
 * Clean, modern architecture for intelligent agents:
 * - Smart agents with built-in capabilities
 * - External tool integration via MCP
 * - Coroutine-first design
 * - Type-safe builders
 * - Zero legacy baggage
 */

// =====================================
// SMART AGENTS
// =====================================

/**
 * 🤖 Smart Agent - Intelligent agent with enhanced capabilities
 */
data class SmartAgent(
    val id: String,
    val name: String,
    val role: String = "",
    val capabilities: Set<String> = emptySet(),
    val tools: Map<String, suspend (Map<String, Any>) -> Any> = emptyMap(),
    val trustLevel: Double = 0.7,
    val active: Boolean = true
) {
    /**
     * Send a message via CommHub
     */
    suspend fun send(comm: Comm): CommResult = 
        CommHub.send(comm.copy(from = id))
    
    /**
     * Broadcast to multiple recipients
     */
    suspend fun broadcast(content: String, to: List<String>): List<CommResult> = 
        to.map { send(Comm(content = content, from = id, to = it)) }
    
    /**
     * Use a tool
     */
    suspend fun useTool(toolName: String, params: Map<String, Any>): Any? = 
        tools[toolName]?.invoke(params)
    
    /**
     * Add a tool to this agent
     */
    fun withTool(name: String, executor: suspend (Map<String, Any>) -> Any): SmartAgent =
        copy(tools = tools + (name to executor))
    
    /**
     * Set trust level
     */
    fun trust(level: Double): SmartAgent = copy(trustLevel = level)
    
    /**
     * Add capabilities
     */
    fun addCapability(vararg caps: String): SmartAgent = 
        copy(capabilities = capabilities + caps.toSet())
    
    /**
     * Activate/deactivate agent
     */
    fun activate(): SmartAgent = copy(active = true)
    fun deactivate(): SmartAgent = copy(active = false)
}

// =====================================
// EXTERNAL TOOLS
// =====================================

/**
 * 🔧 External Tool via MCP
 */
data class ExternalTool(
    val id: String,
    val name: String,
    val endpoint: String,
    val apiKey: String = "",
    val capabilities: Set<String> = emptySet(),
    val timeout: Long = 30000,
    val trusted: Boolean = false
) {
    /**
     * Execute tool with parameters
     */
    suspend fun execute(params: Map<String, Any>): Phase4ToolResult = 
        MCPExecutor.execute(this, params)
    
    /**
     * Validate parameters before execution
     */
    fun validateParams(params: Map<String, Any>): ValidationResult {
        // Basic validation logic
        return ValidationResult(true, emptyList())
    }
}

/**
 * Tool execution result
 */
data class Phase4ToolResult(
    val success: Boolean,
    val result: Any? = null,
    val error: String? = null,
    val executionTime: Long = 0,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Parameter validation result
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

// =====================================
// MCP EXECUTOR
// =====================================

/**
 * 🔌 MCP (Model Context Protocol) Executor
 */
object MCPExecutor {
    private val executors = mutableMapOf<String, suspend (ExternalTool, Map<String, Any>) -> Phase4ToolResult>()
    
    /**
     * Register custom executor
     */
    fun registerExecutor(toolId: String, executor: suspend (ExternalTool, Map<String, Any>) -> Phase4ToolResult) {
        executors[toolId] = executor
    }
    
    /**
     * Execute external tool
     */
    suspend fun execute(tool: ExternalTool, params: Map<String, Any>): Phase4ToolResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Use custom executor if available
            val executor = executors[tool.id] ?: ::defaultExecutor
            
            withTimeout(tool.timeout) {
                executor(tool, params)
            }.copy(executionTime = System.currentTimeMillis() - startTime)
        } catch (e: TimeoutCancellationException) {
            Phase4ToolResult(
                success = false,
                error = "Tool execution timed out after ${tool.timeout}ms",
                executionTime = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Phase4ToolResult(
                success = false,
                error = "Tool execution failed: ${e.message}",
                executionTime = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * Default executor (mock implementation)
     */
    private suspend fun defaultExecutor(tool: ExternalTool, params: Map<String, Any>): Phase4ToolResult {
        // Simulate external call
        delay(100)
        
        return Phase4ToolResult(
            success = true,
            result = "Mock result for ${tool.name} with params: $params",
            metadata = mapOf("tool" to tool.name, "mock" to true)
        )
    }
}

// =====================================
// DSL BUILDERS
// =====================================

/**
 * Build a smart agent
 */
inline fun smartAgent(
    id: String,
    builder: SmartAgentBuilder.() -> Unit
): SmartAgent {
    val agentBuilder = SmartAgentBuilder(id)
    agentBuilder.builder()
    return agentBuilder.build()
}

/**
 * Smart agent builder
 */
class SmartAgentBuilder(private val id: String) {
    var name: String = id
    var role: String = ""
    private val capabilities = mutableSetOf<String>()
    private val tools = mutableMapOf<String, suspend (Map<String, Any>) -> Any>()
    var trustLevel: Double = 0.7
    var active: Boolean = true
    
    fun capability(vararg caps: String) {
        capabilities.addAll(caps)
    }
    
    fun tool(name: String, executor: suspend (Map<String, Any>) -> Any) {
        tools[name] = executor
    }
    
    fun trust(level: Double) {
        trustLevel = level
    }
    
    fun build(): SmartAgent = SmartAgent(
        id = id,
        name = name,
        role = role,
        capabilities = capabilities.toSet(),
        tools = tools.toMap(),
        trustLevel = trustLevel,
        active = active
    )
}

/**
 * Build an external tool
 */
inline fun externalTool(
    id: String,
    builder: ExternalToolBuilder.() -> Unit
): ExternalTool {
    val toolBuilder = ExternalToolBuilder(id)
    toolBuilder.builder()
    return toolBuilder.build()
}

/**
 * External tool builder
 */
class ExternalToolBuilder(private val id: String) {
    var name: String = id
    var endpoint: String = ""
    var apiKey: String = ""
    private val capabilities = mutableSetOf<String>()
    var timeout: Long = 30000
    var trusted: Boolean = false
    
    fun capability(vararg caps: String) {
        capabilities.addAll(caps)
    }
    
    fun build(): ExternalTool = ExternalTool(
        id = id,
        name = name,
        endpoint = endpoint,
        apiKey = apiKey,
        capabilities = capabilities.toSet(),
        timeout = timeout,
        trusted = trusted
    )
}