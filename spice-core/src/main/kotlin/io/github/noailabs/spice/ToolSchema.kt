package io.github.noailabs.spice

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 📋 Tool Schema for OpenAI Function Calling Integration
 *
 * Defines the structure and parameters for a tool that can be called by LLMs.
 * Compatible with OpenAI, Anthropic, LangChain, and other LLM frameworks.
 *
 * **Usage:**
 * ```kotlin
 * val schema = ToolSchema(
 *     name = "web_search",
 *     description = "Search the web for information",
 *     parameters = mapOf(
 *         "query" to ParameterSchema("string", "Search query", required = true),
 *         "limit" to ParameterSchema("number", "Maximum results", required = false)
 *     )
 * )
 *
 * val spec = schema.toOpenAIFunctionSpec()
 * ```
 *
 * @property name Tool name (must be unique)
 * @property description Human-readable description of what the tool does
 * @property parameters Map of parameter name to parameter schema
 * @since 1.0.0
 */
@Serializable
data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterSchema> = emptyMap()
)

/**
 * 📝 Parameter Schema for Tool Parameters
 *
 * Defines a single parameter for a tool, including its type, description,
 * whether it's required, and an optional default value.
 *
 * **Supported Types:**
 * - `string`: Text values
 * - `number`: Numeric values (int, float, double)
 * - `boolean`: true/false values
 * - `array`: List of values
 * - `object`: Nested object/map
 *
 * **Usage:**
 * ```kotlin
 * val param = ParameterSchema(
 *     type = "string",
 *     description = "User's email address",
 *     required = true
 * )
 * ```
 *
 * @property type Parameter type (string, number, boolean, array, object)
 * @property description Human-readable description
 * @property required Whether this parameter is required
 * @property default Optional default value (as JSON element)
 * @since 1.0.0
 */
@Serializable
data class ParameterSchema(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val default: @Contextual JsonElement? = null
)

/**
 * 🔄 Convert ToolSchema to OpenAI Function Calling Specification
 *
 * Converts a Spice ToolSchema to the OpenAI function calling format,
 * which is also compatible with Anthropic, LangChain, and other frameworks.
 *
 * **Output Format:**
 * ```json
 * {
 *   "type": "function",
 *   "name": "function_name",
 *   "description": "Function description",
 *   "parameters": {
 *     "type": "object",
 *     "properties": {
 *       "param_name": {
 *         "type": "string",
 *         "description": "Parameter description"
 *       }
 *     },
 *     "required": ["param1", "param2"],
 *     "additionalProperties": false  // Only if strict=true
 *   },
 *   "strict": true  // Only if strict=true
 * }
 * ```
 *
 * **Strict Mode:**
 * When `strict = true`, adds:
 * - `"strict": true` at top level
 * - `"additionalProperties": false` in parameters
 *
 * This enforces that the LLM cannot add extra parameters beyond those defined.
 *
 * **Usage:**
 * ```kotlin
 * val spec = schema.toOpenAIFunctionSpec()
 * val strictSpec = schema.toOpenAIFunctionSpec(strict = true)
 * ```
 *
 * @param strict Enable strict mode (default: false)
 * @return Map representing OpenAI function calling specification
 * @since 1.0.0
 */
fun ToolSchema.toOpenAIFunctionSpec(strict: Boolean = false): Map<String, Any> {
    // Build properties map
    val properties = parameters.mapValues { (_, param) ->
        buildMap<String, Any> {
            put("type", param.type)
            put("description", param.description)
            param.default?.let { put("default", it) }
        }
    }

    // Collect required parameters
    val required = parameters
        .filter { it.value.required }
        .keys
        .toList()

    // Build parameters object
    val parametersObject = buildMap<String, Any> {
        put("type", "object")
        put("properties", properties)

        // Only include "required" field if there are required parameters
        if (required.isNotEmpty()) {
            put("required", required)
        }

        // Add additionalProperties: false in strict mode
        if (strict) {
            put("additionalProperties", false)
        }
    }

    // Build top-level spec
    return buildMap {
        put("type", "function")
        put("name", name)
        put("description", description)
        put("parameters", parametersObject)

        // Add strict: true at top level in strict mode
        if (strict) {
            put("strict", true)
        }
    }
}
