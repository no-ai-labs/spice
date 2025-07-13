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
 *         maxSteps = 10,
 *         outputFormat = OutputFormat.STRUCTURED_PLAN,
 *         planningStrategy = PlanningStrategy.SEQUENTIAL
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
    description = "Analyzes user goals and generates structured plans with customizable prompts and output formats",
    capabilities = baseAgent.capabilities + listOf(
        "task_planning",
        "goal_decomposition", 
        "workflow_generation",
        "step_sequencing",
        "dependency_analysis",
        "structured_output"
    )
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
                metadata = mapOf(
                    "error" to "planning_failed",
                    "cause" to (e::class.simpleName ?: "Unknown")
                )
            )
        }
    }
    
    /**
     * Extract context from message metadata
     */
    private fun extractContext(message: Message): PlanningContext {
        return PlanningContext(
            userProfile = message.metadata["user_profile"],
            companyProfile = message.metadata["company_profile"],
            domain = message.metadata["domain"],
            priority = message.metadata["priority"],
            deadline = message.metadata["deadline"],
            resources = message.metadata["resources"]?.split(",")?.map { it.trim() },
            constraints = message.metadata["constraints"]?.split(",")?.map { it.trim() }
        )
    }
    
    /**
     * Parse agent response into structured plan
     */
    private fun parseResponse(content: String): StructuredPlan {
        return try {
            // Try to parse as JSON first
            if (content.trim().startsWith("[") || content.trim().startsWith("{")) {
                parseJsonResponse(content)
            } else {
                // Fallback to text parsing
                parseTextResponse(content)
            }
        } catch (e: Exception) {
            // Emergency fallback
            StructuredPlan(
                title = "Generated Plan",
                description = "Plan generated from user goal",
                steps = listOf(
                    PlanStep(
                        id = "step-1",
                        title = "Review and refine plan",
                        description = content,
                        type = StepType.ACTION,
                        estimatedTime = null,
                        dependencies = emptyList()
                    )
                )
            )
        }
    }
    
    /**
     * Parse JSON response
     */
    private fun parseJsonResponse(content: String): StructuredPlan {
        return when {
            content.trim().startsWith("[") -> {
                // Simple array format
                val steps = json.decodeFromString<List<String>>(content)
                StructuredPlan(
                    title = "Generated Plan",
                    description = "Plan generated from user goal",
                    steps = steps.mapIndexed { index, step ->
                        PlanStep(
                            id = "step-${index + 1}",
                            title = step,
                            description = step,
                            type = StepType.ACTION,
                            estimatedTime = null,
                            dependencies = if (index > 0) listOf("step-$index") else emptyList()
                        )
                    }
                )
            }
            content.trim().startsWith("{") -> {
                // Full structured plan format
                json.decodeFromString<StructuredPlan>(content)
            }
            else -> throw IllegalArgumentException("Invalid JSON format")
        }
    }
    
    /**
     * Parse text response
     */
    private fun parseTextResponse(content: String): StructuredPlan {
        val lines = content.split("\n").filter { it.isNotBlank() }
        val steps = mutableListOf<PlanStep>()
        
        for ((index, line) in lines.withIndex()) {
            val cleanLine = line.trim()
                .removePrefix("${index + 1}.")
                .removePrefix("-")
                .removePrefix("*")
                .trim()
            
            if (cleanLine.isNotEmpty()) {
                steps.add(
                    PlanStep(
                        id = "step-${index + 1}",
                        title = cleanLine,
                        description = cleanLine,
                        type = StepType.ACTION,
                        estimatedTime = null,
                        dependencies = if (index > 0) listOf("step-$index") else emptyList()
                    )
                )
            }
        }
        
        return StructuredPlan(
            title = "Generated Plan",
            description = "Plan generated from user goal",
            steps = steps
        )
    }
}

/**
 * 🏗️ PromptBuilder Interface for customizable prompt generation
 */
interface PromptBuilder {
    fun buildPrompt(goal: String, context: PlanningContext, config: PlanningConfig): String
}

/**
 * 📝 Default PromptBuilder implementation
 */
class DefaultPromptBuilder : PromptBuilder {
    override fun buildPrompt(goal: String, context: PlanningContext, config: PlanningConfig): String {
        return buildString {
            appendLine("You are a task planning agent in a multi-agent AI system.")
            appendLine("Break down the following goal into atomic, structured tasks.")
            appendLine()
            
            // Add context if available
            context.userProfile?.let { appendLine("User Profile: $it") }
            context.companyProfile?.let { appendLine("Company Profile: $it") }
            context.domain?.let { appendLine("Domain: $it") }
            context.priority?.let { appendLine("Priority: $it") }
            context.deadline?.let { appendLine("Deadline: $it") }
            context.resources?.let { appendLine("Available Resources: ${it.joinToString(", ")}") }
            context.constraints?.let { appendLine("Constraints: ${it.joinToString(", ")}") }
            
            appendLine()
            appendLine("Planning Strategy: ${config.planningStrategy.description}")
            appendLine("Maximum Steps: ${config.maxSteps}")
            appendLine()
            
            when (config.outputFormat) {
                OutputFormat.JSON -> {
                    appendLine("Output JSON only:")
                    appendLine("[")
                    appendLine("  \"Step 1\",")
                    appendLine("  \"Step 2\",")
                    appendLine("  \"Step 3\"")
                    appendLine("]")
                }
                OutputFormat.STRUCTURED_PLAN -> {
                    appendLine("Output structured plan in JSON format:")
                    appendLine("{")
                    appendLine("  \"title\": \"Plan Title\",")
                    appendLine("  \"description\": \"Plan Description\",")
                    appendLine("  \"steps\": [")
                    appendLine("    {")
                    appendLine("      \"id\": \"step-1\",")
                    appendLine("      \"title\": \"Step Title\",")
                    appendLine("      \"description\": \"Step Description\",")
                    appendLine("      \"type\": \"ACTION\",")
                    appendLine("      \"estimatedTime\": \"30m\",")
                    appendLine("      \"dependencies\": []")
                    appendLine("    }")
                    appendLine("  ]")
                    appendLine("}")
                }
                else -> {
                    appendLine("Output as numbered list:")
                    appendLine("1. Step 1")
                    appendLine("2. Step 2")
                    appendLine("3. Step 3")
                }
            }
            
            appendLine()
            appendLine("Goal: \"$goal\"")
        }
    }
}

/**
 * 🎯 Custom PromptBuilder for company-specific scenarios
 */
class CompanyPromptBuilder(
    private val companyTemplate: String,
    private val domainSpecificRules: Map<String, String> = emptyMap()
) : PromptBuilder {
    
    override fun buildPrompt(goal: String, context: PlanningContext, config: PlanningConfig): String {
        val basePrompt = DefaultPromptBuilder().buildPrompt(goal, context, config)
        
        return buildString {
            appendLine(companyTemplate)
            appendLine()
            
            // Add domain-specific rules
            context.domain?.let { domain ->
                domainSpecificRules[domain]?.let { rules ->
                    appendLine("Domain-specific rules for $domain:")
                    appendLine(rules)
                    appendLine()
                }
            }
            
            appendLine(basePrompt)
        }
    }
}

/**
 * ⚙️ PlanningConfig for customizable planning behavior
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
 * 📋 PlanningContext for contextual information
 */
@Serializable
data class PlanningContext(
    val userProfile: String? = null,
    val companyProfile: String? = null,
    val domain: String? = null,
    val priority: String? = null,
    val deadline: String? = null,
    val resources: List<String>? = null,
    val constraints: List<String>? = null
)

/**
 * 📊 Output format options
 */
enum class OutputFormat {
    JSON,
    STRUCTURED_PLAN,
    MARKDOWN,
    PLAIN_TEXT
}

/**
 * 🎯 Planning strategy options
 */
enum class PlanningStrategy(val description: String) {
    SEQUENTIAL("Execute steps one after another"),
    PARALLEL("Execute steps in parallel where possible"),
    HYBRID("Mix of sequential and parallel execution"),
    MILESTONE_BASED("Organize steps around key milestones"),
    ITERATIVE("Plan in iterations with feedback loops")
}

/**
 * 📝 Step type classification
 */
enum class StepType {
    ACTION,      // Actionable task
    DECISION,    // Decision point
    MILESTONE,   // Key milestone
    REVIEW,      // Review/validation step
    PARALLEL     // Parallel execution group
}

/**
 * 🗂️ StructuredPlan data class
 */
@Serializable
data class StructuredPlan(
    val title: String,
    val description: String,
    val steps: List<PlanStep>,
    val metadata: Map<String, String> = emptyMap()
) {
    fun hasDependencies(): Boolean = steps.any { it.dependencies.isNotEmpty() }
    
    fun toFormattedString(): String = buildString {
        appendLine("📋 $title")
        appendLine("📝 $description")
        appendLine()
        
        steps.forEachIndexed { index, step ->
            appendLine("${index + 1}. ${step.title}")
            if (step.description != step.title) {
                appendLine("   📄 ${step.description}")
            }
            step.estimatedTime?.let { appendLine("   ⏱️ $it") }
            if (step.dependencies.isNotEmpty()) {
                appendLine("   🔗 Depends on: ${step.dependencies.joinToString(", ")}")
            }
            appendLine()
        }
    }
    
    fun toMarkdown(): String = buildString {
        appendLine("# $title")
        appendLine()
        appendLine("$description")
        appendLine()
        
        steps.forEachIndexed { index, step ->
            appendLine("## ${index + 1}. ${step.title}")
            if (step.description != step.title) {
                appendLine("$description")
            }
            step.estimatedTime?.let { appendLine("**Time:** $it") }
            if (step.dependencies.isNotEmpty()) {
                appendLine("**Dependencies:** ${step.dependencies.joinToString(", ")}")
            }
            appendLine()
        }
    }
    
    fun toPlainText(): String = steps.joinToString("\n") { "${it.title}" }
}

/**
 * 📌 Individual plan step
 */
@Serializable
data class PlanStep(
    val id: String,
    val title: String,
    val description: String,
    val type: StepType = StepType.ACTION,
    val estimatedTime: String? = null,
    val dependencies: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) 