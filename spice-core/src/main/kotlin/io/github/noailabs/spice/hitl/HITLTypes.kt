package io.github.noailabs.spice.hitl

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * HITL (Human-in-the-Loop) Option for selection-type interactions
 *
 * Represents a single selectable option in a HITL selection prompt.
 *
 * @property id Unique identifier for this option
 * @property label Display label for the option
 * @property description Optional detailed description
 * @property metadata Additional option-specific metadata
 */
@Serializable
data class HITLOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    companion object {
        /**
         * Create a simple option with just id and label
         */
        fun simple(id: String, label: String): HITLOption =
            HITLOption(id = id, label = label)

        /**
         * Create an option with description
         */
        fun withDescription(id: String, label: String, description: String): HITLOption =
            HITLOption(id = id, label = label, description = description)
    }
}

/**
 * HITL Request Metadata
 *
 * Contains metadata about a HITL request for tracking and processing.
 *
 * @property toolCallId Unique identifier for this HITL request (format: hitl_{runId}_{nodeId}_{invocationIndex})
 * @property hitlType Type of HITL interaction ("input" or "selection")
 * @property prompt Message displayed to the user
 * @property runId Graph run ID for checkpoint correlation
 * @property nodeId Node ID that initiated this HITL request
 * @property graphId Graph ID for context
 * @property invocationIndex Index for this HITL invocation within the run (prevents ID collision in loops)
 * @property options Selection options (only for selection type)
 * @property validationRules Optional validation rules for input type
 * @property timeout Optional timeout in milliseconds
 * @property additionalMetadata Additional custom metadata
 */
@Serializable
data class HITLMetadata(
    val toolCallId: String,
    val hitlType: String,
    val prompt: String,
    val runId: String,
    val nodeId: String,
    val graphId: String? = null,
    val invocationIndex: Int = 0,
    val options: List<HITLOption> = emptyList(),
    val validationRules: Map<String, @Contextual Any> = emptyMap(),
    val timeout: Long? = null,
    val additionalMetadata: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * Check if this is a selection-type HITL request
     */
    val isSelection: Boolean
        get() = hitlType == TYPE_SELECTION

    /**
     * Check if this is an input-type HITL request
     */
    val isInput: Boolean
        get() = hitlType == TYPE_INPUT

    /**
     * Generate the idempotency key for this HITL request
     */
    val idempotencyKey: String
        get() = "$runId:$toolCallId"

    companion object {
        const val TYPE_INPUT = "input"
        const val TYPE_SELECTION = "selection"

        /** Data key for storing HITL invocation counter in SpiceMessage.data */
        const val INVOCATION_INDEX_KEY = "_hitl_invocation_index"

        /**
         * Generate a stable tool_call_id for HITL requests
         *
         * The format is: hitl_{runId}_{nodeId}_{invocationIndex}
         *
         * **Idempotency Rules:**
         * - Same (runId, nodeId, invocationIndex) → Same ID (retry-safe)
         * - Same node called twice → Different invocationIndex → Different ID
         *
         * @param runId Current graph run ID
         * @param nodeId Current node ID
         * @param invocationIndex Index for this invocation (increments per HITL request)
         * @return Stable tool_call_id
         */
        fun generateToolCallId(runId: String, nodeId: String, invocationIndex: Int = 0): String =
            "hitl_${runId}_${nodeId}_$invocationIndex"

        /**
         * Create metadata for an input-type HITL request
         */
        fun forInput(
            runId: String,
            nodeId: String,
            prompt: String,
            invocationIndex: Int = 0,
            graphId: String? = null,
            validationRules: Map<String, Any> = emptyMap(),
            timeout: Long? = null,
            additionalMetadata: Map<String, Any> = emptyMap()
        ): HITLMetadata = HITLMetadata(
            toolCallId = generateToolCallId(runId, nodeId, invocationIndex),
            hitlType = TYPE_INPUT,
            prompt = prompt,
            runId = runId,
            nodeId = nodeId,
            graphId = graphId,
            invocationIndex = invocationIndex,
            validationRules = validationRules,
            timeout = timeout,
            additionalMetadata = additionalMetadata
        )

        /**
         * Create metadata for a selection-type HITL request
         */
        fun forSelection(
            runId: String,
            nodeId: String,
            prompt: String,
            options: List<HITLOption>,
            invocationIndex: Int = 0,
            graphId: String? = null,
            timeout: Long? = null,
            additionalMetadata: Map<String, Any> = emptyMap()
        ): HITLMetadata = HITLMetadata(
            toolCallId = generateToolCallId(runId, nodeId, invocationIndex),
            hitlType = TYPE_SELECTION,
            prompt = prompt,
            runId = runId,
            nodeId = nodeId,
            graphId = graphId,
            invocationIndex = invocationIndex,
            options = options,
            timeout = timeout,
            additionalMetadata = additionalMetadata
        )
    }
}

/**
 * HITL Response from user
 *
 * Represents the user's response to a HITL request.
 *
 * @property toolCallId The tool_call_id this response is for
 * @property value The user's response value (text for input, selected option id(s) for selection)
 * @property selectedOptions List of selected option IDs (for selection type)
 * @property responseType Type of response ("text", "single_selection", "multi_selection")
 * @property timestamp When the response was received (epoch millis)
 * @property metadata Additional response metadata
 */
@Serializable
data class HITLResponse(
    val toolCallId: String,
    val value: String,
    val selectedOptions: List<String> = emptyList(),
    val responseType: String = RESPONSE_TYPE_TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * Check if this is a text input response
     */
    val isTextResponse: Boolean
        get() = responseType == RESPONSE_TYPE_TEXT

    /**
     * Check if this is a selection response
     */
    val isSelectionResponse: Boolean
        get() = responseType in listOf(RESPONSE_TYPE_SINGLE_SELECTION, RESPONSE_TYPE_MULTI_SELECTION)

    /**
     * Get the first selected option (for single selection)
     */
    val firstSelectedOption: String?
        get() = selectedOptions.firstOrNull()

    companion object {
        const val RESPONSE_TYPE_TEXT = "text"
        const val RESPONSE_TYPE_SINGLE_SELECTION = "single_selection"
        const val RESPONSE_TYPE_MULTI_SELECTION = "multi_selection"

        /**
         * Create a text input response
         */
        fun textInput(
            toolCallId: String,
            text: String,
            metadata: Map<String, Any> = emptyMap()
        ): HITLResponse = HITLResponse(
            toolCallId = toolCallId,
            value = text,
            responseType = RESPONSE_TYPE_TEXT,
            metadata = metadata
        )

        /**
         * Create a single selection response
         */
        fun singleSelection(
            toolCallId: String,
            selectedOptionId: String,
            metadata: Map<String, Any> = emptyMap()
        ): HITLResponse = HITLResponse(
            toolCallId = toolCallId,
            value = selectedOptionId,
            selectedOptions = listOf(selectedOptionId),
            responseType = RESPONSE_TYPE_SINGLE_SELECTION,
            metadata = metadata
        )

        /**
         * Create a multi-selection response
         */
        fun multiSelection(
            toolCallId: String,
            selectedOptionIds: List<String>,
            metadata: Map<String, Any> = emptyMap()
        ): HITLResponse = HITLResponse(
            toolCallId = toolCallId,
            value = selectedOptionIds.joinToString(","),
            selectedOptions = selectedOptionIds,
            responseType = RESPONSE_TYPE_MULTI_SELECTION,
            metadata = metadata
        )
    }
}

/**
 * HITL Event Status for EventBus → ToolResult mapping
 *
 * This enum represents the possible statuses of a HITL response event.
 * Each status maps 1:1 to a ToolResultStatus.
 *
 * | HITLEventStatus | ToolResultStatus | Description |
 * |-----------------|------------------|-------------|
 * | COMPLETED       | SUCCESS          | User responded successfully |
 * | TIMEOUT         | TIMEOUT          | User did not respond in time |
 * | CANCELLED       | CANCELLED        | User cancelled the request |
 * | ERROR           | ERROR            | Error processing the response |
 */
@Serializable
enum class HITLEventStatus {
    /** User responded successfully */
    COMPLETED,

    /** User did not respond in time */
    TIMEOUT,

    /** User cancelled the request */
    CANCELLED,

    /** Error processing the response */
    ERROR
}

/**
 * HITL Response Event
 *
 * Event emitted when a HITL response is received from the user.
 * This is used for EventBus communication between the external system
 * and the graph executor.
 *
 * @property toolCallId The tool_call_id this event is for
 * @property status Status of the HITL response
 * @property response The user's response (if status is COMPLETED)
 * @property errorCode Error code (if status is ERROR, TIMEOUT, or CANCELLED)
 * @property errorMessage Error message (if status is ERROR, TIMEOUT, or CANCELLED)
 */
@Serializable
data class HITLResponseEvent(
    val toolCallId: String,
    val status: HITLEventStatus,
    val response: HITLResponse? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null
) {
    companion object {
        /**
         * Create a successful response event
         */
        fun completed(response: HITLResponse): HITLResponseEvent = HITLResponseEvent(
            toolCallId = response.toolCallId,
            status = HITLEventStatus.COMPLETED,
            response = response
        )

        /**
         * Create a timeout event
         */
        fun timeout(toolCallId: String, message: String = "HITL request timed out"): HITLResponseEvent =
            HITLResponseEvent(
                toolCallId = toolCallId,
                status = HITLEventStatus.TIMEOUT,
                errorCode = "HITL_TIMEOUT",
                errorMessage = message
            )

        /**
         * Create a cancelled event
         */
        fun cancelled(toolCallId: String, message: String = "HITL request was cancelled"): HITLResponseEvent =
            HITLResponseEvent(
                toolCallId = toolCallId,
                status = HITLEventStatus.CANCELLED,
                errorCode = "HITL_CANCELLED",
                errorMessage = message
            )

        /**
         * Create an error event
         */
        fun error(toolCallId: String, errorCode: String, message: String): HITLResponseEvent =
            HITLResponseEvent(
                toolCallId = toolCallId,
                status = HITLEventStatus.ERROR,
                errorCode = errorCode,
                errorMessage = message
            )
    }
}
