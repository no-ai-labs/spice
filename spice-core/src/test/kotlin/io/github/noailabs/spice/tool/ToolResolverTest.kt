package io.github.noailabs.spice.tool

import io.github.noailabs.spice.SimpleTool
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolRegistry
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolResolverTest {

    @BeforeEach
    fun setup() {
        // Clear registry before each test
        ToolRegistry.clear()
    }

    @AfterEach
    fun teardown() {
        ToolRegistry.clear()
    }

    // ==================================
    // StaticToolResolver Tests
    // ==================================

    @Test
    fun `StaticToolResolver always returns the bound tool`() = runTest {
        val tool = SimpleTool("test_tool", "Test tool") { _ ->
            ToolResult.success("result")
        }

        val resolver = StaticToolResolver(tool)
        val message = SpiceMessage.create("test", "user")

        val result = resolver.resolve(message)

        assertIs<SpiceResult.Success<*>>(result)
        assertEquals(tool, result.value)
    }

    @Test
    fun `StaticToolResolver displayName includes tool name`() {
        val tool = SimpleTool("my_tool", "My tool") { _ ->
            ToolResult.success("result")
        }

        val resolver = StaticToolResolver(tool)

        assertEquals("static:my_tool", resolver.displayName)
    }

    @Test
    fun `StaticToolResolver validation returns empty list`() {
        val tool = SimpleTool("my_tool", "My tool") { _ ->
            ToolResult.success("result")
        }

        val resolver = StaticToolResolver(tool)
        val validations = resolver.validate()

        assertTrue(validations.isEmpty())
    }

    // ==================================
    // RegistryToolResolver Tests
    // ==================================

    @Test
    fun `RegistryToolResolver resolves tool from registry by name`() = runTest {
        // Register tool in registry
        val registeredTool = SimpleTool("lookup_tool", "Lookup tool") { _ ->
            ToolResult.success("looked up")
        }
        ToolRegistry.register(registeredTool, namespace = "test-ns")

        val resolver = RegistryToolResolver(
            nameSelector = { msg -> msg.getData<String>("toolName")!! },
            namespaceSelector = { "test-ns" }
        )

        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("toolName" to "lookup_tool"))

        val result = resolver.resolve(message)

        assertIs<SpiceResult.Success<Tool>>(result)
        assertEquals("lookup_tool", (result.value as Tool).name)
    }

    @Test
    fun `RegistryToolResolver returns error when tool not found`() = runTest {
        val resolver = RegistryToolResolver(
            nameSelector = { msg -> msg.getData<String>("toolName")!! },
            namespaceSelector = { "missing-ns" }
        )

        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("toolName" to "nonexistent_tool"))

        val result = resolver.resolve(message)

        assertIs<SpiceResult.Failure>(result)
        assertIs<SpiceError.ToolLookupError>(result.error)
        val error = result.error as SpiceError.ToolLookupError
        assertEquals("nonexistent_tool", error.toolName)
        assertEquals("missing-ns", error.namespace)
    }

    @Test
    fun `RegistryToolResolver returns error when name selector fails`() = runTest {
        val resolver = RegistryToolResolver(
            nameSelector = { msg -> msg.getData<String>("toolName")!! },  // Will throw
            namespaceSelector = { "global" }
        )

        val message = SpiceMessage.create("test", "user")  // No toolName in data

        val result = resolver.resolve(message)

        assertIs<SpiceResult.Failure>(result)
        assertIs<SpiceError.ToolLookupError>(result.error)
    }

    @Test
    fun `RegistryToolResolver validation returns warning for missing expected tools`() {
        val resolver = RegistryToolResolver(
            nameSelector = { "tool1" },
            defaultNamespace = "global",
            expectedTools = setOf("tool1", "tool2"),
            strict = false
        )

        val validations = resolver.validate(ToolRegistry, "global")

        // Both tools are missing
        assertEquals(2, validations.size)
        assertTrue(validations.all { it.level == ToolResolverValidation.Level.WARNING })
    }

    @Test
    fun `RegistryToolResolver validation returns error in strict mode`() {
        val resolver = RegistryToolResolver(
            nameSelector = { "tool1" },
            defaultNamespace = "global",
            expectedTools = setOf("missing_tool"),
            strict = true
        )

        val validations = resolver.validate(ToolRegistry, "global")

        assertEquals(1, validations.size)
        assertEquals(ToolResolverValidation.Level.ERROR, validations[0].level)
    }

    @Test
    fun `RegistryToolResolver validation passes when expected tools exist`() {
        // Register the expected tool
        val tool = SimpleTool("expected_tool", "Expected") { _ ->
            ToolResult.success("result")
        }
        ToolRegistry.register(tool, namespace = "global")

        val resolver = RegistryToolResolver(
            nameSelector = { "expected_tool" },
            defaultNamespace = "global",
            expectedTools = setOf("expected_tool"),
            strict = true
        )

        val validations = resolver.validate(ToolRegistry, "global")

        assertTrue(validations.isEmpty())
    }

    // ==================================
    // DynamicToolResolver Tests
    // ==================================

    @Test
    fun `DynamicToolResolver uses custom selector`() = runTest {
        val simpleTool = SimpleTool("simple", "Simple") { _ ->
            ToolResult.success("simple result")
        }
        val advancedTool = SimpleTool("advanced", "Advanced") { _ ->
            ToolResult.success("advanced result")
        }

        val resolver = DynamicToolResolver(
            selector = { msg ->
                val complexity = msg.getData<Int>("complexity") ?: 0
                if (complexity > 50) SpiceResult.success(advancedTool)
                else SpiceResult.success(simpleTool)
            },
            description = "complexity-based"
        )

        // Low complexity -> simple tool
        val lowMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("complexity" to 30))
        val lowResult = resolver.resolve(lowMessage)
        assertIs<SpiceResult.Success<Tool>>(lowResult)
        assertEquals("simple", (lowResult.value as Tool).name)

        // High complexity -> advanced tool
        val highMessage = SpiceMessage.create("test", "user")
            .withData(mapOf("complexity" to 80))
        val highResult = resolver.resolve(highMessage)
        assertIs<SpiceResult.Success<Tool>>(highResult)
        assertEquals("advanced", (highResult.value as Tool).name)
    }

    @Test
    fun `DynamicToolResolver catches selector exceptions`() = runTest {
        val resolver = DynamicToolResolver(
            selector = { _ -> throw RuntimeException("Selector failed") },
            description = "broken"
        )

        val message = SpiceMessage.create("test", "user")
        val result = resolver.resolve(message)

        assertIs<SpiceResult.Failure>(result)
        assertIs<SpiceError.ToolLookupError>(result.error)
    }

    @Test
    fun `DynamicToolResolver displayName includes description`() {
        val resolver = DynamicToolResolver(
            selector = { SpiceResult.failure(SpiceError.ToolLookupError("test")) },
            description = "my-selector"
        )

        assertEquals("dynamic:my-selector", resolver.displayName)
    }

    @Test
    fun `DynamicToolResolver validation returns INFO`() {
        val resolver = DynamicToolResolver(
            selector = { SpiceResult.failure(SpiceError.ToolLookupError("test")) },
            description = "custom-logic"
        )

        val validations = resolver.validate()

        assertEquals(1, validations.size)
        assertEquals(ToolResolverValidation.Level.INFO, validations[0].level)
        assertTrue(validations[0].message.contains("custom-logic"))
    }

    // ==================================
    // FallbackToolResolver Tests
    // ==================================

    @Test
    fun `FallbackToolResolver tries resolvers in order`() = runTest {
        val fallbackTool = SimpleTool("fallback", "Fallback") { _ ->
            ToolResult.success("fallback result")
        }

        val failingResolver = DynamicToolResolver(
            selector = { SpiceResult.failure(SpiceError.ToolLookupError("First failed")) },
            description = "failing"
        )
        val successResolver = StaticToolResolver(fallbackTool)

        val resolver = FallbackToolResolver(listOf(failingResolver, successResolver))
        val message = SpiceMessage.create("test", "user")

        val result = resolver.resolve(message)

        assertIs<SpiceResult.Success<Tool>>(result)
        assertEquals("fallback", (result.value as Tool).name)
    }

    @Test
    fun `FallbackToolResolver returns first success`() = runTest {
        val firstTool = SimpleTool("first", "First") { _ ->
            ToolResult.success("first result")
        }
        val secondTool = SimpleTool("second", "Second") { _ ->
            ToolResult.success("second result")
        }

        val resolver = FallbackToolResolver(listOf(
            StaticToolResolver(firstTool),
            StaticToolResolver(secondTool)
        ))
        val message = SpiceMessage.create("test", "user")

        val result = resolver.resolve(message)

        assertIs<SpiceResult.Success<Tool>>(result)
        assertEquals("first", (result.value as Tool).name)  // First one wins
    }

    @Test
    fun `FallbackToolResolver returns error when all fail`() = runTest {
        val resolver = FallbackToolResolver(listOf(
            DynamicToolResolver({ SpiceResult.failure(SpiceError.ToolLookupError("First")) }, "first"),
            DynamicToolResolver({ SpiceResult.failure(SpiceError.ToolLookupError("Second")) }, "second")
        ))
        val message = SpiceMessage.create("test", "user")

        val result = resolver.resolve(message)

        assertIs<SpiceResult.Failure>(result)
        assertIs<SpiceError.ToolLookupError>(result.error)
        assertTrue(result.error.message.contains("2 fallback resolvers failed"))
    }

    @Test
    fun `FallbackToolResolver displayName shows chain`() {
        val resolver = FallbackToolResolver(listOf(
            DynamicToolResolver({ SpiceResult.failure(SpiceError.ToolLookupError("")) }, "first"),
            DynamicToolResolver({ SpiceResult.failure(SpiceError.ToolLookupError("")) }, "second")
        ))

        assertEquals("fallback:[dynamic:first,dynamic:second]", resolver.displayName)
    }

    // ==================================
    // ToolResolver Companion Tests
    // ==================================

    @Test
    fun `ToolResolver static creates StaticToolResolver`() {
        val tool = SimpleTool("test", "Test") { _ -> ToolResult.success("result") }
        val resolver = ToolResolver.static(tool)

        assertIs<StaticToolResolver>(resolver)
    }

    @Test
    fun `ToolResolver byRegistry creates RegistryToolResolver`() {
        val resolver = ToolResolver.byRegistry(
            nameSelector = { msg -> msg.getData<String>("toolId")!! },
            namespace = "my-namespace"
        )

        assertIs<RegistryToolResolver>(resolver)
    }

    @Test
    fun `ToolResolver dynamic creates DynamicToolResolver`() {
        val resolver = ToolResolver.dynamic("test") { msg ->
            SpiceResult.failure(SpiceError.ToolLookupError("test"))
        }

        assertIs<DynamicToolResolver>(resolver)
    }

    @Test
    fun `ToolResolver fallback with single resolver returns that resolver`() {
        val tool = SimpleTool("test", "Test") { _ -> ToolResult.success("result") }
        val resolver = ToolResolver.fallback(StaticToolResolver(tool))

        assertIs<StaticToolResolver>(resolver)  // Single resolver is returned directly
    }

    @Test
    fun `ToolResolver fallback with multiple resolvers creates FallbackToolResolver`() {
        val tool1 = SimpleTool("test1", "Test1") { _ -> ToolResult.success("result1") }
        val tool2 = SimpleTool("test2", "Test2") { _ -> ToolResult.success("result2") }
        val resolver = ToolResolver.fallback(
            StaticToolResolver(tool1),
            StaticToolResolver(tool2)
        )

        assertIs<FallbackToolResolver>(resolver)
    }

    // ==================================
    // Integration with Graph DSL Tests
    // ==================================

    @Test
    fun `Graph DSL tool with ToolResolver works correctly`() = runTest {
        val tool = SimpleTool("dynamic_test", "Dynamic test") { _ ->
            ToolResult.success("executed", mapOf("source" to "dynamic"))
        }
        ToolRegistry.register(tool, namespace = "test")

        val testGraph = graph("resolver-test") {
            tool("dynamic", ToolResolver.byRegistry(
                nameSelector = { msg -> msg.getData<String>("toolId")!! },
                namespace = "test"
            )) { msg ->
                mapOf("input" to msg.content)
            }
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("input data", "user")
            .withData(mapOf("toolId" to "dynamic_test"))

        val result = runner.execute(testGraph, message)

        assertIs<SpiceResult.Success<SpiceMessage>>(result)
        val output = result.value as SpiceMessage
        assertEquals("executed", output.getData<String>("tool_result"))
        assertEquals("dynamic", output.getData<String>("source"))
    }

    @Test
    fun `Graph execution fails gracefully when dynamic tool not found`() = runTest {
        val testGraph = graph("missing-tool-test") {
            tool("dynamic", ToolResolver.byRegistry(
                nameSelector = { msg -> msg.getData<String>("toolId")!! },
                namespace = "empty"
            ))
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("test", "user")
            .withData(mapOf("toolId" to "nonexistent"))

        val result = runner.execute(testGraph, message)

        assertIs<SpiceResult.Failure>(result)
        assertIs<SpiceError.ToolLookupError>(result.error)
    }

    @Test
    fun `Graph DSL static tool still works (backward compatibility)`() = runTest {
        val tool = SimpleTool("static_tool", "Static tool") { _ ->
            ToolResult.success("static result")
        }

        val testGraph = graph("static-test") {
            tool("static", tool)
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("test", "user")

        val result = runner.execute(testGraph, message)

        assertIs<SpiceResult.Success<SpiceMessage>>(result)
        assertEquals("static result", (result.value as SpiceMessage).getData<String>("tool_result"))
    }

    @Test
    fun `Fallback resolver in graph uses fallback when primary fails`() = runTest {
        val fallbackTool = SimpleTool("fallback_tool", "Fallback") { _ ->
            ToolResult.success("fallback executed")
        }

        val testGraph = graph("fallback-test") {
            tool("resilient", ToolResolver.fallback(
                // Primary: registry lookup that will fail
                ToolResolver.byRegistry(
                    nameSelector = { "nonexistent" },
                    namespace = "empty"
                ),
                // Fallback: static tool
                ToolResolver.static(fallbackTool)
            ))
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("test", "user")

        val result = runner.execute(testGraph, message)

        assertIs<SpiceResult.Success<SpiceMessage>>(result)
        assertEquals("fallback executed", (result.value as SpiceMessage).getData<String>("tool_result"))
    }

    // ==================================
    // Build-time Validation Tests
    // ==================================

    @Test
    fun `Graph build throws exception when strict validation fails`() {
        // Register a different tool so registry isn't empty, but NOT the expected tool
        val dummyTool = SimpleTool("other_tool", "Other") { _ ->
            ToolResult.success("result")
        }
        ToolRegistry.register(dummyTool, namespace = "global")

        try {
            graph("strict-validation-test") {
                tool("strict-tool", ToolResolver.byRegistry(
                    nameSelector = { "missing_tool" },
                    namespace = "global",
                    expectedTools = setOf("missing_tool"),
                    strict = true  // ERROR level
                ))
            }
            // Should not reach here
            assertTrue(false, "Expected IllegalStateException to be thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("ToolResolver validation failed"))
            assertTrue(e.message!!.contains("missing_tool"))
        }
    }

    @Test
    fun `Graph build succeeds with warning when non-strict validation fails`() {
        // Register a different tool so registry isn't empty (validation runs)
        val dummyTool = SimpleTool("other_tool", "Other") { _ ->
            ToolResult.success("result")
        }
        ToolRegistry.register(dummyTool, namespace = "global")

        // This should NOT throw - warnings don't fail the build
        val testGraph = graph("warning-validation-test") {
            tool("warn-tool", ToolResolver.byRegistry(
                nameSelector = { "missing_tool" },
                namespace = "global",
                expectedTools = setOf("missing_tool"),
                strict = false  // WARNING level (default)
            ))
        }

        // Graph should be built successfully
        assertNotNull(testGraph)
        assertEquals("warning-validation-test", testGraph.id)
    }

    @Test
    fun `Graph build skips validation when registry is empty`() {
        // Registry is empty (cleared in @BeforeEach)
        assertTrue(ToolRegistry.getAll().isEmpty())

        // Even with strict=true, validation should be skipped (not throw)
        // This allows early wiring before tools are registered
        val testGraph = graph("early-wiring-test") {
            tool("early-tool", ToolResolver.byRegistry(
                nameSelector = { "some_tool" },
                namespace = "global",
                expectedTools = setOf("some_tool"),
                strict = true  // Would normally ERROR, but skipped when registry empty
            ))
        }

        // Graph should be built successfully
        assertNotNull(testGraph)
        assertEquals("early-wiring-test", testGraph.id)
    }
}
