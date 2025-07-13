package io.github.spice.agents

import io.github.spice.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 🧠 PlanningAgent - Converts user goal into structured plan
 * 
 * Highly customizable planning agent that allows users to:
 * - Customize planning prompts via PromptBuilder
 * - Configure output format and structure
 * - Integrate with different base agents
 * - Support various planning strategies
 * 
 * Example usage:
 * ```kotlin
 * val planningAgent = PlanningAgent(
 *     baseAgent = vertexAgent,
 *     promptBuilder = CustomPromptBuilder(),
 *     config = PlanningConfig(
 *         maxSteps = 8,
 *         outputFormat = OutputFormat.STRUCTURED_PLAN,
 *         planningStrategy = PlanningStrategy.ITERATIVE
 *     )
 * )
 * ```
 */
class PlanningAgent(
    id: String = "planning-agent",
    name: String = "Planning Agent",
    private val baseAgent: BaseAgent,
    private val promptBuilder: PromptBuilder = DefaultPromptBuilder(),
    private val config: PlanningConfig = PlanningConfig()
) : BaseAgent(
    id = id,
    name = name,
    description = "Analyzes user goals and generates structured plans",
    capabilities = baseAgent.capabilities + listOf("planning", "goal_analysis", "task_decomposition")
) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    override suspend fun processMessage(message: Message): Message {
        try {
            // 1. Build planning prompt using configured builder
            val planningPrompt = promptBuilder.buildPrompt(
                goal = message.content,
                context = extractContext(message),
                config = config
            )
            
            // 2. Process with base agent
            val response = baseAgent.processMessage(
                message.copy(content = planningPrompt)
            )
            
            // 3. Parse and structure the response
            val structuredPlan = parseResponse(response.content)
            
            // 4. Return structured response
            return response.copy(
                content = when (config.outputFormat) {
                    OutputFormat.JSON -> json.encodeToString(structuredPlan)
                    OutputFormat.STRUCTURED_PLAN -> structuredPlan.toFormattedString()
                    OutputFormat.MARKDOWN -> structuredPlan.toMarkdown()
                    OutputFormat.PLAIN_TEXT -> structuredPlan.toPlainText()
                },
                metadata = response.metadata + mapOf(
                    "planner_id" to baseAgent.id,
                    "planner_name" to baseAgent.name,
                    "structured" to "true",
                    "output_format" to config.outputFormat.name,
                    "planning_strategy" to config.planningStrategy.name,
                    "step_count" to structuredPlan.steps.size.toString(),
                    "has_dependencies" to structuredPlan.hasDependencies().toString()
                )
            )
            
        } catch (e: Exception) {
            return message.createReply(
                content = "Planning failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf("error_type" to "planning_error")
            )
        }
    }
    
    /**
     * Extract planning context from message metadata
     */
    private fun extractContext(message: Message): PlanningContext {
        return PlanningContext(
            userProfile = message.metadata["user_profile"] ?: "user",
            domain = message.metadata["domain"],
            priority = message.metadata["priority"],
            constraints = message.metadata["constraints"]?.split(",")?.map { it.trim() }
        )
    }
    
    /**
     * Parse agent response into structured plan
     */
    private fun parseResponse(content: String): StructuredPlan {
        return try {
            // Try to parse as JSON first
            if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
                json.decodeFromString<StructuredPlan>(content)
            } else {
                // Fallback: parse as plain text
                parseTextToPlan(content)
            }
        } catch (e: Exception) {
            // Fallback: create simple plan from text
            parseTextToPlan(content)
        }
    }
    
    /**
     * Parse plain text into structured plan
     */
    private fun parseTextToPlan(content: String): StructuredPlan {
        val lines = content.split("\n").filter { it.isNotBlank() }
        val steps = mutableListOf<PlanStep>()
        
        lines.forEachIndexed { index, line ->
            val cleanLine = line.trim().removePrefix("-").removePrefix("*").removePrefix("${index + 1}.").trim()
            if (cleanLine.isNotBlank()) {
                steps.add(PlanStep(
                    id = "step-${index + 1}",
                    title = cleanLine,
                    description = cleanLine,
                    type = StepType.ACTION,
                    estimatedTime = null,
                    dependencies = emptyList()
                ))
            }
        }
        
        return StructuredPlan(
            title = "Generated Plan",
            description = "Plan generated from user goal",
            steps = steps,
            metadata = mapOf("generated_from" to "text_parsing")
        )
    }
}

/**
 * Planning configuration
 */
@Serializable
data class PlanningConfig(
    val maxSteps: Int = 10,
    val outputFormat: OutputFormat = OutputFormat.STRUCTURED_PLAN,
    val planningStrategy: PlanningStrategy = PlanningStrategy.SEQUENTIAL,
    val includeTimeEstimates: Boolean = true,
    val includeDependencies: Boolean = true,
    val allowParallelSteps: Boolean = false,
    val customInstructions: String? = null
)

/**
 * Output formats
 */
enum class OutputFormat {
    JSON,
    STRUCTURED_PLAN,
    MARKDOWN,
    PLAIN_TEXT
}

/**
 * Planning strategies
 */
enum class PlanningStrategy {
    SEQUENTIAL,      // Step-by-step execution
    PARALLEL,        // Parallel task execution
    HYBRID,          // Mix of sequential and parallel
    MILESTONE_BASED, // Organized around milestones
    ITERATIVE        // Iterative development cycles
}

/**
 * Planning context
 */
@Serializable
data class PlanningContext(
    val userProfile: String,
    val domain: String? = null,
    val priority: String? = null,
    val constraints: List<String>? = null
)

/**
 * Structured plan
 */
@Serializable
data class StructuredPlan(
    val title: String,
    val description: String,
    val steps: List<PlanStep>,
    val metadata: Map<String, String> = emptyMap()
) {
    
    fun hasDependencies(): Boolean = steps.any { it.dependencies.isNotEmpty() }
    
    fun toFormattedString(): String {
        val sb = StringBuilder()
        sb.appendLine("📋 $title")
        sb.appendLine("=".repeat(50))
        sb.appendLine()
        sb.appendLine("📝 Description: $description")
        sb.appendLine()
        sb.appendLine("📊 Steps (${steps.size}):")
        
        steps.forEachIndexed { index, step ->
            sb.appendLine("${index + 1}. ${step.title}")
            if (step.description != step.title) {
                sb.appendLine("   └─ ${step.description}")
            }
            if (step.estimatedTime != null) {
                sb.appendLine("   ⏱️ Estimated time: ${step.estimatedTime}")
            }
            if (step.dependencies.isNotEmpty()) {
                sb.appendLine("   🔗 Dependencies: ${step.dependencies.joinToString(", ")}")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    fun toMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine("# $title")
        sb.appendLine()
        sb.appendLine("## Description")
        sb.appendLine(description)
        sb.appendLine()
        sb.appendLine("## Steps")
        sb.appendLine()
        
        steps.forEachIndexed { index, step ->
            sb.appendLine("### ${index + 1}. ${step.title}")
            if (step.description != step.title) {
                sb.appendLine(step.description)
            }
            if (step.estimatedTime != null) {
                sb.appendLine("**Estimated time:** ${step.estimatedTime}")
            }
            if (step.dependencies.isNotEmpty()) {
                sb.appendLine("**Dependencies:** ${step.dependencies.joinToString(", ")}")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    fun toPlainText(): String {
        val sb = StringBuilder()
        sb.appendLine("$title")
        sb.appendLine()
        sb.appendLine("$description")
        sb.appendLine()
        
        steps.forEachIndexed { index, step ->
            sb.appendLine("${index + 1}. ${step.title}")
        }
        
        return sb.toString()
    }
}

/**
 * Plan step
 */
@Serializable
data class PlanStep(
    val id: String,
    val title: String,
    val description: String,
    val type: StepType,
    val estimatedTime: String? = null,
    val dependencies: List<String> = emptyList()
)

/**
 * Step types
 */
enum class StepType {
    ACTION,
    DECISION,
    MILESTONE,
    RESEARCH,
    VALIDATION
}

/**
 * Prompt builder interface
 */
interface PromptBuilder {
    fun buildPrompt(goal: String, context: PlanningContext, config: PlanningConfig): String
}

/**
 * Default prompt builder
 */
class DefaultPromptBuilder : PromptBuilder {
    override fun buildPrompt(goal: String, context: PlanningContext, config: PlanningConfig): String {
        return """
            You are a task planning agent in a multi-agent AI system.
            Break down the following goal into ${config.maxSteps} or fewer structured, actionable steps.
            
            Goal: "$goal"
            
            Context:
            - User Profile: ${context.userProfile}
            - Domain: ${context.domain ?: "general"}
            - Priority: ${context.priority ?: "medium"}
            - Constraints: ${context.constraints?.joinToString(", ") ?: "none"}
            
            Planning Strategy: ${config.planningStrategy}
            
            ${if (config.includeTimeEstimates) "Include time estimates for each step." else ""}
            ${if (config.includeDependencies) "Identify dependencies between steps." else ""}
            ${if (config.allowParallelSteps) "Mark steps that can be executed in parallel." else ""}
            ${config.customInstructions?.let { "Additional instructions: $it" } ?: ""}
            
            Output format: ${config.outputFormat}
            
            Provide a clear, actionable plan that can be executed step by step.
        """.trimIndent()
    }
}

/**
 * Company-specific prompt builder
 */
class CompanyPromptBuilder(
    private val companyTemplate: String,
    private val domainSpecificRules: Map<String, String> = emptyMap()
) : PromptBuilder {
    
    override fun buildPrompt(goal: String, context: PlanningContext, config: PlanningConfig): String {
        val domainRule = context.domain?.let { domainSpecificRules[it] } ?: ""
        
        return """
            $companyTemplate
            
            Goal: "$goal"
            
            Context:
            - User Profile: ${context.userProfile}
            - Domain: ${context.domain ?: "general"}
            - Priority: ${context.priority ?: "medium"}
            - Constraints: ${context.constraints?.joinToString(", ") ?: "none"}
            
            ${if (domainRule.isNotBlank()) "Domain-specific guidance: $domainRule" else ""}
            
            Planning Strategy: ${config.planningStrategy}
            Max Steps: ${config.maxSteps}
            
            ${if (config.includeTimeEstimates) "Include realistic time estimates." else ""}
            ${if (config.includeDependencies) "Identify task dependencies." else ""}
            ${if (config.allowParallelSteps) "Identify parallel execution opportunities." else ""}
            ${config.customInstructions?.let { "Additional instructions: $it" } ?: ""}
            
            Create a detailed, actionable plan optimized for our context.
        """.trimIndent()
    }
} 