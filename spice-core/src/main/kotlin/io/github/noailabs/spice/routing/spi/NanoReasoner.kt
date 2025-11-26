package io.github.noailabs.spice.routing.spi

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlin.reflect.KClass

/**
 * SPI for lightweight reasoning/extraction tasks.
 *
 * NanoReasoner is designed for small, focused reasoning tasks that return
 * structured output. Unlike full LLM calls, these are meant to be fast
 * and cheap (hence "nano").
 *
 * Use Cases:
 * - Entity extraction (names, dates, amounts)
 * - Slot filling for forms
 * - Simple summarization
 * - Sentiment scoring
 * - Intent parameter extraction
 *
 * ## Clean Architecture
 * - spice-core defines this interface (port)
 * - spice-routing-ai implements with sLM/embeddings (adapter)
 * - Application code implements with regex/rules
 *
 * ## Example Implementation
 * ```kotlin
 * data class BookingParams(val date: String?, val guests: Int?, val roomType: String?)
 *
 * class BookingExtractor : NanoReasoner<BookingParams> {
 *     override val id = "booking-extractor"
 *     override val description = "Extracts booking parameters from message"
 *     override val outputType = BookingParams::class
 *     override val outputSchema = """{"date": "string?", "guests": "int?", "roomType": "string?"}"""
 *
 *     override suspend fun reason(message: SpiceMessage, context: RoutingContext): SpiceResult<ReasoningResult<BookingParams>> {
 *         // Extract using regex, LLM, or hybrid approach
 *     }
 * }
 * ```
 *
 * @param T The output type (should be serializable)
 * @since 1.0.7
 */
interface NanoReasoner<T : Any> {
    /**
     * Unique identifier for this reasoner.
     */
    val id: String

    /**
     * Human-readable description.
     */
    val description: String

    /**
     * The Kotlin class of the output type.
     * Used for deserialization and validation.
     */
    val outputType: KClass<T>

    /**
     * JSON schema for the output (optional).
     * Used for structured output generation with LLMs.
     */
    val outputSchema: String?
        get() = null

    /**
     * Perform reasoning and extract structured output.
     *
     * @param message The message to analyze
     * @param context Additional routing context
     * @return Reasoning result with structured output
     */
    suspend fun reason(
        message: SpiceMessage,
        context: RoutingContext = RoutingContext.EMPTY
    ): SpiceResult<ReasoningResult<T>>
}

/**
 * Result of nano reasoning.
 *
 * @param T The output type
 * @property output The extracted/reasoned output
 * @property reasoning Optional explanation of the reasoning process
 * @property confidence Confidence score (0.0-1.0)
 * @property tokensUsed Number of tokens used (for LLM implementations)
 * @property latencyMs Processing time in milliseconds
 * @property metadata Additional metadata
 */
data class ReasoningResult<T>(
    val output: T,
    val reasoning: String? = null,
    val confidence: Double = 1.0,
    val tokensUsed: Int? = null,
    val latencyMs: Long? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(confidence in 0.0..1.0) {
            "Confidence must be between 0.0 and 1.0, got: $confidence"
        }
    }

    /**
     * Check if confidence meets the threshold.
     */
    fun meetsConfidence(threshold: Double): Boolean = confidence >= threshold

    /**
     * Transform the output while preserving metadata.
     */
    fun <R> map(transform: (T) -> R): ReasoningResult<R> = ReasoningResult(
        output = transform(output),
        reasoning = reasoning,
        confidence = confidence,
        tokensUsed = tokensUsed,
        latencyMs = latencyMs,
        metadata = metadata
    )

    companion object {
        /**
         * Create a simple result with just the output.
         */
        fun <T> of(output: T, confidence: Double = 1.0) =
            ReasoningResult(output, confidence = confidence)

        /**
         * Create result with reasoning explanation.
         */
        fun <T> withReasoning(output: T, reasoning: String, confidence: Double = 1.0) =
            ReasoningResult(output, reasoning, confidence)
    }
}

/**
 * A NanoReasoner that returns String output.
 * Convenience interface for simple text extraction/summarization.
 */
interface StringReasoner : NanoReasoner<String> {
    override val outputType: KClass<String>
        get() = String::class
}

/**
 * A NanoReasoner that returns a Map.
 * Convenience interface for dynamic key-value extraction.
 */
interface MapReasoner : NanoReasoner<Map<String, Any>> {
    @Suppress("UNCHECKED_CAST")
    override val outputType: KClass<Map<String, Any>>
        get() = Map::class as KClass<Map<String, Any>>
}
