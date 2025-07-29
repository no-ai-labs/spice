@file:JvmName("AgentToolSerializerSample")

package samples.basic

import io.github.noailabs.spice.model.*
import io.github.noailabs.spice.serialization.SpiceSerializer
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonSchema
import io.github.noailabs.spice.model.AgentToolSerializer.saveToFile
import io.github.noailabs.spice.model.AgentToolSerializer.loadFromFile
import io.github.noailabs.spice.serialization.SpiceSerializer.validateJsonSchema
import io.github.noailabs.spice.ToolResult
import kotlinx.serialization.json.*
import java.io.File

/**
 * 🔄 AgentTool Serialization Sample
 * 
 * Demonstrates JSON Schema conversion and file persistence
 */
fun main() {
    // 1. Create a complex AgentTool
    val weatherTool = agentTool("weather-api") {
        description("Get weather information for a location")
        
        parameters {
            string("location", "City name or coordinates", required = true)
            string("units", "Temperature units (celsius/fahrenheit)", required = false)
            boolean("detailed", "Include detailed forecast", required = false)
            array("days", "Days to forecast (1-7)", required = false)
        }
        
        tags("weather", "api", "external")
        metadata("version", "3.0")
        metadata("api-version", "v2")
        metadata("rate-limit", "100/hour")
        
        implementationType("http-api", mapOf(
            "endpoint" to "https://api.weather.com/v2/forecast",
            "method" to "GET",
            "auth" to "api-key-header"
        ))
        
        // Mock implementation
        implement { params ->
            ToolResult.success("Weather: Sunny, 22°C")
        }
    }
    
    // 2. Convert to JSON Schema
    println("📋 JSON Schema Generation:")
    val jsonSchema = weatherTool.toJsonSchema()
    println(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), jsonSchema))
    
    // 3. Validate the schema
    println("\n✅ Schema Validation:")
    when (val result = validateJsonSchema(jsonSchema)) {
        is ValidationResult.Success -> println("  Schema is valid!")
        is ValidationResult.Error -> {
            println("  Schema has errors:")
            result.messages.forEach { println("    - $it") }
        }
    }
    
    // 4. Save to file
    val tempDir = File("temp-tools")
    tempDir.mkdirs()
    
    val jsonFile = File(tempDir, "weather-api.json")
    weatherTool.saveToFile(jsonFile.absolutePath, SerializationFormat.JSON)
    println("\n💾 Saved to: ${jsonFile.absolutePath}")
    
    // 5. Load from file
    val loadedTool = loadFromFile(jsonFile.absolutePath)
    println("\n📂 Loaded tool:")
    println("  Name: ${loadedTool.name}")
    println("  Description: ${loadedTool.description}")
    println("  Tags: ${loadedTool.tags}")
    println("  Parameters: ${loadedTool.parameters.keys}")
    
    // 6. Create from JSON Schema string
    val customSchema = buildJsonObject {
        put("\$schema", "https://json-schema.org/draft-07/schema#")
        put("title", "text-analyzer")
        put("description", "Analyze text content")
        put("type", "object")
        putJsonArray("required") { add("text") }
        putJsonObject("properties") {
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text to analyze")
            }
            putJsonObject("language") {
                put("type", "string")
                put("description", "Language code")
                putJsonArray("enum") {
                    add("en")
                    add("es")
                    add("fr")
                }
            }
        }
        putJsonArray("x-tags") {
            add("nlp")
            add("analysis")
        }
    }
    
    val toolFromSchema = SpiceSerializer.agentToolFromJsonSchema(customSchema)
    println("\n🔧 Tool from custom schema:")
    println("  Name: ${toolFromSchema.name}")
    println("  Required params: ${toolFromSchema.parameters.filterValues { it.required }.keys}")
    
    // 7. Demonstrate round-trip conversion
    println("\n🔄 Round-trip test:")
    val original = agentTool("round-trip-test") {
        description("Test round-trip conversion")
        parameters {
            string("input", "Test input")
            number("count", "Item count") 
        }
        tags("test")
    }
    
    val schema = original.toJsonSchema()
    val reconstructed = SpiceSerializer.agentToolFromJsonSchema(schema)
    
    println("  Original name: ${original.name}")
    println("  Reconstructed name: ${reconstructed.name}")
    println("  Parameters match: ${original.parameters == reconstructed.parameters}")
    println("  Tags match: ${original.tags == reconstructed.tags}")
    
    // 8. Example of GUI-friendly schema output
    println("\n🎨 GUI-friendly schema excerpt:")
    val guiSchema = weatherTool.toJsonSchema()
    val properties = guiSchema["properties"]?.jsonObject ?: emptyMap()
    
    properties.forEach { (name, def) ->
        val obj = def.jsonObject
        println("  Field: $name")
        println("    Type: ${obj["type"]?.jsonPrimitive?.content}")
        println("    Description: ${obj["description"]?.jsonPrimitive?.content}")
        println("    Required: ${guiSchema["required"]?.jsonArray?.contains(JsonPrimitive(name)) ?: false}")
    }
    
    // Clean up
    tempDir.deleteRecursively()
    println("\n🧹 Cleaned up temporary files")
} 