package io.github.noailabs.spice.hitl.params

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * HITL V2.2 Request Parameters for hitl_request_input
 *
 * @property promptText Prompt message
 * @property allowFreeText Allow free text input (default: true)
 * @property maxLength Maximum input length
 * @property timeoutSeconds Timeout in seconds
 *
 * @since Spice 1.6.0
 */
@Serializable
data class HITLInputParams(
    val promptText: String,
    val allowFreeText: Boolean = true,
    val maxLength: Int? = null,
    val timeoutSeconds: Long? = null
) {
    /**
     * Serialize to JSON string
     */
    fun toJson(): String = json.encodeToString(this)

    /**
     * Convert to Map (for SpiceMessage.data interoperability)
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "prompt_text" to promptText,
        "allowFreeText" to allowFreeText,
        "maxLength" to maxLength,
        "timeoutSeconds" to timeoutSeconds
    )

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        /**
         * Deserialize from JSON string
         */
        fun fromJson(jsonString: String): HITLInputParams = json.decodeFromString(jsonString)

        /**
         * Restore from Map (for SpiceMessage.data interoperability)
         */
        fun fromMap(map: Map<String, Any?>): HITLInputParams = HITLInputParams(
            promptText = map["prompt_text"] as? String ?: error("Missing prompt_text"),
            allowFreeText = map["allowFreeText"] as? Boolean ?: true,
            maxLength = (map["maxLength"] as? Number)?.toInt(),
            timeoutSeconds = (map["timeoutSeconds"] as? Number)?.toLong()
        )
    }
}
