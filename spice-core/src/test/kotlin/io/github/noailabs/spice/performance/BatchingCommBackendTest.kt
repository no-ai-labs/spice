package io.github.noailabs.spice.performance

import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.CommResult
import io.github.noailabs.spice.commhub.BackendHealth
import io.github.noailabs.spice.commhub.CommBackend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comprehensive tests for BatchingCommBackend
 */
class BatchingCommBackendTest {

    @Test
    fun `test size-based batch flush`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batching = BatchingCommBackend(
            delegate = mockBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 3,
                batchWindowMs = 1000, // Large window to prevent time-based flush
                enableMetrics = true
            )
        )

        // Send 3 messages - should trigger immediate flush
        val comm1 = Comm(content = "msg1", from = "user", to = "agent")
        val comm2 = Comm(content = "msg2", from = "user", to = "agent")
        val comm3 = Comm(content = "msg3", from = "user", to = "agent")

        launch { batching.send(comm1) }
        launch { batching.send(comm2) }
        launch { batching.send(comm3) }

        delay(200) // Wait for batch to process

        // Should have sent 1 batch of 3 messages
        assertEquals(1, mockBackend.batchCallCount)
        assertEquals(3, mockBackend.lastBatchSize)

        batching.close()
    }

    @Test
    fun `test window-based batch flush`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batching = BatchingCommBackend(
            delegate = mockBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 100, // Large batch size (won't trigger size-based flush)
                batchWindowMs = 50, // Short window
                maxWaitMs = 10000, // Large maxWait (won't trigger timeout-based flush)
                enableMetrics = true
            )
        )

        // Send 2 messages
        val jobs = listOf(
            async { batching.send(Comm(content = "msg1", from = "user", to = "agent")) },
            async { batching.send(Comm(content = "msg2", from = "user", to = "agent")) }
        )

        // Wait for window to expire and trigger automatic flush (50ms + buffer)
        delay(200)

        // Should have batched the messages via window-based flush
        assertTrue(mockBackend.batchCallCount > 0, "Batch should have been called by window timer, actual: ${mockBackend.batchCallCount}")
        assertEquals(2, mockBackend.lastBatchSize, "Both messages should be batched together")

        jobs.forEach { it.await() }
        batching.close()
    }

    @Test
    fun `test timeout-based flush`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batching = BatchingCommBackend(
            delegate = mockBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 100,
                batchWindowMs = 50, // Shorter window for reliable testing
                maxWaitMs = 100, // Short max wait
                enableMetrics = true
            )
        )

        // Send one message
        val job = async { batching.send(Comm(content = "msg1", from = "user", to = "agent")) }

        // Wait for both window and timeout (whichever triggers first)
        delay(400)

        // Should have flushed due to timeout or window
        assertTrue(mockBackend.batchCallCount > 0, "Batch should have been called at least once")

        job.await()
        batching.close()
    }

    @Test
    fun `test FIFO order preservation`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batching = BatchingCommBackend(
            delegate = mockBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 5,
                enableOrdering = true
            )
        )

        // Send messages in order
        val messages = (1..5).map { Comm(content = "msg$it", from = "user", to = "agent") }
        messages.forEach { launch { batching.send(it) } }

        delay(200)

        // Verify order
        val receivedOrder = mockBackend.lastBatch.map { it.content }
        assertEquals(listOf("msg1", "msg2", "msg3", "msg4", "msg5"), receivedOrder)

        batching.close()
    }

    @Test
    fun `test manual flush`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batching = BatchingCommBackend(
            delegate = mockBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 100,
                batchWindowMs = 10000 // Very large window
            )
        )

        // Send messages
        launch { batching.send(Comm(content = "msg1", from = "user", to = "agent")) }
        launch { batching.send(Comm(content = "msg2", from = "user", to = "agent")) }

        delay(100)

        // Manually flush
        batching.flush()

        // Should have batched
        assertTrue(mockBackend.batchCallCount > 0)

        batching.close()
    }

    @Test
    fun `test batch statistics`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batching = BatchingCommBackend(
            delegate = mockBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 3,
                batchWindowMs = 50,
                enableMetrics = true
            )
        )

        // Send 6 messages (2 batches of 3)
        repeat(6) { i ->
            launch { batching.send(Comm(content = "msg$i", from = "user", to = "agent")) }
        }

        delay(200)

        val stats = batching.getBatchStats()
        assertTrue(stats.totalBatches >= 2)
        assertTrue(stats.totalMessages >= 6)
        assertTrue(stats.avgBatchSize > 0)
        assertEquals(3, stats.maxBatchSize)

        // Test efficiency calculation
        assertTrue(stats.efficiency > 0.0)

        // Test toString
        val statsString = stats.toString()
        assertTrue(statsString.contains("Total Batches:"))
        assertTrue(statsString.contains("Efficiency:"))

        batching.close()
    }

    @Test
    fun `test explicit sendBatch bypasses batching`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batching = BatchingCommBackend(
            delegate = mockBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 100,
                batchWindowMs = 1000
            )
        )

        val messages = listOf(
            Comm(content = "msg1", from = "user", to = "agent"),
            Comm(content = "msg2", from = "user", to = "agent")
        )

        // Explicit batch send - should bypass batching logic
        batching.sendBatch(messages)

        // Should have called delegate directly
        assertEquals(1, mockBackend.batchCallCount)
        assertEquals(2, mockBackend.lastBatchSize)

        batching.close()
    }

    @Test
    fun `test partial failure handling`() = runBlocking {
        val failingBackend = object : MockCommBackend() {
            override suspend fun sendBatch(comms: List<Comm>): List<CommResult> {
                batchCallCount++
                lastBatch = comms
                lastBatchSize = comms.size

                // Return partial failures
                return comms.mapIndexed { index, _ ->
                    if (index % 2 == 0) {
                        CommResult.success("OK")
                    } else {
                        CommResult.failure("Failed")
                    }
                }
            }
        }

        val batching = BatchingCommBackend(
            delegate = failingBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 4
            )
        )

        // Send 4 messages
        val results = mutableListOf<Deferred<CommResult>>()
        repeat(4) { i ->
            val deferred = async {
                batching.send(Comm(content = "msg$i", from = "user", to = "agent"))
            }
            results.add(deferred)
        }

        delay(200)

        val resultList = results.awaitAll()

        // Check that results match expected pattern
        assertEquals(4, resultList.size)
        assertTrue(resultList[0].success)
        assertTrue(!resultList[1].success)
        assertTrue(resultList[2].success)
        assertTrue(!resultList[3].success)

        batching.close()
    }

    @Test
    fun `test batch error handling`() = runBlocking {
        val errorBackend = object : MockCommBackend() {
            override suspend fun sendBatch(comms: List<Comm>): List<CommResult> {
                throw RuntimeException("Backend error")
            }
        }

        val batching = BatchingCommBackend(
            delegate = errorBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 2
            )
        )

        // Send messages
        val result1 = async { batching.send(Comm(content = "msg1", from = "user", to = "agent")) }
        val result2 = async { batching.send(Comm(content = "msg2", from = "user", to = "agent")) }

        delay(200)

        // Both should fail
        assertFalse(result1.await().success)
        assertFalse(result2.await().success)

        batching.close()
    }

    @Test
    fun `test health check includes batching info`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batching = BatchingCommBackend(
            delegate = mockBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 10
            )
        )

        // Send some messages
        repeat(3) { i ->
            launch { batching.send(Comm(content = "msg$i", from = "user", to = "agent")) }
        }

        delay(100)

        val health = batching.health()
        assertTrue(health.healthy)
        assertTrue(health.details.containsKey("batching.enabled"))
        assertTrue(health.details.containsKey("batching.pending"))

        batching.close()
    }

    @Test
    fun `test delegate methods pass through`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batching = BatchingCommBackend(
            delegate = mockBackend,
            batchConfig = BatchingCommBackend.BatchConfig()
        )

        val comm = Comm(content = "test", from = "user", to = "agent1")

        // Test receive
        batching.receive("agent1")
        assertEquals(1, mockBackend.receiveCallCount)

        // Test receiveTimeout
        batching.receiveTimeout("agent1", 1000)
        assertEquals(1, mockBackend.receiveTimeoutCallCount)

        // Test subscribe
        batching.subscribe("agent1")
        assertEquals(1, mockBackend.subscribeCallCount)

        // Test subscribePattern
        batching.subscribePattern("agent*")
        assertEquals(1, mockBackend.subscribePatternCallCount)

        // Test getPendingCount
        batching.getPendingCount("agent1")
        assertEquals(1, mockBackend.getPendingCountCallCount)

        // Test clear
        batching.clear("agent1")
        assertEquals(1, mockBackend.clearCallCount)

        // Test storeHistory
        batching.storeHistory(comm)
        assertEquals(1, mockBackend.storeHistoryCallCount)

        // Test getHistory
        batching.getHistory("agent1", 10, 0)
        assertEquals(1, mockBackend.getHistoryCallCount)

        batching.close()
    }

    @Test
    fun `test batched extension function prevents double wrapping`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batched1 = mockBackend.batched()
        val batched2 = batched1.batched() // Should return same instance

        assertTrue(batched1 === batched2)

        batched1.close()
    }

    @Test
    fun `test close flushes pending messages`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batching = BatchingCommBackend(
            delegate = mockBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 100,
                batchWindowMs = 10000 // Very long window
            )
        )

        // Send messages but don't wait for flush
        launch { batching.send(Comm(content = "msg1", from = "user", to = "agent")) }
        launch { batching.send(Comm(content = "msg2", from = "user", to = "agent")) }

        delay(100)

        // Close should flush pending
        batching.close()

        // Messages should have been sent
        assertTrue(mockBackend.batchCallCount > 0)
        assertTrue(mockBackend.closeCallCount > 0)
    }

    @Test
    fun `test high throughput scenario`() = runBlocking {
        val mockBackend = MockCommBackend()
        val batching = BatchingCommBackend(
            delegate = mockBackend,
            batchConfig = BatchingCommBackend.BatchConfig(
                maxBatchSize = 20,
                batchWindowMs = 50,
                enableMetrics = true
            )
        )

        // Send 100 messages concurrently
        val jobs = (1..100).map { i ->
            async {
                batching.send(Comm(content = "msg$i", from = "user", to = "agent"))
            }
        }

        jobs.awaitAll()
        batching.flush()

        val stats = batching.getBatchStats()
        assertEquals(100, stats.totalMessages)
        assertTrue(stats.totalBatches < 100) // Should have batched
        assertTrue(stats.avgBatchSize > 1.0) // Average batch > 1

        batching.close()
    }

    // Mock CommBackend for testing
    open class MockCommBackend : CommBackend {
        var batchCallCount = 0
        var lastBatch: List<Comm> = emptyList()
        var lastBatchSize = 0

        var receiveCallCount = 0
        var receiveTimeoutCallCount = 0
        var subscribeCallCount = 0
        var subscribePatternCallCount = 0
        var getPendingCountCallCount = 0
        var clearCallCount = 0
        var storeHistoryCallCount = 0
        var getHistoryCallCount = 0
        var closeCallCount = 0

        override suspend fun send(comm: Comm): CommResult {
            return CommResult.success("OK")
        }

        override suspend fun sendBatch(comms: List<Comm>): List<CommResult> {
            batchCallCount++
            lastBatch = comms
            lastBatchSize = comms.size
            return comms.map { CommResult.success("OK") }
        }

        override suspend fun receive(agentId: String): Comm? {
            receiveCallCount++
            return null
        }

        override suspend fun receiveTimeout(agentId: String, timeoutMs: Long): Comm? {
            receiveTimeoutCallCount++
            return null
        }

        override fun subscribe(agentId: String): Flow<Comm> {
            subscribeCallCount++
            return emptyFlow()
        }

        override fun subscribePattern(pattern: String): Flow<Comm> {
            subscribePatternCallCount++
            return emptyFlow()
        }

        override suspend fun getPendingCount(agentId: String): Int {
            getPendingCountCallCount++
            return 0
        }

        override suspend fun clear(agentId: String) {
            clearCallCount++
        }

        override suspend fun storeHistory(comm: Comm) {
            storeHistoryCallCount++
        }

        override suspend fun getHistory(agentId: String?, limit: Int, offset: Int): List<Comm> {
            getHistoryCallCount++
            return emptyList()
        }

        override suspend fun health(): BackendHealth {
            return BackendHealth(
                healthy = true,
                latencyMs = 10,
                pendingMessages = 0,
                details = emptyMap()
            )
        }

        override suspend fun close() {
            closeCallCount++
        }
    }
}

// Helper extension for assertions
fun assertFalse(condition: Boolean, message: String = "Expected false but was true") {
    assertTrue(!condition, message)
}
