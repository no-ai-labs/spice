package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.toolspec.OAIToolCall
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 🧩 Subgraph Checkpoint Context for Spice Framework 1.3.0
 *
 * Stores the context needed to resume execution inside a subgraph after HITL pause.
 * When a subgraph contains an HITL node, this context preserves the path back
 * to the parent graph so that resume can properly re-enter the subgraph.
 *
 * **Stack-based Design:**
 * For nested subgraphs (Parent → Child1 → Child2), the checkpoint stores a stack
 * of contexts. Resume pops from the stack, executing each child until completion,
 * then applies outputMapping back to parent.
 *
 * **Example Flow:**
 * ```
 * Initial execution:
 *   Parent.SubgraphNode("confirmation")
 *     → Child.ToolNode("ask_confirm") [HITL]
 *     → WAITING, checkpoint saved with subgraphStack = [
 *         SubgraphCheckpointContext(parentNodeId="confirmation", childGraphId="generic_confirmation", ...)
 *       ]
 *
 * Resume:
 *   GraphRunner.resume()
 *     → Detects subgraphStack is not empty
 *     → Re-enters subgraph at childNodeId
 *     → Subgraph completes → outputMapping applied
 *     → Parent continues from next node
 * ```
 *
 * @property parentNodeId The SubgraphNode ID in the parent graph
 * @property parentGraphId The parent graph's ID
 * @property parentRunId The parent graph's runId (for restoration)
 * @property childGraphId The child (subgraph) graph's ID
 * @property childNodeId The node ID inside the child graph where HITL paused
 * @property childRunId The child graph's runId (namespaced from parent)
 * @property outputMapping Child → Parent data key mapping (childKey → parentKey)
 * @property depth Nesting depth (1 = direct child of main graph)
 * @since 1.3.0
 */
@Serializable
data class SubgraphCheckpointContext(
    val parentNodeId: String,
    val parentGraphId: String,
    val parentRunId: String,
    val childGraphId: String,
    val childNodeId: String,
    val childRunId: String,
    val outputMapping: Map<String, String> = emptyMap(),
    val depth: Int = 1
) {
    companion object {
        /**
         * Metadata key used to pass subgraph context through SpiceMessage
         */
        const val METADATA_KEY = "__subgraphContext"

        /**
         * Metadata key for the subgraph stack (nested subgraphs)
         */
        const val STACK_METADATA_KEY = "__subgraphStack"
    }
}

/**
 * 💾 Checkpoint for Spice Framework 1.0.0
 *
 * Represents a saved execution state for graph resumption.
 * Used for HITL workflows, long-running processes, and crash recovery.
 *
 * **Architecture:**
 * - Checkpoint saved when message enters WAITING state
 * - Restored when resuming execution via GraphRunner.resume()
 * - Serialized to JSON for storage (Redis, DB, file system)
 *
 * **Key Fields:**
 * - `id`: Unique checkpoint identifier
 * - `runId`: Links to specific graph execution run
 * - `graphId`: Graph definition identifier
 * - `currentNodeId`: Node where execution paused
 * - `state`: Complete execution state (nested Maps/Lists supported)
 * - `message`: The SpiceMessage at pause point
 * - `subgraphStack`: Stack of subgraph contexts for nested HITL (1.3.0+)
 *
 * **Subgraph HITL Support (1.3.0+):**
 * When HITL occurs inside a subgraph, the checkpoint stores the subgraph context
 * stack. On resume, GraphRunner uses this stack to re-enter the subgraph chain
 * and properly apply outputMapping when each subgraph completes.
 *
 * **Serialization:**
 * - Use CheckpointSerializer for production (preserves types)
 * - Direct kotlinx.serialization for simple cases
 *
 * @since 1.0.0, enhanced 1.3.0 (subgraph HITL)
 */
@Serializable
data class Checkpoint(
    val id: String,
    val runId: String,
    val graphId: String,
    val currentNodeId: String,

    // Execution state (nested structures preserved)
    val state: Map<String, @Contextual Any> = emptyMap(),
    val metadata: Map<String, @Contextual Any> = emptyMap(),

    // SpiceMessage at checkpoint
    val message: SpiceMessage? = null,

    // Execution status
    val executionState: GraphExecutionState = GraphExecutionState.WAITING_FOR_HUMAN,

    // Spice 2.0: Tool call based HITL (event-first architecture)
    val pendingToolCall: OAIToolCall? = null,      // REQUEST_USER_INPUT/SELECTION from HITL Tool
    val responseToolCall: OAIToolCall? = null,     // USER_RESPONSE from user

    // Spice 1.3.0: Subgraph HITL support
    // Stack of subgraph contexts for nested subgraph resume
    // Order: [outermost, ..., innermost] - innermost is where HITL actually paused
    val subgraphStack: List<SubgraphCheckpointContext> = emptyList(),

    // Timestamps
    val timestamp: Instant = Clock.System.now(),
    val expiresAt: Instant? = null
) {
    companion object {
        /**
         * Generate unique checkpoint ID
         */
        fun generateId(): String {
            return "cp_${Clock.System.now().toEpochMilliseconds()}_${(Math.random() * 1000000).toInt()}"
        }

        /**
         * Create checkpoint from message in WAITING state
         */
        fun fromMessage(
            message: SpiceMessage,
            graphId: String,
            runId: String
        ): Checkpoint {
            require(message.state == io.github.noailabs.spice.ExecutionState.WAITING) {
                "Can only create checkpoint from WAITING message, got: ${message.state}"
            }

            // Spice 2.0: Extract tool call from message (REQUEST_USER_INPUT or REQUEST_USER_SELECTION)
            // Use lastOrNull to get the most recent tool call (handles loops/retries where multiple tool calls accumulate)
            // Supports both legacy names and Spice 1.0.6+ HITL Tool standard names
            val pendingToolCall = message.toolCalls.lastOrNull {
                it.function.name in listOf(
                    // Legacy names (backward compatibility)
                    "request_user_input",
                    "request_user_selection",
                    "request_user_confirmation",
                    // Spice 1.0.6+ HITL Tool standard names (from OAIToolCall.Companion.ToolNames)
                    OAIToolCall.Companion.ToolNames.HITL_REQUEST_INPUT,      // "hitl_request_input"
                    OAIToolCall.Companion.ToolNames.HITL_REQUEST_SELECTION   // "hitl_request_selection"
                )
            }

            // Spice 1.3.0: Extract subgraph stack from message metadata
            val subgraphStack = extractSubgraphStack(message)

            return Checkpoint(
                id = generateId(),
                runId = runId,
                graphId = graphId,
                currentNodeId = message.nodeId ?: error("WAITING message must have nodeId"),
                message = message,
                state = message.data,
                metadata = message.metadata,
                pendingToolCall = pendingToolCall,
                subgraphStack = subgraphStack,
                timestamp = Clock.System.now()
            )
        }

        /**
         * Extract subgraph stack from message metadata
         *
         * The subgraph stack is stored in metadata by SubgraphNode when HITL occurs
         * inside a (nested) subgraph. Each entry represents one level of subgraph nesting.
         */
        @Suppress("UNCHECKED_CAST")
        private fun extractSubgraphStack(message: SpiceMessage): List<SubgraphCheckpointContext> {
            // Try to get the stack first (for nested subgraphs)
            val stackRaw = message.metadata[SubgraphCheckpointContext.STACK_METADATA_KEY]
            if (stackRaw is List<*>) {
                return stackRaw.mapNotNull { item ->
                    when (item) {
                        is SubgraphCheckpointContext -> item
                        is Map<*, *> -> deserializeSubgraphContext(item as Map<String, Any?>)
                        else -> null
                    }
                }
            }

            // Try to get single context (for single-level subgraph)
            val singleRaw = message.metadata[SubgraphCheckpointContext.METADATA_KEY]
            if (singleRaw != null) {
                val context = when (singleRaw) {
                    is SubgraphCheckpointContext -> singleRaw
                    is Map<*, *> -> deserializeSubgraphContext(singleRaw as Map<String, Any?>)
                    else -> null
                }
                if (context != null) {
                    return listOf(context)
                }
            }

            return emptyList()
        }

        /**
         * Deserialize SubgraphCheckpointContext from a Map
         * (needed when context is serialized/deserialized through JSON)
         */
        private fun deserializeSubgraphContext(map: Map<String, Any?>): SubgraphCheckpointContext? {
            return try {
                SubgraphCheckpointContext(
                    parentNodeId = map["parentNodeId"] as? String ?: return null,
                    parentGraphId = map["parentGraphId"] as? String ?: return null,
                    parentRunId = map["parentRunId"] as? String ?: return null,
                    childGraphId = map["childGraphId"] as? String ?: return null,
                    childNodeId = map["childNodeId"] as? String ?: return null,
                    childRunId = map["childRunId"] as? String ?: return null,
                    outputMapping = (map["outputMapping"] as? Map<*, *>)
                        ?.mapNotNull { (k, v) ->
                            if (k is String && v is String) k to v else null
                        }?.toMap() ?: emptyMap(),
                    depth = (map["depth"] as? Number)?.toInt() ?: 1
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Check if this checkpoint is for a subgraph HITL
     * (i.e., the HITL occurred inside a subgraph)
     */
    fun isSubgraphHitl(): Boolean = subgraphStack.isNotEmpty()

    /**
     * Get the innermost subgraph context (where HITL actually paused)
     */
    fun innermostSubgraphContext(): SubgraphCheckpointContext? = subgraphStack.lastOrNull()

    /**
     * Check if checkpoint has expired
     */
    fun isExpired(): Boolean {
        return expiresAt?.let { Clock.System.now() > it } ?: false
    }
}

/**
 * 📊 Graph Execution State (for checkpoints)
 */
@Serializable
enum class GraphExecutionState {
    RUNNING,
    WAITING_FOR_HUMAN,
    COMPLETED,
    FAILED,
    CANCELLED
}
