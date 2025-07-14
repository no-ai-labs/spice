package io.github.spice.toolhub

import io.github.spice.*

/**
 * 🤖 ToolHubAgent - Agent extension using ToolHub
 * 
 * Decorator pattern implementation that adds ToolHub features to existing Agents.
 * Provides enhanced tool management and execution capabilities.
 * 
 * Usage example:
 * ```kotlin
 * val baseAgent = UniversalAgent("enhanced-agent")
 * val toolHub = StaticToolHub.builder()
 *     .addTool(WebSearchTool())
 *     .addTool(FileReadTool())
 *     .build()
 * 
 * val enhancedAgent = ToolHubAgent(baseAgent, toolHub)
 * val response = enhancedAgent.receive(message)
 * ```
 */
class ToolHubAgent(
    private val baseAgent: Agent,
    private val toolHub: ToolHub
) : Agent by baseAgent {
    
    override val id: String = "${baseAgent.id}-toolhub"
    
    /**
     * 🔧 Add ToolHub tools to Agent's tool list
     */
    override fun getTools(): List<Tool> {
        val baseTools = baseAgent.getTools()
        val toolHubTools = toolHub.getTools()
        return baseTools + toolHubTools
    }
    
    /**
     * 💬 Support tool calls through ToolHub during message processing
     */
    override suspend fun receive(message: Message): Message {
        // Check if message contains tool call request
        if (isToolCallRequest(message)) {
            return processToolCall(message)
        }
        
        // Process with base agent
        val baseResponse = baseAgent.receive(message)
        
        // Check if response contains tool call
        if (isToolCallResponse(baseResponse)) {
            return processToolCall(baseResponse)
        }
        
        return baseResponse
    }
    
    /**
     * 🛠️ Process tool calls through ToolHub
     */
    private suspend fun processToolCall(message: Message): Message {
        try {
            // Extract tool parameters from message
            val toolRequest = extractToolParameters(message)
            
            // Check if tool exists in ToolHub
            if (!toolHub.hasTools(listOf(toolRequest.toolName))) {
                return Message(
                    sender = id,
                    content = "Tool '${toolRequest.toolName}' not found in ToolHub",
                    type = MessageType.ERROR
                )
            }
            
            // Execute tool through ToolHub
            val toolResult = toolHub.execute(
                name = toolRequest.toolName,
                parameters = toolRequest.parameters
            )
            
            // Convert result to message
            return Message(
                sender = id,
                content = if (toolResult.success) toolResult.result else toolResult.error,
                type = if (toolResult.success) MessageType.TOOL_RESULT else MessageType.ERROR,
                metadata = toolResult.metadata + mapOf(
                    "tool_name" to toolRequest.toolName,
                    "tool_success" to toolResult.success.toString()
                )
            )
            
        } catch (e: Exception) {
            return Message(
                sender = id,
                content = "Tool execution failed: ${e.message}",
                type = MessageType.ERROR
            )
        }
    }
    
    /**
     * 📤 Extract tool parameters from message
     */
    private fun extractToolParameters(message: Message): ToolRequest {
        // Simple implementation - can be enhanced with more sophisticated parsing
        val content = message.content
        val toolName = content.substringAfter("tool:").substringBefore(" ").trim()
        val parameters = mapOf("input" to content)
        
        return ToolRequest(toolName, parameters)
    }
    
    /**
     * 🔍 Check if ToolHub includes tools
     */
    private fun hasToolInToolHub(toolName: String): Boolean {
        // Check if tool can be called in ToolHub
        return toolHub.getTools().any { it.name == toolName }
    }
    
    /**
     * Check if message is a tool call request
     */
    private fun isToolCallRequest(message: Message): Boolean {
        return message.content.startsWith("tool:") || message.type == MessageType.TOOL_CALL
    }
    
    /**
     * Check if message is a tool call response
     */
    private fun isToolCallResponse(message: Message): Boolean {
        return message.type == MessageType.TOOL_CALL
    }
    
    /**
     * 📊 Get tool execution statistics
     */
    suspend fun getToolStatistics(): Map<String, Any> {
        return toolHub.getStatistics()
    }
    
    /**
     * Get tool context
     */
    suspend fun getToolContext(): ToolContext {
        return toolHub.getContext()
    }
    
    /**
     * 🗂️ Get tool context
     */
    fun getToolHub(): ToolHub {
        return toolHub
    }
}

/**
 * 🎯 Agent extension function - ToolHub integration
 */
suspend fun Agent.withToolHub(toolHub: ToolHub): ToolHubAgent {
    // Start ToolHub (if not already started)
    if (!toolHub.isStarted()) {
        toolHub.start()
    }
    
    return ToolHubAgent(this, toolHub)
}

/**
 * 🔧 BaseAgent extension function - ToolHub integration (more convenient usage)
 */
fun BaseAgent.enhanceWithToolHub(toolHub: ToolHub): ToolHubAgent {
    return ToolHubAgent(this, toolHub)
}

/**
 * Tool request data class
 */
data class ToolRequest(
    val toolName: String,
    val parameters: Map<String, Any>
) 