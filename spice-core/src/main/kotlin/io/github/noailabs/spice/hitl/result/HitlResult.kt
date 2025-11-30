package io.github.noailabs.spice.hitl.result

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * HITL Response Kind
 *
 * Represents the type of HITL response.
 *
 * @since Spice 1.3.4
 */
@Serializable
enum class HitlResponseKind {
    /** Free text input */
    TEXT,

    /** Single selection from options */
    SINGLE,

    /** Multiple selections from options */
    MULTI,

    /** Quantity selection (id -> quantity mapping) */
    QUANTITY
}

/**
 * Normalized HITL Result
 *
 * A unified representation of HITL responses that normalizes all input types
 * (text, single selection, multi selection, quantity) into a single structure.
 *
 * **Key Design Decisions:**
 * - `canonical` is **required** (non-null) for reliable DECISION node routing
 * - All DECISION nodes should use `hitl.canonical` for branching
 * - Empty canonical is not allowed; parser must provide a meaningful value
 *
 * **Canonical Value Rules:**
 * | Kind     | Canonical Value                    |
 * |----------|-----------------------------------|
 * | TEXT     | `rawText.trim()` (normalized)      |
 * | SINGLE   | `selected.first()`                 |
 * | MULTI    | `selected.joinToString(",")`       |
 * | QUANTITY | `quantities.keys.joinToString(",")` |
 *
 * **Usage in SpiceMessage.data:**
 * ```kotlin
 * data["hitl"] = hitlResult.toMap()
 *
 * // DECISION node reads:
 * message.getData<String>("hitl.canonical")
 * ```
 *
 * @property kind Type of HITL response
 * @property canonical Canonical value for DECISION routing (REQUIRED)
 * @property rawText Original user text input
 * @property selected List of selected option IDs (for SINGLE/MULTI)
 * @property quantities Map of option ID to quantity (for QUANTITY)
 * @property structured LLM/Client structured payload for advanced processing
 * @property toolCallId Tool call ID for tracing resume flow
 * @property timestamp When the response was received
 * @property metadata Additional custom metadata
 *
 * @since Spice 1.3.4
 */
@Serializable
data class HitlResult(
    val kind: HitlResponseKind,
    val canonical: String,  // REQUIRED: empty string not allowed
    val rawText: String? = null,
    val selected: List<String>? = null,
    val quantities: Map<String, Int>? = null,
    val structured: Map<String, @Contextual Any?>? = null,
    val toolCallId: String? = null,
    @Contextual
    val timestamp: Instant = Instant.now(),
    val metadata: Map<String, @Contextual Any?> = emptyMap()
) {
    init {
        require(canonical.isNotBlank()) {
            "HitlResult.canonical must not be blank. Kind=$kind"
        }
    }

    companion object {
        /** Key for storing HitlResult in SpiceMessage.data */
        const val DATA_KEY = "hitl"

        /** Key for accessing canonical value via nested path: "hitl.canonical" */
        const val CANONICAL_PATH = "hitl.canonical"

        // ============================================================
        // Factory Methods
        // ============================================================

        /**
         * Create a TEXT type result from free text input
         *
         * @param rawText The user's text input (must not be blank)
         * @param toolCallId Optional tool call ID for tracing
         * @throws IllegalArgumentException if rawText is blank
         */
        fun text(rawText: String, toolCallId: String? = null): HitlResult {
            require(rawText.isNotBlank()) { "rawText must not be blank for TEXT response" }
            return HitlResult(
                kind = HitlResponseKind.TEXT,
                canonical = rawText.trim(),
                rawText = rawText,
                selected = null,
                quantities = null,
                structured = null,
                toolCallId = toolCallId
            )
        }

        /**
         * Create a SINGLE type result from single selection
         *
         * @param selectedId The selected option ID (must not be blank)
         * @param rawText Optional user text
         * @param toolCallId Optional tool call ID for tracing
         * @throws IllegalArgumentException if selectedId is blank
         */
        fun single(selectedId: String, rawText: String? = null, toolCallId: String? = null): HitlResult {
            require(selectedId.isNotBlank()) { "selectedId must not be blank for SINGLE response" }
            return HitlResult(
                kind = HitlResponseKind.SINGLE,
                canonical = selectedId,
                rawText = rawText,
                selected = listOf(selectedId),
                quantities = null,
                structured = null,
                toolCallId = toolCallId
            )
        }

        /**
         * Create a MULTI type result from multiple selections
         *
         * @param selectedIds The selected option IDs (must not be empty)
         * @param rawText Optional user text
         * @param toolCallId Optional tool call ID for tracing
         * @throws IllegalArgumentException if selectedIds is empty
         */
        fun multi(selectedIds: List<String>, rawText: String? = null, toolCallId: String? = null): HitlResult {
            require(selectedIds.isNotEmpty()) { "selectedIds must not be empty for MULTI response" }
            return HitlResult(
                kind = HitlResponseKind.MULTI,
                canonical = selectedIds.joinToString(","),
                rawText = rawText,
                selected = selectedIds,
                quantities = null,
                structured = null,
                toolCallId = toolCallId
            )
        }

        /**
         * Create a QUANTITY type result from quantity selection
         *
         * @param quantities Map of option ID to quantity (must not be empty)
         * @param rawText Optional user text
         * @param toolCallId Optional tool call ID for tracing
         * @throws IllegalArgumentException if quantities is empty
         */
        fun quantity(
            quantities: Map<String, Int>,
            rawText: String? = null,
            toolCallId: String? = null
        ): HitlResult {
            require(quantities.isNotEmpty()) { "quantities must not be empty for QUANTITY response" }
            return HitlResult(
                kind = HitlResponseKind.QUANTITY,
                canonical = quantities.keys.sorted().joinToString(","),
                rawText = rawText,
                selected = null,
                quantities = quantities,
                structured = null,
                toolCallId = toolCallId
            )
        }

        // ============================================================
        // Deserialization
        // ============================================================

        /**
         * Extract HitlResult from SpiceMessage.data
         *
         * @param data SpiceMessage.data map
         * @return HitlResult if present, null otherwise
         */
        @Suppress("UNCHECKED_CAST")
        fun fromData(data: Map<String, Any?>): HitlResult? {
            val hitlMap = data[DATA_KEY] as? Map<String, Any?> ?: return null
            return fromMap(hitlMap)
        }

        /**
         * Deserialize HitlResult from a map
         *
         * @param map Serialized HitlResult map
         * @return HitlResult instance
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): HitlResult {
            val kindStr = map["kind"] as? String ?: "TEXT"
            val canonical = map["canonical"] as? String
                ?: throw IllegalArgumentException("HitlResult.canonical is required")

            return HitlResult(
                kind = HitlResponseKind.valueOf(kindStr),
                canonical = canonical,
                rawText = map["rawText"] as? String,
                selected = (map["selected"] as? List<*>)?.filterIsInstance<String>(),
                quantities = (map["quantities"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val value = (v as? Number)?.toInt() ?: return@mapNotNull null
                    key to value
                }?.toMap(),
                structured = map["structured"] as? Map<String, Any?>,
                toolCallId = map["toolCallId"] as? String,
                timestamp = (map["timestamp"] as? String)
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    ?: Instant.now(),
                metadata = (map["metadata"] as? Map<String, Any?>) ?: emptyMap()
            )
        }
    }

    // ============================================================
    // Serialization
    // ============================================================

    /**
     * Serialize to a map for SpiceMessage.data storage
     *
     * @return Map representation suitable for JSON serialization
     */
    fun toMap(): Map<String, Any?> = buildMap {
        put("kind", kind.name)
        put("canonical", canonical)
        rawText?.let { put("rawText", it) }
        selected?.let { put("selected", it) }
        quantities?.let { put("quantities", it) }
        structured?.let { put("structured", it) }
        toolCallId?.let { put("toolCallId", it) }
        put("timestamp", timestamp.toString())
        if (metadata.isNotEmpty()) {
            put("metadata", metadata)
        }
    }

    // ============================================================
    // Utility Methods
    // ============================================================

    /**
     * Check if this is a single selection response
     */
    val isSingle: Boolean get() = kind == HitlResponseKind.SINGLE

    /**
     * Check if this is a multi-selection response
     */
    val isMulti: Boolean get() = kind == HitlResponseKind.MULTI

    /**
     * Check if this is a text input response
     */
    val isText: Boolean get() = kind == HitlResponseKind.TEXT

    /**
     * Check if this is a quantity selection response
     */
    val isQuantity: Boolean get() = kind == HitlResponseKind.QUANTITY

    /**
     * Get the first selected option ID (for SINGLE/MULTI)
     */
    val firstSelected: String? get() = selected?.firstOrNull()

    /**
     * Get total quantity sum (for QUANTITY type)
     */
    val totalQuantity: Int get() = quantities?.values?.sum() ?: 0
}
