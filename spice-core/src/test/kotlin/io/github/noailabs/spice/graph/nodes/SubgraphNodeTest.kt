package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubgraphNodeTest {

    // Test agent that echoes input with transformation
    class EchoAgent(
        override val id: String,
        private val prefix: String = "Echo"
    ) : Agent {
        override val name = id
        override val description = "Echo agent"
        override val capabilities = listOf("chat")

        override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
            // Use copy() instead of reply() to preserve state (RUNNING)
            return SpiceResult.success(
                message.copy(
                    content = "$prefix: ${message.content}",
                    from = id,
                    data = message.data + mapOf(
                        "echo_input" to message.content,
                        "agent_id" to id
                    )
                )
            )
        }
    }

    // Test agent that increments a counter
    class CounterAgent(override val id: String) : Agent {
        override val name = id
        override val description = "Counter agent"
        override val capabilities = listOf("counter")

        override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
            val counter = message.getData<Int>("counter") ?: 0
            // Use copy() instead of reply() to preserve state (RUNNING)
            return SpiceResult.success(
                message.copy(
                    content = "Counter: ${counter + 1}",
                    from = id,
                    data = message.data + mapOf("counter" to counter + 1)
                )
            )
        }
    }

    @Test
    fun `subgraph DSL builds and executes child graph`() = runTest {
        // Given
        val parentGraph = graph("parent") {
            agent("start", EchoAgent("start", "Start"))

            subgraph("child-workflow") {
                agent("child-agent", EchoAgent("child-agent", "Child"))
                output("child-output")
                edge("child-agent", "child-output")
            }

            output("parent-output")

            edge("start", "child-workflow")
            edge("child-workflow", "parent-output")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Hello", "user")

        // When
        val result = runner.execute(parentGraph, message)

        // Then
        assertTrue(result.isSuccess)
        val output = (result as SpiceResult.Success).value

        assertEquals(ExecutionState.COMPLETED, output.state)

        // Verify subgraph result is available
        val subgraphResult = output.getData<String>("subgraph_result")
        assertNotNull(subgraphResult)
        assertTrue(subgraphResult.contains("Child"))
    }

    @Test
    fun `subgraph with existing Graph instance`() = runTest {
        // Given
        val childGraph = graph("child") {
            agent("process", EchoAgent("process", "Processed"))
            output("result")
            edge("process", "result")
        }

        val parentGraph = graph("parent") {
            agent("start", EchoAgent("start", "Start"))
            subgraph("child-step", childGraph)
            output("end")

            edge("start", "child-step")
            edge("child-step", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Input", "user")

        // When
        val result = runner.execute(parentGraph, message)

        // Then
        assertTrue(result.isSuccess)
        val output = (result as SpiceResult.Success).value
        assertEquals(ExecutionState.COMPLETED, output.state)
    }

    @Test
    fun `subgraph preserves metadata across boundaries`() = runTest {
        // Given
        val parentGraph = graph("parent") {
            subgraph("child") {
                agent("agent", EchoAgent("agent"))
                output("result")
                edge("agent", "result")
            }
            output("end")
            edge("child", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Test", "user")
            .withMetadata(mapOf(
                "userId" to "user123",
                "tenantId" to "tenant456",
                "traceId" to "trace789"
            ))

        // When
        val result = runner.execute(parentGraph, message)

        // Then
        assertTrue(result.isSuccess)
        val output = (result as SpiceResult.Success).value

        // Metadata should be preserved
        assertEquals("user123", output.getMetadata<String>("userId"))
        assertEquals("tenant456", output.getMetadata<String>("tenantId"))
        assertEquals("trace789", output.getMetadata<String>("traceId"))
    }

    @Test
    fun `subgraph tracks depth correctly`() = runTest {
        // Given - nested subgraphs
        val parentGraph = graph("parent") {
            subgraph("level1") {
                subgraph("level2") {
                    agent("deep-agent", EchoAgent("deep"))
                    output("deep-result")
                    edge("deep-agent", "deep-result")
                }
                output("level2-result")
                edge("level2", "level2-result")
            }
            output("end")
            edge("level1", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Nested", "user")

        // When
        val result = runner.execute(parentGraph, message)

        // Then
        assertTrue(result.isSuccess)
        val output = (result as SpiceResult.Success).value
        assertEquals(ExecutionState.COMPLETED, output.state)

        // Final depth should be 0 after returning from all subgraphs
        val finalDepth = output.getMetadata<Int>("subgraphDepth") ?: 0
        assertEquals(0, finalDepth)
    }

    @Test
    fun `subgraph fails when exceeding max depth`() = runTest {
        // Given - subgraph with low max depth and deep nesting
        fun createDeepGraph(depth: Int): io.github.noailabs.spice.graph.Graph {
            return graph("level-$depth") {
                if (depth > 0) {
                    subgraph("nested", createDeepGraph(depth - 1), maxDepth = 3)
                    output("result")
                    edge("nested", "result")
                } else {
                    agent("leaf", EchoAgent("leaf"))
                    output("result")
                    edge("leaf", "result")
                }
            }
        }

        val parentGraph = graph("parent") {
            subgraph("deep", createDeepGraph(5), maxDepth = 3)
            output("end")
            edge("deep", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Test", "user")

        // When
        val result = runner.execute(parentGraph, message)

        // Then - should fail due to depth limit
        assertTrue(result.isFailure)
        val error = (result as SpiceResult.Failure).error
        assertTrue(error.message.contains("depth limit"))
    }

    @Test
    fun `subgraph provides result data to parent`() = runTest {
        // Given
        val parentGraph = graph("parent") {
            agent("start", CounterAgent("start"))

            subgraph("child") {
                agent("increment", CounterAgent("increment"))
                output("result")
                edge("increment", "result")
            }

            output("end")

            edge("start", "child")
            edge("child", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Start counting", "user")
            .withData(mapOf("counter" to 0))

        // When
        val result = runner.execute(parentGraph, message)

        // Then
        assertTrue(result.isSuccess, "Expected success but got: ${(result as? SpiceResult.Failure)?.error?.message}")
        val output = (result as SpiceResult.Success).value

        // Child's data should be directly available (merged into parent's data)
        val counter = output.getData<Int>("counter")
        assertNotNull(counter, "counter should be present in output data: ${output.data.keys}")
        assertEquals(2, counter) // 0 -> 1 (start) -> 2 (increment)
    }

    @Test
    fun `subgraph generates correct runId namespace`() = runTest {
        // Given
        val parentGraph = graph("parent") {
            subgraph("child-graph") {
                agent("agent", EchoAgent("agent"))
                output("result")
                edge("agent", "result")
            }
            output("end")
            edge("child-graph", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Test", "user")
            .withGraphContext(graphId = null, nodeId = null, runId = "parent-run-123")

        // When
        val result = runner.execute(parentGraph, message)

        // Then
        assertTrue(result.isSuccess)

        // Verify runId namespace generation
        val expectedChildRunId = SubgraphNode.generateSubgraphRunId("parent-run-123", "child-graph")
        assertTrue(expectedChildRunId.contains("parent-run-123"))
        assertTrue(expectedChildRunId.contains("subgraph"))
        assertTrue(expectedChildRunId.contains("child-graph"))
    }

    @Test
    fun `subgraph tracks execution path`() = runTest {
        // Given
        val parentGraph = graph("parent") {
            subgraph("child1") {
                subgraph("child2") {
                    agent("agent", EchoAgent("agent"))
                    output("result")
                    edge("agent", "result")
                }
                output("result")
                edge("child2", "result")
            }
            output("end")
            edge("child1", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Test", "user")

        // When
        val result = runner.execute(parentGraph, message)

        // Then
        assertTrue(result.isSuccess)
        val output = (result as SpiceResult.Success).value

        // Should have execution stats
        assertNotNull(output.getMetadata<Long>("lastSubgraphDuration"))
        assertNotNull(output.getMetadata<String>("lastSubgraphId"))
    }

    @Test
    fun `subgraph with HITL pause propagates to parent`() = runTest {
        // Given
        val parentGraph = graph("parent") {
            agent("start", EchoAgent("start"))

            subgraph("child-with-hitl") {
                hitlInput("user-input", "Please enter a value")
                output("result")
                edge("user-input", "result")
            }

            output("end")

            edge("start", "child-with-hitl")
            edge("child-with-hitl", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Test", "user")

        // When
        val result = runner.execute(parentGraph, message)

        // Then - should pause at HITL
        assertTrue(result.isSuccess)
        val output = (result as SpiceResult.Success).value
        assertEquals(ExecutionState.WAITING, output.state)
    }

    @Test
    fun `subgraph preserves custom metadata keys`() = runTest {
        // Given
        val customKeys = setOf("customKey1", "customKey2", "userId")

        val parentGraph = graph("parent") {
            subgraph("child", maxDepth = 10, preserveKeys = customKeys) {
                agent("agent", EchoAgent("agent"))
                output("result")
                edge("agent", "result")
            }
            output("end")
            edge("child", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Test", "user")
            .withMetadata(mapOf(
                "customKey1" to "value1",
                "customKey2" to "value2",
                "nonPreservedKey" to "should-not-appear-in-child"
            ))

        // When
        val result = runner.execute(parentGraph, message)

        // Then
        assertTrue(result.isSuccess)
        val output = (result as SpiceResult.Success).value

        // Custom keys should be preserved
        assertEquals("value1", output.getMetadata<String>("customKey1"))
        assertEquals("value2", output.getMetadata<String>("customKey2"))
    }
}
