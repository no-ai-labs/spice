package io.github.noailabs.spice.toolspec

import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.CommType

/**
 * 🔧 ToolCall Extensions for Comm
 *
 * Adds OpenAI Tool Spec support to Spice Comm
 *
 * **Core Features:**
 * - Single `tool_calls` field for all tool invocations
 * - Immutable Comm pattern with `withToolCall()`
 * - Automatic legacy field migration
 * - Type-safe tool call retrieval
 *
 * **Usage Examples:**
 * ```kotlin
 * // Add tool call
 * val comm = comm.withToolCall(
 *     OAIToolCall.selection(
 *         items = listOf(...),
 *         promptMessage = "Select an option"
 *     )
 * )
 *
 * // Retrieve tool calls
 * val toolCalls = comm.getToolCalls()
 * val selectionCall = comm.findToolCall("request_user_selection")
 *
 * // Query
 * if (comm.hasToolCalls()) {
 *     val names = comm.getToolCallNames()
 * }
 * ```
 *
 * **Legacy Migration:**
 * - Automatically detects `menu_text`, `workflow_message`, etc.
 * - Transparently converts to `tool_calls` on read
 * - Use `cleanupLegacyFields()` to remove after migration
 *
 * @author Spice Framework
 * @since 0.10.0
 */

/**
 * ToolCall storage key in Comm.data
 */
const val TOOL_CALLS_KEY = "tool_calls"

/**
 * Legacy field names for automatic migration
 */
object LegacyFieldNames {
    const val MENU_TEXT = "menu_text"
    const val WORKFLOW_MESSAGE = "workflow_message"
    const val STRUCTURED_DATA = "structured_data"
    const val NEEDS_USER_SELECTION = "needs_user_selection"
    const val NEEDS_USER_CONFIRMATION = "needs_user_confirmation"
    const val SELECTED_ITEMS = "selected_items"
    const val USER_SELECTION = "user_selection"
    const val CONFIRMATION_REQUIRED = "confirmation_required"
}

// =====================================
// Core Extension Functions
// =====================================

/**
 * Add ToolCall to Comm (Immutable Pattern)
 *
 * Returns a new Comm with the tool call added to existing tool calls.
 *
 * Example:
 * ```kotlin
 * val updatedComm = comm.withToolCall(
 *     OAIToolCall.selection(items, "Select an item")
 * )
 * ```
 *
 * @param toolCall The tool call to add
 * @return New Comm with the tool call added
 */
fun Comm.withToolCall(toolCall: OAIToolCall): Comm {
    val existingToolCalls = getToolCalls()
    val newToolCalls = existingToolCalls + toolCall

    return this.copy(
        data = this.data + (TOOL_CALLS_KEY to newToolCalls),
        type = if (this.type == CommType.TEXT) CommType.TOOL_CALL else this.type
    )
}

/**
 * Add multiple ToolCalls to Comm
 *
 * Example:
 * ```kotlin
 * val updatedComm = comm.withToolCalls(listOf(
 *     OAIToolCall.selection(...),
 *     OAIToolCall.confirmation(...)
 * ))
 * ```
 *
 * @param toolCalls List of tool calls to add
 * @return New Comm with the tool calls added
 */
fun Comm.withToolCalls(toolCalls: List<OAIToolCall>): Comm {
    if (toolCalls.isEmpty()) return this

    val existingToolCalls = getToolCalls()
    val newToolCalls = existingToolCalls + toolCalls

    return this.copy(
        data = this.data + (TOOL_CALLS_KEY to newToolCalls),
        type = if (this.type == CommType.TEXT) CommType.TOOL_CALL else this.type
    )
}

/**
 * Get ToolCalls from Comm
 *
 * Automatically migrates legacy fields if present.
 *
 * Example:
 * ```kotlin
 * val toolCalls = comm.getToolCalls()
 * toolCalls.forEach { toolCall ->
 *     println("Function: ${toolCall.getFunctionName()}")
 * }
 * ```
 *
 * @return List of tool calls (empty if none)
 */
fun Comm.getToolCalls(): List<OAIToolCall> {
    // 1. Check for tool_calls field
    @Suppress("UNCHECKED_CAST")
    val toolCalls = this.data[TOOL_CALLS_KEY] as? List<OAIToolCall>

    if (toolCalls != null && toolCalls.isNotEmpty()) {
        return toolCalls
    }

    // 2. Auto-migrate legacy fields
    val migratedToolCalls = migrateLegacyFields(this)

    // Log migration (optional, removed logger dependency)
    // Can be added back if logging framework is needed

    return migratedToolCalls
}

/**
 * Count ToolCalls
 *
 * @return Number of tool calls
 */
fun Comm.countToolCalls(): Int = getToolCalls().size

/**
 * Check if Comm has ToolCalls
 *
 * @return true if at least one tool call exists
 */
fun Comm.hasToolCalls(): Boolean = getToolCalls().isNotEmpty()

/**
 * Find ToolCall by function name
 *
 * Example:
 * ```kotlin
 * val selectionCall = comm.findToolCall("request_user_selection")
 * if (selectionCall != null) {
 *     val items = selectionCall.function.getArgumentList("items")
 * }
 * ```
 *
 * @param functionName The function name to search for
 * @return First matching tool call or null
 */
fun Comm.findToolCall(functionName: String): OAIToolCall? {
    return getToolCalls().firstOrNull { it.getFunctionName() == functionName }
}

/**
 * Find all ToolCalls by function name
 *
 * @param functionName The function name to search for
 * @return All matching tool calls
 */
fun Comm.findToolCalls(functionName: String): List<OAIToolCall> {
    return getToolCalls().filter { it.getFunctionName() == functionName }
}

/**
 * Get all function names from ToolCalls
 *
 * Example:
 * ```kotlin
 * val names = comm.getToolCallNames()
 * // ["request_user_selection", "workflow_completed"]
 * ```
 *
 * @return List of function names
 */
fun Comm.getToolCallNames(): List<String> {
    return getToolCalls().map { it.getFunctionName() }
}

/**
 * Check if Comm has specific tool call
 *
 * @param functionName The function name to check
 * @return true if tool call with that name exists
 */
fun Comm.hasToolCall(functionName: String): Boolean {
    return findToolCall(functionName) != null
}

// =====================================
// Legacy Migration
// =====================================

/**
 * Migrate legacy fields to tool_calls
 *
 * Automatically detects and converts:
 * - menu_text → request_user_selection
 * - workflow_message + needs_user_confirmation → request_user_confirmation
 * - structured_data → appropriate tool call based on content
 */
private fun migrateLegacyFields(comm: Comm): List<OAIToolCall> {
    val toolCalls = mutableListOf<OAIToolCall>()

    // Migration 1: menu_text → request_user_selection
    val menuText = comm.data[LegacyFieldNames.MENU_TEXT]?.toString()
    if (menuText != null) {
        // Try to parse as items if it's JSON-like
        val items = tryParseMenuTextAsItems(menuText)
        toolCalls.add(
            OAIToolCall.selection(
                items = items,
                promptMessage = menuText,
                selectionType = "legacy_menu",
                metadata = mapOf("legacy_field" to "menu_text")
            )
        )
    }

    // Migration 2: workflow_message + needs_user_confirmation → request_user_confirmation
    val workflowMessage = comm.data[LegacyFieldNames.WORKFLOW_MESSAGE]?.toString()
    val needsConfirmation = comm.data[LegacyFieldNames.NEEDS_USER_CONFIRMATION]?.toString()?.toBoolean() ?: false

    if (workflowMessage != null && needsConfirmation) {
        toolCalls.add(
            OAIToolCall.confirmation(
                message = workflowMessage,
                options = listOf("Yes", "No"),
                confirmationType = "legacy_workflow",
                metadata = mapOf("legacy_field" to "workflow_message")
            )
        )
    } else if (workflowMessage != null) {
        // Just a message, treat as completion
        toolCalls.add(
            OAIToolCall.completion(
                message = workflowMessage,
                metadata = mapOf("legacy_field" to "workflow_message")
            )
        )
    }

    // Migration 3: structured_data → tool_message
    val structuredData = comm.data[LegacyFieldNames.STRUCTURED_DATA]
    if (structuredData != null && toolCalls.isEmpty()) {
        toolCalls.add(
            OAIToolCall.toolMessage(
                message = structuredData.toString(),
                toolName = "legacy_structured_data",
                isIntermediate = true,
                metadata = mapOf("legacy_field" to "structured_data")
            )
        )
    }

    // Migration 4: needs_user_selection → request_user_selection (empty items)
    val needsSelection = comm.data[LegacyFieldNames.NEEDS_USER_SELECTION]?.toString()?.toBoolean() ?: false
    if (needsSelection && toolCalls.isEmpty()) {
        toolCalls.add(
            OAIToolCall.selection(
                items = emptyList(),
                promptMessage = "Selection required",
                selectionType = "legacy_needs_selection",
                metadata = mapOf("legacy_field" to "needs_user_selection")
            )
        )
    }

    return toolCalls
}

/**
 * Try to parse menu_text as structured items
 */
private fun tryParseMenuTextAsItems(menuText: String): List<Map<String, Any?>> {
    // Simple heuristic: if it looks like a list, create items
    val lines = menuText.lines().filter { it.isNotBlank() }

    if (lines.size > 1) {
        return lines.mapIndexed { index, line ->
            mapOf<String, Any?>(
                "id" to "legacy_item_$index",
                "name" to line.trim(),
                "description" to null
            )
        }
    }

    // Otherwise, single item
    return listOf(
        mapOf<String, Any?>(
            "id" to "legacy_item_0",
            "name" to menuText,
            "description" to null
        )
    )
}

/**
 * Cleanup legacy fields after migration
 *
 * Removes legacy fields if tool_calls exist.
 *
 * Example:
 * ```kotlin
 * val cleanComm = comm.cleanupLegacyFields()
 * ```
 *
 * @return New Comm with legacy fields removed
 */
fun Comm.cleanupLegacyFields(): Comm {
    if (!hasToolCalls()) {
        return this
    }

    val cleanedData = this.data.toMutableMap()

    // Remove all known legacy fields
    cleanedData.remove(LegacyFieldNames.MENU_TEXT)
    cleanedData.remove(LegacyFieldNames.WORKFLOW_MESSAGE)
    cleanedData.remove(LegacyFieldNames.STRUCTURED_DATA)
    cleanedData.remove(LegacyFieldNames.NEEDS_USER_SELECTION)
    cleanedData.remove(LegacyFieldNames.NEEDS_USER_CONFIRMATION)
    cleanedData.remove(LegacyFieldNames.SELECTED_ITEMS)
    cleanedData.remove(LegacyFieldNames.USER_SELECTION)
    cleanedData.remove(LegacyFieldNames.CONFIRMATION_REQUIRED)

    return this.copy(data = cleanedData)
}

/**
 * Check if Comm has legacy fields
 *
 * @return true if any legacy field exists
 */
fun Comm.hasLegacyFields(): Boolean {
    return listOf(
        LegacyFieldNames.MENU_TEXT,
        LegacyFieldNames.WORKFLOW_MESSAGE,
        LegacyFieldNames.STRUCTURED_DATA,
        LegacyFieldNames.NEEDS_USER_SELECTION,
        LegacyFieldNames.NEEDS_USER_CONFIRMATION
    ).any { this.data.containsKey(it) }
}

// =====================================
// Statistics & Utilities
// =====================================

/**
 * Get ToolCall statistics
 *
 * Example:
 * ```kotlin
 * val stats = comm.getToolCallStats()
 * println("Total: ${stats.totalToolCalls}")
 * println("Functions: ${stats.functionCounts}")
 * println("Has legacy: ${stats.hasLegacyFields}")
 * ```
 *
 * @return ToolCall statistics
 */
fun Comm.getToolCallStats(): ToolCallStats {
    val toolCalls = getToolCalls()

    val functionCounts = toolCalls
        .groupingBy { it.getFunctionName() }
        .eachCount()

    return ToolCallStats(
        totalToolCalls = toolCalls.size,
        functionCounts = functionCounts,
        hasLegacyFields = hasLegacyFields()
    )
}

/**
 * ToolCall Statistics
 */
data class ToolCallStats(
    val totalToolCalls: Int,
    val functionCounts: Map<String, Int>,
    val hasLegacyFields: Boolean
) {
    override fun toString(): String {
        return """
        ToolCallStats(
            total=$totalToolCalls,
            functions=$functionCounts,
            hasLegacy=$hasLegacyFields
        )
        """.trimIndent()
    }
}

/**
 * Convert Comm with tool_calls to formatted string
 *
 * @return Human-readable representation
 */
fun Comm.toolCallsToString(): String {
    val toolCalls = getToolCalls()
    if (toolCalls.isEmpty()) return "No tool calls"

    return toolCalls.joinToString("\n") { toolCall ->
        val args = toolCall.function.arguments.entries.joinToString(", ") { (k, v) ->
            "$k=$v"
        }
        "${toolCall.getFunctionName()}($args)"
    }
}
