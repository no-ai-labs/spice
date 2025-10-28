package io.github.noailabs.spice.handoff

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.Comm
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * DSL builder for creating handoff requests
 */
class HandoffRequestBuilder(
    private val fromAgentId: String
) {
    var reason: String = ""
    var priority: HandoffPriority = HandoffPriority.NORMAL
    var toAgentId: String = "human-agent-pool"
    private val tasks = mutableListOf<HandoffTask>()
    private val conversationHistory = mutableListOf<String>()
    private val metadata = mutableMapOf<String, String>()

    /**
     * Add a task for the human to complete
     */
    fun task(
        description: String,
        type: HandoffTaskType = HandoffTaskType.RESPOND,
        required: Boolean = true,
        context: Map<String, String> = emptyMap()
    ) {
        tasks.add(
            HandoffTask(
                id = UUID.randomUUID().toString(),
                description = description,
                type = type,
                context = context,
                required = required
            )
        )
    }

    /**
     * Add conversation history
     */
    fun addHistory(message: String) {
        conversationHistory.add(message)
    }

    /**
     * Add metadata
     */
    fun addMetadata(key: String, value: String) {
        metadata[key] = value
    }

    internal fun build(): HandoffRequest {
        require(reason.isNotBlank()) { "Handoff reason is required" }
        require(tasks.isNotEmpty()) { "At least one task is required" }

        return HandoffRequest(
            reason = reason,
            tasks = tasks,
            priority = priority,
            conversationHistory = conversationHistory,
            metadata = metadata,
            fromAgentId = fromAgentId,
            toAgentId = toAgentId
        )
    }
}

/**
 * Create a handoff request using DSL
 */
fun handoffRequest(
    fromAgentId: String,
    block: HandoffRequestBuilder.() -> Unit
): HandoffRequest {
    return HandoffRequestBuilder(fromAgentId).apply(block).build()
}

/**
 * Extension: Create a Comm for handing off to a human
 */
fun Comm.handoff(
    fromAgentId: String,
    block: HandoffRequestBuilder.() -> Unit
): Comm {
    val request = handoffRequest(fromAgentId, block)
    val handoffId = UUID.randomUUID().toString()

    return this.copy(
        content = "🔄 Handoff Request: ${request.reason}",
        from = fromAgentId,
        to = request.toAgentId,
        data = this.data + mapOf(
            HandoffMetadataKeys.HANDOFF_REQUEST to Json.encodeToString(request),
            HandoffMetadataKeys.HANDOFF_ID to handoffId,
            HandoffMetadataKeys.IS_HANDOFF to "true",
            HandoffMetadataKeys.ORIGINAL_AGENT to fromAgentId
        ),
        context = this.context?.plusAll(mapOf(
            HandoffMetadataKeys.HANDOFF_ID to handoffId,
            HandoffMetadataKeys.IS_HANDOFF to "true"
        ))
    )
}

/**
 * Extension: Check if this Comm is a handoff request
 */
fun Comm.isHandoff(): Boolean {
    return data[HandoffMetadataKeys.IS_HANDOFF] == "true"
}

/**
 * Extension: Get the handoff request from this Comm
 */
fun Comm.getHandoffRequest(): HandoffRequest? {
    val requestJson = data[HandoffMetadataKeys.HANDOFF_REQUEST] ?: return null
    return try {
        Json.decodeFromString<HandoffRequest>(requestJson)
    } catch (e: Exception) {
        null
    }
}

/**
 * Extension: Create a Comm for returning from handoff
 */
fun Comm.returnFromHandoff(
    humanAgentId: String,
    result: String,
    completedTasks: List<CompletedTask> = emptyList(),
    notes: String? = null
): Comm {
    val handoffId = data[HandoffMetadataKeys.HANDOFF_ID] ?: UUID.randomUUID().toString()
    val originalAgentId = data[HandoffMetadataKeys.ORIGINAL_AGENT] ?: "unknown"

    val response = HandoffResponse(
        handoffId = handoffId,
        humanAgentId = humanAgentId,
        result = result,
        completedTasks = completedTasks,
        returnToBot = true,
        notes = notes
    )

    return this.copy(
        content = result,
        from = humanAgentId,
        to = originalAgentId,
        data = this.data + mapOf(
            HandoffMetadataKeys.HANDOFF_RESPONSE to Json.encodeToString(response),
            HandoffMetadataKeys.IS_RETURN_FROM_HANDOFF to "true",
            HandoffMetadataKeys.HANDOFF_ID to handoffId
        ),
        context = this.context?.plusAll(mapOf(
            HandoffMetadataKeys.HANDOFF_ID to handoffId,
            HandoffMetadataKeys.IS_RETURN_FROM_HANDOFF to "true"
        ))
    )
}

/**
 * Extension: Check if this Comm is a return from handoff
 */
fun Comm.isReturnFromHandoff(): Boolean {
    return data[HandoffMetadataKeys.IS_RETURN_FROM_HANDOFF] == "true"
}

/**
 * Extension: Get the handoff response from this Comm
 */
fun Comm.getHandoffResponse(): HandoffResponse? {
    val responseJson = data[HandoffMetadataKeys.HANDOFF_RESPONSE] ?: return null
    return try {
        Json.decodeFromString<HandoffResponse>(responseJson)
    } catch (e: Exception) {
        null
    }
}
