package io.github.noailabs.spice.routing.spi

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult

/**
 * SPI for binary Yes/No classification decisions.
 *
 * Use Cases:
 * - Sentiment classification (positive/negative)
 * - Intent confirmation ("Do you want to proceed?")
 * - Guard conditions ("Is user authenticated?")
 * - Simple binary routing decisions
 *
 * ## Clean Architecture
 * - spice-core defines this interface (port)
 * - spice-routing-ai implements with LLM (adapter)
 * - Application code implements with business rules
 *
 * ## Example Implementation
 * ```kotlin
 * class ConfirmationClassifier : YesNoClassifier {
 *     override val id = "confirmation"
 *     override val description = "Classifies user confirmation intent"
 *
 *     override suspend fun classify(message: SpiceMessage, context: RoutingContext): SpiceResult<YesNoResult> {
 *         val content = message.content.lowercase()
 *         return when {
 *             content.contains("yes") || content.contains("confirm") ->
 *                 SpiceResult.success(YesNoResult.yes(0.9, "Contains affirmative keyword"))
 *             content.contains("no") || content.contains("cancel") ->
 *                 SpiceResult.success(YesNoResult.no(0.9, "Contains negative keyword"))
 *             else ->
 *                 SpiceResult.success(YesNoResult.uncertain("No clear intent detected"))
 *         }
 *     }
 * }
 * ```
 *
 * @since 1.0.7
 */
interface YesNoClassifier {
    /**
     * Unique identifier for this classifier.
     * Used for logging, metrics, and registry lookup.
     */
    val id: String

    /**
     * Human-readable description of what this classifier does.
     */
    val description: String

    /**
     * Classify the message as YES, NO, or UNCERTAIN.
     *
     * @param message The message to classify
     * @param context Additional routing context
     * @return Classification result with confidence score
     */
    suspend fun classify(
        message: SpiceMessage,
        context: RoutingContext = RoutingContext.EMPTY
    ): SpiceResult<YesNoResult>
}

/**
 * Result of a Yes/No classification.
 *
 * @property decision The classification decision (YES, NO, or UNCERTAIN)
 * @property confidence Confidence score between 0.0 and 1.0
 * @property reasoning Optional explanation for the decision
 * @property metadata Additional metadata from the classifier
 */
data class YesNoResult(
    val decision: Decision,
    val confidence: Double,
    val reasoning: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(confidence in 0.0..1.0) {
            "Confidence must be between 0.0 and 1.0, got: $confidence"
        }
    }

    /**
     * Classification decision types.
     */
    enum class Decision {
        /** Affirmative/positive decision */
        YES,
        /** Negative decision */
        NO,
        /** Cannot determine with confidence */
        UNCERTAIN
    }

    /**
     * Check if decision is YES.
     */
    val isYes: Boolean get() = decision == Decision.YES

    /**
     * Check if decision is NO.
     */
    val isNo: Boolean get() = decision == Decision.NO

    /**
     * Check if decision is UNCERTAIN.
     */
    val isUncertain: Boolean get() = decision == Decision.UNCERTAIN

    /**
     * Check if confidence meets the threshold.
     */
    fun meetsConfidence(threshold: Double): Boolean = confidence >= threshold

    companion object {
        /**
         * Create a YES result.
         */
        fun yes(confidence: Double = 1.0, reasoning: String? = null, metadata: Map<String, Any> = emptyMap()) =
            YesNoResult(Decision.YES, confidence, reasoning, metadata)

        /**
         * Create a NO result.
         */
        fun no(confidence: Double = 1.0, reasoning: String? = null, metadata: Map<String, Any> = emptyMap()) =
            YesNoResult(Decision.NO, confidence, reasoning, metadata)

        /**
         * Create an UNCERTAIN result.
         */
        fun uncertain(reasoning: String? = null, confidence: Double = 0.5, metadata: Map<String, Any> = emptyMap()) =
            YesNoResult(Decision.UNCERTAIN, confidence, reasoning, metadata)

        /**
         * Create result from boolean value.
         */
        fun fromBoolean(value: Boolean, confidence: Double = 1.0, reasoning: String? = null) =
            if (value) yes(confidence, reasoning) else no(confidence, reasoning)
    }
}
