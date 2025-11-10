package io.github.noailabs.spice.graph.merge

/**
 * Policy for merging results from parallel node execution.
 * Determines how metadata and state are combined when multiple branches execute in parallel.
 */
sealed interface MergePolicy {
    /**
     * Namespace strategy: Store each branch's metadata in separate namespace.
     * Pattern: `parallel.{nodeId}.{branchId}.{key}`
     *
     * Pros: No conflicts, all data preserved
     * Cons: Nested structure, larger metadata size
     */
    object Namespace : MergePolicy

    /**
     * Last-write-wins strategy: Later branches overwrite earlier ones.
     * Simple but may lose data if branches set same keys.
     *
     * Pros: Simple, flat structure
     * Cons: Non-deterministic, data loss
     */
    object LastWrite : MergePolicy

    /**
     * First-write-wins strategy: First branch sets the value, later branches ignored.
     *
     * Pros: Deterministic with ordered branches
     * Cons: Later branches' data ignored
     */
    object FirstWrite : MergePolicy

    /**
     * Custom aggregation strategy: Use aggregation functions per key.
     * Allows fine-grained control over how specific keys are merged.
     *
     * Example:
     * ```
     * Custom(mapOf(
     *   "confidence" to AggregationFunction.AVERAGE,
     *   "results" to AggregationFunction.CONCAT_LIST
     * ))
     * ```
     */
    data class Custom(
        val aggregators: Map<String, AggregationFunction>,
        val defaultStrategy: DefaultStrategy = DefaultStrategy.FAIL
    ) : MergePolicy

    /**
     * Default strategy when no aggregator is defined for a key
     */
    enum class DefaultStrategy {
        FAIL,        // Throw error on conflict
        LAST_WRITE,  // Use last write wins
        FIRST_WRITE, // Use first write wins
        IGNORE       // Skip conflicting keys
    }
}

/**
 * Aggregation function for merging values from multiple branches.
 */
enum class AggregationFunction {
    /** Take first value */
    FIRST,

    /** Take last value */
    LAST,

    /** Calculate average (numeric values only) */
    AVERAGE,

    /** Sum all values (numeric values only) */
    SUM,

    /** Concatenate all values into a list */
    CONCAT_LIST,

    /** Select most common value (voting) */
    VOTE,

    /** Take minimum value (comparable values only) */
    MIN,

    /** Take maximum value (comparable values only) */
    MAX;

    /**
     * Apply aggregation to a list of values.
     * Returns null if aggregation cannot be performed.
     */
    fun aggregate(values: List<Any?>): Any? {
        if (values.isEmpty()) return null

        return when (this) {
            FIRST -> values.firstOrNull()
            LAST -> values.lastOrNull()

            AVERAGE -> {
                val numbers = values.filterIsInstance<Number>()
                if (numbers.isEmpty()) null
                else numbers.map { it.toDouble() }.average()
            }

            SUM -> {
                val numbers = values.filterIsInstance<Number>()
                if (numbers.isEmpty()) null
                else numbers.sumOf { it.toDouble() }
            }

            CONCAT_LIST -> values.filterNotNull()

            VOTE -> {
                values.filterNotNull()
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
            }

            MIN -> {
                val comparables = values.filterIsInstance<Comparable<Any>>()
                if (comparables.isEmpty()) null
                else comparables.minOrNull()
            }

            MAX -> {
                val comparables = values.filterIsInstance<Comparable<Any>>()
                if (comparables.isEmpty()) null
                else comparables.maxOrNull()
            }
        }
    }
}
