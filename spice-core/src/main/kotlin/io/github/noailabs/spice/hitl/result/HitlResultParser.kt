package io.github.noailabs.spice.hitl.result

import io.github.noailabs.spice.hitl.validation.HitlLogLevelMapper
import io.github.noailabs.spice.hitl.validation.HitlResultParserConfig
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
        val options = HitlResultParserConfig.options

        HitlLogLevelMapper.log(logger, options.successLogLevel) {
            "[HitlResultParser] Parsing data: ${data.keys}"
        }

        // Case 0: Already a HitlResult
        if (isHitlResultMap(data)) {
            return try {
                HitlResult.fromMap(data)
            } catch (e: Exception) {
                // Check if it's an empty canonical validation error
                val isEmptyCanonical = e.message?.contains("blank") == true
                val logLevel = if (isEmptyCanonical) options.emptyCanonicalLogLevel else options.parseFailureLogLevel
                HitlLogLevelMapper.log(logger, logLevel) {
                    "[HitlResultParser] Failed to parse existing HitlResult: ${e.message}"
                }
                null
            }
        }

        // Case 0b: Nested hitl object
        val nestedHitl = data[HitlResult.DATA_KEY] as? Map<String, Any?>
        if (nestedHitl != null && isHitlResultMap(nestedHitl)) {
            return try {
                HitlResult.fromMap(nestedHitl)
            } catch (e: Exception) {
                val isEmptyCanonical = e.message?.contains("blank") == true
                val logLevel = if (isEmptyCanonical) options.emptyCanonicalLogLevel else options.parseFailureLogLevel
                HitlLogLevelMapper.log(logger, logLevel) {
                    "[HitlResultParser] Failed to parse nested HitlResult: ${e.message}"
                }
                null
            }
        }

        // Check for unknown fields (only for non-HitlResult data)
        val knownFields = setOf(
            // HitlResult.toMap() fields
            "kind", "canonical", "selected", "rawText", "toolCallId", "structured", "timestamp", "metadata", "quantities",
            // UI selection fields
            "selected_ids", "selectedIds", "selectedOptions",
            // LLM/Legacy fields
            "selected_option", "selectedOption",
            // Text fields
            "text", "response_text", "responseText", "input", "value",
            // Common wrapper/metadata fields
            HitlResult.DATA_KEY, "structured_response", "user_response_tool_call"
        )
        val unknownFields = data.keys.filter { it !in knownFields }
        if (unknownFields.isNotEmpty()) {
            HitlLogLevelMapper.log(logger, options.unknownFieldLogLevel) {
                "[HitlResultParser] Unknown fields detected: $unknownFields"
            }
        }

        // Case 1: selected_ids (UI single/multi selection)
        val selectedIds = extractSelectedIds(data)
        if (selectedIds != null && selectedIds.isNotEmpty()) {
            val rawText = extractText(data)
            return try {
                if (selectedIds.size == 1) {
                    HitlLogLevelMapper.log(logger, options.successLogLevel) {
                        "[HitlResultParser] Parsed as SINGLE: ${selectedIds.first()}"
                    }
                    HitlResult.single(selectedIds.first(), rawText, toolCallId)
                } else {
                    HitlLogLevelMapper.log(logger, options.successLogLevel) {
                        "[HitlResultParser] Parsed as MULTI: $selectedIds"
                    }
                    HitlResult.multi(selectedIds, rawText, toolCallId)
                }
            } catch (e: IllegalArgumentException) {
                HitlLogLevelMapper.log(logger, options.emptyCanonicalLogLevel) {
                    "[HitlResultParser] Validation failed for selected_ids: ${e.message}"
                }
                null
            }
        }

        // Case 2: selected_option (LLM/Legacy single selection)
        val selectedOption = data["selected_option"] as? String
            ?: data["selectedOption"] as? String
        if (!selectedOption.isNullOrBlank()) {
            val rawText = extractText(data)
            return try {
                HitlLogLevelMapper.log(logger, options.successLogLevel) {
                    "[HitlResultParser] Parsed as SINGLE from selected_option: $selectedOption"
                }
                HitlResult.single(selectedOption, rawText, toolCallId)
            } catch (e: IllegalArgumentException) {
                HitlLogLevelMapper.log(logger, options.emptyCanonicalLogLevel) {
                    "[HitlResultParser] Validation failed for selected_option: ${e.message}"
                }
                null
            }
        }

        // Case 3: quantities (UI quantity selection)
        val quantities = extractQuantities(data)
        if (quantities != null && quantities.isNotEmpty()) {
            val rawText = extractText(data)
            return try {
                HitlLogLevelMapper.log(logger, options.successLogLevel) {
                    "[HitlResultParser] Parsed as QUANTITY: $quantities"
                }
                HitlResult.quantity(quantities, rawText, toolCallId)
            } catch (e: IllegalArgumentException) {
                HitlLogLevelMapper.log(logger, options.emptyCanonicalLogLevel) {
                    "[HitlResultParser] Validation failed for quantities: ${e.message}"
                }
                null
            }
        }

        // Case 4: text only
        val text = extractText(data)
        if (!text.isNullOrBlank()) {
            return try {
                HitlLogLevelMapper.log(logger, options.successLogLevel) {
                    "[HitlResultParser] Parsed as TEXT: ${text.take(50)}..."
                }
                HitlResult.text(text, toolCallId)
            } catch (e: IllegalArgumentException) {
                HitlLogLevelMapper.log(logger, options.emptyCanonicalLogLevel) {
                    "[HitlResultParser] Validation failed for text: ${e.message}"
                }
                null
            }
        }

        HitlLogLevelMapper.log(logger, options.parseFailureLogLevel) {
            "[HitlResultParser] Failed to parse data, no recognized format: ${data.keys}"
        }
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
