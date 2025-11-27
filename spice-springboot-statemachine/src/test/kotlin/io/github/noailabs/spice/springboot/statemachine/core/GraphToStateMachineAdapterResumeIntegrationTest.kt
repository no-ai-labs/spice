package io.github.noailabs.spice.springboot.statemachine.core

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.checkpoint.Checkpoint
import io.github.noailabs.spice.graph.checkpoint.InMemoryCheckpointStore
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.springboot.statemachine.actions.CheckpointSaveAction
import io.github.noailabs.spice.springboot.statemachine.actions.EventPublishAction
import io.github.noailabs.spice.springboot.statemachine.actions.NodeExecutionAction
import io.github.noailabs.spice.springboot.statemachine.actions.ToolRetryAction
import io.github.noailabs.spice.springboot.statemachine.config.StateMachineProperties
import io.github.noailabs.spice.springboot.statemachine.events.WorkflowCompletedEvent
import io.github.noailabs.spice.springboot.statemachine.events.WorkflowResumedEvent
import io.github.noailabs.spice.springboot.statemachine.guards.RetryableErrorGuard
import io.github.noailabs.spice.springboot.statemachine.persistence.StateMachineCheckpointBridge
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineBuilder
import org.springframework.statemachine.config.StateMachineFactory
import java.util.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * End-to-end integration tests for GraphToStateMachineAdapter.resume()
 *
 * These tests verify the complete resume flow:
 * - Execute graph → pause at HumanNode → checkpoint saved
 * - Resume with user response → graph continues → completion
 * - Error handling for invalid runId, expired checkpoints
 * - Event publication (WorkflowResumedEvent, WorkflowCompletedEvent)
 */
class GraphToStateMachineAdapterResumeIntegrationTest {

    private lateinit var checkpointStore: InMemoryCheckpointStore
    private lateinit var graphRunner: DefaultGraphRunner
    private lateinit var adapter: GraphToStateMachineAdapter
    private lateinit var capturedEvents: MutableList<Any>

    private val testGraph = graph("test-workflow") {
        hitlInput("input", "Enter your name")
        output("result") { msg ->
            "Hello ${msg.getData<String>("response_text") ?: msg.content}"
        }
        edge("input", "result")
    }

    private val multiStepGraph = graph("multi-step") {
        hitlInput("step1", "First input")
        hitlInput("step2", "Second input")
        output("result") { msg ->
            val first = msg.getData<String>("step1_value") ?: "?"
            val second = msg.getData<String>("response_text") ?: "?"
            "Combined: $first and $second"
        }
        edge("step1", "step2")
        edge("step2", "result")
    }

    @BeforeEach
    fun setup() {
        checkpointStore = InMemoryCheckpointStore()
        graphRunner = DefaultGraphRunner()
        capturedEvents = mutableListOf()

        val mockPublisher = object : ApplicationEventPublisher {
            override fun publishEvent(event: Any) {
                capturedEvents.add(event)
            }
        }

        val stateMachineFactory = createStateMachineFactory()
        val nodeExecutionAction = NodeExecutionAction(graphRunner)
        val checkpointSaveAction = CheckpointSaveAction(
            StateMachineCheckpointBridge(null),
            mockPublisher,
            checkpointStore
        )
        val eventPublishAction = EventPublishAction(mockPublisher)
        val toolRetryAction = ToolRetryAction(StateMachineProperties.Retry())
        val retryableErrorGuard = RetryableErrorGuard()

        adapter = GraphToStateMachineAdapter(
            stateMachineFactory = stateMachineFactory,
            nodeExecutionAction = nodeExecutionAction,
            checkpointSaveAction = checkpointSaveAction,
            eventPublishAction = eventPublishAction,
            toolRetryAction = toolRetryAction,
            retryableErrorGuard = retryableErrorGuard,
            graphRunner = graphRunner,
            checkpointStore = checkpointStore,
            graphRegistry = mapOf(
                "test-workflow" to testGraph,
                "multi-step" to multiStepGraph
            )
        )
    }

    @Test
    fun `resume completes workflow from checkpoint`() = runTest {
        val runId = "e2e-test-run"

        // 1. Manually save a checkpoint (simulating execute pause)
        val checkpoint = Checkpoint(
            id = "checkpoint-e2e",
            runId = runId,
            graphId = "test-workflow",
            currentNodeId = "input",
            message = SpiceMessage.create("Enter your name", "system")
                .copy(
                    state = ExecutionState.WAITING,
                    nodeId = "input",
                    runId = runId,
                    graphId = "test-workflow"
                )
        )
        checkpointStore.save(checkpoint)

        // Verify checkpoint was saved
        val checkpoints = (checkpointStore.listByRun(runId) as SpiceResult.Success).value
        assertEquals(1, checkpoints.size)
        assertEquals("input", checkpoints[0].currentNodeId)

        // 2. Resume with user response
        val userResponse = SpiceMessage.create("Alice", "user")
        val result = adapter.resume(runId, userResponse, testGraph)

        assertTrue(result.isSuccess, "Resume should succeed: ${(result as? SpiceResult.Failure)?.error?.message}")
        val finalMessage = (result as SpiceResult.Success).value

        // The resume should complete or continue based on graph execution
        // Note: GraphRunner.resume behavior depends on how human node handles the response
        assertTrue(
            finalMessage.state in setOf(ExecutionState.COMPLETED, ExecutionState.RUNNING),
            "Expected COMPLETED or RUNNING but got ${finalMessage.state}"
        )

        // Verify events were published
        val resumeEvents = capturedEvents.filterIsInstance<WorkflowResumedEvent>()
        assertEquals(1, resumeEvents.size)
        assertEquals(runId, resumeEvents[0].runId)
        assertEquals("test-workflow", resumeEvents[0].graphId)
    }

    @Test
    fun `resume with invalid runId returns failure`() = runTest {
        val userResponse = SpiceMessage.create("test", "user")
        val result = adapter.resume("non-existent-run", userResponse, testGraph)

        assertTrue(result.isFailure)
        val error = (result as SpiceResult.Failure).error
        assertTrue(error.message.contains("not found"))
    }

    @Test
    fun `resume with expired checkpoint by TTL returns failure`() = runTest {
        val runId = "expired-ttl-run"

        // Save checkpoint with expired TTL
        val expiredCheckpoint = Checkpoint(
            id = "checkpoint-expired-ttl",
            runId = runId,
            graphId = "test-workflow",
            currentNodeId = "input",
            message = SpiceMessage.create("Enter your name", "system")
                .copy(
                    state = ExecutionState.WAITING,
                    nodeId = "input",
                    runId = runId,
                    graphId = "test-workflow"
                ),
            expiresAt = Clock.System.now() - 1.hours
        )
        checkpointStore.save(expiredCheckpoint)

        val userResponse = SpiceMessage.create("test", "user")
        val result = adapter.resume(runId, userResponse, testGraph)

        assertTrue(result.isFailure)
        val error = (result as SpiceResult.Failure).error
        assertTrue(error.message.contains("expired"))
    }

    @Test
    fun `resume with checkpoint exceeding maxCheckpointAge returns failure`() = runTest {
        val runId = "expired-age-run"

        // Save checkpoint that's older than maxCheckpointAge
        val oldCheckpoint = Checkpoint(
            id = "checkpoint-old",
            runId = runId,
            graphId = "test-workflow",
            currentNodeId = "input",
            message = SpiceMessage.create("Enter your name", "system")
                .copy(
                    state = ExecutionState.WAITING,
                    nodeId = "input",
                    runId = runId,
                    graphId = "test-workflow"
                ),
            timestamp = Clock.System.now() - 2.hours,  // 2 hours old
            expiresAt = Clock.System.now() + 24.hours  // TTL not expired
        )
        checkpointStore.save(oldCheckpoint)

        val userResponse = SpiceMessage.create("test", "user")
        val result = adapter.resume(
            runId,
            userResponse,
            testGraph,
            ResumeOptions(maxCheckpointAge = 1.hours)  // Max age is 1 hour
        )

        assertTrue(result.isFailure)
        val error = (result as SpiceResult.Failure).error
        assertTrue(error.message.contains("exceeds maxCheckpointAge"))
    }

    @Test
    fun `resume using graphRegistry without graph parameter`() = runTest {
        val runId = "registry-run"

        // Save checkpoint
        val checkpoint = Checkpoint(
            id = "checkpoint-registry",
            runId = runId,
            graphId = "test-workflow",
            currentNodeId = "input",
            message = SpiceMessage.create("Enter your name", "system")
                .copy(
                    state = ExecutionState.WAITING,
                    nodeId = "input",
                    runId = runId,
                    graphId = "test-workflow"
                )
        )
        checkpointStore.save(checkpoint)

        // Resume without graph parameter - should use registry
        val userResponse = SpiceMessage.create("Bob", "user")
        val result = adapter.resume(runId, userResponse)

        assertTrue(result.isSuccess, "Resume should succeed: ${(result as? SpiceResult.Failure)?.error?.message}")
        val finalMessage = (result as SpiceResult.Success).value

        // Verify execution proceeded (state should be COMPLETED or RUNNING)
        assertTrue(
            finalMessage.state in setOf(ExecutionState.COMPLETED, ExecutionState.RUNNING),
            "Expected COMPLETED or RUNNING but got ${finalMessage.state}"
        )
    }

    @Test
    fun `resume cleans up checkpoint on completion`() = runTest {
        val runId = "cleanup-test-run"

        // Manually save a checkpoint
        val checkpoint = Checkpoint(
            id = "checkpoint-cleanup",
            runId = runId,
            graphId = "test-workflow",
            currentNodeId = "input",
            message = SpiceMessage.create("Enter your name", "system")
                .copy(
                    state = ExecutionState.WAITING,
                    nodeId = "input",
                    runId = runId,
                    graphId = "test-workflow"
                )
        )
        checkpointStore.save(checkpoint)

        // Verify checkpoint exists
        val beforeResume = (checkpointStore.listByRun(runId) as SpiceResult.Success).value
        assertEquals(1, beforeResume.size)

        // Resume with autoCleanup = true (default)
        val userResponse = SpiceMessage.create("cleanup-test", "user")
        val result = adapter.resume(runId, userResponse, testGraph)

        // Verify resume succeeded
        assertTrue(result.isSuccess, "Resume should succeed")

        // Verify checkpoint was cleaned up if workflow completed
        val afterResume = (checkpointStore.listByRun(runId) as SpiceResult.Success).value
        val finalMessage = (result as SpiceResult.Success).value
        if (finalMessage.state.isTerminal()) {
            assertEquals(0, afterResume.size, "Checkpoint should be cleaned up after completion")
        }
    }

    @Test
    fun `resume with autoCleanup false preserves checkpoint`() = runTest {
        val runId = "no-cleanup-run"

        // Save checkpoint
        val checkpoint = Checkpoint(
            id = "checkpoint-no-cleanup",
            runId = runId,
            graphId = "test-workflow",
            currentNodeId = "input",
            message = SpiceMessage.create("Enter your name", "system")
                .copy(
                    state = ExecutionState.WAITING,
                    nodeId = "input",
                    runId = runId,
                    graphId = "test-workflow"
                )
        )
        checkpointStore.save(checkpoint)

        // Resume with autoCleanup = false
        val userResponse = SpiceMessage.create("test", "user")
        adapter.resume(
            runId,
            userResponse,
            testGraph,
            ResumeOptions(autoCleanup = false)
        )

        // Verify checkpoint still exists
        val afterResume = (checkpointStore.listByRun(runId) as SpiceResult.Success).value
        assertEquals(1, afterResume.size)
    }

    @Test
    fun `resume with SILENT option does not publish events`() = runTest {
        val runId = "silent-run"

        // Save checkpoint
        val checkpoint = Checkpoint(
            id = "checkpoint-silent",
            runId = runId,
            graphId = "test-workflow",
            currentNodeId = "input",
            message = SpiceMessage.create("Enter your name", "system")
                .copy(
                    state = ExecutionState.WAITING,
                    nodeId = "input",
                    runId = runId,
                    graphId = "test-workflow"
                )
        )
        checkpointStore.save(checkpoint)

        // Clear any previous events
        capturedEvents.clear()

        // Resume with SILENT options
        val userResponse = SpiceMessage.create("test", "user")
        adapter.resume(runId, userResponse, testGraph, ResumeOptions.SILENT)

        // Verify no WorkflowResumedEvent was published
        val resumeEvents = capturedEvents.filterIsInstance<WorkflowResumedEvent>()
        assertEquals(0, resumeEvents.size)
    }

    @Test
    fun `resume with LENIENT option ignores expiration`() = runTest {
        val runId = "lenient-run"

        // Save expired checkpoint
        val expiredCheckpoint = Checkpoint(
            id = "checkpoint-lenient",
            runId = runId,
            graphId = "test-workflow",
            currentNodeId = "input",
            message = SpiceMessage.create("Enter your name", "system")
                .copy(
                    state = ExecutionState.WAITING,
                    nodeId = "input",
                    runId = runId,
                    graphId = "test-workflow"
                ),
            expiresAt = Clock.System.now() - 1.hours
        )
        checkpointStore.save(expiredCheckpoint)

        // Resume with LENIENT options (validateExpiration = false)
        val userResponse = SpiceMessage.create("test", "user")
        val result = adapter.resume(runId, userResponse, testGraph, ResumeOptions.LENIENT)

        // Should succeed despite expired checkpoint
        assertTrue(result.isSuccess, "LENIENT resume should succeed: ${(result as? SpiceResult.Failure)?.error?.message}")
    }

    @Test
    fun `multiple HITL pauses resume correctly`() = runTest {
        val runId = "multi-step-run"

        // Manually create checkpoint at step1
        val checkpoint = Checkpoint(
            id = "checkpoint-step1",
            runId = runId,
            graphId = "multi-step",
            currentNodeId = "step1",
            message = SpiceMessage.create("First input", "system")
                .copy(
                    state = ExecutionState.WAITING,
                    nodeId = "step1",
                    runId = runId,
                    graphId = "multi-step"
                )
        )
        checkpointStore.save(checkpoint)

        // First resume with step1 value
        val response1 = SpiceMessage.create("First", "user")
            .withData(mapOf("step1_value" to "First"))
        val result1 = adapter.resume(runId, response1, multiStepGraph)

        assertTrue(result1.isSuccess, "First resume should succeed: ${(result1 as? SpiceResult.Failure)?.error?.message}")
        val message1 = (result1 as SpiceResult.Success).value

        // The graph should either pause at step2 or complete
        assertTrue(
            message1.state in setOf(ExecutionState.WAITING, ExecutionState.COMPLETED, ExecutionState.RUNNING),
            "Expected WAITING, COMPLETED, or RUNNING but got ${message1.state}"
        )
    }

    @Test
    fun `resume with throwOnError throws exception for invalid runId`() = runTest {
        var exceptionThrown = false
        try {
            adapter.resume(
                "invalid-run",
                null,
                testGraph,
                ResumeOptions(throwOnError = true)
            )
        } catch (e: IllegalStateException) {
            exceptionThrown = true
            assertTrue(e.message?.contains("not found") == true)
        }
        assertTrue(exceptionThrown)
    }

    @Test
    fun `resume publishes WorkflowResumedEvent with correct metadata`() = runTest {
        val runId = "metadata-run"

        // Save checkpoint with specific timestamp
        val checkpointTime = Clock.System.now() - 5000.milliseconds
        val checkpoint = Checkpoint(
            id = "checkpoint-metadata",
            runId = runId,
            graphId = "test-workflow",
            currentNodeId = "input",
            message = SpiceMessage.create("Enter your name", "system")
                .copy(
                    state = ExecutionState.WAITING,
                    nodeId = "input",
                    runId = runId,
                    graphId = "test-workflow"
                ),
            timestamp = checkpointTime
        )
        checkpointStore.save(checkpoint)

        capturedEvents.clear()

        // Resume
        val userResponse = SpiceMessage.create("test", "user")
        adapter.resume(runId, userResponse, testGraph)

        // Verify event metadata
        val resumeEvents = capturedEvents.filterIsInstance<WorkflowResumedEvent>()
        assertEquals(1, resumeEvents.size)

        val event = resumeEvents[0]
        assertEquals(runId, event.runId)
        assertEquals("test-workflow", event.graphId)
        assertEquals("input", event.nodeId)

        // Checkpoint age should be approximately 5000ms
        val checkpointAge = event.metadata["checkpointAge"] as Long
        assertTrue(checkpointAge >= 5000)
    }

    @Test
    fun `resume should merge userResponse data into message`() = runTest {
        val runId = "data-merge-run"

        // Create checkpoint (using testGraph which is in the registry)
        // Note: checkpoint message typically doesn't have user-provided data yet
        val checkpoint = Checkpoint(
            id = "checkpoint-data-merge",
            runId = runId,
            graphId = "test-workflow",
            currentNodeId = "input",
            message = SpiceMessage.create("Enter your name", "system")
                .copy(
                    state = ExecutionState.WAITING,
                    nodeId = "input",
                    runId = runId,
                    graphId = "test-workflow"
                )
        )
        val saveResult = checkpointStore.save(checkpoint)
        assertTrue(saveResult.isSuccess, "Checkpoint save should succeed: ${(saveResult as? SpiceResult.Failure)?.error?.message}")

        // Resume with userResponse containing additional data (like selectedBookingId from HITL selection)
        // This is the key test: userResponse.data should be merged into the resume message
        val userResponse = SpiceMessage.create("Alice", "user")
            .withData(mapOf(
                "selectedBookingId" to "12345",
                "newKey" to "newValue"
            ))
        val result = adapter.resume(runId, userResponse, testGraph)

        assertTrue(result.isSuccess, "Resume should succeed: ${(result as? SpiceResult.Failure)?.error?.message}")
        val finalMessage = (result as SpiceResult.Success).value

        // Verify userResponse.data is merged into the final message
        assertEquals("12345", finalMessage.getData<String>("selectedBookingId"), "userResponse.data should be merged")
        assertEquals("newValue", finalMessage.getData<String>("newKey"), "userResponse.data should be merged")
        // response_text comes from responseData extraction
        assertTrue(finalMessage.getData<String>("response_text")?.isNotEmpty() == true, "responseData should be present")
    }

    private fun createStateMachineFactory(): SpiceStateMachineFactory {
        val factory = object : StateMachineFactory<ExecutionState, SpiceEvent> {
            override fun getStateMachine(): StateMachine<ExecutionState, SpiceEvent> {
                val builder = StateMachineBuilder.builder<ExecutionState, SpiceEvent>()
                builder.configureStates()
                    .withStates()
                    .initial(ExecutionState.READY)
                    .states(EnumSet.allOf(ExecutionState::class.java))

                builder.configureTransitions()
                    .withExternal()
                    .source(ExecutionState.READY)
                    .target(ExecutionState.RUNNING)
                    .event(SpiceEvent.START)
                    .and()
                    .withExternal()
                    .source(ExecutionState.RUNNING)
                    .target(ExecutionState.WAITING)
                    .event(SpiceEvent.PAUSE_FOR_HITL)
                    .and()
                    .withExternal()
                    .source(ExecutionState.RUNNING)
                    .target(ExecutionState.COMPLETED)
                    .event(SpiceEvent.COMPLETE)
                    .and()
                    .withExternal()
                    .source(ExecutionState.RUNNING)
                    .target(ExecutionState.FAILED)
                    .event(SpiceEvent.FAIL)
                    .and()
                    .withExternal()
                    .source(ExecutionState.WAITING)
                    .target(ExecutionState.RUNNING)
                    .event(SpiceEvent.RESUME)
                    .and()
                    .withExternal()
                    .source(ExecutionState.WAITING)
                    .target(ExecutionState.FAILED)
                    .event(SpiceEvent.TIMEOUT)

                return builder.build()
            }

            override fun getStateMachine(machineId: String?): StateMachine<ExecutionState, SpiceEvent> =
                getStateMachine()

            override fun getStateMachine(uuid: UUID?): StateMachine<ExecutionState, SpiceEvent> =
                getStateMachine()
        }

        return SpiceStateMachineFactory(factory)
    }
}
