package io.github.noailabs.spice.model

import io.github.noailabs.spice.ParameterSchema
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonSchema
import io.github.noailabs.spice.serialization.SpiceSerializer.agentToolFromJsonSchema
import io.github.noailabs.spice.serialization.SpiceSerializer.validateJsonSchema
import io.github.noailabs.spice.serialization.ValidationResult
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.test.assertNotNull

/**
 * Test AgentToolSerializer functionality
 */
class AgentToolSerializerTest {
    
    @Test
    fun `test toJsonSchema generates valid JSON Schema`() {
        // Create test tool
        val tool = agentTool("test-tool") {
            description("Test tool for serialization")
            
            parameters {
                string("name", "User name", required = true)
                number("age", "User age", required = false)
                boolean("active", "Is active", required = false)
            }
            
            tags("test", "sample")
            metadata("version", "1.0")
            
            implement { ToolResult.success("") }
        }
        
        // Convert to JSON Schema
        val schema = tool.toJsonSchema()
        
        // Verify basic structure
        assertEquals("https://json-schema.org/draft-07/schema#", schema["\$schema"]?.jsonPrimitive?.content)
        assertEquals("test-tool", schema["title"]?.jsonPrimitive?.content)
        assertEquals("Test tool for serialization", schema["description"]?.jsonPrimitive?.content)
        assertEquals("object", schema["type"]?.jsonPrimitive?.content)
        assertEquals(false, schema["additionalProperties"]?.jsonPrimitive?.boolean)
        
        // Verify required fields
        val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }
        assertNotNull(required)
        assertTrue(required.contains("name"))
        assertFalse(required.contains("age"))
        
        // Verify properties
        val properties = schema["properties"]?.jsonObject
        assertNotNull(properties)
        assertEquals(3, properties.size)
        
        val nameProperty = properties["name"]?.jsonObject
        assertNotNull(nameProperty)
        assertEquals("string", nameProperty["type"]?.jsonPrimitive?.content)
        assertEquals("User name", nameProperty["description"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun `test fromJsonSchema correctly reconstructs AgentTool`() {
        // Create JSON Schema
        val schema = buildJsonObject {
            put("\$schema", "https://json-schema.org/draft-07/schema#")
            put("title", "reconstructed-tool")
            put("description", "Tool from schema")
            put("type", "object")
            
            putJsonArray("required") {
                add("param1")
            }
            
            putJsonObject("properties") {
                putJsonObject("param1") {
                    put("type", "string")
                    put("description", "First parameter")
                }
                putJsonObject("param2") {
                    put("type", "number")
                    put("description", "Second parameter")
                    put("default", JsonPrimitive(42))
                }
            }
            
            putJsonArray("x-tags") {
                add("tag1")
                add("tag2")
            }
            
            putJsonObject("x-metadata") {
                put("author", "Test")
                put("version", "2.0")
            }
            
            putJsonObject("x-implementation") {
                put("type", "http-api")
                putJsonObject("details") {
                    put("endpoint", "/api/test")
                }
            }
        }
        
        // Reconstruct tool
        val tool = agentToolFromJsonSchema(schema)
        
        // Verify basic properties
        assertEquals("reconstructed-tool", tool.name)
        assertEquals("Tool from schema", tool.description)
        
        // Verify parameters
        assertEquals(2, tool.parameters.size)
        assertTrue(tool.parameters.containsKey("param1"))
        assertTrue(tool.parameters.containsKey("param2"))
        
        val param1 = tool.parameters["param1"]
        assertNotNull(param1)
        assertEquals("string", param1.type)
        assertEquals(true, param1.required)
        
        val param2 = tool.parameters["param2"]
        assertNotNull(param2)
        assertEquals("number", param2.type)
        assertEquals(false, param2.required)
        assertEquals(JsonPrimitive(42), param2.default)
        
        // Verify metadata
        assertEquals(listOf("tag1", "tag2"), tool.tags)
        assertEquals(mapOf("author" to "Test", "version" to "2.0"), tool.metadata)
        
        // Verify implementation
        assertEquals("http-api", tool.implementationType)
        assertEquals(mapOf("endpoint" to "/api/test"), tool.implementationDetails)
    }
    
    @Test
    fun `test round-trip conversion preserves data`() {
        // Create original tool
        val original = agentTool("round-trip") {
            description("Test round-trip conversion")
            
            parameters {
                string("text", "Text input", required = true)
                array("items", "Item list")
                `object`("config", "Configuration object")
            }
            
            tags("test", "round-trip", "validation")
            metadata("created", "2024-01-01")
            metadata("updated", "2024-01-15")
            
            implementationType("custom", mapOf("handler" to "CustomHandler"))
        }
        
        // Convert to schema and back
        val schema = original.toJsonSchema()
        val reconstructed = agentToolFromJsonSchema(schema)
        
        // Verify everything matches
        assertEquals(original.name, reconstructed.name)
        assertEquals(original.description, reconstructed.description)
        assertEquals(original.parameters.size, reconstructed.parameters.size)
        assertEquals(original.tags, reconstructed.tags)
        assertEquals(original.metadata, reconstructed.metadata)
        assertEquals(original.implementationType, reconstructed.implementationType)
        assertEquals(original.implementationDetails, reconstructed.implementationDetails)
        
        // Verify individual parameters
        original.parameters.forEach { (name, originalParam) ->
            val reconstructedParam = reconstructed.parameters[name]
            assertNotNull(reconstructedParam)
            assertEquals(originalParam.type, reconstructedParam.type)
            assertEquals(originalParam.description, reconstructedParam.description)
            assertEquals(originalParam.required, reconstructedParam.required)
        }
    }
    
    @Test
    fun `test validateJsonSchema catches errors`() {
        // Valid schema
        val validSchema = buildJsonObject {
            put("title", "valid-tool")
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("param1") {
                    put("type", "string")
                }
            }
        }
        
        val validResult = validateJsonSchema(validSchema)
        assertTrue(validResult is ValidationResult.Success)
        
        // Missing title
        val missingTitle = buildJsonObject {
            put("type", "object")
        }
        
        val missingTitleResult = validateJsonSchema(missingTitle)
        assertTrue(missingTitleResult is ValidationResult.Error)
        assertTrue((missingTitleResult as ValidationResult.Error).messages.any { 
            it.contains("title") 
        })
        
        // Wrong type
        val wrongType = buildJsonObject {
            put("title", "wrong-type")
            put("type", "array") // Should be "object"
        }
        
        val wrongTypeResult = validateJsonSchema(wrongType)
        assertTrue(wrongTypeResult is ValidationResult.Error)
        
        // Required field not in properties
        val invalidRequired = buildJsonObject {
            put("title", "invalid-required")
            put("type", "object")
            putJsonArray("required") {
                add("nonexistent")
            }
            putJsonObject("properties") {
                putJsonObject("existing") {
                    put("type", "string")
                }
            }
        }
        
        val invalidRequiredResult = validateJsonSchema(invalidRequired)
        assertTrue(invalidRequiredResult is ValidationResult.Error)
        assertTrue((invalidRequiredResult as ValidationResult.Error).messages.any { 
            it.contains("nonexistent") 
        })
    }
    
    @Test
    fun `test JSON Schema with advanced property types`() {
        // Create schema with enum and constraints
        val schema = buildJsonObject {
            put("title", "advanced-tool")
            put("type", "object")
            
            putJsonObject("properties") {
                putJsonObject("status") {
                    put("type", "string")
                    putJsonArray("enum") {
                        add("active")
                        add("inactive")
                        add("pending")
                    }
                }
                putJsonObject("count") {
                    put("type", "integer")
                    put("minimum", 0)
                    put("maximum", 100)
                }
            }
        }
        
        // Should parse without errors
        val tool = agentToolFromJsonSchema(schema)
        assertEquals("advanced-tool", tool.name)
        
        // Note: enum/min/max constraints are not yet fully preserved in ParameterSchema
        // This is expected as ParameterSchema doesn't have these fields yet
        assertTrue(tool.parameters.containsKey("status"))
        assertTrue(tool.parameters.containsKey("count"))
    }
} 