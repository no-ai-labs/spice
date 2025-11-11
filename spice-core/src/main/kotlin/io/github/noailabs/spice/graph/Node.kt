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
 * Contains the current state and execution context for the graph.
 *
 * State is immutable - use withState to modify.
 * Implementation uses immutable collections internally but exposes standard Map interface.
 */
data class NodeContext(
    val graphId: String,
    val state: Map<String, Any?>,  // ✅ Generic Map interface - no dependency leakage
    val context: ExecutionContext
) {
    companion object {
        fun create(
            graphId: String,
            state: Map<String, Any?>,
            context: ExecutionContext
        ): NodeContext = NodeContext(
            graphId = graphId,
            state = state.toMap(),  // Defensive copy
            context = context
        )
    }

    fun withState(key: String, value: Any?): NodeContext =
        copy(state = state + (key to value))

    fun withState(updates: Map<String, Any?>): NodeContext =
        copy(state = state + updates)

    fun withContext(newContext: ExecutionContext): NodeContext =
        copy(context = newContext)
}

/**
 * Preserve and extend metadata from this context.
 */
fun NodeContext.preserveMetadata(additional: Map<String, Any> = emptyMap()): Map<String, Any> =
    this.context.toMap() + additional

/**
 * Result of a node execution.
 */
@ConsistentCopyVisibility
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
            additional: Map<String, Any?> = emptyMap(),
            nextEdges: List<String> = emptyList()
        ): NodeResult {
            @Suppress("UNCHECKED_CAST")
            val mergedMetadata = (ctx.context.toMap() + additional).filterValues { it != null } as Map<String, Any>
            return NodeResult(data, mergedMetadata, nextEdges)
        }

        /**
         * Factory specifically for creating NodeResult from HumanResponse during checkpoint resume.
         * Automatically propagates HumanResponse.metadata to ensure it's available in ExecutionContext
         * for subsequent nodes (especially AgentNode).
         *
         * This is the recommended way to convert HumanResponse to NodeResult in resume flows.
         *
         * @param ctx NodeContext that should already have HumanResponse.metadata merged into its context
         * @param response The HumanResponse to convert
         * @return NodeResult with HumanResponse data and metadata properly propagated
         */
        fun fromHumanResponse(
            ctx: NodeContext,
            response: io.github.noailabs.spice.graph.nodes.HumanResponse
        ): NodeResult {
            // Convert HumanResponse.metadata (Map<String, String>) to Map<String, Any>
            val humanMetadata = response.metadata.mapValues { it.value as Any }

            return fromContext(
                ctx = ctx,
                data = response,
                additional = humanMetadata
            )
        }
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
