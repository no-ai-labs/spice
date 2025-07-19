package io.github.spice

import kotlinx.coroutines.flow.Flow

/**
 * 🌶️ Core Agent interface of Spice Framework
 * Defines the basic contract for all Agent implementations
 */
interface Agent : Identifiable {
    override val id: String
    val name: String
    val description: String
    val capabilities: List<String>
    
    /**
     * Process incoming comm and return response
     */
    suspend fun processComm(comm: Comm): Comm
    
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
     * Get VectorStore by name (if configured)
     */
    fun getVectorStore(name: String): VectorStore? = null
    
    /**
     * Get all VectorStores configured for this agent
     */
    fun getVectorStores(): Map<String, VectorStore> = emptyMap()
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
    private val _vectorStores = mutableMapOf<String, VectorStore>()
    
    fun addTool(tool: Tool) {
        _tools.add(tool)
    }
    
    fun addVectorStore(name: String, store: VectorStore) {
        _vectorStores[name] = store
    }
    
    override fun getTools(): List<Tool> = _tools.toList()
    
    override fun getVectorStore(name: String): VectorStore? = _vectorStores[name]
    
    override fun getVectorStores(): Map<String, VectorStore> = _vectorStores.toMap()
    
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