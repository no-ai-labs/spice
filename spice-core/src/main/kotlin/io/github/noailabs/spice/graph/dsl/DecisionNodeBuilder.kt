package io.github.noailabs.spice.graph.dsl

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.graph.Edge
import io.github.noailabs.spice.graph.nodes.DecisionBranch
import io.github.noailabs.spice.graph.nodes.DecisionNode

/**
 * DSL builder for creating DecisionNode with fluent branch definitions.
 *
 * **Example Usage:**
 * ```kotlin
 * graph("workflow") {
 *     decision("check_payment") {
 *         branch("has_payment", "payment_handler")
 *             .whenData("paymentMethod") { it != null }
 *
 *         branch("no_payment", "default_handler")
 *             .otherwise()
 *     }
 * }
 * ```
 *
 * Or with the shorter syntax:
 * ```kotlin
 * decision("route") {
 *     "handler_a".whenData("type") { it == "A" }
 *     "handler_b".whenData("type") { it == "B" }
 *     "default".otherwise()
 * }
 * ```
 *
 * **Branch Evaluation Order:**
 * Branches are evaluated in the order they are defined. The first matching branch wins.
 * This order is guaranteed and deterministic - branches are stored in a List internally.
 *
 * **Important:** When loading from YAML or other sources, ensure branches are fed in
 * the declared order (use List, not Set). Non-deterministic ordering will cause
 * unpredictable routing behavior.
 *
 * **Fallback Branch:**
 * Only one otherwise() branch is allowed per decision node. Attempting to register
 * multiple fallback branches will throw an IllegalArgumentException.
 *
 * @since 1.0.0
 */
class DecisionNodeBuilder(val id: String) {
    private val branches = mutableListOf<DecisionBranch>()
    internal val generatedEdges = mutableListOf<Edge>()
    private var hasOtherwiseBranch = false

    /**
     * Define a named branch with a target node.
     *
     * @param name Human-readable name for the branch
     * @param target The node ID to route to
     * @return BranchBuilder for adding conditions
     */
    fun branch(name: String, target: String): BranchBuilder {
        return BranchBuilder(this, name, target)
    }

    /**
     * Shorthand: Define a branch where target is used as the name.
     *
     * ```kotlin
     * "handler_a".whenData("type") { it == "A" }
     * ```
     */
    fun String.whenData(key: String, condition: (Any?) -> Boolean): DecisionNodeBuilder {
        branch(this, this).whenData(key, condition)
        return this@DecisionNodeBuilder
    }

    /**
     * Shorthand: Define a branch based on metadata.
     */
    fun String.whenMetadata(key: String, condition: (Any?) -> Boolean): DecisionNodeBuilder {
        branch(this, this).whenMetadata(key, condition)
        return this@DecisionNodeBuilder
    }

    /**
     * Shorthand: Define a branch based on message content.
     */
    fun String.whenContent(condition: (String) -> Boolean): DecisionNodeBuilder {
        branch(this, this).whenContent(condition)
        return this@DecisionNodeBuilder
    }

    /**
     * Shorthand: Define a branch with full message access.
     */
    fun String.whenMessage(condition: (SpiceMessage) -> Boolean): DecisionNodeBuilder {
        branch(this, this).whenMessage(condition)
        return this@DecisionNodeBuilder
    }

    /**
     * Shorthand: Define a default/fallback branch.
     */
    fun String.otherwise(): DecisionNodeBuilder {
        branch(this, this).otherwise()
        return this@DecisionNodeBuilder
    }

    internal fun addBranch(branch: DecisionBranch, isOtherwise: Boolean = false) {
        if (isOtherwise) {
            require(!hasOtherwiseBranch) {
                "Decision node '$id' already has an otherwise() branch. Only one fallback branch is allowed."
            }
            hasOtherwiseBranch = true
        }

        branches.add(branch)
        // Generate edge from decision node to target
        generatedEdges.add(
            Edge(
                from = id,
                to = branch.target,
                priority = branches.size - 1,  // Preserve order
                condition = { message ->
                    message.getData<String>("_selectedBranch") == branch.target
                }
            )
        )
    }

    /**
     * Build the DecisionNode.
     *
     * @throws IllegalArgumentException if no branches are defined
     */
    fun build(): DecisionNode {
        require(branches.isNotEmpty()) {
            "Decision node '$id' must have at least one branch"
        }
        return DecisionNode(id, branches.toList())
    }

    /**
     * Builder for defining branch conditions.
     */
    class BranchBuilder(
        private val parent: DecisionNodeBuilder,
        private val name: String,
        private val target: String
    ) {
        /**
         * Route to target when data[key] matches condition.
         *
         * ```kotlin
         * branch("has_payment", "payment_handler")
         *     .whenData("paymentMethod") { it != null }
         * ```
         */
        fun whenData(key: String, condition: (Any?) -> Boolean): DecisionNodeBuilder {
            parent.addBranch(
                DecisionBranch(
                    name = name,
                    target = target,
                    condition = { message -> condition(message.getData<Any>(key)) }
                )
            )
            return parent
        }

        /**
         * Route to target when data[key] equals expected value.
         *
         * ```kotlin
         * branch("type_a", "handler_a")
         *     .whenDataEquals("type", "A")
         * ```
         */
        fun whenDataEquals(key: String, expected: Any?): DecisionNodeBuilder {
            return whenData(key) { it == expected }
        }

        /**
         * Route to target when metadata[key] matches condition.
         */
        fun whenMetadata(key: String, condition: (Any?) -> Boolean): DecisionNodeBuilder {
            parent.addBranch(
                DecisionBranch(
                    name = name,
                    target = target,
                    condition = { message -> condition(message.getMetadata<Any>(key)) }
                )
            )
            return parent
        }

        /**
         * Route to target when metadata[key] equals expected value.
         */
        fun whenMetadataEquals(key: String, expected: Any?): DecisionNodeBuilder {
            return whenMetadata(key) { it == expected }
        }

        /**
         * Route to target when message content matches condition.
         */
        fun whenContent(condition: (String) -> Boolean): DecisionNodeBuilder {
            parent.addBranch(
                DecisionBranch(
                    name = name,
                    target = target,
                    condition = { message -> condition(message.content) }
                )
            )
            return parent
        }

        /**
         * Route to target when content contains substring.
         */
        fun whenContentContains(substring: String): DecisionNodeBuilder {
            return whenContent { it.contains(substring) }
        }

        /**
         * Route to target with full message access.
         *
         * ```kotlin
         * branch("complex", "handler")
         *     .whenMessage { msg ->
         *         msg.getData<Int>("score") ?: 0 > 80 &&
         *         msg.getMetadata<String>("tier") == "premium"
         *     }
         * ```
         */
        fun whenMessage(condition: (SpiceMessage) -> Boolean): DecisionNodeBuilder {
            parent.addBranch(
                DecisionBranch(
                    name = name,
                    target = target,
                    condition = condition
                )
            )
            return parent
        }

        /**
         * Default branch - always matches.
         * Should be the last branch defined.
         * Only one otherwise() branch is allowed per decision node.
         *
         * ```kotlin
         * branch("default", "fallback_handler")
         *     .otherwise()
         * ```
         *
         * @throws IllegalArgumentException if otherwise() is called more than once
         */
        fun otherwise(): DecisionNodeBuilder {
            parent.addBranch(
                DecisionBranch(
                    name = name,
                    target = target,
                    condition = { true }
                ),
                isOtherwise = true
            )
            return parent
        }
    }
}
