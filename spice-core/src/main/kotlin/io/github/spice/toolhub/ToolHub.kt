package io.github.spice.toolhub

import io.github.spice.Tool

/**
 * 🧰 ToolHub - Integrated tool management system
 * 
 * Manages multiple Tools in an integrated manner, shares common state and resources,
 * and is designed to preserve execution history even in external execution environments like MCP.
 * 
 * Performs a similar role to Autogen's Workbench,
 * but integrates naturally with Spice's structure (Tool, ToolChain, etc).
 */
interface ToolHub {
    
    /**
     * Get list of all registered tools
     */
    suspend fun listTools(): List<Tool>
    
    /**
     * Execute tool
     * 
     * @param name tool name
     * @param parameters execution parameters
     * @param context execution context
     * @return execution result
     */
    suspend fun callTool(
        name: String,
        parameters: Map<String, Any>,
        context: ToolContext
    ): ToolResult
    
    /**
     * Start ToolHub (initialize resources)
     */
    suspend fun start()
    
    /**
     * Stop ToolHub (clean up resources)
     */
    suspend fun stop()
    
    /**
     * Reset ToolHub state
     */
    suspend fun reset()
    
    /**
     * Save current state
     */
    suspend fun saveState(): Map<String, Any>
    
    /**
     * Load saved state
     */
    suspend fun loadState(state: Map<String, Any>)
}

/**
 * 🗂️ ToolContext - Tool execution context
 * 
 * Manages metadata and execution history shared during tool execution.
 */
class ToolContext {
    
    /**
     * Execution metadata (shared state)
     */
    val metadata = mutableMapOf<String, Any>()
    
    /**
     * Execution history
     */
    val executionHistory = mutableListOf<ToolExecutionLog>()
    
    /**
     * Set metadata
     */
    fun setMetadata(key: String, value: Any) {
        metadata[key] = value
    }
    
    /**
     * Get metadata
     */
    fun getMetadata(key: String): Any? = metadata[key]
    
    /**
     * Add execution log
     */
    fun addExecutionLog(log: ToolExecutionLog) {
        executionHistory.add(log)
    }
    
    /**
     * Get last execution result
     */
    fun getLastResult(): ToolExecutionLog? = executionHistory.lastOrNull()
    
    /**
     * Get execution history for specific tool
     */
    fun getExecutionHistory(toolName: String): List<ToolExecutionLog> {
        return executionHistory.filter { it.toolName == toolName }
    }
}

/**
 * 📝 ToolExecutionLog - Tool execution log
 */
data class ToolExecutionLog(
    val toolName: String,
    val parameters: Map<String, Any>,
    val success: Boolean,
    val result: ToolResult,
    val executionTimeMs: Long
) {
    
    /**
     * Execution success status
     */
    val isSuccessful: Boolean = success
    
    /**
     * Summary information including execution time
     */
    val summary: String = if (success) {
        "✅ $toolName completed in ${executionTimeMs}ms"
    } else {
        "❌ $toolName failed in ${executionTimeMs}ms"
    }
}

/**
 * 🔄 ToolResult - Enhanced tool execution result
 * 
 * Extends the existing ToolResult sealed class to provide clearer result types.
 */
sealed class ToolResult {
    abstract val success: Boolean
    abstract val metadata: Map<String, Any>
    
    /**
     * Success result
     */
    data class Success(
        val result: String,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ToolResult() {
        override val success: Boolean = true
        
        // Properties for compatibility with existing ToolResult
        val output: String = result
        val error: String? = null
    }
    
    /**
     * Failure result
     */
    data class Error(
        val error: String,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ToolResult() {
        override val success: Boolean = false
        
        // Properties for compatibility with existing ToolResult
        val output: String? = null
        val message: String = error
    }
    
    /**
     * Retry request
     */
    data class Retry(
        val reason: String,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ToolResult() {
        override val success: Boolean = false
        
        // Properties for compatibility with existing ToolResult
        val output: String? = null
        val error: String = reason
    }
    
    companion object {
        /**
         * Create success result
         */
        fun success(
            result: String,
            metadata: Map<String, Any> = emptyMap()
        ): Success {
            return Success(result, metadata)
        }
        
        /**
         * Create error result
         */
        fun error(
            error: String,
            metadata: Map<String, Any> = emptyMap()
        ): Error {
            return Error(error, metadata)
        }
        
        /**
         * Create retry result
         */
        fun retry(
            reason: String,
            metadata: Map<String, Any> = emptyMap()
        ): Retry {
            return Retry(reason, metadata)
        }
    }
}

/**
 * 🔧 Extension function for compatibility with existing ToolResult
 */
fun ToolResult.toLegacyResult(): io.github.spice.ToolResult {
    return when (this) {
        is ToolResult.Success -> io.github.spice.ToolResult.Success(this.result)
        is ToolResult.Error -> io.github.spice.ToolResult.Error(this.error)
        is ToolResult.Retry -> io.github.spice.ToolResult.Retry(this.reason)
    }
}

/**
 * 🔄 Convert Enhanced ToolResult to existing ToolResult
 */
fun io.github.spice.ToolResult.toEnhancedResult(): ToolResult {
    return when (this) {
        is io.github.spice.ToolResult.Success -> ToolResult.success(this.output)
        is io.github.spice.ToolResult.Error -> ToolResult.error(this.message)
        is io.github.spice.ToolResult.Retry -> ToolResult.retry(this.reason)
    }
} 