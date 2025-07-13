package io.github.spice

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

/**
 * Message role for conversation context
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

/**
 * Core Message class for JVM-Autogen
 * All agent-to-agent communication happens via Message.
 */
@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val role: MessageRole = MessageRole.USER,
    val sender: String,
    val receiver: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = Instant.now().toEpochMilli(),
    val parentId: String? = null,
    val conversationId: String? = null
) {

    /**
     * Create a reply message based on the current one
     */
    fun createReply(
        content: String,
        sender: String,
        type: MessageType = MessageType.TEXT,
        role: MessageRole = MessageRole.ASSISTANT,
        metadata: Map<String, String> = emptyMap()
    ): Message {
        return copy(
            id = UUID.randomUUID().toString(),
            content = content,
            sender = sender,
            receiver = this.sender,
            type = type,
            role = role,
            metadata = metadata,
            timestamp = Instant.now().toEpochMilli(),
            parentId = this.id,
            conversationId = this.conversationId ?: this.id
        )
    }

    /**
     * Forward message to another agent (with updated receiver/timestamp)
     */
    fun forward(newReceiver: String): Message {
        return copy(
            receiver = newReceiver,
            timestamp = Instant.now().toEpochMilli()
        )
    }

    /**
     * Add metadata to the message
     */
    fun withMetadata(key: String, value: String): Message {
        return copy(metadata = metadata + (key to value))
    }

    /**
     * Change the message type
     */
    fun withType(newType: MessageType): Message {
        return copy(type = newType)
    }
    
    /**
     * Change the message role
     */
    fun withRole(newRole: MessageRole): Message {
        return copy(role = newRole)
    }
}

/**
 * Definition of message types
 */
enum class MessageType {
    TEXT,           // General text message
    SYSTEM,         // System message
    TOOL_CALL,      // Tool invocation
    TOOL_RESULT,    // Tool execution result
    ERROR,          // Error message
    DATA,           // Data transfer
    PROMPT,         // Prompt message
    RESULT,         // Final result
    BRANCH,         // Branch message
    MERGE,          // Merge message
    WORKFLOW_START, // Workflow start
    WORKFLOW_END,   // Workflow end
    INTERRUPT,      // Interrupt message
    RESUME
}

/**
 * Reason for an interrupt message (for UI, routing, etc)
 */
enum class InterruptReason {
    CLARIFICATION_NEEDED,
    USER_CONFIRMATION,
    MISSING_DATA
}

/**
 * Message routing rule (used for DAG transformation)
 */
data class MessageRoutingRule(
    val sourceType: MessageType,
    val targetType: MessageType,
    val condition: (Message) -> Boolean = { true },
    val transformer: (Message) -> Message = { it }
) {
    
    fun canRoute(message: Message): Boolean {
        return message.type == sourceType && condition(message)
    }
    
    fun route(message: Message): Message {
        return transformer(message).withType(targetType)
    }
}

/**
 * Message router - handles Mentat DAG node transformation logic
 */
class MessageRouter {
    private val rules = mutableListOf<MessageRoutingRule>()
    
    fun addRule(rule: MessageRoutingRule) {
        rules.add(rule)
    }
    
    fun route(message: Message): List<Message> {
        val applicableRules = rules.filter { it.canRoute(message) }
        
        return if (applicableRules.isEmpty()) {
            listOf(message) // Default forwarding
        } else {
            applicableRules.map { it.route(message) }
        }
    }
    
    companion object {
        /**
         * Convert Spice Flow Graph rules into routing rules
         */
        fun createSpiceFlowRules(): MessageRouter {
            val router = MessageRouter()
            
            // Rule 1: PROMPT → PROMPT converts second to SYSTEM
            router.addRule(MessageRoutingRule(
                sourceType = MessageType.PROMPT,
                targetType = MessageType.SYSTEM,
                condition = { msg -> msg.metadata["isSecondPrompt"] == "true" },
                transformer = { msg -> msg.withMetadata("autoUpgraded", "promptToSystem") }
            ))
            
            // Rule 2: DATA → RESULT inserts SYSTEM message in between
            router.addRule(MessageRoutingRule(
                sourceType = MessageType.DATA,
                targetType = MessageType.SYSTEM,
                condition = { msg -> msg.metadata["targetType"] == "RESULT" },
                transformer = { msg -> 
                    msg.withMetadata("autoInserted", "dataToResult")
                       .withMetadata("nextTarget", "RESULT")
                }
            ))
            
            // Rule 3: BRANCH → Split into multiple paths
            router.addRule(MessageRoutingRule(
                sourceType = MessageType.BRANCH,
                targetType = MessageType.SYSTEM,
                transformer = { msg ->
                    msg.withMetadata("branchExpanded", "true")
                }
            ))
            
            return router
        }
    }
} 