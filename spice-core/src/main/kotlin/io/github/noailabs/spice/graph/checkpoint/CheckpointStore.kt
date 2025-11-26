package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.error.SpiceResult

/**
 * 🗄️ CheckpointStore for Spice Framework 1.0.0
 *
 * Persistent storage for graph execution checkpoints.
 * Enables HITL workflows, crash recovery, and long-running processes.
 *
 * **Use Cases:**
 * - Human-in-the-loop (HITL) workflows
 * - Multi-day approval processes
 * - Crash recovery
 * - Distributed graph execution
 *
 * **Implementations:**
 * - InMemoryCheckpointStore: Testing, development
 * - RedisCheckpointStore: Production, distributed
 * - DatabaseCheckpointStore: Enterprise, audit requirements
 * - S3CheckpointStore: Long-term storage, archival
 *
 * **Thread Safety:**
 * All implementations must be thread-safe for concurrent access.
 *
 * @since 1.0.0
 */
interface CheckpointStore {
    /**
     * Save checkpoint
     *
     * @param checkpoint Checkpoint to save
     * @return SpiceResult with checkpoint ID
     */
    suspend fun save(checkpoint: Checkpoint): SpiceResult<String>

    /**
     * Load checkpoint by ID
     *
     * @param checkpointId Checkpoint identifier
     * @return SpiceResult with Checkpoint or error if not found
     */
    suspend fun load(checkpointId: String): SpiceResult<Checkpoint>

    /**
     * Delete checkpoint
     *
     * @param checkpointId Checkpoint identifier
     * @return SpiceResult indicating success/failure
     */
    suspend fun delete(checkpointId: String): SpiceResult<Unit>

    /**
     * List all checkpoints for a specific graph
     *
     * @param graphId Graph identifier
     * @return SpiceResult with list of checkpoints
     */
    suspend fun listByGraph(graphId: String): SpiceResult<List<Checkpoint>>

    /**
     * List all checkpoints for a specific run
     *
     * @param runId Run identifier
     * @return SpiceResult with list of checkpoints
     */
    suspend fun listByRun(runId: String): SpiceResult<List<Checkpoint>>

    /**
     * Delete all checkpoints for a specific run
     * Used for cleanup after successful completion
     *
     * @param runId Run identifier
     * @return SpiceResult indicating success/failure
     */
    suspend fun deleteByRun(runId: String): SpiceResult<Unit>

    /**
     * Delete expired checkpoints
     * Cleanup method for maintenance
     *
     * @return SpiceResult with count of deleted checkpoints
     */
    suspend fun deleteExpired(): SpiceResult<Int>

    /**
     * Check if checkpoint exists
     *
     * @param checkpointId Checkpoint identifier
     * @return true if exists
     */
    suspend fun exists(checkpointId: String): Boolean

    // =========================================
    // Spice 2.0: Tool Call ID Based Queries
    // =========================================

    /**
     * Find checkpoint by pending tool call ID
     * Enables causation tracking: "Which checkpoint is waiting for this tool call to complete?"
     *
     * Use case: When user responds, find which checkpoint was waiting for that response
     *
     * @param toolCallId Tool call identifier from pendingToolCall
     * @return SpiceResult with Checkpoint or error if not found
     */
    suspend fun loadByPendingToolCallId(toolCallId: String): SpiceResult<Checkpoint>

    /**
     * Find checkpoint by response tool call ID
     * Enables audit trail: "Which checkpoint received this user response?"
     *
     * Use case: Track which checkpoints have been resolved by user input
     *
     * @param toolCallId Tool call identifier from responseToolCall
     * @return SpiceResult with Checkpoint or error if not found
     */
    suspend fun loadByResponseToolCallId(toolCallId: String): SpiceResult<Checkpoint>

    /**
     * Find all checkpoints involving a specific tool call ID
     * Searches both pendingToolCall and responseToolCall
     *
     * Use case: Full causation chain - see both the request and the response
     *
     * @param toolCallId Tool call identifier
     * @return SpiceResult with list of checkpoints (may be empty)
     */
    suspend fun listByToolCallId(toolCallId: String): SpiceResult<List<Checkpoint>>

    // =========================================
    // Spice 1.0.6+: HITL Idempotency Support
    // =========================================

    /**
     * Check if a HITL response has already been processed
     *
     * Used to prevent duplicate processing of the same HITL response.
     * The idempotency key is: (runId, toolCallId)
     *
     * @param runId Graph run identifier
     * @param toolCallId HITL tool call identifier
     * @return true if already processed, false otherwise
     * @since 1.0.6
     */
    suspend fun isHitlProcessed(runId: String, toolCallId: String): Boolean {
        // Default implementation: check if checkpoint exists with responseToolCallId
        val result = loadByResponseToolCallId(toolCallId)
        return result is SpiceResult.Success
    }

    /**
     * Mark a HITL response as processed
     *
     * Called after successfully processing a HITL response to prevent
     * duplicate processing on retry.
     *
     * @param runId Graph run identifier
     * @param toolCallId HITL tool call identifier
     * @return SpiceResult indicating success/failure
     * @since 1.0.6
     */
    suspend fun markHitlProcessed(runId: String, toolCallId: String): SpiceResult<Unit> {
        // Default implementation: no-op (checkpoint update handles this)
        // Override in implementations that need explicit tracking
        return SpiceResult.success(Unit)
    }

    /**
     * Get the idempotency key for a HITL request
     *
     * Format: {runId}:{toolCallId}
     *
     * @param runId Graph run identifier
     * @param toolCallId HITL tool call identifier
     * @return Idempotency key string
     * @since 1.0.6
     */
    fun getHitlIdempotencyKey(runId: String, toolCallId: String): String =
        "$runId:$toolCallId"
}
