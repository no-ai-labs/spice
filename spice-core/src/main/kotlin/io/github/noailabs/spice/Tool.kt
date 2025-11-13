package io.github.noailabs.spice

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.error.SpiceError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Tool interface for JVM-Autogen
 * Defines tools that agents can use.
 *
 * @since 0.2.0 - execute now returns SpiceResult<ToolResult> for type-safe error handling
 * @since 0.10.0 - Added toToolSpec() for OpenAI Function Calling compatibility
 */
interface Tool {
    val name: String
    val description: String
    val schema: ToolSchema

    /**
     * Tool execution
     *
     * @return SpiceResult<ToolResult> - Success with tool result or Failure with error
     */
    suspend fun execute(parameters: Map<String, Any?>): SpiceResult<ToolResult>

    /**
     * Tool execution with context
     *
     * @return SpiceResult<ToolResult> - Success with tool result or Failure with error
     */
    suspend fun execute(parameters: Map<String, Any?>, context: ToolContext): SpiceResult<ToolResult> =
        execute(parameters)

    /**
     * Check if tool can process specific parameters
     */
    fun canExecute(parameters: Map<String, Any?>): Boolean = true

    /**
     * Export tool schema as OpenAI Function Calling spec
     *
     * Returns a map that can be directly used with OpenAI, Anthropic, or other
     * LLM APIs that support function calling.
     *
     * Example output:
     * ```json
     * {
     *   "type": "function",
     *   "name": "web_search",
     *   "description": "Search the web",
     *   "parameters": {
     *     "type": "object",
     *     "properties": {
     *       "query": { "type": "string", "description": "Search query" }
     *     },
     *     "required": ["query"]
     *   }
     * }
     * ```
     *
     * @param strict Whether to enforce strict schema validation (default: false)
     * @return Map representation of OpenAI function calling spec
     * @since 0.10.0
     */
    fun toToolSpec(strict: Boolean = false): Map<String, Any> = schema.toOpenAIFunctionSpec(strict)

    /**
     * Validate parameters before execution
     */
    fun validateParameters(parameters: Map<String, Any?>): ValidationResult {
        val errors = mutableListOf<String>()

        schema.parameters.forEach { (name, paramSchema) ->
            if (paramSchema.required && !parameters.containsKey(name)) {
                errors.add("Missing required parameter: $name")
            }

            parameters[name]?.let { value ->
                if (!isValidType(value, paramSchema.type)) {
                    errors.add("Parameter '$name' has invalid type. Expected: ${paramSchema.type}")
                }
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult(valid = true, errors = emptyList())
        } else {
            ValidationResult(valid = false, errors = errors)
        }
    }

    private fun isValidType(value: Any?, expectedType: String): Boolean {
        if (value == null) return true  // null is valid for nullable types
        return when (expectedType.lowercase()) {
            "string" -> value is String
            "number" -> value is Number
            "boolean" -> value is Boolean
            "array" -> value is List<*> || value is Array<*>
            "object" -> value is Map<*, *>
            else -> true
        }
    }
}

/**
 * Tool schema definition
 */
@Serializable
data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterSchema>
)

/**
 * Parameter schema
 */
@Serializable
data class ParameterSchema(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val default: JsonElement? = null
)

/**
 * Tool execution result
 */
@Serializable
data class ToolResult(
    val success: Boolean,
    val result: String = "",
    val error: String = "",
    @Serializable(with = AnyValueMapSerializer::class)
    val metadata: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun success(result: String, metadata: Map<String, Any?> = emptyMap()): ToolResult {
            return ToolResult(success = true, result = result, metadata = metadata)
        }

        fun error(error: String, metadata: Map<String, Any?> = emptyMap()): ToolResult {
            return ToolResult(success = false, error = error, metadata = metadata)
        }
    }
}

/**
 * Basic Tool implementation
 */
abstract class BaseTool : Tool {
    override fun canExecute(parameters: Map<String, Any?>): Boolean {
        return true
    }
}

/**
 * Tool execution context
 */
data class ToolContext(
    val agentId: String,
    val userId: String? = null,
    val tenantId: String? = null,
    val correlationId: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
)


/**
 * Web search tool
 */
class WebSearchTool : BaseTool() {
    override val name = "web_search"
    override val description = "tool.web_search.description".i18n()
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "query" to ParameterSchema("string", "tool.web_search.param.query".i18n(), required = true),
            "limit" to ParameterSchema("number", "tool.web_search.param.limit".i18n(), required = false)
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any?>): SpiceResult<ToolResult> {
        val query = parameters["query"] as? String
            ?: return SpiceResult.success(ToolResult.error("tool.web_search.error.query_required".i18n()))

        val limit = (parameters["limit"] as? Number)?.toInt() ?: 5

        // Actual web search logic (mock implementation here)
        val results = try {
            searchWeb(query, limit)
        } catch (e: Exception) {
            return SpiceResult.success(ToolResult.error("tool.web_search.error.search_failed".i18n()))
        }

        return SpiceResult.success(ToolResult.success(
            result = results.joinToString("\n") { "- $it" },
            metadata = mapOf("query" to query, "resultCount" to results.size)
        ))
    }
    
    private suspend fun searchWeb(query: String, limit: Int): List<String> {
        return (1..limit).map { "Search result $it: $query related information" }
    }
}

/**
 * File read tool
 */
class FileReadTool : BaseTool() {
    override val name = "file_read"
    override val description = "tool.file_read.description".i18n()
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "path" to ParameterSchema("string", "tool.file_read.param.path".i18n(), required = true)
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any?>): SpiceResult<ToolResult> {
        val path = parameters["path"] as? String
            ?: return SpiceResult.success(ToolResult.error("tool.file_read.error.path_required".i18n()))

        return try {
            val content = java.io.File(path).readText()
            SpiceResult.success(ToolResult.success(
                result = content,
                metadata = mapOf("path" to path, "size" to content.length)
            ))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error("tool.file_read.error.read_failed".i18n()))
        }
    }
}

/**
 * File write tool
 */
class FileWriteTool : BaseTool() {
    override val name = "file_write"
    override val description = "tool.file_write.description".i18n()
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "path" to ParameterSchema("string", "tool.file_write.param.path".i18n(), required = true),
            "content" to ParameterSchema("string", "tool.file_write.param.content".i18n(), required = true)
        )
    )

    override suspend fun execute(parameters: Map<String, Any?>): SpiceResult<ToolResult> {
        val path = parameters["path"] as? String
            ?: return SpiceResult.success(ToolResult.error("tool.file_write.error.path_required".i18n()))

        val content = parameters["content"] as? String
            ?: return SpiceResult.success(ToolResult.error("tool.file_write.error.content_required".i18n()))

        return try {
            java.io.File(path).writeText(content)
            SpiceResult.success(ToolResult.success(
                result = "tool.file_write.success".i18n(),
                metadata = mapOf("path" to path, "size" to content.length)
            ))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error("tool.file_write.error.write_failed".i18n()))
        }
    }
}

// =====================================
// OpenAI Function Calling Spec Export
// =====================================

/**
 * Convert ToolSchema to OpenAI Function Calling specification format
 *
 * @param strict Whether to enforce strict schema validation (default: false)
 * @return Map representation of OpenAI function calling spec
 * @see <a href="https://platform.openai.com/docs/guides/function-calling">OpenAI Function Calling</a>
 *
 * Example output:
 * ```json
 * {
 *   "type": "function",
 *   "name": "web_search",
 *   "description": "Search the web for information",
 *   "parameters": {
 *     "type": "object",
 *     "properties": {
 *       "query": {
 *         "type": "string",
 *         "description": "Search query"
 *       }
 *     },
 *     "required": ["query"],
 *     "additionalProperties": false
 *   },
 *   "strict": false
 * }
 * ```
 */
fun ToolSchema.toOpenAIFunctionSpec(strict: Boolean = false): Map<String, Any> {
    val properties = parameters.mapValues { (_, param) ->
        buildMap<String, Any> {
            put("type", param.type)
            put("description", param.description)
            param.default?.let { put("default", it) }
        }
    }

    val required = parameters
        .filter { it.value.required }
        .keys
        .toList()

    return buildMap {
        put("type", "function")
        put("name", name)
        put("description", description)
        put("parameters", buildMap<String, Any> {
            put("type", "object")
            put("properties", properties)
            if (required.isNotEmpty()) {
                put("required", required)
            }
            if (strict) {
                put("additionalProperties", false)
            }
        })
        if (strict) {
            put("strict", true)
        }
    }
}

/**
 * Convert Tool to OpenAI Function Calling specification format
 *
 * @param strict Whether to enforce strict schema validation (default: false)
 * @return Map representation of OpenAI function calling spec
 * @see <a href="https://platform.openai.com/docs/guides/function-calling">OpenAI Function Calling</a>
 */
fun Tool.toOpenAIFunctionSpec(strict: Boolean = false): Map<String, Any> =
    schema.toOpenAIFunctionSpec(strict) 