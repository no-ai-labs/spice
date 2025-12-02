package io.github.noailabs.spice.types

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Canonical Types for Kafka Bridge Integration
 * - ConversationTurn: Conversation history (included in Kafka messages)
 * - WorkflowState: Workflow state (included in Kafka messages)
 *
 * Phase 0 Contract:
 * - Shared types between AutoAll and kai-core
 * - Used as conversation_history, workflow_state fields in SpiceMessage
 *
 * @since Spice 1.6.0
 */

/**
 * ConversationTurn - Conversation turn
 * - Simplified version included in Kafka messages (last 10 turns)
 * - Full history stored in Redis SessionHistory
 */
@Serializable
data class ConversationTurn(
    /**
     * Role
     * - "user": User
     * - "assistant": AI Agent
     * - "system": System message
     */
    val role: String,

    /**
     * Message content
     */
    val content: String,

    /**
     * Timestamp (ISO-8601 string)
     */
    val timestamp: Instant,

    /**
     * Tool Calls (optional)
     * - Only included in assistant turns
     * - OpenAI Tool Call structure
     * - Stored as JsonElement (kotlinx.serialization compatible)
     */
    val toolCalls: JsonElement? = null
) {
    /**
     * Serialize to JSON string
     */
    fun toJson(): String = json.encodeToString(this)

    /**
     * Convert to Map (for SpiceMessage.data interoperability)
     */
    fun toMap(): Map<String, Any> = buildMap {
        put("role", role)
        put("content", content)
        put("timestamp", timestamp.toString())
        toolCalls?.let { put("toolCalls", it) }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        /**
         * Deserialize from JSON string
         */
        fun fromJson(jsonString: String): ConversationTurn = json.decodeFromString(jsonString)

        /**
         * Restore from Map (for SpiceMessage.data interoperability)
         */
        fun fromMap(map: Map<*, *>): ConversationTurn {
            return ConversationTurn(
                role = map["role"] as? String ?: "user",
                content = map["content"] as? String ?: "",
                timestamp = Instant.parse(map["timestamp"] as? String ?: Instant.DISTANT_PAST.toString()),
                toolCalls = null
            )
        }
    }
}

/**
 * WorkflowState - Workflow state
 * - Simplified version included in Kafka messages (current state only)
 * - Full state stored in Redis SessionHistory
 */
@Serializable
data class WorkflowState(
    /**
     * Current Step ID
     */
    val stepId: String,

    /**
     * Current Step name (Human-readable)
     */
    val stepName: String,

    /**
     * Collected data (simplified version)
     * - Full data stored in Redis
     * - Only essential info included to minimize Kafka message size
     * - Stored as JsonElement (kotlinx.serialization compatible)
     */
    val collectedData: Map<String, JsonElement> = emptyMap(),

    /**
     * Next possible steps
     */
    val nextSteps: List<String> = emptyList(),

    /**
     * Additional metadata
     * - Stored as JsonElement (kotlinx.serialization compatible)
     */
    val metadata: Map<String, JsonElement> = emptyMap()
) {
    /**
     * Serialize to JSON string
     */
    fun toJson(): String = json.encodeToString(this)

    /**
     * Convert to Map (for SpiceMessage.data interoperability)
     */
    fun toMap(): Map<String, Any> = buildMap {
        put("stepId", stepId)
        put("stepName", stepName)
        if (collectedData.isNotEmpty()) put("collectedData", collectedData)
        if (nextSteps.isNotEmpty()) put("nextSteps", nextSteps)
        if (metadata.isNotEmpty()) put("metadata", metadata)
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        /**
         * Deserialize from JSON string
         */
        fun fromJson(jsonString: String): WorkflowState = json.decodeFromString(jsonString)

        /**
         * Restore from Map (for SpiceMessage.data interoperability)
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<*, *>): WorkflowState {
            return WorkflowState(
                stepId = map["stepId"] as? String ?: "",
                stepName = map["stepName"] as? String ?: "",
                collectedData = emptyMap(),
                nextSteps = (map["nextSteps"] as? List<String>) ?: emptyList(),
                metadata = emptyMap()
            )
        }
    }
}
