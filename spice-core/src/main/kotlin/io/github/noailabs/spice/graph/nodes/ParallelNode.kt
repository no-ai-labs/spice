package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.graph.merge.MergePolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Node that executes multiple branches in parallel.
 *
 * Each branch is executed concurrently, and results are collected into a map.
 * Metadata is stored in namespaced format to avoid conflicts.
 *
 * Usage:
 * ```
 * val parallelNode = ParallelNode(
 *     id = "data-processing",
 *     branches = mapOf(
 *         "fetch" to fetchNode,
 *         "validate" to validateNode,
 *         "transform" to transformNode
 *     )
 * )
 * ```
 *
 * Result structure:
 * - `data`: Map<String, Any?> with branch results
 * - `state["parallel.{id}.{branchId}"]`: Individual branch results
 * - `metadata["parallel.{id}.{branchId}.*"]`: Branch-specific metadata
 */
class ParallelNode(
    override val id: String,
    private val branches: Map<String, Node>,
    private val mergePolicy: MergePolicy = MergePolicy.Namespace,
    private val failFast: Boolean = true
) : Node {

    init {
        require(branches.isNotEmpty()) { "ParallelNode must have at least one branch" }
        require(branches.keys.all { it.isNotBlank() }) { "Branch IDs must not be blank" }
    }

    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> = coroutineScope {
        SpiceResult.catchingSuspend {
            // Execute all branches in parallel
            val branchResults = branches.map { (branchId, node) ->
                async {
                    val result = node.run(ctx)
                    branchId to result
                }
            }.awaitAll()

            // Check for failures
            val failures = branchResults.filter { (_, result) -> result is SpiceResult.Failure }
            if (failures.isNotEmpty() && failFast) {
                val firstFailure = failures.first().second as SpiceResult.Failure
                throw firstFailure.error.toException()
            }

            // Extract successful results (filter out nulls for failFast=false case)
            val successResults: Map<String, NodeResult> = branchResults.mapNotNull { (branchId, result) ->
                when (result) {
                    is SpiceResult.Success -> branchId to result.value
                    is SpiceResult.Failure -> null  // Skip failed branches
                }
            }.toMap()

            // Build result map for data field
            val resultMap = successResults.mapValues { (_, nodeResult) -> nodeResult.data }

            // Merge metadata according to policy
            val mergedMetadata = when (mergePolicy) {
                is MergePolicy.Namespace -> mergeMetadataNamespace(successResults)
                is MergePolicy.LastWrite -> mergeMetadataLastWrite(successResults)
                is MergePolicy.FirstWrite -> mergeMetadataFirstWrite(successResults)
                is MergePolicy.Custom -> mergeMetadataCustom(successResults, mergePolicy)
            }

            // Add parallel execution metadata
            val executionMetadata = mergedMetadata + mapOf(
                "parallelNodeId" to id,
                "parallelBranches" to branches.keys.toList(),
                "parallelExecutionComplete" to true,
                "parallelSuccessCount" to successResults.size,
                "parallelFailureCount" to failures.size
            )

            // Return result with namespaced state updates
            val stateUpdates = successResults.mapKeys { (branchId, _) ->
                "parallel.$id.$branchId"
            }.mapValues { (_, nodeResult) -> nodeResult.data }

            NodeResult.fromContext(
                ctx = ctx,
                data = resultMap,
                additional = executionMetadata
            )
        }.recoverWith { error ->
            SpiceResult.failure(
                SpiceError.UnknownError(
                    message = "ParallelNode '$id' execution failed: ${error.message}",
                    cause = error.cause,
                    context = mapOf(
                        "nodeId" to id,
                        "branches" to branches.keys.toList(),
                        "originalError" to error.code
                    )
                )
            )
        }
    }

    /**
     * Merge metadata using namespace strategy.
     * Each branch's metadata is stored under `parallel.{nodeId}.{branchId}.*`
     */
    private fun mergeMetadataNamespace(
        results: Map<String, NodeResult>
    ): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()

        results.forEach { (branchId, nodeResult) ->
            nodeResult.metadata.forEach { (key, value) ->
                // Skip ExecutionContext fields (they should be consistent)
                if (key !in listOf("tenantId", "userId", "correlationId", "agentId")) {
                    metadata["parallel.$id.$branchId.$key"] = value
                }
            }
        }

        return metadata
    }

    /**
     * Merge metadata using last-write-wins strategy.
     * Later branches overwrite earlier ones.
     */
    private fun mergeMetadataLastWrite(
        results: Map<String, NodeResult>
    ): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()

        results.values.forEach { nodeResult ->
            metadata.putAll(nodeResult.metadata)
        }

        return metadata
    }

    /**
     * Merge metadata using first-write-wins strategy.
     * First branch to set a key wins.
     */
    private fun mergeMetadataFirstWrite(
        results: Map<String, NodeResult>
    ): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()

        results.values.forEach { nodeResult ->
            nodeResult.metadata.forEach { (key, value) ->
                if (!metadata.containsKey(key)) {
                    metadata[key] = value
                }
            }
        }

        return metadata
    }

    /**
     * Merge metadata using custom aggregation functions.
     */
    private fun mergeMetadataCustom(
        results: Map<String, NodeResult>,
        policy: MergePolicy.Custom
    ): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()

        // Collect all keys across all results
        val allKeys = results.values.flatMap { it.metadata.keys }.toSet()

        allKeys.forEach { key ->
            val values = results.values.mapNotNull { it.metadata[key] }

            if (values.isEmpty()) return@forEach

            // Check if we have an aggregator for this key
            val aggregator = policy.aggregators[key]
            if (aggregator != null) {
                val aggregated = aggregator.aggregate(values)
                if (aggregated != null) {
                    metadata[key] = aggregated
                }
            } else {
                // Use default strategy
                when (policy.defaultStrategy) {
                    MergePolicy.DefaultStrategy.FAIL -> {
                        if (values.toSet().size > 1) {
                            throw IllegalStateException(
                                "Metadata conflict for key '$key': ${values.toSet()}. " +
                                        "Define an aggregator or change defaultStrategy."
                            )
                        }
                        metadata[key] = values.first()
                    }
                    MergePolicy.DefaultStrategy.LAST_WRITE -> {
                        metadata[key] = values.last()
                    }
                    MergePolicy.DefaultStrategy.FIRST_WRITE -> {
                        metadata[key] = values.first()
                    }
                    MergePolicy.DefaultStrategy.IGNORE -> {
                        // Skip conflicting keys
                        if (values.toSet().size == 1) {
                            metadata[key] = values.first()
                        }
                    }
                }
            }
        }

        return metadata
    }
}
