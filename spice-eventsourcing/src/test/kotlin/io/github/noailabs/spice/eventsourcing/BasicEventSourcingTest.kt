package io.github.noailabs.spice.eventsourcing

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

/**
 * Basic unit tests that verify core functionality without external dependencies
 */
class BasicEventSourcingTest {
    
    @Test
    fun `test Event interface basic implementation`() {
        val event = BasicEvent(
            eventId = "event-1",
            eventType = "TestEvent",
            streamId = "stream-1",
            version = 1,
            timestamp = Instant.now(),
            metadata = EventMetadata(
                userId = "user-1",
                correlationId = "corr-1"
            )
        )
        
        assertEquals("event-1", event.eventId)
        assertEquals("TestEvent", event.eventType)
        assertEquals("stream-1", event.streamId)
        assertEquals(1, event.version)
        assertNotNull(event.timestamp)
        assertNotNull(event.toProto())
    }
    
    @Test
    fun `test DomainEvent hierarchy`() {
        val event = EntityCreatedEvent(
            aggregateId = "agg-1",
            aggregateType = "TestAggregate",
            createdBy = "user-1"
        )
        
        assertEquals("agg-1", event.aggregateId)
        assertEquals("TestAggregate", event.aggregateType)
        assertEquals("user-1", event.createdBy)
        assertEquals("TestAggregateCreated", event.eventType)
        assertTrue(event is DomainEvent)
        assertTrue(event is Event)
    }
    
    @Test
    fun `test EventMetadata creation`() {
        val metadata = EventMetadata(
            userId = "user-1",
            correlationId = "corr-1",
            causationId = "cause-1",
            tenantId = "tenant-1",
            sourceSystem = "test"
        )
        
        assertEquals("user-1", metadata.userId)
        assertEquals("corr-1", metadata.correlationId)
        assertEquals("cause-1", metadata.causationId)
        assertEquals("tenant-1", metadata.tenantId)
        assertEquals("test", metadata.sourceSystem)
    }
    
    @Test
    fun `test Snapshot data class`() {
        val data = "test-data".toByteArray()
        val snapshot = Snapshot(
            streamId = "stream-1",
            version = 10,
            timestamp = Instant.now(),
            data = data,
            metadata = mapOf("key" to "value")
        )
        
        assertEquals("stream-1", snapshot.streamId)
        assertEquals(10, snapshot.version)
        assertArrayEquals(data, snapshot.data)
        assertEquals("value", snapshot.metadata["key"])
    }
    
    @Test
    fun `test EventStoreResult Success`() {
        val result: EventStoreResult<String> = EventStoreResult.Success("test")
        
        assertTrue(result is EventStoreResult.Success)
        assertEquals("test", result.getOrThrow())
        assertEquals("test", result.getOrNull())
        
        val mapped = result.map { it.uppercase() }
        assertEquals("TEST", mapped.getOrThrow())
    }
    
    @Test
    fun `test EventStoreResult Failure`() {
        val exception = StreamNotFoundException("stream-1")
        val result: EventStoreResult<String> = EventStoreResult.Failure(exception)
        
        assertTrue(result is EventStoreResult.Failure)
        assertNull(result.getOrNull())
        assertThrows(StreamNotFoundException::class.java) {
            result.getOrThrow()
        }
        
        val mapped = result.map { it.uppercase() }
        assertTrue(mapped is EventStoreResult.Failure)
    }
    
    @Test
    fun `test SagaContext operations`() {
        val context = SagaContext()
        
        context.set("key1", "value1")
        context.set("key2", 42)
        
        assertEquals("value1", context.get<String>("key1"))
        assertEquals(42, context.get<Int>("key2"))
        assertNull(context.get<String>("non-existent"))
        
        context.remove("key1")
        assertNull(context.get<String>("key1"))
        
        val all = context.getAll()
        assertEquals(1, all.size)
        assertEquals(42, all["key2"])
    }
    
    @Test
    fun `test ExponentialBackoffRetryPolicy`() {
        val policy = ExponentialBackoffRetryPolicy(
            maxAttempts = 3,
            baseDelayMillis = 100,
            maxDelayMillis = 1000,
            multiplier = 2.0
        )
        
        assertEquals(3, policy.maxAttempts)
        
        // Test delay calculation
        assertEquals(100, policy.getRetryDelay(1))
        assertEquals(200, policy.getRetryDelay(2))
        assertEquals(400, policy.getRetryDelay(3))
        
        // Test max delay cap
        assertEquals(1000, policy.getRetryDelay(10))
        
        // Test retry decision
        val timeoutException = EventStoreTimeoutException("test", 1000)
        assertTrue(policy.shouldRetry(timeoutException, 1))
        assertTrue(policy.shouldRetry(timeoutException, 2))
        assertFalse(policy.shouldRetry(timeoutException, 3)) // Max attempts reached
    }
}

// Basic event implementation for testing
data class BasicEvent(
    override val eventId: String,
    override val eventType: String,
    override val streamId: String,
    override val version: Long,
    override val timestamp: Instant,
    override val metadata: EventMetadata
) : Event {
    override fun toProto(): ByteArray = "$eventType:$eventId".toByteArray()
}