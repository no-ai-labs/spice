package io.github.spice.tools

import io.github.spice.*
import io.github.spice.agents.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * 🧠 Planning Tool - Wraps PlanningAgent as a Tool
 * 
 * Allows other agents to use planning capabilities internally
 * without needing to register PlanningAgent in AgentEngine
 * 
 * Usage:
 * ```kotlin
 * val planningTool = PlanningTool(
 *     planningAgent = PlanningAgent(baseAgent = vertexAgent)
 * )
 * 
 * // Add to any agent
 * someAgent.addTool(planningTool)
 * 
 * // Use in agent's processMessage
 * val result = executeTool("planning", mapOf(
 *     "goal" to "Create a mobile app",
 *     "context" to mapOf("domain" to "mobile_app")
 * ))
 * ```
 */
class PlanningTool(
    private val planningAgent: PlanningAgent,
    private val outputFormat: OutputFormat = OutputFormat.STRUCTURED_PLAN
) : Tool {
    
    override val name: String = "planning"
    override val description: String = "Generate structured plans from user goals using AI planning capabilities"
    
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "goal" to ParameterSchema(
                type = "string",
                description = "The goal or objective to create a plan for",
                required = true
            ),
            "context" to ParameterSchema(
                type = "object",
                description = "Optional context information (company_profile, domain, priority, etc.)",
                required = false
            ),
            "config" to ParameterSchema(
                type = "object", 
                description = "Optional planning configuration (maxSteps, outputFormat, planningStrategy)",
                required = false
            )
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return try {
            val goal = parameters["goal"] as? String
                ?: return ToolResult.error("Goal parameter is required")
            
            val contextMap = parameters["context"] as? Map<String, Any> ?: emptyMap()
            val configMap = parameters["config"] as? Map<String, Any> ?: emptyMap()
            
            // Build message with context
            val metadata = buildMap {
                contextMap.forEach { (key, value) ->
                    put(key, value.toString())
                }
            }
            
            // Create planning message
            val planningMessage = Message(
                content = goal,
                sender = "planning-tool",
                metadata = metadata
            )
            
            // Execute planning
            val response = planningAgent.processMessage(planningMessage)
            
            if (response.type == MessageType.ERROR) {
                return ToolResult.error("Planning failed: ${response.content}")
            }
            
            // Return structured result
            ToolResult.success(
                result = response.content,
                metadata = mapOf(
                    "planner_id" to (response.metadata["planner_id"] ?: "unknown"),
                    "output_format" to (response.metadata["output_format"] ?: "unknown"),
                    "step_count" to (response.metadata["step_count"] ?: "0"),
                    "planning_strategy" to (response.metadata["planning_strategy"] ?: "unknown")
                )
            )
            
        } catch (e: Exception) {
            ToolResult.error("Planning tool execution failed: ${e.message}")
        }
    }
    
    override fun canExecute(parameters: Map<String, Any>): Boolean {
        return parameters.containsKey("goal") && 
               parameters["goal"] is String &&
               (parameters["goal"] as String).isNotBlank()
    }
}

/**
 * 🏗️ Planning Tool Builder - Convenience function
 */
fun createPlanningTool(
    baseAgent: BaseAgent,
    promptBuilder: PromptBuilder = DefaultPromptBuilder(),
    config: PlanningConfig = PlanningConfig()
): PlanningTool {
    val planningAgent = PlanningAgent(
        baseAgent = baseAgent,
        promptBuilder = promptBuilder,
        config = config
    )
    
    return PlanningTool(planningAgent)
}

/**
 * 🌶️ Extension function to add planning capability to any Agent
 */
fun BaseAgent.addPlanningCapability(
    baseAgent: BaseAgent,
    promptBuilder: PromptBuilder = DefaultPromptBuilder(),
    config: PlanningConfig = PlanningConfig()
): BaseAgent {
    val planningTool = createPlanningTool(baseAgent, promptBuilder, config)
    this.addTool(planningTool)
    return this
}

/**
 * 🎯 Specialized Planning Tools
 */

/**
 * Startup-focused planning tool
 */
fun createStartupPlanningTool(baseAgent: BaseAgent): PlanningTool {
    val startupPrompt = CompanyPromptBuilder(
        companyTemplate = """
            🚀 STARTUP PLANNING MODE
            Focus on rapid iteration, MVP development, and market validation.
            Keep solutions lean and scalable.
            Prioritize time-to-market and customer feedback.
        """.trimIndent(),
        domainSpecificRules = mapOf(
            "mobile_app" to "Cross-platform development, user feedback loops",
            "web_development" to "Progressive web app, serverless architecture",
            "ai_ml" to "Pre-trained models, cloud ML services",
            "data_analysis" to "Real-time analytics, automated insights"
        )
    )
    
    val config = PlanningConfig(
        maxSteps = 8,
        outputFormat = OutputFormat.STRUCTURED_PLAN,
        planningStrategy = PlanningStrategy.ITERATIVE,
        allowParallelSteps = true
    )
    
    return createPlanningTool(baseAgent, startupPrompt, config)
}

/**
 * Enterprise-focused planning tool
 */
fun createEnterprisePlanningTool(baseAgent: BaseAgent): PlanningTool {
    val enterprisePrompt = CompanyPromptBuilder(
        companyTemplate = """
            🏢 ENTERPRISE PLANNING MODE
            Focus on compliance, security, and governance.
            Include stakeholder approval processes and risk management.
            Prioritize integration with existing systems.
        """.trimIndent(),
        domainSpecificRules = mapOf(
            "web_development" to "Security audits, compliance requirements",
            "data_analysis" to "Data governance, privacy compliance",
            "ai_ml" to "Model governance, ethical AI considerations",
            "mobile_app" to "MDM integration, security policies"
        )
    )
    
    val config = PlanningConfig(
        maxSteps = 12,
        outputFormat = OutputFormat.STRUCTURED_PLAN,
        planningStrategy = PlanningStrategy.MILESTONE_BASED,
        includeTimeEstimates = true,
        includeDependencies = true
    )
    
    return createPlanningTool(baseAgent, enterprisePrompt, config)
} 