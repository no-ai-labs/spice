package io.github.noailabs.spice

/**
 * 🔌 Tool Extension Functions for OpenAI Integration
 *
 * Provides convenient extension functions for converting Spice Tools
 * to OpenAI Function Calling specification format.
 *
 * @since 1.0.0
 */

/**
 * Convert Tool to OpenAI Function Calling Specification
 *
 * Converts a Spice Tool to the OpenAI function calling format,
 * which is also compatible with Anthropic, LangChain, and other frameworks.
 *
 * **Behavior:**
 * - For `SimpleTool`: Delegates to `tool.schema.toOpenAIFunctionSpec()`
 * - For other tools: Creates minimal schema from name/description only
 *
 * **Usage:**
 * ```kotlin
 * val tool = SimpleTool(
 *     name = "web_search",
 *     description = "Search the web",
 *     parameterSchemas = mapOf(
 *         "query" to ParameterSchema("string", "Search query", required = true)
 *     )
 * ) { params -> ToolResult.success("Results...") }
 *
 * val spec = tool.toOpenAIFunctionSpec()
 * ```
 *
 * **Integration with OpenAI SDK:**
 * ```kotlin
 * val tools = listOf(searchTool, calculatorTool, weatherTool)
 *
 * val completion = openAI.createChatCompletion(
 *     model = "gpt-4",
 *     messages = messages,
 *     tools = tools.map { it.toOpenAIFunctionSpec() }
 * )
 * ```
 *
 * @param strict Enable strict mode (default: false)
 * @return Map representing OpenAI function calling specification
 * @since 1.0.0
 */
fun Tool.toOpenAIFunctionSpec(strict: Boolean = false): Map<String, Any> {
    return when (this) {
        is SimpleTool -> this.schema.toOpenAIFunctionSpec(strict)
        else -> {
            // Fallback: create minimal schema from name/description only
            ToolSchema(
                name = this.name,
                description = this.description,
                parameters = emptyMap()
            ).toOpenAIFunctionSpec(strict)
        }
    }
}

/**
 * Alias for toOpenAIFunctionSpec
 *
 * Provides a shorter method name as documented in CLAUDE.md.
 *
 * **Usage:**
 * ```kotlin
 * val spec = tool.toToolSpec()
 * val strictSpec = tool.toToolSpec(strict = true)
 * ```
 *
 * @param strict Enable strict mode (default: false)
 * @return Map representing OpenAI function calling specification
 * @since 1.0.0
 */
fun Tool.toToolSpec(strict: Boolean = false): Map<String, Any> =
    toOpenAIFunctionSpec(strict)
