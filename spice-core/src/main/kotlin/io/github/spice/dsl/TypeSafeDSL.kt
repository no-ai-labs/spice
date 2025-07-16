package io.github.spice.dsl

import io.github.spice.*
import kotlin.reflect.KClass

/**
 * 🛡️ Type-Safe DSL
 * 
 * Enhanced type safety with Generic-based message type checking
 * and compile-time validation support.
 */

/**
 * Typed message interface for type safety
 */
interface TypedMessage<out T : MessageContent> {
    val message: Message
    val content: T
}

/**
 * Message content types
 */
sealed class MessageContent

/**
 * Text content
 */
data class TextContent(
    val text: String,
    val language: String = "en",
    val encoding: String = "utf-8"
) : MessageContent()

/**
 * Data content
 */
data class DataContent(
    val data: Map<String, Any>,
    val schema: String? = null,
    val format: String = "json"
) : MessageContent()

/**
 * Tool call content
 */
data class ToolCallContent(
    val toolName: String,
    val parameters: Map<String, Any>,
    val callId: String = java.util.UUID.randomUUID().toString()
) : MessageContent()

/**
 * Tool result content
 */
data class ToolResultContent(
    val callId: String,
    val result: Any?,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) : MessageContent()

/**
 * System content
 */
data class SystemContent(
    val instruction: String,
    val priority: Int = 0,
    val context: Map<String, Any> = emptyMap()
) : MessageContent()

/**
 * Error content
 */
data class ErrorContent(
    val error: String,
    val code: String? = null,
    val details: Map<String, Any> = emptyMap(),
    val recoverable: Boolean = true
) : MessageContent()

/**
 * Typed message implementation
 */
class TypedMessageImpl<T : MessageContent>(
    override val message: Message,
    override val content: T
) : TypedMessage<T>

/**
 * Type-safe message builder
 */
class TypeSafeMessageBuilder<T : MessageContent>(
    private val contentType: KClass<T>
) {
    private var sender: String = ""
    private var receiver: String? = null
    private var metadata: Map<String, String> = emptyMap()
    private var parentId: String? = null
    private var conversationId: String? = null
    private lateinit var content: T
    
    /**
     * Set sender
     */
    fun from(senderId: String): TypeSafeMessageBuilder<T> {
        sender = senderId
        return this
    }
    
    /**
     * Set receiver
     */
    fun to(receiverId: String): TypeSafeMessageBuilder<T> {
        receiver = receiverId
        return this
    }
    
    /**
     * Set metadata
     */
    fun withMetadata(metadata: Map<String, String>): TypeSafeMessageBuilder<T> {
        this.metadata = metadata
        return this
    }
    
    /**
     * Add single metadata entry
     */
    fun withMetadata(key: String, value: String): TypeSafeMessageBuilder<T> {
        this.metadata = this.metadata + (key to value)
        return this
    }
    
    /**
     * Set parent message
     */
    fun replyTo(parentMessageId: String): TypeSafeMessageBuilder<T> {
        parentId = parentMessageId
        return this
    }
    
    /**
     * Set conversation
     */
    fun inConversation(conversationId: String): TypeSafeMessageBuilder<T> {
        this.conversationId = conversationId
        return this
    }
    
    /**
     * Set typed content
     */
    fun withContent(content: T): TypeSafeMessageBuilder<T> {
        this.content = content
        return this
    }
    
    /**
     * Build typed message
     */
    fun build(): TypedMessage<T> {
        require(sender.isNotEmpty()) { "Sender must be specified" }
        require(::content.isInitialized) { "Content must be specified" }
        
        val messageType = when (contentType) {
            TextContent::class -> MessageType.TEXT
            DataContent::class -> MessageType.DATA
            ToolCallContent::class -> MessageType.TOOL_CALL
            ToolResultContent::class -> MessageType.TOOL_RESULT
            SystemContent::class -> MessageType.SYSTEM
            ErrorContent::class -> MessageType.ERROR
            else -> MessageType.TEXT
        }
        
        val messageContent = content
        val message = Message(
            content = when (messageContent) {
                is TextContent -> messageContent.text
                is DataContent -> messageContent.data.toString()
                is ToolCallContent -> "Tool call: ${messageContent.toolName}"
                is ToolResultContent -> messageContent.result?.toString() ?: messageContent.error ?: "Tool result"
                is SystemContent -> messageContent.instruction
                is ErrorContent -> messageContent.error
                else -> messageContent.toString()
            },
            type = messageType,
            sender = sender,
            receiver = receiver,
            metadata = metadata,
            parentId = parentId,
            conversationId = conversationId
        )
        
        return TypedMessageImpl(message, content)
    }
}

/**
 * Type-safe agent interface
 */
interface TypeSafeAgent<I : MessageContent, O : MessageContent> {
    val inputType: KClass<out I>
    val outputType: KClass<out O>
    
    suspend fun process(input: TypedMessage<I>): TypedMessage<O>
}

/**
 * Type-safe agent builder
 */
class TypeSafeAgentBuilder<I : MessageContent, O : MessageContent>(
    private val inputType: KClass<I>,
    private val outputType: KClass<O>
) {
    private var agentId: String = ""
    private var agentName: String = ""
    private var description: String = ""
    private var processor: (suspend (TypedMessage<I>) -> TypedMessage<O>)? = null
    
    /**
     * Set agent identity
     */
    fun identity(id: String, name: String, description: String = ""): TypeSafeAgentBuilder<I, O> {
        agentId = id
        agentName = name
        this.description = description
        return this
    }
    
    /**
     * Set processor
     */
    fun processor(processor: suspend (TypedMessage<I>) -> TypedMessage<O>): TypeSafeAgentBuilder<I, O> {
        this.processor = processor
        return this
    }
    
    /**
     * Build type-safe agent
     */
    fun build(): TypeSafeAgent<I, O> {
        require(agentId.isNotEmpty()) { "Agent ID must be specified" }
        require(agentName.isNotEmpty()) { "Agent name must be specified" }
        require(processor != null) { "Processor must be specified" }
        
        return TypeSafeAgentImpl(
            id = agentId,
            name = agentName,
            description = description,
            inputType = inputType,
            outputType = outputType,
            processor = processor!!
        )
    }
}

/**
 * Type-safe agent implementation
 */
class TypeSafeAgentImpl<I : MessageContent, O : MessageContent>(
    val id: String,
    val name: String,
    val description: String,
    override val inputType: KClass<out I>,
    override val outputType: KClass<out O>,
    private val processor: suspend (TypedMessage<I>) -> TypedMessage<O>
) : TypeSafeAgent<I, O> {
    
    override suspend fun process(input: TypedMessage<I>): TypedMessage<O> {
        return processor(input)
    }
}

/**
 * Type-safe workflow builder
 */
class TypeSafeWorkflowBuilder {
    private val typedNodes = mutableListOf<TypedWorkflowNode<*, *>>()
    private val typedEdges = mutableListOf<TypedWorkflowEdge>()
    
    /**
     * Add typed agent node
     */
    fun <I : MessageContent, O : MessageContent> agentNode(
        id: String,
        agent: TypeSafeAgent<I, O>
    ): TypeSafeWorkflowBuilder {
        typedNodes.add(TypedWorkflowNode(id, agent.inputType, agent.outputType) { input ->
            @Suppress("UNCHECKED_CAST")
            agent.process(input as TypedMessage<I>)
        })
        return this
    }
    
    /**
     * Add typed edge with type checking
     */
    fun edge(
        from: String,
        to: String,
        messageType: KClass<out MessageContent>
    ): TypeSafeWorkflowBuilder {
        typedEdges.add(TypedWorkflowEdge(from, to, messageType))
        return this
    }
    
    /**
     * Validate type consistency
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        // Check that edges connect compatible types
        typedEdges.forEach { edge ->
            val fromNode = typedNodes.find { it.id == edge.from }
            val toNode = typedNodes.find { it.id == edge.to }
            
            if (fromNode == null) {
                errors.add("Edge references non-existent from node: ${edge.from}")
            }
            if (toNode == null) {
                errors.add("Edge references non-existent to node: ${edge.to}")
            }
            
            if (fromNode != null && toNode != null) {
                // Check type compatibility
                if (fromNode.outputType != toNode.inputType) {
                    errors.add(
                        "Type mismatch: ${edge.from} outputs ${fromNode.outputType.simpleName} " +
                        "but ${edge.to} expects ${toNode.inputType.simpleName}"
                    )
                }
            }
        }
        
        return errors
    }
    
    /**
     * Build type-safe workflow
     */
    fun build(): TypeSafeWorkflow {
        val errors = validate()
        if (errors.isNotEmpty()) {
            throw IllegalStateException("Type safety validation failed: ${errors.joinToString(", ")}")
        }
        
        return TypeSafeWorkflow(typedNodes.toList(), typedEdges.toList())
    }
}

/**
 * Typed workflow node
 */
data class TypedWorkflowNode<I : MessageContent, O : MessageContent>(
    val id: String,
    val inputType: KClass<out I>,
    val outputType: KClass<out O>,
    val processor: suspend (TypedMessage<*>) -> TypedMessage<*>
)

/**
 * Typed workflow edge
 */
data class TypedWorkflowEdge(
    val from: String,
    val to: String,
    val messageType: KClass<out MessageContent>
)

/**
 * Type-safe workflow
 */
class TypeSafeWorkflow(
    private val nodes: List<TypedWorkflowNode<*, *>>,
    private val edges: List<TypedWorkflowEdge>
) {
    /**
     * Execute workflow with type checking
     */
    suspend fun <T : MessageContent> execute(
        startNodeId: String,
        input: TypedMessage<T>
    ): TypedMessage<*> {
        val startNode = nodes.find { it.id == startNodeId }
            ?: throw IllegalArgumentException("Start node not found: $startNodeId")
        
        // Verify input type matches start node expectation
        if (startNode.inputType != input.content::class) {
            throw IllegalArgumentException(
                "Input type mismatch: provided ${input.content::class.simpleName} " +
                "but start node expects ${startNode.inputType.simpleName}"
            )
        }
        
        return startNode.processor(input)
    }
}

/**
 * DSL factory functions
 */
inline fun <reified T : MessageContent> messageOf(): TypeSafeMessageBuilder<T> {
    return TypeSafeMessageBuilder(T::class)
}

inline fun <reified I : MessageContent, reified O : MessageContent> typeSafeAgent(): TypeSafeAgentBuilder<I, O> {
    return TypeSafeAgentBuilder(I::class, O::class)
}

fun typeSafeWorkflow(init: TypeSafeWorkflowBuilder.() -> Unit): TypeSafeWorkflow {
    val builder = TypeSafeWorkflowBuilder()
    builder.init()
    return builder.build()
}

/**
 * Type conversion utilities
 */
object TypeConverters {
    
    /**
     * Convert regular message to typed message
     */
    inline fun <reified T : MessageContent> Message.toTyped(content: T): TypedMessage<T> {
        return TypedMessageImpl(this, content)
    }
    
    /**
     * Extract content from typed message
     */
    fun <T : MessageContent> TypedMessage<T>.extractContent(): T = content
    
    /**
     * Convert typed message to regular message
     */
    fun <T : MessageContent> TypedMessage<T>.toMessage(): Message = message
    
    /**
     * Parse message content based on type
     */
    inline fun <reified T : MessageContent> Message.parseContent(): T? {
        return when (T::class) {
            TextContent::class -> TextContent(content) as? T
            DataContent::class -> {
                try {
                    // Simple JSON-like parsing for demo
                    val data = mapOf("content" to content)
                    DataContent(data) as? T
                } catch (e: Exception) {
                    null
                }
            }
            SystemContent::class -> SystemContent(content) as? T
            ErrorContent::class -> ErrorContent(content) as? T
            else -> null
        }
    }
}

/**
 * Type-safe agent adapter
 */
class TypeSafeAgentAdapter<I : MessageContent, O : MessageContent>(
    private val typeSafeAgent: TypeSafeAgent<I, O>,
    private val inputTypeClass: KClass<out I>,
    private val outputTypeClass: KClass<out O>
) : Agent {
    
    override val id: String = (typeSafeAgent as? TypeSafeAgentImpl)?.id ?: "typed-agent"
    override val name: String = (typeSafeAgent as? TypeSafeAgentImpl)?.name ?: "Typed Agent"
    override val description: String = (typeSafeAgent as? TypeSafeAgentImpl)?.description ?: ""
    override val capabilities: List<String> = listOf("type-safe-processing")
    
    override suspend fun processMessage(message: Message): Message {
        val parsedContent = parseMessageContent(message, inputTypeClass)
            ?: return message.createReply(
                content = "Failed to parse input content",
                sender = id,
                type = MessageType.ERROR
            )
        
        val typedInput = TypeConverters.run { message.toTyped(parsedContent) }
        val typedOutput = typeSafeAgent.process(typedInput)
        
        return TypeConverters.run { typedOutput.toMessage() }
    }
    
    override fun canHandle(message: Message): Boolean {
        return parseMessageContent(message, inputTypeClass) != null
    }
    
    override fun getTools(): List<Tool> = emptyList()
    
    override fun isReady(): Boolean = true
    
    private fun parseMessageContent(message: Message, typeClass: KClass<out I>): I? {
        @Suppress("UNCHECKED_CAST")
        return when (typeClass) {
            TextContent::class -> TextContent(message.content) as? I
            DataContent::class -> {
                try {
                    val data = mapOf("content" to message.content)
                    DataContent(data) as? I
                } catch (e: Exception) {
                    null
                }
            }
            SystemContent::class -> SystemContent(message.content) as? I
            ErrorContent::class -> ErrorContent(message.content) as? I
            else -> null
        }
    }
}

/**
 * Extension function to convert type-safe agent to regular agent
 */
inline fun <reified I : MessageContent, reified O : MessageContent> TypeSafeAgent<I, O>.toAgent(): Agent {
    return TypeSafeAgentAdapter(this, I::class, O::class)
} 