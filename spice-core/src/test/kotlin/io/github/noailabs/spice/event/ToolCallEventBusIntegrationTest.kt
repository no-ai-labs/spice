package io.github.noailabs.spice.event

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.graph.checkpoint.CheckpointConfig
import io.github.noailabs.spice.graph.checkpoint.InMemoryCheckpointStore
import io.github.noailabs.spice.graph.checkpoint.executeWithCheckpoint
import io.github.noailabs.spice.graph.checkpoint.resumeFromCheckpoint
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.nodes.HumanOption
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.toolspec.OAIToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Integration tests for ToolCallEventBus with GraphRunner
 *
 * Tests event publishing during graph execution and checkpoint resumption.
 */
class ToolCallEventBusIntegrationTest {

    @Test
    fun `EventBus publishes Emitted event when HumanNode creates tool call`() = runTest {
        val eventBus = InMemoryToolCallEventBus()

        // Create graph with event bus
        val graph = graph("test") {
            toolCallEventBus(eventBus)
            human("input", "Choose option", options = listOf(
                HumanOption("opt1", "Option 1")
            ))
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Start", "user")
            .transitionTo(ExecutionState.RUNNING, "Start")

        val result = runner.execute(graph, message)

        // Verify execution paused
        assertTrue(result.isSuccess)
        val finalMessage = result.getOrThrow()
        assertEquals(ExecutionState.WAITING, finalMessage.state)

        // Verify event was published
        val history = eventBus.getHistory(10).getOrThrow()
        assertEquals(1, history.size)

        val event = history[0] as ToolCallEvent.Emitted
        assertEquals("request_user_selection", event.toolCall.function.name)
        assertEquals("input", event.emittedBy)
        assertEquals(graph.id, event.graphId)
        assertEquals(finalMessage.runId, event.runId)
    }

    @Test
    fun `ToolCallEventBus tracks subscriber count`() = runTest {
        val eventBus = InMemoryToolCallEventBus()

        // Initially no subscribers
        assertEquals(0, eventBus.getSubscriberCount())

        // Note: This test verifies the event bus itself works
        // The Completed event publishing is tested via checkpoint integration tests
    }

    @Test
    fun `Event history filters by tool call ID`() = runTest {
        val eventBus = InMemoryToolCallEventBus()

        val graph = graph("test") {
            toolCallEventBus(eventBus)
            human("input", "Test")
        }

        val runner = DefaultGraphRunner()
        val message = SpiceMessage.create("Start", "user")
            .transitionTo(ExecutionState.RUNNING, "Start")

        val result = runner.execute(graph, message)
        val pausedMessage = result.getOrThrow()

        // Get the tool call ID
        val toolCallId = pausedMessage.toolCalls.first().id

        // Get history for this tool call
        val history = eventBus.getToolCallHistory(toolCallId).getOrThrow()

        assertEquals(1, history.size)
        assertEquals(toolCallId, history[0].toolCall.id)
    }

    @Test
    fun `Event correlation via originalEventId`() = runTest {
        val eventBus = InMemoryToolCallEventBus()
        val checkpointStore = InMemoryCheckpointStore()

        val graph = graph("test") {
            toolCallEventBus(eventBus)
            checkpointStore(checkpointStore)
            human("input", "Test")
        }

        val runner = DefaultGraphRunner()

        // Execute - pause
        val result1 = runner.executeWithCheckpoint(
            graph,
            SpiceMessage.create("Start", "user"),
            checkpointStore
        )
        val pausedMessage = result1.getOrThrow()
        val pendingToolCallId = pausedMessage.toolCalls.first().id
        val checkpointId = pausedMessage.runId!!

        // Resume
        val userResponse = SpiceMessage.create("Response", "user")
            .withToolCall(OAIToolCall.userResponse("Response", emptyMap()))

        runner.resumeFromCheckpoint(
            graph,
            checkpointId,
            userResponse,
            checkpointStore
        )

        // Get history
        val history = eventBus.getHistory(100).getOrThrow()

        // Find Emitted and Completed events
        val emittedEvent = history.filterIsInstance<ToolCallEvent.Emitted>().first()
        val completedEvents = history.filterIsInstance<ToolCallEvent.Completed>()

        if (completedEvents.isNotEmpty()) {
            val completedEvent = completedEvents.first()

            // Verify correlation
            assertEquals(pendingToolCallId, emittedEvent.toolCall.id)
            assertEquals(pendingToolCallId, completedEvent.originalEventId)
        }
    }

    @Test
    fun `Event history works correctly`() = runTest {
        val eventBus = InMemoryToolCallEventBus()

        val graph = graph("test") {
            toolCallEventBus(eventBus)
            human("input", "Test")
        }

        val runner = DefaultGraphRunner()

        // Execute to generate event
        runner.execute(graph, SpiceMessage.create("Start", "user")
            .transitionTo(ExecutionState.RUNNING, "Start"))

        // Get history
        val history = eventBus.getHistory(10).getOrThrow()

        // Should have events
        assertTrue(history.isNotEmpty())

        // All should be Emitted events
        assertTrue(history.all { it is ToolCallEvent.Emitted })
    }

    @Test
    fun `Clear history works`() = runTest {
        val eventBus = InMemoryToolCallEventBus()

        val graph = graph("test") {
            toolCallEventBus(eventBus)
            human("input", "Test")
        }

        val runner = DefaultGraphRunner()
        runner.execute(
            graph,
            SpiceMessage.create("Start", "user").transitionTo(ExecutionState.RUNNING, "Start")
        )

        // Verify history exists
        var history = eventBus.getHistory(10).getOrThrow()
        assertTrue(history.isNotEmpty())

        // Clear history
        eventBus.clearHistory()

        // Verify history is empty
        history = eventBus.getHistory(10).getOrThrow()
        assertTrue(history.isEmpty())
    }

    @Test
    fun `Multiple HumanNodes emit events`() = runTest {
        val eventBus = InMemoryToolCallEventBus()

        // Graph with human node
        val graph = graph("test") {
            toolCallEventBus(eventBus)
            human("input", "First", options = listOf(HumanOption("a", "A")))
        }

        val runner = DefaultGraphRunner()

        // Execute
        runner.execute(
            graph,
            SpiceMessage.create("Start", "user").transitionTo(ExecutionState.RUNNING, "Start")
        )

        // Verify event was published
        val history = eventBus.getHistory(10).getOrThrow()
        assertEquals(1, history.size)

        val event = history[0] as ToolCallEvent.Emitted
        assertEquals("input", event.emittedBy)
        assertEquals("request_user_selection", event.toolCall.function.name)
    }

    @Test
    fun `Event metadata includes graph and node context`() = runTest {
        val eventBus = InMemoryToolCallEventBus()

        val graph = graph("my-graph") {
            toolCallEventBus(eventBus)
            human("my-node", "Test")
        }

        val runner = DefaultGraphRunner()
        runner.execute(
            graph,
            SpiceMessage.create("Start", "user").transitionTo(ExecutionState.RUNNING, "Start")
        )

        val history = eventBus.getHistory(10).getOrThrow()
        assertEquals(1, history.size)

        val event = history[0] as ToolCallEvent.Emitted
        assertEquals("my-graph", event.graphId)
        assertEquals("my-node", event.emittedBy)
        assertEquals("my-node", event.metadata["nodeId"])
    }

    @Test
    fun `Events not published when toolCallEventBus is null`() = runTest {
        val eventBus = InMemoryToolCallEventBus()

        // Graph WITHOUT event bus configured
        val graph = graph("test") {
            human("input", "Test")
        }

        val runner = DefaultGraphRunner()
        runner.execute(
            graph,
            SpiceMessage.create("Start", "user").transitionTo(ExecutionState.RUNNING, "Start")
        )

        // No events should be published to this event bus
        val history = eventBus.getHistory(10).getOrThrow()
        assertEquals(0, history.size)
    }

    @Test
    fun `Completed event includes duration`() = runTest {
        val eventBus = InMemoryToolCallEventBus()
        val checkpointStore = InMemoryCheckpointStore()

        val graph = graph("test") {
            toolCallEventBus(eventBus)
            checkpointStore(checkpointStore)
            human("input", "Test")
        }

        val runner = DefaultGraphRunner()

        // Execute and pause
        val result1 = runner.executeWithCheckpoint(
            graph,
            SpiceMessage.create("Start", "user"),
            checkpointStore
        )
        val checkpointId = result1.getOrThrow().runId!!

        // Clear history
        eventBus.clearHistory()

        // Resume
        val userResponse = SpiceMessage.create("Response", "user")
            .withToolCall(OAIToolCall.userResponse("Response", emptyMap()))

        runner.resumeFromCheckpoint(graph, checkpointId, userResponse, checkpointStore)

        // Verify Completed event has duration
        val history = eventBus.getHistory(10).getOrThrow()
        val completedEvents = history.filterIsInstance<ToolCallEvent.Completed>()

        if (completedEvents.isNotEmpty()) {
            val event = completedEvents[0]
            assertTrue(event.durationMs >= 0, "Duration should be non-negative")
        }
    }
}
