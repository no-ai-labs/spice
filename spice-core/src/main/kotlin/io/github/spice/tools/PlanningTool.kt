package io.github.spice.tools

import io.github.spice.*
import io.github.spice.agents.PlanningAgent
import io.github.spice.agents.PlanningConfig
import io.github.spice.agents.OutputFormat
import io.github.spice.model.clients.ModelClient
import io.github.spice.model.clients.createOpenAIClient
import io.github.spice.model.clients.createClaudeClient
import kotlinx.serialization.json.Json

/**
 * 🧠 Planning Tool - Spice Framework Integration
 * 
 * Provides planning capabilities as a Tool that can be used by any Agent
 * - Integrates with PlanningAgent
 * - Supports multiple output formats
 * - Configurable planning strategies
 */
class PlanningTool(
    private val client: ModelClient,
    private val config: PlanningConfig = PlanningConfig()
) : Tool {
    
    override val name: String = "planning_tool"
    override val description: String = "Converts goals into structured, actionable plans"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "goal" to ParameterSchema(
                type = "string",
                description = "The goal to create a plan for",
                required = true
            ),
            "domain" to ParameterSchema(
                type = "string",
                description = "The domain context (optional)",
                required = false
            ),
            "priority" to ParameterSchema(
                type = "string",
                description = "Priority level: low, medium, high",
                required = false
            ),
            "output_format" to ParameterSchema(
                type = "string",
                description = "Output format: JSON, STRUCTURED_PLAN, MARKDOWN, PLAIN_TEXT",
                required = false
            ),
            "max_steps" to ParameterSchema(
                type = "integer",
                description = "Maximum number of steps in the plan",
                required = false
            )
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return try {
            val goal = parameters["goal"] as? String
                ?: return ToolResult.error("Goal parameter is required")
            
            val domain = parameters["domain"] as? String
            val priority = parameters["priority"] as? String ?: "medium"
            val outputFormat = parameters["output_format"] as? String ?: "STRUCTURED_PLAN"
            val maxSteps = parameters["max_steps"] as? Int ?: 10
            
            // Create planning configuration
            val planningConfig = config.copy(
                maxSteps = maxSteps,
                outputFormat = OutputFormat.valueOf(outputFormat.uppercase())
            )
            
            // Create planning agent
            val planningAgent = PlanningAgent(
                client = client,
                config = planningConfig
            )
            
            // Generate plan
            val plan = planningAgent.generatePlan(
                goal = goal,
                domain = domain,
                priority = priority
            )
            
            // Format output based on requested format
            val formattedOutput = when (planningConfig.outputFormat) {
                OutputFormat.JSON -> Json.encodeToString(plan)
                OutputFormat.STRUCTURED_PLAN -> plan.toFormattedString()
                OutputFormat.MARKDOWN -> plan.toMarkdown()
                OutputFormat.PLAIN_TEXT -> plan.toPlainText()
            }
            
            ToolResult.success(
                result = formattedOutput,
                metadata = mapOf(
                    "step_count" to plan.steps.size,
                    "has_dependencies" to plan.hasDependencies(),
                    "total_estimated_time" to plan.getTotalEstimatedTime(),
                    "output_format" to planningConfig.outputFormat.name
                )
            )
            
        } catch (e: Exception) {
            ToolResult.error("Planning failed: ${e.message}")
        }
    }
    
    override fun canExecute(parameters: Map<String, Any>): Boolean {
        return parameters.containsKey("goal") && 
               parameters["goal"] is String && 
               (parameters["goal"] as String).isNotBlank()
    }
}

/**
 * 🏭 Planning Tool Factory Functions
 */

/**
 * Create OpenAI-powered planning tool
 */
fun createOpenAIPlanningTool(
    apiKey: String,
    model: String = "gpt-4o-mini",
    config: PlanningConfig = PlanningConfig()
): PlanningTool {
    val client = createOpenAIClient(apiKey, model)
    return PlanningTool(client, config)
}

/**
 * Create Claude-powered planning tool
 */
fun createClaudePlanningTool(
    apiKey: String,
    model: String = "claude-3-5-sonnet-20241022",
    config: PlanningConfig = PlanningConfig()
): PlanningTool {
    val client = createClaudeClient(apiKey, model)
    return PlanningTool(client, config)
}

/**
 * Create planning tool with custom client
 */
fun createCustomPlanningTool(
    client: ModelClient,
    config: PlanningConfig = PlanningConfig()
): PlanningTool {
    return PlanningTool(client, config)
}

/**
 * 🎯 Advanced Planning Tool with Custom Strategies
 */
class AdvancedPlanningTool(
    private val client: ModelClient,
    private val config: PlanningConfig = PlanningConfig(),
    private val domainRules: Map<String, String> = emptyMap()
) : Tool {
    
    override val name: String = "advanced_planning_tool"
    override val description: String = "Advanced planning with domain-specific rules and strategies"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "goal" to ParameterSchema("string", "The goal to create a plan for", required = true),
            "domain" to ParameterSchema("string", "The domain context", required = false),
            "constraints" to ParameterSchema("array", "List of constraints", required = false),
            "priority" to ParameterSchema("string", "Priority level", required = false),
            "output_format" to ParameterSchema("string", "Output format", required = false),
            "refinement_iterations" to ParameterSchema("integer", "Number of refinement iterations", required = false)
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return try {
            val goal = parameters["goal"] as? String
                ?: return ToolResult.error("Goal parameter is required")
            
            val domain = parameters["domain"] as? String
            val constraints = parameters["constraints"] as? List<String> ?: emptyList()
            val priority = parameters["priority"] as? String ?: "medium"
            val refinementIterations = parameters["refinement_iterations"] as? Int ?: 1
            
            // Create planning agent
            val planningAgent = PlanningAgent(
                client = client,
                config = config
            )
            
            // Generate initial plan
            var plan = planningAgent.generatePlan(
                goal = goal,
                domain = domain,
                constraints = constraints,
                priority = priority
            )
            
            // Refine plan if requested
            repeat(refinementIterations) {
                plan = planningAgent.refinePlan(plan)
            }
            
            // Validate plan
            val validationResult = planningAgent.validatePlan(plan)
            
            val formattedOutput = when (config.outputFormat) {
                OutputFormat.JSON -> Json.encodeToString(plan)
                OutputFormat.STRUCTURED_PLAN -> plan.toFormattedString()
                OutputFormat.MARKDOWN -> plan.toMarkdown()
                OutputFormat.PLAIN_TEXT -> plan.toPlainText()
            }
            
            ToolResult.success(
                result = formattedOutput,
                metadata = mapOf(
                    "step_count" to plan.steps.size,
                    "refinement_iterations" to refinementIterations,
                    "validation_result" to validationResult.isValid,
                    "validation_issues" to validationResult.issues.size,
                    "total_estimated_time" to plan.getTotalEstimatedTime()
                )
            )
            
        } catch (e: Exception) {
            ToolResult.error("Advanced planning failed: ${e.message}")
        }
    }
    
    override fun canExecute(parameters: Map<String, Any>): Boolean {
        return parameters.containsKey("goal") && 
               parameters["goal"] is String && 
               (parameters["goal"] as String).isNotBlank()
    }
}

/**
 * 🎨 Convenience Functions
 */

/**
 * Quick planning with default settings
 */
suspend fun quickPlan(
    goal: String,
    client: ModelClient
): String {
    val tool = PlanningTool(client)
    val result = tool.execute(mapOf("goal" to goal))
    return if (result.success) result.result else "Planning failed: ${result.error}"
}

/**
 * Domain-specific planning
 */
suspend fun domainPlan(
    goal: String,
    domain: String,
    client: ModelClient
): String {
    val tool = PlanningTool(client)
    val result = tool.execute(mapOf(
        "goal" to goal,
        "domain" to domain
    ))
    return if (result.success) result.result else "Planning failed: ${result.error}"
} 