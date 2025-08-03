package io.github.noailabs.spice.mcp

import io.github.noailabs.spice.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

/**
 * 🔌 MCP (Model Context Protocol) Client for Spice
 * 
 * Communicates with MCP servers like mnemo to share context,
 * save patterns, and retrieve intelligent suggestions.
 */
class MCPClient(
    private val serverUrl: String = "http://localhost:8080",
    private val apiKey: String? = null
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    /**
     * Call an MCP tool/function
     */
    suspend fun callTool(
        toolName: String,
        parameters: Map<String, Any?> = emptyMap()
    ): JsonObject {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", toolName)
                put("arguments", parameters.toJsonObject())
            })
            put("id", System.currentTimeMillis())
        }
        
        val response = client.post("$serverUrl/mcp") {
            contentType(ContentType.Application.Json)
            setBody(request)
            apiKey?.let { bearerAuth(it) }
        }
        
        return response.body<JsonObject>()
    }
    
    /**
     * List available MCP tools
     */
    suspend fun listTools(): List<MCPTool> {
        val response = callTool("tools/list")
        val tools = response["result"]?.jsonObject?.get("tools")?.jsonArray
        
        return tools?.map { toolJson ->
            val tool = toolJson.jsonObject
            MCPTool(
                name = tool["name"]?.jsonPrimitive?.content ?: "",
                description = tool["description"]?.jsonPrimitive?.content ?: "",
                parameters = tool["inputSchema"]?.jsonObject ?: JsonObject(emptyMap())
            )
        } ?: emptyList()
    }
    
    /**
     * Close the client
     */
    fun close() {
        client.close()
    }
}

/**
 * Represents an MCP tool definition
 */
data class MCPTool(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

/**
 * Extension to convert Map to JsonObject
 */
fun Map<String, Any?>.toJsonObject(): JsonObject = buildJsonObject {
    this@toJsonObject.forEach { (key, value) ->
        when (value) {
            null -> put(key, JsonNull)
            is String -> put(key, value)
            is Number -> put(key, value)
            is Boolean -> put(key, value)
            is Map<*, *> -> put(key, (value as Map<String, Any?>).toJsonObject())
            is List<*> -> putJsonArray(key) {
                value.forEach { item ->
                    when (item) {
                        null -> add(JsonNull)
                        is String -> add(item)
                        is Number -> add(item)
                        is Boolean -> add(item)
                        is Map<*, *> -> add((item as Map<String, Any?>).toJsonObject())
                        else -> add(item.toString())
                    }
                }
            }
            else -> put(key, value.toString())
        }
    }
}