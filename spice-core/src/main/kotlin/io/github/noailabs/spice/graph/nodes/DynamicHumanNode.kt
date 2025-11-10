package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult
import java.time.Duration
import java.time.Instant

/**
 * HumanNode with dynamic prompt that reads from NodeContext state.
 *
 * Unlike the standard HumanNode which has a static prompt, DynamicHumanNode
 * reads the prompt text from the execution state at runtime. This allows
 * agents to generate prompts dynamically based on their processing results.
 *
 * Example usage:
 * ```kotlin
 * graph("reservation-workflow") {
 *     // Agent generates menu and stores in state
 *     agent("list-reservations", listAgent)
 *
 *     // DynamicHumanNode reads menu from state["menu_text"]
 *     dynamicHumanNode(
 *         id = "select-reservation",
 *         promptKey = "menu_text",
 *         fallbackPrompt = "Please make a selection"
 *     )
 *
 *     // Agent processes user selection
 *     agent("cancel-reservation", cancelAgent)
 * }
 *
 * // In the listAgent:
 * comm.withData(mapOf(
 *     "menu_text" to "1. Hotel A\n2. Hotel B\n3. Hotel C",
 *     "reservations" to reservationsList
 * ))
 * ```
 *
 * @param id Unique identifier for this node
 * @param promptKey Key in NodeContext.state to read the prompt from (default: "menu_text")
 * @param fallbackPrompt Prompt to use if promptKey is not found in state
 * @param options List of predefined options for multiple choice (empty for free text)
 * @param timeout Optional timeout for human response
 * @param validator Optional validator function for human response
 * @param allowFreeText Whether to allow free text input (default: true if options is empty)
 */
class DynamicHumanNode(
    override val id: String,
    private val promptKey: String = "menu_text",
    private val fallbackPrompt: String = "사용자 입력을 기다립니다...",
    val options: List<HumanOption> = emptyList(),
    val timeout: Duration? = null,
    val validator: ((HumanResponse) -> Boolean)? = null,
    val allowFreeText: Boolean = options.isEmpty()
) : Node {

    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Read dynamic prompt from state or context
        // Try state first (direct state updates), then context (from AgentNode metadata)
        val dynamicPrompt = (ctx.state[promptKey] as? String)
            ?: (ctx.context.get(promptKey) as? String)
            ?: fallbackPrompt

        val interaction = HumanInteraction(
            nodeId = id,
            prompt = dynamicPrompt,
            options = options,
            pausedAt = Instant.now().toString(),
            expiresAt = timeout?.let { Instant.now().plus(it).toString() },
            allowFreeText = allowFreeText
        )

        val additional = mapOf(
            "type" to "human-interaction",
            "requires_human_input" to true,
            "prompt_key" to promptKey  // For debugging
        )

        return SpiceResult.success(
            NodeResult.fromContext(ctx, data = interaction, additional = additional)
        )
    }
}
