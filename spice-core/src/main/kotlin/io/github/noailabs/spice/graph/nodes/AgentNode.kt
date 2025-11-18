package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.error.SpiceResult

/**
 * 🤖 Agent Node for Spice Framework 1.0.0
 *
 * Wraps an Agent and integrates it into graph execution.
 *
 * **BREAKING CHANGE from 0.x:**
 * - Direct SpiceMessage → Agent → SpiceMessage flow
 * - No more Context/Result conversion
 * - Tool calls automatically passed through
 * - State machine integrated
 *
 * **Architecture:**
 * ```
 * Input Message (RUNNING)
 *   ↓
 * Agent.processMessage()
 *   ↓
 * Output Message (RUNNING/WAITING/COMPLETED)
 *   ↓
 * Tool calls propagated in message.toolCalls
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val agent = MyAgent(id = "booking-agent")
 * val node = AgentNode(agent)
 *
 * graph("workflow") {
 *     agent("booking", agent)  // Uses AgentNode internally
 *     output("result")
 *     edge("booking", "result")
 * }
 * ```
 *
 * @property agent The wrapped agent instance
 * @since 1.0.0
 */
class AgentNode(
    private val agent: Agent
) : Node {
    override val id: String = agent.id

    /**
     * Execute agent with message
     *
     * **Flow:**
     * 1. Validate agent is ready
     * 2. Call agent.processMessage()
     * 3. Preserve message correlation
     * 4. Propagate tool calls
     * 5. Update graph context
     *
     * **State Handling:**
     * - Input state: Typically RUNNING
     * - Output state: Depends on agent logic (RUNNING/WAITING/COMPLETED)
     *
     * **Tool Calls:**
     * - Agent can add tool calls via message.withToolCall()
     * - Tool calls are preserved in output message
     * - Next nodes can access via message.toolCalls
     *
     * @param message Input message with execution context
     * @return SpiceResult with agent response or error
     */
    override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        // Validate agent is ready
        if (!agent.isReady()) {
            return SpiceResult.failure(
                io.github.noailabs.spice.error.SpiceError.executionError(
                    "Agent is not ready: ${agent.id}",
                    nodeId = id
                )
            )
        }

        // Execute agent
        return when (val result = agent.processMessage(message)) {
            is SpiceResult.Success -> {
                val response = result.value

                // Ensure correlationId is preserved
                val output = if (response.correlationId != message.correlationId) {
                    response.copy(correlationId = message.correlationId)
                } else {
                    response
                }

                // Update graph context
                val contextualizedOutput = output.withGraphContext(
                    graphId = message.graphId,
                    nodeId = id,
                    runId = message.runId
                )

                SpiceResult.success(contextualizedOutput)
            }
            is SpiceResult.Failure -> {
                // Propagate agent error
                SpiceResult.failure(result.error)
            }
        }
    }

    /**
     * Get wrapped agent
     * @return Agent instance
     */
    fun getAgent(): Agent = agent
}
