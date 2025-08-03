package io.github.noailabs.spice.model

import io.github.noailabs.spice.ParameterSchema
import io.github.noailabs.spice.serialization.ValidationResult
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * 🔄 AgentTool Serialization Support
 * 
 * @deprecated Use SpiceSerializer for unified JSON handling across all Spice components
 * 
 * This class is maintained for backward compatibility but all functionality
 * has been moved to SpiceSerializer for consistency.
 */
@Deprecated(
    message = "Use SpiceSerializer for unified JSON handling",
    replaceWith = ReplaceWith(
        "SpiceSerializer",
        "io.github.noailabs.spice.serialization.SpiceSerializer"
    )
)
object AgentToolSerializer {
    
    private const val JSON_SCHEMA_DRAFT = "https://json-schema.org/draft-07/schema#"
    
    /**
     * Convert AgentTool to JSON Schema format
     * @deprecated Use SpiceSerializer.toJsonSchema() instead
     */
    @Deprecated(
        message = "Use SpiceSerializer.toJsonSchema() instead",
        replaceWith = ReplaceWith(
            "this.toJsonSchema()",
            "io.github.noailabs.spice.serialization.SpiceSerializer.toJsonSchema"
        )
    )
    fun AgentTool.toJsonSchema(): JsonObject {
        // Delegate to SpiceSerializer
        return io.github.noailabs.spice.serialization.SpiceSerializer.run {
            this@toJsonSchema.toJsonSchema()
        }
    }
    
    /**
     * Create AgentTool from JSON Schema
     * @deprecated Use SpiceSerializer.agentToolFromJsonSchema() instead
     */
    @Deprecated(
        message = "Use SpiceSerializer.agentToolFromJsonSchema() instead",
        replaceWith = ReplaceWith(
            "SpiceSerializer.agentToolFromJsonSchema(schema)",
            "io.github.noailabs.spice.serialization.SpiceSerializer"
        )
    )
    fun fromJsonSchema(schema: JsonObject): AgentTool {
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
        
        // Delegate to SpiceSerializer
        return io.github.noailabs.spice.serialization.SpiceSerializer.agentToolFromJsonSchema(schema)
    }
    
    /**
     * Validate JSON Schema
     * @deprecated Use SpiceSerializer.validateJsonSchema() instead
     */
    @Deprecated(
        message = "Use SpiceSerializer.validateJsonSchema() instead",
        replaceWith = ReplaceWith(
            "SpiceSerializer.validateJsonSchema(schema)",
            "io.github.noailabs.spice.serialization.SpiceSerializer"
        )
    )
    fun validateJsonSchema(schema: JsonObject): ValidationResult {
        // Delegate to SpiceSerializer
        return io.github.noailabs.spice.serialization.SpiceSerializer.validateJsonSchema(schema)
    }
    
    /**
     * Convert AgentTool to YAML string
     */
    fun AgentTool.toYaml(): String {
        val jsonSchema = this.toJsonSchema()
        return jsonSchemaToYaml(jsonSchema)
    }
    
    /**
     * Create AgentTool from YAML string
     */
    fun fromYaml(yaml: String): AgentTool {
        val jsonSchema = yamlToJsonSchema(yaml)
        return fromJsonSchema(jsonSchema)
    }
    
    /**
     * Convert JSON Schema to YAML format
     * Note: This is a simplified version. For production, use a proper YAML library
     */
    private fun jsonSchemaToYaml(schema: JsonObject): String {
        val json = Json { 
            prettyPrint = true
            encodeDefaults = true
        }
        val jsonString = json.encodeToString(schema)
        
        // Basic JSON to YAML conversion (simplified)
        // In production, use kaml or jackson-dataformat-yaml
        return jsonString
            .replace("{", "")
            .replace("}", "")
            .replace("\"", "")
            .replace(",", "")
            .replace("[", "\n  -")
            .replace("]", "")
            .trim()
    }
    
    /**
     * Convert YAML to JSON Schema using simple regex parsing
     * Note: This is a basic implementation. For production, use proper YAML library like kaml
     */
    private fun yamlToJsonSchema(yaml: String): JsonObject {
        // Basic YAML to JSON Schema conversion
        val lines = yaml.lines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        val jsonMap = mutableMapOf<String, Any?>()
        
        var currentKey = ""
        var inParameters = false
        val parameters = mutableMapOf<String, Map<String, Any?>>()
        var currentParam: MutableMap<String, Any?>? = null
        var currentParamName = ""
        
        for (line in lines) {
            val trimmed = line.trim()
            val indent = line.takeWhile { it == ' ' }.length
            
            when {
                trimmed.endsWith(":") -> {
                    val key = trimmed.dropLast(1)
                    when (indent) {
                        0 -> {
                            currentKey = key
                            if (key == "parameters") inParameters = true
                        }
                        2 -> {
                            if (inParameters) {
                                currentParamName = key
                                currentParam = mutableMapOf()
                            }
                        }
                    }
                }
                trimmed.contains(":") -> {
                    val (key, value) = trimmed.split(":", limit = 2).map { it.trim() }
                    val parsedValue = parseYamlValue(value)
                    
                    when (indent) {
                        2 -> {
                            if (!inParameters) {
                                jsonMap[key] = parsedValue
                            }
                        }
                        4 -> {
                            if (inParameters && currentParam != null) {
                                currentParam[key] = parsedValue
                                if (key == "required" || key == "description" || key == "type") {
                                    parameters[currentParamName] = currentParam.toMap()
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Build JSON Schema
        return buildJsonObject {
            put("name", jsonMap["name"]?.toString() ?: "")
            put("description", jsonMap["description"]?.toString() ?: "")
            putJsonObject("input_schema") {
                put("type", "object")
                putJsonObject("properties") {
                    parameters.forEach { (name, props) ->
                        putJsonObject(name) {
                            put("type", props["type"]?.toString() ?: "string")
                            put("description", props["description"]?.toString() ?: "")
                        }
                    }
                }
                putJsonArray("required") {
                    parameters.filter { (_, props) -> 
                        props["required"] == true 
                    }.keys.forEach { add(it) }
                }
            }
        }
    }
    
    /**
     * Parse YAML value (simple types only)
     */
    private fun parseYamlValue(value: String): Any? {
        val trimmed = value.trim()
        return when {
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed == "null" || trimmed.isEmpty() -> null
            trimmed.startsWith("\"") && trimmed.endsWith("\"") -> trimmed.drop(1).dropLast(1)
            trimmed.startsWith("'") && trimmed.endsWith("'") -> trimmed.drop(1).dropLast(1)
            trimmed.toIntOrNull() != null -> trimmed.toInt()
            trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
            else -> trimmed
        }
    }
    
    /**
     * Save AgentTool to file
     */
    fun AgentTool.saveToFile(filePath: String, format: SerializationFormat = SerializationFormat.JSON) {
        val content = when (format) {
            SerializationFormat.JSON -> Json.encodeToString(this.toJsonSchema())
            SerializationFormat.YAML -> this.toYaml()
        }
        java.io.File(filePath).writeText(content)
    }
    
    /**
     * Load AgentTool from file
     */
    fun loadFromFile(filePath: String): AgentTool {
        val content = java.io.File(filePath).readText()
        return when {
            filePath.endsWith(".yaml") || filePath.endsWith(".yml") -> fromYaml(content)
            filePath.endsWith(".json") -> fromJsonSchema(Json.decodeFromString(content))
            else -> throw IllegalArgumentException("Unsupported file format: $filePath")
        }
    }
}

// ValidationResult moved to SpiceSerializer

/**
 * Serialization format
 */
enum class SerializationFormat {
    JSON, YAML
}

/**
 * Extended ParameterSchema for additional constraints
 */
val ParameterSchema.enum: List<String>? get() = null // Extension point
val ParameterSchema.minimum: Double? get() = null // Extension point  
val ParameterSchema.maximum: Double? get() = null // Extension point
val ParameterSchema.itemType: String? get() = null // Extension point 