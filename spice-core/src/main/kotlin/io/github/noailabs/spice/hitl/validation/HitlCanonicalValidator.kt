package io.github.noailabs.spice.hitl.validation

import io.github.noailabs.spice.hitl.result.HitlResponseKind

/**
 * SPI for validating HitlResult.canonical values.
 *
 * Implement this interface to provide custom validation logic
 * for HITL canonical values.
 *
 * **Thread Safety**: Implementations must be thread-safe.
 *
 * **Usage:**
 * ```kotlin
 * // Use default validator
 * HitlResultValidators.canonicalValidator.validate("confirm", HitlResponseKind.SINGLE)
 *
 * // Swap to custom validator
 * HitlResultValidators.setCanonicalValidator(MyCustomValidator())
 * ```
 *
 * @since Spice 1.3.5
 */
fun interface HitlCanonicalValidator {

    /**
     * Validate a canonical value.
     *
     * @param canonical The canonical value to validate
     * @param kind The response kind for context
     * @return ValidationResult indicating success or failure with error message
     */
    fun validate(canonical: String, kind: HitlResponseKind): ValidationResult

    /**
     * Result of canonical validation.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    ) {
        companion object {
            /**
             * Create a successful validation result.
             */
            fun valid(): ValidationResult = ValidationResult(true)

            /**
             * Create a failed validation result with error message.
             */
            fun invalid(message: String): ValidationResult = ValidationResult(false, message)
        }

        /**
         * Throw IllegalArgumentException if invalid.
         *
         * @throws IllegalArgumentException if validation failed
         */
        fun throwIfInvalid() {
            if (!isValid) {
                throw IllegalArgumentException(errorMessage ?: "Validation failed")
            }
        }
    }
}

/**
 * Default implementation that requires non-blank canonical without leading/trailing whitespace.
 *
 * **Validation Rules:**
 * - Rejects blank strings (empty or whitespace-only)
 * - Rejects strings with leading or trailing whitespace
 *
 * @since Spice 1.3.5
 */
class DefaultHitlCanonicalValidator : HitlCanonicalValidator {

    override fun validate(
        canonical: String,
        kind: HitlResponseKind
    ): HitlCanonicalValidator.ValidationResult {
        return when {
            canonical.trim().isBlank() -> {
                HitlCanonicalValidator.ValidationResult.invalid(
                    "HitlResult.canonical must not be blank. Kind=$kind"
                )
            }
            canonical != canonical.trim() -> {
                HitlCanonicalValidator.ValidationResult.invalid(
                    "HitlResult.canonical must not have leading/trailing whitespace. " +
                    "Got: \"$canonical\", expected: \"${canonical.trim()}\". Kind=$kind"
                )
            }
            else -> HitlCanonicalValidator.ValidationResult.valid()
        }
    }
}

/**
 * Lenient validator that allows any string including empty.
 *
 * Use for testing or legacy compatibility scenarios.
 *
 * @since Spice 1.3.5
 */
class LenientHitlCanonicalValidator : HitlCanonicalValidator {
    override fun validate(
        canonical: String,
        kind: HitlResponseKind
    ): HitlCanonicalValidator.ValidationResult {
        return HitlCanonicalValidator.ValidationResult.valid()
    }
}

/**
 * Strict validator with additional rules.
 *
 * **Rules:**
 * - Non-blank (required)
 * - No leading/trailing whitespace
 * - Maximum length check
 *
 * @param maxLength Maximum allowed length for canonical (default: 1024)
 *
 * @since Spice 1.3.5
 */
class StrictHitlCanonicalValidator(
    private val maxLength: Int = 1024
) : HitlCanonicalValidator {

    override fun validate(
        canonical: String,
        kind: HitlResponseKind
    ): HitlCanonicalValidator.ValidationResult {
        if (canonical.isBlank()) {
            return HitlCanonicalValidator.ValidationResult.invalid(
                "HitlResult.canonical must not be blank. Kind=$kind"
            )
        }

        if (canonical != canonical.trim()) {
            return HitlCanonicalValidator.ValidationResult.invalid(
                "HitlResult.canonical must not have leading/trailing whitespace. Kind=$kind"
            )
        }

        if (canonical.length > maxLength) {
            return HitlCanonicalValidator.ValidationResult.invalid(
                "HitlResult.canonical exceeds max length $maxLength. Kind=$kind, length=${canonical.length}"
            )
        }

        return HitlCanonicalValidator.ValidationResult.valid()
    }
}
