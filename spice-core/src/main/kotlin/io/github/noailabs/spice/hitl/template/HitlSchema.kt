package io.github.noailabs.spice.hitl.template

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * HITL Schema
 *
 * JSON Schema representation for HITL templates, enabling:
 * - Client-side validation
 * - UI generation
 * - Documentation
 *
 * The schema follows JSON Schema draft-07 specification with
 * HITL-specific extensions.
 *
 * **Schema Extensions:**
 * - `x-hitl-kind`: HITL template kind
 * - `x-hitl-options`: Selection options for UI rendering
 * - `x-hitl-flags`: Template flags
 *
 * @since Spice 1.3.5
 */
object HitlSchema {

    /**
     * Generate JSON Schema from a HitlTemplate
     *
     * @param template Template to generate schema for
     * @return JsonObject representing the schema
     */
    fun fromTemplate(template: HitlTemplate): JsonObject = buildJsonObject {
        put("${'$'}schema", "http://json-schema.org/draft-07/schema#")
        put("title", template.id)
        put("type", "object")

        // HITL extensions
        put("x-hitl-kind", template.kind.name)

        // Properties based on template kind
        putJsonObject("properties") {
            when (template.kind) {
                HitlTemplateKind.TEXT -> {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", template.promptTemplate)
                        template.validationRules.forEach { rule ->
                            when (rule.type) {
                                "min_length" -> put("minLength", rule.value.toIntOrNull() ?: 1)
                                "max_length" -> put("maxLength", rule.value.toIntOrNull() ?: 1000)
                                "regex" -> put("pattern", rule.value)
                                "email" -> put("format", "email")
                            }
                        }
                    }
                }
                HitlTemplateKind.SINGLE_SELECT,
                HitlTemplateKind.CONFIRM -> {
                    putJsonObject("selected_id") {
                        put("type", "string")
                        put("description", "Selected option ID")
                        template.options?.let { opts ->
                            putJsonArray("enum") {
                                opts.forEach { add(JsonPrimitive(it.id)) }
                            }
                        }
                    }
                }
                HitlTemplateKind.MULTI_SELECT -> {
                    putJsonObject("selected_ids") {
                        put("type", "array")
                        put("description", "Selected option IDs")
                        putJsonObject("items") {
                            put("type", "string")
                            template.options?.let { opts ->
                                putJsonArray("enum") {
                                    opts.forEach { add(JsonPrimitive(it.id)) }
                                }
                            }
                        }
                    }
                }
                HitlTemplateKind.QUANTITY -> {
                    putJsonObject("quantity") {
                        put("type", "integer")
                        put("description", template.promptTemplate)
                        template.quantityConfig?.let { config ->
                            put("minimum", config.min)
                            put("maximum", config.max)
                            put("default", config.defaultValue)
                            put("x-hitl-step", config.step)
                        }
                    }
                }
                HitlTemplateKind.MULTI_QUANTITY -> {
                    putJsonObject("quantities") {
                        put("type", "object")
                        put("description", "Quantities for each item")
                        putJsonObject("additionalProperties") {
                            put("type", "integer")
                            template.quantityConfig?.let { config ->
                                put("minimum", config.min)
                                put("maximum", config.max)
                            }
                        }
                    }
                }
                HitlTemplateKind.INFO -> {
                    putJsonObject("acknowledged") {
                        put("type", "boolean")
                        put("const", true)
                        put("description", "User acknowledged the info")
                    }
                }
                HitlTemplateKind.ESCALATE -> {
                    putJsonObject("reason") {
                        put("type", "string")
                        put("description", "Reason for escalation")
                    }
                }
            }
        }

        // x-hitl-options for UI rendering (for selection types)
        if (template.kind in listOf(
            HitlTemplateKind.SINGLE_SELECT,
            HitlTemplateKind.MULTI_SELECT,
            HitlTemplateKind.CONFIRM
        )) {
            putJsonArray("x-hitl-options") {
                val options = template.options ?: if (template.kind == HitlTemplateKind.CONFIRM) {
                    HitlOption.yesNo()
                } else {
                    emptyList()
                }
                options.forEach { opt ->
                    add(buildJsonObject {
                        put("id", opt.id)
                        put("label", opt.label)
                        opt.description?.let { put("description", it) }
                        opt.icon?.let { put("icon", it) }
                    })
                }
            }
        }

        // Required fields
        putJsonArray("required") {
            when (template.kind) {
                HitlTemplateKind.TEXT -> add(JsonPrimitive("text"))
                HitlTemplateKind.SINGLE_SELECT, HitlTemplateKind.CONFIRM -> add(JsonPrimitive("selected_id"))
                HitlTemplateKind.MULTI_SELECT -> add(JsonPrimitive("selected_ids"))
                HitlTemplateKind.QUANTITY -> add(JsonPrimitive("quantity"))
                HitlTemplateKind.MULTI_QUANTITY -> add(JsonPrimitive("quantities"))
                HitlTemplateKind.INFO -> add(JsonPrimitive("acknowledged"))
                HitlTemplateKind.ESCALATE -> {} // No required field
            }
        }

        // Template flags
        putJsonObject("x-hitl-flags") {
            put("required", template.flags.required)
            put("autoProceed", template.flags.autoProceed)
            put("allowSkip", template.flags.allowSkip)
            template.flags.timeout?.let { put("timeout", it) }
            put("retryCount", template.flags.retryCount)
        }
    }

    /**
     * Generate a combined schema for multiple templates
     *
     * @param templates List of templates
     * @return JsonObject with oneOf schema
     */
    fun fromTemplates(templates: List<HitlTemplate>): JsonObject = buildJsonObject {
        put("${'$'}schema", "http://json-schema.org/draft-07/schema#")
        put("title", "HITL Templates")
        putJsonArray("oneOf") {
            templates.forEach { template ->
                add(fromTemplate(template))
            }
        }
    }
}

// Reusable Json format for schema serialization
private val prettyJson = Json { prettyPrint = true }

/**
 * Extension to convert HitlTemplate to JSON Schema string
 */
fun HitlTemplate.toJsonSchema(): String {
    val schema = HitlSchema.fromTemplate(this)
    return prettyJson.encodeToString(JsonObject.serializer(), schema)
}

/**
 * Extension to get schema for a registry
 */
fun HitlTemplateRegistry.getSchema(tenantId: String? = null): JsonObject {
    val templates = listTemplateIds(tenantId).mapNotNull { resolve(it, tenantId) }
    return HitlSchema.fromTemplates(templates)
}
