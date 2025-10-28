package io.github.noailabs.spice.graph

import io.github.noailabs.spice.ExecutionContext
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
    val context: ExecutionContext
)

/**
 * Preserve and extend metadata from this context.
 */
fun NodeContext.preserveMetadata(additional: Map<String, Any> = emptyMap()): Map<String, Any> =
    this.context.toMap() + additional

/**
 * Result of a node execution.
 */
data class NodeResult private constructor(
    val data: Any?,
    val metadata: Map<String, Any>,
    val nextEdges: List<String> = emptyList()
) {
    init {
        // Soft size policy (warning by default). Hard limit is opt-in via policy.
        val approxSize = metadata.toString().length
        val hard = HARD_LIMIT
        if (hard != null && approxSize > hard) {
            when (onOverflow) {
                OverflowPolicy.FAIL -> throw IllegalArgumentException("Metadata size $approxSize exceeds hard limit $hard")
                OverflowPolicy.WARN -> println("[Spice] Metadata size $approxSize exceeds hard limit $hard (policy=WARN)")
                OverflowPolicy.IGNORE -> {}
            }
        } else if (approxSize > METADATA_WARN_THRESHOLD && onOverflow == OverflowPolicy.WARN) {
            println("[Spice] Metadata size $approxSize exceeds warn threshold $METADATA_WARN_THRESHOLD")
        }
    }

    companion object {
        // Default: warning at 5KB; no hard limit unless configured
        const val METADATA_WARN_THRESHOLD: Int = 5_000
        var HARD_LIMIT: Int? = null

        enum class OverflowPolicy { WARN, FAIL, IGNORE }
        var onOverflow: OverflowPolicy = OverflowPolicy.WARN

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
        ): NodeResult = NodeResult(data, ctx.context.toMap() + additional, nextEdges)
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
