package io.github.noailabs.spice.graph

import io.github.noailabs.spice.Identifiable
import io.github.noailabs.spice.graph.middleware.Middleware

/**
 * Represents a directed graph of nodes.
 * By default enforces acyclic (DAG) structure, but can allow cycles when needed.
 * Implements Identifiable to allow graph registration in GraphRegistry.
 */
data class Graph(
    override val id: String,
    val nodes: Map<String, Node>,
    val edges: List<Edge>,
    val entryPoint: String,
    val middleware: List<Middleware> = emptyList(),
    val allowCycles: Boolean = false
) : Identifiable

/**
 * Represents a directed edge between two nodes.
 *
 * @property from Source node ID (use "*" for wildcard matching all nodes)
 * @property to Target node ID
 * @property condition Predicate to evaluate if this edge should be followed
 * @property priority Lower values are evaluated first (default: 0)
 * @property isFallback If true, this edge is only used when no regular edges match
 * @property name Optional name for debugging and logging
 */
data class Edge(
    val from: String,
    val to: String,
    val priority: Int = 0,
    val isFallback: Boolean = false,
    val name: String? = null,
    val condition: (NodeResult) -> Boolean = { true }
)

/**
 * Builder for creating edges with multiple conditions.
 * Supports OR and AND composition of conditions, with metadata helpers.
 */
class EdgeGroup(
    val from: String,
    val to: String,
    val priority: Int = 0,
    private var name: String? = null
) {
    private val conditions = mutableListOf<(NodeResult) -> Boolean>()
    private var combineMode: CombineMode? = null  // Null until first orWhen/andWhen

    enum class CombineMode { OR, AND }

    /**
     * Add a condition. This is the first condition and doesn't set combine mode.
     * Use andWhen/orWhen for subsequent conditions.
     */
    fun where(condition: (NodeResult) -> Boolean): EdgeGroup {
        conditions.add(condition)
        return this
    }

    /**
     * Add an OR condition (any condition must match)
     */
    fun orWhen(condition: (NodeResult) -> Boolean): EdgeGroup {
        if (combineMode == null) {
            combineMode = CombineMode.OR
        } else if (combineMode == CombineMode.AND) {
            throw IllegalStateException("Cannot mix AND and OR conditions in same EdgeGroup")
        }
        conditions.add(condition)
        return this
    }

    /**
     * Add an AND condition (all conditions must match)
     */
    fun andWhen(condition: (NodeResult) -> Boolean): EdgeGroup {
        if (combineMode == null) {
            combineMode = CombineMode.AND
        } else if (combineMode == CombineMode.OR) {
            throw IllegalStateException("Cannot mix OR and AND conditions in same EdgeGroup")
        }
        conditions.add(condition)
        return this
    }

    /**
     * Metadata helper: Check if metadata key equals specific value
     */
    fun whenMetadata(key: String, equals: Any?): EdgeGroup {
        conditions.add { result -> result.metadata[key] == equals }
        return this
    }

    /**
     * Metadata helper: Check if metadata key is not null (or check null)
     */
    fun whenMetadataNotNull(key: String): EdgeGroup {
        conditions.add { result -> result.metadata[key] != null }
        return this
    }

    /**
     * Metadata helper: Check if metadata string contains substring
     */
    fun whenMetadataContains(key: String, substring: String): EdgeGroup {
        conditions.add { result ->
            result.metadata[key]?.toString()?.contains(substring) == true
        }
        return this
    }

    /**
     * Metadata helper: OR variant
     */
    fun orWhenMetadata(key: String, equals: Any?): EdgeGroup {
        if (combineMode == null) {
            combineMode = CombineMode.OR
        } else if (combineMode == CombineMode.AND) {
            throw IllegalStateException("Cannot mix AND and OR conditions in same EdgeGroup")
        }
        conditions.add { result -> result.metadata[key] == equals }
        return this
    }

    /**
     * Metadata helper: AND variant
     */
    fun andWhenMetadata(key: String, equals: Any?): EdgeGroup {
        if (combineMode == null) {
            combineMode = CombineMode.AND
        } else if (combineMode == CombineMode.OR) {
            throw IllegalStateException("Cannot mix OR and AND conditions in same EdgeGroup")
        }
        conditions.add { result -> result.metadata[key] == equals }
        return this
    }

    /**
     * Set edge name for debugging
     */
    fun named(name: String): EdgeGroup {
        this.name = name
        return this
    }

    /**
     * Convert to Edge with combined condition
     */
    fun toEdge(): Edge {
        val combinedCondition: (NodeResult) -> Boolean = when {
            conditions.isEmpty() -> { _ -> true }
            conditions.size == 1 -> conditions[0]  // Single condition, no combine needed
            combineMode == CombineMode.OR || combineMode == null -> { result ->
                conditions.any { it(result) }  // Default to OR if mode not set
            }
            else -> { result ->
                conditions.all { it(result) }  // AND mode
            }
        }

        return Edge(
            from = from,
            to = to,
            priority = priority,
            isFallback = false,
            name = name,
            condition = combinedCondition
        )
    }
}
