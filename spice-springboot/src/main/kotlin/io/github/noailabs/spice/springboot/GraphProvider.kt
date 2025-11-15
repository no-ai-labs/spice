package io.github.noailabs.spice.springboot

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.graph.Graph

/**
 * Functional interface for providing graphs to the Arbiter.
 *
 * Implement this interface to define how graphs are selected based on incoming messages.
 *
 * **Example:**
 * ```kotlin
 * @Bean
 * fun graphProvider(graphRegistry: MyGraphRegistry): GraphProvider = GraphProvider { message ->
 *     val graphId = message.data["graphId"] as? String
 *         ?: throw IllegalArgumentException("Missing graphId in message")
 *
 *     graphRegistry.get(graphId)
 *         ?: throw IllegalArgumentException("Graph not found: $graphId")
 * }
 * ```
 *
 * @since 1.0.0
 */
fun interface GraphProvider {
    /**
     * Provide a graph for the given message.
     *
     * @param message The incoming message
     * @return The graph to execute
     * @throws IllegalArgumentException if no suitable graph can be found
     */
    fun provide(message: SpiceMessage): Graph
}
