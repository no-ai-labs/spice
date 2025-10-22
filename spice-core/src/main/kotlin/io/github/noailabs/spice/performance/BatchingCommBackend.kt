package io.github.noailabs.spice.performance

import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.CommResult
import io.github.noailabs.spice.commhub.BackendHealth
import io.github.noailabs.spice.commhub.CommBackend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * ⚡ BatchingCommBackend - Performance optimization through message batching
 *
 * Wraps any CommBackend with intelligent batching to:
 * - Reduce network round trips
 * - Improve throughput
 * - Optimize resource usage
 *
 * Features:
 * - Window-based batching
 * - Size-based flush
 * - Timeout-based flush
 * - Order preservation (FIFO)
 * - Partial failure handling
 * - Comprehensive metrics
 *
 * Usage:
 * ```kotlin
 * val backend = InMemoryCommBackend()
 * val batchingBackend = BatchingCommBackend(
 *     delegate = backend,
 *     batchConfig = BatchConfig(maxBatchSize = 20, batchWindowMs = 50)
 * )
 * ```
 */
class BatchingCommBackend(
    private val delegate: CommBackend,
    private val batchConfig: BatchConfig = BatchConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : CommBackend {

    private val pendingMessages = ConcurrentLinkedQueue<BatchedComm>()
    private val batchProcessor: Job
    private val totalBatches = AtomicLong(0)
    private val totalMessages = AtomicLong(0)
    private val currentBatchSize = AtomicInteger(0)

    /**
     * Batch configuration
     */
    data class BatchConfig(
        val maxBatchSize: Int = 10,           // Maximum messages per batch
        val batchWindowMs: Long = 100,        // Collection window (ms)
        val maxWaitMs: Long = 1000,           // Maximum wait time (ms)
        val enableOrdering: Boolean = true,   // Preserve message order
        val enableMetrics: Boolean = true     // Collect metrics
    )

    /**
     * Batched message with completion
     */
    private data class BatchedComm(
        val comm: Comm,
        val deferred: CompletableDeferred<CommResult>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(maxWaitMs: Long): Boolean {
            return System.currentTimeMillis() - timestamp > maxWaitMs
        }
    }

    init {
        // Start batch processor
        batchProcessor = scope.launch {
            while (isActive) {
                try {
                    processBatch()
                    delay(batchConfig.batchWindowMs)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Log error but continue processing
                    println("[BATCHING] Error in batch processor: ${e.message}")
                }
            }
        }
    }

    override suspend fun send(comm: Comm): CommResult {
        val deferred = CompletableDeferred<CommResult>()
        val batchedComm = BatchedComm(comm, deferred)

        // Add to queue
        pendingMessages.offer(batchedComm)
        currentBatchSize.incrementAndGet()
        totalMessages.incrementAndGet()

        // Check if we should flush immediately
        if (shouldFlushImmediately()) {
            flushBatch()
        }

        // Wait for result
        return deferred.await()
    }

    override suspend fun sendBatch(comms: List<Comm>): List<CommResult> {
        // For explicit batch sends, bypass batching and send directly
        return delegate.sendBatch(comms)
    }

    /**
     * Check if batch should flush immediately
     */
    private fun shouldFlushImmediately(): Boolean {
        val size = currentBatchSize.get()

        // Flush if batch is full
        if (size >= batchConfig.maxBatchSize) {
            return true
        }

        // Flush if oldest message is expired
        val oldest = pendingMessages.peek()
        if (oldest != null && oldest.isExpired(batchConfig.maxWaitMs)) {
            return true
        }

        return false
    }

    /**
     * Process pending batch
     *
     * Called periodically by batch processor at batchWindowMs interval.
     * Window-based batching: flush any pending messages when window expires.
     */
    private suspend fun processBatch() {
        if (pendingMessages.isEmpty()) {
            return
        }

        // Window has expired - flush all pending messages
        // This implements true window-based batching
        flushBatch()
    }

    /**
     * Flush current batch
     */
    private suspend fun flushBatch() {
        if (pendingMessages.isEmpty()) {
            return
        }

        // Collect batch
        val batch = mutableListOf<BatchedComm>()
        val batchSize = minOf(currentBatchSize.get(), batchConfig.maxBatchSize)

        repeat(batchSize) {
            pendingMessages.poll()?.let { batch.add(it) }
        }

        if (batch.isEmpty()) {
            return
        }

        currentBatchSize.addAndGet(-batch.size)

        // Send batch
        try {
            val comms = batch.map { it.comm }
            val results = delegate.sendBatch(comms)

            // Complete deferreds
            batch.forEachIndexed { index, batchedComm ->
                val result = results.getOrNull(index) ?: CommResult.failure("Batch send failed")
                batchedComm.deferred.complete(result)
            }

            // Record metrics
            if (batchConfig.enableMetrics) {
                totalBatches.incrementAndGet()
            }
        } catch (e: Exception) {
            // Complete all with error
            batch.forEach { batchedComm ->
                batchedComm.deferred.complete(
                    CommResult.failure("Batch send failed: ${e.message}")
                )
            }
        }
    }

    /**
     * Force flush all pending messages
     */
    suspend fun flush() {
        while (pendingMessages.isNotEmpty()) {
            flushBatch()
        }
    }

    /**
     * Get batching statistics
     */
    fun getBatchStats(): BatchStats {
        val totalBatches = totalBatches.get()
        val totalMessages = totalMessages.get()
        val avgBatchSize = if (totalBatches > 0) {
            totalMessages.toDouble() / totalBatches
        } else {
            0.0
        }

        return BatchStats(
            totalBatches = totalBatches,
            totalMessages = totalMessages,
            avgBatchSize = avgBatchSize,
            currentPending = currentBatchSize.get(),
            maxBatchSize = batchConfig.maxBatchSize
        )
    }

    // Delegate methods (no batching)

    override suspend fun receive(agentId: String): Comm? {
        return delegate.receive(agentId)
    }

    override suspend fun receiveTimeout(agentId: String, timeoutMs: Long): Comm? {
        return delegate.receiveTimeout(agentId, timeoutMs)
    }

    override fun subscribe(agentId: String): Flow<Comm> {
        return delegate.subscribe(agentId)
    }

    override fun subscribePattern(pattern: String): Flow<Comm> {
        return delegate.subscribePattern(pattern)
    }

    override suspend fun getPendingCount(agentId: String): Int {
        return delegate.getPendingCount(agentId)
    }

    override suspend fun clear(agentId: String) {
        delegate.clear(agentId)
    }

    override suspend fun storeHistory(comm: Comm) {
        delegate.storeHistory(comm)
    }

    override suspend fun getHistory(
        agentId: String?,
        limit: Int,
        offset: Int
    ): List<Comm> {
        return delegate.getHistory(agentId, limit, offset)
    }

    override suspend fun health(): BackendHealth {
        val delegateHealth = delegate.health()

        return BackendHealth(
            healthy = delegateHealth.healthy && pendingMessages.size < batchConfig.maxBatchSize * 10,
            latencyMs = delegateHealth.latencyMs,
            pendingMessages = delegateHealth.pendingMessages + pendingMessages.size,
            details = delegateHealth.details + mapOf(
                "batching.enabled" to true,
                "batching.pending" to pendingMessages.size,
                "batching.totalBatches" to totalBatches.get(),
                "batching.totalMessages" to totalMessages.get()
            )
        )
    }

    override suspend fun close() {
        // Flush remaining messages
        flush()

        // Cancel batch processor
        batchProcessor.cancel()

        // Close delegate
        delegate.close()
    }
}

/**
 * Batch statistics
 */
data class BatchStats(
    val totalBatches: Long,
    val totalMessages: Long,
    val avgBatchSize: Double,
    val currentPending: Int,
    val maxBatchSize: Int
) {
    val efficiency: Double
        get() = if (maxBatchSize > 0) {
            avgBatchSize / maxBatchSize
        } else {
            0.0
        }

    override fun toString(): String {
        return """
            Batch Statistics:
            - Total Batches: $totalBatches
            - Total Messages: $totalMessages
            - Avg Batch Size: ${"%.2f".format(avgBatchSize)}
            - Current Pending: $currentPending
            - Max Batch Size: $maxBatchSize
            - Efficiency: ${"%.2f".format(efficiency * 100)}%
        """.trimIndent()
    }
}

/**
 * Extension function to wrap any backend with batching
 */
fun CommBackend.batched(config: BatchingCommBackend.BatchConfig = BatchingCommBackend.BatchConfig()): CommBackend {
    return if (this is BatchingCommBackend) {
        this // Already batched
    } else {
        BatchingCommBackend(this, config)
    }
}

/**
 * DSL function to create a batching backend
 */
fun batchingBackend(
    delegate: CommBackend,
    config: BatchingCommBackend.BatchConfig = BatchingCommBackend.BatchConfig()
): CommBackend = BatchingCommBackend(delegate, config)
