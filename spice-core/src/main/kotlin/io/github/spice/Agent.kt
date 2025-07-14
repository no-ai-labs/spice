package io.github.spice

import kotlinx.coroutines.flow.Flow

/**
 * 🌶️ Core Agent interface of Spice Framework
 * Defines the basic contract for all Agent implementations
 */
interface Agent {
    val id: String
    val name: String
    val description: String
    val capabilities: List<String>
    
    /**
     * Process incoming message and return response
     */
    suspend fun processMessage(message: Message): Message
    
    /**
     * Check if this Agent can handle the given message
     */
    fun canHandle(message: Message): Boolean
    
    /**
     * Get Tools available to this Agent
     */
    fun getTools(): List<Tool>
    
    /**
     * Check if Agent is ready for operation
     */
    fun isReady(): Boolean
}

/**
 * 🔧 Base Agent implementation providing common functionality
 */
abstract class BaseAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    override val capabilities: List<String> = emptyList()
) : Agent {
    
    private val _tools = mutableListOf<Tool>()
    
    fun addTool(tool: Tool) {
        _tools.add(tool)
    }
    
    override fun getTools(): List<Tool> = _tools.toList()
    
    override fun canHandle(message: Message): Boolean {
        return when (message.type) {
            MessageType.TEXT, MessageType.PROMPT, MessageType.SYSTEM, 
            MessageType.WORKFLOW_START, MessageType.WORKFLOW_END -> true
            MessageType.TOOL_CALL -> _tools.any { tool -> tool.name == message.metadata["toolName"] }
            else -> false
        }
    }
    
    override fun isReady(): Boolean = true
    
    /**
     * Execute Tool by name
     */
    protected suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val tool = _tools.find { tool -> tool.name == toolName }
            ?: return ToolResult.error("Tool not found: $toolName")
        
        return if (tool.canExecute(parameters)) {
            tool.execute(parameters)
        } else {
            ToolResult.error("Tool execution conditions not met: $toolName")
        }
    }
} 