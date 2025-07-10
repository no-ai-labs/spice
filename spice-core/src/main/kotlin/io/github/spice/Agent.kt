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

/**
 * 🎯 Prompt processing specialized Agent
 */
class PromptAgent(
    id: String = "prompt-agent",
    name: String = "Prompt Processing Agent",
    description: String = "Agent specialized in prompt processing and text generation"
) : BaseAgent(id, name, description, listOf("prompt_processing")) {
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            // Simple prompt processing (can be extended)
            val processedContent = "Processed: ${message.content}"
            
            message.createReply(
                content = processedContent,
                sender = id,
                type = MessageType.TEXT,
                metadata = mapOf<String, String>("status" to "success")
            )
        } catch (e: Exception) {
            message.createReply(
                content = "Prompt processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>("errorType" to "processing_error")
            )
        }
    }
}

/**
 * 📊 Data provision specialized Agent
 */
class DataAgent(
    id: String = "data-agent", 
    name: String = "Data Provision Agent",
    description: String = "Agent specialized in data provision and management"
) : BaseAgent(id, name, description, listOf("data_provision")) {
    
    override fun canHandle(message: Message): Boolean {
        return message.type in listOf(
            MessageType.DATA, MessageType.TEXT, MessageType.WORKFLOW_START, MessageType.WORKFLOW_END
        )
    }
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            // Data processing logic
            val data = "Data: ${message.content}"
            
            message.createReply(
                content = data,
                sender = id,
                type = MessageType.DATA,
                metadata = mapOf<String, String>("dataType" to (data::class.simpleName ?: "unknown"))
            )
        } catch (e: Exception) {
            message.createReply(
                content = "Data processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>("errorType" to "processing_error")
            )
        }
    }
}

/**
 * 📈 Result processing and visualization specialized Agent
 */
class ResultAgent(
    id: String = "result-agent",
    name: String = "Result Processing Agent", 
    description: String = "Agent specialized in result processing and visualization"
) : BaseAgent(id, name, description, listOf("result_processing", "visualization")) {
    
    override fun canHandle(message: Message): Boolean {
        return message.type in listOf(
            MessageType.TEXT, MessageType.DATA, MessageType.SYSTEM, 
            MessageType.WORKFLOW_START, MessageType.WORKFLOW_END, MessageType.RESULT
        )
    }
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            // Result formatting logic
            val formattedResult = "Result: ${message.content}"
            
            message.createReply(
                content = formattedResult,
                sender = id,
                type = MessageType.RESULT,
                metadata = mapOf<String, String>("formatted" to "true", "resultType" to "final")
            )
        } catch (e: Exception) {
            message.createReply(
                content = "Result processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>("errorType" to "processing_error")
            )
        }
    }
}

/**
 * 🔀 Conditional branching specialized Agent
 */
class BranchAgent(
    id: String = "branch-agent",
    name: String = "Branch Logic Agent",
    description: String = "Agent specialized in conditional branching logic"
) : BaseAgent(id, name, description, listOf("conditional_logic", "branching")) {
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            // Simple branching logic (can be extended)
            val shouldBranch = message.content.contains("branch", ignoreCase = true)
            
            message.createReply(
                content = if (shouldBranch) "Branch path taken" else "Main path continued",
                sender = id,
                type = MessageType.BRANCH,
                metadata = mapOf<String, String>(
                    "branchResult" to shouldBranch.toString(),
                    "branchLogic" to "applied"
                )
            )
        } catch (e: Exception) {
            message.createReply(
                content = "Branch processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>("errorType" to "processing_error")
            )
        }
    }
}

/**
 * 🔗 Flow merging specialized Agent
 */
class MergeAgent(
    id: String = "merge-agent",
    name: String = "Merge Logic Agent",
    description: String = "Agent specialized in merging multiple flows"
) : BaseAgent(id, name, description, listOf("flow_merging", "aggregation")) {
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            // Simple merge logic (can be extended)
            val messageCount = message.metadata["messageCount"]?.toIntOrNull() ?: 1
            
            if (messageCount >= 2) {
                message.createReply(
                    content = "Flows merged successfully",
                    sender = id,
                    type = MessageType.MERGE,
                    metadata = mapOf<String, String>("mergedCount" to messageCount.toString())
                )
            } else {
                message.createReply(
                    content = "Waiting for more flows to merge",
                    sender = id,
                    type = MessageType.MERGE,
                    metadata = mapOf<String, String>("status" to "waiting")
                )
            }
        } catch (e: Exception) {
            message.createReply(
                content = "Merge processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>("errorType" to "processing_error")
            )
        }
    }
} 