package io.github.noailabs.spice.mcp

import io.github.noailabs.spice.*
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonObject
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

