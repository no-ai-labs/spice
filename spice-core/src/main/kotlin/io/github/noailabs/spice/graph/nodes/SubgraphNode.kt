package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.checkpoint.SubgraphCheckpointContext
import io.github.noailabs.spice.graph.runner.GraphRunner
import io.github.noailabs.spice.template.TemplateResolver
import io.github.noailabs.spice.template.resolveInputMapping
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Subgraph Node for Spice Framework 1.2.0
 *
 * Executes a child graph within a parent graph, managing context propagation,
 * checkpoint coordination, and data mapping.
 *
 * **Features:**
 * - Automatic context propagation (subgraphDepth, parentGraphId, subgraphPath)
 * - Checkpoint coordination with parent runId namespace
 * - Recursive subgraph support with depth limiting
 * - Data and metadata inheritance from parent
 * - **NEW (1.2.0):** inputMapping/outputMapping for explicit data transformation
 *
 * **inputMapping (Parent → Child):**
 * Maps parent context to child's initial data using template expressions.
 * Format: `childKey → template/literal`
 *
 * ```yaml
 * inputMapping:
 *   preselectedItemId: "{{data.selectedBookingId}}"  # template
 *   confirmationType: "cancel"                       # literal
 *   locale: "{{metadata.locale}}"                    # metadata access
 * ```
 *
 * **outputMapping (Child → Parent):**
 * Maps child's output data to parent's context after subgraph completes.
 * Format: `childKey → parentKey`
 *
 * ```yaml
 * outputMapping:
 *   confirmed: "user_confirm"     # child.data.confirmed → parent.data.user_confirm
 *   selectedOption: "selection"   # child.data.selectedOption → parent.data.selection
 * ```
 *
 * **Data Merge Priority (outputMapping):**
 * 1. outputMapping applied child keys (highest)
 * 2. Unmapped child keys (raw propagation)
 * 3. Parent keys (preserved unless overwritten)
 *
 * **Architecture:**
 * ```
 * Parent Graph
 *   ├─ Node A
 *   ├─ SubgraphNode (child graph)
 *   │   ├─ Child Node 1
 *   │   ├─ Child Node 2 (HITL pause)
 *   │   └─ Child Node 3
 *   └─ Node B (receives child result via outputMapping)
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
 *     subgraph(
 *         id = "confirmation",
 *         childGraph = confirmationGraph,
 *         inputMapping = mapOf(
 *             "preselectedItemId" to "{{data.selectedBookingId}}",
 *             "confirmationType" to "cancel"
 *         ),
 *         outputMapping = mapOf(
 *             "confirmed" to "user_confirm"
 *         )
 *     )
 *
 *     agent("end", endAgent)
 *
 *     edge("start", "confirmation")
 *     edge("confirmation", "end")
 * }
 * ```
 *
 * @property id Node identifier (also used as subgraph namespace)
 * @property childGraph The child graph to execute
 * @property maxDepth Maximum nesting depth (default: 10)
 * @property preserveKeys Metadata keys to preserve across boundaries
 * @property inputMapping Parent → Child data mapping (childKey → template)
 * @property outputMapping Child → Parent data mapping (childKey → parentKey)
 * @property templateResolver Resolver for template expressions in inputMapping
 * @since 1.0.5 (basic), 1.2.0 (inputMapping/outputMapping)
 */
class SubgraphNode(
    override val id: String,
    val childGraph: Graph,
    private val maxDepth: Int = 10,
    private val preserveKeys: Set<String> = DEFAULT_PRESERVE_KEYS,
    private val inputMapping: Map<String, Any> = emptyMap(),
    private val outputMapping: Map<String, String> = emptyMap(),
    private val templateResolver: TemplateResolver = TemplateResolver.DEFAULT
) : Node {

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
     * **inputMapping Processing:**
     * 1. Resolve templates against parent message using templateResolver
     * 2. Merge resolved values into child's initial data
     * 3. inputMapping values take precedence over inherited parent data
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

        // 3. Apply inputMapping (Parent → Child)
        // Resolve templates and merge into child's initial data
        val resolvedInputMapping = if (inputMapping.isNotEmpty()) {
            templateResolver.resolveInputMapping(inputMapping, message).also { resolved ->
                logger.debug {
                    "[SubgraphNode '$id'] inputMapping resolved: ${resolved.keys} " +
                    "(original templates: ${inputMapping.keys})"
                }
            }
        } else {
            emptyMap()
        }

        // Child data = parent.data + resolvedInputMapping (inputMapping takes precedence)
        val childData = message.data + resolvedInputMapping

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
            stateHistory = emptyList(),
            // Apply inputMapping-resolved data
            data = childData
        ).withMetadata(buildChildMetadata(
            parentMessage = message,
            currentDepth = currentDepth,
            parentGraphId = parentGraphId,
            newPath = newPath,
            childRunId = childRunId
        ))

        // 4. Execute child graph using the provided runner
        val result = runner.execute(childGraph, childMessage)

        // 5. Process result and restore parent context
        return when (result) {
            is SpiceResult.Success -> {
                val childOutput = result.value

                // Restore parent graph context with outputMapping applied
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
     * **WAITING State (HITL inside subgraph) - Spice 1.3.0:**
     * When child is WAITING, we attach SubgraphCheckpointContext to metadata
     * so that GraphRunner.resume() can re-enter the subgraph and apply
     * outputMapping after the subgraph completes.
     *
     * **outputMapping Processing (Child → Parent):**
     * 1. Apply outputMapping: `childKey → parentKey` renames
     * 2. Merge unmapped child keys (raw propagation)
     * 3. Parent keys are preserved unless overwritten
     *
     * **Data Merge Priority:**
     * 1. outputMapping applied child keys (highest)
     * 2. Unmapped child keys (raw propagation)
     * 3. Parent keys (preserved unless overwritten)
     *
     * **Metadata Handling:**
     * - Filter out subgraph-internal tracking keys
     * - Child metadata takes precedence over parent (tool flags, custom flags)
     * - Subgraph execution stats are added for observability
     */
    private fun restoreParentContext(
        childOutput: SpiceMessage,
        parentMessage: SpiceMessage,
        currentDepth: Int,
        parentGraphId: String?
    ): SpiceMessage {
        // ===============================================================
        // Spice 1.3.0: Handle WAITING state (HITL inside subgraph)
        // ===============================================================
        if (childOutput.state == ExecutionState.WAITING) {
            return handleWaitingState(
                childOutput = childOutput,
                parentMessage = parentMessage,
                currentDepth = currentDepth,
                parentGraphId = parentGraphId
            )
        }

        // ===============================================================
        // Normal execution: child completed (COMPLETED/FAILED)
        // ===============================================================
        val enteredAt = childOutput.getMetadata<Long>("subgraphEnteredAt") ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - enteredAt

        // Apply outputMapping (Child → Parent)
        // Format: childKey → parentKey
        val childDataMapped: Map<String, Any> = applyOutputMapping(childOutput)

        // Merge data: parent.data + childDataMapped (child takes precedence)
        val mergedData = parentMessage.data + childDataMapped + mapOf(
            // Also provide namespaced access for explicit reference
            "subgraph_result" to childOutput.content,
            "subgraph_state" to childOutput.state.name
        )

        // Merge child's metadata into parent's metadata
        // Preserve child's tool metadata, custom flags, etc.
        // Filter out subgraph-internal tracking keys that shouldn't leak to parent
        val childMetadataToMerge = childOutput.metadata.filterKeys { key ->
            key !in SUBGRAPH_INTERNAL_KEYS
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

    /**
     * Handle WAITING state - HITL occurred inside the subgraph
     *
     * **Spice 1.3.0 Subgraph HITL Support:**
     * When a child graph returns WAITING (e.g., HITL tool requesting user input),
     * we need to:
     * 1. Create SubgraphCheckpointContext with all info needed for resume
     * 2. Build/extend the subgraph stack (for nested subgraphs)
     * 3. Attach the stack to metadata so Checkpoint.fromMessage() can extract it
     * 4. Return WAITING state with parent graph context restored
     *
     * On resume, GraphRunner will:
     * 1. Detect subgraphStack in checkpoint
     * 2. Re-enter this subgraph and resume the child graph
     * 3. When child completes, apply outputMapping
     * 4. Continue parent execution
     */
    private fun handleWaitingState(
        childOutput: SpiceMessage,
        parentMessage: SpiceMessage,
        currentDepth: Int,
        parentGraphId: String?
    ): SpiceMessage {
        logger.debug {
            "[SubgraphNode '$id'] Child graph '${childGraph.id}' is WAITING (HITL). " +
            "Attaching SubgraphCheckpointContext for resume. " +
            "childNodeId=${childOutput.nodeId}, depth=${currentDepth + 1}"
        }

        // Create context for this subgraph level
        val subgraphContext = SubgraphCheckpointContext(
            parentNodeId = id,
            parentGraphId = parentGraphId ?: "unknown",
            parentRunId = parentMessage.runId ?: "unknown",
            childGraphId = childGraph.id,
            childNodeId = childOutput.nodeId ?: "unknown",
            childRunId = childOutput.runId ?: "unknown",
            outputMapping = outputMapping,
            depth = currentDepth + 1
        )

        // Build subgraph stack (for nested subgraphs)
        // If child already has a stack (nested subgraph HITL), extend it
        @Suppress("UNCHECKED_CAST")
        val existingStack = childOutput.metadata[SubgraphCheckpointContext.STACK_METADATA_KEY] as? List<SubgraphCheckpointContext>
            ?: emptyList()

        // Stack order: [outermost, ..., innermost]
        // We prepend our context since we're the outer layer
        val newStack = listOf(subgraphContext) + existingStack

        // Preserve child's metadata (tool calls, HITL metadata, etc.)
        // but filter out subgraph-internal keys
        val childMetadataToMerge = childOutput.metadata.filterKeys { key ->
            key !in SUBGRAPH_INTERNAL_KEYS &&
            key != SubgraphCheckpointContext.METADATA_KEY &&
            key != SubgraphCheckpointContext.STACK_METADATA_KEY
        }

        // Build final metadata with subgraph stack attached
        val waitingMetadata = parentMessage.metadata + childMetadataToMerge + mapOf(
            // Attach subgraph stack for Checkpoint.fromMessage()
            SubgraphCheckpointContext.STACK_METADATA_KEY to newStack,
            // Keep tracking info for debugging
            "subgraphDepth" to (currentDepth + 1),
            "isSubgraph" to true,
            "currentGraphId" to childGraph.id,
            "waitingInSubgraph" to true,
            "waitingSubgraphId" to childGraph.id,
            "waitingChildNodeId" to (childOutput.nodeId ?: "unknown")
        )

        // Return WAITING message with:
        // - Parent graph context (graphId, nodeId, runId) for checkpoint storage
        // - Child's data and tool calls preserved
        // - Subgraph stack in metadata for resume
        return childOutput.copy(
            // Use PARENT graph context for checkpoint
            // (checkpoint is stored at parent level, resume enters from parent)
            graphId = parentGraphId,
            nodeId = id,  // This SubgraphNode's ID
            runId = parentMessage.runId,
            // Keep WAITING state
            state = ExecutionState.WAITING,
            // Preserve child's data (includes HITL prompt data)
            // Merge with parent data for context availability
            data = parentMessage.data + childOutput.data,
            // Attach subgraph stack
            metadata = waitingMetadata
        )
    }

    /**
     * Apply outputMapping to child output data
     *
     * @return Map with child keys renamed according to outputMapping
     */
    private fun applyOutputMapping(childOutput: SpiceMessage): Map<String, Any> {
        if (outputMapping.isEmpty()) {
            return childOutput.data
        }

        val mapped = mutableMapOf<String, Any>()
        val mappedChildKeys = mutableSetOf<String>()

        // Apply outputMapping: rename childKey to parentKey
        outputMapping.forEach { (childKey, parentKey) ->
            val childValue = childOutput.data[childKey]
            if (childValue != null) {
                mapped[parentKey] = childValue
                mappedChildKeys.add(childKey)
                logger.debug {
                    "[SubgraphNode '$id'] outputMapping: child.$childKey → parent.$parentKey = $childValue"
                }
            } else {
                logger.debug {
                    "[SubgraphNode '$id'] outputMapping: child.$childKey not found (skipped)"
                }
            }
        }

        // Include unmapped child keys (raw propagation)
        childOutput.data.forEach { (key, value) ->
            if (key !in mappedChildKeys && key !in mapped) {
                mapped[key] = value
            }
        }

        return mapped
    }

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
         * Subgraph-internal metadata keys that should be filtered out
         * when merging metadata back to parent
         */
        private val SUBGRAPH_INTERNAL_KEYS = setOf(
            "subgraphEnteredAt",
            "childRunId",
            "parentRunId",
            "subgraphPath",
            "currentGraphId",
            "parentGraphId",
            "subgraphDepth",
            "isSubgraph",
            "waitingInSubgraph",
            "waitingSubgraphId",
            "waitingChildNodeId"
        )

        /**
         * Generate subgraph-aware runId
         */
        fun generateSubgraphRunId(parentRunId: String?, childGraphId: String): String {
            val base = parentRunId ?: "run_${System.currentTimeMillis()}"
            return "$base:subgraph:$childGraphId"
        }
    }
}
