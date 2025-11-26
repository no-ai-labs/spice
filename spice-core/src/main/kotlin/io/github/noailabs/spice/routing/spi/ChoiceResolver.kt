package io.github.noailabs.spice.routing.spi

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.serialization.Serializable

/**
 * SPI for resolving a choice from multiple options.
 *
 * Use Cases:
 * - Intent classification with multiple intents
 * - Menu/option selection routing
 * - Dynamic workflow branching based on content analysis
 * - Multi-class classification
 *
 * ## Clean Architecture
 * - spice-core defines this interface (port)
 * - spice-routing-ai implements with LLM/embedding (adapter)
 * - Application code implements with business rules
 *
 * ## Example Implementation
 * ```kotlin
 * class IntentResolver : ChoiceResolver {
 *     override val id = "intent-resolver"
 *     override val description = "Resolves user intent from message"
 *     override val choices = listOf(
 *         Choice("booking", "Booking", "User wants to make a reservation"),
 *         Choice("inquiry", "Inquiry", "User has a question"),
 *         Choice("complaint", "Complaint", "User has a complaint")
 *     )
 *
 *     override suspend fun resolve(message: SpiceMessage, context: RoutingContext): SpiceResult<ChoiceResult> {
 *         // Implementation using keywords, embeddings, or LLM
 *     }
 * }
 * ```
 *
 * @since 1.0.7
 */
interface ChoiceResolver {
    /**
     * Unique identifier for this resolver.
     */
    val id: String

    /**
     * Human-readable description.
     */
    val description: String

    /**
     * Available choices for resolution.
     */
    val choices: List<Choice>

    /**
     * Resolve the best matching choice for the message.
     *
     * @param message The message to analyze
     * @param context Additional routing context
     * @return The selected choice with confidence and rankings
     */
    suspend fun resolve(
        message: SpiceMessage,
        context: RoutingContext = RoutingContext.EMPTY
    ): SpiceResult<ChoiceResult>
}

/**
 * Represents a selectable choice/option.
 *
 * @property id Unique identifier for this choice (used for routing)
 * @property label Human-readable label
 * @property description Optional detailed description
 * @property examples Example inputs that match this choice (for training/matching)
 * @property metadata Additional metadata for this choice
 */
@Serializable
data class Choice(
    val id: String,
    val label: String,
    val description: String? = null,
    val examples: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * Create a simple choice with just id and label.
         */
        fun simple(id: String, label: String = id) = Choice(id, label)

        /**
         * Create choice with description.
         */
        fun of(id: String, label: String, description: String) = Choice(id, label, description)
    }
}

/**
 * Result of choice resolution.
 *
 * @property selectedChoice The primary selected choice
 * @property confidence Confidence score for the selection (0.0-1.0)
 * @property rankings All choices ranked by score (optional)
 * @property reasoning Explanation for the selection
 * @property metadata Additional metadata from the resolver
 */
data class ChoiceResult(
    val selectedChoice: Choice,
    val confidence: Double,
    val rankings: List<RankedChoice> = emptyList(),
    val reasoning: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(confidence in 0.0..1.0) {
            "Confidence must be between 0.0 and 1.0, got: $confidence"
        }
    }

    /**
     * The selected choice ID.
     */
    val selectedId: String get() = selectedChoice.id

    /**
     * Check if confidence meets the threshold.
     */
    fun meetsConfidence(threshold: Double): Boolean = confidence >= threshold

    /**
     * Get top N ranked choices.
     */
    fun topN(n: Int): List<RankedChoice> = rankings.take(n)

    /**
     * Check if there are alternative choices above a confidence threshold.
     */
    fun hasAlternatives(minConfidence: Double): Boolean =
        rankings.drop(1).any { it.score >= minConfidence }

    companion object {
        /**
         * Create result with single choice (no rankings).
         */
        fun single(choice: Choice, confidence: Double, reasoning: String? = null) =
            ChoiceResult(choice, confidence, listOf(RankedChoice(choice, confidence)), reasoning)

        /**
         * Create result from ranked choices.
         */
        fun fromRankings(rankings: List<RankedChoice>, reasoning: String? = null): ChoiceResult {
            require(rankings.isNotEmpty()) { "Rankings cannot be empty" }
            val sorted = rankings.sortedByDescending { it.score }
            val top = sorted.first()
            return ChoiceResult(top.choice, top.score, sorted, reasoning)
        }
    }
}

/**
 * A choice with its ranking score.
 */
data class RankedChoice(
    val choice: Choice,
    val score: Double
) {
    init {
        require(score in 0.0..1.0) {
            "Score must be between 0.0 and 1.0, got: $score"
        }
    }
}
