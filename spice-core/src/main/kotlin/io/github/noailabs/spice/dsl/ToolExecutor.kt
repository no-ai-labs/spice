package io.github.noailabs.spice.dsl

import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.ToolRegistry
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.coroutineContext

/**
 * 🔧 Ultimate Tool Executor with Coroutine Context
 * 
 * Kotlin coroutine-based tool execution system with safe isolation
 */

/**
 * Tool context for coroutine-based isolation
 */
data class ToolContext(
    val tools: Map<String, Tool>,
    val namespace: String = "agent",
    val parent: ToolContext? = null
) : AbstractCoroutineContextElement(ToolContext) {
    
    companion object Key : CoroutineContext.Key<ToolContext>
    
    /**
     * Find tool in current context or parent contexts
     */
    fun findTool(name: String): Tool? {
        return tools[name] ?: parent?.findTool(name)
    }
    
    /**
     * Get all available tool names
     */
    fun getAvailableTools(): Set<String> {
        val allTools = mutableSetOf<String>()
        allTools.addAll(tools.keys)
        parent?.let { allTools.addAll(it.getAvailableTools()) }
        return allTools
    }
    
    /**
     * Create child context with additional tools
     */
    fun withTools(additionalTools: Map<String, Tool>, childNamespace: String = namespace): ToolContext {
        return ToolContext(
            tools = additionalTools,
            namespace = childNamespace,
            parent = this
        )
    }
}

/**
 * Tool execution DSL with sugar functions
 */
class ToolDSL(
    private val agentId: String,
    private val currentMessage: Comm
) {

    /**
     * Get current AgentContext (if available)
     *
     * @since 0.4.0
     */
    suspend fun getContext(): io.github.noailabs.spice.AgentContext? {
        return coroutineContext[io.github.noailabs.spice.AgentContext]
    }

    /**
     * Get current AgentContext or throw exception
     *
     * @since 0.4.0
     * @throws IllegalStateException if no AgentContext in coroutine scope
     */
    suspend fun requireContext(): io.github.noailabs.spice.AgentContext {
        return coroutineContext[io.github.noailabs.spice.AgentContext]
            ?: throw IllegalStateException("No AgentContext in coroutine scope")
    }

    /**
     * Execute tool in current context
     */
    suspend fun runTool(name: String, params: Map<String, Any> = emptyMap()): Any? {
        val context = coroutineContext[ToolContext]
            ?: return "[Error] No tool context available. Use withToolContext { } block."
        
        val tool = context.findTool(name)
            ?: return "[Error] Tool '$name' not found. Available: ${context.getAvailableTools().joinToString(", ")}"
        
        return try {
            if (!tool.canExecute(params)) {
                "[Error] Invalid parameters for tool '$name'"
            } else {
                val result = tool.execute(params)
                result.fold(
                    onSuccess = { toolResult -> toolResult.result ?: toolResult.error },
                    onFailure = { error -> "[Error] ${error.message}" }
                )
            }
        } catch (e: Exception) {
            "[Error] Tool '$name' execution failed: ${e.message}"
        }
    }
    
    /**
     * Execute tool or throw exception on failure
     */
    suspend fun runToolOrError(name: String, params: Map<String, Any> = emptyMap()): Any {
        val result = runTool(name, params)
        if (result is String && result.startsWith("[Error]")) {
            throw ToolExecutionException(result)
        }
        return result ?: throw ToolExecutionException("Tool '$name' returned null")
    }
    
    /**
     * Execute tool and return result with metadata
     */
    suspend fun runToolWithMetadata(name: String, params: Map<String, Any> = emptyMap()): ToolResultWithMeta {
        val context = coroutineContext[ToolContext]
            ?: return ToolResultWithMeta(null, "[Error] No tool context", emptyMap())
        
        val tool = context.findTool(name)
            ?: return ToolResultWithMeta(null, "[Error] Tool '$name' not found", emptyMap())
        
        return try {
            val result = tool.execute(params)
            result.fold(
                onSuccess = { toolResult ->
                    ToolResultWithMeta(
                        result = toolResult.result,
                        error = toolResult.error,
                        metadata = toolResult.metadata + mapOf(
                            "tool_name" to name,
                            "execution_time" to System.currentTimeMillis().toString(),
                            "success" to toolResult.success.toString()
                        )
                    )
                },
                onFailure = { error ->
                    ToolResultWithMeta(null, "Execution failed: ${error.message}", emptyMap())
                }
            )
        } catch (e: Exception) {
            ToolResultWithMeta(null, "Tool execution failed: ${e.message}", emptyMap())
        }
    }
    
    /**
     * Create response comm (sugar for Comm.reply)
     */
    fun respond(content: String, data: Map<String, String> = emptyMap()): Comm {
        return currentMessage.reply(
            content = content,
            from = agentId,
            data = data
        )
    }
    
    /**
     * Debug logging with agent prefix
     */
    fun debug(message: String) {
        println("[$agentId] $message")
    }
    
    /**
     * Check if tool exists in current context
     */
    suspend fun toolExists(name: String): Boolean {
        val context = coroutineContext[ToolContext] ?: return false
        return context.findTool(name) != null
    }
    
    /**
     * Get all available tools in current context
     */
    suspend fun getAvailableTools(): Set<String> {
        val context = coroutineContext[ToolContext] ?: return emptySet()
        return context.getAvailableTools()
    }
}

/**
 * Tool execution with context isolation
 */
suspend fun <T> withToolContext(
    tools: List<Tool>,
    namespace: String = "agent",
    agentId: String = "unknown",
    currentMessage: Comm,
    block: suspend ToolDSL.() -> T
): T {
    val toolContext = ToolContext(
        tools = tools.associateBy { it.name },
        namespace = namespace
    )
    
    val dsl = ToolDSL(agentId, currentMessage)
    
    return withContext(toolContext) {
        dsl.block()
    }
}

/**
 * Advanced tool execution with parent context
 */
suspend fun <T> withToolContext(
    tools: List<Tool>,
    namespace: String = "agent",
    agentId: String = "unknown", 
    currentMessage: Comm,
    parentContext: ToolContext? = null,
    block: suspend ToolDSL.() -> T
): T {
    val toolContext = if (parentContext != null) {
        parentContext.withTools(tools.associateBy { it.name }, namespace)
    } else {
        ToolContext(tools.associateBy { it.name }, namespace)
    }
    
    val dsl = ToolDSL(agentId, currentMessage)
    
    return withContext(toolContext) {
        dsl.block()
    }
}

/**
 * Tool result with metadata
 */
data class ToolResultWithMeta(
    val result: Any?,
    val error: String?,
    val metadata: Map<String, String>
)

/**
 * Tool execution exception
 */
class ToolExecutionException(message: String) : Exception(message)

/**
 * Global tool executor (for backwards compatibility)
 */
object ToolExecutor {
    
    /**
     * Execute tool from global registry
     */
    suspend fun runTool(name: String, parameters: Map<String, Any> = emptyMap()): Any? {
        val tool = ToolRegistry.getTool(name)
            ?: return "[Error] Tool '$name' not found in global registry"

        return try {
            val result = tool.execute(parameters)
            result.fold(
                onSuccess = { toolResult -> toolResult.result ?: toolResult.error },
                onFailure = { error -> "[Error] ${error.message}" }
            )
        } catch (e: Exception) {
            "[Error] Global tool execution failed: ${e.message}"
        }
    }
    
    /**
     * Check if tool exists in global registry
     */
    fun toolExists(name: String): Boolean {
        return ToolRegistry.getTool(name) != null
    }
    
    /**
     * Get all available tools from global registry
     */
    fun getAvailableTools(): List<String> {
        return ToolRegistry.getAll().map { it.id }
    }
} 