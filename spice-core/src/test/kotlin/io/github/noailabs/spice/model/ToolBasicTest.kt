package io.github.noailabs.spice.model

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic Tool tests with SpiceResult
 */
class ToolBasicTest {

    @Test
    fun `test tool executes successfully`() = runBlocking {
        val tool = SimpleTool(
            name = "calculator",
            description = "Simple calculator",
            parameterSchemas = mapOf(
                "a" to ParameterSchema("number", "First number", true),
                "b" to ParameterSchema("number", "Second number", true)
            )
        ) { params ->
            val a = (params["a"] as? Number)?.toInt() ?: 0
            val b = (params["b"] as? Number)?.toInt() ?: 0
            ToolResult.success((a + b).toString())
        }

        val result = tool.execute(mapOf("a" to 5, "b" to 3))

        assertTrue(result.isSuccess)
        result.fold(
            onSuccess = { toolResult ->
                assertTrue(toolResult.success)
                assertEquals("8", toolResult.result)
            },
            onFailure = { error ->
                throw AssertionError("Should not fail: ${error.message}")
            }
        )
    }

    @Test
    fun `test tool can check execution conditions`() = runBlocking {
        val tool = SimpleTool(
            name = "test",
            description = "Test tool",
            parameterSchemas = mapOf(
                "required" to ParameterSchema("string", "Required param", true)
            )
        ) { params ->
            ToolResult.success("OK")
        }

        // Test with required parameter
        assertTrue(tool.canExecute(mapOf("required" to "value")))

        // Test without required parameter
        assertTrue(!tool.canExecute(emptyMap()))
    }

    @Test
    fun `test AgentTool structure`() {
        val agentTool = AgentTool(
            name = "weatherTool",
            description = "Get weather information"
        )

        assertEquals("weatherTool", agentTool.name)
        assertEquals("Get weather information", agentTool.description)
    }

    @Test
    fun `test ToolSchema toOpenAIFunctionSpec with required parameters`() {
        val schema = ToolSchema(
            name = "web_search",
            description = "Search the web for information",
            parameters = mapOf(
                "query" to ParameterSchema("string", "Search query", required = true),
                "limit" to ParameterSchema("number", "Maximum results", required = false)
            )
        )

        val spec = schema.toOpenAIFunctionSpec()

        // Verify top-level fields
        assertEquals("function", spec["type"])  // ✅ New: type field
        assertEquals("web_search", spec["name"])
        assertEquals("Search the web for information", spec["description"])

        // Verify parameters structure
        val parameters = spec["parameters"] as Map<*, *>
        assertEquals("object", parameters["type"])

        // Verify properties
        val properties = parameters["properties"] as Map<*, *>
        assertEquals(2, properties.size)

        val queryProp = properties["query"] as Map<*, *>
        assertEquals("string", queryProp["type"])
        assertEquals("Search query", queryProp["description"])

        val limitProp = properties["limit"] as Map<*, *>
        assertEquals("number", limitProp["type"])
        assertEquals("Maximum results", limitProp["description"])

        // Verify required array
        val required = parameters["required"] as List<*>
        assertEquals(1, required.size)
        assertTrue(required.contains("query"))
        assertTrue(!required.contains("limit"))

        // Verify strict is not present (default)
        assertTrue(!spec.containsKey("strict"))
        assertTrue(!parameters.containsKey("additionalProperties"))
    }

    @Test
    fun `test ToolSchema toOpenAIFunctionSpec without required parameters`() {
        val schema = ToolSchema(
            name = "random_number",
            description = "Generate a random number",
            parameters = mapOf(
                "min" to ParameterSchema("number", "Minimum value", required = false),
                "max" to ParameterSchema("number", "Maximum value", required = false)
            )
        )

        val spec = schema.toOpenAIFunctionSpec()

        // When no parameters are required, the "required" field should be omitted
        val parameters = spec["parameters"] as Map<*, *>
        assertTrue(!parameters.containsKey("required"))
    }

    @Test
    fun `test ToolSchema toOpenAIFunctionSpec with default values`() {
        val schema = ToolSchema(
            name = "calculator",
            description = "Perform calculations",
            parameters = mapOf(
                "operation" to ParameterSchema(
                    type = "string",
                    description = "Operation to perform",
                    required = true
                )
            )
        )

        val spec = schema.toOpenAIFunctionSpec()

        val parameters = spec["parameters"] as Map<*, *>
        val properties = parameters["properties"] as Map<*, *>
        val operationProp = properties["operation"] as Map<*, *>

        assertEquals("string", operationProp["type"])
        assertEquals("Operation to perform", operationProp["description"])
    }

    @Test
    fun `test Tool toOpenAIFunctionSpec delegates to schema`() {
        val tool = SimpleTool(
            name = "test_tool",
            description = "A test tool",
            parameterSchemas = mapOf(
                "param1" to ParameterSchema("string", "First parameter", required = true)
            )
        ) { ToolResult.success("OK") }

        val spec = tool.toOpenAIFunctionSpec()

        assertEquals("test_tool", spec["name"])
        assertEquals("A test tool", spec["description"])

        val parameters = spec["parameters"] as Map<*, *>
        assertEquals("object", parameters["type"])

        val properties = parameters["properties"] as Map<*, *>
        assertTrue(properties.containsKey("param1"))

        val required = parameters["required"] as List<*>
        assertTrue(required.contains("param1"))
    }

    @Test
    fun `test ToolSchema toOpenAIFunctionSpec with complex example`() {
        val schema = ToolSchema(
            name = "create_task",
            description = "Create a new task in the system",
            parameters = mapOf(
                "title" to ParameterSchema("string", "Task title", required = true),
                "description" to ParameterSchema("string", "Task description", required = false),
                "priority" to ParameterSchema("number", "Priority level (1-5)", required = false),
                "assignee" to ParameterSchema("string", "User to assign task to", required = false),
                "tags" to ParameterSchema("array", "Task tags", required = false)
            )
        )

        val spec = schema.toOpenAIFunctionSpec()

        // Verify structure
        assertEquals("create_task", spec["name"])
        assertEquals("Create a new task in the system", spec["description"])

        val parameters = spec["parameters"] as Map<*, *>
        val properties = parameters["properties"] as Map<*, *>
        assertEquals(5, properties.size)

        // Verify only required parameters are in required array
        val required = parameters["required"] as List<*>
        assertEquals(1, required.size)
        assertEquals("title", required[0])

        // Verify all properties exist
        assertTrue(properties.containsKey("title"))
        assertTrue(properties.containsKey("description"))
        assertTrue(properties.containsKey("priority"))
        assertTrue(properties.containsKey("assignee"))
        assertTrue(properties.containsKey("tags"))

        // Verify types
        val titleProp = properties["title"] as Map<*, *>
        assertEquals("string", titleProp["type"])

        val priorityProp = properties["priority"] as Map<*, *>
        assertEquals("number", priorityProp["type"])

        val tagsProp = properties["tags"] as Map<*, *>
        assertEquals("array", tagsProp["type"])
    }

    @Test
    fun `test ToolSchema toOpenAIFunctionSpec with strict mode`() {
        val schema = ToolSchema(
            name = "get_weather",
            description = "Get current weather",
            parameters = mapOf(
                "location" to ParameterSchema("string", "City and country", required = true),
                "units" to ParameterSchema("string", "Temperature units", required = true)
            )
        )

        // Test with strict mode enabled
        val strictSpec = schema.toOpenAIFunctionSpec(strict = true)

        // Verify type field
        assertEquals("function", strictSpec["type"])

        // Verify strict field is present
        assertEquals(true, strictSpec["strict"])

        // Verify parameters have additionalProperties: false
        val parameters = strictSpec["parameters"] as Map<*, *>
        assertEquals(false, parameters["additionalProperties"])

        // Verify other fields still present
        assertEquals("get_weather", strictSpec["name"])
        assertEquals("Get current weather", strictSpec["description"])

        val properties = parameters["properties"] as Map<*, *>
        assertTrue(properties.containsKey("location"))
        assertTrue(properties.containsKey("units"))

        val required = parameters["required"] as List<*>
        assertEquals(2, required.size)
        assertTrue(required.contains("location"))
        assertTrue(required.contains("units"))
    }

    @Test
    fun `test ToolSchema toOpenAIFunctionSpec default mode is non-strict`() {
        val schema = ToolSchema(
            name = "calculate",
            description = "Calculate something",
            parameters = mapOf(
                "a" to ParameterSchema("number", "First number", required = true)
            )
        )

        val spec = schema.toOpenAIFunctionSpec()

        // Verify strict field is not present (default behavior)
        assertTrue(!spec.containsKey("strict"))

        // Verify additionalProperties is not in parameters (default behavior)
        val parameters = spec["parameters"] as Map<*, *>
        assertTrue(!parameters.containsKey("additionalProperties"))
    }

    @Test
    fun `test Tool toOpenAIFunctionSpec with strict mode`() {
        val tool = SimpleTool(
            name = "test_strict",
            description = "Test strict mode",
            parameterSchemas = mapOf(
                "param1" to ParameterSchema("string", "Test parameter", required = true)
            )
        ) { ToolResult.success("OK") }

        // Test strict mode
        val strictSpec = tool.toOpenAIFunctionSpec(strict = true)

        assertEquals("function", strictSpec["type"])
        assertEquals(true, strictSpec["strict"])

        val parameters = strictSpec["parameters"] as Map<*, *>
        assertEquals(false, parameters["additionalProperties"])

        // Test default mode
        val defaultSpec = tool.toOpenAIFunctionSpec()

        assertEquals("function", defaultSpec["type"])
        assertTrue(!defaultSpec.containsKey("strict"))

        val defaultParams = defaultSpec["parameters"] as Map<*, *>
        assertTrue(!defaultParams.containsKey("additionalProperties"))
    }
}
