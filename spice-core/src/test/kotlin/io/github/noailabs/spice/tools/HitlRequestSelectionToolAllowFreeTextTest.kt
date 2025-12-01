package io.github.noailabs.spice.tools

import io.github.noailabs.spice.GraphContext
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolResultStatus
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.hitl.result.HITLOption
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for HitlRequestSelectionTool allowFreeText (1.5.5+)
 *
 * Verifies allowFreeText parameter in:
 * - params() companion method
 * - execute() metadata propagation
 */
class HitlRequestSelectionToolAllowFreeTextTest {

    private val tool = HitlRequestSelectionTool()

    private val testOptions = listOf(
        HITLOption(id = "option_a", label = "Option A"),
        HITLOption(id = "option_b", label = "Option B")
    )

    private fun createContext(): ToolContext {
        return ToolContext(
            agentId = "test-agent",
            graph = GraphContext(
                runId = "run-123",
                nodeId = "node-456",
                graphId = "graph-789"
            )
        )
    }

    // ===========================================
    // params() Tests
    // ===========================================

    @Test
    fun `params() without allowFreeText includes key with false`() {
        val params = HitlRequestSelectionTool.params(
            prompt = "Select an option",
            options = testOptions
        )

        // Always record allow_free_text (true or false) for strict mode support
        assertTrue(params.containsKey("allow_free_text"))
        assertEquals(false, params["allow_free_text"])
    }

    @Test
    fun `params() with allowFreeText false includes key with false`() {
        val params = HitlRequestSelectionTool.params(
            prompt = "Select an option",
            options = testOptions,
            allowFreeText = false
        )

        assertTrue(params.containsKey("allow_free_text"))
        assertEquals(false, params["allow_free_text"])
    }

    @Test
    fun `params() with allowFreeText true includes key`() {
        val params = HitlRequestSelectionTool.params(
            prompt = "Select an option",
            options = testOptions,
            allowFreeText = true
        )

        assertTrue(params.containsKey("allow_free_text"))
        assertEquals(true, params["allow_free_text"])
    }

    // ===========================================
    // execute() Tests
    // ===========================================

    @Test
    fun `execute() without allow_free_text returns metadata with false`() = runTest {
        val params = mapOf(
            "prompt" to "Select",
            "options" to testOptions,
            "selection_type" to "single"
        )

        val result = tool.execute(params, createContext())

        assertTrue(result is SpiceResult.Success)
        val toolResult = (result as SpiceResult.Success).value
        assertEquals(ToolResultStatus.WAITING_HITL, toolResult.status)

        // Always record allow_free_text (true or false) for strict mode support
        @Suppress("UNCHECKED_CAST")
        val metadata = toolResult.metadata as Map<String, Any?>
        assertTrue(metadata.containsKey("allow_free_text"))
        assertEquals(false, metadata["allow_free_text"])
    }

    @Test
    fun `execute() with allow_free_text false returns metadata with false`() = runTest {
        val params = mapOf(
            "prompt" to "Select",
            "options" to testOptions,
            "selection_type" to "single",
            "allow_free_text" to false
        )

        val result = tool.execute(params, createContext())

        assertTrue(result is SpiceResult.Success)
        val toolResult = (result as SpiceResult.Success).value
        assertEquals(ToolResultStatus.WAITING_HITL, toolResult.status)

        @Suppress("UNCHECKED_CAST")
        val metadata = toolResult.metadata as Map<String, Any?>
        assertTrue(metadata.containsKey("allow_free_text"))
        assertEquals(false, metadata["allow_free_text"])
    }

    @Test
    fun `execute() with allow_free_text true returns metadata with key`() = runTest {
        val params = mapOf(
            "prompt" to "Select",
            "options" to testOptions,
            "selection_type" to "single",
            "allow_free_text" to true
        )

        val result = tool.execute(params, createContext())

        assertTrue(result is SpiceResult.Success)
        val toolResult = (result as SpiceResult.Success).value
        assertEquals(ToolResultStatus.WAITING_HITL, toolResult.status)

        @Suppress("UNCHECKED_CAST")
        val metadata = toolResult.metadata as Map<String, Any?>
        assertTrue(metadata.containsKey("allow_free_text"))
        assertEquals(true, metadata["allow_free_text"])
    }

    @Test
    fun `execute() with multiple selection and allow_free_text true`() = runTest {
        val params = mapOf(
            "prompt" to "Select multiple",
            "options" to testOptions,
            "selection_type" to "multiple",
            "allow_free_text" to true
        )

        val result = tool.execute(params, createContext())

        assertTrue(result is SpiceResult.Success)
        val toolResult = (result as SpiceResult.Success).value
        assertEquals(ToolResultStatus.WAITING_HITL, toolResult.status)

        @Suppress("UNCHECKED_CAST")
        val metadata = toolResult.metadata as Map<String, Any?>
        assertEquals("multiple", metadata["selection_type"])
        assertEquals(true, metadata["allow_free_text"])
    }

    // ===========================================
    // Integration Test: End-to-End Flow
    // ===========================================

    @Test
    fun `full flow with allowFreeText via params() helper`() = runTest {
        // Use the params() helper to build parameters
        val params = HitlRequestSelectionTool.params(
            prompt = "Choose your favorite",
            options = testOptions,
            selectionType = "single",
            allowFreeText = true
        )

        val result = tool.execute(params, createContext())

        assertTrue(result is SpiceResult.Success)
        val toolResult = (result as SpiceResult.Success).value
        assertEquals(ToolResultStatus.WAITING_HITL, toolResult.status)

        // ToolResult.waitingHitl() sets hitl_tool_call_id in metadata
        @Suppress("UNCHECKED_CAST")
        val metadata = toolResult.metadata as Map<String, Any?>
        assertNotNull(metadata["hitl_tool_call_id"])
        assertEquals(true, metadata["allow_free_text"])
        assertEquals("single", metadata["selection_type"])
    }
}
