package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * 💾 In-Memory Checkpoint Store
 *
 * Fast, ephemeral checkpoint storage for testing and development.
 * Uses JSON serialization to simulate production persistence behavior.
 *
 * **Characteristics:**
 * - Performance: O(1) operations
 * - Persistence: None (data lost on restart)
 * - Scalability: Single-instance only
 * - Thread Safety: Yes (Mutex)
 *
 * **Use Cases:**
 * - Unit tests and integration tests
 * - Development environment
 * - Demo and prototype
 *
 * **NOT for production** - Use RedisCheckpointStore or DatabaseCheckpointStore instead
 *
 * @since 1.0.0
 */
class InMemoryCheckpointStore : CheckpointStore {
    private val checkpoints = mutableMapOf<String, String>()  // ID -> JSON
    private val mutex = Mutex()

    override suspend fun save(checkpoint: Checkpoint): SpiceResult<String> = mutex.withLock {
        SpiceResult.catching {
            val json = CheckpointSerializer.serialize(checkpoint)
            checkpoints[checkpoint.id] = json
            checkpoint.id
        }
    }

    override suspend fun load(checkpointId: String): SpiceResult<Checkpoint> = mutex.withLock {
        val json = checkpoints[checkpointId]
            ?: return SpiceResult.failure(
                SpiceError.CheckpointError("Checkpoint not found: $checkpointId")
            )

        SpiceResult.catching {
            CheckpointSerializer.deserialize(json)
        }
    }

    override suspend fun delete(checkpointId: String): SpiceResult<Unit> = mutex.withLock {
        SpiceResult.catching {
            checkpoints.remove(checkpointId)
            Unit
        }
    }

    override suspend fun listByGraph(graphId: String): SpiceResult<List<Checkpoint>> = mutex.withLock {
        SpiceResult.catching {
            checkpoints.values
                .map { CheckpointSerializer.deserialize(it) }
                .filter { it.graphId == graphId }
        }
    }

    override suspend fun listByRun(runId: String): SpiceResult<List<Checkpoint>> = mutex.withLock {
        SpiceResult.catching {
            checkpoints.values
                .map { CheckpointSerializer.deserialize(it) }
                .filter { it.runId == runId }
        }
    }

    override suspend fun deleteByRun(runId: String): SpiceResult<Unit> = mutex.withLock {
        SpiceResult.catching {
            val toDelete = checkpoints.values
                .map { CheckpointSerializer.deserialize(it) }
                .filter { it.runId == runId }
                .map { it.id }

            toDelete.forEach { checkpoints.remove(it) }
            Unit
        }
    }

    override suspend fun deleteExpired(): SpiceResult<Int> = mutex.withLock {
        SpiceResult.catching {
            val now = Clock.System.now()
            val toDelete = checkpoints.values
                .map { CheckpointSerializer.deserialize(it) }
                .filter { it.expiresAt?.let { exp -> now > exp } ?: false }
                .map { it.id }

            toDelete.forEach { checkpoints.remove(it) }
            toDelete.size
        }
    }

    override suspend fun exists(checkpointId: String): Boolean = mutex.withLock {
        checkpoints.containsKey(checkpointId)
    }

    /**
     * Clear all checkpoints (testing only)
     */
    suspend fun clear() = mutex.withLock {
        checkpoints.clear()
    }

    /**
     * Get current checkpoint count (testing/monitoring)
     */
    suspend fun size(): Int = mutex.withLock {
        checkpoints.size
    }
}
