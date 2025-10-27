package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of CheckpointStore.
 * Suitable for testing and single-process applications.
 *
 * Note: Checkpoints are lost when the process terminates.
 * For production use with persistence, implement a custom CheckpointStore
 * backed by a database or distributed cache.
 */
class InMemoryCheckpointStore : CheckpointStore {
    private val checkpoints = ConcurrentHashMap<String, Checkpoint>()
    private val runIndex = ConcurrentHashMap<String, MutableSet<String>>() // runId -> checkpoint IDs
    private val graphIndex = ConcurrentHashMap<String, MutableSet<String>>() // graphId -> checkpoint IDs

    override suspend fun save(checkpoint: Checkpoint): SpiceResult<String> {
        return SpiceResult.catching {
            checkpoints[checkpoint.id] = checkpoint

            // Update indices
            runIndex.computeIfAbsent(checkpoint.runId) { ConcurrentHashMap.newKeySet() }
                .add(checkpoint.id)

            graphIndex.computeIfAbsent(checkpoint.graphId) { ConcurrentHashMap.newKeySet() }
                .add(checkpoint.id)

            checkpoint.id
        }.mapError { e ->
            SpiceError.CheckpointError(
                message = "Failed to save checkpoint: ${e.message}",
                cause = e as? Throwable
            )
        }
    }

    override suspend fun load(checkpointId: String): SpiceResult<Checkpoint> {
        val checkpoint = checkpoints[checkpointId]
        return if (checkpoint != null) {
            SpiceResult.success(checkpoint)
        } else {
            SpiceResult.failure(
                SpiceError.CheckpointError(
                    message = "Checkpoint not found: $checkpointId",
                    checkpointId = checkpointId
                )
            )
        }
    }

    override suspend fun listByRun(runId: String): SpiceResult<List<Checkpoint>> {
        return SpiceResult.catching {
            val ids = runIndex[runId] ?: emptySet()
            ids.mapNotNull { checkpoints[it] }
                .sortedByDescending { it.timestamp }
        }.mapError { e ->
            SpiceError.CheckpointError(
                message = "Failed to list checkpoints: ${e.message}",
                cause = e as? Throwable
            )
        }
    }

    override suspend fun listByGraph(graphId: String): SpiceResult<List<Checkpoint>> {
        return SpiceResult.catching {
            val ids = graphIndex[graphId] ?: emptySet()
            ids.mapNotNull { checkpoints[it] }
                .sortedByDescending { it.timestamp }
        }.mapError { e ->
            SpiceError.CheckpointError(
                message = "Failed to list checkpoints: ${e.message}",
                cause = e as? Throwable
            )
        }
    }

    override suspend fun delete(checkpointId: String): SpiceResult<Unit> {
        return SpiceResult.catching {
            val checkpoint = checkpoints.remove(checkpointId)
            if (checkpoint != null) {
                // Clean up indices
                runIndex[checkpoint.runId]?.remove(checkpointId)
                graphIndex[checkpoint.graphId]?.remove(checkpointId)
            }
        }.mapError { e ->
            SpiceError.CheckpointError(
                message = "Failed to delete checkpoint: ${e.message}",
                cause = e as? Throwable
            )
        }
    }

    override suspend fun deleteByRun(runId: String): SpiceResult<Unit> {
        return SpiceResult.catching {
            val ids = runIndex.remove(runId) ?: emptySet()
            ids.forEach { id ->
                val checkpoint = checkpoints.remove(id)
                checkpoint?.let {
                    graphIndex[it.graphId]?.remove(id)
                }
            }
        }.mapError { e ->
            SpiceError.CheckpointError(
                message = "Failed to delete checkpoints: ${e.message}",
                cause = e as? Throwable
            )
        }
    }

    /**
     * Clear all checkpoints (useful for testing).
     */
    fun clear() {
        checkpoints.clear()
        runIndex.clear()
        graphIndex.clear()
    }

    /**
     * Get total number of checkpoints stored.
     */
    fun size(): Int = checkpoints.size
}
