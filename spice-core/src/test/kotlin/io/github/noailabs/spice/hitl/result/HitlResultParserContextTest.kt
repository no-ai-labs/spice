package io.github.noailabs.spice.hitl.result

import io.github.noailabs.spice.hitl.validation.HitlResultParserConfig
import io.github.noailabs.spice.hitl.validation.HitlResultValidators
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for HitlResultParser with ParseContext (1.5.5+)
 *
 * Verifies allowFreeText policy enforcement in parse() method.
 */
class HitlResultParserContextTest {

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
    // ParseContext Tests
    // ===========================================

    @Test
    fun `ParseContext LENIENT has null allowFreeText`() {
        val context = ParseContext.LENIENT
        assertNull(context.allowFreeText)
        assertNull(context.selectionType)
    }

    @Test
    fun `ParseContext strict creates context with allowFreeText false`() {
        val context = ParseContext.strict("single")
        assertEquals(false, context.allowFreeText)
        assertEquals("single", context.selectionType)
    }

    @Test
    fun `ParseContext allowFreeText creates context with allowFreeText true`() {
        val context = ParseContext.allowFreeText("multiple")
        assertEquals(true, context.allowFreeText)
        assertEquals("multiple", context.selectionType)
    }

    @Test
    fun `ParseContext fromMetadata returns null when both params null`() {
        val context = ParseContext.fromMetadata(null, null)
        assertNull(context)
    }

    @Test
    fun `ParseContext fromMetadata returns context when allowFreeText provided`() {
        val context = ParseContext.fromMetadata(true, null)
        assertNotNull(context)
        assertEquals(true, context.allowFreeText)
        assertNull(context.selectionType)
    }

    @Test
    fun `ParseContext fromMetadata returns context when selectionType provided`() {
        val context = ParseContext.fromMetadata(null, "single")
        assertNotNull(context)
        assertNull(context.allowFreeText)
        assertEquals("single", context.selectionType)
    }

    // ===========================================
    // Parser with Context Tests
    // ===========================================

    @Test
    fun `context null with text-only returns HitlResult text (lenient mode)`() {
        // TC 7: context=null + text-only → HitlResult.text (기존 관대 동작)
        val data = mapOf("text" to "custom input")

        val result = HitlResultParser.parse(data, null, null)

        assertNotNull(result)
        assertEquals(HitlResponseKind.TEXT, result.kind)
        assertEquals("custom input", result.canonical)
    }

    @Test
    fun `context allowFreeText false with text-only rejects for single selection`() {
        // TC 8: context.allowFreeText=false + text-only → null 반환 (거부)
        val data = mapOf("text" to "custom input")
        val context = ParseContext.strict("single")

        val result = HitlResultParser.parse(data, null, context)

        assertNull(result) // Rejected - should route to DECISION otherwise
    }

    @Test
    fun `context allowFreeText false with text-only rejects for multiple selection`() {
        // TC 8 variant: multiple selection
        val data = mapOf("text" to "눈먼고래")
        val context = ParseContext.strict("multiple")

        val result = HitlResultParser.parse(data, null, context)

        assertNull(result) // Rejected
    }

    @Test
    fun `context allowFreeText true with text-only returns HitlResult text`() {
        // TC 9: context.allowFreeText=true + text-only → HitlResult.text
        val data = mapOf("text" to "눈먼고래")
        val context = ParseContext.allowFreeText("single")

        val result = HitlResultParser.parse(data, null, context)

        assertNotNull(result)
        assertEquals(HitlResponseKind.TEXT, result.kind)
        assertEquals("눈먼고래", result.canonical)
    }

    @Test
    fun `context allowFreeText false with selected_ids parses normally`() {
        // TC 10: context.allowFreeText=false + selected_ids 존재 → 정상 파싱
        val data = mapOf(
            "selected_ids" to listOf("option_a"),
            "text" to "I chose A"
        )
        val context = ParseContext.strict("single")

        val result = HitlResultParser.parse(data, null, context)

        assertNotNull(result)
        assertEquals(HitlResponseKind.SINGLE, result.kind)
        assertEquals("option_a", result.canonical)
        assertEquals("I chose A", result.rawText)
    }

    @Test
    fun `multiSelect with selected_ids parses as MULTI`() {
        // TC 11: multiSelect + selected_ids → HitlResult.multi 정상
        val data = mapOf(
            "selected_ids" to listOf("feature_a", "feature_b"),
            "text" to "Both features"
        )
        val context = ParseContext.allowFreeText("multiple")

        val result = HitlResultParser.parse(data, null, context)

        assertNotNull(result)
        assertEquals(HitlResponseKind.MULTI, result.kind)
        assertEquals("feature_a,feature_b", result.canonical)
        assertEquals(listOf("feature_a", "feature_b"), result.selected)
    }

    @Test
    fun `context with non-selection type does not reject text-only`() {
        // allowFreeText=false but selectionType is not single/multiple
        val data = mapOf("text" to "some text")
        val context = ParseContext(allowFreeText = false, selectionType = "quantity")

        val result = HitlResultParser.parse(data, null, context)

        // Should NOT reject - only single/multiple selection types are affected
        assertNotNull(result)
        assertEquals(HitlResponseKind.TEXT, result.kind)
    }

    @Test
    fun `context with null selectionType does not reject text-only`() {
        // allowFreeText=false but selectionType is null
        val data = mapOf("text" to "some text")
        val context = ParseContext(allowFreeText = false, selectionType = null)

        val result = HitlResultParser.parse(data, null, context)

        // Should NOT reject - selectionType not specified
        assertNotNull(result)
        assertEquals(HitlResponseKind.TEXT, result.kind)
    }

    // ===========================================
    // Regression Tests
    // ===========================================

    @Test
    fun `existing parse behavior without context unchanged`() {
        // TC 13: 기존 동작 유지 확인

        // Single selection
        val singleData = mapOf("selected_ids" to listOf("yes"))
        val singleResult = HitlResultParser.parse(singleData)
        assertNotNull(singleResult)
        assertEquals(HitlResponseKind.SINGLE, singleResult.kind)

        // Multi selection
        val multiData = mapOf("selected_ids" to listOf("a", "b"))
        val multiResult = HitlResultParser.parse(multiData)
        assertNotNull(multiResult)
        assertEquals(HitlResponseKind.MULTI, multiResult.kind)

        // Text only (should still work - lenient)
        val textData = mapOf("text" to "hello")
        val textResult = HitlResultParser.parse(textData)
        assertNotNull(textResult)
        assertEquals(HitlResponseKind.TEXT, textResult.kind)
    }

    @Test
    fun `selected_option legacy format still works with context`() {
        val data = mapOf("selected_option" to "confirm_yes")
        val context = ParseContext.strict("single")

        val result = HitlResultParser.parse(data, null, context)

        assertNotNull(result)
        assertEquals(HitlResponseKind.SINGLE, result.kind)
        assertEquals("confirm_yes", result.canonical)
    }
}
