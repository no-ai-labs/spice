package io.github.noailabs.spice.routing.spi

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * SPI for date-based policy extraction tasks.
 *
 * PolicyReasoner extends NanoReasoner with specialized methods for extracting
 * policy information that depends on dates (e.g., refund policies, cancellation policies).
 *
 * ## Use Cases
 * - Refund policy extraction (check-in date vs current date)
 * - Cancellation policy parsing
 * - Booking modification rules
 * - Price policy extraction based on dates
 *
 * ## Architecture
 * ```
 * NanoReasoner<T>
 *      ▲
 *      └── PolicyReasoner<T>
 *              ▲
 *              └── RefundPolicyReasoner (kai-core)
 * ```
 *
 * ## Implementation Pattern
 * Implementations should follow the hybrid approach:
 * 1. Rule-based parsing (fast, free)
 * 2. LLM fallback (accurate, cost)
 * 3. Caching of parsed rules (not computed values)
 *
 * ## Example
 * ```kotlin
 * class RefundPolicyReasoner : PolicyReasoner<ParsedRefundPolicy> {
 *     override val domain = "refund"
 *
 *     override suspend fun extractWithDate(
 *         policyText: String,
 *         targetDate: Instant,
 *         referenceDate: Instant
 *     ): SpiceResult<ReasoningResult<ParsedRefundPolicy>> {
 *         // 1. Try rule-based parsing
 *         // 2. Fallback to LLM
 *         // 3. Compute rate based on dates
 *     }
 * }
 * ```
 *
 * @param T The parsed policy output type
 * @since 1.0.8
 */
interface PolicyReasoner<T : Any> : NanoReasoner<T> {

    /**
     * Domain identifier for this policy type.
     * Used for caching, metrics, and registry lookup.
     *
     * Examples: "refund", "cancellation", "modification", "pricing"
     */
    val domain: String

    /**
     * Extract policy information based on date context.
     *
     * This is the primary method for date-dependent policy extraction.
     * The targetDate (e.g., check-in date) and referenceDate (e.g., today)
     * are used to determine which policy rule applies.
     *
     * @param policyText The raw policy text to parse
     * @param targetDate The target date (e.g., check-in date)
     * @param referenceDate The reference date for calculation (default: now)
     * @return Parsed policy result with confidence score
     */
    suspend fun extractWithDate(
        policyText: String,
        targetDate: Instant,
        referenceDate: Instant = Clock.System.now()
    ): SpiceResult<ReasoningResult<T>>

    /**
     * Default implementation bridges NanoReasoner.reason() to extractWithDate().
     *
     * Expects message metadata to contain:
     * - "policy_text": String - The policy text to parse
     * - "target_date": String (ISO8601) - The target date
     * - "reference_date": String (ISO8601, optional) - The reference date
     */
    override suspend fun reason(
        message: SpiceMessage,
        context: RoutingContext
    ): SpiceResult<ReasoningResult<T>> {
        val policyText = message.getMetadata<String>("policy_text")
            ?: return SpiceResult.failure(
                io.github.noailabs.spice.error.SpiceError.ValidationError(
                    "Missing required metadata: policy_text"
                )
            )

        val targetDateStr = message.getMetadata<String>("target_date")
            ?: return SpiceResult.failure(
                io.github.noailabs.spice.error.SpiceError.ValidationError(
                    "Missing required metadata: target_date"
                )
            )

        val targetDate = try {
            Instant.parse(targetDateStr)
        } catch (e: Exception) {
            return SpiceResult.failure(
                io.github.noailabs.spice.error.SpiceError.ValidationError(
                    "Invalid target_date format: $targetDateStr"
                )
            )
        }

        val referenceDate = message.getMetadata<String>("reference_date")
            ?.let {
                try { Instant.parse(it) } catch (e: Exception) { null }
            }
            ?: Clock.System.now()

        return extractWithDate(policyText, targetDate, referenceDate)
    }
}

/**
 * Enumeration of supported parse methods for policy extraction.
 */
enum class PolicyParseMethod {
    /** Rule-based parsing (regex, patterns) */
    RULE,
    /** LLM-based extraction */
    LLM,
    /** Retrieved from cache */
    CACHED,
    /** Fallback value when parsing fails */
    FALLBACK
}
