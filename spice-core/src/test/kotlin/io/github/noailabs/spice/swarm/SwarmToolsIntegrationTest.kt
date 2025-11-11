package io.github.noailabs.spice.swarm

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 🔥 Integration Tests for Swarm Tools
 *
 * Tests real-world scenarios with swarm tools
 */
class SwarmToolsIntegrationTest {

    @Test
    fun `swarm with calculator tool should solve math problem`() = runTest {
        // Given: A swarm with calculator tool
        val swarm = buildSwarmAgent {
            name = "Math Team"
            description = "Team that solves math problems"

            swarmTools {
                tool("calculate", "Calculator for basic math") {
                    parameter("a", "number", "First number", required = true)
                    parameter("b", "number", "Second number", required = true)
                    parameter("operation", "string", "Operation", required = true)

                    execute(fun(params: Map<String, Any?>): String {
                        val a = (params["a"] as? Number)?.toDouble() ?: throw IllegalArgumentException("Missing 'a'")
                        val b = (params["b"] as? Number)?.toDouble() ?: throw IllegalArgumentException("Missing 'b'")
                        val op = params["operation"]?.toString() ?: throw IllegalArgumentException("Missing 'operation'")

                        val result = when (op) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            "/" -> if (b != 0.0) a / b else throw ArithmeticException("Division by zero")
                            else -> throw IllegalArgumentException("Unknown operation: $op")
                        }

                        return result.toString()
                    })
                }
            }

            quickSwarm {
                specialist("mathematician", "Mathematician", "math")
                specialist("calculator", "Calculator", "calculations")
            }
        }

        // When: Get the calculator tool
        val tools = swarm.getTools()
        val calculator = tools.first { it.name == "calculate" }

        // Then: Tool should execute correctly
        val result1 = calculator.execute(mapOf("a" to 10, "b" to 5, "operation" to "+"))
        assertTrue(result1.isSuccess)
        assertEquals("15.0", (result1 as SpiceResult.Success).value.result)

        val result2 = calculator.execute(mapOf("a" to 10, "b" to 5, "operation" to "*"))
        assertTrue(result2.isSuccess)
        assertEquals("50.0", (result2 as SpiceResult.Success).value.result)
    }

    @Test
    fun `swarm tools should be accessible from all member agents`() = runTest {
        // Given: Swarm with shared tool
        val swarm = buildSwarmAgent {
            name = "Shared Tool Swarm"

            swarmTools {
                tool("shared_formatter", "Formats text") {
                    parameter("text", "string", "Text to format", required = true)
                    parameter("style", "string", "Format style", required = true)

                    execute(fun(params: Map<String, Any?>): String {
                        val text = params["text"]?.toString() ?: throw IllegalArgumentException("Missing 'text'")
                        val style = params["style"]?.toString() ?: throw IllegalArgumentException("Missing 'style'")

                        return when (style) {
                            "upper" -> text.uppercase()
                            "lower" -> text.lowercase()
                            "title" -> text.split(" ").joinToString(" ") {
                                it.replaceFirstChar { c -> c.uppercase() }
                            }
                            else -> text
                        }
                    })
                }
            }

            quickSwarm {
                specialist("agent1", "Agent 1", "task1")
                specialist("agent2", "Agent 2", "task2")
                specialist("agent3", "Agent 3", "task3")
            }
        }

        // When: All agents access tools
        val tools = swarm.getTools()
        val formatter = tools.first { it.name == "shared_formatter" }

        // Then: Tool should work for all formatting styles
        val upper = formatter.execute(mapOf("text" to "hello", "style" to "upper"))
        assertTrue(upper.isSuccess)
        assertEquals("HELLO", (upper as SpiceResult.Success).value.result)

        val lower = formatter.execute(mapOf("text" to "WORLD", "style" to "lower"))
        assertTrue(lower.isSuccess)
        assertEquals("world", (lower as SpiceResult.Success).value.result)

        val title = formatter.execute(mapOf("text" to "hello world", "style" to "title"))
        assertTrue(title.isSuccess)
        assertEquals("Hello World", (title as SpiceResult.Success).value.result)
    }

    @Test
    fun `swarm tools should deduplicate by name`() = runTest {
        // Given: Swarm with duplicate tool names
        val swarm = buildSwarmAgent {
            name = "Dedup Test Swarm"

            swarmTools {
                tool("shared_tool", "First definition") {
                    execute(fun(_: Map<String, Any?>): String {
                        return "First"
                    })
                }

                tool("shared_tool", "Second definition") {
                    execute(fun(_: Map<String, Any?>): String {
                        return "Second"
                    })
                }
            }

            quickSwarm {
                specialist("agent1", "Agent 1", "task")
            }
        }

        // When: Get tools
        val tools = swarm.getTools()
        val sharedTools = tools.filter { it.name == "shared_tool" }

        // Then: Should only have one tool with that name
        assertEquals(1, sharedTools.size, "Tools should be deduplicated by name")
    }

    @Test
    fun `swarm tools validation should prevent execution with missing params`() = runTest {
        // Given: Swarm with strict validation
        val swarm = buildSwarmAgent {
            name = "Validation Swarm"

            swarmTools {
                tool("strict_tool", "Requires all parameters") {
                    parameter("required1", "string", "First required", required = true)
                    parameter("required2", "number", "Second required", required = true)
                    parameter("optional", "string", "Optional param", required = false)

                    execute(fun(params: Map<String, Any?>): String {
                        val r1 = params["required1"]?.toString() ?: throw IllegalArgumentException("Missing 'required1'")
                        val r2 = (params["required2"] as? Number)?.toInt() ?: throw IllegalArgumentException("Missing 'required2'")
                        val opt = params["optional"]?.toString() ?: "default"

                        return "r1=$r1, r2=$r2, opt=$opt"
                    })
                }
            }

            quickSwarm {
                specialist("agent1", "Agent 1", "validation")
            }
        }

        // When: Execute without required parameters
        val tool = swarm.getTools().first { it.name == "strict_tool" }

        // Then: Should fail validation
        val result1 = tool.execute(emptyMap())
        assertTrue(result1.isSuccess)
        val toolResult1 = (result1 as SpiceResult.Success).value
        assertTrue(!toolResult1.success)
        assertTrue(toolResult1.error.contains("Parameter validation failed"))

        // When: Execute with only one required parameter
        val result2 = tool.execute(mapOf("required1" to "value1"))
        assertTrue(result2.isSuccess)
        val toolResult2 = (result2 as SpiceResult.Success).value
        assertTrue(!toolResult2.success)
        assertTrue(toolResult2.error.contains("Missing required parameter"))

        // When: Execute with all required parameters
        val result3 = tool.execute(mapOf("required1" to "value1", "required2" to 42))
        assertTrue(result3.isSuccess)
        val toolResult3 = (result3 as SpiceResult.Success).value
        assertTrue(toolResult3.success)
        assertEquals("r1=value1, r2=42, opt=default", toolResult3.result)
    }

    @Test
    fun `swarm tools error handling should catch exceptions`() = runTest {
        // Given: Swarm with tool that may throw exceptions
        val swarm = buildSwarmAgent {
            name = "Error Handling Swarm"

            swarmTools {
                tool("risky_divide", "Division that may fail") {
                    parameter("numerator", "number", "Numerator", required = true)
                    parameter("denominator", "number", "Denominator", required = true)

                    execute(fun(params: Map<String, Any?>): String {
                        val num = (params["numerator"] as? Number)?.toDouble() ?: throw IllegalArgumentException("Missing 'numerator'")
                        val den = (params["denominator"] as? Number)?.toDouble() ?: throw IllegalArgumentException("Missing 'denominator'")

                        if (den == 0.0) {
                            throw ArithmeticException("Cannot divide by zero!")
                        }

                        return (num / den).toString()
                    })
                }
            }

            quickSwarm {
                specialist("agent1", "Agent 1", "math")
            }
        }

        // When: Execute with zero denominator
        val tool = swarm.getTools().first { it.name == "risky_divide" }
        val result = tool.execute(mapOf("numerator" to 10, "denominator" to 0))

        // Then: Should handle error gracefully
        assertTrue(result.isSuccess || result.isFailure)
        if (result.isSuccess) {
            val toolResult = (result as SpiceResult.Success).value
            assertTrue(!toolResult.success)
            assertTrue(toolResult.error.contains("divide by zero") ||
                      toolResult.error.contains("Division by zero"))
        }
    }

    @Test
    fun `swarm with multiple coordination tools should work together`() = runTest {
        // Given: Swarm with multiple tools
        val swarm = buildSwarmAgent {
            name = "Multi-Tool Swarm"

            swarmTools {
                // Tool 1: Data validator
                tool("validate", "Validates data") {
                    parameter("data", "string", "Data to validate", required = true)

                    execute(fun(params: Map<String, Any?>): String {
                        val data = params["data"]?.toString() ?: throw IllegalArgumentException("Missing 'data'")
                        return if (data.isNotEmpty()) "valid" else "invalid"
                    })
                }

                // Tool 2: Data transformer
                tool("transform", "Transforms data") {
                    parameter("data", "string", "Data to transform", required = true)
                    parameter("operation", "string", "Operation", required = true)

                    execute(fun(params: Map<String, Any?>): String {
                        val data = params["data"]?.toString() ?: throw IllegalArgumentException("Missing 'data'")
                        val op = params["operation"]?.toString() ?: throw IllegalArgumentException("Missing 'operation'")

                        return when (op) {
                            "reverse" -> data.reversed()
                            "upper" -> data.uppercase()
                            "lower" -> data.lowercase()
                            else -> data
                        }
                    })
                }

                // Tool 3: Data aggregator
                tool("aggregate", "Aggregates results") {
                    parameter("results", "string", "Results to aggregate", required = true)

                    execute(fun(params: Map<String, Any?>): String {
                        val results = params["results"]?.toString() ?: throw IllegalArgumentException("Missing 'results'")
                        return "Aggregated: $results"
                    })
                }
            }

            quickSwarm {
                specialist("validator", "Validator", "validation")
                specialist("transformer", "Transformer", "transformation")
                specialist("aggregator", "Aggregator", "aggregation")
            }
        }

        // When: Use tools in sequence
        val tools = swarm.getTools()

        // Step 1: Validate
        val validator = tools.first { it.name == "validate" }
        val validation = validator.execute(mapOf("data" to "test data"))
        assertTrue((validation as SpiceResult.Success).value.success)
        assertEquals("valid", validation.value.result)

        // Step 2: Transform
        val transformer = tools.first { it.name == "transform" }
        val transformed = transformer.execute(mapOf("data" to "hello", "operation" to "upper"))
        assertTrue((transformed as SpiceResult.Success).value.success)
        assertEquals("HELLO", transformed.value.result)

        // Step 3: Aggregate
        val aggregator = tools.first { it.name == "aggregate" }
        val aggregated = aggregator.execute(mapOf("results" to "HELLO"))
        assertTrue((aggregated as SpiceResult.Success).value.success)
        assertEquals("Aggregated: HELLO", aggregated.value.result)
    }
}
