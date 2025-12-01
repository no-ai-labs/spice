package io.github.noailabs.spice.hitl.template

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for HitlTemplate allowFreeText (1.5.5+)
 *
 * Verifies allowFreeText in:
 * - HitlTemplateFlags data class
 * - HitlTemplate factory methods
 * - HitlTemplate.bind() metadata propagation
 */
class HitlTemplateAllowFreeTextTest {

    private val testOptions = listOf(
        HitlOption.simple("opt_a", "Option A"),
        HitlOption.simple("opt_b", "Option B")
    )

    // ===========================================
    // HitlTemplateFlags Tests
    // ===========================================

    @Test
    fun `HitlTemplateFlags DEFAULT has allowFreeText false`() {
        val flags = HitlTemplateFlags.DEFAULT
        assertFalse(flags.allowFreeText)
    }

    @Test
    fun `HitlTemplateFlags withFreeText() creates flags with allowFreeText true`() {
        val flags = HitlTemplateFlags.withFreeText()
        assertTrue(flags.allowFreeText)
    }

    @Test
    fun `HitlTemplateFlags copy() preserves allowFreeText`() {
        val original = HitlTemplateFlags(allowFreeText = true)
        val copied = original.copy(timeout = 5000L)
        assertTrue(copied.allowFreeText)
        assertEquals(5000L, copied.timeout)
    }

    // ===========================================
    // Factory Method Tests
    // ===========================================

    @Test
    fun `singleSelect without allowFreeText has flags false`() {
        val template = HitlTemplate.singleSelect(
            id = "test-single",
            prompt = "Choose one",
            options = testOptions
        )

        assertFalse(template.flags.allowFreeText)
    }

    @Test
    fun `singleSelect with allowFreeText true has flags true`() {
        val template = HitlTemplate.singleSelect(
            id = "test-single",
            prompt = "Choose one",
            options = testOptions,
            allowFreeText = true
        )

        assertTrue(template.flags.allowFreeText)
    }

    @Test
    fun `multiSelect without allowFreeText has flags false`() {
        val template = HitlTemplate.multiSelect(
            id = "test-multi",
            prompt = "Choose many",
            options = testOptions
        )

        assertFalse(template.flags.allowFreeText)
    }

    @Test
    fun `multiSelect with allowFreeText true has flags true`() {
        val template = HitlTemplate.multiSelect(
            id = "test-multi",
            prompt = "Choose many",
            options = testOptions,
            allowFreeText = true
        )

        assertTrue(template.flags.allowFreeText)
    }

    @Test
    fun `singleSelect allowFreeText parameter overrides existing flags`() {
        val customFlags = HitlTemplateFlags(allowFreeText = false, timeout = 10000L)
        val template = HitlTemplate.singleSelect(
            id = "test",
            prompt = "Choose",
            options = testOptions,
            flags = customFlags,
            allowFreeText = true  // Should override flags.allowFreeText
        )

        assertTrue(template.flags.allowFreeText)
        assertEquals(10000L, template.flags.timeout)  // Other flags preserved
    }

    // ===========================================
    // bind() Metadata Tests
    // ===========================================

    @Test
    fun `bind() without allowFreeText includes allow_free_text false in metadata`() {
        val template = HitlTemplate.singleSelect(
            id = "test",
            prompt = "Choose",
            options = testOptions,
            allowFreeText = false
        )

        val metadata = template.bind(
            context = emptyMap(),
            toolCallId = "tc-123",
            runId = "run-456",
            nodeId = "node-789"
        )

        // Always record allow_free_text (true or false) for strict mode support
        assertTrue(metadata.additionalMetadata.containsKey("allow_free_text"))
        assertEquals(false, metadata.additionalMetadata["allow_free_text"])
    }

    @Test
    fun `bind() with allowFreeText true includes allow_free_text in metadata`() {
        val template = HitlTemplate.singleSelect(
            id = "test",
            prompt = "Choose",
            options = testOptions,
            allowFreeText = true
        )

        val metadata = template.bind(
            context = emptyMap(),
            toolCallId = "tc-123",
            runId = "run-456",
            nodeId = "node-789"
        )

        assertTrue(metadata.additionalMetadata.containsKey("allow_free_text"))
        assertEquals(true, metadata.additionalMetadata["allow_free_text"])
    }

    @Test
    fun `bind() includes allowFreeText in flags map`() {
        val template = HitlTemplate.singleSelect(
            id = "test",
            prompt = "Choose",
            options = testOptions,
            allowFreeText = true
        )

        val metadata = template.bind(
            context = emptyMap(),
            toolCallId = "tc-123",
            runId = "run-456",
            nodeId = "node-789"
        )

        @Suppress("UNCHECKED_CAST")
        val flagsMap = metadata.additionalMetadata["flags"] as? Map<String, Any?>
        assertTrue(flagsMap != null)
        assertEquals(true, flagsMap["allowFreeText"])
    }

    // ===========================================
    // Template Kind Tests
    // ===========================================

    @Test
    fun `SINGLE_SELECT template has correct hitlType`() {
        val template = HitlTemplate.singleSelect(
            id = "test",
            prompt = "Choose",
            options = testOptions,
            allowFreeText = true
        )

        val metadata = template.bind(
            context = emptyMap(),
            toolCallId = "tc-123",
            runId = "run-456",
            nodeId = "node-789"
        )

        assertEquals("selection", metadata.hitlType)
    }

    @Test
    fun `MULTI_SELECT template has correct hitlType`() {
        val template = HitlTemplate.multiSelect(
            id = "test",
            prompt = "Choose many",
            options = testOptions,
            allowFreeText = true
        )

        val metadata = template.bind(
            context = emptyMap(),
            toolCallId = "tc-123",
            runId = "run-456",
            nodeId = "node-789"
        )

        assertEquals("multi_selection", metadata.hitlType)
    }

    // ===========================================
    // Template Context Rendering Tests
    // ===========================================

    @Test
    fun `bind() renders prompt template correctly with allowFreeText`() {
        val template = HitlTemplate.singleSelect(
            id = "test",
            prompt = "Hello {{name}}, please choose",
            options = testOptions,
            allowFreeText = true
        )

        val metadata = template.bind(
            context = mapOf("name" to "World"),
            toolCallId = "tc-123",
            runId = "run-456",
            nodeId = "node-789"
        )

        assertEquals("Hello World, please choose", metadata.prompt)
        assertTrue(metadata.additionalMetadata.containsKey("allow_free_text"))
    }
}
