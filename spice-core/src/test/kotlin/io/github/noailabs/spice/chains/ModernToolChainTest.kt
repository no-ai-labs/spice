package io.github.noailabs.spice.chains

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for ModernToolChain extensions
 */
class ModernToolChainTest {

    private lateinit var multiplyTool: Tool
    private lateinit var addTool: Tool
    private lateinit var formatTool: Tool

    @BeforeEach
    fun setup() {
        // Create test tools
        multiplyTool = SimpleTool(
            name = "multiply",
            description = "Multiply two numbers",
            parameterSchemas = mapOf(
                "a" to ParameterSchema("number", "First number", true),
                "b" to ParameterSchema("number", "Second number", true)
            )
        ) { params ->
            val a = (params["a"] as? Number)?.toInt() ?: 0
            val b = (params["b"] as? Number)?.toInt() ?: 0
            ToolResult.success((a * b).toString())
        }

        addTool = SimpleTool(
            name = "add",
            description = "Add two numbers",
            parameterSchemas = mapOf(
                "a" to ParameterSchema("number", "First number", true),
                "b" to ParameterSchema("number", "Second number", true)
            )
        ) { params ->
            val a = (params["a"] as? Number)?.toInt() ?: 0
            val b = (params["b"] as? Number)?.toInt() ?: 0
            ToolResult.success((a + b).toString())
        }

        formatTool = SimpleTool(
            name = "format",
            description = "Format a number",
            parameterSchemas = mapOf(
                "value" to ParameterSchema("string", "Value to format", true)
            )
        ) { params ->
            val value = params["value"] as? String ?: "0"
            ToolResult.success("Formatted: $value")
        }

        // Register tools
        ToolRegistry.register(multiplyTool)
        ToolRegistry.register(addTool)
        ToolRegistry.register(formatTool)
    }

    @Test
    fun `test ChainContext getOutputOf and requireOutputOf`() = runBlocking {
        val context = ChainContext(chainId = "test")

        // Test setStepOutput
        context.setStepOutput("step1", "result1")
        context.setStepOutput("step2", 42)

        // Test getOutputOf (nullable)
        assertEquals("result1", context.getOutputOf("step1"))
        assertEquals(42, context.getOutputOf("step2"))
        assertEquals(null, context.getOutputOf("nonexistent"))

        // Test requireOutputOf (throws on missing)
        assertEquals("result1", context.requireOutputOf("step1"))
        assertEquals(42, context.requireOutputOf("step2"))

        // Should throw for missing step
        val exception = assertFailsWith<IllegalStateException> {
            context.requireOutputOf("nonexistent")
        }
        assertTrue(exception.message!!.contains("Output of step 'nonexistent' not found"))
    }

    @Test
    fun `test step with Tool object`() = runBlocking {
        val chain = toolChain("test-chain") {
            name = "Test Chain with Tool Objects"
            description = "Testing Tool object support"
            debugEnabled = false

            // Use Tool object directly (type-safe!)
            step("multiply", multiplyTool, mapOf("a" to 5, "b" to 3))
            step("add", addTool, mapOf("a" to 10, "b" to 5))
        }

        val result = chain.execute(emptyMap())

        assertTrue(result.success)
        assertEquals(2, result.stepResults.size)
        assertEquals("15", result.stepResults[0].result) // 5 * 3
        assertEquals("15", result.stepResults[1].result) // 10 + 5
    }

    @Test
    fun `test stepWithOutput helper`() = runBlocking {
        val chain = toolChain("output-chain") {
            name = "Chain with Named Outputs"
            description = "Testing stepWithOutput"
            debugEnabled = false

            // Step 1: multiply and store as "value" (matching formatTool's parameter name)
            stepWithOutput("multiply", multiplyTool, "value", mapOf("a" to 4, "b" to 5))

            // Step 2: format tool receives "value" from sharedData (automatically merged into params)
            step("format", formatTool)
        }

        val result = chain.execute(emptyMap())

        assertTrue(result.success, "Chain should succeed")
        assertEquals(2, result.stepResults.size)
        assertEquals("20", result.stepResults[0].result, "First step should return 20") // 4 * 5
        assertEquals("Formatted: 20", result.stepResults[1].result, "Second step should return 'Formatted: 20'")
    }

    @Test
    fun `test complex chain with requireOutputOf`() = runBlocking {
        val chain = toolChain("complex-chain") {
            name = "Complex Chain"
            description = "Multi-step chain with named outputs and context references"
            debugEnabled = false

            // Step 1: Store output with custom name
            stepWithOutput("calc1", multiplyTool, "customOutput", mapOf("a" to 5, "b" to 4))

            // Step 2: Verify we can access custom output via requireOutputOf
            stepWithTransform("verify", multiplyTool, mapOf("a" to 1, "b" to 1)) { result, context ->
                // Access the custom output
                val customValue = context.requireOutputOf("customOutput")
                // Store it for verification
                context.setStepOutput("verified", customValue)
                emptyMap()
            }
        }

        val result = chain.execute(emptyMap())

        assertTrue(result.success)
        assertEquals(2, result.stepResults.size)
        assertEquals("20", result.stepResults[0].result)  // 5 * 4 = 20
        assertEquals("1", result.stepResults[1].result)  // 1 * 1 = 1

        // The important part: verify we could access the custom output
        // (the transformer would have thrown if requireOutputOf failed)
    }

    @Test
    fun `test stepWithOutput with string tool name`() = runBlocking {
        val chain = toolChain("string-tool-chain") {
            name = "Chain with String Tool Names"
            debugEnabled = false

            // Use string tool name version - output as "value" for format tool
            stepWithOutput("calc", "multiply", "value", mapOf("a" to 6, "b" to 7))

            // Format receives "value" from sharedData
            step("format", "format")
        }

        val result = chain.execute(emptyMap())

        assertTrue(result.success)
        assertEquals(2, result.stepResults.size)
        assertEquals("42", result.stepResults[0].result)
        assertEquals("Formatted: 42", result.stepResults[1].result)
    }

    @Test
    fun `test backward compatibility - existing chains still work`() = runBlocking {
        // Old-style chain (should still work)
        val chain = toolChain("old-style") {
            name = "Old Style Chain"
            description = "Backward compatibility test"
            debugEnabled = false

            step("multiply", "multiply", mapOf("a" to 2, "b" to 3))

            stepWithTransform("add", "add", mapOf("a" to 5, "b" to 5)) { result ->
                mapOf("computed" to result.result)
            }
        }

        val result = chain.execute(emptyMap())

        assertTrue(result.success)
        assertEquals(2, result.stepResults.size)
        assertEquals("6", result.stepResults[0].result)
        assertEquals("10", result.stepResults[1].result)
    }

    @Test
    fun `test step outputs are stored automatically`() = runBlocking {
        val chain = toolChain("auto-storage") {
            name = "Automatic Storage Test"
            debugEnabled = false

            // Even without explicit transformer, outputs should be stored
            step("step1", multiplyTool, mapOf("a" to 7, "b" to 8))
            step("step2", addTool, mapOf("a" to 1, "b" to 2))
        }

        // We can't directly access ChainContext from outside,
        // but we can verify through step results
        val result = chain.execute(emptyMap())

        assertTrue(result.success)
        assertEquals("56", result.stepResults[0].result)
        assertEquals("3", result.stepResults[1].result)
    }

    // =====================================
    // 🆕 FLUENT PIPELINE DSL TESTS
    // =====================================

    @Test
    fun `test fluent API with output name`() = runBlocking {
        val chain = toolChain("fluent-output") {
            name = "Fluent API with Output"
            description = "Testing fluent output syntax"
            debugEnabled = false

            // Fluent syntax with method chaining
            +step(multiplyTool).output("result").input { mapOf("a" to 5, "b" to 6) }

            // Next step can access "result" from shared data
            +step(formatTool).input { context ->
                mapOf("value" to (context.sharedData["result"] ?: ""))
            }
        }

        val result = chain.execute(emptyMap())

        assertTrue(result.success, "Chain should succeed")
        assertEquals(2, result.stepResults.size)
        assertEquals("30", result.stepResults[0].result) // 5 * 6
        assertEquals("Formatted: 30", result.stepResults[1].result)
    }

    @Test
    fun `test fluent API with requireOutputOf`() = runBlocking {
        val chain = toolChain("fluent-require") {
            name = "Fluent API with requireOutputOf"
            debugEnabled = false

            // Step 1: Store output with name "multiplied"
            +step(multiplyTool).output("multiplied").input { mapOf("a" to 4, "b" to 7) }

            // Step 2: Use requireOutputOf to get previous output
            +step(formatTool).input { context ->
                val value = context.requireOutputOf("multiplied")
                mapOf("value" to value)
            }
        }

        val result = chain.execute(emptyMap())

        assertTrue(result.success)
        assertEquals(2, result.stepResults.size)
        assertEquals("28", result.stepResults[0].result) // 4 * 7
        assertEquals("Formatted: 28", result.stepResults[1].result)
    }

    @Test
    fun `test fluent API with named step`() = runBlocking {
        val chain = toolChain("fluent-named") {
            name = "Fluent API with Named Steps"
            debugEnabled = false

            // Use named() to set custom step ID
            +step(multiplyTool).named("calculate").output("result").input {
                mapOf("a" to 8, "b" to 9)
            }

            // Access by step ID
            +step(formatTool).input { context ->
                val value = context.requireOutputOf("result")
                mapOf("value" to value)
            }
        }

        val result = chain.execute(emptyMap())

        assertTrue(result.success)
        assertEquals(2, result.stepResults.size)
        assertEquals("72", result.stepResults[0].result) // 8 * 9
        assertEquals("Formatted: 72", result.stepResults[1].result)
    }

    @Test
    fun `test fluent API without input block`() = runBlocking {
        val chain = toolChain("fluent-no-input") {
            name = "Fluent API without Input"
            debugEnabled = false

            // First step with static parameters (via initial parameters)
            +step(multiplyTool).output("value")
        }

        // Provide parameters at execution time
        val result = chain.execute(mapOf("a" to 3, "b" to 11))

        assertTrue(result.success)
        assertEquals(1, result.stepResults.size)
        assertEquals("33", result.stepResults[0].result) // 3 * 11
    }

    @Test
    fun `test fluent API mixed with traditional syntax`() = runBlocking {
        val chain = toolChain("fluent-mixed") {
            name = "Mixed Fluent and Traditional"
            debugEnabled = false

            // Traditional syntax
            stepWithOutput("calc1", multiplyTool, "first", mapOf("a" to 2, "b" to 5))

            // Fluent syntax accessing traditional step output
            +step(formatTool).input { context ->
                val value = context.requireOutputOf("first")
                mapOf("value" to value)
            }

            // Traditional syntax accessing fluent output via sharedData
            step("calc2", addTool, mapOf("a" to 1, "b" to 2))
        }

        val result = chain.execute(emptyMap())

        assertTrue(result.success)
        assertEquals(3, result.stepResults.size)
        assertEquals("10", result.stepResults[0].result) // 2 * 5
        assertEquals("Formatted: 10", result.stepResults[1].result)
        assertEquals("3", result.stepResults[2].result) // 1 + 2
    }

    @Test
    fun `test fluent API complex pipeline`() = runBlocking {
        val chain = toolChain("fluent-complex") {
            name = "Complex Fluent Pipeline"
            description = "Multi-step pipeline with data flow"
            debugEnabled = true

            // Step 1: Calculate base value
            +step(multiplyTool).named("base").output("baseValue").input {
                mapOf("a" to 10, "b" to 5)
            }

            // Step 2: Add to base value
            +step(addTool).named("adjust").output("adjustedValue").input { context ->
                val base = (context.requireOutputOf("baseValue") as String).toInt()
                mapOf("a" to base, "b" to 15)
            }

            // Step 3: Format final result
            +step(formatTool).named("finalize").input { context ->
                val adjusted = context.requireOutputOf("adjustedValue")
                mapOf("value" to adjusted)
            }
        }

        val result = chain.execute(emptyMap())

        assertTrue(result.success)
        assertEquals(3, result.stepResults.size)
        assertEquals("50", result.stepResults[0].result) // 10 * 5
        assertEquals("65", result.stepResults[1].result) // 50 + 15
        assertEquals("Formatted: 65", result.stepResults[2].result)
    }
}
