package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.runner.GraphRunner

/**
 * Subgraph Node for Spice Framework 1.0.4
 *
 * Executes a child graph within a parent graph, managing context propagation
 * and checkpoint coordination.
 *
 * **Features:**
 * - Automatic context propagation (subgraphDepth, parentGraphId, subgraphPath)
 * - Checkpoint coordination with parent runId namespace
 * - Recursive subgraph support with depth limiting
 * - Data and metadata inheritance from parent
 *
 * **Architecture:**
 * ```
 * Parent Graph
 *   ├─ Node A
 *   ├─ SubgraphNode (child graph)
 *   │   ├─ Child Node 1
 *   │   ├─ Child Node 2 (HITL pause)
 *   │   └─ Child Node 3
 *   └─ Node B (receives child result)
 * ```
 *
 * **Checkpoint Coordination:**
 * - Child graph's runId is namespaced: `{parentRunId}:subgraph:{childGraphId}`
 * - When child pauses (HITL), parent also pauses with child's checkpoint
 * - Resume propagates through parent → child
 *
 * **Usage via DSL:**
 * ```kotlin
 * graph("parent") {
 *     agent("start", startAgent)
 *
 *     subgraph("child-workflow") {
 *         agent("step1", agent1)
 *         human("input", "Enter value")
 *         agent("step2", agent2)
 *         output("result")
 *
 *         edge("step1", "input")
 *         edge("input", "step2")
 *         edge("step2", "result")
 *     }
 *
 *     agent("end", endAgent)
 *
 *     edge("start", "child-workflow")
 *     edge("child-workflow", "end")
 * }
 * ```
 *
 * @property id Node identifier (also used as subgraph namespace)
 * @property childGraph The child graph to execute
 * @property maxDepth Maximum nesting depth (default: 10)
 * @property preserveKeys Metadata keys to preserve across boundaries
 * @since 1.0.4
 */
class SubgraphNode(
    override val id: String,
    val childGraph: Graph,
    private val maxDepth: Int = 10,
    private val preserveKeys: Set<String> = DEFAULT_PRESERVE_KEYS
) : Node {

    companion object {
        /**
         * Default metadata keys to preserve across subgraph boundaries
         */
        val DEFAULT_PRESERVE_KEYS = setOf(
            "userId",
            "tenantId",
            "traceId",
            "spanId",
            "sessionToken",
            "correlationId",
            "isLoggedIn"
        )

        /**
         * Generate subgraph-aware runId
         */
        fun generateSubgraphRunId(parentRunId: String?, childGraphId: String): String {
            val base = parentRunId ?: "run_${System.currentTimeMillis()}"
            return "$base:subgraph:$childGraphId"
        }
    }

    /**
     * Execute child graph with context propagation
     *
     * Note: This method should not be called directly. Use runWithRunner() instead.
     * GraphRunner automatically calls runWithRunner() for SubgraphNode instances.
     */
    override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return SpiceResult.failure(
            SpiceError.executionError(
                "SubgraphNode.run() called without runner. " +
                "GraphRunner should call runWithRunner() for SubgraphNode instances.",
                nodeId = id
            )
        )
    }

    /**
     * Execute child graph with the provided runner (thread-safe)
     *
     * This method is called by GraphRunner to ensure proper runner inheritance
     * without shared mutable state.
     *
     * @param message Input message
     * @param runner The GraphRunner to use for child graph execution
     * @return SpiceResult with child graph output
     */
    suspend fun runWithRunner(message: SpiceMessage, runner: GraphRunner): SpiceResult<SpiceMessage> {
        // 1. Validate and prepare context
        val currentDepth = message.getMetadata<Int>("subgraphDepth") ?: 0

        // Check depth limit
        if (currentDepth >= maxDepth) {
            return SpiceResult.failure(
                SpiceError.executionError(
                    "Subgraph depth limit exceeded (max: $maxDepth, current: $currentDepth)",
                    graphId = childGraph.id,
                    nodeId = id
                )
            )
        }

        // 2. Prepare message for child graph execution
        val childRunId = generateSubgraphRunId(message.runId, childGraph.id)
        val parentGraphId = message.graphId

        // Build subgraph path for debugging
        val parentPath = message.getMetadata<String>("subgraphPath") ?: ""
        val newPath = if (parentPath.isEmpty()) {
            "${parentGraphId ?: "root"} -> ${childGraph.id}"
        } else {
            "$parentPath -> ${childGraph.id}"
        }

        // Prepare child message with subgraph context
        // Child graph expects READY state for fresh execution
        val childMessage = message.copy(
            // Reset graph context for child
            graphId = childGraph.id,
            nodeId = null,
            runId = childRunId,
            // Child graph should start fresh with READY state
            state = ExecutionState.READY,
            // Fresh state history for child graph execution
            stateHistory = emptyList()
        ).withMetadata(buildChildMetadata(
            parentMessage = message,
            currentDepth = currentDepth,
            parentGraphId = parentGraphId,
            newPath = newPath,
            childRunId = childRunId
        ))

        // 3. Execute child graph using the provided runner
        val result = runner.execute(childGraph, childMessage)

        // 4. Process result and restore parent context
        return when (result) {
            is SpiceResult.Success -> {
                val childOutput = result.value

                // Restore parent graph context
                val parentOutput = restoreParentContext(
                    childOutput = childOutput,
                    parentMessage = message,
                    currentDepth = currentDepth,
                    parentGraphId = parentGraphId
                )

                SpiceResult.success(parentOutput)
            }
            is SpiceResult.Failure -> {
                // Wrap error with subgraph context
                SpiceResult.failure(
                    result.error.withContext(
                        "subgraphId" to childGraph.id,
                        "subgraphDepth" to (currentDepth + 1).toString(),
                        "parentGraphId" to (parentGraphId ?: "")
                    )
                )
            }
        }
    }

    /**
     * Build metadata for child graph execution
     */
    private fun buildChildMetadata(
        parentMessage: SpiceMessage,
        currentDepth: Int,
        parentGraphId: String?,
        newPath: String,
        childRunId: String
    ): Map<String, Any> = buildMap {
        // Preserve important metadata from parent
        preserveKeys.forEach { key ->
            parentMessage.getMetadata<Any>(key)?.let { value ->
                put(key, value)
            }
        }

        // Add subgraph tracking metadata
        put("subgraphDepth", currentDepth + 1)
        put("isSubgraph", true)
        put("currentGraphId", childGraph.id)
        put("subgraphPath", newPath)
        put("subgraphEnteredAt", System.currentTimeMillis())
        put("childRunId", childRunId)

        if (parentGraphId != null) {
            put("parentGraphId", parentGraphId)
        }

        // Store parent runId for checkpoint coordination
        parentMessage.runId?.let { put("parentRunId", it) }
    }

    /**
     * Restore parent context after child execution
     *
     * Properly merges child's data and metadata back to parent:
     * - Child's data keys are directly available to next parent node
     * - Child's metadata (tool flags, custom flags) is preserved
     * - Subgraph execution stats are added for observability
     */
    private fun restoreParentContext(
        childOutput: SpiceMessage,
        parentMessage: SpiceMessage,
        currentDepth: Int,
        parentGraphId: String?
    ): SpiceMessage {
        val enteredAt = childOutput.getMetadata<Long>("subgraphEnteredAt") ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - enteredAt

        // Merge child's data directly into parent's data
        // Child keys take precedence over parent keys (child is latest execution)
        val mergedData = parentMessage.data + childOutput.data + mapOf(
            // Also provide namespaced access for explicit reference
            "subgraph_result" to childOutput.content,
            "subgraph_state" to childOutput.state.name
        )

        // Merge child's metadata into parent's metadata
        // Preserve child's tool metadata, custom flags, etc.
        // Filter out subgraph-internal tracking keys that shouldn't leak to parent
        val childMetadataToMerge = childOutput.metadata.filterKeys { key ->
            key !in setOf(
                "subgraphEnteredAt", "childRunId", "parentRunId",
                "subgraphPath", "currentGraphId", "parentGraphId",
                "subgraphDepth", "isSubgraph"
            )
        }

        // Build final metadata with proper context restoration
        val mergedMetadata = parentMessage.metadata + childMetadataToMerge + buildMap<String, Any> {
            // Restore parent context
            put("subgraphDepth", currentDepth)
            put("isSubgraph", currentDepth > 0)

            if (parentGraphId != null) {
                put("currentGraphId", parentGraphId)
            }

            // Add execution stats for observability
            put("lastSubgraphDuration", duration)
            put("lastSubgraphId", childGraph.id)
            put("lastSubgraphState", childOutput.state.name)
        }

        return childOutput.copy(
            // Restore parent graph context
            graphId = parentGraphId,
            nodeId = id,
            runId = parentMessage.runId,
            // Use merged data and metadata
            data = mergedData,
            metadata = mergedMetadata
        )
    }

}
