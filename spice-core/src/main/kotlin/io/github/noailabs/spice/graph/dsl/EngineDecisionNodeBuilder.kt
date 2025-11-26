package io.github.noailabs.spice.graph.dsl

import io.github.noailabs.spice.graph.Edge
import io.github.noailabs.spice.graph.nodes.EngineDecisionNode
import io.github.noailabs.spice.routing.DecisionEngine
import io.github.noailabs.spice.routing.DecisionLifecycleListener
import io.github.noailabs.spice.routing.DecisionResult
import io.github.noailabs.spice.routing.SelectionResult

/**
 * DSL builder for creating [EngineDecisionNode] with fluent result-to-target mapping.
 *
 * ## Usage
 *
 * ```kotlin
 * graph("workflow") {
 *     decisionNode("route")
 *         .by(myDecisionEngine)
 *         .on(StandardResult.YES).to("yesFlow")
 *         .on(StandardResult.NO).to("noFlow")
 *         .on(SelectionResult.option("premium")).to("premiumFlow")
 *         .otherwise("defaultFlow")
 *
 *     // ...
 * }
 * ```
 *
 * ## Important: resultId-based Matching
 *
 * Edge routing uses [DecisionResult.resultId] for matching.
 * Two results with the same resultId will route to the same target.
 *
 * For example:
 * - `StandardResult.YES.resultId` = "YES"
 * - `DelegationResult.DELEGATE_TO_LLM().resultId` = "DELEGATE_TO_LLM"
 * - `SelectionResult.option("a").resultId` = "OPTION:a"
 *
 * @since 1.0.7
 */
class EngineDecisionNodeBuilder(val id: String) {
    private var engine: DecisionEngine? = null
    private val resultMappings = mutableListOf<ResultMapping>()
    private var fallbackTarget: String? = null
    private var lifecycleListener: DecisionLifecycleListener = DecisionLifecycleListener.NOOP

    /**
     * Edges automatically generated from result mappings.
     * These are added to the graph during build.
     */
    internal val generatedEdges = mutableListOf<Edge>()

    /**
     * Set the decision engine for this node.
     *
     * @param engine The decision engine to use
     * @return RouteBuilder for fluent chaining
     */
    fun by(engine: DecisionEngine): RouteBuilder {
        this.engine = engine
        return RouteBuilder(this)
    }

    /**
     * Set lifecycle listener for decision events.
     */
    fun withListener(listener: DecisionLifecycleListener): EngineDecisionNodeBuilder {
        this.lifecycleListener = listener
        return this
    }

    /**
     * Fluent builder for result-to-target routing.
     */
    class RouteBuilder internal constructor(
        private val parent: EngineDecisionNodeBuilder
    ) {
        /**
         * Map a decision result to a target node.
         *
         * @param result The decision result to match (by resultId)
         * @return TargetBuilder for specifying the target
         */
        fun on(result: DecisionResult): TargetBuilder {
            return TargetBuilder(parent, this, result)
        }

        /**
         * Set fallback target for unmatched results.
         *
         * This is called when no explicit mapping matches the engine's result.
         *
         * @param target The fallback target node ID
         * @return This builder for chaining
         */
        fun otherwise(target: String): RouteBuilder {
            parent.fallbackTarget = target
            parent.generateFallbackEdge(target)
            return this
        }

        /**
         * Build the EngineDecisionNode.
         */
        fun build(): EngineDecisionNode = parent.build()
    }

    /**
     * Fluent builder for specifying target node.
     */
    class TargetBuilder internal constructor(
        private val parent: EngineDecisionNodeBuilder,
        private val routeBuilder: RouteBuilder,
        private val result: DecisionResult
    ) {
        /**
         * Set the target node for this result.
         *
         * @param target The target node ID
         * @return RouteBuilder for continued chaining
         */
        fun to(target: String): RouteBuilder {
            parent.addMapping(result, target)
            return routeBuilder
        }
    }

    /**
     * Internal: Add a result-to-target mapping.
     */
    internal fun addMapping(result: DecisionResult, target: String) {
        val priority = resultMappings.size
        val resultId = result.resultId

        resultMappings.add(ResultMapping(resultId, target, priority))

        // Generate edge for this mapping
        // Edge condition checks _decisionResult matches
        generatedEdges.add(
            Edge(
                from = id,
                to = target,
                priority = priority,
                name = "decision:$resultId->$target",
                condition = { message ->
                    message.getData<String>("_decisionResult") == resultId
                }
            )
        )
    }

    /**
     * Internal: Generate fallback edge.
     */
    private fun generateFallbackEdge(target: String) {
        generatedEdges.add(
            Edge(
                from = id,
                to = target,
                priority = Int.MAX_VALUE,
                isFallback = true,
                name = "decision:fallback->$target",
                condition = { true }  // Always matches as last resort
            )
        )
    }

    /**
     * Build the EngineDecisionNode.
     *
     * @throws IllegalStateException if engine is not set or no mappings defined
     */
    fun build(): EngineDecisionNode {
        val engineInstance = engine
        requireNotNull(engineInstance) {
            "DecisionNode '$id' must have an engine. Use .by(engine) to set it."
        }
        require(resultMappings.isNotEmpty() || fallbackTarget != null) {
            "DecisionNode '$id' must have at least one result mapping (.on().to()) or a fallback (.otherwise())."
        }

        val resultToTargetMap = resultMappings.associate { it.resultId to it.target }

        return EngineDecisionNode(
            id = id,
            engine = engineInstance,
            resultToTargetMap = resultToTargetMap,
            fallbackTarget = fallbackTarget,
            lifecycleListener = lifecycleListener
        )
    }

    /**
     * Internal data class for result mapping.
     */
    private data class ResultMapping(
        val resultId: String,
        val target: String,
        val priority: Int
    )

    companion object {
        /**
         * Create a builder for the given node ID.
         */
        fun create(id: String): EngineDecisionNodeBuilder = EngineDecisionNodeBuilder(id)
    }
}
