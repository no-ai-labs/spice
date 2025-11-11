package io.github.noailabs.spice

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import java.util.*
import java.time.Instant

/**
 * 🚀 Comm - Modern Communication System for Spice Framework
 * 
 * The unified messaging system that replaces legacy Message/EnhancedMessage.
 * Clean, powerful, and future-proof design.
 */

// =====================================
// ENUMS
// =====================================

/**
 * Communication types for different content
 */
enum class CommType {
    TEXT,           // General text message
    SYSTEM,         // System message
    TOOL_CALL,      // Tool invocation
    TOOL_RESULT,    // Tool execution result
    ERROR,          // Error message
    DATA,           // Data transfer
    PROMPT,         // Prompt message
    RESULT,         // Final result
    WORKFLOW_START, // Workflow start
    WORKFLOW_END,   // Workflow end
    INTERRUPT,      // Interrupt message
    RESUME,         // Resume message
    
    // Media types
    IMAGE,          // Image content
    DOCUMENT,       // Document attachment
    AUDIO,          // Audio content
    VIDEO           // Video content
}

/**
 * Role in conversation (similar to MessageRole)
 */
enum class CommRole {
    USER,           // User/Human input
    ASSISTANT,      // AI Assistant response
    SYSTEM,         // System message
    TOOL,           // Tool-related message
    AGENT           // Agent-to-agent communication
}

/**
 * Message priority levels
 */
enum class Priority {
    LOW,
    NORMAL,
    HIGH,
    URGENT,
    CRITICAL
}

// =====================================
// SERIALIZERS
// =====================================

/**
 * Custom serializer for Map<String, Any?> that handles null values
 */
object AnyValueMapSerializer : KSerializer<Map<String, Any?>> {
    private val delegateSerializer = MapSerializer(String.serializer(), JsonElement.serializer())

    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        val jsonMap = value.mapValues { (_, v) ->
            when (v) {
                null -> JsonNull
                is String -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is JsonElement -> v
                else -> JsonPrimitive(v.toString())
            }
        }
        encoder.encodeSerializableValue(delegateSerializer, jsonMap)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        val jsonMap = decoder.decodeSerializableValue(delegateSerializer)
        return jsonMap.mapValues { (_, element) ->
            when (element) {
                is JsonNull -> null
                is JsonPrimitive -> {
                    when {
                        element.isString -> element.content
                        element.booleanOrNull != null -> element.boolean
                        element.longOrNull != null -> element.long
                        element.doubleOrNull != null -> element.double
                        else -> element.content
                    }
                }
                is JsonArray -> element.toString()
                is JsonObject -> element.toString()
            }
        }
    }
}

// =====================================
// CORE COMM CLASS
// =====================================

/**
 * 💬 Universal Communication Unit
 *
 * The foundation of all communication in modern Spice Framework.
 * The unified communication type for the Spice Framework.
 */
@Serializable
data class Comm(
    // Core identification
    val id: String = "comm-${UUID.randomUUID()}",
    val content: String,
    val from: String,
    val to: String? = null,

    // Type and role
    val type: CommType = CommType.TEXT,
    val role: CommRole = CommRole.USER,

    // Timing and threading
    val timestamp: Long = Instant.now().toEpochMilli(),
    val conversationId: String? = null,
    val thread: String? = null,
    val parentId: String? = null,

    // Flexible metadata (replaces rigid fields)
    @Serializable(with = AnyValueMapSerializer::class)
    val data: Map<String, Any?> = emptyMap(),

    // Execution Context (since 0.6.0 - unified context)
    @kotlinx.serialization.Transient
    val context: ExecutionContext? = null,

    // Rich content features
    val media: List<MediaItem> = emptyList(),
    val mentions: List<String> = emptyList(),
    val reactions: List<Reaction> = emptyList(),

    // Control features
    val priority: Priority = Priority.NORMAL,
    val encrypted: Boolean = false,
    val ttl: Long? = null,
    val expiresAt: Long? = null
) {
    
    /**
     * Create reply (without data)
     */
    fun reply(
        content: String, 
        from: String,
        role: CommRole = CommRole.ASSISTANT,
        type: CommType = CommType.TEXT
    ): Comm = copy(
        id = "comm-${UUID.randomUUID()}",
        content = content,
        from = from,
        to = this.from,
        role = role,
        type = type,
        thread = this.thread ?: this.id,
        parentId = this.id,
        conversationId = this.conversationId ?: this.id,
        timestamp = Instant.now().toEpochMilli()
    )
    
    /**
     * Create reply (with data)
     */
    fun reply(
        content: String,
        from: String,
        type: CommType = CommType.TEXT,
        role: CommRole = CommRole.ASSISTANT,
        data: Map<String, Any?> = emptyMap()
    ): Comm = copy(
        id = "comm-${UUID.randomUUID()}",
        content = content,
        from = from,
        to = this.from,
        role = role,
        type = type,
        thread = this.thread ?: this.id,
        parentId = this.id,
        conversationId = this.conversationId ?: this.id,
        data = this.data + data,
        timestamp = Instant.now().toEpochMilli()
    )
    
    /**
     * Forward to another recipient
     */
    fun forward(to: String): Comm = copy(
        id = "comm-${UUID.randomUUID()}",
        to = to,
        timestamp = Instant.now().toEpochMilli()
    )
    
    /**
     * Add metadata
     */
    fun withData(key: String, value: Any?): Comm = copy(
        data = data + (key to value)
    )

    /**
     * Add multiple metadata entries
     */
    fun withData(vararg pairs: Pair<String, Any?>): Comm = copy(
        data = data + pairs.toMap()
    )
    
    /**
     * Change type
     */
    fun withType(type: CommType): Comm = copy(type = type)
    
    /**
     * Change role
     */
    fun withRole(role: CommRole): Comm = copy(role = role)
    
    /**
     * Add reaction
     */
    fun addReaction(user: String, emoji: String): Comm = copy(
        reactions = reactions + Reaction(user, emoji, Instant.now().toEpochMilli())
    )
    
    /**
     * Add mentions
     */
    fun mention(vararg users: String): Comm = copy(
        mentions = mentions + users.toList()
    )
    
    /**
     * Add media
     */
    fun withMedia(vararg items: MediaItem): Comm = copy(
        media = media + items.toList()
    )
    
    /**
     * Set priority
     */
    fun urgent(): Comm = copy(priority = Priority.URGENT)
    fun critical(): Comm = copy(priority = Priority.CRITICAL)
    fun lowPriority(): Comm = copy(priority = Priority.LOW)
    
    /**
     * Security
     */
    fun encrypt(): Comm = copy(encrypted = true)
    
    /**
     * Set TTL
     */
    fun expires(ttlMs: Long): Comm = copy(
        ttl = ttlMs,
        expiresAt = Instant.now().toEpochMilli() + ttlMs
    )

    // =========================================
    // Context Integration (since 0.4.0)
    // =========================================

    /**
     * Get value from context first, then fall back to data
     *
     * Example:
     * ```kotlin
     * val tenantId = comm.getContextValue("tenantId")
     * ```
     *
     * @since 0.4.0
     */
    fun getContextValue(key: String): String? {
        return context?.get(key)?.toString() ?: data[key]?.toString()
    }

    /**
     * Safe accessor for string data
     */
    fun getDataAsString(key: String): String? = data[key]?.toString()

    /**
     * Safe accessor for boolean data
     */
    fun getDataAsBoolean(key: String): Boolean? = when (val value = data[key]) {
        is Boolean -> value
        is String -> value.toBoolean()
        else -> null
    }

    /**
     * Safe accessor for int data
     */
    fun getDataAsInt(key: String): Int? = when (val value = data[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    /**
     * Safe accessor for long data
     */
    fun getDataAsLong(key: String): Long? = when (val value = data[key]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

    /**
     * Safe accessor for double data
     */
    fun getDataAsDouble(key: String): Double? = when (val value = data[key]) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    /**
     * Create new Comm with ExecutionContext
     *
     * Example:
     * ```kotlin
     * val contextualComm = comm.withContext(
     *     ExecutionContext.of(mapOf("tenantId" to "CHIC", "userId" to "user-123"))
     * )
     * ```
     *
     * @since 0.6.0
     */
    fun withContext(context: ExecutionContext): Comm {
        return copy(context = context)
    }

    /**
     * Create new Comm with AgentContext (backward compatibility bridge)
     *
     * @since 0.4.0
     */
    fun withContext(context: AgentContext): Comm {
        return copy(context = context.toExecutionContext())
    }

    /**
     * Create new Comm with context values merged
     *
     * Example:
     * ```kotlin
     * val enrichedComm = comm.withContextValues(
     *     "tenantId" to "CHIC",
     *     "userId" to "user-123"
     * )
     * ```
     *
     * @since 0.6.0
     */
    fun withContextValues(vararg pairs: Pair<String, Any>): Comm {
        val additionalMap = pairs.toMap()
        val newContext = context?.plusAll(additionalMap) ?: ExecutionContext.of(additionalMap)
        return copy(context = newContext)
    }
    
    /**
     * Check if expired
     */
    fun isExpired(): Boolean = expiresAt?.let { 
        Instant.now().toEpochMilli() > it 
    } ?: false
    
    /**
     * Create error response
     */
    fun error(message: String, from: String = "system"): Comm = reply(
        content = message,
        from = from,
        type = CommType.ERROR,
        role = CommRole.SYSTEM
    )
    
    /**
     * Create tool call
     */
    fun toolCall(toolName: String, params: Map<String, Any>, from: String): Comm = copy(
        id = "comm-${UUID.randomUUID()}",
        content = "Tool call: $toolName",
        from = from,
        type = CommType.TOOL_CALL,
        role = CommRole.TOOL,
        data = data + mapOf(
            "tool_name" to toolName,
            "tool_params" to params.toString()
        ),
        timestamp = Instant.now().toEpochMilli()
    )
    
    /**
     * Create tool result
     */
    fun toolResult(result: String, from: String = "system"): Comm = reply(
        content = result,
        from = from,
        type = CommType.TOOL_RESULT,
        role = CommRole.TOOL
    )
}

// =====================================
// SUPPORTING DATA CLASSES
// =====================================

/**
 * Media attachment
 */
@Serializable
data class MediaItem(
    val filename: String,
    val url: String,
    val type: String,
    val size: Long = 0,
    val caption: String? = null,
    @Serializable(with = AnyValueMapSerializer::class)
    val metadata: Map<String, Any?> = emptyMap()
)

/**
 * User reaction
 */
@Serializable
data class Reaction(
    val user: String,
    val emoji: String,
    val timestamp: Long
)

/**
 * Communication result
 */
data class CommResult(
    val success: Boolean,
    val commId: String? = null,
    val deliveredTo: List<String> = emptyList(),
    val error: String? = null,
    val timestamp: Long = Instant.now().toEpochMilli()
) {
    companion object {
        fun success(commId: String, deliveredTo: List<String> = emptyList()) = 
            CommResult(true, commId, deliveredTo, null)
        
        fun failure(error: String) = 
            CommResult(false, null, emptyList(), error)
    }
}

// =====================================
// DSL FUNCTIONS
// =====================================

/**
 * DSL for creating Comm
 */
fun comm(content: String, builder: CommBuilder.() -> Unit = {}): Comm {
    val commBuilder = CommBuilder(content)
    commBuilder.builder()
    return commBuilder.build()
}

/**
 * Quick comm creation
 */
fun quickComm(
    content: String,
    from: String,
    to: String? = null,
    type: CommType = CommType.TEXT,
    role: CommRole = CommRole.USER
): Comm = Comm(
    content = content,
    from = from,
    to = to,
    type = type,
    role = role
)

/**
 * System comm
 */
fun systemComm(content: String, to: String? = null): Comm = Comm(
    content = content,
    from = "system",
    to = to,
    type = CommType.SYSTEM,
    role = CommRole.SYSTEM
)

/**
 * Error comm
 */
fun errorComm(error: String, to: String? = null): Comm = Comm(
    content = error,
    from = "system",
    to = to,
    type = CommType.ERROR,
    role = CommRole.SYSTEM
)

// =====================================
// COMM BUILDER
// =====================================

/**
 * Builder for Comm with fluent API
 */
class CommBuilder(private val content: String) {
    private var from: String = ""
    private var to: String? = null
    private var type: CommType = CommType.TEXT
    private var role: CommRole = CommRole.USER
    private var conversationId: String? = null
    private var thread: String? = null
    private var parentId: String? = null
    private val data = mutableMapOf<String, Any?>()
    private val media = mutableListOf<MediaItem>()
    private val mentions = mutableListOf<String>()
    private var priority: Priority = Priority.NORMAL
    private var encrypted: Boolean = false
    private var ttl: Long? = null

    fun from(sender: String) { this.from = sender }
    fun to(recipient: String) { this.to = recipient }
    fun type(type: CommType) { this.type = type }
    fun role(role: CommRole) { this.role = role }
    fun conversation(id: String) { this.conversationId = id }
    fun thread(id: String) { this.thread = id }
    fun replyTo(parentId: String) { this.parentId = parentId }

    fun data(key: String, value: Any?) { this.data[key] = value }
    fun data(vararg pairs: Pair<String, Any?>) { this.data.putAll(pairs) }
    
    fun mention(vararg users: String) { this.mentions.addAll(users) }
    fun urgent() { this.priority = Priority.URGENT }
    fun critical() { this.priority = Priority.CRITICAL }
    fun encrypt() { this.encrypted = true }
    fun expires(ttlMs: Long) { this.ttl = ttlMs }
    
    fun image(filename: String, url: String, size: Long = 0, caption: String? = null) {
        media.add(MediaItem(filename, url, "image", size, caption))
    }
    
    fun document(filename: String, url: String, size: Long = 0) {
        media.add(MediaItem(filename, url, "document", size))
    }
    
    fun audio(filename: String, url: String, size: Long = 0) {
        media.add(MediaItem(filename, url, "audio", size))
    }
    
    fun video(filename: String, url: String, size: Long = 0) {
        media.add(MediaItem(filename, url, "video", size))
    }
    
    fun build(): Comm = Comm(
        content = content,
        from = from,
        to = to,
        type = type,
        role = role,
        conversationId = conversationId,
        thread = thread,
        parentId = parentId,
        data = data.toMap(),
        media = media.toList(),
        mentions = mentions.toList(),
        priority = priority,
        encrypted = encrypted,
        ttl = ttl,
        expiresAt = ttl?.let { Instant.now().toEpochMilli() + it }
    )
}

// =====================================
// EXTENSION FUNCTIONS
// =====================================

/**
 * Check if comm is from system
 */
fun Comm.isSystem(): Boolean = role == CommRole.SYSTEM || from == "system"

/**
 * Check if comm is error
 */
fun Comm.isError(): Boolean = type == CommType.ERROR

/**
 * Check if comm is tool-related
 */
fun Comm.isTool(): Boolean = type == CommType.TOOL_CALL || type == CommType.TOOL_RESULT

/**
 * Get tool name from comm
 */
fun Comm.getToolName(): String? = data["tool_name"]?.toString()

/**
 * Get tool params from comm
 */
fun Comm.getToolParams(): String? = data["tool_params"]?.toString() 