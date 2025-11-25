package io.github.noailabs.spice.tools

import io.github.noailabs.spice.SimpleTool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolRegistry
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GenericListDispatcherTest {
    private val dispatcher = GenericListDispatcher()

    @BeforeEach
    fun setup() {
        ToolRegistry.clear()
    }

    @AfterEach
    fun tearDown() {
        ToolRegistry.clear()
    }

    @Test
    fun `dispatches to registered tool`() = runTest {
        val targetTool = SimpleTool(
            name = "user_list_tool",
            description = "returns users"
        ) { parameters ->
            ToolResult.success("users:${parameters["userId"]}")
        }
        ToolRegistry.register(targetTool)

        val result = dispatcher.execute(
            mapOf(
                "targetToolId" to targetTool.name,
                "targetParams" to mapOf("userId" to "123")
            ),
            ToolContext(agentId = "agent")
        )

        assertTrue(result is SpiceResult.Success)
        assertEquals("users:123", (result as SpiceResult.Success).value.result)
    }

    @Test
    fun `returns failure when targetToolId missing`() = runTest {
        val result = dispatcher.execute(emptyMap(), ToolContext(agentId = "agent"))

        assertTrue(result is SpiceResult.Failure)
        assertIs<SpiceError.ValidationError>((result as SpiceResult.Failure).error)
    }

    @Test
    fun `returns failure when target tool not found`() = runTest {
        val result = dispatcher.execute(
            mapOf("targetToolId" to "missing"),
            ToolContext(agentId = "agent")
        )

        assertTrue(result is SpiceResult.Failure)
        assertIs<SpiceError.ExecutionError>((result as SpiceResult.Failure).error)
    }

    @Test
    fun `returns failure when targetParams is not a map`() = runTest {
        val result = dispatcher.execute(
            mapOf(
                "targetToolId" to "any",
                "targetParams" to "not-a-map"
            ),
            ToolContext(agentId = "agent")
        )

        assertTrue(result is SpiceResult.Failure)
        assertIs<SpiceError.ValidationError>((result as SpiceResult.Failure).error)
    }
}
