package io.github.spice.dsl

import io.github.spice.*

/**
 * 🧩 Composition DSL
 * 
 * Enhanced composition patterns for intuitive agent and tool combinations.
 * Provides fluent API for building complex agent hierarchies and tool chains.
 */

/**
 * Composable agent interface
 */
interface ComposableAgent : Agent {
    fun compose(other: ComposableAgent): ComposableAgent
    fun withTool(tool: Tool): ComposableAgent
    fun withTools(vararg tools: Tool): ComposableAgent
    fun then(next: ComposableAgent): ComposableAgent
    fun parallel(vararg others: ComposableAgent): ComposableAgent
    fun conditional(condition: suspend (Message) -> Boolean, ifTrue: ComposableAgent, ifFalse: ComposableAgent? = null): ComposableAgent
}

/**
 * Composition strategy
 */
enum class CompositionStrategy {
    SEQUENTIAL,     // Execute one after another
    PARALLEL,       // Execute simultaneously
    CONDITIONAL,    // Execute based on condition
    PIPELINE,       // Pass output as input
    FALLBACK,       // Try alternatives on failure
    AGGREGATE       // Combine results
}

/**
 * Agent composition result
 */
data class CompositionResult(
    val messages: List<Message>,
    val executionTime: Long,
    val strategy: CompositionStrategy,
    val success: Boolean,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Composable agent implementation
 */
class ComposableAgentImpl(
    private val baseAgent: Agent
) : ComposableAgent {
    
    override val id: String = baseAgent.id
    override val name: String = baseAgent.name
    override val description: String = baseAgent.description
    override val capabilities: List<String> = baseAgent.capabilities
    
    override suspend fun processMessage(message: Message): Message {
        return baseAgent.processMessage(message)
    }
    
    override fun canHandle(message: Message): Boolean {
        return baseAgent.canHandle(message)
    }
    
    override fun getTools(): List<Tool> {
        return baseAgent.getTools()
    }
    
    override fun isReady(): Boolean {
        return baseAgent.isReady()
    }
    
    override fun compose(other: ComposableAgent): ComposableAgent {
        return CompositeAgent(listOf(this, other), CompositionStrategy.SEQUENTIAL)
    }
    
    override fun withTool(tool: Tool): ComposableAgent {
        return ToolEnhancedAgent(this, listOf(tool))
    }
    
    override fun withTools(vararg tools: Tool): ComposableAgent {
        return ToolEnhancedAgent(this, tools.toList())
    }
    
    override fun then(next: ComposableAgent): ComposableAgent {
        return SequentialComposite(this, next)
    }
    
    override fun parallel(vararg others: ComposableAgent): ComposableAgent {
        return ParallelComposite(listOf(this) + others.toList())
    }
    
    override fun conditional(
        condition: suspend (Message) -> Boolean,
        ifTrue: ComposableAgent,
        ifFalse: ComposableAgent?
    ): ComposableAgent {
        return ConditionalComposite(condition, ifTrue, ifFalse)
    }
}

/**
 * Composite agent base class
 */
abstract class CompositeAgent(
    protected val agents: List<ComposableAgent>,
    protected val strategy: CompositionStrategy
) : ComposableAgent {
    
    override val id: String = "composite-${agents.joinToString("-") { it.id }}"
    override val name: String = "Composite Agent (${strategy.name})"
    override val description: String = "Composite of: ${agents.joinToString(", ") { it.name }}"
    override val capabilities: List<String> = agents.flatMap { it.capabilities }.distinct()
    
    override fun canHandle(message: Message): Boolean {
        return agents.any { it.canHandle(message) }
    }
    
    override fun getTools(): List<Tool> {
        return agents.flatMap { it.getTools() }
    }
    
    override fun isReady(): Boolean {
        return agents.any { it.isReady() }
    }
    
    override fun compose(other: ComposableAgent): ComposableAgent {
        return CompositeAgent(agents + other, strategy)
    }
    
    override fun withTool(tool: Tool): ComposableAgent {
        return ToolEnhancedAgent(this, listOf(tool))
    }
    
    override fun withTools(vararg tools: Tool): ComposableAgent {
        return ToolEnhancedAgent(this, tools.toList())
    }
    
    override fun then(next: ComposableAgent): ComposableAgent {
        return SequentialComposite(this, next)
    }
    
    override fun parallel(vararg others: ComposableAgent): ComposableAgent {
        return ParallelComposite(listOf(this) + others.toList())
    }
    
    override fun conditional(
        condition: suspend (Message) -> Boolean,
        ifTrue: ComposableAgent,
        ifFalse: ComposableAgent?
    ): ComposableAgent {
        return ConditionalComposite(condition, ifTrue, ifFalse)
    }
}

/**
 * Sequential composite agent
 */
class SequentialComposite(
    private val first: ComposableAgent,
    private val second: ComposableAgent
) : CompositeAgent(listOf(first, second), CompositionStrategy.SEQUENTIAL) {
    
    override suspend fun processMessage(message: Message): Message {
        val startTime = System.currentTimeMillis()
        
        val firstResult = first.processMessage(message)
        val secondResult = second.processMessage(firstResult)
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return secondResult.copy(
            metadata = secondResult.metadata + mapOf(
                "composition_strategy" to strategy.name,
                "execution_time_ms" to executionTime.toString(),
                "intermediate_steps" to "2"
            )
        )
    }
}

/**
 * Parallel composite agent
 */
class ParallelComposite(
    agents: List<ComposableAgent>
) : CompositeAgent(agents, CompositionStrategy.PARALLEL) {
    
    override suspend fun processMessage(message: Message): Message {
        val startTime = System.currentTimeMillis()
        
        // Execute all agents in parallel
        val results = agents.map { agent ->
            kotlinx.coroutines.async {
                try {
                    agent.processMessage(message)
                } catch (e: Exception) {
                    message.createReply(
                        content = "Agent ${agent.id} failed: ${e.message}",
                        sender = id,
                        type = MessageType.ERROR
                    )
                }
            }
        }.map { it.await() }
        
        val executionTime = System.currentTimeMillis() - startTime
        
        // Aggregate results
        val successfulResults = results.filter { it.type != MessageType.ERROR }
        val aggregatedContent = successfulResults.joinToString("\n") { it.content }
        
        return message.createReply(
            content = aggregatedContent,
            sender = id,
            metadata = mapOf(
                "composition_strategy" to strategy.name,
                "execution_time_ms" to executionTime.toString(),
                "parallel_agents" to agents.size.toString(),
                "successful_results" to successfulResults.size.toString()
            )
        )
    }
}

/**
 * Conditional composite agent
 */
class ConditionalComposite(
    private val condition: suspend (Message) -> Boolean,
    private val ifTrue: ComposableAgent,
    private val ifFalse: ComposableAgent?
) : CompositeAgent(listOfNotNull(ifTrue, ifFalse), CompositionStrategy.CONDITIONAL) {
    
    override suspend fun processMessage(message: Message): Message {
        val startTime = System.currentTimeMillis()
        
        val selectedAgent = if (condition(message)) ifTrue else ifFalse
        
        val result = selectedAgent?.processMessage(message) ?: message.createReply(
            content = "No agent selected for conditional execution",
            sender = id,
            type = MessageType.ERROR
        )
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return result.copy(
            metadata = result.metadata + mapOf(
                "composition_strategy" to strategy.name,
                "execution_time_ms" to executionTime.toString(),
                "condition_result" to condition(message).toString(),
                "selected_agent" to (selectedAgent?.id ?: "none")
            )
        )
    }
}

/**
 * Tool-enhanced agent
 */
class ToolEnhancedAgent(
    private val baseAgent: ComposableAgent,
    private val additionalTools: List<Tool>
) : ComposableAgent by baseAgent {
    
    override val id: String = "${baseAgent.id}-enhanced"
    override val description: String = "${baseAgent.description} (Enhanced with ${additionalTools.size} tools)"
    
    override fun getTools(): List<Tool> {
        return baseAgent.getTools() + additionalTools
    }
    
    override suspend fun processMessage(message: Message): Message {
        // Check if message is a tool call for our additional tools
        if (message.type == MessageType.TOOL_CALL) {
            val toolName = message.metadata["toolName"]
            val additionalTool = additionalTools.find { it.name == toolName }
            
            if (additionalTool != null) {
                val parameters = message.metadata.toMap()
                val result = additionalTool.execute(parameters)
                
                return message.createReply(
                    content = result.result ?: result.error ?: "Tool execution completed",
                    sender = id,
                    type = if (result.success) MessageType.TOOL_RESULT else MessageType.ERROR,
                    metadata = result.metadata + mapOf("tool_enhanced" to "true")
                )
            }
        }
        
        // Otherwise delegate to base agent
        return baseAgent.processMessage(message)
    }
}

/**
 * Fallback composite agent
 */
class FallbackComposite(
    agents: List<ComposableAgent>
) : CompositeAgent(agents, CompositionStrategy.FALLBACK) {
    
    override suspend fun processMessage(message: Message): Message {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        
        for ((index, agent) in agents.withIndex()) {
            try {
                val result = agent.processMessage(message)
                
                // If successful, return immediately
                if (result.type != MessageType.ERROR) {
                    val executionTime = System.currentTimeMillis() - startTime
                    return result.copy(
                        metadata = result.metadata + mapOf(
                            "composition_strategy" to strategy.name,
                            "execution_time_ms" to executionTime.toString(),
                            "fallback_attempt" to (index + 1).toString(),
                            "successful_agent" to agent.id
                        )
                    )
                } else {
                    errors.add("Agent ${agent.id}: ${result.content}")
                }
            } catch (e: Exception) {
                errors.add("Agent ${agent.id}: ${e.message}")
            }
        }
        
        // All agents failed
        val executionTime = System.currentTimeMillis() - startTime
        return message.createReply(
            content = "All fallback agents failed: ${errors.joinToString("; ")}",
            sender = id,
            type = MessageType.ERROR,
            metadata = mapOf(
                "composition_strategy" to strategy.name,
                "execution_time_ms" to executionTime.toString(),
                "failed_agents" to agents.size.toString()
            )
        )
    }
}

/**
 * Pipeline composite agent
 */
class PipelineComposite(
    agents: List<ComposableAgent>
) : CompositeAgent(agents, CompositionStrategy.PIPELINE) {
    
    override suspend fun processMessage(message: Message): Message {
        val startTime = System.currentTimeMillis()
        var currentMessage = message
        
        for (agent in agents) {
            currentMessage = agent.processMessage(currentMessage)
            
            // Stop pipeline on error
            if (currentMessage.type == MessageType.ERROR) {
                break
            }
        }
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return currentMessage.copy(
            metadata = currentMessage.metadata + mapOf(
                "composition_strategy" to strategy.name,
                "execution_time_ms" to executionTime.toString(),
                "pipeline_stages" to agents.size.toString()
            )
        )
    }
}

/**
 * Composition DSL builder
 */
class CompositionBuilder {
    private val agents = mutableListOf<ComposableAgent>()
    private var strategy = CompositionStrategy.SEQUENTIAL
    
    /**
     * Add agent to composition
     */
    fun agent(agent: Agent): CompositionBuilder {
        agents.add(agent.toComposable())
        return this
    }
    
    /**
     * Add composable agent
     */
    fun agent(agent: ComposableAgent): CompositionBuilder {
        agents.add(agent)
        return this
    }
    
    /**
     * Set composition strategy
     */
    fun strategy(strategy: CompositionStrategy): CompositionBuilder {
        this.strategy = strategy
        return this
    }
    
    /**
     * Build composition
     */
    fun build(): ComposableAgent {
        require(agents.isNotEmpty()) { "At least one agent must be specified" }
        
        return when (strategy) {
            CompositionStrategy.SEQUENTIAL -> {
                agents.reduce { acc, agent -> acc.then(agent) }
            }
            CompositionStrategy.PARALLEL -> {
                val first = agents.first()
                val rest = agents.drop(1).toTypedArray()
                first.parallel(*rest)
            }
            CompositionStrategy.PIPELINE -> {
                PipelineComposite(agents)
            }
            CompositionStrategy.FALLBACK -> {
                FallbackComposite(agents)
            }
            else -> {
                CompositeAgent(agents, strategy)
            }
        }
    }
}

/**
 * Extension functions for agent composition
 */
fun Agent.toComposable(): ComposableAgent {
    return if (this is ComposableAgent) this else ComposableAgentImpl(this)
}

infix fun ComposableAgent.then(next: ComposableAgent): ComposableAgent {
    return this.then(next)
}

infix fun ComposableAgent.or(fallback: ComposableAgent): ComposableAgent {
    return FallbackComposite(listOf(this, fallback))
}

infix fun ComposableAgent.and(parallel: ComposableAgent): ComposableAgent {
    return this.parallel(parallel)
}

fun ComposableAgent.withTimeout(timeoutMs: Long): ComposableAgent {
    return TimeoutAgent(this, timeoutMs)
}

/**
 * Timeout agent wrapper
 */
class TimeoutAgent(
    private val baseAgent: ComposableAgent,
    private val timeoutMs: Long
) : ComposableAgent by baseAgent {
    
    override val id: String = "${baseAgent.id}-timeout"
    override val description: String = "${baseAgent.description} (Timeout: ${timeoutMs}ms)"
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                baseAgent.processMessage(message)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            message.createReply(
                content = "Agent execution timed out after ${timeoutMs}ms",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf(
                    "timeout_ms" to timeoutMs.toString(),
                    "error_type" to "timeout"
                )
            )
        }
    }
}

/**
 * DSL entry points
 */
fun composition(init: CompositionBuilder.() -> Unit): ComposableAgent {
    val builder = CompositionBuilder()
    builder.init()
    return builder.build()
}

fun agents(vararg agents: Agent): CompositionBuilder {
    val builder = CompositionBuilder()
    agents.forEach { builder.agent(it) }
    return builder
}

/**
 * Predefined composition patterns
 */
object CompositionPatterns {
    
    /**
     * Load balancer pattern
     */
    fun loadBalancer(agents: List<ComposableAgent>): ComposableAgent {
        var currentIndex = 0
        
        return object : ComposableAgent {
            override val id = "load-balancer"
            override val name = "Load Balancer"
            override val description = "Distributes load across ${agents.size} agents"
            override val capabilities = agents.flatMap { it.capabilities }.distinct()
            
            override suspend fun processMessage(message: Message): Message {
                val selectedAgent = agents[currentIndex % agents.size]
                currentIndex++
                
                return selectedAgent.processMessage(message).copy(
                    metadata = message.metadata + mapOf(
                        "load_balancer" to "true",
                        "selected_agent" to selectedAgent.id,
                        "agent_pool_size" to agents.size.toString()
                    )
                )
            }
            
            override fun canHandle(message: Message) = agents.any { it.canHandle(message) }
            override fun getTools() = agents.flatMap { it.getTools() }
            override fun isReady() = agents.any { it.isReady() }
            
            override fun compose(other: ComposableAgent) = CompositeAgent(listOf(this, other), CompositionStrategy.SEQUENTIAL)
            override fun withTool(tool: Tool) = ToolEnhancedAgent(this, listOf(tool))
            override fun withTools(vararg tools: Tool) = ToolEnhancedAgent(this, tools.toList())
            override fun then(next: ComposableAgent) = SequentialComposite(this, next)
            override fun parallel(vararg others: ComposableAgent) = ParallelComposite(listOf(this) + others.toList())
            override fun conditional(condition: suspend (Message) -> Boolean, ifTrue: ComposableAgent, ifFalse: ComposableAgent?) = ConditionalComposite(condition, ifTrue, ifFalse)
        }
    }
    
    /**
     * Circuit breaker pattern
     */
    fun circuitBreaker(
        agent: ComposableAgent,
        maxFailures: Int = 5,
        timeoutMs: Long = 60000
    ): ComposableAgent {
        var failureCount = 0
        var lastFailureTime = 0L
        
        return object : ComposableAgent by agent {
            override val id = "${agent.id}-circuit-breaker"
            override val description = "${agent.description} (Circuit Breaker)"
            
            override suspend fun processMessage(message: Message): Message {
                val now = System.currentTimeMillis()
                
                // Check if circuit is open
                if (failureCount >= maxFailures && (now - lastFailureTime) < timeoutMs) {
                    return message.createReply(
                        content = "Circuit breaker is open. Service temporarily unavailable.",
                        sender = id,
                        type = MessageType.ERROR,
                        metadata = mapOf(
                            "circuit_breaker" to "open",
                            "failure_count" to failureCount.toString(),
                            "retry_after_ms" to (timeoutMs - (now - lastFailureTime)).toString()
                        )
                    )
                }
                
                // Reset if timeout has passed
                if ((now - lastFailureTime) >= timeoutMs) {
                    failureCount = 0
                }
                
                return try {
                    val result = agent.processMessage(message)
                    
                    if (result.type == MessageType.ERROR) {
                        failureCount++
                        lastFailureTime = now
                    } else {
                        failureCount = 0 // Reset on success
                    }
                    
                    result
                } catch (e: Exception) {
                    failureCount++
                    lastFailureTime = now
                    
                    message.createReply(
                        content = "Agent failed: ${e.message}",
                        sender = id,
                        type = MessageType.ERROR,
                        metadata = mapOf(
                            "circuit_breaker" to "failure",
                            "failure_count" to failureCount.toString()
                        )
                    )
                }
            }
        }
    }
} 