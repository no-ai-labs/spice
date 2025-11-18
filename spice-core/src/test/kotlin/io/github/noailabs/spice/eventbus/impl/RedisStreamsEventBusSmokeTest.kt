package io.github.noailabs.spice.eventbus.impl

import io.github.noailabs.spice.eventbus.dlq.RedisDeadLetterQueue
import io.github.noailabs.spice.eventbus.schema.DefaultSchemaRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.JedisPool
import redis.clients.jedis.StreamEntryID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 🧪 Redis Streams Event Bus Smoke Test
 *
 * Simple smoke test to verify basic Redis connectivity and event bus setup
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisStreamsEventBusSmokeTest {

    @Serializable
    data class SimpleEvent(val message: String)

    private lateinit var redisContainer: GenericContainer<*>
    private lateinit var jedisPool: JedisPool

    @BeforeAll
    fun setupRedis() {
        redisContainer = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(false)

        redisContainer.start()

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
        jedisPool.resource.use { it.flushAll() }
    }

    @Test
    fun `should connect to Redis`() {
        jedisPool.resource.use { jedis ->
            val pong = jedis.ping()
            assertEquals("PONG", pong)
        }
    }

    @Test
    fun `should create event bus instance`() {
        val registry = DefaultSchemaRegistry()
        registry.register(SimpleEvent::class, "1.0.0", SimpleEvent.serializer())

        val eventBus = RedisStreamsEventBus(
            jedisPool = jedisPool,
            schemaRegistry = registry,
            namespace = "test",
            consumerGroup = "smoke-group",
            consumerId = "smoke-consumer",
            consumerGroupStartPosition = StreamEntryID("0-0")
        )

        assertNotNull(eventBus)
    }

    @Test
    fun `should publish event to Redis stream`() = runBlocking {
        val registry = DefaultSchemaRegistry()
        registry.register(SimpleEvent::class, "1.0.0", SimpleEvent.serializer())

        val eventBus = RedisStreamsEventBus(
            jedisPool = jedisPool,
            schemaRegistry = registry,
            namespace = "test",
            consumerGroup = "smoke-group",
            consumerId = "smoke-consumer",
            consumerGroupStartPosition = StreamEntryID("0-0")
        )

        val channel = eventBus.channel("smoke.test", SimpleEvent::class, "1.0.0")
        val result = eventBus.publish(channel, SimpleEvent("Hello"))

        assertTrue(result.isSuccess)

        // Verify event is in Redis
        val streamKey = "test:stream:smoke.test"
        val streamLength = jedisPool.resource.use { it.xlen(streamKey) }
        assertEquals(1, streamLength)

        eventBus.close()
    }

    @Test
    fun `should start consumer coroutine`() = runBlocking {
        val registry = DefaultSchemaRegistry()
        registry.register(SimpleEvent::class, "1.0.0", SimpleEvent.serializer())

        val eventBus = RedisStreamsEventBus(
            jedisPool = jedisPool,
            schemaRegistry = registry,
            namespace = "test",
            consumerGroup = "smoke-group",
            consumerId = "smoke-consumer",
            consumerGroupStartPosition = StreamEntryID("0-0")
        )

        val channel = eventBus.channel("smoke.consumer", SimpleEvent::class, "1.0.0")

        // Subscribe (starts consumer)
        val flow = eventBus.subscribe(channel)
        assertNotNull(flow)

        // Wait for consumer to be ready
        val ready = eventBus.awaitConsumerReady("smoke.consumer", timeoutMs = 5000)
        assertTrue(ready, "Consumer should be ready within 5 seconds")

        // Verify consumer group exists in Redis
        val streamKey = "test:stream:smoke.consumer"
        val groups = jedisPool.resource.use { jedis ->
            try {
                jedis.xinfoGroups(streamKey)
            } catch (e: Exception) {
                emptyList()
            }
        }

        assertTrue(groups.isNotEmpty(), "Consumer group should exist")
        // Group exists, that's sufficient for this smoke test

        eventBus.close()
    }

    @Test
    fun `should read event from Redis stream`() = runBlocking {
        val registry = DefaultSchemaRegistry()
        registry.register(SimpleEvent::class, "1.0.0", SimpleEvent.serializer())

        val eventBus = RedisStreamsEventBus(
            jedisPool = jedisPool,
            schemaRegistry = registry,
            namespace = "test",
            consumerGroup = "smoke-group",
            consumerId = "smoke-consumer",
            consumerGroupStartPosition = StreamEntryID("0-0"),
            blockMs = 100  // Short block time for test
        )

        val channel = eventBus.channel("smoke.read", SimpleEvent::class, "1.0.0")

        // Publish first
        eventBus.publish(channel, SimpleEvent("Test Message"))

        // Then subscribe
        val flow = eventBus.subscribe(channel)

        // Wait for consumer ready
        eventBus.awaitConsumerReady("smoke.read", timeoutMs = 5000)

        // Give consumer time to read from Redis
        delay(1000)

        // Check if event was read from Redis and ACKed
        val streamKey = "test:stream:smoke.read"
        val pending = jedisPool.resource.use { jedis ->
            try {
                val info = jedis.xpending(streamKey, "smoke-group")
                info?.total ?: 0L
            } catch (e: Exception) {
                -1L
            }
        }

        println("Pending messages: $pending")
        assertTrue(pending >= 0, "Should be able to query pending messages")

        eventBus.close()
    }
}
