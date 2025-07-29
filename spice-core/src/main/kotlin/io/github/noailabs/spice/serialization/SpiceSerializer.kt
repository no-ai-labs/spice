package io.github.noailabs.spice.serialization

import kotlinx.serialization.json.*
import io.github.noailabs.spice.*
import io.github.noailabs.spice.model.AgentTool
import java.time.Instant

/**
 * 🔄 Unified JSON Serialization for Spice Framework
 * 
 * Provides consistent JSON conversion for all Spice components
 * with proper type handling and structure preservation.
 */
object SpiceSerializer {
    
    /**
     * Convert any value to JsonElement with proper type handling
     */
    fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Enum<*> -> JsonPrimitive(this.name)
        is List<*> -> JsonArray(this.map { it.toJsonElement() })
        is Array<*> -> JsonArray(this.map { it.toJsonElement() })
        is Map<*, *> -> buildJsonObject {
            this@toJsonElement.forEach { (k, v) ->
                val key = k.toString()
                put(key, v.toJsonElement())
            }
        }
        is Instant -> JsonPrimitive(this.toString())
        else -> {
            // Try to convert complex objects to structured JSON
            when (this) {
                is Agent -> this.toJson()
                is Tool -> this.toJson()
                is AgentTool -> this.toJson()
                is VectorStoreConfig -> this.toJson()
                is AgentPersona -> this.toJson()
                else -> JsonPrimitive(this.toString()) // Last resort
            }
        }
    }
    
    /**
     * Convert Map to JsonObject with proper type handling
     */
    fun Map<String, Any?>.toJsonObject(): JsonObject = buildJsonObject {
        this@toJsonObject.forEach { (key, value) ->
            put(key, value.toJsonElement())
        }
    }
    
    /**
     * Improved metadata converter (replaces the hungry one!)
     */
    fun toJsonMetadata(map: Map<String, Any>): Map<String, JsonElement> {
        return map.mapValues { (_, value) ->
            value.toJsonElement()
        }
    }
    
    // ===== Agent Serialization =====
    
    /**
     * Convert Agent to JSON representation
     */
    fun Agent.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("description", description)
        putJsonArray("capabilities") {
            capabilities.forEach { add(it) }
        }
        put("ready", isReady())
        
        // Add tools if available
        val agentTools = (this@toJson as? io.github.noailabs.spice.dsl.CoreAgent)?.getTools()
        if (!agentTools.isNullOrEmpty()) {
            putJsonArray("tools") {
                agentTools.forEach { tool ->
                    add(tool.name)
                }
            }
        }
        
        // Add metadata if available
        (this@toJson as? HasMetadata)?.let { 
            put("metadata", it.metadata.toJsonObject())
        }
    }
    
    /**
     * Create Agent from JSON (basic implementation)
     */
    fun agentFromJson(json: JsonObject): AgentDescriptor {
        return AgentDescriptor(
            id = json["id"]?.jsonPrimitive?.content ?: "",
            name = json["name"]?.jsonPrimitive?.content ?: "",
            description = json["description"]?.jsonPrimitive?.content ?: "",
            capabilities = json["capabilities"]?.jsonArray?.map { 
                it.jsonPrimitive.content 
            } ?: emptyList(),
            toolNames = json["tools"]?.jsonArray?.map {
                it.jsonPrimitive.content
            } ?: emptyList()
        )
    }
    
    // ===== Tool Serialization =====
    
    /**
     * Convert Tool to JSON representation
     */
    fun Tool.toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        
        // Schema
        putJsonObject("schema") {
            put("name", schema.name)
            put("description", schema.description)
            putJsonObject("parameters") {
                schema.parameters.forEach { (paramName, paramSchema) ->
                    putJsonObject(paramName) {
                        put("type", paramSchema.type)
                        put("description", paramSchema.description)
                        put("required", paramSchema.required)
                        paramSchema.default?.let { put("default", it) }
                    }
                }
            }
        }
        
        // Add source type if available
        (this as? HasMetadata)?.metadata?.get("source")?.let {
            put("source", it.toString())
        }
    }
    
    // ===== AgentTool Serialization =====
    
    /**
     * Convert AgentTool to clean JSON (not JSON Schema)
     */
    fun AgentTool.toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        putJsonArray("tags") {
            tags.forEach { add(it) }
        }
        put("metadata", metadata.toJsonObject())
        put("implementationType", implementationType)
        put("implementationDetails", implementationDetails.toJsonObject())
        
        // Parameters
        putJsonObject("parameters") {
            parameters.forEach { (name, schema) ->
                putJsonObject(name) {
                    put("type", schema.type)
                    put("description", schema.description)
                    put("required", schema.required)
                    schema.default?.let { put("default", it) }
                }
            }
        }
    }
    
    /**
     * Convert AgentTool to JSON Schema format
     * Moved from AgentToolSerializer for consistency
     */
    fun AgentTool.toJsonSchema(): JsonObject = buildJsonObject {
        // Standard JSON Schema fields
        put("\$schema", "https://json-schema.org/draft-07/schema#")
        put("title", name)
        put("description", description)
        put("type", "object")
        put("additionalProperties", false)
        
        // Required fields
        val requiredParams = parameters.filterValues { it.required }.keys
        if (requiredParams.isNotEmpty()) {
            putJsonArray("required") {
                requiredParams.forEach { add(it) }
            }
        }
        
        // Properties with proper type handling
        putJsonObject("properties") {
            parameters.forEach { (key, param) ->
                putJsonObject(key) {
                    put("type", param.type)
                    put("description", param.description)
                    param.default?.let { put("default", it) }
                }
            }
        }
        
        // Extended metadata (x- prefix for custom fields)
        if (tags.isNotEmpty()) {
            putJsonArray("x-tags") {
                tags.forEach { add(it) }
            }
        }
        
        if (metadata.isNotEmpty()) {
            putJsonObject("x-metadata") {
                metadata.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            }
        }
        
        // Implementation details
        putJsonObject("x-implementation") {
            put("type", implementationType)
            if (implementationDetails.isNotEmpty()) {
                putJsonObject("details") {
                    implementationDetails.forEach { (k, v) -> 
                        put(k, JsonPrimitive(v)) 
                    }
                }
            }
        }
    }
    
    /**
     * Create AgentTool from JSON Schema
     * Moved from AgentToolSerializer for consistency
     */
    fun agentToolFromJsonSchema(schema: JsonObject): AgentTool {
        val name = schema["title"]?.jsonPrimitive?.content 
            ?: throw IllegalArgumentException("Missing 'title' in JSON Schema")
        
        val description = schema["description"]?.jsonPrimitive?.content 
            ?: "No description provided"
        
        // Extract required fields
        val requiredFields = schema["required"]?.jsonArray?.map { 
            it.jsonPrimitive.content 
        }?.toSet() ?: emptySet()
        
        // Parse parameters from properties
        val parameters = mutableMapOf<String, ParameterSchema>()
        schema["properties"]?.jsonObject?.forEach { (paramName, paramDef) ->
            val paramObj = paramDef.jsonObject
            
            parameters[paramName] = ParameterSchema(
                type = paramObj["type"]?.jsonPrimitive?.content ?: "string",
                description = paramObj["description"]?.jsonPrimitive?.content ?: "",
                required = paramName in requiredFields,
                default = paramObj["default"]
            )
        }
        
        // Extract tags
        val tags = schema["x-tags"]?.jsonArray?.map { 
            it.jsonPrimitive.content 
        } ?: emptyList()
        
        // Extract metadata
        val metadata = mutableMapOf<String, String>()
        schema["x-metadata"]?.jsonObject?.forEach { (k, v) ->
            metadata[k] = v.jsonPrimitive.content
        }
        
        // Extract implementation details
        val implObj = schema["x-implementation"]?.jsonObject
        val implementationType = implObj?.get("type")?.jsonPrimitive?.content 
            ?: "kotlin-function"
        
        val implementationDetails = mutableMapOf<String, String>()
        implObj?.get("details")?.jsonObject?.forEach { (k, v) ->
            implementationDetails[k] = v.jsonPrimitive.content
        }
        
        return AgentTool(
            name = name,
            description = description,
            parameters = parameters,
            tags = tags,
            metadata = metadata,
            implementationType = implementationType,
            implementationDetails = implementationDetails
        )
    }
    
    /**
     * Validate JSON Schema
     * Moved from AgentToolSerializer for consistency  
     */
    fun validateJsonSchema(schema: JsonObject): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check required fields
        if (!schema.containsKey("title")) {
            errors.add("Missing required field: title")
        }
        
        if (!schema.containsKey("type") || schema["type"]?.jsonPrimitive?.content != "object") {
            errors.add("Schema type must be 'object'")
        }
        
        // Validate properties
        schema["properties"]?.jsonObject?.forEach { (propName, propDef) ->
            val propObj = propDef as? JsonObject
            if (propObj == null) {
                errors.add("Property '$propName' must be an object")
            } else {
                if (!propObj.containsKey("type")) {
                    errors.add("Property '$propName' missing 'type' field")
                }
            }
        }
        
        // Check that required fields exist in properties
        schema["required"]?.jsonArray?.forEach { req ->
            val reqName = req.jsonPrimitive.content
            if (schema["properties"]?.jsonObject?.containsKey(reqName) != true) {
                errors.add("Required field '$reqName' not found in properties")
            }
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    // ===== VectorStore Serialization =====
    
    /**
     * Convert VectorStoreConfig to JSON
     */
    fun VectorStoreConfig.toJson(): JsonObject = buildJsonObject {
        put("provider", provider)
        put("host", host)
        put("port", port)
        apiKey?.let { put("apiKey", "[REDACTED]") } // Don't expose API keys
        put("collection", collection)
        put("vectorSize", vectorSize)
        put("config", config.toJsonObject())
    }
    
    /**
     * Create VectorStoreConfig from JSON
     */
    fun vectorStoreConfigFromJson(json: JsonObject): VectorStoreConfig {
        return VectorStoreConfig(
            provider = json["provider"]?.jsonPrimitive?.content ?: "qdrant",
            host = json["host"]?.jsonPrimitive?.content ?: "localhost",
            port = json["port"]?.jsonPrimitive?.int ?: 6333,
            apiKey = json["apiKey"]?.jsonPrimitive?.contentOrNull,
            collection = json["collection"]?.jsonPrimitive?.content ?: "default",
            vectorSize = json["vectorSize"]?.jsonPrimitive?.int ?: 384,
            config = json["config"]?.jsonObject?.let { obj ->
                obj.entries.associate { (k, v) ->
                    k to v.jsonPrimitive.content
                }
            } ?: emptyMap()
        )
    }
    
    // ===== AgentPersona Serialization =====
    
    /**
     * Convert AgentPersona to JSON
     * Note: Simplified for now, full implementation would handle all nested types
     */
    fun AgentPersona.toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("personalityType", personalityType.name)
        put("communicationStyle", communicationStyle.name)
        putJsonArray("traits") {
            traits.forEach { add(it.name) }
        }
        // Complex nested objects simplified for now
        put("responsePatterns", "[Complex Object - See Full Implementation]")
        put("vocabulary", "[Complex Object - See Full Implementation]")
        put("behaviorModifiers", behaviorModifiers.toJsonObject())
    }
}

/**
 * Data class for Agent reconstruction
 */
data class AgentDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val capabilities: List<String>,
    val toolNames: List<String>
)

/**
 * Interface for objects with metadata
 */
interface HasMetadata {
    val metadata: Map<String, Any>
}

// ===== Extension Functions =====

/**
 * Extension to safely get content or null
 */
val JsonPrimitive.contentOrNull: String?
    get() = if (isString) content else null

/**
 * Extension to safely get int or null
 */
val JsonPrimitive.intOrNull: Int?
    get() = try { int } catch (_: Exception) { null }

// ===== Validation =====

/**
 * Validation result for JSON Schema
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val messages: List<String>) : ValidationResult()
} 