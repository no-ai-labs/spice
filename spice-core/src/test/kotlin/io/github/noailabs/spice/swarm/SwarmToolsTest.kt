package io.github.noailabs.spice.swarm

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for Swarm Tools Integration
 */
class SwarmToolsTest {

    @Test
    fun `swarm should accept inline tool definitions`() = runTest {
        // Given: A swarm with inline tool
        val swarm = buildSwarmAgent {
            name = "Test Swarm"
            description = "Swarm with tools"

            swarmTools {
                tool("calculate", "Simple calculator") {
                    parameter("a", "number", "First number", required = true)
                    parameter("b", "number", "Second number", required = true)
                    parameter("operation", "string", "Operation (+, -, *, /)", required = true)

                    execute { params: Map<String, Any> ->
                        val a = (params["a"] as Number).toDouble()
                        val b = (params["b"] as Number).toDouble()
                        val op = params["operation"] as String

                        val result = when (op) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            "/" -> a / b
                            else -> throw IllegalArgumentException("Unknown operation: $op")
                        }

                        "Result: $result"
                    }
                }
            }

            quickSwarm {
                specialist("agent1", "Agent 1", "test")
            }
        }

        // When: Get tools
        val tools = swarm.getTools()

        // Then: Should contain the calculate tool
        assertTrue(tools.any { it.name == "calculate" })
        assertEquals("Simple calculator", tools.first { it.name == "calculate" }.description)
    }

    @Test
    fun `swarm tool should execute correctly`() = runTest {
        // Given: A swarm with calculator tool
        val swarm = buildSwarmAgent {
            name = "Calculator Swarm"
            description = "Swarm with calculator"

            swarmTools {
                tool("add", "Addition tool") {
                    parameter("x", "number", "First number", required = true)
                    parameter("y", "number", "Second number", required = true)

                    execute { params: Map<String, Any> ->
                        val x = (params["x"] as Number).toDouble()
                        val y = (params["y"] as Number).toDouble()
                        "${x + y}"
                    }
                }
            }

            quickSwarm {
                specialist("calculator", "Calculator Agent", "calculations")
            }
        }

        // When: Execute the tool
        val tool = swarm.getTools().first { it.name == "add" }
        val result = tool.execute(mapOf("x" to 5, "y" to 3))

        // Then: Should return correct result
        assertTrue(result.isSuccess)
        val toolResult = (result as SpiceResult.Success).value
        assertTrue(toolResult.success)
        assertEquals("8.0", toolResult.result)
    }

    @Test
    fun `swarm tool should validate parameters`() = runTest {
        // Given: A swarm with tool requiring parameters
        val swarm = buildSwarmAgent {
            name = "Validation Test Swarm"
            description = "Tests parameter validation"

            swarmTools {
                tool("greet", "Greeting tool") {
                    parameter("name", "string", "Name to greet", required = true)

                    execute { params: Map<String, Any> ->
                        val name = params["name"] as String
                        "Hello, $name!"
                    }
                }
            }

            quickSwarm {
                specialist("greeter", "Greeter Agent", "greetings")
            }
        }

        // When: Execute without required parameter
        val tool = swarm.getTools().first { it.name == "greet" }
        val result = tool.execute(emptyMap())

        // Then: Should return validation error
        assertTrue(result.isSuccess)
        val toolResult = (result as SpiceResult.Success).value
        assertTrue(!toolResult.success) // Tool result indicates failure
        assertTrue(toolResult.error.contains("Parameter validation failed"))
        assertTrue(toolResult.error.contains("Missing required parameter: name"))
    }

    @Test
    fun `swarm should combine swarm tools and member agent tools`() = runTest {
        // Given: A swarm with its own tools and member agents
        val swarm = buildSwarmAgent {
            name = "Combined Tools Swarm"
            description = "Swarm with combined tools"

            swarmTools {
                tool("swarm_tool", "Swarm's own tool") {
                    execute { _: Map<String, Any> ->
                        "Swarm tool executed"
                    }
                }
            }

            quickSwarm {
                specialist("agent1", "Agent 1", "test")
            }
        }

        // When: Get all tools
        val tools = swarm.getTools()

        // Then: Should have swarm tools
        assertTrue(tools.any { it.name == "swarm_tool" })
    }

    @Test
    fun `swarm should have tools from config`() = runTest {
        // Given: A swarm with tools in config
        val swarm = buildSwarmAgent {
            name = "Config Tools Swarm"
            description = "Tests tools from config"

            swarmTools {
                tool("tool1", "Tool 1") {
                    execute { _: Map<String, Any> -> "Tool 1" }
                }
                tool("tool2", "Tool 2") {
                    execute { _: Map<String, Any> -> "Tool 2" }
                }
            }

            quickSwarm {
                specialist("agent1", "Agent 1", "test")
            }
        }

        // When: Get tools
        val tools = swarm.getTools()

        // Then: Should have both tools
        assertTrue(tools.any { it.name == "tool1" })
        assertTrue(tools.any { it.name == "tool2" })
    }

    @Test
    fun `swarm tools should be accessible from multiple member agents`() = runTest {
        // Given: Swarm with coordination tool
        val swarm = buildSwarmAgent {
            name = "Coordinated Swarm"
            description = "Swarm with shared coordination tools"

            swarmTools {
                tool("coordinate", "Coordination tool") {
                    parameter("action", "string", "Action to coordinate", required = true)

                    execute { params: Map<String, Any> ->
                        val action = params["action"] as String
                        "Coordinating: $action"
                    }
                }
            }

            quickSwarm {
                specialist("agent1", "Agent 1", "task 1")
                specialist("agent2", "Agent 2", "task 2")
                specialist("agent3", "Agent 3", "task 3")
            }
        }

        // When: All member agents access the swarm
        val tools = swarm.getTools()

        // Then: Coordination tool should be available to all
        val coordinateTool = tools.firstOrNull { it.name == "coordinate" }
        assertTrue(coordinateTool != null)

        // And: Can be executed by any member
        val result = coordinateTool!!.execute(mapOf("action" to "sync_data"))
        assertTrue(result.isSuccess)
        val toolResult = (result as SpiceResult.Success).value
        assertTrue(toolResult.success)
        assertEquals("Coordinating: sync_data", toolResult.result)
    }

    @Test
    fun `swarm tool should handle errors gracefully`() = runTest {
        // Given: Swarm with tool that may throw exceptions
        val swarm = buildSwarmAgent {
            name = "Error Handling Swarm"
            description = "Tests error handling"

            swarmTools {
                tool("divide", "Division tool") {
                    parameter("numerator", "number", "Numerator", required = true)
                    parameter("denominator", "number", "Denominator", required = true)

                    execute { params: Map<String, Any> ->
                        val numerator = (params["numerator"] as Number).toDouble()
                        val denominator = (params["denominator"] as Number).toDouble()

                        if (denominator == 0.0) {
                            throw ArithmeticException("Division by zero")
                        }

                        "${numerator / denominator}"
                    }
                }
            }

            quickSwarm {
                specialist("calculator", "Calculator", "math")
            }
        }

        // When: Execute with division by zero
        val tool = swarm.getTools().first { it.name == "divide" }
        val result = tool.execute(mapOf("numerator" to 10, "denominator" to 0))

        // Then: Should handle error gracefully
        assertTrue(result.isFailure ||
                   (result.isSuccess && !(result as SpiceResult.Success).value.success))
    }
}
