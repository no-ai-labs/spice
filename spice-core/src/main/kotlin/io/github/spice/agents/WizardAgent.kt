package io.github.spice.agents

import io.github.spice.*
import kotlinx.coroutines.delay

/**
 * 🧙‍♂️ WizardAgent - One-Shot Model Upgrade Agent
 * 
 * This agent operates in two modes:
 * - Normal Mode: Uses a fast, cost-effective model for routine tasks
 * - Wizard Mode: Temporarily upgrades to a high-intelligence model for complex tasks
 * 
 * Think of it as a "Super Saiyan" transformation - the agent temporarily becomes
 * much more powerful for specific tasks, then reverts to normal mode.
 * 
 * Unlike multi-agent delegation, this maintains conversation context and provides
 * a seamless user experience where one AI appears to "get smarter" temporarily.
 */
class WizardAgent(
    id: String = "wizard-agent",
    name: String = "Wizard Agent",
    private val normalAgent: BaseAgent,
    private val wizardAgent: BaseAgent,
    private val upgradeConditions: List<String> = defaultUpgradeConditions,
    private val complexityThreshold: Int = 50, // Message length threshold for complexity
    private val wizardModeIndicator: String = "🧙‍♂️ *Wizard Mode Activated*",
    private val normalModeIndicator: String = "🐂 *Back to Normal Mode*"
) : BaseAgent(
    id = id,
    name = name,
    description = "Shape-shifting agent that upgrades intelligence for complex tasks",
    capabilities = (normalAgent.capabilities + wizardAgent.capabilities + listOf(
        "dynamic_intelligence",
        "one_shot_upgrade",
        "context_preservation",
        "cost_optimization"
    )).distinct()
) {
    
    companion object {
        val defaultUpgradeConditions = listOf(
            // Visualization and Graphics
            "시각화", "visualize", "그래프", "graph", "차트", "chart", "다이어그램", "diagram",
            "SVG", "svg", "흐름도", "flowchart", "플로우차트", "flow chart",
            
            // Complex Analysis
            "복잡한", "complex", "어려운", "difficult", "분석", "analyze", "analysis",
            "설계", "design", "아키텍처", "architecture", "구조", "structure",
            
            // Programming and Technical
            "코드", "code", "프로그래밍", "programming", "알고리즘", "algorithm",
            "데이터베이스", "database", "시스템", "system", "네트워크", "network",
            
            // Creative and Advanced Tasks
            "창작", "creative", "작문", "writing", "번역", "translate", "요약", "summary",
            "기획", "planning", "전략", "strategy", "제안", "proposal",
            
            // Research and Documentation
            "연구", "research", "조사", "investigation", "문서", "document", "보고서", "report",
            "논문", "paper", "리뷰", "review", "평가", "evaluation"
        )
    }
    
    private var isWizardMode = false
    private var wizardModeUsageCount = 0
    
    override suspend fun processMessage(message: Message): Message {
        val shouldUpgrade = shouldUpgradeToWizardMode(message)
        
        return if (shouldUpgrade) {
            processWithWizardMode(message)
        } else {
            processWithNormalMode(message)
        }
    }
    
    /**
     * 🔮 Determine if message requires wizard mode upgrade
     */
    private fun shouldUpgradeToWizardMode(message: Message): Boolean {
        val content = message.content.lowercase()
        
        // Check for upgrade trigger words
        val hasUpgradeKeywords = upgradeConditions.any { condition ->
            content.contains(condition.lowercase())
        }
        
        // Check message complexity (length-based heuristic)
        val isComplexByLength = message.content.length > complexityThreshold
        
        // Check for question complexity indicators
        val hasComplexityIndicators = listOf("어떻게", "how", "왜", "why", "설명", "explain", "분석", "analyze").any {
            content.contains(it.lowercase())
        }
        
        return hasUpgradeKeywords || (isComplexByLength && hasComplexityIndicators)
    }
    
    /**
     * 🧙‍♂️ Process message in Wizard Mode (high-intelligence)
     */
    private suspend fun processWithWizardMode(message: Message): Message {
        try {
            // Activate wizard mode
            isWizardMode = true
            wizardModeUsageCount++
            
            // Add wizard mode indicator to message
            val wizardMessage = message.copy(
                content = "$wizardModeIndicator\n\n${message.content}",
                metadata = message.metadata + mapOf(
                    "wizard_mode_requested" to "true",
                    "upgrade_reason" to "complex_task_detected"
                )
            )
            
            // Process with high-intelligence agent
            val wizardResponse = wizardAgent.processMessage(wizardMessage)
            
            // Add wizard mode metadata
            val enhancedResponse = wizardResponse.copy(
                metadata = wizardResponse.metadata + mapOf(
                    "wizard_mode" to "true",
                    "normal_agent" to normalAgent.id,
                    "wizard_agent" to wizardAgent.id,
                    "upgrade_count" to wizardModeUsageCount.toString(),
                    "processing_mode" to "wizard"
                )
            )
            
            // Simulate brief "transformation" delay
            delay(500)
            
            return enhancedResponse
            
        } finally {
            // Always revert to normal mode after processing
            isWizardMode = false
        }
    }
    
    /**
     * 🐂 Process message in Normal Mode (standard intelligence)
     */
    private suspend fun processWithNormalMode(message: Message): Message {
        val normalResponse = normalAgent.processMessage(message)
        
        return normalResponse.copy(
            metadata = normalResponse.metadata + mapOf(
                "wizard_mode" to "false",
                "processing_mode" to "normal",
                "agent_used" to normalAgent.id
            )
        )
    }
    
    /**
     * 🎯 Process with custom upgrade conditions
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
     * 📊 Get wizard mode statistics
     */
    fun getWizardStats(): Map<String, Any> {
        return mapOf(
            "wizard_mode_usage_count" to wizardModeUsageCount,
            "current_mode" to if (isWizardMode) "wizard" else "normal",
            "normal_agent_id" to normalAgent.id,
            "wizard_agent_id" to wizardAgent.id,
            "upgrade_conditions_count" to upgradeConditions.size
        )
    }
    
    /**
     * 🔄 Create a new WizardAgent with different agents
     */
    fun withAgents(newNormalAgent: BaseAgent, newWizardAgent: BaseAgent): WizardAgent {
        return WizardAgent(
            id = id,
            name = name,
            normalAgent = newNormalAgent,
            wizardAgent = newWizardAgent,
            upgradeConditions = upgradeConditions,
            complexityThreshold = complexityThreshold,
            wizardModeIndicator = wizardModeIndicator,
            normalModeIndicator = normalModeIndicator
        )
    }
    
    /**
     * ⚙️ Create a new WizardAgent with custom conditions
     */
    fun withUpgradeConditions(newConditions: List<String>): WizardAgent {
        return WizardAgent(
            id = id,
            name = name,
            normalAgent = normalAgent,
            wizardAgent = wizardAgent,
            upgradeConditions = newConditions,
            complexityThreshold = complexityThreshold,
            wizardModeIndicator = wizardModeIndicator,
            normalModeIndicator = normalModeIndicator
        )
    }
    
    /**
     * 🧠 Get current intelligence level description
     */
    fun getCurrentIntelligenceLevel(): String {
        return if (isWizardMode) {
            "🧙‍♂️ Wizard Mode (High Intelligence) - Using ${wizardAgent.name}"
        } else {
            "🐂 Normal Mode (Standard Intelligence) - Using ${normalAgent.name}"
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
                    it.lowercase() in listOf("시각화", "visualize", "그래프", "graph", "차트", "chart", "다이어그램", "diagram")
                },
                "🔧 Technical" to upgradeConditions.filter { 
                    it.lowercase() in listOf("코드", "code", "프로그래밍", "programming", "알고리즘", "algorithm")
                },
                "📊 Analysis" to upgradeConditions.filter { 
                    it.lowercase() in listOf("복잡한", "complex", "분석", "analyze", "설계", "design")
                },
                "📚 Research" to upgradeConditions.filter { 
                    it.lowercase() in listOf("연구", "research", "조사", "investigation", "문서", "document")
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
 * 🏗️ Builder for creating WizardAgent instances
 */
class WizardAgentBuilder {
    private var id: String = "wizard-agent"
    private var name: String = "Wizard Agent"
    private var normalAgent: BaseAgent? = null
    private var wizardAgent: BaseAgent? = null
    private var upgradeConditions: List<String> = WizardAgent.defaultUpgradeConditions
    private var complexityThreshold: Int = 50
    private var wizardModeIndicator: String = "🧙‍♂️ *Wizard Mode Activated*"
    private var normalModeIndicator: String = "🐂 *Back to Normal Mode*"
    
    fun id(id: String) = apply { this.id = id }
    fun name(name: String) = apply { this.name = name }
    fun normalAgent(agent: BaseAgent) = apply { this.normalAgent = agent }
    fun wizardAgent(agent: BaseAgent) = apply { this.wizardAgent = agent }
    fun upgradeConditions(conditions: List<String>) = apply { this.upgradeConditions = conditions }
    fun complexityThreshold(threshold: Int) = apply { this.complexityThreshold = threshold }
    fun wizardModeIndicator(indicator: String) = apply { this.wizardModeIndicator = indicator }
    fun normalModeIndicator(indicator: String) = apply { this.normalModeIndicator = indicator }
    
    fun build(): WizardAgent {
        require(normalAgent != null) { "Normal agent must be specified" }
        require(wizardAgent != null) { "Wizard agent must be specified" }
        
        return WizardAgent(
            id = id,
            name = name,
            normalAgent = normalAgent!!,
            wizardAgent = wizardAgent!!,
            upgradeConditions = upgradeConditions,
            complexityThreshold = complexityThreshold,
            wizardModeIndicator = wizardModeIndicator,
            normalModeIndicator = normalModeIndicator
        )
    }
}

/**
 * 🎯 Factory methods for common WizardAgent configurations
 */
object WizardAgentFactory {
    
    /**
     * Create a WizardAgent using OpenRouter models
     */
    fun createOpenRouterWizard(
        apiKey: String,
        normalModel: String = "google/bison-001",
        wizardModel: String = "anthropic/claude-3.5-sonnet"
    ): WizardAgent {
        val normalAgent = OpenRouterAgent(
            id = "normal-openrouter",
            name = "Normal OpenRouter Agent",
            apiKey = apiKey,
            model = normalModel
        )
        
        val wizardAgent = OpenRouterAgent(
            id = "wizard-openrouter",
            name = "Wizard OpenRouter Agent",
            apiKey = apiKey,
            model = wizardModel
        )
        
        return WizardAgent(
            normalAgent = normalAgent,
            wizardAgent = wizardAgent
        )
    }
    
    /**
     * Create a WizardAgent with mixed providers
     */
    fun createMixedProviderWizard(
        normalAgent: BaseAgent,
        wizardAgent: BaseAgent,
        customConditions: List<String> = emptyList()
    ): WizardAgent {
        val conditions = if (customConditions.isNotEmpty()) {
            customConditions
        } else {
            WizardAgent.defaultUpgradeConditions
        }
        
        return WizardAgent(
            normalAgent = normalAgent,
            wizardAgent = wizardAgent,
            upgradeConditions = conditions
        )
    }
}
