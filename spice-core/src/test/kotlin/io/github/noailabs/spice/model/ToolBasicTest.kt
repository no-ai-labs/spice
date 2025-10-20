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
}
