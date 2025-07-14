package io.github.spice.agents

import io.github.spice.Agent
import io.github.spice.Message
import io.github.spice.MessageType
import io.github.spice.Tool
import io.github.spice.ToolResult
import io.github.spice.AgentPersona
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 🧠 Planning Agent - Strategic planning and task decomposition
 * 
 * Specialized agent for breaking down complex tasks into manageable steps
 * - Strategic planning and goal decomposition
 * - Task prioritization and resource allocation
 * - Progress tracking and milestone management
 * - Adaptive planning based on execution feedback
 */
class PlanningAgent(
    override val id: String = "planning-agent-${System.currentTimeMillis()}",
    private val config: PlanningConfig = PlanningConfig()
) : Agent {
    
    private val planningHistory = mutableListOf<PlanningSession>()
    private val activePlans = mutableMapOf<String, ExecutionPlan>()
    private var currentSession: PlanningSession? = null
    
    override suspend fun receive(message: Message): Message {
        return when (message.type) {
            MessageType.TEXT -> handlePlanningRequest(message)
            MessageType.SYSTEM -> handleSystemMessage(message)
            MessageType.TOOL_CALL -> handleToolCall(message)
            else -> Message(
                sender = id,
                content = "Planning agent received: ${message.type}",
                type = MessageType.TEXT
            )
        }
    }
    
    /**
     * Handle planning request
     */
    private suspend fun handlePlanningRequest(message: Message): Message {
        val session = PlanningSession(
            id = "session-${System.currentTimeMillis()}",
            originalRequest = message.content,
            timestamp = LocalDateTime.now()
        )
        
        currentSession = session
        planningHistory.add(session)
        
        val plan = createExecutionPlan(message.content)
        activePlans[session.id] = plan
        
        return Message(
            sender = id,
            content = formatPlanResponse(plan),
            type = MessageType.TEXT,
            metadata = mapOf(
                "planId" to plan.id,
                "sessionId" to session.id,
                "totalSteps" to plan.steps.size
            )
        )
    }
    
    /**
     * Create execution plan from request
     */
    private fun createExecutionPlan(request: String): ExecutionPlan {
        val steps = mutableListOf<PlanStep>()
        
        // Simple planning logic - in real implementation, this would use LLM
        when {
            request.contains("develop", ignoreCase = true) -> {
                steps.add(PlanStep("1", "Analyze requirements", "HIGH", emptyList()))
                steps.add(PlanStep("2", "Design architecture", "HIGH", listOf("1")))
                steps.add(PlanStep("3", "Implement core features", "MEDIUM", listOf("2")))
                steps.add(PlanStep("4", "Test and validate", "MEDIUM", listOf("3")))
                steps.add(PlanStep("5", "Deploy and monitor", "LOW", listOf("4")))
            }
            request.contains("analyze", ignoreCase = true) -> {
                steps.add(PlanStep("1", "Gather data", "HIGH", emptyList()))
                steps.add(PlanStep("2", "Process information", "HIGH", listOf("1")))
                steps.add(PlanStep("3", "Generate insights", "MEDIUM", listOf("2")))
                steps.add(PlanStep("4", "Create report", "LOW", listOf("3")))
            }
            else -> {
                steps.add(PlanStep("1", "Understand the task", "HIGH", emptyList()))
                steps.add(PlanStep("2", "Break down into subtasks", "HIGH", listOf("1")))
                steps.add(PlanStep("3", "Execute subtasks", "MEDIUM", listOf("2")))
                steps.add(PlanStep("4", "Review and finalize", "LOW", listOf("3")))
            }
        }
        
        return ExecutionPlan(
            id = "plan-${System.currentTimeMillis()}",
            title = "Execution Plan for: ${request.take(50)}...",
            steps = steps,
            estimatedDuration = steps.size * 30, // 30 minutes per step
            priority = "MEDIUM"
        )
    }
    
    /**
     * Format plan response
     */
    private fun formatPlanResponse(plan: ExecutionPlan): String {
        val sb = StringBuilder()
        sb.appendLine("🧠 Planning Agent - Execution Plan")
        sb.appendLine("=" * 50)
        sb.appendLine("Plan ID: ${plan.id}")
        sb.appendLine("Title: ${plan.title}")
        sb.appendLine("Estimated Duration: ${plan.estimatedDuration} minutes")
        sb.appendLine("Priority: ${plan.priority}")
        sb.appendLine()
        sb.appendLine("📋 Steps:")
        
        plan.steps.forEach { step ->
            sb.appendLine("${step.id}. ${step.description}")
            sb.appendLine("   Priority: ${step.priority}")
            if (step.dependencies.isNotEmpty()) {
                sb.appendLine("   Dependencies: ${step.dependencies.joinToString(", ")}")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    /**
     * Handle system message
     */
    private fun handleSystemMessage(message: Message): Message {
        return Message(
            sender = id,
            content = "Planning agent system status: ACTIVE",
            type = MessageType.SYSTEM
        )
    }
    
    /**
     * Handle tool call
     */
    private fun handleToolCall(message: Message): Message {
        return Message(
            sender = id,
            content = "Planning agent tool execution completed",
            type = MessageType.TOOL_RESULT
        )
    }
    
    override fun getTools(): List<Tool> {
        return listOf(
            PlanningTool(),
            TaskDecompositionTool(),
            ProgressTrackingTool()
        )
    }
    
    override fun getPersona(): AgentPersona? {
        return AgentPersona(
            name = "Strategic Planner",
            role = "Planning Specialist",
            personality = listOf("analytical", "strategic", "methodical"),
            communicationStyle = "structured",
            expertise = listOf("strategic planning", "task decomposition", "project management"),
            responsePatterns = mapOf(
                "planning" to "Let me break this down into manageable steps...",
                "analysis" to "Based on the requirements, I recommend...",
                "prioritization" to "The most critical items are..."
            )
        )
    }
    
    /**
     * Get planning statistics
     */
    fun getPlanningStatistics(): Map<String, Any> {
        return mapOf(
            "totalSessions" to planningHistory.size,
            "activePlans" to activePlans.size,
            "averageStepsPerPlan" to if (activePlans.isNotEmpty()) 
                activePlans.values.map { it.steps.size }.average() else 0.0,
            "currentSessionId" to (currentSession?.id ?: "none")
        )
    }
    
    /**
     * Get active plans
     */
    fun getActivePlans(): Map<String, ExecutionPlan> = activePlans.toMap()
    
    /**
     * Get planning history
     */
    fun getPlanningHistory(): List<PlanningSession> = planningHistory.toList()
}

/**
 * 📋 Planning Configuration
 */
data class PlanningConfig(
    val maxSteps: Int = 10,
    val defaultPriority: String = "MEDIUM",
    val enableProgressTracking: Boolean = true,
    val planningStrategy: String = "ADAPTIVE"
)

/**
 * 📊 Planning Session
 */
data class PlanningSession(
    val id: String,
    val originalRequest: String,
    val timestamp: LocalDateTime,
    val status: String = "ACTIVE"
)

/**
 * 📋 Execution Plan
 */
data class ExecutionPlan(
    val id: String,
    val title: String,
    val steps: List<PlanStep>,
    val estimatedDuration: Int,
    val priority: String,
    val status: String = "PENDING"
)

/**
 * 📝 Plan Step
 */
data class PlanStep(
    val id: String,
    val description: String,
    val priority: String,
    val dependencies: List<String>,
    val status: String = "PENDING",
    val estimatedTime: Int = 30
)

/**
 * 🔧 Planning Tools
 */
class PlanningTool : Tool {
    override val name = "planning_tool"
    override val description = "Strategic planning and task decomposition"
    override val parameters = mapOf(
        "task" to "string",
        "priority" to "string",
        "complexity" to "number"
    )
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return ToolResult.success("Planning completed for task: ${parameters["task"]}")
    }
}

class TaskDecompositionTool : Tool {
    override val name = "task_decomposition"
    override val description = "Break down complex tasks into subtasks"
    override val parameters = mapOf(
        "mainTask" to "string",
        "granularity" to "string"
    )
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return ToolResult.success("Task decomposition completed")
    }
}

class ProgressTrackingTool : Tool {
    override val name = "progress_tracking"
    override val description = "Track progress of execution plans"
    override val parameters = mapOf(
        "planId" to "string",
        "stepId" to "string",
        "status" to "string"
    )
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return ToolResult.success("Progress updated")
    }
}

/**
 * 🏭 Factory functions for PlanningAgent
 */
object PlanningAgentFactory {
    
    /**
     * Create basic planning agent
     */
    fun createBasicPlanningAgent(config: PlanningConfig = PlanningConfig()): PlanningAgent {
        return PlanningAgent(config = config)
    }
    
    /**
     * Create strategic planning agent
     */
    fun createStrategicPlanningAgent(): PlanningAgent {
        val config = PlanningConfig(
            maxSteps = 15,
            defaultPriority = "HIGH",
            enableProgressTracking = true,
            planningStrategy = "STRATEGIC"
        )
        return PlanningAgent(config = config)
    }
    
    /**
     * Create agile planning agent
     */
    fun createAgilePlanningAgent(): PlanningAgent {
        val config = PlanningConfig(
            maxSteps = 8,
            defaultPriority = "MEDIUM",
            enableProgressTracking = true,
            planningStrategy = "AGILE"
        )
        return PlanningAgent(config = config)
    }
} 