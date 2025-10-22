package io.github.noailabs.spice.swarm

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.error.SpiceResult

/**
 * Mock agent registry function (replace with actual AgentRegistry when available)
 */
private fun getAgentFromRegistry(agentId: String): Agent? {
    // For now, return null - in real implementation, use AgentRegistry.get(agentId)
    return null
}

/**
 * 🤖 Swarm Agent DSL - Revolutionary Multi-Agent Coordination
 * 
 * Ultimate DSL for creating sophisticated swarm agents that coordinate
 * multiple specialized agents to solve complex problems.
 */

/**
 * 🚀 Build SwarmAgent with Ultimate DSL
 */
fun buildSwarmAgent(config: SwarmAgentBuilder.() -> Unit): SwarmAgent {
    val builder = SwarmAgentBuilder()
    builder.config()
    return builder.build()
}

/**
 * 🔧 SwarmAgent Builder with Ultimate DSL
 */
class SwarmAgentBuilder {
    var id: String = "swarm-${System.currentTimeMillis()}"
    var name: String = ""
    var description: String = ""

    // Swarm-specific configurations
    private val memberAgents = mutableMapOf<String, Agent>()
    private var coordinatorType: CoordinatorType = CoordinatorType.SMART
    private var swarmConfig = SwarmConfig()
    private var defaultStrategy: SwarmStrategyType = SwarmStrategyType.PARALLEL
    private var llmCoordinatorAgent: Agent? = null  // Optional LLM for AI-powered coordination
    
    /**
     * Configure swarm behavior
     */
    fun config(block: SwarmConfigBuilder.() -> Unit) {
        val configBuilder = SwarmConfigBuilder()
        configBuilder.block()
        swarmConfig = configBuilder.build()
    }
    
    /**
     * Add member agents to the swarm
     */
    fun agents(vararg agentIds: String) {
        agentIds.forEach { agentId ->
            // Try to get from global agent registry (mock for now)
            val agent = getAgentFromRegistry(agentId)
            if (agent != null) {
                memberAgents[agentId] = agent
            } else {
                println("⚠️ Warning: Agent '$agentId' not found in registry")
            }
        }
    }
    
    /**
     * Add agents with direct references
     */
    fun addAgent(agentId: String, agent: Agent) {
        memberAgents[agentId] = agent
    }
    
    /**
     * Add multiple agents with map
     */
    fun addAgents(agents: Map<String, Agent>) {
        memberAgents.putAll(agents)
    }
    
    /**
     * Configure swarm strategy
     */
    fun defaultStrategy(strategy: SwarmStrategyType) {
        defaultStrategy = strategy
    }
    
    /**
     * Set coordinator type
     */
    fun coordinator(type: CoordinatorType) {
        coordinatorType = type
    }

    /**
     * Set LLM agent for AI-powered coordination
     * When set with CoordinatorType.AI_POWERED, this agent will be used
     * for meta-coordination decisions.
     */
    fun llmCoordinator(agent: Agent) {
        llmCoordinatorAgent = agent
    }

    /**
     * Quick setup: Use AI-powered coordinator with given LLM agent
     */
    fun aiCoordinator(llmAgent: Agent) {
        coordinatorType = CoordinatorType.AI_POWERED
        llmCoordinatorAgent = llmAgent
    }
    
    /**
     * 🔧 Configure specialized swarm tools for coordination
     */
    fun swarmTools(block: SwarmToolBuilder.() -> Unit) {
        val toolBuilder = SwarmToolBuilder()
        toolBuilder.block()
        swarmConfig = swarmConfig.copy(
            swarmTools = toolBuilder.build()
        )
    }
    
    /**
     * Quick swarm setup with member agent creation
     */
    fun quickSwarm(block: QuickSwarmBuilder.() -> Unit) {
        val quickBuilder = QuickSwarmBuilder()
        quickBuilder.block()
        memberAgents.putAll(quickBuilder.getAgents())
    }
    
    internal fun build(): SwarmAgent {
        require(name.isNotEmpty()) { "Swarm agent name is required" }
        require(memberAgents.isNotEmpty()) { "At least one member agent is required" }
        
        val coordinator = createCoordinator()
        
        return SwarmAgent(
            id = id,
            name = name,
            description = description,
            memberAgents = memberAgents.toMap(),
            coordinator = coordinator,
            config = swarmConfig
        )
    }
    
    private fun createCoordinator(): SwarmCoordinator {
        return when (coordinatorType) {
            CoordinatorType.SMART -> SmartSwarmCoordinator(memberAgents, swarmConfig)
            CoordinatorType.SIMPLE -> SimpleSwarmCoordinator(memberAgents, swarmConfig)
            CoordinatorType.AI_POWERED -> AISwarmCoordinator(memberAgents, swarmConfig, llmCoordinatorAgent)
        }
    }
}

/**
 * 🛠️ Swarm Configuration Builder
 */
class SwarmConfigBuilder {
    var debugEnabled: Boolean = false
    var maxConcurrentOperations: Int = 10
    var timeoutMs: Long = 30000
    var retryAttempts: Int = 3
    
    fun debug(enabled: Boolean = true) {
        debugEnabled = enabled
    }
    
    fun timeout(milliseconds: Long) {
        timeoutMs = milliseconds
    }
    
    fun maxOperations(count: Int) {
        maxConcurrentOperations = count
    }
    
    fun retries(attempts: Int) {
        retryAttempts = attempts
    }
    
    internal fun build(): SwarmConfig {
        return SwarmConfig(
            debugEnabled = debugEnabled,
            maxConcurrentOperations = maxConcurrentOperations,
            timeoutMs = timeoutMs,
            retryAttempts = retryAttempts
        )
    }
}

/**
 * ⚡ Quick Swarm Builder for rapid prototyping
 */
class QuickSwarmBuilder {
    private val agents = mutableMapOf<String, Agent>()
    
    /**
     * Add Claude agent
     */
    fun claudeAgent(
        id: String = "claude",
        name: String = "Claude Agent",
        apiKey: String? = null
    ) {
        if (apiKey != null) {
            // Create actual Claude agent - simplified for demo
            val agent = buildAgent {
                this.name = name
                description = "Claude-powered AI agent"
                handle { comm ->
                    SpiceResult.success(comm.reply(
                        content = "🤖 Claude response to: ${comm.content}",
                        from = id
                    ))
                }
            }
            agents[id] = agent
        } else {
            // Mock Claude agent for testing
            agents[id] = createMockAgent(id, name, "Claude-style responses")
        }
    }
    
    /**
     * Add GPT agent
     */
    fun gptAgent(
        id: String = "gpt",
        name: String = "GPT Agent", 
        apiKey: String? = null
    ) {
        if (apiKey != null) {
            // Create actual GPT agent - simplified for demo
            val agent = buildAgent {
                this.name = name
                description = "GPT-powered AI agent"
                handle { comm ->
                    SpiceResult.success(comm.reply(
                        content = "🧠 GPT response to: ${comm.content}",
                        from = id
                    ))
                }
            }
            agents[id] = agent
        } else {
            // Mock GPT agent for testing
            agents[id] = createMockAgent(id, name, "GPT-style responses")
        }
    }
    
    /**
     * Add specialist agent
     */
    fun specialist(
        id: String,
        name: String,
        speciality: String,
        responsePattern: String = "Specialist analysis"
    ) {
        agents[id] = createMockAgent(id, name, "$speciality: $responsePattern")
    }
    
    /**
     * Add research agent
     */
    fun researchAgent(
        id: String = "researcher",
        name: String = "Research Agent"
    ) {
        agents[id] = createMockAgent(id, name, "📚 Research findings")
    }
    
    /**
     * Add analysis agent
     */
    fun analysisAgent(
        id: String = "analyst", 
        name: String = "Analysis Agent"
    ) {
        agents[id] = createMockAgent(id, name, "📊 Analytical insights")
    }
    
    /**
     * Add creative agent
     */
    fun creativeAgent(
        id: String = "creative",
        name: String = "Creative Agent"
    ) {
        agents[id] = createMockAgent(id, name, "🎨 Creative solutions")
    }
    
    private fun createMockAgent(id: String, name: String, responsePrefix: String): Agent {
        return buildAgent {
            this.name = name
            description = "Mock agent for swarm testing"

            handle { comm ->
                SpiceResult.success(comm.reply(
                    content = "$responsePrefix: ${comm.content}",
                    from = id,
                    data = mapOf(
                        "agent_type" to "mock",
                        "speciality" to responsePrefix
                    )
                ))
            }
        }
    }
    
    internal fun getAgents(): Map<String, Agent> = agents.toMap()
}

/**
 * 🤔 Simple Coordinator Implementation (for lightweight swarms)
 */
class SimpleSwarmCoordinator(
    private val memberAgents: Map<String, Agent>,
    private val config: SwarmConfig
) : SwarmCoordinator {
    
    override suspend fun analyzeTask(task: String): SwarmStrategy {
        // Simple strategy: always use all agents in parallel
        return SwarmStrategy(
            type = SwarmStrategyType.PARALLEL,
            selectedAgents = memberAgents.keys.toList(),
            confidence = 0.7
        )
    }
    
    override suspend fun aggregateResults(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult {
        val successfulResults = results.filter { it.success }
        val content = if (successfulResults.isEmpty()) {
            "🤖 No successful results from swarm agents"
        } else {
            "🤖 Simple Swarm Results:\n\n${successfulResults.joinToString("\n") { "• ${it.content}" }}"
        }
        
        return SwarmResult(
            content = content,
            successRate = successfulResults.size.toDouble() / results.size,
            agentResults = results
        )
    }
    
    override suspend fun buildConsensus(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult {
        return aggregateResults(results, strategy) // Simple consensus = aggregation
    }
    
    override suspend fun selectBestResult(results: SwarmResult, strategy: SwarmStrategy): SwarmResult {
        val best = results.agentResults.filter { it.success }.randomOrNull()
        return SwarmResult(
            content = best?.content ?: "No results",
            successRate = results.successRate,
            agentResults = results.agentResults
        )
    }
}

/**
 * 🧠 AI-Powered Coordinator - LLM-Enhanced Meta-Coordination
 *
 * Uses an LLM agent to make sophisticated decisions about:
 * - Task analysis and strategy selection
 * - Intelligent result aggregation
 * - Consensus building
 * - Best result selection
 */
class AISwarmCoordinator(
    private val memberAgents: Map<String, Agent>,
    private val config: SwarmConfig,
    private val llmAgent: Agent? = null  // Optional LLM for meta-coordination
) : SwarmCoordinator {

    private val fallbackCoordinator = SmartSwarmCoordinator(memberAgents, config)

    override suspend fun analyzeTask(task: String): SwarmStrategy {
        if (llmAgent == null) {
            return fallbackCoordinator.analyzeTask(task)
        }

        return try {
            val agentCapabilities = memberAgents.map { (id, agent) ->
                "$id: ${agent.name} - ${agent.capabilities.joinToString(", ")}"
            }.joinToString("\n")

            val prompt = """
                Analyze this task and determine the optimal swarm coordination strategy.

                Task: "$task"

                Available Agents:
                $agentCapabilities

                Available Strategies:
                - PARALLEL: Execute all agents simultaneously (good for independent analyses)
                - SEQUENTIAL: Execute agents in sequence, passing results forward (good for step-by-step processing)
                - CONSENSUS: Build consensus through multi-round discussion (good for decision-making)
                - COMPETITION: Compete and select best result (good for creative tasks)
                - HIERARCHICAL: Hierarchical delegation with levels (good for complex tasks)

                Respond ONLY with valid JSON in this exact format (no additional text):
                {
                  "strategy": "PARALLEL|SEQUENTIAL|CONSENSUS|COMPETITION|HIERARCHICAL",
                  "selectedAgents": ["agent1", "agent2"],
                  "confidence": 0.85,
                  "reasoning": "Brief explanation"
                }
            """.trimIndent()

            val comm = Comm(
                content = prompt,
                from = "ai-coordinator",
                to = llmAgent.id,
                type = CommType.TEXT
            )

            val result = llmAgent.processComm(comm)

            result.fold(
                onSuccess = { response ->
                    parseStrategyFromLLM(response.content, task)
                },
                onFailure = {
                    if (config.debugEnabled) {
                        println("[AI-COORDINATOR] LLM task analysis failed, using fallback")
                    }
                    fallbackCoordinator.analyzeTask(task)
                }
            )
        } catch (e: Exception) {
            if (config.debugEnabled) {
                println("[AI-COORDINATOR] Error in analyzeTask: ${e.message}")
            }
            fallbackCoordinator.analyzeTask(task)
        }
    }

    override suspend fun aggregateResults(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult {
        if (llmAgent == null || results.isEmpty()) {
            return fallbackCoordinator.aggregateResults(results, strategy)
        }

        return try {
            val successfulResults = results.filter { it.success }
            if (successfulResults.isEmpty()) {
                return fallbackCoordinator.aggregateResults(results, strategy)
            }

            val resultsText = successfulResults.mapIndexed { index, result ->
                "Agent ${result.agentId} (${index + 1}/${successfulResults.size}):\n${result.content}"
            }.joinToString("\n\n---\n\n")

            val prompt = """
                Synthesize these agent results into a comprehensive, coherent response.

                Strategy Used: ${strategy.type}
                Number of Agents: ${successfulResults.size}/${results.size}

                Agent Results:
                $resultsText

                Your task:
                1. Identify key patterns and insights across all results
                2. Note any contradictions or disagreements
                3. Synthesize into a unified, comprehensive response
                4. Maintain important details from individual agents

                Provide a well-structured synthesis that combines the best of all perspectives.
            """.trimIndent()

            val comm = Comm(
                content = prompt,
                from = "ai-coordinator",
                to = llmAgent.id,
                type = CommType.TEXT
            )

            val result = llmAgent.processComm(comm)

            result.fold(
                onSuccess = { response ->
                    SwarmResult(
                        content = "🧠 AI-Synthesized Result:\n\n${response.content}",
                        successRate = successfulResults.size.toDouble() / results.size,
                        agentResults = results,
                        timestamp = System.currentTimeMillis()
                    )
                },
                onFailure = {
                    if (config.debugEnabled) {
                        println("[AI-COORDINATOR] LLM aggregation failed, using fallback")
                    }
                    fallbackCoordinator.aggregateResults(results, strategy)
                }
            )
        } catch (e: Exception) {
            if (config.debugEnabled) {
                println("[AI-COORDINATOR] Error in aggregateResults: ${e.message}")
            }
            fallbackCoordinator.aggregateResults(results, strategy)
        }
    }

    override suspend fun buildConsensus(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult {
        if (llmAgent == null || results.isEmpty()) {
            return fallbackCoordinator.buildConsensus(results, strategy)
        }

        return try {
            val successfulResults = results.filter { it.success }
            if (successfulResults.isEmpty()) {
                return fallbackCoordinator.buildConsensus(results, strategy)
            }

            val perspectivesText = successfulResults.mapIndexed { index, result ->
                "Perspective ${index + 1} (Agent ${result.agentId}):\n${result.content}"
            }.joinToString("\n\n")

            val prompt = """
                Build a sophisticated consensus from these diverse agent perspectives.

                Number of Perspectives: ${successfulResults.size}

                $perspectivesText

                Your task:
                1. Identify areas of strong agreement across agents
                2. Acknowledge areas of disagreement or different viewpoints
                3. Weigh the quality and reasoning of each perspective
                4. Build a consensus that represents the collective intelligence
                5. Note confidence level and any caveats

                Provide a consensus statement that:
                - Highlights key agreements
                - Addresses contradictions thoughtfully
                - Synthesizes the best reasoning from all perspectives
                - Indicates confidence level
            """.trimIndent()

            val comm = Comm(
                content = prompt,
                from = "ai-coordinator",
                to = llmAgent.id,
                type = CommType.TEXT
            )

            val result = llmAgent.processComm(comm)

            result.fold(
                onSuccess = { response ->
                    SwarmResult(
                        content = "🤝 AI-Powered Consensus:\n\n${response.content}",
                        successRate = successfulResults.size.toDouble() / results.size,
                        agentResults = results,
                        timestamp = System.currentTimeMillis()
                    )
                },
                onFailure = {
                    if (config.debugEnabled) {
                        println("[AI-COORDINATOR] LLM consensus failed, using fallback")
                    }
                    fallbackCoordinator.buildConsensus(results, strategy)
                }
            )
        } catch (e: Exception) {
            if (config.debugEnabled) {
                println("[AI-COORDINATOR] Error in buildConsensus: ${e.message}")
            }
            fallbackCoordinator.buildConsensus(results, strategy)
        }
    }

    override suspend fun selectBestResult(results: SwarmResult, strategy: SwarmStrategy): SwarmResult {
        if (llmAgent == null) {
            return fallbackCoordinator.selectBestResult(results, strategy)
        }

        return try {
            val successfulResults = results.agentResults.filter { it.success }
            if (successfulResults.isEmpty()) {
                return fallbackCoordinator.selectBestResult(results, strategy)
            }

            val competitorsText = successfulResults.mapIndexed { index, result ->
                "Candidate ${index + 1} (Agent ${result.agentId}):\n${result.content}"
            }.joinToString("\n\n---\n\n")

            val prompt = """
                Evaluate these competing results and select the best one.

                Number of Candidates: ${successfulResults.size}

                $competitorsText

                Evaluation Criteria:
                - Quality and depth of analysis
                - Accuracy and correctness
                - Completeness of response
                - Clarity and coherence
                - Practical value

                Your task:
                1. Evaluate each result against the criteria
                2. Compare strengths and weaknesses
                3. Select the best overall result
                4. Explain your selection reasoning

                Format your response as:
                WINNER: Agent [agent_id]
                REASONING: [Your detailed reasoning]

                [Then include or enhance the winning result]
            """.trimIndent()

            val comm = Comm(
                content = prompt,
                from = "ai-coordinator",
                to = llmAgent.id,
                type = CommType.TEXT
            )

            val result = llmAgent.processComm(comm)

            result.fold(
                onSuccess = { response ->
                    SwarmResult(
                        content = "🏆 AI-Selected Best Result:\n\n${response.content}",
                        successRate = results.successRate,
                        agentResults = results.agentResults,
                        timestamp = System.currentTimeMillis()
                    )
                },
                onFailure = {
                    if (config.debugEnabled) {
                        println("[AI-COORDINATOR] LLM selection failed, using fallback")
                    }
                    fallbackCoordinator.selectBestResult(results, strategy)
                }
            )
        } catch (e: Exception) {
            if (config.debugEnabled) {
                println("[AI-COORDINATOR] Error in selectBestResult: ${e.message}")
            }
            fallbackCoordinator.selectBestResult(results, strategy)
        }
    }

    /**
     * Parse strategy from LLM response (JSON or natural language)
     */
    private fun parseStrategyFromLLM(content: String, originalTask: String): SwarmStrategy {
        return try {
            // Try to extract JSON from response
            val jsonStart = content.indexOf('{')
            val jsonEnd = content.lastIndexOf('}') + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonContent = content.substring(jsonStart, jsonEnd)

                // Simple JSON parsing (in production, use kotlinx.serialization)
                val strategyType = extractJsonField(jsonContent, "strategy")?.let { strategyName ->
                    try {
                        SwarmStrategyType.valueOf(strategyName)
                    } catch (e: Exception) {
                        SwarmStrategyType.PARALLEL
                    }
                } ?: SwarmStrategyType.PARALLEL

                val selectedAgentsJson = extractJsonArrayField(jsonContent, "selectedAgents")
                val selectedAgents = if (selectedAgentsJson.isNotEmpty()) {
                    selectedAgentsJson.filter { memberAgents.containsKey(it) }
                } else {
                    memberAgents.keys.toList()
                }

                val confidence = extractJsonField(jsonContent, "confidence")?.toDoubleOrNull() ?: 0.8

                SwarmStrategy(
                    type = strategyType,
                    selectedAgents = selectedAgents.ifEmpty { memberAgents.keys.toList() },
                    confidence = confidence.coerceIn(0.0, 1.0)
                )
            } else {
                // Fallback to keyword-based parsing
                parseStrategyFromKeywords(content, originalTask)
            }
        } catch (e: Exception) {
            if (config.debugEnabled) {
                println("[AI-COORDINATOR] Failed to parse LLM strategy, using fallback: ${e.message}")
            }
            // Return default strategy instead of calling suspend function
            parseStrategyFromKeywords(originalTask, originalTask)
        }
    }

    /**
     * Extract field from simple JSON string
     */
    private fun extractJsonField(json: String, field: String): String? {
        val pattern = """"$field"\s*:\s*"([^"]*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    /**
     * Extract array field from simple JSON string
     */
    private fun extractJsonArrayField(json: String, field: String): List<String> {
        val pattern = """"$field"\s*:\s*\[([^\]]*)]""".toRegex()
        val arrayContent = pattern.find(json)?.groupValues?.get(1) ?: return emptyList()
        return arrayContent.split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }

    /**
     * Parse strategy from natural language keywords
     */
    private fun parseStrategyFromKeywords(content: String, originalTask: String): SwarmStrategy {
        val lowerContent = content.lowercase()

        val strategyType = when {
            lowerContent.contains("consensus") -> SwarmStrategyType.CONSENSUS
            lowerContent.contains("competition") || lowerContent.contains("compete") -> SwarmStrategyType.COMPETITION
            lowerContent.contains("sequential") || lowerContent.contains("sequence") -> SwarmStrategyType.SEQUENTIAL
            lowerContent.contains("hierarchical") || lowerContent.contains("hierarchy") -> SwarmStrategyType.HIERARCHICAL
            else -> SwarmStrategyType.PARALLEL
        }

        return SwarmStrategy(
            type = strategyType,
            selectedAgents = memberAgents.keys.toList(),
            confidence = 0.7
        )
    }
}

/**
 * 🎯 Coordinator Types
 */
enum class CoordinatorType {
    SIMPLE,      // Basic coordination
    SMART,       // Intelligent coordination (default)
    AI_POWERED   // AI-enhanced coordination (future)
}

// =====================================
// CONVENIENCE FUNCTIONS  
// =====================================

/**
 * 🚀 Quick swarm creation functions
 */

/**
 * Create research swarm
 */
fun researchSwarm(
    name: String = "Research Swarm",
    debugEnabled: Boolean = false
): SwarmAgent {
    return buildSwarmAgent {
        this.name = name
        description = "Multi-agent research and analysis swarm"
        
        config {
            debug(debugEnabled)
            timeout(45000) // Research takes time
        }
        
        quickSwarm {
            researchAgent("researcher", "Lead Researcher")
            analysisAgent("analyst", "Data Analyst") 
            specialist("domain-expert", "Domain Expert", "Expert analysis")
        }
        
        defaultStrategy(SwarmStrategyType.SEQUENTIAL)
    }
}

/**
 * Create creative swarm
 */
fun creativeSwarm(
    name: String = "Creative Swarm",
    debugEnabled: Boolean = false
): SwarmAgent {
    return buildSwarmAgent {
        this.name = name
        description = "Multi-agent creative collaboration swarm"
        
        config {
            debug(debugEnabled)
            maxOperations(5)
        }
        
        quickSwarm {
            creativeAgent("creative1", "Creative Director")
            creativeAgent("creative2", "Design Specialist")
            specialist("critic", "Creative Critic", "Constructive feedback")
        }
        
        defaultStrategy(SwarmStrategyType.CONSENSUS)
    }
}

/**
 * Create decision-making swarm
 */
fun decisionSwarm(
    name: String = "Decision Swarm", 
    debugEnabled: Boolean = false
): SwarmAgent {
    return buildSwarmAgent {
        this.name = name
        description = "Multi-agent decision making swarm"
        
        config {
            debug(debugEnabled)
            retries(2)
        }
        
        quickSwarm {
            analysisAgent("pro-analyst", "Pro Analyst")
            analysisAgent("con-analyst", "Con Analyst")
            specialist("judge", "Decision Judge", "Final judgment")
        }
        
        defaultStrategy(SwarmStrategyType.COMPETITION)
    }
}

/**
 * Create AI powerhouse swarm (when you have real API keys)
 */
fun aiPowerhouseSwarm(
    name: String = "AI Powerhouse",
    claudeApiKey: String? = null,
    gptApiKey: String? = null,
    debugEnabled: Boolean = false
): SwarmAgent {
    return buildSwarmAgent {
        this.name = name
        description = "Premium AI agents collaboration swarm"
        
        config {
            debug(debugEnabled)
            timeout(60000) // AI takes time
            maxOperations(3) // Premium agents
        }
        
        quickSwarm {
            if (claudeApiKey != null) {
                claudeAgent("claude", "Claude-3.5 Sonnet", claudeApiKey)
            }
            if (gptApiKey != null) {
                gptAgent("gpt", "GPT-4o", gptApiKey)
            }
            // Add mock agents if no API keys
            if (claudeApiKey == null && gptApiKey == null) {
                claudeAgent("claude-mock", "Claude (Mock)")
                gptAgent("gpt-mock", "GPT (Mock)")
            }
        }
        
        defaultStrategy(SwarmStrategyType.CONSENSUS)
        coordinator(CoordinatorType.SMART)
    }
} 