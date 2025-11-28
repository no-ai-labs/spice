package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.checkpoint.SubgraphCheckpointContext
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

    // =====================================================================
    // Subgraph HITL Resume Tests (Spice 1.3.0)
    // =====================================================================

    @Test
    fun `subgraph HITL stores SubgraphCheckpointContext in metadata`() = runTest {
        // Given - subgraph with HITL that will pause
        val parentGraph = graph("parent") {
            agent("start", EchoAgent("start"))

            subgraph("confirm-child") {
                hitlInput("ask-user", "Please confirm")
                output("child-result")
                edge("ask-user", "child-result")
            }

            output("end")

            edge("start", "confirm-child")
            edge("confirm-child", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Test", "user")
            .withGraphContext(graphId = "parent", nodeId = null, runId = "test-run-123")

        // When
        val result = runner.execute(parentGraph, message)

        // Then - should pause at HITL with subgraph context
        assertTrue(result.isSuccess)
        val output = (result as SpiceResult.Success).value
        assertEquals(ExecutionState.WAITING, output.state)

        // Verify SubgraphCheckpointContext is in metadata
        @Suppress("UNCHECKED_CAST")
        val subgraphStack = output.metadata[SubgraphCheckpointContext.STACK_METADATA_KEY] as? List<*>
        assertNotNull(subgraphStack, "subgraphStack should be present in metadata")
        assertTrue(subgraphStack.isNotEmpty(), "subgraphStack should not be empty")

        // Verify context content
        val context = subgraphStack.first()
        assertTrue(context is SubgraphCheckpointContext || context is Map<*, *>,
            "Context should be SubgraphCheckpointContext or Map")
    }

    @Test
    fun `subgraph HITL resume applies outputMapping`() = runTest {
        // Given - subgraph with outputMapping
        val parentGraph = graph("parent") {
            agent("start", EchoAgent("start"))

            subgraph(
                id = "confirm-child",
                outputMapping = mapOf("confirmed" to "user_confirm")  // child.confirmed → parent.user_confirm
            ) {
                hitlInput("ask-user", "Please confirm")
                agent("process", ConfirmAgent("process"))  // Sets data["confirmed"] = true
                output("child-result")
                edge("ask-user", "process")
                edge("process", "child-result")
            }

            output("end")

            edge("start", "confirm-child")
            edge("confirm-child", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Test", "user")
            .withGraphContext(graphId = "parent", nodeId = null, runId = "test-run-123")

        // Execute until WAITING
        val initialResult = runner.execute(parentGraph, message)
        assertTrue(initialResult.isSuccess)
        val waitingMessage = (initialResult as SpiceResult.Success).value
        assertEquals(ExecutionState.WAITING, waitingMessage.state)

        // When - resume with user response
        val resumeMessage = waitingMessage.copy(
            data = waitingMessage.data + mapOf("user_response" to "yes")
        )
        val resumeResult = runner.resume(parentGraph, resumeMessage)

        // Then - should complete with outputMapping applied
        assertTrue(resumeResult.isSuccess, "Resume should succeed but got: ${(resumeResult as? SpiceResult.Failure)?.error?.message}")
        val finalOutput = (resumeResult as SpiceResult.Success).value
        assertEquals(ExecutionState.COMPLETED, finalOutput.state)

        // Verify outputMapping: child's "confirmed" → parent's "user_confirm"
        val userConfirm = finalOutput.getData<Boolean>("user_confirm")
        assertNotNull(userConfirm, "user_confirm should be present after outputMapping: ${finalOutput.data.keys}")
        assertTrue(userConfirm, "user_confirm should be true")
    }

    @Test
    fun `nested subgraph HITL resume works with stack`() = runTest {
        // Given - parent → level1 → level2 (with HITL)
        val parentGraph = graph("parent") {
            subgraph("level1") {
                subgraph("level2") {
                    hitlInput("deep-hitl", "Deep confirm needed")
                    agent("deep-process", EchoAgent("deep-process"))
                    output("deep-result")
                    edge("deep-hitl", "deep-process")
                    edge("deep-process", "deep-result")
                }
                output("level1-result")
                edge("level2", "level1-result")
            }
            output("end")
            edge("level1", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Nested test", "user")
            .withGraphContext(graphId = "parent", nodeId = null, runId = "nested-run-456")

        // Execute until WAITING (at level2's HITL)
        val initialResult = runner.execute(parentGraph, message)
        assertTrue(initialResult.isSuccess)
        val waitingMessage = (initialResult as SpiceResult.Success).value
        assertEquals(ExecutionState.WAITING, waitingMessage.state)

        // Verify stack has 2 entries (level1 and level2)
        @Suppress("UNCHECKED_CAST")
        val subgraphStack = waitingMessage.metadata[SubgraphCheckpointContext.STACK_METADATA_KEY] as? List<*>
        assertNotNull(subgraphStack, "subgraphStack should be present")
        assertEquals(2, subgraphStack.size, "Stack should have 2 contexts for nested subgraphs")

        // When - resume with user response
        val resumeMessage = waitingMessage.copy(
            data = waitingMessage.data + mapOf("user_response" to "confirmed")
        )
        val resumeResult = runner.resume(parentGraph, resumeMessage)

        // Then - should complete successfully
        assertTrue(resumeResult.isSuccess, "Nested resume should succeed but got: ${(resumeResult as? SpiceResult.Failure)?.error?.message}")
        val finalOutput = (resumeResult as SpiceResult.Success).value
        assertEquals(ExecutionState.COMPLETED, finalOutput.state)

        // Verify stack is cleaned up
        val finalStack = finalOutput.metadata[SubgraphCheckpointContext.STACK_METADATA_KEY]
        assertTrue(finalStack == null || (finalStack as? List<*>)?.isEmpty() == true,
            "subgraphStack should be empty after completion")
    }

    @Test
    fun `subgraph HITL resume preserves parent data`() = runTest {
        // Given - parent has data that should survive subgraph HITL
        val parentGraph = graph("parent") {
            agent("start", CounterAgent("start"))  // Sets counter = 1

            subgraph("child") {
                hitlInput("ask-user", "Need input")
                agent("child-agent", EchoAgent("child-agent"))
                output("child-result")
                edge("ask-user", "child-agent")
                edge("child-agent", "child-result")
            }

            agent("after", CounterAgent("after"))  // Should see counter from start
            output("end")

            edge("start", "child")
            edge("child", "after")
            edge("after", "end")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Test", "user")
            .withData(mapOf("counter" to 0))

        // Execute until WAITING
        val initialResult = runner.execute(parentGraph, message)
        assertTrue(initialResult.isSuccess)
        val waitingMessage = (initialResult as SpiceResult.Success).value
        assertEquals(ExecutionState.WAITING, waitingMessage.state)

        // Verify parent's counter is preserved
        val counterBeforeResume = waitingMessage.getData<Int>("counter")
        assertEquals(1, counterBeforeResume, "counter should be 1 from start agent")

        // When - resume
        val resumeMessage = waitingMessage.copy(
            data = waitingMessage.data + mapOf("user_response" to "okay")
        )
        val resumeResult = runner.resume(parentGraph, resumeMessage)

        // Then
        assertTrue(resumeResult.isSuccess)
        val finalOutput = (resumeResult as SpiceResult.Success).value
        assertEquals(ExecutionState.COMPLETED, finalOutput.state)

        // Counter should be incremented by "after" agent: 1 → 2
        val finalCounter = finalOutput.getData<Int>("counter")
        assertEquals(2, finalCounter, "counter should be 2 after resume and 'after' agent")
    }

    // Test agent that sets confirmed = true
    class ConfirmAgent(override val id: String) : Agent {
        override val name = id
        override val description = "Confirm agent"
        override val capabilities = listOf("confirm")

        override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
            return SpiceResult.success(
                message.copy(
                    content = "Confirmed!",
                    from = id,
                    data = message.data + mapOf("confirmed" to true)
                )
            )
        }
    }
}
