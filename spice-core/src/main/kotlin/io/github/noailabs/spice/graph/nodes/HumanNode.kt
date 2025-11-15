package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.toolspec.OAIToolCall
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * 👤 Human Node for Spice Framework 1.0.0
 *
 * Pauses graph execution and waits for human input (HITL pattern).
 *
 * **BREAKING CHANGE from 0.x:**
 * - Uses SpiceMessage state machine (RUNNING → WAITING)
 * - HumanInteraction embedded in message.data
 * - No separate checkpoint state field
 *
 * **Architecture:**
 * ```
 * Input Message (RUNNING)
 *   ↓
 * HumanNode.run()
 *   ↓
 * Output Message (WAITING)
 *   - HumanInteraction in data
 *   - State = WAITING
 *   ↓
 * GraphRunner saves checkpoint
 *   ↓
 * [Wait for human response...]
 *   ↓
 * Resume with HumanResponse
 *   ↓
 * Message (RUNNING) with response data
 * ```
 *
 * **Usage:**
 * ```kotlin
 * graph("booking") {
 *     agent("search", searchAgent)
 *     human("select", "Please select a reservation", options = listOf(
 *         HumanOption("res1", "Reservation 1"),
 *         HumanOption("res2", "Reservation 2")
 *     ))
 *     agent("confirm", confirmAgent)
 *
 *     edge("search", "select")
 *     edge("select", "confirm")
 * }
 * ```
 *
 * @property prompt Message to display to human
 * @property options Multiple choice options (if any)
 * @property timeout Optional timeout duration
 * @property validator Optional validation function for responses
 * @property allowFreeText Allow free-form text input (default: true if no options)
 * @since 1.0.0
 */
class HumanNode(
    override val id: String,
    val prompt: String,
    val options: List<HumanOption> = emptyList(),
    val timeout: Duration? = null,
    val validator: ((HumanResponse) -> Boolean)? = null,
    val allowFreeText: Boolean = options.isEmpty()
) : Node {

    /**
     * Execute human node - transitions to WAITING state
     *
     * **Flow:**
     * 1. Create HumanInteraction with prompt/options
     * 2. Transition message to WAITING state
     * 3. Embed interaction in message.data
     * 4. Return message for checkpoint save
     *
     * **State Transition:**
     * - RUNNING → WAITING (paused for human input)
     *
     * @param message Input message (must be RUNNING)
     * @return SpiceResult with message in WAITING state
     */
    override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        // Create human interaction (still needed for backward compatibility and expiration tracking)
        val interaction = HumanInteraction(
            nodeId = id,
            prompt = prompt,
            options = options,
            pausedAt = Clock.System.now(),
            expiresAt = timeout?.let { Clock.System.now() + it },
            allowFreeText = allowFreeText
        )

        // Spice 2.0: Create appropriate tool call based on interaction type
        val toolCall = if (options.isNotEmpty()) {
            // Multiple choice selection
            OAIToolCall.selection(
                items = options.map { option ->
                    mapOf(
                        "id" to option.id,
                        "label" to option.label,
                        "description" to (option.description ?: ""),
                        "metadata" to option.metadata
                    )
                },
                promptMessage = prompt,
                selectionType = "single", // Future: support "multiple" based on node config
                metadata = mapOf(
                    "node_id" to id,
                    "paused_at" to interaction.pausedAt.toString(),
                    "expires_at" to (interaction.expiresAt?.toString() ?: ""),
                    "allow_free_text" to allowFreeText.toString()
                )
            )
        } else {
            // Free-form input
            OAIToolCall.hitl(
                type = "input",
                question = prompt,
                context = mapOf(
                    "node_id" to id,
                    "paused_at" to interaction.pausedAt.toString(),
                    "expires_at" to (interaction.expiresAt?.toString() ?: ""),
                    "allow_free_text" to allowFreeText.toString()
                )
            )
        }

        // Transition to WAITING state
        val waitingMessage = message.transitionTo(
            newState = ExecutionState.WAITING,
            reason = "Waiting for human input: $prompt",
            nodeId = id
        )

        // Spice 2.0: Add tool call to message (event-first architecture)
        // Also keep interaction in data for backward compatibility during transition
        val output = waitingMessage
            .withToolCall(toolCall)
            .withData(
                mapOf(
                    "human_interaction" to interaction,  // Backward compat
                    "requires_human_input" to true,       // Backward compat
                    "type" to "human-interaction"         // Backward compat
                )
            )
            .withMetadata(
                mapOf(
                    "paused_node_id" to id,
                    "paused_at" to interaction.pausedAt.toString()
                )
            )

        return SpiceResult.success(output)
    }
}

/**
 * 📝 Human Input Option
 *
 * Represents a choice in multiple-choice human interaction.
 *
 * @property id Unique identifier for this option
 * @property label Display label for user
 * @property description Optional description
 * @property metadata Additional metadata
 */
@Serializable
data class HumanOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 🎫 Human Interaction
 *
 * Represents a pending human interaction.
 * Embedded in SpiceMessage.data when graph pauses.
 *
 * @property nodeId Node ID that requested interaction
 * @property prompt Message to display
 * @property options Available options (if multiple choice)
 * @property pausedAt When interaction was created
 * @property expiresAt When interaction expires (optional)
 * @property allowFreeText Whether free-form text is allowed
 */
@Serializable
data class HumanInteraction(
    val nodeId: String,
    val prompt: String,
    val options: List<HumanOption> = emptyList(),
    val pausedAt: Instant,
    val expiresAt: Instant? = null,
    val allowFreeText: Boolean = true
) {
    /**
     * Check if interaction has expired
     */
    fun isExpired(): Boolean {
        return expiresAt?.let { Clock.System.now() >= it } ?: false
    }

    /**
     * Get remaining time before expiration
     */
    fun remainingTime(): Duration? {
        return expiresAt?.let {
            val now = Clock.System.now()
            if (now < it) {
                it - now
            } else {
                Duration.ZERO
            }
        }
    }
}

/**
 * 💬 Human Response
 *
 * User's response to a human interaction.
 * Used to resume graph execution.
 *
 * @property nodeId Node ID that requested interaction
 * @property selectedOption Selected option ID (if multiple choice)
 * @property freeText Free-form text input (if allowed)
 * @property metadata Additional data from user
 * @property respondedAt When response was received
 */
@Serializable
data class HumanResponse(
    val nodeId: String,
    val selectedOption: String? = null,
    val freeText: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val respondedAt: Instant = Clock.System.now()
) {
    /**
     * Get effective response value
     * Returns selectedOption or freeText (whichever is provided)
     */
    fun getValue(): String? {
        return selectedOption ?: freeText
    }

    /**
     * Validate response has a value
     */
    fun hasValue(): Boolean {
        return selectedOption != null || freeText != null
    }
}
