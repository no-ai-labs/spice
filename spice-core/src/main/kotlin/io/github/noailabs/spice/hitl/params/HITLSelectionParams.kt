package io.github.noailabs.spice.hitl.params

import io.github.noailabs.spice.hitl.result.HITLOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * HITL V2.2 Request Parameters for hitl_request_selection
 *
 * @property promptText Prompt message
 * @property options Selection options (V2.2: {id, label, description?})
 * @property allowFreeText Allow "other" input (default: false)
 * @property multiSelect Allow multiple selection (default: false)
 * @property timeoutSeconds Timeout in seconds
 *
 * @since Spice 1.6.0
 */
data class HITLSelectionParams(
    val promptText: String,
    val options: List<HITLOption>,
    val allowFreeText: Boolean = false,
    val multiSelect: Boolean = false,
    val timeoutSeconds: Long? = null
) {
    /**
     * Convert to Map (for SpiceMessage.data interoperability)
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "prompt_text" to promptText,
        "options" to options.map { mapOf("id" to it.id, "label" to it.label, "description" to it.description) },
        "allowFreeText" to allowFreeText,
        "multiSelect" to multiSelect,
        "timeoutSeconds" to timeoutSeconds
    )

    /**
     * Serialize to JSON string
     *
     * Note: HITLOption is an external library type, so manual conversion is used.
     */
    fun toJson(): String {
        val optionsArray = buildJsonArray {
            options.forEach { opt ->
                add(buildJsonObject {
                    put("id", JsonPrimitive(opt.id))
                    put("label", JsonPrimitive(opt.label))
                    opt.description?.let { put("description", JsonPrimitive(it)) }
                })
            }
        }

        val jsonObj = buildJsonObject {
            put("promptText", JsonPrimitive(promptText))
            put("options", optionsArray)
            put("allowFreeText", JsonPrimitive(allowFreeText))
            put("multiSelect", JsonPrimitive(multiSelect))
            timeoutSeconds?.let { put("timeoutSeconds", JsonPrimitive(it)) }
        }

        return json.encodeToString(JsonObject.serializer(), jsonObj)
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        /**
         * Deserialize from JSON string
         *
         * Note: HITLOption is an external library type, so manual conversion is used.
         */
        fun fromJson(jsonString: String): HITLSelectionParams {
            val jsonObj = json.parseToJsonElement(jsonString).jsonObject
            val promptText = jsonObj["promptText"]?.jsonPrimitive?.content ?: error("Missing promptText")
            val allowFreeText = jsonObj["allowFreeText"]?.jsonPrimitive?.booleanOrNull ?: false
            val multiSelect = jsonObj["multiSelect"]?.jsonPrimitive?.booleanOrNull ?: false
            val timeoutSeconds = jsonObj["timeoutSeconds"]?.jsonPrimitive?.longOrNull

            val optionsArray = jsonObj["options"]?.jsonArray ?: error("Missing options")
            val options = optionsArray.map { optElem ->
                val optObj = optElem.jsonObject
                HITLOption(
                    id = optObj["id"]?.jsonPrimitive?.content ?: error("Option missing id"),
                    label = optObj["label"]?.jsonPrimitive?.content ?: error("Option missing label"),
                    description = optObj["description"]?.jsonPrimitive?.content
                )
            }

            return HITLSelectionParams(
                promptText = promptText,
                options = options,
                allowFreeText = allowFreeText,
                multiSelect = multiSelect,
                timeoutSeconds = timeoutSeconds
            )
        }

        /**
         * Restore from Map (for SpiceMessage.data interoperability)
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): HITLSelectionParams {
            val optionsList = map["options"] as? List<Map<String, Any?>> ?: emptyList()
            val options = optionsList.map { opt ->
                HITLOption(
                    id = opt["id"] as? String ?: error("Option missing id"),
                    label = opt["label"] as? String ?: error("Option missing label"),
                    description = opt["description"] as? String
                )
            }
            return HITLSelectionParams(
                promptText = map["prompt_text"] as? String ?: error("Missing prompt_text"),
                options = options,
                allowFreeText = map["allowFreeText"] as? Boolean ?: false,
                multiSelect = map["multiSelect"] as? Boolean ?: false,
                timeoutSeconds = (map["timeoutSeconds"] as? Number)?.toLong()
            )
        }
    }
}
