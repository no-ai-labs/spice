package io.github.noailabs.spice.routing.spi

import io.github.noailabs.spice.SpiceMessage

/**
 * Context information for routing decisions.
 *
 * Provides contextual data that routing engines can use to make decisions.
 * Automatically extracted from SpiceMessage or can be manually constructed.
 *
 * @since 1.0.7
 */
data class RoutingContext(
    /**
     * Current graph ID (if executing within a graph)
     */
    val graphId: String? = null,

    /**
     * Current node ID (if executing within a node)
     */
    val nodeId: String? = null,

    /**
     * Current run ID (for checkpoint/resume)
     */
    val runId: String? = null,

    /**
     * User ID (from message metadata)
     */
    val userId: String? = null,

    /**
     * Tenant ID (for multi-tenant applications)
     */
    val tenantId: String? = null,

    /**
     * Trace ID (for distributed tracing)
     */
    val traceId: String? = null,

    /**
     * Hints for routing decision (key-value pairs)
     * Can be used to pass additional context to routing engines.
     */
    val hints: Map<String, String> = emptyMap(),

    /**
     * Additional metadata (for custom extensions)
     */
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * Empty routing context (default)
         */
        val EMPTY = RoutingContext()

        /**
         * Create routing context from SpiceMessage.
         *
         * Extracts common metadata fields automatically.
         */
        fun from(message: SpiceMessage): RoutingContext {
            return RoutingContext(
                graphId = message.graphId,
                nodeId = message.nodeId,
                runId = message.runId,
                userId = message.getMetadata<String>("userId"),
                tenantId = message.getMetadata<String>("tenantId"),
                traceId = message.getMetadata<String>("traceId")
            )
        }

        /**
         * Create routing context with hints.
         */
        fun withHints(vararg hints: Pair<String, String>): RoutingContext {
            return RoutingContext(hints = hints.toMap())
        }
    }

    /**
     * Add hints to this context.
     */
    fun withHints(vararg newHints: Pair<String, String>): RoutingContext {
        return copy(hints = hints + newHints.toMap())
    }

    /**
     * Add metadata to this context.
     */
    fun withMetadata(vararg newMetadata: Pair<String, Any>): RoutingContext {
        return copy(metadata = metadata + newMetadata.toMap())
    }

    /**
     * Get hint value by key.
     */
    fun getHint(key: String): String? = hints[key]

    /**
     * Get typed metadata value.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMetadataValue(key: String): T? = metadata[key] as? T
}
