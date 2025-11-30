package io.github.noailabs.spice.hitl.validation

import io.github.noailabs.spice.hitl.result.HitlResponseKind
import io.github.noailabs.spice.hitl.result.HitlResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HitlCanonicalValidatorTest {

    @AfterEach
    fun cleanup() {
        HitlResultValidators.resetToDefaults()
    }

    // ===========================================
    // DefaultHitlCanonicalValidator Tests
    // ===========================================

    @Test
    fun `default validator accepts non-blank canonical`() {
        val validator = DefaultHitlCanonicalValidator()
        val result = validator.validate("confirm_yes", HitlResponseKind.SINGLE)
        assertTrue(result.isValid)
    }

    @Test
    fun `default validator rejects blank canonical`() {
        val validator = DefaultHitlCanonicalValidator()
        val result = validator.validate("", HitlResponseKind.SINGLE)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("blank") == true)
    }

    @Test
    fun `default validator rejects whitespace-only canonical`() {
        val validator = DefaultHitlCanonicalValidator()
        val result = validator.validate("   ", HitlResponseKind.TEXT)
        assertFalse(result.isValid)
    }

    @Test
    fun `default validator rejects leading whitespace`() {
        val validator = DefaultHitlCanonicalValidator()
        val result = validator.validate(" confirm", HitlResponseKind.SINGLE)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("leading/trailing whitespace") == true)
        assertTrue(result.errorMessage?.contains("confirm") == true)
    }

    @Test
    fun `default validator rejects trailing whitespace`() {
        val validator = DefaultHitlCanonicalValidator()
        val result = validator.validate("confirm ", HitlResponseKind.SINGLE)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("leading/trailing whitespace") == true)
        assertTrue(result.errorMessage?.contains("confirm") == true)
    }

    // ===========================================
    // LenientHitlCanonicalValidator Tests
    // ===========================================

    @Test
    fun `lenient validator accepts empty string`() {
        val validator = LenientHitlCanonicalValidator()
        val result = validator.validate("", HitlResponseKind.SINGLE)
        assertTrue(result.isValid)
    }

    @Test
    fun `lenient validator accepts whitespace`() {
        val validator = LenientHitlCanonicalValidator()
        val result = validator.validate("   ", HitlResponseKind.TEXT)
        assertTrue(result.isValid)
    }

    // ===========================================
    // StrictHitlCanonicalValidator Tests
    // ===========================================

    @Test
    fun `strict validator accepts valid canonical`() {
        val validator = StrictHitlCanonicalValidator()
        val result = validator.validate("confirm_yes", HitlResponseKind.SINGLE)
        assertTrue(result.isValid)
    }

    @Test
    fun `strict validator rejects blank canonical`() {
        val validator = StrictHitlCanonicalValidator()
        val result = validator.validate("", HitlResponseKind.SINGLE)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("blank") == true)
    }

    @Test
    fun `strict validator rejects leading whitespace`() {
        val validator = StrictHitlCanonicalValidator()
        val result = validator.validate(" confirm", HitlResponseKind.SINGLE)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("whitespace") == true)
    }

    @Test
    fun `strict validator rejects trailing whitespace`() {
        val validator = StrictHitlCanonicalValidator()
        val result = validator.validate("confirm ", HitlResponseKind.SINGLE)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("whitespace") == true)
    }

    @Test
    fun `strict validator rejects canonical exceeding max length`() {
        val validator = StrictHitlCanonicalValidator(maxLength = 10)
        val result = validator.validate("12345678901", HitlResponseKind.SINGLE)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("max length") == true)
    }

    @Test
    fun `strict validator accepts canonical at max length`() {
        val validator = StrictHitlCanonicalValidator(maxLength = 10)
        val result = validator.validate("1234567890", HitlResponseKind.SINGLE)
        assertTrue(result.isValid)
    }

    // ===========================================
    // HitlResultValidators Global Holder Tests
    // ===========================================

    @Test
    fun `global validator is default by default`() {
        val validator = HitlResultValidators.canonicalValidator
        assertTrue(validator is DefaultHitlCanonicalValidator)
    }

    @Test
    fun `can swap to custom validator`() {
        HitlResultValidators.setCanonicalValidator(LenientHitlCanonicalValidator())
        val validator = HitlResultValidators.canonicalValidator
        assertTrue(validator is LenientHitlCanonicalValidator)
    }

    @Test
    fun `resetToDefaults restores default validator`() {
        HitlResultValidators.setCanonicalValidator(LenientHitlCanonicalValidator())
        HitlResultValidators.resetToDefaults()
        val validator = HitlResultValidators.canonicalValidator
        assertTrue(validator is DefaultHitlCanonicalValidator)
    }

    @Test
    fun `configureForTesting sets lenient validator`() {
        HitlResultValidators.configureForTesting()
        val validator = HitlResultValidators.canonicalValidator
        assertTrue(validator is LenientHitlCanonicalValidator)
    }

    @Test
    fun `configureForStrictMode sets strict validator`() {
        HitlResultValidators.configureForStrictMode(maxLength = 100)
        val validator = HitlResultValidators.canonicalValidator
        assertTrue(validator is StrictHitlCanonicalValidator)
    }

    // ===========================================
    // HitlResult Integration Tests
    // ===========================================

    @Test
    fun `HitlResult creation uses global validator - valid`() {
        assertDoesNotThrow {
            HitlResult.single("confirm_yes")
        }
    }

    @Test
    fun `HitlResult creation uses global validator - invalid with default`() {
        val exception = assertThrows<IllegalArgumentException> {
            HitlResult(
                kind = HitlResponseKind.SINGLE,
                canonical = "",
                selected = listOf("confirm_yes")
            )
        }
        assertTrue(exception.message?.contains("blank") == true)
    }

    @Test
    fun `HitlResult creation succeeds with lenient validator for empty canonical`() {
        HitlResultValidators.configureForTesting()

        // This should NOT throw with lenient validator
        // Note: We need to bypass the factory method which has its own validation
        assertDoesNotThrow {
            val validator = HitlResultValidators.canonicalValidator
            val result = validator.validate("", HitlResponseKind.SINGLE)
            assertTrue(result.isValid)
        }
    }

    // ===========================================
    // ValidationResult Tests
    // ===========================================

    @Test
    fun `ValidationResult valid factory`() {
        val result = HitlCanonicalValidator.ValidationResult.valid()
        assertTrue(result.isValid)
        assertEquals(null, result.errorMessage)
    }

    @Test
    fun `ValidationResult invalid factory`() {
        val result = HitlCanonicalValidator.ValidationResult.invalid("test error")
        assertFalse(result.isValid)
        assertEquals("test error", result.errorMessage)
    }

    @Test
    fun `ValidationResult throwIfInvalid does not throw for valid`() {
        val result = HitlCanonicalValidator.ValidationResult.valid()
        assertDoesNotThrow { result.throwIfInvalid() }
    }

    @Test
    fun `ValidationResult throwIfInvalid throws for invalid`() {
        val result = HitlCanonicalValidator.ValidationResult.invalid("test error")
        val exception = assertThrows<IllegalArgumentException> { result.throwIfInvalid() }
        assertEquals("test error", exception.message)
    }
}
