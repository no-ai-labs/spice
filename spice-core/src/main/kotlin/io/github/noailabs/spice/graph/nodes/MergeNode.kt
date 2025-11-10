package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult

/**
 * Node that merges results from a ParallelNode execution.
 *
 * Collects results stored by ParallelNode in state (under `parallel.{nodeId}.{branchId}` keys)
 * and applies a user-defined merge function to combine them into a single result.
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
 *
 * val mergeNode = MergeNode(
 *     id = "aggregate",
 *     parallelNodeId = "data-processing"
 * ) { results ->
 *     mapOf(
 *         "fetchedData" to results["fetch"],
 *         "isValid" to results["validate"],
 *         "transformed" to results["transform"]
 *     )
 * }
 * ```
 */
class MergeNode(
    override val id: String,
    private val parallelNodeId: String,
    private val merger: (Map<String, Any?>) -> Any?
) : Node {

    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        return SpiceResult.catching {
            // Collect results from parallel execution
            val parallelResults = collectParallelResults(ctx)

            if (parallelResults.isEmpty()) {
                throw IllegalStateException(
                    "MergeNode '$id': No results found for ParallelNode '$parallelNodeId'. " +
                            "Ensure MergeNode is placed after ParallelNode in the graph."
                )
            }

            // Apply user-defined merge logic
            val merged = merger(parallelResults)

            // Return merged result
            NodeResult.fromContext(
                ctx = ctx,
                data = merged,
                additional = mapOf(
                    "mergeNodeId" to id,
                    "parallelNodeId" to parallelNodeId,
                    "mergedBranches" to parallelResults.keys.toList(),
                    "branchCount" to parallelResults.size
                )
            )
        }
    }

    /**
     * Collect results from ParallelNode stored in state.
     * ParallelNode stores its results as a Map in state[parallelNodeId].
     */
    private fun collectParallelResults(ctx: NodeContext): Map<String, Any?> {
        // ParallelNode stores results as Map<String, Any?> in state[parallelNodeId]
        val parallelData = ctx.state[parallelNodeId]

        return when (parallelData) {
            is Map<*, *> -> parallelData.mapKeys { it.key.toString() }
            else -> emptyMap()
        }
    }
}

/**
 * Common merge strategies as helper functions.
 */
object MergeStrategies {
    /**
     * Take the first non-null result.
     */
    val first: (Map<String, Any?>) -> Any? = { results ->
        results.values.firstOrNull { it != null }
    }

    /**
     * Take the last non-null result.
     */
    val last: (Map<String, Any?>) -> Any? = { results ->
        results.values.lastOrNull { it != null }
    }

    /**
     * Concatenate all results into a list.
     */
    val concatList: (Map<String, Any?>) -> Any? = { results ->
        results.values.filterNotNull()
    }

    /**
     * Concatenate all string results.
     */
    val concatStrings: (Map<String, Any?>) -> Any? = { results ->
        results.values
            .filterNotNull()
            .joinToString("\n")
    }

    /**
     * Return all results as a map (no merging).
     */
    val asMap: (Map<String, Any?>) -> Any? = { results ->
        results
    }

    /**
     * Count non-null results.
     */
    val count: (Map<String, Any?>) -> Any? = { results ->
        results.values.filterNotNull().size
    }

    /**
     * Vote: select the most common result.
     */
    val vote: (Map<String, Any?>) -> Any? = { results ->
        results.values
            .filterNotNull()
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    /**
     * Average numeric results.
     */
    val average: (Map<String, Any?>) -> Any? = { results ->
        val numbers = results.values.filterIsInstance<Number>()
        if (numbers.isEmpty()) null
        else numbers.map { it.toDouble() }.average()
    }

    /**
     * Sum numeric results.
     */
    val sum: (Map<String, Any?>) -> Any? = { results ->
        val numbers = results.values.filterIsInstance<Number>()
        if (numbers.isEmpty()) null
        else numbers.sumOf { it.toDouble() }
    }

    /**
     * Take minimum numeric result.
     */
    val min: (Map<String, Any?>) -> Any? = { results ->
        results.values
            .filterIsInstance<Number>()
            .minOfOrNull { it.toDouble() }
    }

    /**
     * Take maximum numeric result.
     */
    val max: (Map<String, Any?>) -> Any? = { results ->
        results.values
            .filterIsInstance<Number>()
            .maxOfOrNull { it.toDouble() }
    }

    /**
     * Custom merge with field-specific strategies.
     */
    fun custom(
        fieldStrategies: Map<String, (List<Any?>) -> Any?>
    ): (Map<String, Any?>) -> Any? = { results ->
        // Assuming results are maps, merge field by field
        val allMaps = results.values
            .filterIsInstance<Map<*, *>>()
            .map { it.mapKeys { (k, _) -> k.toString() } }

        if (allMaps.isEmpty()) {
            results // Can't merge, return as-is
        } else {
            val allKeys = allMaps.flatMap { it.keys }.toSet()

            allKeys.associateWith { key ->
                val values = allMaps.mapNotNull { it[key] }
                val strategy = fieldStrategies[key]

                if (strategy != null && values.isNotEmpty()) {
                    strategy(values)
                } else {
                    values.lastOrNull() // Default: last write wins
                }
            }
        }
    }
}
