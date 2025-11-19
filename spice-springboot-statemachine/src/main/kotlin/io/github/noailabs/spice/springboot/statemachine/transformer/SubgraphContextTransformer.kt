package io.github.noailabs.spice.springboot.statemachine.transformer

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import org.slf4j.LoggerFactory

/**
 * Manages context propagation for super-graph → subgraph execution.
 *
 * When a graph calls another graph (subgraph), this transformer:
 * - Preserves parent graph context (traceId, userId, etc.)
 * - Adds subgraph execution context (depth, parentGraphId)
 * - Ensures clean context isolation between subgraphs
 *
 * **kai-core super-graph example:**
 * ```
 * Super-graph (BFS routing)
 *   ├─ Subgraph: logged-in-flow
 *   │   ├─ Node: check-session
 *   │   └─ Node: load-profile
 *   └─ Subgraph: logged-out-flow
 *       ├─ Node: show-login
 *       └─ Node: create-account
 * ```
 *
 * **Usage:**
 * ```kotlin
 * @Bean
 * fun subgraphContextTransformer(): MessageTransformer {
 *     return SubgraphContextTransformer(
 *         maxDepth = 5,  // Prevent infinite recursion
 *         preserveKeys = setOf("userId", "tenantId", "traceId", "sessionToken")
 *     )
 * }
 * ```
 *
 * **Accessing subgraph context:**
 * ```kotlin
 * val depth = message.getMetadata<Int>("subgraphDepth") ?: 0
 * val parentGraph = message.getMetadata<String>("parentGraphId")
 * val isSubgraph = message.getMetadata<Boolean>("isSubgraph") == true
 * ```
 *
 * @param maxDepth Maximum subgraph nesting depth (default: 10)
 * @param preserveKeys Metadata keys to preserve across subgraph boundaries
 */
class SubgraphContextTransformer(
    private val maxDepth: Int = 10,
    private val preserveKeys: Set<String> = setOf(
        "userId",
        "tenantId",
        "traceId",
        "sessionToken",
        "correlationId",
        "isLoggedIn"
    )
) : MessageTransformer {

    private val logger = LoggerFactory.getLogger(SubgraphContextTransformer::class.java)

    override suspend fun beforeExecution(
        graph: Graph,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        val currentDepth = message.getMetadata<Int>("subgraphDepth") ?: 0
        val parentGraphId = message.getMetadata<String>("currentGraphId")

        // Check max depth
        if (currentDepth >= maxDepth) {
            logger.error("Subgraph depth limit exceeded: depth=$currentDepth, maxDepth=$maxDepth, graph=${graph.id}")
            return SpiceResult.failure(
                io.github.noailabs.spice.error.SpiceError.executionError(
                    "Subgraph depth limit exceeded (max: $maxDepth, current: $currentDepth)"
                )
            )
        }

        val isSubgraph = currentDepth > 0

        logger.debug(
            "Entering graph: graphId=${graph.id}, depth=$currentDepth, isSubgraph=$isSubgraph, parentGraph=$parentGraphId"
        )

        // Prepare subgraph context
        val subgraphContext = buildMap {
            // Preserve important metadata from parent
            preserveKeys.forEach { key ->
                message.getMetadata<Any>(key)?.let { value ->
                    put(key, value)
                }
            }

            // Add subgraph tracking
            put("subgraphDepth", currentDepth + 1)
            put("isSubgraph", isSubgraph)
            put("currentGraphId", graph.id)

            if (parentGraphId != null) {
                put("parentGraphId", parentGraphId)
            }

            // Track subgraph path (for debugging)
            val parentPath = message.getMetadata<String>("subgraphPath") ?: ""
            val newPath = if (parentPath.isEmpty()) {
                graph.id
            } else {
                "$parentPath -> ${graph.id}"
            }
            put("subgraphPath", newPath)

            // Timestamp for subgraph entry
            put("subgraphEnteredAt", System.currentTimeMillis())
        }

        return SpiceResult.success(
            message.withMetadata(subgraphContext)
        )
    }

    override suspend fun afterExecution(
        graph: Graph,
        input: SpiceMessage,
        output: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        val depth = input.getMetadata<Int>("subgraphDepth") ?: 0
        val enteredAt = input.getMetadata<Long>("subgraphEnteredAt") ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - enteredAt

        logger.debug(
            "Exiting graph: graphId=${graph.id}, depth=$depth, duration=${duration}ms, state=${output.state}"
        )

        // Restore parent context
        val parentGraphId = input.getMetadata<String>("parentGraphId")
        val parentDepth = (depth - 1).coerceAtLeast(0)

        val exitContext = buildMap {
            // Restore parent graph context
            put("subgraphDepth", parentDepth)
            put("isSubgraph", parentDepth > 0)

            if (parentGraphId != null) {
                put("currentGraphId", parentGraphId)
            }

            // Add execution stats
            put("lastSubgraphDuration", duration)
            put("lastSubgraphId", graph.id)
        }

        return SpiceResult.success(
            output.withMetadata(exitContext)
        )
    }

    companion object {
        /**
         * Creates a SubgraphContextTransformer with default settings.
         */
        fun default(): SubgraphContextTransformer {
            return SubgraphContextTransformer()
        }

        /**
         * Creates a SubgraphContextTransformer for kai-core (preserves auth + trace).
         */
        fun forKaiCore(): SubgraphContextTransformer {
            return SubgraphContextTransformer(
                maxDepth = 5,
                preserveKeys = setOf(
                    "userId",
                    "tenantId",
                    "traceId",
                    "spanId",
                    "sessionToken",
                    "isLoggedIn",
                    "correlationId",
                    "deviceId",
                    "ipAddress"
                )
            )
        }

        /**
         * Creates a SubgraphContextTransformer with no depth limit (for testing).
         */
        fun unlimited(): SubgraphContextTransformer {
            return SubgraphContextTransformer(maxDepth = Int.MAX_VALUE)
        }
    }
}
