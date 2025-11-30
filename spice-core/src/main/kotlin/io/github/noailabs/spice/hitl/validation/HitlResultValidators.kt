package io.github.noailabs.spice.hitl.validation

/**
 * Global registry for HitlResult validators.
 *
 * Provides a thread-safe way to swap validator implementations at runtime.
 *
 * **Thread Safety**: Uses @Volatile for read visibility and synchronized block
 * for atomic updates.
 *
 * **Usage:**
 * ```kotlin
 * // Default behavior (DefaultHitlCanonicalValidator)
 * HitlResultValidators.canonicalValidator.validate("confirm", HitlResponseKind.SINGLE)
 *
 * // Swap to lenient mode for testing
 * HitlResultValidators.setCanonicalValidator(LenientHitlCanonicalValidator())
 *
 * // Reset to default
 * HitlResultValidators.resetToDefaults()
 * ```
 *
 * @since Spice 1.3.5
 */
object HitlResultValidators {

    private val DEFAULT_CANONICAL_VALIDATOR = DefaultHitlCanonicalValidator()

    @Volatile
    private var _canonicalValidator: HitlCanonicalValidator = DEFAULT_CANONICAL_VALIDATOR

    private val lock = Any()

    /**
     * Current canonical validator.
     *
     * Thread-safe read via @Volatile.
     */
    val canonicalValidator: HitlCanonicalValidator
        get() = _canonicalValidator

    /**
     * Set a custom canonical validator.
     *
     * Thread-safe update via synchronized block.
     *
     * @param validator The validator to use
     */
    fun setCanonicalValidator(validator: HitlCanonicalValidator) {
        synchronized(lock) {
            _canonicalValidator = validator
        }
    }

    /**
     * Reset all validators to default implementations.
     *
     * Useful for test cleanup.
     */
    fun resetToDefaults() {
        synchronized(lock) {
            _canonicalValidator = DEFAULT_CANONICAL_VALIDATOR
        }
    }

    /**
     * Configure for lenient testing mode.
     *
     * Sets the canonical validator to [LenientHitlCanonicalValidator]
     * which allows any canonical value including empty.
     */
    fun configureForTesting() {
        setCanonicalValidator(LenientHitlCanonicalValidator())
    }

    /**
     * Configure for strict production mode.
     *
     * Sets the canonical validator to [StrictHitlCanonicalValidator]
     * with additional rules (no whitespace, max length).
     *
     * @param maxLength Maximum allowed canonical length (default: 1024)
     */
    fun configureForStrictMode(maxLength: Int = 1024) {
        setCanonicalValidator(StrictHitlCanonicalValidator(maxLength))
    }
}
