package io.github.spice.agents

import io.github.spice.*
import io.github.spice.model.*
import io.github.spice.model.clients.ClaudeClient

/**
 * 🤖 ClaudeAgent - ModelClient-based Claude Agent
 * 
 * Context-aware Agent using Anthropic Claude models.
 * Manages conversation history through ModelClient and ModelContext,
 * supporting Autogen-style buffered conversations.
 */
class ClaudeAgent(
    /**
     * Claude model client
     */
    private val client: ModelClient,
    
    /**
     * Conversation context (buffered)
     */
    private val context: ModelContext = ModelContext(),
    
    /**
     * Agent basic information
     */
    id: String = "claude-agent-${System.currentTimeMillis()}",
    name: String = "Claude Agent",
    description: String = "Context-aware Claude Agent using ModelClient",
    capabilities: List<String> = listOf("chat", "analysis", "reasoning", "context-awareness")
) : BaseAgent(id, name, description, capabilities) {
    
    /**
     * System prompt (default value)
     */
    var systemPrompt: String = "You are Claude, a helpful AI assistant created by Anthropic. Be thoughtful, accurate, and helpful in your responses."
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            // 1. Add user message to context
            context.addUser(message.content, mapOf(
                "message_id" to message.id,
                "sender" to message.sender,
                "timestamp" to System.currentTimeMillis().toString()
            ))
            
            // 2. Get full message history
            val fullMessages = context.getFullMessages()
            
            // 3. Generate response through ModelClient
            val response = client.chat(
                messages = fullMessages,
                systemPrompt = systemPrompt,
                metadata = mapOf(
                    "agent_id" to id,
                    "conversation_id" to (message.conversationId ?: "unknown")
                )
            )
            
            if (response.success) {
                // 4. Add assistant response to context
                context.addAssistant(response.content, mapOf(
                    "response_time_ms" to response.responseTimeMs.toString(),
                    "model" to client.modelName,
                    "token_usage" to (response.usage?.totalTokens?.toString() ?: "unknown"),
                    "stop_reason" to (response.metadata["stop_reason"] ?: "unknown")
                ))
                
                // 5. Create response message
                message.createReply(
                    content = response.content,
                    sender = id,
                    type = MessageType.TEXT,
                    metadata = mapOf(
                        "model" to client.modelName,
                        "provider" to (response.metadata["provider"] ?: "anthropic"),
                        "response_time_ms" to response.responseTimeMs.toString(),
                        "token_usage" to (response.usage?.totalTokens?.toString() ?: "0"),
                        "context_size" to context.size().toString(),
                        "stop_reason" to (response.metadata["stop_reason"] ?: "unknown")
                    )
                )
            } else {
                // Error response - bilingual support
                message.createReply(
                    content = "error.claude.generation".i18nBilingual(response.error ?: "Unknown error"),
                    sender = id,
                    type = MessageType.ERROR,
                    metadata = mapOf(
                        "error" to (response.error ?: "Unknown error"),
                        "model" to client.modelName,
                        "response_time_ms" to response.responseTimeMs.toString()
                    )
                )
            }
            
        } catch (e: Exception) {
            message.createReply(
                content = "error.system".i18nBilingual(e.message ?: "Unknown error"),
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf(
                    "error_type" to e::class.simpleName.orEmpty(),
                    "error_message" to (e.message ?: "Unknown error")
                )
            )
        }
    }
    
    override fun canHandle(message: Message): Boolean {
        return when (message.type) {
            MessageType.TEXT, MessageType.PROMPT -> true
            else -> super.canHandle(message)
        }
    }
    
    override fun isReady(): Boolean {
        return client.isReady()
    }
    
    /**
     * 🧠 Context management functions
     */
    
    /**
     * Clear context
     */
    suspend fun clearContext() {
        context.clear()
    }
    
    /**
     * Get context summary information
     */
    suspend fun getContextSummary(): ContextSummary {
        return context.getSummary()
    }
    
    /**
     * Get conversation history
     */
    suspend fun getConversationHistory(): List<ModelMessage> {
        return context.getMessages()
    }
    
    /**
     * Update system prompt
     */
    fun updateSystemPrompt(prompt: String) {
        systemPrompt = prompt
    }
    
    /**
     * Get ModelClient status
     */
    suspend fun getClientStatus(): ClientStatus {
        return client.getStatus()
    }
    
    /**
     * Add system message to context
     */
    suspend fun addSystemMessage(content: String) {
        context.addSystem(content)
    }
    
    /**
     * Agent status information
     */
    suspend fun getAgentStatus(): Map<String, Any> {
        val contextSummary = getContextSummary()
        val clientStatus = getClientStatus()
        
        return mapOf(
            "agent_id" to id,
            "agent_name" to name,
            "model_client" to client.modelName,
            "context_summary" to contextSummary.getSummaryText(),
            "client_status" to clientStatus.getSummary(),
            "is_ready" to isReady(),
            "system_prompt_length" to systemPrompt.length,
            "capabilities" to capabilities
        )
    }
    
    /**
     * 🎯 Claude specialized features
     */
    
    /**
     * Content analysis processing (leveraging Claude's strengths)
     */
    suspend fun analyzeContent(content: String, analysisType: String = "general"): String {
        val analysisPrompt = when (analysisType) {
            "code" -> "Please analyze the following code and provide insights about its structure, potential issues, and improvements:"
            "text" -> "Please analyze the following text and provide insights about its content, style, and key points:"
            "data" -> "Please analyze the following data and provide insights about patterns, trends, and conclusions:"
            else -> "Please provide a thoughtful analysis of the following content:"
        }
        
        // Perform analysis with temporary context
        val tempMessages = listOf(
            ModelMessage.user("$analysisPrompt\n\n$content")
        )
        
        val response = client.chat(
            messages = tempMessages,
            systemPrompt = "You are Claude, an AI assistant specialized in thoughtful analysis. Provide detailed, structured insights.",
            metadata = mapOf(
                "analysis_type" to analysisType,
                "agent_id" to id
            )
        )
        
        return if (response.success) {
            response.content
        } else {
            "error.processing".i18nBilingual(response.error ?: "Unknown error")
        }
    }
    
    /**
     * Summary generation (leveraging Claude's strengths)
     */
    suspend fun summarizeContent(content: String, summaryStyle: String = "concise"): String {
        val summaryPrompt = when (summaryStyle) {
            "bullet" -> "Please provide a bullet-point summary of the following content:"
            "detailed" -> "Please provide a detailed summary of the following content:"
            "executive" -> "Please provide an executive summary of the following content:"
            else -> "Please provide a concise summary of the following content:"
        }
        
        val tempMessages = listOf(
            ModelMessage.user("$summaryPrompt\n\n$content")
        )
        
        val response = client.chat(
            messages = tempMessages,
            systemPrompt = "You are Claude, an AI assistant specialized in creating clear, well-structured summaries.",
            metadata = mapOf(
                "summary_style" to summaryStyle,
                "agent_id" to id
            )
        )
        
        return if (response.success) {
            response.content
        } else {
            "error.processing".i18nBilingual(response.error ?: "Unknown error")
        }
    }
}

/**
 * 🏗️ ClaudeAgent builder functions
 */

/**
 * Create ClaudeAgent with Anthropic configuration
 */
fun createClaudeAgent(
    apiKey: String,
    model: String = "claude-3-sonnet-20240229",
    systemPrompt: String = "You are Claude, a helpful AI assistant created by Anthropic.",
    bufferSize: Int = 10,
    id: String = "claude-agent-${System.currentTimeMillis()}",
    name: String = "Claude Agent"
): ClaudeAgent {
    val config = ModelClientConfig(
        apiKey = apiKey,
        defaultModel = model
    )
    
    val client = ClaudeClient(config, model)
    val context = ModelContext(bufferSize, systemPrompt)
    
    return ClaudeAgent(
        client = client,
        context = context,
        id = id,
        name = name,
        description = "Claude Agent using $model"
    ).apply {
        updateSystemPrompt(systemPrompt)
    }
}

/**
 * Create ClaudeAgent with existing ModelClient
 */
fun createClaudeAgent(
    client: ModelClient,
    systemPrompt: String = "You are Claude, a helpful AI assistant created by Anthropic.",
    bufferSize: Int = 10,
    id: String = "claude-agent-${System.currentTimeMillis()}",
    name: String = "Claude Agent"
): ClaudeAgent {
    val context = ModelContext(bufferSize, systemPrompt)
    
    return ClaudeAgent(
        client = client,
        context = context,
        id = id,
        name = name,
        description = "Claude Agent using ${client.modelName}"
    ).apply {
        updateSystemPrompt(systemPrompt)
    }
} 