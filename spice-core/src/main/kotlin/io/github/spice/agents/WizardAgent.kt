package io.github.spice.agents

import io.github.spice.*
import io.github.spice.model.*
import kotlinx.coroutines.delay

/**
 * 🧙‍♂️ WizardAgent - ModelClient based One-Shot Model Upgrade Agent
 * 
 * This Agent operates in two modes:
 * - Normal Mode: Uses fast and cost-efficient models for routine tasks
 * - Wizard Mode: Temporarily upgrades to high-intelligence models for complex tasks
 * 
 * Key features:
 * - Shares a single ModelContext to maintain conversation context
 * - Dynamic model switching for cost optimization
 * - Appears to users as a single AI that temporarily "becomes smarter"
 */
class WizardAgent(
    /**
     * ModelClient for normal mode (fast and inexpensive model)
     */
    private val normalClient: ModelClient,
    
    /**
     * ModelClient for wizard mode (high-performance model)
     */
    private val wizardClient: ModelClient,
    
    /**
     * Shared conversation context (maintains context between modes)
     */
    private val sharedContext: ModelContext = ModelContext(),
    
    /**
     * Upgrade condition keywords
     */
    private val upgradeConditions: List<String> = defaultUpgradeConditions,
    
    /**
     * Complexity threshold (based on message length)
     */
    private val complexityThreshold: Int = 50,
    
    /**
     * Agent basic information
     */
    id: String = "wizard-agent-${System.currentTimeMillis()}",
    name: String = "Wizard Agent",
    description: String = "Shape-shifting agent that upgrades intelligence for complex tasks",
    capabilities: List<String> = listOf(
        "dynamic_intelligence", "one_shot_upgrade", "context_preservation", 
        "cost_optimization", "multi_model_routing"
    )
) : BaseAgent(id, name, description, capabilities) {
    
    companion object {
        val defaultUpgradeConditions = listOf(
            // Visualization and Graphics
            "visualization".i18nBilingual(), "visualize", "graph".i18nBilingual(), "graph", "chart".i18nBilingual(), "chart", "diagram".i18nBilingual(), "diagram",
"SVG", "svg", "flow / flow", "flowchart", "flow chart / flow chart", "flow chart",
            
            // Complex Analysis
"complex".i18nBilingual(), "complex", "difficult / difficult", "difficult", "analysis".i18nBilingual(), "analyze", "analysis",
"design".i18nBilingual(), "design", "architecture / architecture", "architecture", "structure / structure", "structure",
            
            // Programming and Technical
            "code".i18nBilingual(), "code", "programming".i18nBilingual(), "programming", "algorithm".i18nBilingual(), "algorithm",
"database / database", "database", "system / system", "system", "network / network", "network",
            
            // Creative and Advanced Tasks
"creative / creative", "creative", "writing / writing", "writing", "translate / translate", "translate", "summary / summary", "summary",
"planning / planning", "planning", "strategy / strategy", "strategy", "suggestion".i18nBilingual(), "proposal",
            
            // Research and Documentation
"research".i18nBilingual(), "research", "investigation".i18nBilingual(), "investigation", "document".i18nBilingual(), "document", "report / report", "report",
"paper / paper", "paper", "review / review", "review", "evaluation / evaluation", "evaluation"
        )
    }
    
    private var isWizardMode = false
    private var wizardModeUsageCount = 0
    private var normalModeUsageCount = 0
    
    /**
     * system prompt (common to both modes)
     */
    var systemPrompt: String = "You are a helpful AI assistant that can dynamically adjust intelligence based on task complexity."
    
    override suspend fun processMessage(message: Message): Message {
        val shouldUpgrade = shouldUpgradeToWizardMode(message)
        
        return if (shouldUpgrade) {
            processWithWizardMode(message)
        } else {
            processWithNormalMode(message)
        }
    }
    
    /**
     * 🔮 Determine if Wizard Mode upgrade is needed
     */
    private fun shouldUpgradeToWizardMode(message: Message): Boolean {
        val content = message.content.lowercase()
        
        // Upgrade keyword check
        val hasUpgradeKeywords = upgradeConditions.any { condition ->
            content.contains(condition.lowercase())
        }
        
        // Message complexity check (based on length)
        val isComplexByLength = message.content.length > complexityThreshold
        
        // Complexity indicator check
        val hasComplexityIndicators = listOf(
"how / how", "how", "why / why", "why", "explain / explain", "explain", "analysis".i18nBilingual(), "analyze",
"detailed / detailed", "detail", "deep / deep", "deep", "complex / complex", "complex"
        ).any { content.contains(it.lowercase()) }
        
        return hasUpgradeKeywords || (isComplexByLength && hasComplexityIndicators)
    }
    
    /**
     * 🧙‍♂️ Process message with Wizard Mode (high-intelligence model)
     */
    private suspend fun processWithWizardMode(message: Message): Message {
        return try {
            // Activate Wizard Mode
            isWizardMode = true
            wizardModeUsageCount++
            
            // Add user message to shared context
            sharedContext.addUser(message.content, mapOf(
                "message_id" to message.id,
                "sender" to message.sender,
                "timestamp" to System.currentTimeMillis().toString(),
                "processing_mode" to "wizard"
            ))
            
            // Get full message history
            val fullMessages = sharedContext.getFullMessages()
            
            // Add Wizard mode instruction
            val wizardSystemPrompt = """
                $systemPrompt
                
                🧙‍♂️ **WIZARD MODE ACTIVATED** - You are now operating in high-intelligence mode.
                Apply your maximum capabilities to provide the most comprehensive, accurate, and insightful response possible.
                Take time to think deeply and provide detailed analysis.
            """.trimIndent()
            
            // Generate response with Wizard client
            val response = wizardClient.chat(
                messages = fullMessages,
                systemPrompt = wizardSystemPrompt,
                metadata = mapOf(
                    "agent_id" to id,
                    "conversation_id" to (message.conversationId ?: "unknown"),
                    "processing_mode" to "wizard",
                    "upgrade_reason" to "complex_task_detected"
                )
            )
            
            // Simulate conversion delay
            delay(500)
            
            if (response.success) {
                // Add assistant response to shared context
                sharedContext.addAssistant(response.content, mapOf(
                    "response_time_ms" to response.responseTimeMs.toString(),
                    "model" to wizardClient.modelName,
                    "processing_mode" to "wizard",
                    "token_usage" to (response.usage?.totalTokens?.toString() ?: "unknown")
                ))
                
                // Display Wizard Mode with response generation
                val wizardContent = "🧙‍♂️ *Wizard Mode Activated*\n\n${response.content}"
                
                message.createReply(
                    content = wizardContent,
                    sender = id,
                    type = MessageType.TEXT,
                    metadata = mapOf(
                        "wizard_mode" to "true",
                        "normal_client" to normalClient.modelName,
                        "wizard_client" to wizardClient.modelName,
                        "upgrade_count" to wizardModeUsageCount.toString(),
                        "processing_mode" to "wizard",
                        "response_time_ms" to response.responseTimeMs.toString(),
                        "token_usage" to (response.usage?.totalTokens?.toString() ?: "0"),
                        "context_size" to sharedContext.size().toString()
                    )
                )
            } else {
                // Error response
                message.createReply(
                    content = "🧙‍♂️ Wizard Mode processing encountered an error: ${response.error} / 🧙‍♂️ Wizard Mode processing encountered an error: ${response.error}",
                    sender = id,
                    type = MessageType.ERROR,
                    metadata = mapOf(
                        "error" to (response.error ?: "Unknown error"),
                        "wizard_mode" to "true",
                        "processing_mode" to "wizard",
                        "model" to wizardClient.modelName
                    )
                )
            }
            
        } catch (e: Exception) {
            message.createReply(
                content = "🧙‍♂️ Wizard Mode processing encountered an exception: ${e.message} / 🧙‍♂️ Wizard Mode processing encountered an exception: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf(
                    "error_type" to e::class.simpleName.orEmpty(),
                    "error_message" to (e.message ?: "Unknown error"),
                    "wizard_mode" to "true",
                    "processing_mode" to "wizard"
                )
            )
        } finally {
            // Always return to normal mode
            isWizardMode = false
        }
    }
    
    /**
     * 🐂 Process message with Normal Mode (standard intelligence)
     */
    private suspend fun processWithNormalMode(message: Message): Message {
        return try {
            normalModeUsageCount++
            
            // Add user message to shared context
            sharedContext.addUser(message.content, mapOf(
                "message_id" to message.id,
                "sender" to message.sender,
                "timestamp" to System.currentTimeMillis().toString(),
                "processing_mode" to "normal"
            ))
            
            // Get full message history
            val fullMessages = sharedContext.getFullMessages()
            
            // Generate response with normal client
            val response = normalClient.chat(
                messages = fullMessages,
                systemPrompt = systemPrompt,
                metadata = mapOf(
                    "agent_id" to id,
                    "conversation_id" to (message.conversationId ?: "unknown"),
                    "processing_mode" to "normal"
                )
            )
            
            if (response.success) {
                // Add assistant response to shared context
                sharedContext.addAssistant(response.content, mapOf(
                    "response_time_ms" to response.responseTimeMs.toString(),
                    "model" to normalClient.modelName,
                    "processing_mode" to "normal",
                    "token_usage" to (response.usage?.totalTokens?.toString() ?: "unknown")
                ))
                
                message.createReply(
                    content = response.content,
                    sender = id,
                    type = MessageType.TEXT,
                    metadata = mapOf(
                        "wizard_mode" to "false",
                        "processing_mode" to "normal",
                        "agent_used" to normalClient.modelName,
                        "response_time_ms" to response.responseTimeMs.toString(),
                        "token_usage" to (response.usage?.totalTokens?.toString() ?: "0"),
                        "context_size" to sharedContext.size().toString()
                    )
                )
            } else {
                // Error response
                message.createReply(
                    content = "Normal mode processing encountered an error: ${response.error} / Normal mode processing encountered an error: ${response.error}",
                    sender = id,
                    type = MessageType.ERROR,
                    metadata = mapOf(
                        "error" to (response.error ?: "Unknown error"),
                        "wizard_mode" to "false",
                        "processing_mode" to "normal",
                        "model" to normalClient.modelName
                    )
                )
            }
            
        } catch (e: Exception) {
            message.createReply(
                content = "Normal mode processing encountered an exception: ${e.message} / Normal mode processing encountered an exception: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf(
                    "error_type" to e::class.simpleName.orEmpty(),
                    "error_message" to (e.message ?: "Unknown error"),
                    "wizard_mode" to "false",
                    "processing_mode" to "normal"
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
        return normalClient.isReady() && wizardClient.isReady()
    }
    
    /**
     * 🎯 Process with user-defined upgrade conditions
     */
    suspend fun processWithCustomUpgrade(
        message: Message,
        forceWizardMode: Boolean = false,
        customConditions: List<String> = emptyList()
    ): Message {
        val conditions = if (customConditions.isNotEmpty()) customConditions else upgradeConditions
        val shouldUpgrade = forceWizardMode || conditions.any { condition ->
            message.content.contains(condition, ignoreCase = true)
        }
        
        return if (shouldUpgrade) {
            processWithWizardMode(message)
        } else {
            processWithNormalMode(message)
        }
    }
    
    /**
     * 🧠 Context management functions
     */
    
    /**
     * Clear shared context
     */
    suspend fun clearContext() {
        sharedContext.clear()
    }
    
    /**
     * Get context summary information
     */
    suspend fun getContextSummary(): ContextSummary {
        return sharedContext.getSummary()
    }
    
    /**
     * Get conversation history
     */
    suspend fun getConversationHistory(): List<ModelMessage> {
        return sharedContext.getMessages()
    }
    
    /**
     * Update system prompt
     */
    fun updateSystemPrompt(prompt: String) {
        systemPrompt = prompt
    }
    
    /**
     * Add system message to context
     */
    suspend fun addSystemMessage(content: String) {
        sharedContext.addSystem(content)
    }
    
    /**
     * 📊 Get Wizard Mode statistics
     */
    suspend fun getWizardStats(): Map<String, Any> {
        val contextSummary = getContextSummary()
        val normalStatus = normalClient.getStatus()
        val wizardStatus = wizardClient.getStatus()
        
        return mapOf(
            "wizard_mode_usage_count" to wizardModeUsageCount,
            "normal_mode_usage_count" to normalModeUsageCount,
            "current_mode" to if (isWizardMode) "wizard" else "normal",
            "normal_client" to normalClient.modelName,
            "wizard_client" to wizardClient.modelName,
            "upgrade_conditions_count" to upgradeConditions.size,
            "context_summary" to contextSummary.getSummaryText(),
            "normal_client_status" to normalStatus.getSummary(),
            "wizard_client_status" to wizardStatus.getSummary(),
            "is_ready" to isReady(),
            "total_usage_count" to (wizardModeUsageCount + normalModeUsageCount)
        )
    }
    
    /**
     * 🧠 Describe current intelligence level
     */
    fun getCurrentIntelligenceLevel(): String {
        return if (isWizardMode) {
            "🧙‍♂️ Wizard Mode (High Intelligence) - Using ${wizardClient.modelName}"
        } else {
            "🐂 Normal Mode (Standard Intelligence) - Using ${normalClient.modelName}"
        }
    }
    
    /**
     * 📝 Generate upgrade condition explanation
     */
    fun explainUpgradeConditions(): String {
        return buildString {
            appendLine("🔮 Wizard Mode Upgrade Conditions:")
            appendLine("The agent will upgrade to Wizard Mode when messages contain:")
            
            val categories = mapOf(
                "🎨 Visualization" to upgradeConditions.filter { 
                    it.lowercase() in listOf("visualization".i18nBilingual(), "visualize", "graph".i18nBilingual(), "graph", "chart".i18nBilingual(), "chart", "diagram".i18nBilingual(), "diagram")
                },
                "🔧 Technical" to upgradeConditions.filter { 
                    it.lowercase() in listOf("code".i18nBilingual(), "code", "programming".i18nBilingual(), "programming", "algorithm".i18nBilingual(), "algorithm")
                },
                "📊 Analysis" to upgradeConditions.filter { 
                    it.lowercase() in listOf("complex".i18nBilingual(), "complex", "analysis".i18nBilingual(), "analyze", "design".i18nBilingual(), "design")
                },
                "📚 Research" to upgradeConditions.filter { 
                    it.lowercase() in listOf("research".i18nBilingual(), "research", "investigation".i18nBilingual(), "investigation", "document".i18nBilingual(), "document")
                }
            )
            
            categories.forEach { (category, conditions) ->
                if (conditions.isNotEmpty()) {
                    appendLine("$category: ${conditions.joinToString(", ")}")
                }
            }
            
            appendLine("\n💡 Complex messages (>${complexityThreshold} characters) with question indicators also trigger Wizard Mode.")
        }
    }
}

/**
 * 🏗️ WizardAgent builder functions
 */

/**
 * Generate WizardAgent with two ModelClients
 */
fun createWizardAgent(
    normalClient: ModelClient,
    wizardClient: ModelClient,
    systemPrompt: String = "You are a helpful AI assistant that can dynamically adjust intelligence based on task complexity.",
    bufferSize: Int = 15,
    upgradeConditions: List<String> = WizardAgent.defaultUpgradeConditions,
    complexityThreshold: Int = 50,
    id: String = "wizard-agent-${System.currentTimeMillis()}",
    name: String = "Wizard Agent"
): WizardAgent {
    val sharedContext = ModelContext(bufferSize, systemPrompt)
    
    return WizardAgent(
        normalClient = normalClient,
        wizardClient = wizardClient,
        sharedContext = sharedContext,
        upgradeConditions = upgradeConditions,
        complexityThreshold = complexityThreshold,
        id = id,
        name = name
    ).apply {
        updateSystemPrompt(systemPrompt)
    }
}

/**
 * Generate WizardAgent with OpenAI models
 */
fun createOpenAIWizardAgent(
    apiKey: String,
    normalModel: String = "gpt-3.5-turbo",
    wizardModel: String = "gpt-4",
    systemPrompt: String = "You are a helpful AI assistant that can dynamically adjust intelligence based on task complexity.",
    bufferSize: Int = 15,
    id: String = "openai-wizard-${System.currentTimeMillis()}",
    name: String = "OpenAI Wizard Agent"
): WizardAgent {
    val normalConfig = ModelClientConfig(apiKey = apiKey, defaultModel = normalModel)
    val wizardConfig = ModelClientConfig(apiKey = apiKey, defaultModel = wizardModel)
    
    val normalClient = io.github.spice.model.clients.OpenAIClient(normalConfig, normalModel)
    val wizardClient = io.github.spice.model.clients.OpenAIClient(wizardConfig, wizardModel)
    
    return createWizardAgent(
        normalClient = normalClient,
        wizardClient = wizardClient,
        systemPrompt = systemPrompt,
        bufferSize = bufferSize,
        id = id,
        name = name
    )
}

/**
 * Generate WizardAgent with mixed providers (e.g., OpenAI for normal, Claude for wizard)
 */
fun createMixedWizardAgent(
    normalClient: ModelClient,
    wizardClient: ModelClient,
    systemPrompt: String = "You are a helpful AI assistant that can dynamically adjust intelligence based on task complexity.",
    bufferSize: Int = 15,
    customConditions: List<String> = emptyList(),
    id: String = "mixed-wizard-${System.currentTimeMillis()}",
    name: String = "Mixed Wizard Agent"
): WizardAgent {
    val conditions = if (customConditions.isNotEmpty()) {
        customConditions
    } else {
        WizardAgent.defaultUpgradeConditions
    }
    
    return createWizardAgent(
        normalClient = normalClient,
        wizardClient = wizardClient,
        systemPrompt = systemPrompt,
        bufferSize = bufferSize,
        upgradeConditions = conditions,
        id = id,
        name = name
    )
} 