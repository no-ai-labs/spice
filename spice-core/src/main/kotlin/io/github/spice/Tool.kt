package io.github.spice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JVM-Autogen의 Tool 인터페이스
 * Agent가 사용할 수 있는 도구들을 정의합니다.
 */
interface Tool {
    val name: String
    val description: String
    val schema: ToolSchema
    
    /**
     * 도구 실행
     */
    suspend fun execute(parameters: Map<String, Any>): ToolResult
    
    /**
     * 도구가 특정 파라미터를 처리할 수 있는지 확인
     */
    fun canExecute(parameters: Map<String, Any>): Boolean = true
}

/**
 * 도구 스키마 정의
 */
@Serializable
data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterSchema>
)

/**
 * 파라미터 스키마
 */
@Serializable
data class ParameterSchema(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val default: JsonElement? = null
)

/**
 * 도구 실행 결과
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
 * 기본 Tool 구현체
 */
abstract class BaseTool(
    override val name: String,
    override val description: String,
    override val schema: ToolSchema
) : Tool {
    
    protected fun validateParameters(parameters: Map<String, Any>): Boolean {
        return schema.parameters.all { (paramName, paramSchema) ->
            if (paramSchema.required) {
                parameters.containsKey(paramName)
            } else {
                true
            }
        }
    }
    
    override fun canExecute(parameters: Map<String, Any>): Boolean {
        return validateParameters(parameters)
    }
}

/**
 * 웹 검색 도구
 */
class WebSearchTool : BaseTool(
    name = "web_search",
    description = "웹에서 정보를 검색합니다",
    schema = ToolSchema(
        name = "web_search",
        description = "웹 검색 도구",
        parameters = mapOf<String, ParameterSchema>(
            "query" to ParameterSchema("string", "검색할 키워드", required = true),
            "limit" to ParameterSchema("number", "검색 결과 수", required = false)
        )
    )
) {
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        val query = parameters["query"] as? String
            ?: return ToolResult.error("검색 키워드가 필요합니다")
        
        val limit = (parameters["limit"] as? Number)?.toInt() ?: 5
        
        // 실제 웹 검색 로직 (여기서는 모의 구현)
        val results = simulateWebSearch(query, limit)
        
        return ToolResult.success(
            result = results.joinToString("\n") { "- $it" },
            metadata = mapOf("query" to query, "resultCount" to results.size.toString())
        )
    }
    
    private fun simulateWebSearch(query: String, limit: Int): List<String> {
        return (1..limit).map { "검색 결과 $it: $query 관련 정보" }
    }
}

/**
 * 파일 읽기 도구
 */
class FileReadTool : BaseTool(
    name = "file_read",
    description = "파일을 읽습니다",
    schema = ToolSchema(
        name = "file_read",
        description = "파일 읽기 도구",
        parameters = mapOf<String, ParameterSchema>(
            "path" to ParameterSchema("string", "읽을 파일 경로", required = true)
        )
    )
) {
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        val path = parameters["path"] as? String
            ?: return ToolResult.error("파일 경로가 필요합니다")
        
        return try {
            val content = java.io.File(path).readText()
            ToolResult.success(
                result = content,
                metadata = mapOf("path" to path, "size" to content.length.toString())
            )
        } catch (e: Exception) {
            ToolResult.error("파일 읽기 실패: ${e.message}")
        }
    }
}

/**
 * 파일 쓰기 도구
 */
class FileWriteTool : BaseTool(
    name = "file_write",
    description = "파일에 내용을 씁니다",
    schema = ToolSchema(
        name = "file_write",
        description = "파일 쓰기 도구",
        parameters = mapOf<String, ParameterSchema>(
            "path" to ParameterSchema("string", "쓸 파일 경로", required = true),
            "content" to ParameterSchema("string", "파일 내용", required = true)
        )
    )
) {
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        val path = parameters["path"] as? String
            ?: return ToolResult.error("파일 경로가 필요합니다")
        
        val content = parameters["content"] as? String
            ?: return ToolResult.error("파일 내용이 필요합니다")
        
        return try {
            java.io.File(path).writeText(content)
            ToolResult.success(
                result = "파일 쓰기 완료",
                metadata = mapOf("path" to path, "size" to content.length.toString())
            )
        } catch (e: Exception) {
            ToolResult.error("파일 쓰기 실패: ${e.message}")
        }
    }
} 