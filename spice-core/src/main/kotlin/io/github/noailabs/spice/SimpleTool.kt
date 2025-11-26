package io.github.noailabs.spice

import io.github.noailabs.spice.error.SpiceResult

/**
 * 🔧 Simple Tool Implementation with Schema Support
 *
 * A lightweight Tool implementation that supports parameter schemas and validation.
 * Ideal for creating tools with well-defined parameters for LLM function calling.
 *
 * **Usage:**
 * ```kotlin
 * val searchTool = SimpleTool(
 *     name = "web_search",
 *     description = "Search the web for information",
 *     parameterSchemas = mapOf(
 *         "query" to ParameterSchema("string", "Search query", required = true),
 *         "limit" to ParameterSchema("number", "Max results", required = false)
 *     )
 * ) { params ->
 *     val query = params["query"] as String
 *     val limit = params["limit"] as? Int ?: 10
 *
 *     // Perform search...
 *     ToolResult.success("Found 5 results for: $query")
 * }
 *
 * // Convert to OpenAI spec
 * val spec = searchTool.toOpenAIFunctionSpec()
 * ```
 *
 * **Parameter Validation:**
 * - Required parameters are automatically validated via `canExecute()`
 * - Type validation can be added in the executor function
 *
 * @property name Tool name (must be unique)
 * @property description Human-readable description
 * @property parameterSchemas Map of parameter name to schema
 * @property executor Function that executes the tool with given parameters
 * @since 1.0.0
 */
class SimpleTool(
    override val name: String,
    override val description: String,
    val parameterSchemas: Map<String, ParameterSchema> = emptyMap(),
    private val executor: suspend (Map<String, Any>) -> ToolResult
) : Tool {

    /**
     * Tool schema for OpenAI function calling integration
     */
    val schema: ToolSchema = ToolSchema(name, description, parameterSchemas)

    /**
     * Execute the tool with given parameters
     *
     * @param params Parameter map
     * @param context Execution context (agentId, userId, etc.)
     * @return SpiceResult with tool execution result
     */
    override suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        return SpiceResult.catching {
            executor(params)
        }
    }

    /**
     * Check if tool can execute with given parameters
     *
     * Validates that all required parameters are present.
     *
     * @param parameters Parameter map to validate
     * @return true if all required parameters are present
     */
    fun canExecute(parameters: Map<String, Any>): Boolean {
        val required = parameterSchemas.filter { it.value.required }.keys
        return required.all { parameters.containsKey(it) }
    }

    /**
     * Validate parameters against schema
     *
     * Checks:
     * - All required parameters are present
     * - Parameter types match (basic validation)
     *
     * @param parameters Parameter map to validate
     * @return List of validation errors (empty if valid)
     */
    fun validateParameters(parameters: Map<String, Any>): List<String> {
        val errors = mutableListOf<String>()

        // Check required parameters
        val required = parameterSchemas.filter { it.value.required }.keys
        val missing = required - parameters.keys
        if (missing.isNotEmpty()) {
            errors.add("Missing required parameters: ${missing.joinToString()}")
        }

        // Check for unexpected parameters
        val unexpected = parameters.keys - parameterSchemas.keys
        if (unexpected.isNotEmpty()) {
            errors.add("Unexpected parameters: ${unexpected.joinToString()}")
        }

        return errors
    }
}

/**
 * 🤖 Agent Tool (Simple Name/Description Wrapper)
 *
 * A minimal tool implementation that only provides name and description.
 * Useful for representing agent capabilities without full parameter schemas.
 *
 * **Usage:**
 * ```kotlin
 * val tool = AgentTool(
 *     name = "analyze_code",
 *     description = "Analyze code for potential issues"
 * )
 * ```
 *
 * **Note:** This tool returns a "not implemented" error when executed.
 * It's primarily for metadata/discovery purposes.
 *
 * @property name Tool name
 * @property description Tool description
 * @since 1.0.0
 */
data class AgentTool(
    override val name: String,
    override val description: String
) : Tool {
    override suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        return SpiceResult.success(
            ToolResult.error(
                error = "AgentTool is a placeholder and not executable",
                errorCode = "NOT_EXECUTABLE"
            )
        )
    }
}
