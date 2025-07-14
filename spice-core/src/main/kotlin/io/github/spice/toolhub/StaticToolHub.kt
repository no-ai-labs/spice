package io.github.spice.toolhub

import io.github.spice.Tool
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 🏗️ StaticToolHub - Static tool hub implementation
 * 
 * Basic ToolHub implementation that manages a fixed set of tools.
 * 
 * Usage scenarios:
 * - Providing tools for LLM-based Agents
 * - When tool configuration is fixed and no separate state management is needed
 * - Example: FileTool, WebSearchTool, NotionTool, etc.
 */
class StaticToolHub(
    private val tools: List<Tool>
) : ToolHub {
    
    private val toolMap = tools.associateBy { it.name }
    private val executionMutex = Mutex()
    private val globalState = ConcurrentHashMap<String, Any>()
    private var isStarted = false
    
    /**
     * 🔧 Get tool list
     */
    override suspend fun listTools(): List<Tool> = tools
    
    /**
     * 🚀 Execute tool
     */
    override suspend fun callTool(
        name: String,
        parameters: Map<String, Any>,
        context: ToolContext
    ): ToolResult {
        if (!isStarted) {
            return ToolResult.error("ToolHub not started. Call start() first.")
        }
        
        val tool = toolMap[name]
            ?: return ToolResult.error("Tool '$name' not found. Available tools: ${toolMap.keys}")
        
        return executionMutex.withLock {
            val startTime = System.currentTimeMillis()
            
            try {
                // Pre-execution validation
                if (!tool.canExecute(parameters)) {
                    return@withLock ToolResult.error("Tool '$name' cannot execute with provided parameters")
                }
                
                // Execute tool
                val result = tool.execute(parameters)
                val executionTime = System.currentTimeMillis() - startTime
                
                // Record execution log
                val executionLog = ToolExecutionLog(
                    toolName = name,
                    parameters = parameters,
                    success = result.success,
                    result = result,
                    executionTimeMs = executionTime
                )
                context.addExecutionLog(executionLog)
                
                // Store result in context on success
                if (result.success) {
                    context.setMetadata("${name}_result", result)
                    context.setMetadata("${name}_execution_time", executionTime)
                }
                
                return@withLock result
                
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                val errorResult = ToolResult.error("Tool execution failed: ${e.message}")
                
                // Record execution log for error
                val executionLog = ToolExecutionLog(
                    toolName = name,
                    parameters = parameters,
                    success = false,
                    result = errorResult,
                    executionTimeMs = executionTime
                )
                context.addExecutionLog(executionLog)
                
                return@withLock errorResult
            }
        }
    }
    
    /**
     * 🏁 Start ToolHub
     */
    override suspend fun start() {
        if (isStarted) {
            return
        }
        
        // Initialize tools
        tools.forEach { tool ->
            try {
                tool.initialize()
            } catch (e: Exception) {
                println("Warning: Failed to initialize tool '${tool.name}': ${e.message}")
            }
        }
        
        isStarted = true
        println("StaticToolHub started with ${tools.size} tools")
    }
    
    /**
     * 🛑 Stop ToolHub
     */
    override suspend fun stop() {
        if (!isStarted) {
            return
        }
        
        // Clean up state
        globalState.clear()
        
        isStarted = false
        println("StaticToolHub stopped")
    }
    
    /**
     * 🔄 Reset ToolHub state
     */
    override suspend fun reset() {
        globalState.clear()
        println("StaticToolHub state reset")
    }
    
    /**
     * 💾 Save current state
     */
    override suspend fun saveState(): Map<String, Any> {
        return mapOf(
            "started" to isStarted,
            "tools" to tools.map { it.name },
            "globalState" to globalState.toMap()
        )
    }
    
    /**
     * 📂 Load saved state
     */
    override suspend fun loadState(state: Map<String, Any>) {
        // Restore global state
        globalState.clear()
        (state["globalState"] as? Map<String, Any>)?.let { savedState ->
            globalState.putAll(savedState)
        }
        
        // Restore started state
        isStarted = state["started"] as? Boolean ?: false
        
        println("StaticToolHub state loaded")
    }
    
    /**
     * 📊 Get tool execution statistics
     */
    fun getExecutionStats(context: ToolContext): Map<String, Any> {
        val executionHistory = context.executionHistory
        
        // Total execution count
        val totalExecutions = executionHistory.size
        
        // Per-tool execution count
        val toolExecutionCount = executionHistory.groupingBy { it.toolName }.eachCount()
        
        // Per-tool average execution time
        val toolAverageTime = executionHistory
            .groupBy { it.toolName }
            .mapValues { (_, logs) ->
                logs.map { it.executionTimeMs }.average()
            }
        
        // Success rate
        val successfulExecutions = executionHistory.count { it.success }
        val successRate = if (totalExecutions > 0) {
            (successfulExecutions.toDouble() / totalExecutions) * 100
        } else {
            0.0
        }
        
        return mapOf(
            "totalExecutions" to totalExecutions,
            "successfulExecutions" to successfulExecutions,
            "successRate" to successRate,
            "toolExecutionCount" to toolExecutionCount,
            "toolAverageTime" to toolAverageTime
        )
    }
    
    /**
     * 🔍 Search for tools
     */
    fun findTools(query: String): List<Tool> {
        return tools.filter { it.name.contains(query, ignoreCase = true) }
    }
    
    /**
     * 📋 Get tool names list
     */
    fun getToolNames(): List<String> = tools.map { it.name }
    
    /**
     * ✅ Check if tool exists
     */
    fun hasTools(toolNames: List<String>): Boolean {
        return toolNames.all { toolName -> toolMap.containsKey(toolName) }
    }
    
    /**
     * 🗂️ Get tool by name
     */
    fun getTool(name: String): Tool? = toolMap[name]
    
    /**
     * 📈 Get ToolHub status
     */
    fun getStatus(): Map<String, Any> {
        return mapOf(
            "started" to isStarted,
            "toolCount" to tools.size,
            "toolNames" to getToolNames(),
            "globalStateSize" to globalState.size
        )
    }
    
    /**
     * 🔧 Check if ToolHub is started
     */
    fun isStarted(): Boolean = isStarted
    
    /**
     * StaticToolHub builder
     */
    class Builder {
        private val tools = mutableListOf<Tool>()
        
        /**
         * Add tool to list
         */
        fun addTool(tool: Tool): Builder {
            tools.add(tool)
            return this
        }
        
        /**
         * Build StaticToolHub
         */
        fun build(): StaticToolHub {
            return StaticToolHub(tools.toList())
        }
    }
    
    companion object {
        /**
         * Create builder
         */
        fun builder(): Builder = Builder()
    }
}

/**
 * 🔧 Convenience function - Create StaticToolHub with tool list
 */
fun staticToolHub(tools: List<Tool>): StaticToolHub {
    return StaticToolHub(tools)
}

/**
 * 🔧 Convenience function - Create StaticToolHub with variable arguments
 */
fun staticToolHub(vararg tools: Tool): StaticToolHub {
    return StaticToolHub(tools.toList())
} 