package io.github.noailabs.spice.routing

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.routing.spi.*

/**
 * Decision Engine - the main SPI for routing decisions.
 *
 * DecisionEngine is the primary interface injected into EngineDecisionNode.
 * It evaluates a message and returns a DecisionResult that determines routing.
 *
 * ## Clean Architecture
 * - spice-core defines this interface (port)
 * - spice-routing-ai implements with LLM/ML (adapter)
 * - Application code implements with business rules
 *
 * ## Example Implementation
 * ```kotlin
 * class AmountBasedRouter : DecisionEngine {
 *     override val id = "amount-router"
 *     override val description = "Routes based on transaction amount"
 *
 *     override suspend fun evaluate(message: SpiceMessage): SpiceResult<DecisionResult> {
 *         val amount = message.getData<Double>("amount") ?: 0.0
 *         return SpiceResult.success(when {
 *             amount < 100 -> StandardResult.YES
 *             amount < 1000 -> DelegationResult.DELEGATE_TO_AGENT("supervisor")
 *             else -> DelegationResult.ESCALATE("High value transaction")
 *         })
 *     }
 * }
 * ```
 *
 * @since 1.0.7
 */
interface DecisionEngine {
    /**
     * Unique identifier for this engine.
     * Used for logging, metrics, and registry lookup.
     */
    val id: String

    /**
     * Human-readable description of what this engine does.
     */
    val description: String
        get() = id

    /**
     * Evaluate the message and return a routing decision.
     *
     * @param message The message to evaluate
     * @return DecisionResult determining the routing path
     */
    suspend fun evaluate(message: SpiceMessage): SpiceResult<DecisionResult>

    /**
     * Validate this engine's configuration.
     * Called during graph validation.
     *
     * @return List of validation issues (empty if valid)
     */
    fun validate(): List<DecisionEngineValidation> = emptyList()
}

/**
 * Validation result for DecisionEngine.
 */
data class DecisionEngineValidation(
    val level: Level,
    val message: String,
    val context: Map<String, Any> = emptyMap()
) {
    enum class Level {
        /** Informational - does not prevent execution */
        INFO,
        /** Warning - execution continues but may have issues */
        WARNING,
        /** Error - prevents execution */
        ERROR
    }

    val isError: Boolean get() = level == Level.ERROR
}

/**
 * Factory methods for creating common DecisionEngine patterns.
 */
object DecisionEngines {

    /**
     * Create an engine from a lambda function.
     *
     * ```kotlin
     * val engine = DecisionEngines.create("simple") { message ->
     *     if (message.content.contains("urgent")) StandardResult.YES
     *     else StandardResult.NO
     * }
     * ```
     */
    fun create(
        id: String,
        description: String = id,
        evaluator: suspend (SpiceMessage) -> DecisionResult
    ): DecisionEngine = object : DecisionEngine {
        override val id = id
        override val description = description

        override suspend fun evaluate(message: SpiceMessage): SpiceResult<DecisionResult> {
            return try {
                SpiceResult.success(evaluator(message))
            } catch (e: Exception) {
                SpiceResult.failure(
                    SpiceError.executionError(
                        "Decision engine '$id' failed: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    }

    /**
     * Create an engine from a YesNoClassifier.
     *
     * Maps YES -> StandardResult.YES, NO -> StandardResult.NO, UNCERTAIN -> StandardResult.UNCERTAIN
     */
    fun fromYesNo(
        classifier: YesNoClassifier,
        yesResult: DecisionResult = StandardResult.YES,
        noResult: DecisionResult = StandardResult.NO,
        uncertainResult: DecisionResult = StandardResult.UNCERTAIN
    ): DecisionEngine = object : DecisionEngine {
        override val id = classifier.id
        override val description = classifier.description

        override suspend fun evaluate(message: SpiceMessage): SpiceResult<DecisionResult> {
            return classifier.classify(message, RoutingContext.from(message)).map { result ->
                when (result.decision) {
                    YesNoResult.Decision.YES -> yesResult
                    YesNoResult.Decision.NO -> noResult
                    YesNoResult.Decision.UNCERTAIN -> uncertainResult
                }
            }
        }
    }

    /**
     * Create an engine from a ChoiceResolver.
     *
     * Maps selected choice ID to SelectionResult for per-option routing.
     */
    fun fromChoice(
        resolver: ChoiceResolver,
        perOptionRouting: Boolean = true
    ): DecisionEngine = object : DecisionEngine {
        override val id = resolver.id
        override val description = resolver.description

        override suspend fun evaluate(message: SpiceMessage): SpiceResult<DecisionResult> {
            return resolver.resolve(message, RoutingContext.from(message)).map { result ->
                SelectionResult(
                    selectedOptionId = result.selectedId,
                    perOptionRouting = perOptionRouting,
                    metadata = mapOf(
                        "confidence" to result.confidence,
                        "reasoning" to (result.reasoning ?: "")
                    )
                )
            }
        }
    }

    /**
     * Create an engine based on message data field.
     *
     * ```kotlin
     * val engine = DecisionEngines.fromData("status", mapOf(
     *     "approved" to StandardResult.YES,
     *     "rejected" to StandardResult.NO
     * ))
     * ```
     */
    fun fromData(
        key: String,
        mapping: Map<Any?, DecisionResult>,
        default: DecisionResult = StandardResult.DEFAULT
    ): DecisionEngine = create("data:$key", "Route based on data['$key']") { message ->
        val value = message.getData<Any>(key)
        mapping[value] ?: default
    }

    /**
     * Create an engine based on message metadata field.
     */
    fun fromMetadata(
        key: String,
        mapping: Map<Any?, DecisionResult>,
        default: DecisionResult = StandardResult.DEFAULT
    ): DecisionEngine = create("metadata:$key", "Route based on metadata['$key']") { message ->
        val value = message.getMetadata<Any>(key)
        mapping[value] ?: default
    }

    /**
     * Create a fallback chain of engines.
     *
     * Tries each engine in order until one succeeds without returning DEFAULT.
     */
    fun fallback(vararg engines: DecisionEngine): DecisionEngine = object : DecisionEngine {
        override val id = "fallback[${engines.joinToString(",") { it.id }}]"
        override val description = "Fallback chain of ${engines.size} engines"

        override suspend fun evaluate(message: SpiceMessage): SpiceResult<DecisionResult> {
            for (engine in engines) {
                val result = engine.evaluate(message)
                if (result is SpiceResult.Success && result.value != StandardResult.DEFAULT) {
                    return result
                }
            }
            return SpiceResult.success(StandardResult.DEFAULT)
        }

        override fun validate(): List<DecisionEngineValidation> {
            return engines.flatMap { it.validate() }
        }
    }

    /**
     * Create a conditional engine that uses different engines based on a predicate.
     */
    fun conditional(
        predicate: (SpiceMessage) -> Boolean,
        ifTrue: DecisionEngine,
        ifFalse: DecisionEngine
    ): DecisionEngine = object : DecisionEngine {
        override val id = "conditional[${ifTrue.id}|${ifFalse.id}]"
        override val description = "Conditional routing"

        override suspend fun evaluate(message: SpiceMessage): SpiceResult<DecisionResult> {
            return if (predicate(message)) {
                ifTrue.evaluate(message)
            } else {
                ifFalse.evaluate(message)
            }
        }

        override fun validate(): List<DecisionEngineValidation> {
            return ifTrue.validate() + ifFalse.validate()
        }
    }

    /**
     * NoOp engine that always returns DEFAULT.
     * Use as placeholder or in tests.
     */
    val NOOP: DecisionEngine = object : DecisionEngine {
        override val id = "noop"
        override val description = "No-op engine (always returns DEFAULT)"

        override suspend fun evaluate(message: SpiceMessage): SpiceResult<DecisionResult> {
            return SpiceResult.success(StandardResult.DEFAULT)
        }
    }

    /**
     * Always returns the specified result.
     * Useful for testing or static routing.
     */
    fun always(result: DecisionResult): DecisionEngine = object : DecisionEngine {
        override val id = "always[${result.resultId}]"
        override val description = "Always returns ${result.resultId}"

        override suspend fun evaluate(message: SpiceMessage): SpiceResult<DecisionResult> {
            return SpiceResult.success(result)
        }
    }
}
