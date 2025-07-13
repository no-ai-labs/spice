package io.github.spice.springboot

import io.github.spice.*
import io.github.spice.agents.*
import io.github.spice.tools.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * 🌶️ Planning Agent Auto Configuration for Spring Boot
 * 
 * Automatically configures PlanningAgent based on application properties
 * 
 * Configuration properties:
 * ```yaml
 * spice:
 *   planning:
 *     enabled: true
 *     auto-register: true
 *     output-format: STRUCTURED_PLAN
 *     max-steps: 10
 *     planning-strategy: SEQUENTIAL
 *     company-profile: startup
 *     domain-rules:
 *       web_development: "Include responsive design and SEO"
 *       mobile_app: "Consider cross-platform development"
 * ```
 */
@Configuration
@ConditionalOnClass(PlanningAgent::class)
@EnableConfigurationProperties(PlanningProperties::class)
@ConditionalOnProperty(
    prefix = "spice.planning",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class PlanningAgentAutoConfiguration {
    
    /**
     * Default PlanningAgent bean
     */
    @Bean
    @ConditionalOnMissingBean
    fun planningAgent(
        agentEngine: AgentEngine,
        planningProperties: PlanningProperties
    ): PlanningAgent {
        
        // Select base agent from registered agents
        val baseAgent = selectBaseAgent(agentEngine, planningProperties)
        
        // Create prompt builder based on configuration
        val promptBuilder = createPromptBuilder(planningProperties)
        
        // Create planning configuration
        val config = PlanningConfig(
            maxSteps = planningProperties.maxSteps,
            outputFormat = planningProperties.outputFormat,
            planningStrategy = planningProperties.planningStrategy,
            includeTimeEstimates = planningProperties.includeTimeEstimates,
            includeDependencies = planningProperties.includeDependencies,
            allowParallelSteps = planningProperties.allowParallelSteps,
            customInstructions = planningProperties.customInstructions
        )
        
        val planningAgent = PlanningAgent(
            id = planningProperties.agentId,
            name = planningProperties.agentName,
            baseAgent = baseAgent,
            promptBuilder = promptBuilder,
            config = config
        )
        
        // Auto-register if enabled
        if (planningProperties.autoRegister) {
            agentEngine.registerAgent(planningAgent)
            println("🌶️ [AUTO-CONFIG] PlanningAgent registered: ${planningAgent.id}")
        }
        
        return planningAgent
    }
    
    /**
     * Planning Tool bean for internal use by other agents
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "spice.planning",
        name = ["tool-enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun planningTool(planningAgent: PlanningAgent): PlanningTool {
        return PlanningTool(planningAgent)
    }
    
    /**
     * Startup-specific planning tool
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "spice.planning",
        name = ["company-profile"],
        havingValue = "startup"
    )
    fun startupPlanningTool(
        agentEngine: AgentEngine,
        planningProperties: PlanningProperties
    ): PlanningTool {
        val baseAgent = selectBaseAgent(agentEngine, planningProperties)
        return createStartupPlanningTool(baseAgent)
    }
    
    /**
     * Enterprise-specific planning tool
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "spice.planning",
        name = ["company-profile"],
        havingValue = "enterprise"
    )
    fun enterprisePlanningTool(
        agentEngine: AgentEngine,
        planningProperties: PlanningProperties
    ): PlanningTool {
        val baseAgent = selectBaseAgent(agentEngine, planningProperties)
        return createEnterprisePlanningTool(baseAgent)
    }
    
    /**
     * Select appropriate base agent from registered agents
     */
    private fun selectBaseAgent(agentEngine: AgentEngine, properties: PlanningProperties): BaseAgent {
        val registry = agentEngine.getAgentRegistry()
        
        // Try to find preferred agent
        val preferredAgent = properties.preferredBaseAgent?.let { registry.get(it) }
        if (preferredAgent is BaseAgent) {
            return preferredAgent
        }
        
        // Try to find by provider
        val providerAgents = registry.findByProvider(properties.preferredProvider)
        val providerAgent = providerAgents.firstOrNull()
        if (providerAgent is BaseAgent) {
            return providerAgent
        }
        
        // Try to find by capability
        val capabilityAgents = registry.findByCapability("text_processing")
        val capabilityAgent = capabilityAgents.firstOrNull()
        if (capabilityAgent is BaseAgent) {
            return capabilityAgent
        }
        
        // Fallback to first available agent
        val allAgents = registry.getAll()
        val fallbackAgent = allAgents.firstOrNull()
        if (fallbackAgent is BaseAgent) {
            return fallbackAgent
        }
        
        // Last resort: create a simple mock agent
        return object : BaseAgent(
            id = "fallback-agent",
            name = "Fallback Agent",
            description = "Fallback agent for planning when no other agents are available"
        ) {
            override suspend fun processMessage(message: Message): Message {
                return message.createReply(
                    content = "Planning response: ${message.content}",
                    sender = id
                )
            }
        }
    }
    
    /**
     * Create prompt builder based on configuration
     */
    private fun createPromptBuilder(properties: PlanningProperties): PromptBuilder {
        return when (properties.companyProfile) {
            "startup" -> CompanyPromptBuilder(
                companyTemplate = """
                    🚀 STARTUP PLANNING MODE
                    Focus on rapid iteration, MVP development, and market validation.
                    Keep solutions lean and scalable.
                """.trimIndent(),
                domainSpecificRules = properties.domainRules
            )
            "enterprise" -> CompanyPromptBuilder(
                companyTemplate = """
                    🏢 ENTERPRISE PLANNING MODE
                    Focus on compliance, security, and governance.
                    Include stakeholder approval processes.
                """.trimIndent(),
                domainSpecificRules = properties.domainRules
            )
            "consulting" -> CompanyPromptBuilder(
                companyTemplate = """
                    🤝 CONSULTING PLANNING MODE
                    Focus on client deliverables and clear milestones.
                    Include knowledge transfer and quality assurance.
                """.trimIndent(),
                domainSpecificRules = properties.domainRules
            )
            else -> DefaultPromptBuilder()
        }
    }
}

/**
 * 🔧 Planning Configuration Properties
 */
@ConfigurationProperties(prefix = "spice.planning")
data class PlanningProperties(
    /**
     * Whether planning agent is enabled
     */
    val enabled: Boolean = false,
    
    /**
     * Whether to auto-register planning agent in AgentEngine
     */
    val autoRegister: Boolean = true,
    
    /**
     * Whether planning tool is enabled for other agents
     */
    val toolEnabled: Boolean = true,
    
    /**
     * Planning agent ID
     */
    val agentId: String = "planning-agent",
    
    /**
     * Planning agent name
     */
    val agentName: String = "Planning Agent",
    
    /**
     * Preferred base agent ID to use for planning
     */
    val preferredBaseAgent: String? = null,
    
    /**
     * Preferred provider (OpenAI, Anthropic, Google Vertex, etc.)
     */
    val preferredProvider: String = "OpenAI",
    
    /**
     * Output format for generated plans
     */
    val outputFormat: OutputFormat = OutputFormat.STRUCTURED_PLAN,
    
    /**
     * Maximum number of steps in generated plans
     */
    val maxSteps: Int = 10,
    
    /**
     * Planning strategy to use
     */
    val planningStrategy: PlanningStrategy = PlanningStrategy.SEQUENTIAL,
    
    /**
     * Whether to include time estimates in plans
     */
    val includeTimeEstimates: Boolean = true,
    
    /**
     * Whether to include dependencies in plans
     */
    val includeDependencies: Boolean = true,
    
    /**
     * Whether to allow parallel steps in plans
     */
    val allowParallelSteps: Boolean = false,
    
    /**
     * Custom instructions for planning
     */
    val customInstructions: String? = null,
    
    /**
     * Company profile (startup, enterprise, consulting)
     */
    val companyProfile: String = "startup",
    
    /**
     * Domain-specific rules
     */
    val domainRules: Map<String, String> = emptyMap()
)

/**
 * 🎯 Planning Agent Configuration DSL
 */
class PlanningConfigurationDsl {
    var enabled: Boolean = false
    var autoRegister: Boolean = true
    var outputFormat: OutputFormat = OutputFormat.STRUCTURED_PLAN
    var maxSteps: Int = 10
    var planningStrategy: PlanningStrategy = PlanningStrategy.SEQUENTIAL
    var companyProfile: String = "startup"
    var domainRules: MutableMap<String, String> = mutableMapOf()
    
    fun domainRule(domain: String, rule: String) {
        domainRules[domain] = rule
    }
    
    fun startup() {
        companyProfile = "startup"
        maxSteps = 8
        planningStrategy = PlanningStrategy.ITERATIVE
    }
    
    fun enterprise() {
        companyProfile = "enterprise"
        maxSteps = 12
        planningStrategy = PlanningStrategy.MILESTONE_BASED
    }
    
    fun consulting() {
        companyProfile = "consulting"
        maxSteps = 10
        planningStrategy = PlanningStrategy.SEQUENTIAL
    }
}

/**
 * 🌶️ Extension function for easy configuration
 */
fun SpiceProperties.planning(config: PlanningConfigurationDsl.() -> Unit): PlanningProperties {
    val dsl = PlanningConfigurationDsl()
    dsl.config()
    
    return PlanningProperties(
        enabled = dsl.enabled,
        autoRegister = dsl.autoRegister,
        outputFormat = dsl.outputFormat,
        maxSteps = dsl.maxSteps,
        planningStrategy = dsl.planningStrategy,
        companyProfile = dsl.companyProfile,
        domainRules = dsl.domainRules
    )
} 