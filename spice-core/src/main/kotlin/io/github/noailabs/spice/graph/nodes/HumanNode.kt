package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * Node that pauses graph execution and waits for human input.
 * This is the core of the HITL (Human-in-the-Loop) pattern.
 */
class HumanNode(
    override val id: String,
    val prompt: String,
    val options: List<HumanOption> = emptyList(),
    val timeout: Duration? = null,
    val validator: ((HumanResponse) -> Boolean)? = null,
    val allowFreeText: Boolean = options.isEmpty()
) : Node {

    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // HumanNode doesn't execute immediately
        // It signals that graph should pause and wait for human input
        val interaction = HumanInteraction(
            nodeId = id,
            prompt = prompt,
            options = options,
            pausedAt = Instant.now().toString(),
            expiresAt = timeout?.let { Instant.now().plus(it).toString() },
            allowFreeText = allowFreeText
        )
        val additional = mapOf(
            "type" to "human-interaction",
            "requires_human_input" to true
        )
        return SpiceResult.success(
            NodeResult.fromContext(ctx, data = interaction, additional = additional)
        )
    }
}

/**
 * Human input option (for multiple choice)
 */
@Serializable
data class HumanOption(
    val id: String,
    val label: String,
    val description: String? = null
)

/**
 * Response from human after completing interaction
 */
@Serializable
data class HumanResponse(
    val nodeId: String,
    val selectedOption: String? = null,
    val text: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: String = Instant.now().toString()
) {
    companion object {
        fun choice(nodeId: String, optionId: String): HumanResponse {
            return HumanResponse(nodeId = nodeId, selectedOption = optionId)
        }

        fun text(nodeId: String, text: String): HumanResponse {
            return HumanResponse(nodeId = nodeId, text = text)
        }
    }
}

/**
 * Represents a pending human interaction
 */
@Serializable
data class HumanInteraction(
    val nodeId: String,
    val prompt: String,
    val options: List<HumanOption>,
    val pausedAt: String = Instant.now().toString(),
    val expiresAt: String? = null,
    val allowFreeText: Boolean = false
)

/**
 * Graph execution state
 */
enum class GraphExecutionState {
    RUNNING,
    WAITING_FOR_HUMAN,
    COMPLETED,
    FAILED,
    CANCELLED
}
