package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult

/**
 * Interface for storing and retrieving checkpoints.
 * Enables resumable graph execution.
 */
interface CheckpointStore {
    /**
     * Save a checkpoint.
     * Returns the checkpoint ID.
     */
    suspend fun save(checkpoint: Checkpoint): SpiceResult<String>

    /**
     * Load a checkpoint by ID.
     */
    suspend fun load(checkpointId: String): SpiceResult<Checkpoint>

    /**
     * List all checkpoints for a specific graph run.
     */
    suspend fun listByRun(runId: String): SpiceResult<List<Checkpoint>>

    /**
     * List all checkpoints for a specific graph.
     */
    suspend fun listByGraph(graphId: String): SpiceResult<List<Checkpoint>>

    /**
     * Delete a checkpoint.
     */
    suspend fun delete(checkpointId: String): SpiceResult<Unit>

    /**
     * Delete all checkpoints for a run.
     */
    suspend fun deleteByRun(runId: String): SpiceResult<Unit>
}
