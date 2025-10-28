package io.github.noailabs.spice.graph

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.error.SpiceResult

/**
 * Core abstraction for a node in the execution graph.
 * Every node represents a unit of work that can be executed.
 *
 * Returns SpiceResult for consistent error handling across Spice framework.
 */
interface Node {
    val id: String
    suspend fun run(ctx: NodeContext): SpiceResult<NodeResult>
}

/**
 * Execution context passed to each node.
 * Contains the current state and metadata for the graph execution.
 *
 * @property agentContext Optional AgentContext for multi-tenant support and context propagation
 */
data class NodeContext(
    val graphId: String,
    val state: MutableMap<String, Any?>,
    val metadata: MutableMap<String, Any> = mutableMapOf(),
    val agentContext: AgentContext? = null  // ✨ Context propagation support!
)

/**
 * Preserve and extend metadata from this context.
 */
fun NodeContext.preserveMetadata(additional: Map<String, Any> = emptyMap()): Map<String, Any> =
    this.metadata + additional

/**
 * Result of a node execution.
 */
data class NodeResult private constructor(
    val data: Any?,
    val metadata: Map<String, Any>,
    val nextEdges: List<String> = emptyList()
) {
    init {
        // Simple size guard to prevent unbounded growth
        val approxSize = metadata.toString().length
        if (approxSize > MAX_METADATA_SIZE) {
            throw IllegalArgumentException("Metadata size $approxSize exceeds limit $MAX_METADATA_SIZE")
        }
    }

    companion object {
        const val MAX_METADATA_SIZE: Int = 10_000

        /**
         * Factory to explicitly provide metadata.
         */
        fun create(
            data: Any?,
            metadata: Map<String, Any>,
            nextEdges: List<String> = emptyList()
        ): NodeResult = NodeResult(data, metadata, nextEdges)

        /**
         * Factory to preserve metadata from context with optional additions.
         */
        fun fromContext(
            ctx: NodeContext,
            data: Any?,
            additional: Map<String, Any> = emptyMap(),
            nextEdges: List<String> = emptyList()
        ): NodeResult = NodeResult(data, ctx.metadata + additional, nextEdges)
    }
}

/**
 * Builder for NodeResult that preserves context metadata by default.
 */
sealed interface NodeResultBuilder {
    fun build(): NodeResult

    class WithContext(private val ctx: NodeContext) : NodeResultBuilder {
        private var data: Any? = null
        private val additionalMetadata: MutableMap<String, Any> = mutableMapOf()

        fun withData(value: Any?): WithContext = apply { this.data = value }

        fun addMetadata(key: String, value: Any): WithContext = apply {
            additionalMetadata[key] = value
        }

        fun addMetadata(map: Map<String, Any>): WithContext = apply {
            additionalMetadata.putAll(map)
        }

        override fun build(): NodeResult = NodeResult.fromContext(
            ctx = ctx,
            data = data,
            additional = additionalMetadata
        )
    }
}

/**
 * DSL helper bound to NodeContext for building NodeResult safely.
 */
fun NodeContext.buildNodeResult(block: NodeResultBuilder.WithContext.() -> Unit): NodeResult =
    NodeResultBuilder.WithContext(this).apply(block).build()
