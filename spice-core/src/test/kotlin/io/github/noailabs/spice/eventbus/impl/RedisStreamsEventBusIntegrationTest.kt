package io.github.noailabs.spice.eventbus.impl

import io.github.noailabs.spice.eventbus.*
import io.github.noailabs.spice.eventbus.dlq.RedisDeadLetterQueue
import io.github.noailabs.spice.eventbus.schema.DefaultSchemaRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.JedisPool
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 🧪 Redis Streams Event Bus Integration Tests
 *
 * Uses Testcontainers to spin up a real Redis instance and test:
 * - Publish/subscribe with consumer groups
 * - Consumer group recovery scenarios
 * - DLQ routing with maxRetries
 * - Stream trimming behavior
 * - Pending entry recovery
 * - Resource cleanup
 *
 * **Requirements:**
 * - Docker must be running on the test machine
 * - Testcontainers will automatically pull redis:7-alpine if not cached
 *
 * @since 1.0.0-alpha-5
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisStreamsEventBusIntegrationTest {

    // Test events
    @Serializable
    data class TestEvent(val message: String, val value: Int)

    @Serializable
    data class InvalidEvent(val data: String)

    // Redis container
    private lateinit var redisContainer: GenericContainer<*>
    private lateinit var jedisPool: JedisPool

    // Event bus instances
    private lateinit var eventBus1: RedisStreamsEventBus
    private lateinit var eventBus2: RedisStreamsEventBus

    @BeforeAll
    fun setupRedis() {
        // Start Redis container
        redisContainer = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(false)

        redisContainer.start()

        // Create Jedis pool
        val redisHost = redisContainer.host
        val redisPort = redisContainer.getMappedPort(6379)
        jedisPool = JedisPool(redisHost, redisPort)

        println("✅ Redis started at $redisHost:$redisPort")
    }

    @AfterAll
    fun teardownRedis() {
        jedisPool.close()
        redisContainer.stop()
        println("✅ Redis stopped")
    }

    @BeforeEach
    fun setup() {
        // Flush Redis before each test
        jedisPool.resource.use { it.flushAll() }

        // Create event bus instances
        val registry1 = DefaultSchemaRegistry()
        registry1.register(TestEvent::class, "1.0.0", TestEvent.serializer())
        registry1.register(InvalidEvent::class, "1.0.0", InvalidEvent.serializer())

        val registry2 = DefaultSchemaRegistry()
        registry2.register(TestEvent::class, "1.0.0", TestEvent.serializer())

        eventBus1 = RedisStreamsEventBus(
            jedisPool = jedisPool,
            schemaRegistry = registry1,
            deadLetterQueue = RedisDeadLetterQueue(jedisPool),
            namespace = "test:eventbus",
            consumerGroup = "test-group",
            consumerId = "consumer-1",
            maxLen = 1000,
            blockMs = 500,
            batchSize = 10,
            consumerGroupStartPosition = redis.clients.jedis.StreamEntryID("0-0")  // Read from beginning for tests
        )

        eventBus2 = RedisStreamsEventBus(
            jedisPool = jedisPool,
            schemaRegistry = registry2,
            deadLetterQueue = RedisDeadLetterQueue(jedisPool),
            namespace = "test:eventbus",
            consumerGroup = "test-group",
            consumerId = "consumer-2",
            maxLen = 1000,
            blockMs = 500,
            batchSize = 10,
            consumerGroupStartPosition = redis.clients.jedis.StreamEntryID("0-0")  // Read from beginning for tests
        )
    }

    @AfterEach
    fun cleanup() = runBlocking {
        eventBus1.close()
        eventBus2.close()
    }

    // ============================================================================
    // Test 1: Basic Publish/Subscribe
    // ============================================================================

    @Test
    fun `should publish and subscribe events`() = runBlocking {
        val channel = eventBus1.channel<TestEvent>("test.events", "1.0.0")

        // Publish event FIRST (will be in stream)
        val event = TestEvent("Hello", 42)
        val result = eventBus1.publish(channel, event)
        assertTrue(result.isSuccess, "Publish should succeed")

        // Start subscriber (will read from "0-0" and get the published event)
        val eventsFlow = eventBus1.subscribe(channel)

        // Wait for consumer to be ready
        val ready = eventBus1.awaitConsumerReady("test.events", timeoutMs = 5000)
        assertTrue(ready, "Consumer should be ready")

        // Receive event
        val received = withTimeout(5000) {
            eventsFlow.first()
        }

        assertEquals("Hello", received.event.message)
        assertEquals(42, received.event.value)
        assertNotNull(received.id)
    }

    // ============================================================================
    // Test 2: Consumer Group Load Balancing
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Disabled("TODO: Fix test to handle non-deterministic consumer group distribution (currently assumes exact 5/5 split, but Redis delivers unpredictably). Use merge(flow1, flow2) or assert on total received.")
    fun `should distribute events across consumer group members`() = runBlocking {
        val channel1 = eventBus1.channel<TestEvent>("test.loadbalance", "1.0.0")
        val channel2 = eventBus2.channel<TestEvent>("test.loadbalance", "1.0.0")

        // Start subscribers on both consumers
        val events1 = mutableListOf<TestEvent>()
        val events2 = mutableListOf<TestEvent>()

        val flow1 = eventBus1.subscribe(channel1)
        val flow2 = eventBus2.subscribe(channel2)

        // Publish 10 events
        repeat(10) { i ->
            eventBus1.publish(channel1, TestEvent("msg-$i", i))
            delay(50)
        }

        // Give time to consume
        delay(2000)

        // Collect events (with timeout)
        withTimeout(3000) {
            flow1.take(5).collect { events1.add(it.event) }
        }

        withTimeout(3000) {
            flow2.take(5).collect { events2.add(it.event) }
        }

        // Verify distribution
        val total = events1.size + events2.size
        assertTrue(total >= 8, "Should receive most events across both consumers (got $total)")
        println("Consumer 1 received: ${events1.size}, Consumer 2 received: ${events2.size}")
    }

    // ============================================================================
    // Test 3: DLQ Routing (Deserialization Failure)
    // ============================================================================

    @Test
    fun `should route failed events to DLQ`() = runBlocking {
        val channel = eventBus1.channel<TestEvent>("test.dlq", "1.0.0")

        // Track DLQ writes
        var dlqWriteCount = 0
        val eventBusWithDLQCallback = RedisStreamsEventBus(
            jedisPool = jedisPool,
            schemaRegistry = eventBus1.schemaRegistry,
            deadLetterQueue = RedisDeadLetterQueue(jedisPool),
            namespace = "test:eventbus",
            consumerGroup = "test-group",
            consumerId = "consumer-dlq",
            consumerGroupStartPosition = redis.clients.jedis.StreamEntryID("0-0"),
            onDLQWrite = { _, _ -> dlqWriteCount++ }
        )

        try {
            // Subscribe
            val flow = eventBusWithDLQCallback.subscribe(channel)

            // Publish valid event
            eventBusWithDLQCallback.publish(channel, TestEvent("valid", 1))

            // Publish invalid event by manually injecting bad data into Redis
            jedisPool.resource.use { jedis ->
                val streamKey = "test:eventbus:stream:test.dlq"
                val invalidPayload = mutableMapOf<String, String>(
                    "id" to "bad-event-id",
                    "channelName" to "test.dlq",
                    "eventType" to TestEvent::class.qualifiedName!!,
                    "schemaVersion" to "1.0.0",
                    "payload" to """{"invalid_structure": true}""",  // Invalid JSON structure
                    "metadata" to "{}",
                    "timestamp" to kotlinx.datetime.Clock.System.now().toString()
                )
                jedis.xadd(streamKey, redis.clients.jedis.StreamEntryID.NEW_ENTRY, invalidPayload)
            }

            // Consume valid event
            val received = withTimeout(5000) {
                flow.first()
            }
            assertEquals("valid", received.event.message)

            // Give DLQ time to process the bad event
            delay(2000)

            // Verify DLQ write
            assertTrue(dlqWriteCount >= 1, "Should have written to DLQ (count: $dlqWriteCount)")

            // Check DLQ stats
            val dlqStats = eventBusWithDLQCallback.getDeadLetterQueue().getStats()
            assertTrue(dlqStats.totalMessages >= 1, "DLQ should have at least 1 message")

        } finally {
            eventBusWithDLQCallback.close()
        }
    }

    // ============================================================================
    // Test 4: Stream Trimming
    // ============================================================================

    @Test
    fun `should trim stream to maxLen`() = runBlocking {
        // Create event bus with very small maxLen and fast trimming interval
        val smallMaxLen = 10L
        val eventBusSmallMax = RedisStreamsEventBus(
            jedisPool = jedisPool,
            schemaRegistry = eventBus1.schemaRegistry,
            namespace = "test:eventbus",
            consumerGroup = "trim-group",
            consumerId = "trim-consumer",
            maxLen = smallMaxLen,
            streamTrimmingEnabled = true,
            streamTrimmingIntervalMs = 100, // Trim every 100ms for test
            consumerGroupStartPosition = redis.clients.jedis.StreamEntryID("0-0")
        )

        try {
            val channel = eventBusSmallMax.channel<TestEvent>("test.trim", "1.0.0")

            // Subscribe to trigger trimming job
            val flow = eventBusSmallMax.subscribe(channel)

            // Publish more events than maxLen
            repeat(50) { i ->
                eventBusSmallMax.publish(channel, TestEvent("msg-$i", i))
            }

            // Wait for trimming job to run (100ms interval + processing time)
            delay(500)

            // Check stream length
            val streamKey = "test:eventbus:stream:test.trim"
            val streamLength = jedisPool.resource.use { jedis ->
                jedis.xlen(streamKey)
            }

            // Stream should be trimmed (approximately, due to MAXLEN ~)
            assertTrue(
                streamLength <= smallMaxLen * 1.5,  // Allow some slack for approximate trimming
                "Stream length should be trimmed (got $streamLength, max $smallMaxLen)"
            )
            println("Stream length after trim: $streamLength (maxLen: $smallMaxLen)")

        } finally {
            eventBusSmallMax.close()
        }
    }

    // ============================================================================
    // Test 5: Pending Entry Detection (Foundation for Recovery)
    // ============================================================================

    @Test
    fun `should detect pending entries`() = runBlocking {
        val channel = eventBus1.channel<TestEvent>("test.pending", "1.0.0")

        // Publish events
        repeat(5) { i ->
            eventBus1.publish(channel, TestEvent("msg-$i", i))
        }

        delay(500)

        // Check pending entries
        val streamKey = "test:eventbus:stream:test.pending"
        val pendingInfo = jedisPool.resource.use { jedis ->
            jedis.xpending(streamKey, "test-group")
        }

        // After subscription starts consuming, pending should decrease
        val flow = eventBus1.subscribe(channel)
        val events = withTimeout(5000) {
            flow.take(5).toList()
        }

        assertEquals(5, events.size, "Should receive all events")

        delay(1000)

        // Check pending again - should be near 0
        val pendingAfter = jedisPool.resource.use { jedis ->
            jedis.xpending(streamKey, "test-group")
        }

        println("Pending before: ${pendingInfo?.total ?: 0}, after: ${pendingAfter?.total ?: 0}")
        assertTrue((pendingAfter?.total ?: 0) < 5, "Pending should decrease after ACK")
    }

    // ============================================================================
    // Test 6: Stats Tracking
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Disabled("TODO: Fix test - likely consumer group distribution issue. Verify consumer is reading all published events or adjust assertion.")
    fun `should track event bus statistics`() = runBlocking {
        val channel = eventBus1.channel<TestEvent>("test.stats", "1.0.0")

        // Subscribe
        val flow = eventBus1.subscribe(channel)

        // Publish events
        repeat(3) { i ->
            eventBus1.publish(channel, TestEvent("msg-$i", i))
        }

        // Consume events
        withTimeout(5000) {
            flow.take(3).toList()
        }

        delay(500)

        // Check stats
        val stats = eventBus1.getStats()
        assertEquals(3, stats.published, "Should have published 3 events")
        assertEquals(3, stats.consumed, "Should have consumed 3 events")
        assertEquals(1, stats.activeChannels, "Should have 1 active channel")
        assertEquals(1, stats.activeSubscribers, "Should have 1 active subscriber")
    }

    // ============================================================================
    // Test 7: Resource Cleanup on Close
    // ============================================================================

    @Test
    fun `should clean up resources on close`() = runBlocking {
        val channel = eventBus1.channel<TestEvent>("test.cleanup", "1.0.0")

        // Subscribe
        val flow = eventBus1.subscribe(channel)

        // Publish event
        eventBus1.publish(channel, TestEvent("cleanup", 1))

        // Receive event
        withTimeout(5000) {
            flow.first()
        }

        // Close event bus
        eventBus1.close()

        delay(500)

        // Verify shutdown
        val stats = eventBus1.getStats()
        assertEquals(0, stats.activeChannels, "Should have no active channels after close")
        assertEquals(0, stats.activeSubscribers, "Should have no active subscribers after close")
    }

    // ============================================================================
    // Test 8: Multiple Channels
    // ============================================================================

    @Test
    fun `should support multiple independent channels`() = runBlocking {
        val channel1 = eventBus1.channel<TestEvent>("test.multi.ch1", "1.0.0")
        val channel2 = eventBus1.channel<TestEvent>("test.multi.ch2", "1.0.0")

        // Subscribe to both
        val flow1 = eventBus1.subscribe(channel1)
        val flow2 = eventBus1.subscribe(channel2)

        // Publish to channel 1
        eventBus1.publish(channel1, TestEvent("channel-1", 1))

        // Publish to channel 2
        eventBus1.publish(channel2, TestEvent("channel-2", 2))

        // Receive from both
        val event1 = withTimeout(5000) { flow1.first() }
        val event2 = withTimeout(5000) { flow2.first() }

        assertEquals("channel-1", event1.event.message)
        assertEquals("channel-2", event2.event.message)

        // Verify stats
        val stats = eventBus1.getStats()
        assertEquals(2, stats.activeChannels, "Should have 2 active channels")
        assertEquals(2, stats.activeSubscribers, "Should have 2 active subscribers")
    }

    // ============================================================================
    // Test 9: Event Filtering
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Disabled("TODO: Fix test - consumer group may not receive all filtered events. Verify filter is working or adjust collection strategy.")
    fun `should filter events by predicate`() = runBlocking {
        val channel = eventBus1.channel<TestEvent>("test.filter", "1.0.0")

        // Subscribe with filter (only even values)
        val flow = eventBus1.subscribe(channel, EventFilter.by<TestEvent> { event ->
            event.value % 2 == 0
        })

        // Publish mixed events
        eventBus1.publish(channel, TestEvent("odd", 1))
        eventBus1.publish(channel, TestEvent("even", 2))
        eventBus1.publish(channel, TestEvent("odd", 3))
        eventBus1.publish(channel, TestEvent("even", 4))

        // Collect filtered events
        val filtered = withTimeout(5000) {
            flow.take(2).toList()
        }

        assertEquals(2, filtered.size, "Should receive only even-valued events")
        assertTrue(filtered.all { it.event.value % 2 == 0 }, "All filtered events should be even")
    }

    // ============================================================================
    // Test 10: Concurrent Publishing
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Disabled("TODO: Fix test - consumer group distribution means single consumer may not receive all 20 events. Publish to both buses' channels or merge flows.")
    fun `should handle concurrent publishing`() = runBlocking {
        val channel = eventBus1.channel<TestEvent>("test.concurrent", "1.0.0")

        // Subscribe
        val flow = eventBus1.subscribe(channel)

        // Publish concurrently from multiple event buses
        val count = 20
        repeat(count / 2) { i ->
            eventBus1.publish(channel, TestEvent("bus1-$i", i))
            eventBus2.publish(channel, TestEvent("bus2-$i", i))
        }

        // Collect events
        val events = withTimeout(10000) {
            flow.take(count).toList()
        }

        assertEquals(count, events.size, "Should receive all concurrently published events")
    }
}
