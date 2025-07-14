package io.github.spice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Tool interface for JVM-Autogen
 * Defines tools that agents can use.
 */
interface Tool {
    val name: String
    val description: String
    val schema: ToolSchema
    
    /**
     * Tool execution
     */
    suspend fun execute(parameters: Map<String, Any>): ToolResult
    
    /**
     * Check if tool can process specific parameters
     */
    fun canExecute(parameters: Map<String, Any>): Boolean = true
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
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        fun success(result: String, metadata: Map<String, String> = emptyMap()): ToolResult {
            return ToolResult(success = true, result = result, metadata = metadata)
        }
        
        fun error(error: String, metadata: Map<String, String> = emptyMap()): ToolResult {
            return ToolResult(success = false, error = error, metadata = metadata)
        }
    }
}

/**
 * Basic Tool implementation
 */
abstract class BaseTool : Tool {
    override fun canExecute(parameters: Map<String, Any>): Boolean {
        return true
    }
}

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
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        val query = parameters["query"] as? String
            ?: return ToolResult.error("tool.web_search.error.query_required".i18n())
        
        val limit = (parameters["limit"] as? Number)?.toInt() ?: 5
        
        // Actual web search logic (mock implementation here)
        val results = try {
            searchWeb(query, limit)
        } catch (e: Exception) {
            return ToolResult.error("tool.web_search.error.search_failed".i18n())
        }
        
        return ToolResult.success(
            result = results.joinToString("\n") { "- $it" },
            metadata = mapOf("query" to query, "resultCount" to results.size.toString())
        )
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
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        val path = parameters["path"] as? String
            ?: return ToolResult.error("tool.file_read.error.path_required".i18n())
        
        return try {
            val content = java.io.File(path).readText()
            ToolResult.success(
                result = content,
                metadata = mapOf("path" to path, "size" to content.length.toString())
            )
        } catch (e: Exception) {
            ToolResult.error("tool.file_read.error.read_failed".i18n())
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
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        val path = parameters["path"] as? String
            ?: return ToolResult.error("tool.file_write.error.path_required".i18n())
        
        val content = parameters["content"] as? String
            ?: return ToolResult.error("tool.file_write.error.content_required".i18n())
        
        return try {
            java.io.File(path).writeText(content)
            ToolResult.success(
                result = "tool.file_write.success".i18n(),
                metadata = mapOf("path" to path, "size" to content.length.toString())
            )
        } catch (e: Exception) {
            ToolResult.error("tool.file_write.error.write_failed".i18n())
        }
    }
} 