package io.github.noailabs.spice.routing

/**
 * Result of a decision engine evaluation.
 *
 * DecisionResult is the core type for routing decisions. The [resultId] is used
 * for edge matching in EngineDecisionNode - this is the key mechanism for routing.
 *
 * ## Important: resultId-based Matching
 *
 * Edge routing is done by matching `resultId` strings, NOT by object equality.
 * This means:
 * - `StandardResult.YES.resultId` == "YES" (always matches edges mapped to YES)
 * - `DelegationResult.DELEGATE_TO_LLM().resultId` == "DELEGATE_TO_LLM" (matches regardless of instance)
 * - `SelectionResult("option-a").resultId` == "OPTION:option-a" (for per-option routing)
 *
 * ## Per-Option Routing
 *
 * For option-specific routing, use [SelectionResult] with option ID:
 * ```kotlin
 * decisionNode("route")
 *     .by(optionEngine)
 *     .on(SelectionResult.option("standard")).to("standardFlow")
 *     .on(SelectionResult.option("premium")).to("premiumFlow")
 *     .otherwise("defaultFlow")
 * ```
 *
 * For common flow regardless of option, use [SelectionResult.ANY]:
 * ```kotlin
 * decisionNode("route")
 *     .by(optionEngine)
 *     .on(SelectionResult.ANY).to("selectionHandler")  // All options go here
 * ```
 *
 * @since 1.0.7
 */
sealed interface DecisionResult {
    /**
     * Unique identifier for this result type.
     *
     * **Critical**: This is used for edge matching in EngineDecisionNode.
     * Two results with the same resultId will route to the same target,
     * regardless of other properties.
     */
    val resultId: String

    /**
     * Human-readable description of this result.
     */
    val description: String

    /**
     * Additional metadata from the decision engine.
     * This is passed through to SpiceMessage.data.
     */
    val metadata: Map<String, Any>
        get() = emptyMap()
}

/**
 * Standard routing decisions - the most common binary/simple cases.
 */
sealed class StandardResult(
    override val resultId: String,
    override val description: String
) : DecisionResult {

    /** Affirmative decision */
    data object YES : StandardResult("YES", "Affirmative decision")

    /** Negative decision */
    data object NO : StandardResult("NO", "Negative decision")

    /** Skip current step */
    data object SKIP : StandardResult("SKIP", "Skip current step")

    /** Retry previous step */
    data object RETRY : StandardResult("RETRY", "Retry previous step")

    /** Error condition detected */
    data object ERROR : StandardResult("ERROR", "Error condition")

    /** Default fallback route */
    data object DEFAULT : StandardResult("DEFAULT", "Default fallback route")

    /** Uncertain/cannot determine */
    data object UNCERTAIN : StandardResult("UNCERTAIN", "Cannot determine with confidence")

    companion object {
        /**
         * Get StandardResult by resultId.
         */
        fun fromId(id: String): StandardResult? = when (id) {
            "YES" -> YES
            "NO" -> NO
            "SKIP" -> SKIP
            "RETRY" -> RETRY
            "ERROR" -> ERROR
            "DEFAULT" -> DEFAULT
            "UNCERTAIN" -> UNCERTAIN
            else -> null
        }
    }
}

/**
 * Delegation decisions - hand off to another component.
 */
sealed class DelegationResult(
    override val resultId: String,
    override val description: String,
    override val metadata: Map<String, Any> = emptyMap()
) : DecisionResult {

    /**
     * Delegate to LLM for full processing.
     *
     * @property prompt Optional prompt override for the LLM
     */
    data class DELEGATE_TO_LLM(
        val prompt: String? = null,
        override val metadata: Map<String, Any> = emptyMap()
    ) : DelegationResult(
        resultId = "DELEGATE_TO_LLM",
        description = "Delegate to LLM"
    )

    /**
     * Delegate to a specific agent.
     *
     * @property agentId Target agent identifier
     */
    data class DELEGATE_TO_AGENT(
        val agentId: String,
        override val metadata: Map<String, Any> = emptyMap()
    ) : DelegationResult(
        resultId = "DELEGATE_TO_AGENT",
        description = "Delegate to agent: $agentId"
    )

    /**
     * Switch to a different workflow/graph.
     *
     * @property targetWorkflow Target workflow identifier
     */
    data class REORCHESTRATE(
        val targetWorkflow: String,
        override val metadata: Map<String, Any> = emptyMap()
    ) : DelegationResult(
        resultId = "REORCHESTRATE",
        description = "Reorchestrate to: $targetWorkflow"
    )

    /**
     * Escalate to human review.
     *
     * @property reason Reason for escalation
     */
    data class ESCALATE(
        val reason: String? = null,
        override val metadata: Map<String, Any> = emptyMap()
    ) : DelegationResult(
        resultId = "ESCALATE",
        description = "Escalate to human: ${reason ?: "No reason provided"}"
    )
}

/**
 * Selection result - user/system selected from options.
 *
 * ## Routing Patterns
 *
 * **Per-option routing** (resultId = "OPTION:{optionId}"):
 * ```kotlin
 * decisionNode("route")
 *     .on(SelectionResult.option("standard")).to("standardFlow")
 *     .on(SelectionResult.option("premium")).to("premiumFlow")
 * ```
 *
 * **Common routing** (resultId = "OPTION_SELECTED"):
 * ```kotlin
 * decisionNode("route")
 *     .on(SelectionResult.ANY).to("handleSelection")
 * // Then check message.getData<String>("_selectedOptionId") in handler
 * ```
 *
 * @property selectedOptionId The selected option identifier
 * @property selectedOptionIds All selected option IDs (for multi-select)
 * @property perOptionRouting If true, resultId is "OPTION:{optionId}" for per-option routing
 */
data class SelectionResult(
    val selectedOptionId: String,
    val selectedOptionIds: List<String> = listOf(selectedOptionId),
    val perOptionRouting: Boolean = true,
    override val metadata: Map<String, Any> = emptyMap()
) : DecisionResult {

    /**
     * Result ID depends on routing mode:
     * - perOptionRouting=true: "OPTION:{selectedOptionId}" (per-option routing)
     * - perOptionRouting=false: "OPTION_SELECTED" (common routing)
     */
    override val resultId: String = if (perOptionRouting) {
        "OPTION:$selectedOptionId"
    } else {
        "OPTION_SELECTED"
    }

    override val description: String = "Option selected: $selectedOptionId"

    companion object {
        /**
         * Sentinel for matching any option selection (common flow).
         * Use this in DSL when you want all selections to go to the same target.
         */
        val ANY = SelectionResult(
            selectedOptionId = "*",
            perOptionRouting = false
        )

        /**
         * Create a per-option routing result.
         * Use in DSL to map specific options to specific targets.
         */
        fun option(optionId: String) = SelectionResult(
            selectedOptionId = optionId,
            perOptionRouting = true
        )

        /**
         * Create a common routing result.
         * The actual selected option is stored but routes to a common handler.
         */
        fun selected(optionId: String) = SelectionResult(
            selectedOptionId = optionId,
            perOptionRouting = false
        )

        /**
         * Create result from multiple selections.
         */
        fun multiple(optionIds: List<String>, perOptionRouting: Boolean = false) = SelectionResult(
            selectedOptionId = optionIds.first(),
            selectedOptionIds = optionIds,
            perOptionRouting = perOptionRouting
        )
    }
}

/**
 * Custom result for user-defined decision types.
 *
 * Use this to extend the routing system without modifying core framework.
 *
 * ```kotlin
 * object MyResults {
 *     val NEEDS_APPROVAL = CustomResult("NEEDS_APPROVAL", "Requires manager approval")
 *     val RATE_LIMITED = CustomResult("RATE_LIMITED", "Request is rate limited")
 * }
 *
 * decisionNode("custom-route")
 *     .by(myEngine)
 *     .on(MyResults.NEEDS_APPROVAL).to("approval-flow")
 *     .on(MyResults.RATE_LIMITED).to("rate-limit-handler")
 * ```
 */
data class CustomResult(
    override val resultId: String,
    override val description: String = resultId,
    override val metadata: Map<String, Any> = emptyMap()
) : DecisionResult {
    companion object {
        /**
         * Create a custom result.
         */
        fun of(id: String, description: String = id) = CustomResult(id, description)

        /**
         * Create a custom result with metadata.
         */
        fun of(id: String, description: String, vararg metadata: Pair<String, Any>) =
            CustomResult(id, description, metadata.toMap())
    }
}
