package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of CheckpointStore with JSON serialization.
 * Suitable for testing and single-process applications.
 *
 * ⚠️ Important: This implementation now uses JSON serialization to simulate
 * Redis/DB behavior and test nested structure preservation. This ensures that
 * Checkpoint state/metadata with nested Maps/Lists are properly preserved
 * through serialization cycles.
 *
 * Note: Checkpoints are lost when the process terminates.
 * For production use with persistence, implement a custom CheckpointStore
 * backed by a database or distributed cache, and use CheckpointSerializer
 * to ensure type-safe serialization.
 */
class InMemoryCheckpointStore : CheckpointStore {
    // Store as JSON strings to simulate Redis/DB serialization behavior
    private val checkpoints = ConcurrentHashMap<String, String>()
    private val runIndex = ConcurrentHashMap<String, MutableSet<String>>() // runId -> checkpoint IDs
    private val graphIndex = ConcurrentHashMap<String, MutableSet<String>>() // graphId -> checkpoint IDs

    override suspend fun save(checkpoint: Checkpoint): SpiceResult<String> {
        return SpiceResult.catching {
            // Serialize to JSON to simulate Redis/DB behavior
            val json = CheckpointSerializer.serialize(checkpoint)
            checkpoints[checkpoint.id] = json

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
        val json = checkpoints[checkpointId]
        return if (json != null) {
            SpiceResult.catching {
                // Deserialize from JSON to test round-trip preservation
                CheckpointSerializer.deserialize(json)
            }.mapError { e ->
                SpiceError.CheckpointError(
                    message = "Failed to deserialize checkpoint: ${e.message}",
                    checkpointId = checkpointId,
                    cause = e as? Throwable
                )
            }
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
            ids.mapNotNull { id ->
                checkpoints[id]?.let { json ->
                    CheckpointSerializer.deserialize(json)
                }
            }.sortedByDescending { it.timestamp }
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
            ids.mapNotNull { id ->
                checkpoints[id]?.let { json ->
                    CheckpointSerializer.deserialize(json)
                }
            }.sortedByDescending { it.timestamp }
        }.mapError { e ->
            SpiceError.CheckpointError(
                message = "Failed to list checkpoints: ${e.message}",
                cause = e as? Throwable
            )
        }
    }

    override suspend fun delete(checkpointId: String): SpiceResult<Unit> {
        return SpiceResult.catching {
            val json = checkpoints.remove(checkpointId)
            if (json != null) {
                val checkpoint = CheckpointSerializer.deserialize(json)
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
                val json = checkpoints.remove(id)
                json?.let {
                    val checkpoint = CheckpointSerializer.deserialize(it)
                    graphIndex[checkpoint.graphId]?.remove(id)
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
