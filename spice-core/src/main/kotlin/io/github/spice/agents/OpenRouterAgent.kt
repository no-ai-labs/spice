package io.github.spice.agents

import io.github.spice.*
import io.github.spice.model.*
import io.github.spice.model.clients.*

/**
 * 🌐 OpenRouterAgent - Multi-model LLM Access Agent
 * 
 * Agent that provides access to various LLM models (Claude, GPT, Llama, etc.) through OpenRouter API.
 * Very convenient as it allows using multiple models with a single API key.
 */
class OpenRouterAgent(
    /**
     * OpenRouter ModelClient
     */
    private val client: OpenRouterClient,
    
    /**
     * Conversation context management
     */
    private val context: ModelContext = ModelContext(),
    
    /**
     * Agent basic information
     */
    id: String = "openrouter-agent-${System.currentTimeMillis()}",
    name: String = "OpenRouter Agent",
    description: String = "Multi-model agent powered by OpenRouter API"
) : BaseAgent(
    id = id,
    name = name,
    description = description,
    capabilities = listOf(
        "multi_model_access",
        "claude_models",
        "gpt_models", 
        "llama_models",
        "gemini_models",
        "context_aware",
        "token_tracking",
        "model_switching",
        "cost_optimization",
        "unified_api"
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
                        "agent_type" to "openrouter",
                        "context_size" to context.size().toString(),
                        "token_usage" to (response.usage?.toString() ?: "unknown"),
                        "response_time_ms" to response.responseTimeMs.toString(),
                        "model_used" to (response.metadata["model_used"] ?: client.modelName),
                        "generation_id" to (response.metadata["generation_id"] ?: "unknown")
                    )
                )
            } else {
                // Error response - bilingual support
                Message(
                    content = "error.openrouter.processing".i18nBilingual(response.error ?: "Unknown error"),
                    sender = name,
                    metadata = message.metadata + mapOf(
                        "error" to "true",
                        "error_message" to (response.error ?: "unknown")
                    )
                )
            }
        } catch (e: Exception) {
            Message(
                content = "error.openrouter.connection".i18nBilingual(e.message ?: "Unknown error"),
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
     * context clear
     */
    suspend fun clearContext() {
        context.clear()
    }
    
    /**
     * system prompt setting
     */
    suspend fun setSystemPrompt(prompt: String) {
        context.addSystem(prompt)
    }
    
    /**
     * context summary information get
     */
    suspend fun getContextSummary(): ContextSummary {
        return context.getSummary()
    }
    
    /**
     * conversation history get
     */
    suspend fun getConversationHistory(): List<ModelMessage> {
        return context.getMessages()
    }
    
    /**
* 📊 Status and model information
     */
    
    /**
     * client status check
     */
    suspend fun getClientStatus(): ClientStatus {
        return client.getStatus()
    }
    
    /**
     * client ready status check
     */
    override fun isReady(): Boolean {
        return client.isReady()
    }
    
    /**
* usage possible model list get
     */
    suspend fun getAvailableModels(): List<OpenRouterModel> {
        return client.getAvailableModels()
    }
    
    /**
* currently model information get
     */
    suspend fun getCurrentModelInfo(): OpenRouterModel? {
        return client.getModelInfo(client.modelName)
    }
    
    /**
     * status summary
     */
    suspend fun getStatusSummary(): String {
        val status = client.getStatus()
        val summary = context.getSummary()
        val modelInfo = getCurrentModelInfo()
        
        return buildString {
            appendLine("🌐 OpenRouter Agent Status")
            appendLine("Current Model: ${client.modelName}")
            modelInfo?.let { model ->
                appendLine("Model Name: ${model.name}")
                model.description?.let { desc ->
                    appendLine("Description: $desc")
                }
                model.context_length?.let { length ->
                    appendLine("Context Length: $length tokens")
                }
                model.pricing?.let { pricing ->
                    appendLine("Pricing: Input ${pricing.prompt}, Output ${pricing.completion}")
                }
            }
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
* Model-specific specialized processing
     */
    suspend fun processWithModelHint(
        message: String,
        preferredModel: String? = null
    ): Message {
// Currently in implementation, model is fixed when client is created
// Future dynamic model switching feature will be added
        return processMessage(Message(content = message, sender = "user"))
    }
    
    /**
     * batch processing (multiple message simultaneous processing)
     */
    suspend fun processBatch(messages: List<String>): List<Message> {
        return messages.map { content ->
            processMessage(Message(content = content, sender = "user"))
        }
    }
    
    /**
     * creative specialized processing (Claude model optimization)
     */
    suspend fun processCreativeTask(
        prompt: String,
        creativity: String = "balanced" // balanced, creative, precise
    ): Message {
        val creativePrompt = buildString {
            when (creativity) {
                "creative" -> appendLine("Be creative and imaginative in your response.")
                "precise" -> appendLine("Be precise and factual in your response.")
                else -> appendLine("Provide a balanced, thoughtful response.")
            }
            appendLine()
            appendLine(prompt)
        }
        
        return processMessage(Message(content = creativePrompt, sender = "user"))
    }
    
    /**
     * analytical specialized processing (GPT model optimization)
     */
    suspend fun processAnalyticalTask(
        data: String,
        analysisType: String = "general" // general, technical, business
    ): Message {
        val analyticalPrompt = buildString {
            appendLine("Please analyze the following data:")
            when (analysisType) {
                "technical" -> appendLine("Focus on technical aspects and implementation details.")
                "business" -> appendLine("Focus on business implications and strategic insights.")
                else -> appendLine("Provide a comprehensive general analysis.")
            }
            appendLine()
            appendLine(data)
        }
        
        return processMessage(Message(content = analyticalPrompt, sender = "user"))
    }
    
    /**
* Conversational learning session
     */
    suspend fun startLearningSession(topic: String): Message {
        val learningPrompt = """
            You are now in a learning session mode for: $topic
            
            As an educational assistant, please:
            - Provide clear, structured explanations
            - Use examples and analogies when helpful
            - Ask follow-up questions to check understanding
            - Adapt your teaching style to the learner's level
            
            Let's start exploring $topic together. What would you like to learn first?
        """.trimIndent()
        
        setSystemPrompt(learningPrompt)
        return Message(
            content = "session.learning.started".i18nBilingual(topic),
            sender = name
        )
    }
    
    /**
     * model recommendation feature
     */
    suspend fun recommendModel(taskType: String): List<String> {
        return when (taskType.lowercase()) {
            "creative", "writing", "story" -> listOf(
                "anthropic/claude-3.5-sonnet",
                "anthropic/claude-3-opus",
                "openai/gpt-4o"
            )
            "code", "programming", "technical" -> listOf(
                "anthropic/claude-3.5-sonnet",
                "openai/gpt-4o",
                "meta-llama/llama-3.1-70b-instruct"
            )
            "analysis", "research", "data" -> listOf(
                "openai/gpt-4o",
                "anthropic/claude-3.5-sonnet",
                "google/gemini-pro-1.5"
            )
            "chat", "conversation", "general" -> listOf(
                "openai/gpt-4o-mini",
                "anthropic/claude-3-haiku",
                "meta-llama/llama-3.1-8b-instruct"
            )
            else -> listOf(
                "anthropic/claude-3.5-sonnet",
                "openai/gpt-4o-mini"
            )
        }
    }
    
    /**
     * cost estimation feature
     */
    suspend fun estimateCost(inputTokens: Int, outputTokens: Int): String {
        val modelInfo = getCurrentModelInfo()
                 return modelInfo?.pricing?.let { pricing ->
             try {
                 val inputCost = pricing.prompt?.toDoubleOrNull() ?: 0.0
                 val outputCost = pricing.completion?.toDoubleOrNull() ?: 0.0
                 val totalCost = (inputTokens * inputCost) + (outputTokens * outputCost)
                 "cost.estimated".i18nBilingual(String.format("%.6f", totalCost))
             } catch (e: Exception) {
                 "cost.calculation_failed".i18nBilingual()
             }
         } ?: "cost.pricing_info_unavailable".i18nBilingual()
    }
}

/**
 * 🏗️ Builder functions for OpenRouterAgent
 */

/**
 * basic OpenRouterAgent generation
 */
fun createOpenRouterAgent(
    apiKey: String,
    model: String = "anthropic/claude-3.5-sonnet",
    appName: String = "Spice-Framework",
    systemPrompt: String? = null,
    contextBufferSize: Int = 10
): OpenRouterAgent {
    val client = createOpenRouterClient(
        apiKey = apiKey,
        model = model,
        appName = appName
    )
    
    val context = ModelContext(bufferSize = contextBufferSize, systemPrompt = systemPrompt)
    
    return OpenRouterAgent(
        client = client,
        context = context,
        name = "OpenRouter Agent (${model.split("/").last()})"
    )
}

/**
 * Claude specialized OpenRouterAgent generation
 */
fun createOpenRouterClaudeAgent(
    apiKey: String,
    model: String = "anthropic/claude-3.5-sonnet",
    systemPrompt: String? = null
): OpenRouterAgent {
    val claudeSystemPrompt = systemPrompt ?: """
        You are Claude, an AI assistant created by Anthropic via OpenRouter.
        You are helpful, harmless, and honest. You excel at creative tasks,
        analysis, and thoughtful conversation.
    """.trimIndent()
    
    return createOpenRouterAgent(
        apiKey = apiKey,
        model = model,
        systemPrompt = claudeSystemPrompt
    )
}

/**
 * GPT specialized OpenRouterAgent generation
 */
fun createOpenRouterGPTAgent(
    apiKey: String,
    model: String = "openai/gpt-4o-mini",
    systemPrompt: String? = null
): OpenRouterAgent {
    val gptSystemPrompt = systemPrompt ?: """
        You are GPT, an AI assistant created by OpenAI via OpenRouter.
        You are helpful and knowledgeable. You excel at reasoning,
        analysis, and providing accurate information.
    """.trimIndent()
    
    return createOpenRouterAgent(
        apiKey = apiKey,
        model = model,
        systemPrompt = gptSystemPrompt
    )
}

/**
 * Llama specialized OpenRouterAgent generation
 */
fun createOpenRouterLlamaAgent(
    apiKey: String,
    model: String = "meta-llama/llama-3.1-8b-instruct",
    systemPrompt: String? = null
): OpenRouterAgent {
    val llamaSystemPrompt = systemPrompt ?: """
        You are Llama, an AI assistant created by Meta via OpenRouter.
        You are helpful and efficient. You excel at general conversation,
        reasoning, and providing practical solutions.
    """.trimIndent()
    
    return createOpenRouterAgent(
        apiKey = apiKey,
        model = model,
        systemPrompt = llamaSystemPrompt
    )
}

/**
 * multimodal specialized OpenRouterAgent generation
 */
fun createOpenRouterMultimodalAgent(
    apiKey: String,
    model: String = "google/gemini-pro-1.5",
    systemPrompt: String? = null
): OpenRouterAgent {
    val multimodalSystemPrompt = systemPrompt ?: """
        You are a multimodal AI assistant via OpenRouter.
        You can process both text and images. You excel at visual analysis,
        creative tasks, and comprehensive understanding.
    """.trimIndent()
    
    return createOpenRouterAgent(
        apiKey = apiKey,
        model = model,
        systemPrompt = multimodalSystemPrompt
    )
} 