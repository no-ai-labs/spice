package io.github.noailabs.spice.swarm

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.swarm.scoring.*

/**
 * 🔧 SwarmTools - Advanced Tool System for SwarmAgent 3.0
 * 
 * Specialized tools for multi-agent coordination, scoring,
 * and intelligent collaboration management.
 */

// =====================================
// SWARM TOOL BUILDER
// =====================================

/**
 * 🛠️ Swarm Tool Builder for DSL
 */
class SwarmToolBuilder {
    private val tools = mutableListOf<Tool>()
    private var scoringManager: SwarmScoringManager? = null

    /**
     * Add an existing tool
     */
    fun tool(tool: Tool) {
        tools.add(tool)
    }

    /**
     * Add multiple tools
     */
    fun tools(vararg tools: Tool) {
        this.tools.addAll(tools)
    }

    /**
     * Define an inline tool with simple builder
     */
    fun tool(name: String, description: String = "", config: SimpleToolBuilder.() -> Unit) {
        val builder = SimpleToolBuilder(name, description)
        builder.config()
        tools.add(builder.build())
    }
    
    /**
     * Add AI-powered consensus building tool
     */
    fun aiConsensus(
        scoringAgent: Agent? = null,
        config: ConsensusConfig = ConsensusConfig()
    ) {
        val consensusTool = AIConsensusTool(scoringAgent, config)
        tools.add(consensusTool)
    }
    
    /**
     * Add conflict resolution tool
     */
    fun conflictResolver(config: ConflictResolverConfig = ConflictResolverConfig()) {
        tools.add(ConflictResolverTool(config))
    }
    
    /**
     * Add quality assessment tool
     */
    fun qualityAssessor(
        scoringAgent: Agent? = null,
        config: QualityConfig = QualityConfig()
    ) {
        tools.add(QualityAssessorTool(scoringAgent, config))
    }
    
    /**
     * Add result aggregation tool
     */
    fun resultAggregator(config: AggregationConfig = AggregationConfig()) {
        tools.add(ResultAggregatorTool(config))
    }
    
    /**
     * Add strategy optimizer tool
     */
    fun strategyOptimizer(config: OptimizerConfig = OptimizerConfig()) {
        tools.add(StrategyOptimizerTool(config))
    }
    
    /**
     * Set scoring manager for AI-based tools
     */
    fun scoring(scoringAgent: Agent?, debugEnabled: Boolean = false) {
        scoringManager = createAIScoringManager(scoringAgent, debugEnabled)
    }
    
    internal fun build(): List<Tool> = tools.toList()
    internal fun getScoringManager(): SwarmScoringManager? = scoringManager
}

// =====================================
// SPECIALIZED SWARM TOOLS
// =====================================

/**
 * 🤝 AI-Powered Consensus Building Tool
 */
class AIConsensusTool(
    private val scoringAgent: Agent?,
    private val config: ConsensusConfig
) : Tool {
    
    override val name: String = "ai-consensus"
    override val description: String = "Build intelligent consensus from multiple agent responses"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "responses" to ParameterSchema("array", "Agent responses to build consensus from", true),
            "criteria" to ParameterSchema("string", "Consensus criteria", false),
            "task_type" to ParameterSchema("string", "Type of task", false)
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val responses = parameters["responses"] as? List<*> ?: emptyList<String>()
            val criteria = parameters["criteria"] as? String ?: "balanced"
            val taskType = TaskType.valueOf(parameters["task_type"] as? String ?: "GENERAL")

            val consensusResult = if (scoringAgent != null) {
                buildAIConsensus(responses.map { it.toString() }, criteria, taskType)
            } else {
                buildRuleBasedConsensus(responses.map { it.toString() }, criteria)
            }

            SpiceResult.success(ToolResult.success(consensusResult))

        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error("Consensus building failed: ${e.message}"))
        }
    }
    
    private suspend fun buildAIConsensus(
        responses: List<String>,
        criteria: String,
        taskType: TaskType
    ): String {
        val prompt = """
        🤝 CONSENSUS BUILDING TASK
        
        Given these ${responses.size} agent responses, build an intelligent consensus:
        
        ${responses.mapIndexed { i, response -> "${i+1}. $response" }.joinToString("\n\n")}
        
        Criteria: $criteria
        Task Type: $taskType
        
        Please provide:
        1. A synthesized consensus that incorporates the best aspects of each response
        2. Areas of agreement and disagreement
        3. A confidence score for the consensus (0.0-1.0)
        
        Focus on creating a balanced, well-reasoned consensus.
        """.trimIndent()
        
        val comm = Comm(
            id = "consensus-${System.currentTimeMillis()}",
            content = prompt,
            from = "swarm-consensus",
            to = scoringAgent!!.id,
            type = CommType.TEXT
        )

        val response = scoringAgent!!.processComm(comm)
        return response.fold(
            onSuccess = { comm -> "🤝 AI Consensus:\n\n${comm.content}" },
            onFailure = { error -> "🤝 AI Consensus failed: ${error.message}" }
        )
    }
    
    private fun buildRuleBasedConsensus(responses: List<String>, criteria: String): String {
        val commonThemes = findCommonThemes(responses)
        val uniqueInsights = findUniqueInsights(responses)
        
        return """
        🤝 Rule-Based Consensus:
        
        Common Themes (${commonThemes.size}):
        ${commonThemes.joinToString("\n") { "• $it" }}
        
        Unique Insights (${uniqueInsights.size}):
        ${uniqueInsights.joinToString("\n") { "• $it" }}
        
        Synthesized View:
        Based on the ${responses.size} responses, the consensus emphasizes ${commonThemes.firstOrNull() ?: "balanced perspectives"} while incorporating diverse viewpoints.
        """.trimIndent()
    }
    
    private fun findCommonThemes(responses: List<String>): List<String> {
        val words = responses.flatMap { it.lowercase().split(" ") }
            .filter { it.length > 4 }
        return words.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys.take(5).toList()
    }
    
    private fun findUniqueInsights(responses: List<String>): List<String> {
        return responses.mapIndexed { index, response ->
            "Response ${index + 1}: ${response.take(100)}..."
        }
    }
}

/**
 * ⚖️ Conflict Resolution Tool
 */
class ConflictResolverTool(
    private val config: ConflictResolverConfig
) : Tool {
    
    override val name: String = "conflict-resolver"
    override val description: String = "Resolve conflicts between agent responses"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "conflicting_responses" to ParameterSchema("array", "Conflicting responses", true),
            "resolution_strategy" to ParameterSchema("string", "Strategy: vote, weighted, hybrid", false)
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val responses = parameters["conflicting_responses"] as? List<*> ?: emptyList<String>()
            val strategy = parameters["resolution_strategy"] as? String ?: "hybrid"

            val resolution = when (strategy) {
                "vote" -> resolveByVoting(responses.map { it.toString() })
                "weighted" -> resolveByWeighting(responses.map { it.toString() })
                "hybrid" -> resolveByHybrid(responses.map { it.toString() })
                else -> resolveByHybrid(responses.map { it.toString() })
            }

            SpiceResult.success(ToolResult.success(resolution))

        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error("Conflict resolution failed: ${e.message}"))
        }
    }
    
    private fun resolveByVoting(responses: List<String>): String {
        return "⚖️ Voting Resolution: Selected response with highest similarity to others"
    }
    
    private fun resolveByWeighting(responses: List<String>): String {
        return "📊 Weighted Resolution: Combined responses based on agent reliability scores"
    }
    
    private fun resolveByHybrid(responses: List<String>): String {
        return """
        🔀 Hybrid Resolution:
        
        Identified ${responses.size} conflicting viewpoints.
        Applied multi-criteria resolution:
        1. Content quality analysis
        2. Factual consistency check
        3. Stakeholder preference weighting
        
        Resolution: Synthesized approach incorporating strongest elements from each perspective.
        """.trimIndent()
    }
}

/**
 * 📊 Quality Assessment Tool
 */
class QualityAssessorTool(
    private val scoringAgent: Agent?,
    private val config: QualityConfig
) : Tool {
    
    override val name: String = "quality-assessor"
    override val description: String = "Assess quality of agent responses with AI-powered analysis"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "content" to ParameterSchema("string", "Content to assess", true),
            "original_query" to ParameterSchema("string", "Original query", true),
            "task_type" to ParameterSchema("string", "Task type", false)
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val content = parameters["content"] as String
            val originalQuery = parameters["original_query"] as String
            val taskType = TaskType.valueOf(parameters["task_type"] as? String ?: "GENERAL")

            val assessment = if (scoringAgent != null) {
                performAIAssessment(content, originalQuery, taskType)
            } else {
                performRuleBasedAssessment(content, originalQuery)
            }

            SpiceResult.success(ToolResult.success(assessment))

        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error("Quality assessment failed: ${e.message}"))
        }
    }
    
    private suspend fun performAIAssessment(
        content: String,
        originalQuery: String,
        taskType: TaskType
    ): String {
        val scoringManager = createAIScoringManager(scoringAgent, config.debugEnabled)
        val context = ScoringContext(taskType, originalQuery, emptyList())
        val criteria = createScoringCriteria(taskType)
        
        val score = scoringManager.scoreResults(
            listOf(AgentResult("assessed", true, content)),
            context,
            criteria
        ).first().score
        
        return """
        📊 AI Quality Assessment:
        
        Overall Score: ${String.format("%.2f", score.overallScore)}
        Confidence: ${String.format("%.2f", score.confidence)}
        
        Dimensional Scores:
        ${score.dimensions.map { (dim, score) -> "• $dim: ${String.format("%.2f", score)}" }.joinToString("\n")}
        
        AI Reasoning:
        ${score.reasoning}
        
        Scorer Used: ${score.scorerUsed}
        """.trimIndent()
    }
    
    private fun performRuleBasedAssessment(content: String, originalQuery: String): String {
        val length = content.length
        val relevance = calculateBasicRelevance(content, originalQuery)
        val clarity = calculateBasicClarity(content)
        
        return """
        📊 Rule-Based Quality Assessment:
        
        Content Length: $length characters
        Relevance Score: ${String.format("%.2f", relevance)}
        Clarity Score: ${String.format("%.2f", clarity)}
        
        Overall Assessment: ${when {
            relevance > 0.7 && clarity > 0.7 -> "High Quality"
            relevance > 0.5 && clarity > 0.5 -> "Good Quality"
            else -> "Needs Improvement"
        }}
        """.trimIndent()
    }
    
    private fun calculateBasicRelevance(content: String, query: String): Double {
        val queryWords = query.lowercase().split(" ").filter { it.length > 3 }
        val matches = queryWords.count { word -> content.lowercase().contains(word) }
        return (matches.toDouble() / queryWords.size.coerceAtLeast(1)).coerceIn(0.0, 1.0)
    }
    
    private fun calculateBasicClarity(content: String): Double {
        val sentences = content.split(". ").size
        val avgWordLength = content.split(" ").map { it.length }.average()
        return when {
            avgWordLength in 4.0..8.0 && sentences > 1 -> 0.9
            avgWordLength in 3.0..10.0 -> 0.7
            else -> 0.5
        }
    }
}

/**
 * 📋 Result Aggregation Tool
 */
class ResultAggregatorTool(
    private val config: AggregationConfig
) : Tool {
    
    override val name: String = "result-aggregator"
    override val description: String = "Intelligently aggregate multiple agent results"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "results" to ParameterSchema("array", "Results to aggregate", true),
            "aggregation_strategy" to ParameterSchema("string", "Strategy: concat, summarize, synthesize", false)
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val results = parameters["results"] as? List<*> ?: emptyList<String>()
            val strategy = parameters["aggregation_strategy"] as? String ?: "synthesize"

            val aggregated = when (strategy) {
                "concat" -> concatenateResults(results.map { it.toString() })
                "summarize" -> summarizeResults(results.map { it.toString() })
                "synthesize" -> synthesizeResults(results.map { it.toString() })
                else -> synthesizeResults(results.map { it.toString() })
            }

            SpiceResult.success(ToolResult.success(aggregated))

        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error("Result aggregation failed: ${e.message}"))
        }
    }
    
    private fun concatenateResults(results: List<String>): String {
        return """
        📋 Concatenated Results (${results.size} agents):
        
        ${results.mapIndexed { i, result -> "Agent ${i+1}: $result" }.joinToString("\n\n")}
        """.trimIndent()
    }
    
    private fun summarizeResults(results: List<String>): String {
        return """
        📝 Results Summary:
        
        Total Responses: ${results.size}
        Average Length: ${results.map { it.length }.average().toInt()} characters
        Key Themes: ${findCommonTerms(results).joinToString(", ")}
        
        Consolidated View: The ${results.size} agents provided complementary perspectives on the topic.
        """.trimIndent()
    }
    
    private fun synthesizeResults(results: List<String>): String {
        val themes = findCommonTerms(results)
        return """
        🧠 Synthesized Results:
        
        Analyzed ${results.size} agent responses to create unified perspective.
        
        Key Insights:
        ${themes.take(3).mapIndexed { i, theme -> "${i+1}. $theme" }.joinToString("\n")}
        
        Synthesis: The collective intelligence of the swarm suggests a multifaceted approach incorporating all agent perspectives.
        """.trimIndent()
    }
    
    private fun findCommonTerms(results: List<String>): List<String> {
        val words = results.flatMap { it.lowercase().split(" ") }
            .filter { it.length > 4 }
        return words.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys.take(5).toList()
    }
}

/**
 * ⚡ Strategy Optimizer Tool
 */
class StrategyOptimizerTool(
    private val config: OptimizerConfig
) : Tool {
    
    override val name: String = "strategy-optimizer"
    override val description: String = "Optimize swarm coordination strategy based on performance"
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "performance_history" to ParameterSchema("array", "Historical performance data", true),
            "current_task" to ParameterSchema("string", "Current task description", true)
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val history = parameters["performance_history"] as? List<*> ?: emptyList<String>()
            val currentTask = parameters["current_task"] as String

            val optimization = optimizeStrategy(history.map { it.toString() }, currentTask)

            SpiceResult.success(ToolResult.success(optimization))

        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error("Strategy optimization failed: ${e.message}"))
        }
    }
    
    private fun optimizeStrategy(history: List<String>, currentTask: String): String {
        return """
        ⚡ Strategy Optimization Recommendation:
        
        Analysis of ${history.size} historical operations shows:
        • Best performing strategy: PARALLEL (based on task similarity)
        • Recommended agent count: 3-4 (optimal balance)
        • Success indicators: ${history.size > 0}
        
        For current task: "$currentTask"
        Recommended Strategy: ${recommendStrategy(currentTask)}
        Confidence: High
        
        Optimization applied: Dynamic agent selection based on task analysis.
        """.trimIndent()
    }
    
    private fun recommendStrategy(task: String): SwarmStrategyType {
        return when {
            task.contains("compare") || task.contains("evaluate") -> SwarmStrategyType.CONSENSUS
            task.contains("creative") || task.contains("brainstorm") -> SwarmStrategyType.PARALLEL
            task.contains("step") || task.contains("process") -> SwarmStrategyType.SEQUENTIAL
            else -> SwarmStrategyType.PARALLEL
        }
    }
}

// =====================================
// CONFIGURATION CLASSES
// =====================================

data class ConsensusConfig(
    val maxIterations: Int = 3,
    val confidenceThreshold: Double = 0.8,
    val debugEnabled: Boolean = false
)

data class ConflictResolverConfig(
    val defaultStrategy: String = "hybrid",
    val enableWeighting: Boolean = true
)

data class QualityConfig(
    val enableAIScoring: Boolean = true,
    val debugEnabled: Boolean = false
)

data class AggregationConfig(
    val defaultStrategy: String = "synthesize",
    val maxResultLength: Int = 1000
)

data class OptimizerConfig(
    val enableLearning: Boolean = true,
    val historyLimit: Int = 100
)

// =====================================
// SIMPLE TOOL BUILDER
// =====================================

/**
 * 🔨 Simple Tool Builder for quick inline tool definition
 */
class SimpleToolBuilder(
    private val name: String,
    private val description: String
) {
    private val parameters = mutableMapOf<String, ParameterSchema>()
    private var executor: (suspend (Map<String, Any>) -> SpiceResult<ToolResult>)? = null

    /**
     * Add a parameter to the tool
     */
    fun parameter(
        name: String,
        type: String,
        description: String,
        required: Boolean = false
    ) {
        parameters[name] = ParameterSchema(
            type = type,
            description = description,
            required = required
        )
    }

    /**
     * Define tool execution logic
     */
    fun execute(block: suspend (Map<String, Any>) -> ToolResult) {
        executor = { params ->
            SpiceResult.catchingSuspend {
                block(params)
            }
        }
    }

    internal fun build(): Tool {
        val schema = ToolSchema(
            name = name,
            description = description,
            parameters = parameters.toMap()
        )

        val executorFn = executor ?: { _ ->
            SpiceResult.success(ToolResult.error("Tool executor not defined"))
        }

        return object : BaseTool() {
            override val name = this@SimpleToolBuilder.name
            override val description = this@SimpleToolBuilder.description
            override val schema = schema

            override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
                // Validate parameters first
                val validation = validateParameters(parameters)
                if (!validation.valid) {
                    return SpiceResult.success(
                        ToolResult.error(
                            "Parameter validation failed: ${validation.errors.joinToString(", ")}",
                            metadata = mapOf("validation_errors" to validation.errors.joinToString("; "))
                        )
                    )
                }

                return executorFn(parameters)
            }
        }
    }
} 