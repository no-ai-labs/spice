package io.github.noailabs.spice.extensions.sparql

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.context.ContextExtension

/**
 * 🎯 Named Graphs Context Extension
 *
 * Automatically enriches AgentContext with Named Graphs URIs for SPARQL queries.
 * Supports multi-tenant RDF data isolation and context-aware graph selection.
 *
 * Example:
 * ```kotlin
 * // Register extension
 * val extension = NamedGraphsExtension { context ->
 *     listOf(
 *         "http://example.com/graphs/${context.tenantId}",
 *         "http://example.com/graphs/shared"
 *     )
 * }
 * ContextExtensionRegistry.register(extension)
 *
 * // Context will automatically have named graphs
 * withAgentContext("tenantId" to "CHIC") {
 *     val graphs = currentNamedGraphs()
 *     // ["http://example.com/graphs/CHIC", "http://example.com/graphs/shared"]
 * }
 * ```
 *
 * @since 0.4.1
 */
class NamedGraphsExtension(
    private val graphProvider: suspend (AgentContext) -> List<String>
) : ContextExtension {

    override val key = "named-graphs"

    override suspend fun enrich(context: AgentContext): AgentContext {
        return try {
            val graphs = graphProvider(context)
            context.with(NAMED_GRAPHS_KEY, graphs)
        } catch (e: Exception) {
            println("⚠️ Failed to enrich named graphs: ${e.message}")
            context
        }
    }

    companion object {
        const val NAMED_GRAPHS_KEY = "sparql_named_graphs"
    }
}

/**
 * Get Named Graphs from AgentContext
 */
fun AgentContext.getNamedGraphs(): List<String>? {
    return this.get(NamedGraphsExtension.NAMED_GRAPHS_KEY) as? List<String>
}

/**
 * Get current Named Graphs from coroutine context
 */
suspend fun currentNamedGraphs(): List<String>? {
    val context = kotlin.coroutines.coroutineContext[AgentContext]
    return context?.getNamedGraphs()
}

/**
 * Named Graphs URI Builder
 *
 * Provides common patterns for building Named Graph URIs
 */
object NamedGraphsBuilder {

    /**
     * Build tenant-scoped graph URI
     *
     * Example: `http://example.com/graphs/tenant/CHIC`
     */
    fun tenantGraph(baseUri: String, tenantId: String): String {
        return "$baseUri/tenant/$tenantId"
    }

    /**
     * Build user-scoped graph URI
     *
     * Example: `http://example.com/graphs/user/user-123`
     */
    fun userGraph(baseUri: String, userId: String): String {
        return "$baseUri/user/$userId"
    }

    /**
     * Build session-scoped graph URI
     *
     * Example: `http://example.com/graphs/session/session-456`
     */
    fun sessionGraph(baseUri: String, sessionId: String): String {
        return "$baseUri/session/$sessionId"
    }

    /**
     * Build type-scoped graph URI
     *
     * Example: `http://example.com/graphs/type/policy`
     */
    fun typeGraph(baseUri: String, type: String): String {
        return "$baseUri/type/$type"
    }

    /**
     * Build shared/public graph URI
     *
     * Example: `http://example.com/graphs/shared`
     */
    fun sharedGraph(baseUri: String): String {
        return "$baseUri/shared"
    }

    /**
     * Build composite graph URI with multiple segments
     *
     * Example: `http://example.com/graphs/tenant/CHIC/policy`
     */
    fun compositeGraph(baseUri: String, vararg segments: String): String {
        return segments.fold(baseUri) { acc, segment -> "$acc/$segment" }
    }
}

/**
 * Pre-built Named Graphs strategies
 */
object NamedGraphsStrategies {

    /**
     * Tenant-only isolation
     *
     * Each tenant has its own graph
     */
    fun tenantOnly(baseUri: String): suspend (AgentContext) -> List<String> = { context ->
        val tenantId = context.tenantId
        if (tenantId != null) {
            listOf(NamedGraphsBuilder.tenantGraph(baseUri, tenantId))
        } else {
            emptyList()
        }
    }

    /**
     * Tenant + Shared
     *
     * Tenant-specific graph + shared graph
     */
    fun tenantWithShared(baseUri: String): suspend (AgentContext) -> List<String> = { context ->
        val tenantId = context.tenantId
        if (tenantId != null) {
            listOf(
                NamedGraphsBuilder.tenantGraph(baseUri, tenantId),
                NamedGraphsBuilder.sharedGraph(baseUri)
            )
        } else {
            listOf(NamedGraphsBuilder.sharedGraph(baseUri))
        }
    }

    /**
     * Multi-level hierarchy: Tenant + User + Shared
     */
    fun hierarchical(baseUri: String): suspend (AgentContext) -> List<String> = { context ->
        buildList {
            // User-specific graph (most specific)
            context.userId?.let { userId ->
                add(NamedGraphsBuilder.userGraph(baseUri, userId))
            }

            // Tenant-specific graph
            context.tenantId?.let { tenantId ->
                add(NamedGraphsBuilder.tenantGraph(baseUri, tenantId))
            }

            // Shared graph (least specific)
            add(NamedGraphsBuilder.sharedGraph(baseUri))
        }
    }

    /**
     * Type-based partitioning
     *
     * Different graphs for different data types within a tenant
     */
    fun typePartitioned(
        baseUri: String,
        types: List<String>
    ): suspend (AgentContext) -> List<String> = { context ->
        val tenantId = context.tenantId
        if (tenantId != null) {
            types.map { type ->
                NamedGraphsBuilder.compositeGraph(baseUri, "tenant", tenantId, type)
            }
        } else {
            emptyList()
        }
    }

    /**
     * Session-based graphs
     *
     * Useful for temporary/session-specific data
     */
    fun sessionBased(baseUri: String): suspend (AgentContext) -> List<String> = { context ->
        buildList {
            context.sessionId?.let { sessionId ->
                add(NamedGraphsBuilder.sessionGraph(baseUri, sessionId))
            }
            context.tenantId?.let { tenantId ->
                add(NamedGraphsBuilder.tenantGraph(baseUri, tenantId))
            }
        }
    }

    /**
     * Custom strategy with fallback
     *
     * Try custom provider, fall back to tenant-only
     */
    fun withFallback(
        primary: suspend (AgentContext) -> List<String>,
        fallback: suspend (AgentContext) -> List<String> = tenantOnly("http://default")
    ): suspend (AgentContext) -> List<String> = { context ->
        try {
            val graphs = primary(context)
            if (graphs.isEmpty()) fallback(context) else graphs
        } catch (e: Exception) {
            fallback(context)
        }
    }
}

/**
 * DSL for building Named Graphs extension
 *
 * Example:
 * ```kotlin
 * val extension = namedGraphsExtension {
 *     baseUri = "http://kaibrain.com/graphs"
 *
 *     strategy { context ->
 *         buildList {
 *             // User graph
 *             context.userId?.let { add(userGraph(it)) }
 *             // Tenant graph
 *             context.tenantId?.let { add(tenantGraph(it)) }
 *             // Shared graph
 *             add(shared())
 *         }
 *     }
 * }
 * ```
 */
class NamedGraphsExtensionBuilder {
    var baseUri: String = "http://example.com/graphs"
    private var strategyFn: (suspend (AgentContext) -> List<String>)? = null

    /**
     * Set custom strategy
     */
    fun strategy(fn: suspend (AgentContext) -> List<String>) {
        this.strategyFn = fn
    }

    /**
     * Use tenant-only strategy
     */
    fun tenantOnly() {
        strategyFn = NamedGraphsStrategies.tenantOnly(baseUri)
    }

    /**
     * Use tenant + shared strategy
     */
    fun tenantWithShared() {
        strategyFn = NamedGraphsStrategies.tenantWithShared(baseUri)
    }

    /**
     * Use hierarchical strategy
     */
    fun hierarchical() {
        strategyFn = NamedGraphsStrategies.hierarchical(baseUri)
    }

    /**
     * Helper: build tenant graph URI
     */
    fun tenantGraph(tenantId: String) = NamedGraphsBuilder.tenantGraph(baseUri, tenantId)

    /**
     * Helper: build user graph URI
     */
    fun userGraph(userId: String) = NamedGraphsBuilder.userGraph(baseUri, userId)

    /**
     * Helper: build session graph URI
     */
    fun sessionGraph(sessionId: String) = NamedGraphsBuilder.sessionGraph(baseUri, sessionId)

    /**
     * Helper: build shared graph URI
     */
    fun shared() = NamedGraphsBuilder.sharedGraph(baseUri)

    /**
     * Helper: build type graph URI
     */
    fun typeGraph(type: String) = NamedGraphsBuilder.typeGraph(baseUri, type)

    internal fun build(): NamedGraphsExtension {
        val provider = strategyFn ?: NamedGraphsStrategies.tenantOnly(baseUri)
        return NamedGraphsExtension(provider)
    }
}

/**
 * Create Named Graphs extension with DSL
 */
fun namedGraphsExtension(block: NamedGraphsExtensionBuilder.() -> Unit): NamedGraphsExtension {
    val builder = NamedGraphsExtensionBuilder()
    builder.block()
    return builder.build()
}
