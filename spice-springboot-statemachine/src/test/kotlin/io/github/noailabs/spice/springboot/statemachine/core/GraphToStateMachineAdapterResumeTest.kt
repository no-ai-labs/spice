package io.github.noailabs.spice.springboot.statemachine.core

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.checkpoint.Checkpoint
import io.github.noailabs.spice.graph.checkpoint.InMemoryCheckpointStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * Unit tests for ResumeOptions and checkpoint loading logic.
 *
 * Note: Full integration tests for GraphToStateMachineAdapter.resume()
 * require Spring context setup and will be in separate integration test class.
 */
class GraphToStateMachineAdapterResumeTest {

    private lateinit var checkpointStore: InMemoryCheckpointStore

    @BeforeEach
    fun setup() {
        checkpointStore = InMemoryCheckpointStore()
    }

    @Test
    fun `ResumeOptions DEFAULT has expected values`() {
        val options = ResumeOptions.DEFAULT

        assertTrue(options.publishEvents)
        assertFalse(options.throwOnError)
        assertTrue(options.validateExpiration)
        assertEquals(24.hours, options.maxCheckpointAge)
        assertTrue(options.autoCleanup)
    }

    @Test
    fun `ResumeOptions SILENT disables events`() {
        val options = ResumeOptions.SILENT

        assertFalse(options.publishEvents)
    }

    @Test
    fun `ResumeOptions STRICT throws on error`() {
        val options = ResumeOptions.STRICT

        assertTrue(options.throwOnError)
    }

    @Test
    fun `ResumeOptions LENIENT disables validation and cleanup`() {
        val options = ResumeOptions.LENIENT

        assertFalse(options.validateExpiration)
        assertFalse(options.autoCleanup)
    }

    @Test
    fun `effectiveCheckpointConfig uses options when no config provided`() {
        val options = ResumeOptions(
            maxCheckpointAge = 2.hours,
            autoCleanup = false
        )

        val config = options.effectiveCheckpointConfig()

        assertEquals(2.hours, config.ttl)
        assertFalse(config.autoCleanup)
    }

    @Test
    fun `checkpoint can be saved and loaded by runId`() = runTest {
        val runId = "test-run-123"
        val checkpoint = Checkpoint(
            id = "checkpoint-1",
            runId = runId,
            graphId = "test-graph",
            currentNodeId = "input",
            message = SpiceMessage.create("test", "user")
                .copy(state = ExecutionState.WAITING, nodeId = "input")
        )

        // Save checkpoint
        val saveResult = checkpointStore.save(checkpoint)
        assertTrue(saveResult.isSuccess)

        // Load by runId
        val listResult = checkpointStore.listByRun(runId)
        assertTrue(listResult.isSuccess)

        val checkpoints = (listResult as SpiceResult.Success).value
        assertEquals(1, checkpoints.size)
        assertEquals("checkpoint-1", checkpoints[0].id)
        assertEquals(runId, checkpoints[0].runId)
    }

    @Test
    fun `multiple checkpoints for same runId returns all`() = runTest {
        val runId = "multi-checkpoint-run"

        // Save multiple checkpoints
        for (i in 1..3) {
            val checkpoint = Checkpoint(
                id = "checkpoint-$i",
                runId = runId,
                graphId = "test-graph",
                currentNodeId = "node-$i",
                message = SpiceMessage.create("test-$i", "user")
                    .copy(state = ExecutionState.WAITING)
            )
            checkpointStore.save(checkpoint)
        }

        // Load by runId
        val listResult = checkpointStore.listByRun(runId)
        assertTrue(listResult.isSuccess)

        val checkpoints = (listResult as SpiceResult.Success).value
        assertEquals(3, checkpoints.size)
    }

    @Test
    fun `expired checkpoint is detected`() = runTest {
        val runId = "expired-run"
        val checkpoint = Checkpoint(
            id = "expired-checkpoint",
            runId = runId,
            graphId = "test-graph",
            currentNodeId = "input",
            message = SpiceMessage.create("test", "user")
                .copy(state = ExecutionState.WAITING),
            expiresAt = Clock.System.now() - 1.hours
        )

        checkpointStore.save(checkpoint)

        // Load and check expiration
        val listResult = checkpointStore.listByRun(runId)
        assertTrue(listResult.isSuccess)

        val loadedCheckpoint = (listResult as SpiceResult.Success).value.first()
        assertTrue(loadedCheckpoint.isExpired())
    }

    @Test
    fun `non-expired checkpoint is valid`() = runTest {
        val runId = "valid-run"
        val checkpoint = Checkpoint(
            id = "valid-checkpoint",
            runId = runId,
            graphId = "test-graph",
            currentNodeId = "input",
            message = SpiceMessage.create("test", "user")
                .copy(state = ExecutionState.WAITING),
            expiresAt = Clock.System.now() + 1.hours
        )

        checkpointStore.save(checkpoint)

        // Load and check expiration
        val listResult = checkpointStore.listByRun(runId)
        assertTrue(listResult.isSuccess)

        val loadedCheckpoint = (listResult as SpiceResult.Success).value.first()
        assertFalse(loadedCheckpoint.isExpired())
    }

    @Test
    fun `checkpoint without expiration is valid`() = runTest {
        val runId = "no-expiry-run"
        val checkpoint = Checkpoint(
            id = "no-expiry-checkpoint",
            runId = runId,
            graphId = "test-graph",
            currentNodeId = "input",
            message = SpiceMessage.create("test", "user")
                .copy(state = ExecutionState.WAITING),
            expiresAt = null
        )

        checkpointStore.save(checkpoint)

        val listResult = checkpointStore.listByRun(runId)
        assertTrue(listResult.isSuccess)

        val loadedCheckpoint = (listResult as SpiceResult.Success).value.first()
        assertFalse(loadedCheckpoint.isExpired())
    }

    @Test
    fun `deleteByRun removes all checkpoints for runId`() = runTest {
        val runId = "delete-run"

        // Save multiple checkpoints
        for (i in 1..3) {
            val checkpoint = Checkpoint(
                id = "checkpoint-$i",
                runId = runId,
                graphId = "test-graph",
                currentNodeId = "node-$i",
                message = SpiceMessage.create("test-$i", "user")
                    .copy(state = ExecutionState.WAITING)
            )
            checkpointStore.save(checkpoint)
        }

        // Verify they exist
        val beforeDelete = checkpointStore.listByRun(runId)
        assertEquals(3, (beforeDelete as SpiceResult.Success).value.size)

        // Delete all
        checkpointStore.deleteByRun(runId)

        // Verify they're gone
        val afterDelete = checkpointStore.listByRun(runId)
        assertEquals(0, (afterDelete as SpiceResult.Success).value.size)
    }

    @Test
    fun `userResponseMetadata is applied`() {
        val options = ResumeOptions(
            userResponseMetadata = mapOf(
                "source" to "api",
                "version" to "v2"
            )
        )

        assertEquals("api", options.userResponseMetadata["source"])
        assertEquals("v2", options.userResponseMetadata["version"])
    }
}
