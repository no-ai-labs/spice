package io.github.spice.agents

import io.github.spice.*
import io.github.spice.model.*
import io.github.spice.model.clients.OpenAIClient
import kotlinx.coroutines.flow.Flow

/**
 * 🤖 GPTAgent - OpenAI GPT model based Agent
 * 
 * Agent that uses OpenAI's GPT models (GPT-3.5, GPT-4, etc.) through ModelClient.
 * Provides conversation context management and streaming response capabilities.
 * 
 * Key features:
 * - conversation context (buffering)
 * - streaming response support
 * - multi-turn conversation
 * - context management
 * - temperature control
 * - token usage tracking
 */
class GPTAgent(
    private val modelClient: ModelClient,
    private val context: ModelContext = ModelContext(),
    override val id: String = "gpt-agent-${System.currentTimeMillis()}",
    override val name: String = "GPT Agent",
    override val description: String = "OpenAI GPT model based conversational agent",
    override val capabilities: List<String> = listOf("conversation", "text_generation", "analysis")
) : Agent {
    
    /**
     * system prompt (default value)
     */
    private var systemPrompt: String = "You are a helpful AI assistant."
    
    override suspend fun receive(message: Message): Message {
        return try {
            // 1. Add user message to context
            context.addUserMessage(message.content)
            
            // 2. Create chat completion request
            val response = modelClient.chatCompletion(
                messages = context.getAllMessages(),
                systemPrompt = systemPrompt,
                metadata = message.metadata
            )
            
            if (response.success) {
                // 3. Add assistant response to context
                context.addAssistantMessage(response.content)
                
                // 4. Add assistant response to context
                Message(
                    sender = id,
                    content = response.content,
                    type = MessageType.TEXT,
                    metadata = mapOf(
                        "model" to modelClient.modelName,
                        "usage" to (response.usage?.let { 
                            mapOf(
                                "inputTokens" to it.inputTokens,
                                "outputTokens" to it.outputTokens,
                                "totalTokens" to it.totalTokens
                            )
                        } ?: emptyMap()),
                        "responseTime" to response.responseTimeMs
                    )
                )
            } else {
                Message(
                    sender = id,
                    content = "Error generating response: ${response.error}".i18n(),
                    type = MessageType.ERROR,
                    metadata = mapOf(
                        "error" to response.error,
                        "model" to modelClient.modelName
                    )
                )
            }
        } catch (e: Exception) {
            Message(
                sender = id,
                content = "System error occurred: ${e.message}".i18n(),
                type = MessageType.ERROR,
                metadata = mapOf(
                    "error" to e.message,
                    "exception" to e.javaClass.simpleName
                )
            )
        }
    }
    
    /**
     * 🌊 Streaming response
     */
    suspend fun receiveStream(message: Message): Flow<Message> {
        // Add user message to context
        context.addUserMessage(message.content)
        
        // Create streaming chat completion request
        return modelClient.chatCompletionStream(
            messages = context.getAllMessages(),
            systemPrompt = systemPrompt,
            metadata = message.metadata
        )
    }
    
    override fun getTools(): List<Tool> {
        return emptyList() // GPTAgent doesn't use tools by default
    }
    
    override fun canHandle(message: Message): Boolean {
        return message.type in listOf(MessageType.TEXT, MessageType.PROMPT, MessageType.SYSTEM)
    }
    
    /**
     * 🧠 Context management functions
     */
    
    /**
     * Reset context
     */
    fun resetContext() {
        context.clear()
    }
    
    /**
     * Get context summary information
     */
    fun getContextSummary(): Map<String, Any> {
        return mapOf(
            "messageCount" to context.getMessageCount(),
            "bufferUsage" to context.getBufferUsage(),
            "isEmpty" to context.isEmpty()
        )
    }
    
    /**
     * Get conversation history
     */
    fun getConversationHistory(): List<ModelMessage> {
        return context.getMessages()
    }
    
    /**
     * Set system prompt
     */
    fun setSystemPrompt(prompt: String) {
        systemPrompt = prompt
    }
    
    /**
     * Get system prompt
     */
    fun getSystemPrompt(): String = systemPrompt
    
    /**
     * Get ModelClient status
     */
    fun getModelClientStatus(): ModelClientStatus {
        return modelClient.getStatus()
    }
    
    /**
     * Get model information
     */
    fun getModelInfo(): Map<String, Any> {
        return mapOf(
            "clientId" to modelClient.clientId,
            "modelName" to modelClient.modelName,
            "description" to modelClient.description,
            "isReady" to modelClient.isReady()
        )
    }
    
    /**
     * Get context configuration
     */
    fun getContextConfig(): Map<String, Any> {
        return mapOf(
            "bufferSize" to context.bufferSize,
            "systemPrompt" to context.systemPrompt
        )
    }
    
    /**
     * Set context buffer size
     */
    fun setContextBufferSize(size: Int) {
        // Create new context with different buffer size
        val newContext = ModelContext(
            bufferSize = size,
            systemPrompt = context.systemPrompt
        )
        // Copy existing messages
        context.getMessages().forEach { message ->
            newContext.addMessage(message)
        }
        // Replace context (this would require making context mutable)
        // For now, this is a placeholder for the functionality
    }
    
    /**
     * 🏗️ GPTAgent builder functions
     */
    
    companion object {
        /**
         * Create GPTAgent with OpenAI client
         */
        fun withOpenAI(
            apiKey: String,
            model: String = "gpt-3.5-turbo",
            temperature: Double = 0.7,
            maxTokens: Int = 2048,
            bufferSize: Int = 20
        ): GPTAgent {
            val client = OpenAIClient(
                apiKey = apiKey,
                model = model,
                temperature = temperature,
                maxTokens = maxTokens
            )
            
            val context = ModelContext(
                bufferSize = bufferSize
            )
            
            return GPTAgent(
                modelClient = client,
                context = context,
                name = "GPT Agent ($model)",
                description = "OpenAI $model based conversational agent"
            )
        }
        
        /**
         * Create GPTAgent from existing ModelClient
         */
        fun fromModelClient(
            client: ModelClient,
            bufferSize: Int = 20,
            systemPrompt: String = "You are a helpful AI assistant."
        ): GPTAgent {
            val context = ModelContext(
                bufferSize = bufferSize,
                systemPrompt = systemPrompt
            )
            
            return GPTAgent(
                modelClient = client,
                context = context,
                name = "GPT Agent (${client.modelName})",
                description = "GPT model based agent using ${client.modelName}"
            )
        }
    }
} 