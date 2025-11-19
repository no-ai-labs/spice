package io.github.noailabs.spice.tool

import io.github.noailabs.spice.SimpleTool
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolLifecycleListenerTest {

    @Test
    fun `listener receives all callbacks on successful tool execution`() = runTest {
        // Track callback order
        val callbacks = mutableListOf<String>()
        var capturedResult: ToolResult? = null
        var capturedDuration: Long? = null

        val listener = object : ToolLifecycleListener {
            override suspend fun onInvoke(context: ToolInvocationContext) {
                callbacks.add("onInvoke")
            }

            override suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long) {
                callbacks.add("onSuccess")
                capturedResult = result
                capturedDuration = durationMs
            }

            override suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long) {
                callbacks.add("onFailure")
            }

            override suspend fun onComplete(context: ToolInvocationContext) {
                callbacks.add("onComplete")
            }
        }

        val tool = SimpleTool(
            name = "test_tool",
            description = "Test tool"
        ) { _ ->
            ToolResult.success("result", mapOf("key" to "value"))
        }

        val testGraph = graph("test") {
            toolLifecycleListeners(listener)
            tool("tool1", tool)
            // No edge needed - graph ends after single node
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("test", "user")
        val result = runner.execute(testGraph, message)

        assertTrue(result is SpiceResult.Success)
        assertEquals(listOf("onInvoke", "onSuccess", "onComplete"), callbacks)
        assertEquals("result", capturedResult?.result)
        assertTrue(capturedDuration != null && capturedDuration!! >= 0)
    }

    @Test
    fun `listener receives onFailure on SpiceResult Failure`() = runTest {
        val callbacks = mutableListOf<String>()
        var capturedError: SpiceError? = null

        val listener = object : ToolLifecycleListener {
            override suspend fun onInvoke(context: ToolInvocationContext) {
                callbacks.add("onInvoke")
            }

            override suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long) {
                callbacks.add("onSuccess")
            }

            override suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long) {
                callbacks.add("onFailure")
                capturedError = error
            }

            override suspend fun onComplete(context: ToolInvocationContext) {
                callbacks.add("onComplete")
            }
        }

        val tool = SimpleTool(
            name = "failing_tool",
            description = "Tool that fails"
        ) { _ ->
            throw RuntimeException("Tool failed")
        }

        val testGraph = graph("test") {
            toolLifecycleListeners(listener)
            tool("tool1", tool)
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("test", "user")
        val result = runner.execute(testGraph, message)

        assertTrue(result is SpiceResult.Failure)
        assertEquals(listOf("onInvoke", "onFailure", "onComplete"), callbacks)
        assertTrue(capturedError != null)
    }

    @Test
    fun `listener exception does not break tool execution`() = runTest {
        val toolExecuted = mutableListOf<Boolean>()

        val brokenListener = object : ToolLifecycleListener {
            override suspend fun onInvoke(context: ToolInvocationContext) {
                throw RuntimeException("Listener broken")
            }

            override suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long) {
                throw RuntimeException("Listener broken")
            }

            override suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long) {
                throw RuntimeException("Listener broken")
            }

            override suspend fun onComplete(context: ToolInvocationContext) {
                throw RuntimeException("Listener broken")
            }
        }

        val tool = SimpleTool(
            name = "test_tool",
            description = "Test tool"
        ) { _ ->
            toolExecuted.add(true)
            ToolResult.success("result")
        }

        val testGraph = graph("test") {
            toolLifecycleListeners(brokenListener)
            tool("tool1", tool)
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("test", "user")
        val result = runner.execute(testGraph, message)

        // Tool should still execute despite listener exceptions
        assertTrue(result is SpiceResult.Success)
        assertEquals(1, toolExecuted.size)
    }

    @Test
    fun `multiple listeners all receive events`() = runTest {
        val listener1Calls = mutableListOf<String>()
        val listener2Calls = mutableListOf<String>()

        val listener1 = object : ToolLifecycleListener {
            override suspend fun onInvoke(context: ToolInvocationContext) {
                listener1Calls.add("onInvoke")
            }

            override suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long) {
                listener1Calls.add("onSuccess")
            }

            override suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long) {
                listener1Calls.add("onFailure")
            }

            override suspend fun onComplete(context: ToolInvocationContext) {
                listener1Calls.add("onComplete")
            }
        }

        val listener2 = object : ToolLifecycleListener {
            override suspend fun onInvoke(context: ToolInvocationContext) {
                listener2Calls.add("onInvoke")
            }

            override suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long) {
                listener2Calls.add("onSuccess")
            }

            override suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long) {
                listener2Calls.add("onFailure")
            }

            override suspend fun onComplete(context: ToolInvocationContext) {
                listener2Calls.add("onComplete")
            }
        }

        val tool = SimpleTool(
            name = "test_tool",
            description = "Test tool"
        ) { _ ->
            ToolResult.success("result")
        }

        val testGraph = graph("test") {
            toolLifecycleListeners(listener1, listener2)
            tool("tool1", tool)
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("test", "user")
        runner.execute(testGraph, message)

        assertEquals(listOf("onInvoke", "onSuccess", "onComplete"), listener1Calls)
        assertEquals(listOf("onInvoke", "onSuccess", "onComplete"), listener2Calls)
    }

    @Test
    fun `invocation context contains correct information`() = runTest {
        var capturedContext: ToolInvocationContext? = null

        val listener = object : ToolLifecycleListener {
            override suspend fun onInvoke(context: ToolInvocationContext) {
                capturedContext = context
            }

            override suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long) {}
            override suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long) {}
            override suspend fun onComplete(context: ToolInvocationContext) {}
        }

        val tool = SimpleTool(
            name = "test_tool",
            description = "Test tool"
        ) { _ ->
            ToolResult.success("result")
        }

        val testGraph = graph("test") {
            toolLifecycleListeners(listener)
            tool("tool1", tool) { msg ->
                mapOf("param1" to "value1")
            }
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("test", "user")
            .withMetadata(mapOf("userId" to "user123"))
        runner.execute(testGraph, message)

        assertTrue(capturedContext != null)
        assertEquals("test_tool", capturedContext!!.toolName)
        assertEquals("test", capturedContext!!.graphId)
        assertEquals(mapOf("param1" to "value1"), capturedContext!!.params)
        assertEquals("user123", capturedContext!!.userId)
        assertEquals(1, capturedContext!!.attemptNumber)
    }
}
