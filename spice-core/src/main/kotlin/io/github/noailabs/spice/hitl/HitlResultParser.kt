package io.github.noailabs.spice.hitl

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * HITL Result Parser
 *
 * Normalizes various HITL response formats into a unified [HitlResult].
 *
 * **Supported Input Formats:**
 *
 * 1. **UI Single Selection:**
 *    ```json
 *    { "selected_ids": ["bbq_set"], "text": "BBQ 세트요" }
 *    ```
 *
 * 2. **UI Multi Selection:**
 *    ```json
 *    { "selected_ids": ["bbq_set", "campfire"], "text": "둘 다요" }
 *    ```
 *
 * 3. **UI Quantity Selection:**
 *    ```json
 *    { "quantities": { "bbq_set": 2, "campfire": 1 } }
 *    ```
 *
 * 4. **LLM Structured Response:**
 *    ```json
 *    { "selected_option": "confirm_yes" }
 *    ```
 *
 * 5. **Text Input:**
 *    ```json
 *    { "text": "네, 맞아요" }
 *    ```
 *
 * 6. **Already Normalized HitlResult:**
 *    ```json
 *    { "kind": "SINGLE", "canonical": "confirm_yes", ... }
 *    ```
 *
 * @since Spice 1.3.4
 */
object HitlResultParser {

    /**
     * Parse HITL response data into a normalized HitlResult
     *
     * @param data Response data map from various sources (UI, LLM, etc.)
     * @param toolCallId Optional tool call ID for tracing
     * @return Normalized HitlResult, or null if parsing fails
     */
    @Suppress("UNCHECKED_CAST")
    fun parse(data: Map<String, Any?>, toolCallId: String? = null): HitlResult? {
        logger.debug { "[HitlResultParser] Parsing data: ${data.keys}" }

        // Case 0: Already a HitlResult
        if (isHitlResultMap(data)) {
            return try {
                HitlResult.fromMap(data)
            } catch (e: Exception) {
                logger.warn { "[HitlResultParser] Failed to parse existing HitlResult: ${e.message}" }
                null
            }
        }

        // Case 0b: Nested hitl object
        val nestedHitl = data[HitlResult.DATA_KEY] as? Map<String, Any?>
        if (nestedHitl != null && isHitlResultMap(nestedHitl)) {
            return try {
                HitlResult.fromMap(nestedHitl)
            } catch (e: Exception) {
                logger.warn { "[HitlResultParser] Failed to parse nested HitlResult: ${e.message}" }
                null
            }
        }

        // Case 1: selected_ids (UI single/multi selection)
        val selectedIds = extractSelectedIds(data)
        if (selectedIds != null && selectedIds.isNotEmpty()) {
            val rawText = extractText(data)
            return if (selectedIds.size == 1) {
                logger.debug { "[HitlResultParser] Parsed as SINGLE: ${selectedIds.first()}" }
                HitlResult.single(selectedIds.first(), rawText, toolCallId)
            } else {
                logger.debug { "[HitlResultParser] Parsed as MULTI: $selectedIds" }
                HitlResult.multi(selectedIds, rawText, toolCallId)
            }
        }

        // Case 2: selected_option (LLM/Legacy single selection)
        val selectedOption = data["selected_option"] as? String
            ?: data["selectedOption"] as? String
            ?: data["user_response"] as? String  // Legacy backward compatibility
        if (!selectedOption.isNullOrBlank()) {
            val rawText = extractText(data)
            logger.debug { "[HitlResultParser] Parsed as SINGLE from selected_option: $selectedOption" }
            return HitlResult.single(selectedOption, rawText, toolCallId)
        }

        // Case 3: quantities (UI quantity selection)
        val quantities = extractQuantities(data)
        if (quantities != null && quantities.isNotEmpty()) {
            val rawText = extractText(data)
            logger.debug { "[HitlResultParser] Parsed as QUANTITY: $quantities" }
            return HitlResult.quantity(quantities, rawText, toolCallId)
        }

        // Case 4: text only
        val text = extractText(data)
        if (!text.isNullOrBlank()) {
            logger.debug { "[HitlResultParser] Parsed as TEXT: ${text.take(50)}..." }
            return HitlResult.text(text, toolCallId)
        }

        logger.warn { "[HitlResultParser] Failed to parse data, no recognized format: ${data.keys}" }
        return null
    }

    /**
     * Parse with fallback to ensure a result is always returned
     *
     * @param data Response data map
     * @param toolCallId Optional tool call ID
     * @param fallbackText Fallback text if parsing fails
     * @return HitlResult (never null)
     */
    fun parseOrFallback(
        data: Map<String, Any?>,
        toolCallId: String? = null,
        fallbackText: String = "unknown"
    ): HitlResult {
        return parse(data, toolCallId) ?: HitlResult.text(fallbackText, toolCallId)
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    /**
     * Check if the map is already a serialized HitlResult
     */
    private fun isHitlResultMap(map: Map<String, Any?>): Boolean {
        return map.containsKey("kind") && map.containsKey("canonical")
    }

    /**
     * Extract selected IDs from various key formats
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractSelectedIds(data: Map<String, Any?>): List<String>? {
        // Try multiple key formats
        val candidates = listOf(
            data["selected_ids"],
            data["selectedIds"],
            data["selected"],
            data["selectedOptions"]
        )

        for (candidate in candidates) {
            when (candidate) {
                is List<*> -> {
                    val strings = candidate.filterIsInstance<String>()
                    if (strings.isNotEmpty()) return strings
                }
                is String -> {
                    if (candidate.isNotBlank()) return listOf(candidate)
                }
            }
        }

        return null
    }

    /**
     * Extract quantities from data
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractQuantities(data: Map<String, Any?>): Map<String, Int>? {
        val quantitiesRaw = data["quantities"] as? Map<*, *> ?: return null

        val result = mutableMapOf<String, Int>()
        for ((key, value) in quantitiesRaw) {
            val keyStr = key as? String ?: continue
            val valueInt = when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            } ?: continue
            if (valueInt > 0) {
                result[keyStr] = valueInt
            }
        }

        return result.takeIf { it.isNotEmpty() }
    }

    /**
     * Extract text from various key formats
     */
    private fun extractText(data: Map<String, Any?>): String? {
        val candidates = listOf(
            data["text"],
            data["rawText"],
            data["response_text"],
            data["responseText"],
            data["input"],
            data["value"]
        )

        for (candidate in candidates) {
            if (candidate is String && candidate.isNotBlank()) {
                return candidate
            }
        }

        return null
    }
}
