package io.github.noailabs.spice.hitl.validation

import io.github.noailabs.spice.hitl.result.HitlResultParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HitlResultParserOptionsTest {

    @BeforeEach
    fun setup() {
        HitlResultParserConfig.resetToDefaults()
        HitlResultValidators.resetToDefaults()
    }

    @AfterEach
    fun cleanup() {
        HitlResultParserConfig.resetToDefaults()
        HitlResultValidators.resetToDefaults()
    }

    // ===========================================
    // HitlResultParserOptions Tests
    // ===========================================

    @Test
    fun `default options have expected values`() {
        val options = HitlResultParserOptions.DEFAULT
        assertEquals(HitlLogLevel.DEBUG, options.successLogLevel)
        assertEquals(HitlLogLevel.DEBUG, options.unknownFieldLogLevel)
        assertEquals(HitlLogLevel.WARN, options.emptyCanonicalLogLevel)
        assertEquals(HitlLogLevel.WARN, options.parseFailureLogLevel)
    }

    @Test
    fun `quiet options have minimal logging`() {
        val options = HitlResultParserOptions.quiet()
        assertEquals(HitlLogLevel.OFF, options.successLogLevel)
        assertEquals(HitlLogLevel.OFF, options.unknownFieldLogLevel)
        assertEquals(HitlLogLevel.WARN, options.emptyCanonicalLogLevel)
        assertEquals(HitlLogLevel.ERROR, options.parseFailureLogLevel)
    }

    @Test
    fun `verbose options have detailed logging`() {
        val options = HitlResultParserOptions.verbose()
        assertEquals(HitlLogLevel.DEBUG, options.successLogLevel)
        assertEquals(HitlLogLevel.INFO, options.unknownFieldLogLevel)
    }

    @Test
    fun `production options are balanced`() {
        val options = HitlResultParserOptions.production()
        assertEquals(HitlLogLevel.TRACE, options.successLogLevel)
        assertEquals(HitlLogLevel.ERROR, options.parseFailureLogLevel)
    }

    @Test
    fun `silent options disable all logging`() {
        val options = HitlResultParserOptions.silent()
        assertEquals(HitlLogLevel.OFF, options.successLogLevel)
        assertEquals(HitlLogLevel.OFF, options.unknownFieldLogLevel)
        assertEquals(HitlLogLevel.OFF, options.emptyCanonicalLogLevel)
        assertEquals(HitlLogLevel.OFF, options.parseFailureLogLevel)
    }

    // ===========================================
    // HitlResultParserConfig Tests
    // ===========================================

    @Test
    fun `config starts with default options`() {
        val options = HitlResultParserConfig.options
        assertEquals(HitlResultParserOptions.DEFAULT, options)
    }

    @Test
    fun `can set options directly`() {
        val customOptions = HitlResultParserOptions(
            successLogLevel = HitlLogLevel.INFO,
            parseFailureLogLevel = HitlLogLevel.ERROR
        )
        HitlResultParserConfig.setOptions(customOptions)
        assertEquals(customOptions, HitlResultParserConfig.options)
    }

    @Test
    fun `can update options with transform function`() {
        HitlResultParserConfig.update { current ->
            current.copy(successLogLevel = HitlLogLevel.INFO)
        }
        assertEquals(HitlLogLevel.INFO, HitlResultParserConfig.options.successLogLevel)
        // Other fields unchanged
        assertEquals(HitlLogLevel.WARN, HitlResultParserConfig.options.parseFailureLogLevel)
    }

    @Test
    fun `resetToDefaults restores default options`() {
        HitlResultParserConfig.setOptions(HitlResultParserOptions.silent())
        HitlResultParserConfig.resetToDefaults()
        assertEquals(HitlResultParserOptions.DEFAULT, HitlResultParserConfig.options)
    }

    // ===========================================
    // Parser Integration Tests
    // ===========================================

    @Test
    fun `parser returns null for unrecognized format`() {
        val result = HitlResultParser.parse(mapOf("unknown" to "data"))
        assertNull(result)
    }

    @Test
    fun `parser returns result for valid selected_ids`() {
        val result = HitlResultParser.parse(mapOf("selected_ids" to listOf("option_a")))
        assertNotNull(result)
        assertEquals("option_a", result.canonical)
    }

    @Test
    fun `parseOrFallback returns fallback for unrecognized format`() {
        val result = HitlResultParser.parseOrFallback(
            mapOf("unknown" to "data"),
            fallbackText = "fallback_value"
        )
        assertEquals("fallback_value", result.canonical)
    }

    @Test
    fun `parser with OFF log level still returns correct result`() {
        // Set all log levels to OFF
        HitlResultParserConfig.setOptions(HitlResultParserOptions.silent())

        // Parser should still work correctly
        val result = HitlResultParser.parse(mapOf("selected_ids" to listOf("option_a")))
        assertNotNull(result)
        assertEquals("option_a", result.canonical)
    }

    @Test
    fun `parseOrFallback with OFF log level still works`() {
        HitlResultParserConfig.setOptions(HitlResultParserOptions.silent())

        // Should still return fallback for unrecognized data
        val result = HitlResultParser.parseOrFallback(
            mapOf("unknown" to "data"),
            fallbackText = "fallback"
        )
        assertEquals("fallback", result.canonical)
    }

    @Test
    fun `parser handles empty canonical in already-formed HitlResult gracefully`() {
        // This should fail due to validation, but return null (not throw) from parser
        val data = mapOf(
            "kind" to "SINGLE",
            "canonical" to ""  // Empty canonical
        )

        // Parser should catch the validation error and return null
        val result = HitlResultParser.parse(data)
        assertNull(result)
    }

    // ===========================================
    // HitlLogLevel Tests
    // ===========================================

    @Test
    fun `HitlLogLevel has all expected values`() {
        val levels = HitlLogLevel.entries
        assertTrue(levels.contains(HitlLogLevel.TRACE))
        assertTrue(levels.contains(HitlLogLevel.DEBUG))
        assertTrue(levels.contains(HitlLogLevel.INFO))
        assertTrue(levels.contains(HitlLogLevel.WARN))
        assertTrue(levels.contains(HitlLogLevel.ERROR))
        assertTrue(levels.contains(HitlLogLevel.OFF))
    }
}
