package io.github.spice.agents

import io.github.spice.*
import io.github.spice.model.*
import io.github.spice.model.clients.*

/**
 * 🤖 VertexAgent - Google Vertex AI based Agent
 * 
 * Agent supporting various Google Vertex AI models including Gemini, PaLM, Bison, etc.
 * Uses ModelClient architecture to provide context management and token tracking.
 */
class VertexAgent(
    /**
     * Vertex AI ModelClient
     */
    private val client: VertexClient,
    
    /**
     * Conversation context management
     */
    private val context: ModelContext = ModelContext(),
    
    /**
     * Agent basic information
     */
    id: String = "vertex-agent-${System.currentTimeMillis()}",
    name: String = "Vertex Agent",
    description: String = "Google Vertex AI powered agent with context management"
) : BaseAgent(
    id = id,
    name = name,
    description = description,
    capabilities = listOf(
        "gemini_pro",
        "gemini_flash", 
        "palm_bison",
        "large_context",
        "multimodal",
        "function_calling",
        "code_generation",
        "context_aware",
        "token_tracking"
    )
) {
    
    /**
     * Message processing and response generation
     */
    override suspend fun processMessage(message: Message): Message {
        return try {
            // Add user message to context
            context.addUser(message.content)
            
            // Call model
            val response = client.chat(
                messages = context.getFullMessages(),
                systemPrompt = null // Already handled in getFullMessages()
            )
            
            if (response.success) {
                // Add response to context
                context.addAssistant(response.content)
                
                // Generate response message
                Message(
                    content = response.content,
                    sender = name,
                    metadata = message.metadata + mapOf(
                        "model" to client.modelName,
                        "agent_type" to "vertex",
                        "context_size" to context.size().toString(),
                        "token_usage" to (response.usage?.toString() ?: "unknown"),
                        "response_time_ms" to response.responseTimeMs.toString()
                    )
                )
            } else {
                // Error response - bilingual support
                Message(
                    content = "error.processing".i18nBilingual(response.error ?: "Unknown error"),
                    sender = name,
                    metadata = message.metadata + mapOf(
                        "error" to "true",
                        "error_message" to (response.error ?: "unknown")
                    )
                )
            }
        } catch (e: Exception) {
            Message(
                content = "error.system".i18nBilingual(e.message ?: "Unknown error"),
                sender = name,
                metadata = message.metadata + mapOf(
                    "error" to "true",
                    "exception" to e.javaClass.simpleName
                )
            )
        }
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
     * Set system prompt
     */
    suspend fun setSystemPrompt(prompt: String) {
        context.addSystem(prompt)
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
     * 📊 Status and statistics information
     */
    
    /**
     * Check client status
     */
    suspend fun getClientStatus(): ClientStatus {
        return client.getStatus()
    }
    
    /**
     * Check client ready state
     */
    override fun isReady(): Boolean {
        return client.isReady()
    }
    
    /**
     * Status summary
     */
    suspend fun getStatusSummary(): String {
        val status = client.getStatus()
        val summary = context.getSummary()
        
        return buildString {
            appendLine("🤖 Vertex Agent Status")
            appendLine("Model: ${client.modelName}")
            appendLine("Client: ${status.getSummary()}")
            appendLine("Context: ${summary.messageCount} messages, ${summary.estimatedTokens} tokens")
            if (summary.hasSystemPrompt) {
                appendLine("System Prompt: Active")
            }
        }
    }
    
    /**
     * 🔧 Advanced features
     */
    
    /**
     * Multimodal message processing (including images)
     */
    suspend fun processMultimodalMessage(
        text: String,
        imageData: ByteArray? = null,
        imageType: String = "image/jpeg"
    ): Message {
        // Currently processes text only, image support to be added in the future
        return processMessage(Message(content = text, sender = "user"))
    }
    
    /**
     * Batch processing (multiple messages simultaneously)
     */
    suspend fun processBatch(messages: List<String>): List<Message> {
        return messages.map { content ->
            processMessage(Message(content = content, sender = "user"))
        }
    }
    
    /**
     * Adjust context size
     */
    suspend fun adjustContextSize(newSize: Int) {
        // Adjust ModelContext buffer size (implementation needed)
        // context.setBufferSize(newSize)
    }
}

/**
 * 🏗️ Builder functions for VertexAgent
 */

/**
 * Create basic VertexAgent
 */
fun createVertexAgent(
    projectId: String,
    location: String = "us-central1",
    model: String = "gemini-1.5-flash-002",
    serviceAccountKeyPath: String = "",
    useApplicationDefaultCredentials: Boolean = true,
    systemPrompt: String? = null,
    contextBufferSize: Int = 10
): VertexAgent {
    val client = createVertexClient(
        projectId = projectId,
        location = location,
        model = model,
        serviceAccountKeyPath = serviceAccountKeyPath,
        useApplicationDefaultCredentials = useApplicationDefaultCredentials
    )
    
    val context = ModelContext(bufferSize = contextBufferSize, systemPrompt = systemPrompt)
    
    return VertexAgent(
        client = client,
        context = context,
        name = "Vertex Agent ($model)"
    )
}

/**
 * Create Gemini Pro dedicated VertexAgent
 */
fun createGeminiProAgent(
    projectId: String,
    location: String = "us-central1",
    systemPrompt: String? = null
): VertexAgent {
    return createVertexAgent(
        projectId = projectId,
        location = location,
        model = "gemini-1.5-pro-002",
        systemPrompt = systemPrompt
    )
}

/**
 * Create Gemini Flash dedicated VertexAgent (fast and economical)
 */
fun createGeminiFlashAgent(
    projectId: String,
    location: String = "us-central1",
    systemPrompt: String? = null
): VertexAgent {
    return createVertexAgent(
        projectId = projectId,
        location = location,
        model = "gemini-1.5-flash-002",
        systemPrompt = systemPrompt
    )
} 