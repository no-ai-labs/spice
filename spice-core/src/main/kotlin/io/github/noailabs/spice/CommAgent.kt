package io.github.noailabs.spice

import io.github.noailabs.spice.error.SpiceResult

/**
 * Base implementation of Agent that processes Comm messages
 */
abstract class CommAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    override val capabilities: List<String> = emptyList()
) : Agent {
    
    private val tools = mutableListOf<Tool>()
    
    /**
     * Add a tool to this agent
     */
    fun addTool(tool: Tool) {
        tools.add(tool)
    }
    
    override fun getTools(): List<Tool> = tools.toList()
    
    override fun canHandle(comm: Comm): Boolean {
        return when (comm.type) {
            CommType.TEXT, CommType.PROMPT -> true
            CommType.TOOL_CALL -> tools.any { it.name == comm.getToolName() }
            else -> true // Let agent decide in processComm
        }
    }
    
    override fun isReady(): Boolean = true
    
    /**
     * Execute a tool by name
     */
    protected suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val tool = tools.find { it.name == toolName }
            ?: return ToolResult.error("Tool not found: $toolName")

        return if (tool.canExecute(parameters)) {
            tool.execute(parameters).fold(
                onSuccess = { result -> result },
                onFailure = { error -> ToolResult.error(error.message ?: "Execution failed") }
            )
        } else {
            ToolResult.error("Tool execution conditions not met: $toolName")
        }
    }
}

/**
 * Create a SmartAgent that processes Message via Comm
 */
fun createCommBasedAgent(
    id: String = "smart-${System.currentTimeMillis()}",
    name: String,
    handler: suspend (Comm) -> Comm
): SmartAgent {
    return smartAgent(id) {
        this.name = name
        
        // Add a tool that processes comms
        tool("process") { params ->
            val comm = params["comm"] as? Comm ?: return@tool "No comm provided"
            handler(comm)
        }
    }
} 