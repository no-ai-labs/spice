package io.github.noailabs.spice

import io.github.noailabs.spice.error.SpiceResult
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 🛠️ Core Tool interface for Spice Framework 1.0.0
 */
interface Tool {
    val name: String
    val description: String

    suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult>
}

/**
 * Authentication context for tool execution
 *
 * @property userId User identifier
 * @property tenantId Tenant identifier for multi-tenancy
 * @property sessionToken Optional session token
 * @property isLoggedIn Whether user is authenticated
 */
@Serializable
data class AuthContext(
    val userId: String? = null,
    val tenantId: String? = null,
    val sessionToken: String? = null,
    val isLoggedIn: Boolean = false
) {
    /**
     * Get userId or throw if not present
     * @throws IllegalStateException if userId is null
     */
    fun requireUserId(): String = userId
        ?: throw IllegalStateException("userId is required but not present in AuthContext")

    /**
     * Get tenantId or throw if not present
     * @throws IllegalStateException if tenantId is null
     */
    fun requireTenantId(): String = tenantId
        ?: throw IllegalStateException("tenantId is required but not present in AuthContext")
}

/**
 * Distributed tracing context for tool execution
 *
 * @property traceId Unique identifier for the entire trace
 * @property spanId Identifier for the current span
 * @property parentSpanId Identifier for the parent span
 */
@Serializable
data class TracingContext(
    val traceId: String? = null,
    val spanId: String? = null,
    val parentSpanId: String? = null
) {
    /**
     * Check if tracing is enabled (has traceId)
     */
    fun isEnabled(): Boolean = traceId != null

    /**
     * Get traceId or throw if not present
     * @throws IllegalStateException if traceId is null
     */
    fun requireTraceId(): String = traceId
        ?: throw IllegalStateException("traceId is required but not present in TracingContext")
}

/**
 * Graph execution context for tool execution
 *
 * @property graphId Identifier of the executing graph
 * @property runId Unique run identifier (used for checkpoints)
 * @property nodeId Current node identifier
 * @property subgraphDepth Nesting depth for subgraph execution
 */
@Serializable
data class GraphContext(
    val graphId: String? = null,
    val runId: String? = null,
    val nodeId: String? = null,
    val subgraphDepth: Int = 0
) {
    /**
     * Check if this is a subgraph execution
     */
    fun isSubgraph(): Boolean = subgraphDepth > 0

    /**
     * Get graphId or throw if not present
     * @throws IllegalStateException if graphId is null
     */
    fun requireGraphId(): String = graphId
        ?: throw IllegalStateException("graphId is required but not present in GraphContext")

    /**
     * Get runId or throw if not present
     * @throws IllegalStateException if runId is null
     */
    fun requireRunId(): String = runId
        ?: throw IllegalStateException("runId is required but not present in GraphContext")
}

/**
 * Context provided to tools during execution
 *
 * Contains structured sections for authentication, tracing, and graph context.
 * Tools receive only curated context, not the full runtime state.
 *
 * **Migration from 0.x:**
 * - Old: `context.userId`
 * - New: `context.auth.userId` (or `context.auth.requireUserId()`)
 *
 * @property agentId Identifier of the agent executing the tool
 * @property correlationId Request correlation ID for distributed tracing
 * @property auth Authentication context (userId, tenantId, sessionToken, isLoggedIn)
 * @property tracing Distributed tracing context (traceId, spanId, parentSpanId)
 * @property graph Graph execution context (graphId, runId, nodeId, subgraphDepth)
 * @property metadata Additional custom metadata (filtered, no internal runtime state)
 */
@Serializable
data class ToolContext(
    val agentId: String,
    val correlationId: String? = null,
    val auth: AuthContext = AuthContext(),
    val tracing: TracingContext = TracingContext(),
    val graph: GraphContext = GraphContext(),
    val metadata: Map<String, @Contextual Any> = emptyMap(),

    // Deprecated fields for backward compatibility - will be removed in 2.0.0
    @Deprecated(
        message = "Use auth.userId instead. Will be removed in version 2.0.0",
        replaceWith = ReplaceWith("auth.userId"),
        level = DeprecationLevel.WARNING
    )
    val userId: String? = null,
    @Deprecated(
        message = "Use auth.tenantId instead. Will be removed in version 2.0.0",
        replaceWith = ReplaceWith("auth.tenantId"),
        level = DeprecationLevel.WARNING
    )
    val tenantId: String? = null
) {
    companion object {
        /**
         * Create ToolContext from SpiceMessage
         *
         * Extracts only curated context from message metadata.
         * Internal runtime state is not exposed to tools.
         *
         * @param message The SpiceMessage to extract context from
         * @param agentId The agent ID executing the tool
         * @return ToolContext with structured sections populated
         */
        fun from(message: SpiceMessage, agentId: String): ToolContext {
            // Filter metadata to exclude internal runtime fields
            val filteredMetadata = message.metadata
                .filterKeys { key ->
                    // Exclude internal runtime state keys
                    key !in INTERNAL_METADATA_KEYS
                }
                .filterValues { it != null }
                .mapValues { it.value!! }

            return ToolContext(
                agentId = agentId,
                correlationId = message.correlationId,
                auth = AuthContext(
                    userId = message.getMetadata("userId"),
                    tenantId = message.getMetadata("tenantId"),
                    sessionToken = message.getMetadata("sessionToken"),
                    isLoggedIn = message.getMetadata("isLoggedIn") ?: false
                ),
                tracing = TracingContext(
                    traceId = message.getMetadata("traceId"),
                    spanId = message.getMetadata("spanId"),
                    parentSpanId = message.getMetadata("parentSpanId")
                ),
                graph = GraphContext(
                    graphId = message.graphId,
                    runId = message.runId,
                    nodeId = message.nodeId,
                    subgraphDepth = message.getMetadata("subgraphDepth") ?: 0
                ),
                metadata = filteredMetadata,
                // Deprecated fields for backward compatibility
                userId = message.getMetadata("userId"),
                tenantId = message.getMetadata("tenantId")
            )
        }

        // Internal metadata keys that should not be exposed to tools
        private val INTERNAL_METADATA_KEYS = setOf(
            // Auth context fields
            "userId", "tenantId", "sessionToken", "isLoggedIn",
            // Tracing context fields
            "traceId", "spanId", "parentSpanId",
            // Graph context fields (may be duplicated in metadata by transformers)
            "graphId", "runId", "nodeId",
            "subgraphDepth", "isSubgraph", "currentGraphId", "parentGraphId",
            "subgraphPath", "subgraphEnteredAt",
            // Runtime state fields
            "authCheckedAt", "spanOperation", "lastNodeDuration",
            "totalDuration", "traceCompleted"
        )
    }

    /**
     * Get a value from metadata
     */
    inline fun <reified T> getMetadata(key: String): T? {
        return metadata[key] as? T
    }

    /**
     * Check if metadata contains a key
     */
    fun hasMetadata(key: String): Boolean = metadata.containsKey(key)
}

@Serializable
data class ToolResult(
    val result: @Contextual Any?,
    val success: Boolean = true,
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    companion object {
        /**
         * Create a successful tool result
         *
         * @param result Result value
         * @param metadata Optional metadata
         * @return ToolResult with success = true
         */
        fun success(result: Any?, metadata: Map<String, Any> = emptyMap()): ToolResult {
            return ToolResult(result = result, success = true, metadata = metadata)
        }

        /**
         * Create a failed tool result
         *
         * @param error Error message or value
         * @param metadata Optional metadata
         * @return ToolResult with success = false
         */
        fun error(error: Any?, metadata: Map<String, Any> = emptyMap()): ToolResult {
            return ToolResult(result = error, success = false, metadata = metadata)
        }
    }
}
