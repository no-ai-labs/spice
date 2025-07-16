package io.github.spice.dsl

import io.github.spice.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 🌊 Reactive Streams DSL
 * 
 * Flow-based asynchronous processing DSL for building reactive agent systems.
 * Provides backpressure handling, buffering, and stream transformation capabilities.
 */

/**
 * Reactive agent interface
 */
interface ReactiveAgent : Agent {
    /**
     * Process messages as a reactive stream
     */
    fun processStream(input: Flow<Message>): Flow<Message>
    
    /**
     * Convert single message processing to stream
     */
    fun asFlow(): Flow<Message>.() -> Flow<Message> = { map { processMessage(it) } }
}

/**
 * Stream processing configuration
 */
data class StreamConfig(
    val bufferSize: Int = 64,
    val bufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    val parallelism: Int = 1,
    val timeout: Duration = 30.seconds,
    val retryAttempts: Int = 3,
    val retryDelay: Duration = 1.seconds
)

/**
 * Reactive agent implementation
 */
class ReactiveAgentImpl(
    private val baseAgent: Agent,
    private val config: StreamConfig = StreamConfig()
) : ReactiveAgent {
    
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
    
    override fun processStream(input: Flow<Message>): Flow<Message> {
        return input
            .buffer(config.bufferSize, config.bufferOverflow)
            .map { message ->
                try {
                    processMessage(message)
                } catch (e: Exception) {
                    message.createReply(
                        content = "Stream processing failed: ${e.message}",
                        sender = id,
                        type = MessageType.ERROR,
                        metadata = mapOf(
                            "error_type" to "stream_processing",
                            "original_sender" to message.sender
                        )
                    )
                }
            }
    }
}

/**
 * Stream operators for message flows
 */
object StreamOperators {
    
    /**
     * Filter messages by type
     */
    fun filterByType(vararg types: MessageType): Flow<Message>.() -> Flow<Message> = {
        filter { message -> types.contains(message.type) }
    }
    
    /**
     * Filter messages by sender
     */
    fun filterBySender(vararg senders: String): Flow<Message>.() -> Flow<Message> = {
        filter { message -> senders.contains(message.sender) }
    }
    
    /**
     * Filter messages by content pattern
     */
    fun filterByContent(pattern: Regex): Flow<Message>.() -> Flow<Message> = {
        filter { message -> pattern.containsMatchIn(message.content) }
    }
    
    /**
     * Transform message content
     */
    fun transformContent(transformer: (String) -> String): Flow<Message>.() -> Flow<Message> = {
        map { message -> message.copy(content = transformer(message.content)) }
    }
    
    /**
     * Add metadata to messages
     */
    fun addMetadata(metadata: Map<String, String>): Flow<Message>.() -> Flow<Message> = {
        map { message -> message.copy(metadata = message.metadata + metadata) }
    }
    
    /**
     * Batch messages by count
     */
    fun batchByCount(count: Int): Flow<Message>.() -> Flow<List<Message>> = {
        flow {
            val buffer = mutableListOf<Message>()
            collect { message ->
                buffer.add(message)
                if (buffer.size >= count) {
                    emit(buffer.toList())
                    buffer.clear()
                }
            }
            if (buffer.isNotEmpty()) {
                emit(buffer.toList())
            }
        }
    }
    
    /**
     * Batch messages by time window
     */
    fun batchByTime(windowDuration: Duration): Flow<Message>.() -> Flow<List<Message>> = {
        flow {
            val buffer = mutableListOf<Message>()
            var lastEmitTime = System.currentTimeMillis()
            
            collect { message ->
                buffer.add(message)
                val now = System.currentTimeMillis()
                
                if (now - lastEmitTime >= windowDuration.inWholeMilliseconds) {
                    if (buffer.isNotEmpty()) {
                        emit(buffer.toList())
                        buffer.clear()
                    }
                    lastEmitTime = now
                }
            }
            
            // Emit remaining messages
            if (buffer.isNotEmpty()) {
                emit(buffer.toList())
            }
        }
    }
    
    /**
     * Throttle messages
     */
    fun throttle(interval: Duration): Flow<Message>.() -> Flow<Message> = {
        flow {
            var lastEmitTime = 0L
            
            collect { message ->
                val now = System.currentTimeMillis()
                if (now - lastEmitTime >= interval.inWholeMilliseconds) {
                    emit(message)
                    lastEmitTime = now
                } else {
                    // Skip message due to throttling
                }
            }
        }
    }
    
    /**
     * Debounce messages
     */
    fun debounce(timeout: Duration): Flow<Message>.() -> Flow<Message> = {
        flow {
            var pendingMessage: Message? = null
            var lastMessageTime = 0L
            
            collect { message ->
                pendingMessage = message
                lastMessageTime = System.currentTimeMillis()
                
                delay(timeout)
                
                // Check if this is still the latest message
                if (pendingMessage == message && 
                    System.currentTimeMillis() - lastMessageTime >= timeout.inWholeMilliseconds) {
                    emit(message)
                    pendingMessage = null
                }
            }
        }
    }
    
    /**
     * Retry failed processing
     */
    fun retryOnError(
        maxAttempts: Int = 3,
        delay: Duration = 1.seconds
    ): Flow<Message>.() -> Flow<Message> = {
        retryWhen { cause, attempt ->
            if (attempt < maxAttempts) {
                delay(delay)
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Add timeout to processing
     */
    fun withTimeout(timeout: Duration): Flow<Message>.() -> Flow<Message> = {
        map { message ->
            try {
                kotlinx.coroutines.withTimeout(timeout) {
                    message
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                message.createReply(
                    content = "Message processing timed out",
                    sender = "timeout-operator",
                    type = MessageType.ERROR,
                    metadata = mapOf(
                        "timeout_ms" to timeout.inWholeMilliseconds.toString(),
                        "original_id" to message.id
                    )
                )
            }
        }
    }
}

/**
 * Stream composition builder
 */
class StreamCompositionBuilder {
    private val operators = mutableListOf<Flow<Message>.() -> Flow<Message>>()
    private var config = StreamConfig()
    
    /**
     * Add stream operator
     */
    fun operator(op: Flow<Message>.() -> Flow<Message>): StreamCompositionBuilder {
        operators.add(op)
        return this
    }
    
    /**
     * Filter by message type
     */
    fun filterByType(vararg types: MessageType): StreamCompositionBuilder {
        return operator(StreamOperators.filterByType(*types))
    }
    
    /**
     * Filter by sender
     */
    fun filterBySender(vararg senders: String): StreamCompositionBuilder {
        return operator(StreamOperators.filterBySender(*senders))
    }
    
    /**
     * Transform content
     */
    fun transformContent(transformer: (String) -> String): StreamCompositionBuilder {
        return operator(StreamOperators.transformContent(transformer))
    }
    
    /**
     * Add metadata
     */
    fun addMetadata(metadata: Map<String, String>): StreamCompositionBuilder {
        return operator(StreamOperators.addMetadata(metadata))
    }
    
    /**
     * Throttle messages
     */
    fun throttle(interval: Duration): StreamCompositionBuilder {
        return operator(StreamOperators.throttle(interval))
    }
    
    /**
     * Debounce messages
     */
    fun debounce(timeout: Duration): StreamCompositionBuilder {
        return operator(StreamOperators.debounce(timeout))
    }
    
    /**
     * Add timeout
     */
    fun withTimeout(timeout: Duration): StreamCompositionBuilder {
        return operator(StreamOperators.withTimeout(timeout))
    }
    
    /**
     * Configure stream
     */
    fun configure(config: StreamConfig): StreamCompositionBuilder {
        this.config = config
        return this
    }
    
    /**
     * Build stream processor
     */
    fun build(): Flow<Message>.() -> Flow<Message> = {
        operators.fold(this) { flow, operator ->
            flow.operator()
        }
    }
}

/**
 * Reactive multi-agent system
 */
class ReactiveMultiAgentSystem(
    private val agents: List<ReactiveAgent>,
    private val config: StreamConfig = StreamConfig()
) {
    
    /**
     * Process messages through all agents in parallel
     */
    fun processParallel(input: Flow<Message>): Flow<Message> {
        return input.flatMapMerge(config.parallelism) { message ->
            flow {
                agents.forEach { agent ->
                    if (agent.canHandle(message)) {
                        try {
                            emit(agent.processMessage(message))
                        } catch (e: Exception) {
                            emit(message.createReply(
                                content = "Agent ${agent.id} failed: ${e.message}",
                                sender = "reactive-system",
                                type = MessageType.ERROR
                            ))
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Process messages through agents sequentially
     */
    fun processSequential(input: Flow<Message>): Flow<Message> {
        return input.map { message ->
            var currentMessage = message
            
            for (agent in agents) {
                if (agent.canHandle(currentMessage)) {
                    currentMessage = agent.processMessage(currentMessage)
                    
                    // Stop on error
                    if (currentMessage.type == MessageType.ERROR) {
                        break
                    }
                }
            }
            
            currentMessage
        }
    }
    
    /**
     * Route messages to appropriate agents
     */
    fun routeMessages(input: Flow<Message>): Flow<Message> {
        return input.map { message ->
            val suitableAgent = agents.find { it.canHandle(message) && it.isReady() }
            
            suitableAgent?.processMessage(message) ?: message.createReply(
                content = "No suitable agent found for message",
                sender = "reactive-system",
                type = MessageType.ERROR,
                metadata = mapOf(
                    "message_type" to message.type.name,
                    "available_agents" to agents.size.toString()
                )
            )
        }
    }
    
    /**
     * Load balance messages across agents
     */
    fun loadBalance(input: Flow<Message>): Flow<Message> {
        val agentCycle = agents.asSequence().cycle().iterator()
        
        return input.map { message ->
            val selectedAgent = agentCycle.next()
            selectedAgent.processMessage(message)
        }
    }
}

/**
 * Message stream builder
 */
class MessageStreamBuilder {
    private var sourceFlow: Flow<Message>? = null
    
    /**
     * Set source flow
     */
    fun source(flow: Flow<Message>): MessageStreamBuilder {
        sourceFlow = flow
        return this
    }
    
    /**
     * Create from list
     */
    fun fromList(messages: List<Message>): MessageStreamBuilder {
        sourceFlow = messages.asFlow()
        return this
    }
    
    /**
     * Create from varargs
     */
    fun fromMessages(vararg messages: Message): MessageStreamBuilder {
        sourceFlow = messages.asFlow()
        return this
    }
    
    /**
     * Create periodic messages
     */
    fun periodic(
        interval: Duration,
        content: String,
        sender: String,
        type: MessageType = MessageType.TEXT
    ): MessageStreamBuilder {
        sourceFlow = flow {
            while (true) {
                emit(Message(
                    content = content,
                    type = type,
                    sender = sender
                ))
                delay(interval)
            }
        }
        return this
    }
    
    /**
     * Apply stream composition
     */
    fun compose(composition: Flow<Message>.() -> Flow<Message>): MessageStreamBuilder {
        sourceFlow = sourceFlow?.composition()
        return this
    }
    
    /**
     * Process with reactive agent
     */
    fun processWithAgent(agent: ReactiveAgent): MessageStreamBuilder {
        sourceFlow = sourceFlow?.let { agent.processStream(it) }
        return this
    }
    
    /**
     * Process with multi-agent system
     */
    fun processWithSystem(system: ReactiveMultiAgentSystem): MessageStreamBuilder {
        sourceFlow = sourceFlow?.let { system.routeMessages(it) }
        return this
    }
    
    /**
     * Build final flow
     */
    fun build(): Flow<Message> {
        return sourceFlow ?: throw IllegalStateException("Source flow not specified")
    }
}

/**
 * Extension functions
 */
fun Agent.reactive(config: StreamConfig = StreamConfig()): ReactiveAgent {
    return ReactiveAgentImpl(this, config)
}

fun Flow<Message>.processWithAgent(agent: Agent): Flow<Message> {
    val reactiveAgent = agent.reactive()
    return reactiveAgent.processStream(this)
}

fun Flow<Message>.processWithAgents(vararg agents: Agent): Flow<Message> {
    val reactiveAgents = agents.map { it.reactive() }
    val system = ReactiveMultiAgentSystem(reactiveAgents)
    return system.routeMessages(this)
}

/**
 * Utility extension for Sequence cycling
 */
private fun <T> Sequence<T>.cycle(): Sequence<T> = sequence {
    while (true) {
        yieldAll(this@cycle)
    }
}

/**
 * DSL entry points
 */
fun messageStream(init: MessageStreamBuilder.() -> Unit): Flow<Message> {
    val builder = MessageStreamBuilder()
    builder.init()
    return builder.build()
}

fun streamComposition(init: StreamCompositionBuilder.() -> Unit): Flow<Message>.() -> Flow<Message> {
    val builder = StreamCompositionBuilder()
    builder.init()
    return builder.build()
}

fun reactiveSystem(
    agents: List<ReactiveAgent>,
    config: StreamConfig = StreamConfig()
): ReactiveMultiAgentSystem {
    return ReactiveMultiAgentSystem(agents, config)
}

/**
 * Common stream patterns
 */
object StreamPatterns {
    
    /**
     * Error handling with fallback
     */
    fun errorHandling(
        fallbackMessage: String = "Processing failed",
        fallbackSender: String = "error-handler"
    ): Flow<Message>.() -> Flow<Message> = {
        catch { e ->
            emit(Message(
                content = "$fallbackMessage: ${e.message}",
                type = MessageType.ERROR,
                sender = fallbackSender
            ))
        }
    }
    
    /**
     * Message logging
     */
    fun logging(
        logger: (Message) -> Unit = { println("📨 ${it.sender}: ${it.content}") }
    ): Flow<Message>.() -> Flow<Message> = {
        onEach { logger(it) }
    }
    
    /**
     * Performance monitoring
     */
    fun monitoring(): Flow<Message>.() -> Flow<Message> = {
        onEach { message ->
            val processingTime = System.currentTimeMillis() - message.timestamp
            println("⏱️ Message ${message.id} processing time: ${processingTime}ms")
        }
    }
    
    /**
     * Message validation
     */
    fun validation(
        validator: (Message) -> Boolean,
        onInvalid: (Message) -> Message = { msg ->
            msg.createReply(
                content = "Invalid message",
                sender = "validator",
                type = MessageType.ERROR
            )
        }
    ): Flow<Message>.() -> Flow<Message> = {
        map { message ->
            if (validator(message)) message else onInvalid(message)
        }
    }
} 