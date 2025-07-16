package io.github.spice.dsl.experimental

import io.github.spice.*
import io.github.spice.dsl.AgentRegistry
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * 🧪 Experimental DSL Extensions
 * 
 * Advanced and experimental DSL features that extend the core Agent > Flow > Tool structure.
 * These features are opt-in and require explicit experimental DSL usage.
 */

// =====================================
// EXPERIMENTAL WRAPPER
// =====================================

/**
 * Experimental DSL context
 */
class ExperimentalDSLContext {
    /**
     * Conditional flow builder
     */
    fun conditional(init: ConditionalFlowBuilder.() -> Unit): ConditionalFlow {
        val builder = ConditionalFlowBuilder()
        builder.init()
        return builder.build()
    }
    
    /**
     * Reactive agent builder
     */
    fun reactive(agent: Agent, config: StreamConfig = StreamConfig()): ReactiveAgent {
        return ReactiveAgentWrapper(agent, config)
    }
    
    /**
     * Type-safe message builder
     */
    inline fun <reified T : MessageContent> typedMessage(): TypedMessageBuilder<T> {
        return TypedMessageBuilder(T::class)
    }
    
    /**
     * Composition builder
     */
    fun composition(init: CompositionBuilder.() -> Unit): ComposableAgent {
        val builder = CompositionBuilder()
        builder.init()
        return builder.build()
    }
    
    /**
     * Workflow builder (simplified from complex version)
     */
    fun workflow(id: String, init: SimpleWorkflowBuilder.() -> Unit): SimpleWorkflow {
        val builder = SimpleWorkflowBuilder(id)
        builder.init()
        return builder.build()
    }
}

// =====================================
// SIMPLIFIED CONDITIONAL FLOW
// =====================================

/**
 * Simplified conditional flow builder
 */
class ConditionalFlowBuilder {
    private val conditions = mutableListOf<ConditionalBranch>()
    private var elseBranch: (suspend (Message) -> Message)? = null
    
    /**
     * Add when/then condition
     */
    fun whenThen(
        condition: suspend (Message) -> Boolean,
        action: suspend (Message) -> Message
    ) {
        conditions.add(ConditionalBranch(condition, action))
    }
    
    /**
     * Set else branch
     */
    fun otherwise(action: suspend (Message) -> Message) {
        elseBranch = action
    }
    
    fun build(): ConditionalFlow {
        return ConditionalFlow(conditions.toList(), elseBranch)
    }
}

/**
 * Conditional branch
 */
data class ConditionalBranch(
    val condition: suspend (Message) -> Boolean,
    val action: suspend (Message) -> Message
)

/**
 * Simplified conditional flow
 */
class ConditionalFlow(
    private val branches: List<ConditionalBranch>,
    private val elseBranch: (suspend (Message) -> Message)?
) {
    suspend fun execute(message: Message): Message {
        for (branch in branches) {
            if (branch.condition(message)) {
                return branch.action(message)
            }
        }
        return elseBranch?.invoke(message) ?: message
    }
}

// =====================================
// SIMPLIFIED REACTIVE STREAMS
// =====================================

/**
 * Stream configuration
 */
data class StreamConfig(
    val bufferSize: Int = 64,
    val parallelism: Int = 1,
    val timeout: Duration = Duration.INFINITE
)

/**
 * Reactive agent interface
 */
interface ReactiveAgent : Agent {
    fun processStream(input: Flow<Message>): Flow<Message>
}

/**
 * Reactive agent wrapper
 */
class ReactiveAgentWrapper(
    private val baseAgent: Agent,
    private val config: StreamConfig
) : ReactiveAgent, Agent by baseAgent {
    
    override fun processStream(input: Flow<Message>): Flow<Message> {
        return kotlinx.coroutines.flow.flow {
            input.collect { message ->
                try {
                    emit(baseAgent.processMessage(message))
                } catch (e: Exception) {
                    emit(message.createReply(
                        content = "Stream processing failed: ${e.message}",
                        sender = baseAgent.id,
                        type = MessageType.ERROR
                    ))
                }
            }
        }
    }
}

// =====================================
// SIMPLIFIED TYPE SAFETY
// =====================================

/**
 * Message content types (simplified)
 */
sealed class MessageContent

data class TextContent(val text: String) : MessageContent()
data class DataContent(val data: Map<String, Any>) : MessageContent()
data class ErrorContent(val error: String) : MessageContent()

/**
 * Typed message interface
 */
interface TypedMessage<out T : MessageContent> {
    val message: Message
    val content: T
}

/**
 * Typed message builder
 */
class TypedMessageBuilder<T : MessageContent>(
    private val contentType: kotlin.reflect.KClass<T>
) {
    private var sender: String = ""
    private lateinit var content: T
    
    fun from(senderId: String): TypedMessageBuilder<T> {
        sender = senderId
        return this
    }
    
    fun withContent(content: T): TypedMessageBuilder<T> {
        this.content = content
        return this
    }
    
    fun build(): TypedMessage<T> {
        require(sender.isNotEmpty()) { "Sender required" }
        require(::content.isInitialized) { "Content required" }
        
        val contentValue = content
        val messageContent = when (contentValue) {
            is TextContent -> contentValue.text
            is DataContent -> contentValue.data.toString()
            is ErrorContent -> contentValue.error
            else -> contentValue.toString()
        }
        
        val message = Message(
            content = messageContent,
            sender = sender,
            type = when (contentValue) {
                is TextContent -> MessageType.TEXT
                is DataContent -> MessageType.DATA
                is ErrorContent -> MessageType.ERROR
                else -> MessageType.TEXT
            }
        )
        
        return TypedMessageImpl(message, contentValue)
    }
}

/**
 * Typed message implementation
 */
class TypedMessageImpl<T : MessageContent>(
    override val message: Message,
    override val content: T
) : TypedMessage<T>

// =====================================
// SIMPLIFIED COMPOSITION
// =====================================

/**
 * Composable agent interface
 */
interface ComposableAgent : Agent {
    fun then(next: ComposableAgent): ComposableAgent
    fun parallel(other: ComposableAgent): ComposableAgent
}

/**
 * Composition builder
 */
class CompositionBuilder {
    private val agents = mutableListOf<Agent>()
    private var strategy = CompositionStrategy.SEQUENTIAL
    
    enum class CompositionStrategy { SEQUENTIAL, PARALLEL }
    
    fun agent(agent: Agent): CompositionBuilder {
        agents.add(agent)
        return this
    }
    
    fun strategy(strategy: CompositionStrategy): CompositionBuilder {
        this.strategy = strategy
        return this
    }
    
    fun build(): ComposableAgent {
        require(agents.isNotEmpty()) { "At least one agent required" }
        return CompositeAgent(agents.toList(), strategy)
    }
}

/**
 * Composite agent implementation
 */
class CompositeAgent(
    private val agents: List<Agent>,
    private val strategy: CompositionBuilder.CompositionStrategy
) : ComposableAgent {
    
    override val id = "composite-${agents.joinToString("-") { it.id }}"
    override val name = "Composite Agent"
    override val description = "Composite of ${agents.size} agents"
    override val capabilities = agents.flatMap { it.capabilities }.distinct()
    
    override suspend fun processMessage(message: Message): Message {
        return when (strategy) {
            CompositionBuilder.CompositionStrategy.SEQUENTIAL -> {
                var currentMessage = message
                for (agent in agents) {
                    currentMessage = agent.processMessage(currentMessage)
                    if (currentMessage.type == MessageType.ERROR) break
                }
                currentMessage
            }
            CompositionBuilder.CompositionStrategy.PARALLEL -> {
                // Simplified parallel processing - just use first agent for demo
                agents.first().processMessage(message)
            }
        }
    }
    
    override fun canHandle(message: Message) = agents.any { it.canHandle(message) }
    override fun getTools() = agents.flatMap { it.getTools() }
    override fun isReady() = agents.any { it.isReady() }
    
    override fun then(next: ComposableAgent): ComposableAgent {
        return CompositeAgent(listOf(this, next), CompositionBuilder.CompositionStrategy.SEQUENTIAL)
    }
    
    override fun parallel(other: ComposableAgent): ComposableAgent {
        return CompositeAgent(listOf(this, other), CompositionBuilder.CompositionStrategy.PARALLEL)
    }
}

// =====================================
// SIMPLIFIED WORKFLOW
// =====================================

/**
 * Simple workflow builder
 */
class SimpleWorkflowBuilder(private val id: String) {
    private val steps = mutableListOf<WorkflowStep>()
    
    /**
     * Add agent step
     */
    fun agent(stepId: String, agentId: String) {
        steps.add(WorkflowStep(stepId, WorkflowStep.Type.AGENT, agentId))
    }
    
    /**
     * Add transform step
     */
    fun transform(stepId: String, transformer: suspend (Message) -> Message) {
        steps.add(WorkflowStep(stepId, WorkflowStep.Type.TRANSFORM, "", transformer))
    }
    
    fun build(): SimpleWorkflow {
        return SimpleWorkflow(id, steps.toList())
    }
}

/**
 * Workflow step
 */
data class WorkflowStep(
    val id: String,
    val type: Type,
    val agentId: String = "",
    val transformer: (suspend (Message) -> Message)? = null
) {
    enum class Type { AGENT, TRANSFORM }
}

/**
 * Simple workflow
 */
class SimpleWorkflow(
    val id: String,
    private val steps: List<WorkflowStep>
) {
    suspend fun execute(message: Message): Message {
        var currentMessage = message
        
        for (step in steps) {
            currentMessage = when (step.type) {
                WorkflowStep.Type.AGENT -> {
                    val agent = AgentRegistry.getAgent(step.agentId)
                        ?: return currentMessage.createReply(
                            content = "Agent not found: ${step.agentId}",
                            sender = "workflow",
                            type = MessageType.ERROR
                        )
                    agent.processMessage(currentMessage)
                }
                WorkflowStep.Type.TRANSFORM -> {
                    step.transformer?.invoke(currentMessage) ?: currentMessage
                }
            }
            
            if (currentMessage.type == MessageType.ERROR) break
        }
        
        return currentMessage
    }
}

// =====================================
// EXTENSION FUNCTIONS
// =====================================

/**
 * Convert agent to composable
 */
fun Agent.toComposable(): ComposableAgent {
    return if (this is ComposableAgent) this 
    else ComposableAgentWrapper(this)
}

/**
 * Composable agent wrapper
 */
class ComposableAgentWrapper(private val agent: Agent) : ComposableAgent, Agent by agent {
    override fun then(next: ComposableAgent): ComposableAgent {
        return CompositeAgent(listOf(this, next), CompositionBuilder.CompositionStrategy.SEQUENTIAL)
    }
    
    override fun parallel(other: ComposableAgent): ComposableAgent {
        return CompositeAgent(listOf(this, other), CompositionBuilder.CompositionStrategy.PARALLEL)
    }
}

// =====================================
// DSL ENTRY POINT
// =====================================

/**
 * Experimental DSL entry point
 */
fun experimental(init: ExperimentalDSLContext.() -> Unit): ExperimentalDSLContext {
    val context = ExperimentalDSLContext()
    context.init()
    return context
} 