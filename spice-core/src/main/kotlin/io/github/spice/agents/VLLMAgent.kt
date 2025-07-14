package io.github.spice.agents

import io.github.spice.Agent
import io.github.spice.Message
import io.github.spice.MessageType
import io.github.spice.Tool
import io.github.spice.AgentPersona

/**
 * 🌶️ VLLM Agent - Local LLM serving via VLLM
 * 
 * Simple VLLM Agent implementation for local LLM serving
 * Note: This is a basic implementation without actual VLLM client
 */
class VLLMAgent(
    override val id: String = "vllm-agent-${System.currentTimeMillis()}",
    private val serverUrl: String = "http://localhost:8000",
    private val modelName: String = "default-model"
) : Agent {
    
    override suspend fun receive(message: Message): Message {
        return when (message.type) {
            MessageType.TEXT -> handleTextMessage(message)
            MessageType.SYSTEM -> handleSystemMessage(message)
            else -> Message(
                sender = id,
                content = "VLLM Agent received: ${message.type}",
                type = MessageType.TEXT
            )
        }
    }
    
    private suspend fun handleTextMessage(message: Message): Message {
        // Simulate VLLM processing
        val response = "VLLM response to: ${message.content}"
        
        return Message(
            sender = id,
            content = response,
            type = MessageType.TEXT,
            metadata = mapOf(
                "model" to modelName,
                "server" to serverUrl,
                "processing_time" to "simulated"
            )
        )
    }
    
    private fun handleSystemMessage(message: Message): Message {
        return Message(
            sender = id,
            content = "VLLM Agent system status: ACTIVE",
            type = MessageType.SYSTEM
        )
    }
    
    override fun getTools(): List<Tool> {
        return listOf(
            VLLMTool()
        )
    }
    
    override fun getPersona(): AgentPersona? {
        return AgentPersona(
            name = "VLLM Assistant",
            role = "Local LLM Provider",
            personality = listOf("efficient", "local", "fast"),
            communicationStyle = "direct",
            expertise = listOf("local inference", "model serving"),
            responsePatterns = mapOf(
                "greeting" to "Hello! I'm running locally via VLLM.",
                "processing" to "Processing your request locally...",
                "completion" to "Local processing completed."
            )
        )
    }
    
    /**
     * Check if VLLM server is ready
     */
    fun isServerReady(): Boolean {
        return true // Simulated
    }
    
    /**
     * Get server status
     */
    fun getServerStatus(): String {
        return "ONLINE" // Simulated
    }
    
    /**
     * Get model information
     */
    fun getModelInfo(): String {
        return buildString {
            appendLine("🌶️ VLLM Agent Information")
            appendLine("Model: $modelName")
            appendLine("Server: $serverUrl")
            appendLine("Status: ${if (isServerReady()) "ONLINE" else "OFFLINE"}")
        }
    }
}

/**
 * 🔧 VLLM Tool
 */
class VLLMTool : Tool {
    override val name = "vllm_inference"
    override val description = "Local LLM inference via VLLM"
    override val parameters = mapOf(
        "prompt" to "string",
        "max_tokens" to "number",
        "temperature" to "number"
    )
    
    override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
        return io.github.spice.ToolResult.success("VLLM inference completed")
    }
}

/**
 * 🏭 Factory functions for VLLMAgent
 */
object VLLMAgentFactory {
    
    /**
     * Create basic VLLM agent
     */
    fun createBasicVLLMAgent(
        serverUrl: String = "http://localhost:8000",
        modelName: String = "default-model"
    ): VLLMAgent {
        return VLLMAgent(
            serverUrl = serverUrl,
            modelName = modelName
        )
    }
    
    /**
     * Create local VLLM agent
     */
    fun createLocalVLLMAgent(): VLLMAgent {
        return VLLMAgent(
            serverUrl = "http://localhost:8000",
            modelName = "local-model"
        )
    }
} 