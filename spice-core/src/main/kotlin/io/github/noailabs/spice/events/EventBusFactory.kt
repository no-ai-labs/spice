package io.github.noailabs.spice.events

import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 🏭 Event Bus Factory
 *
 * Factory for creating EventBus instances in non-Spring applications.
 * Provides a fluent configuration API for all event bus backends.
 *
 * **Usage:**
 * ```kotlin
 * // In-Memory (dev/testing)
 * val eventBus = EventBusFactory.inMemory()
 *
 * // Redis Streams (production)
 * val eventBus = EventBusFactory.redisStreams {
 *     host = "localhost"
 *     port = 6379
 *     streamKey = "spice:events"
 * }
 *
 * // Kafka (high-throughput production)
 * val eventBus = EventBusFactory.kafka {
 *     bootstrapServers = "localhost:9092"
 *     topic = "spice.events"
 *     acks = "all"
 * }
 *
 * // From config (map/properties)
 * val eventBus = EventBusFactory.fromConfig(configMap)
 * ```
 *
 * @since 1.0.0
 */
object EventBusFactory {

    /**
     * Create in-memory event bus
     */
    fun inMemory(): EventBus = InMemoryEventBus()

    /**
     * Create Redis Streams event bus with configuration
     */
    fun redisStreams(configure: RedisConfig.() -> Unit): EventBus {
        val config = RedisConfig().apply(configure)

        val jedisPool = createJedisPool(config)

        return RedisStreamsEventBus(
            jedisPool = jedisPool,
            streamKey = config.streamKey,
            consumerPrefix = config.consumerPrefix,
            batchSize = config.batchSize,
            blockTimeout = config.pollTimeout
        )
    }

    /**
     * Create Kafka event bus with configuration
     */
    fun kafka(configure: KafkaConfig.() -> Unit): EventBus {
        val config = KafkaConfig().apply(configure)

        return KafkaEventBus(
            bootstrapServers = config.bootstrapServers,
            topic = config.topic,
            clientId = config.clientId,
            pollTimeout = config.pollTimeout,
            acks = config.acks,
            securityProtocol = config.securityProtocol,
            saslMechanism = config.saslMechanism,
            saslJaasConfig = config.saslJaasConfig
        )
    }

    /**
     * Create event bus from configuration map
     *
     * Expected keys:
     * - `type`: "inmemory" | "redis" | "kafka"
     * - Redis: `redis.host`, `redis.port`, `redis.streamKey`, etc.
     * - Kafka: `kafka.bootstrapServers`, `kafka.topic`, etc.
     */
    fun fromConfig(config: Map<String, Any>): EventBus {
        val type = config["type"] as? String ?: "inmemory"

        return when (type.lowercase()) {
            "inmemory", "in-memory", "memory" -> inMemory()

            "redis", "redis-streams" -> {
                redisStreams {
                    host = config["redis.host"] as? String ?: "localhost"
                    port = config["redis.port"] as? Int ?: 6379
                    password = config["redis.password"] as? String
                    ssl = config["redis.ssl"] as? Boolean ?: false
                    database = config["redis.database"] as? Int ?: 0
                    streamKey = config["redis.streamKey"] as? String ?: "spice:events"
                    consumerPrefix = config["redis.consumerPrefix"] as? String ?: "spice-events"
                    batchSize = config["redis.batchSize"] as? Int ?: 100
                    pollTimeout = (config["redis.pollTimeout"] as? Long)?.seconds ?: 1.seconds
                }
            }

            "kafka" -> {
                kafka {
                    bootstrapServers = config["kafka.bootstrapServers"] as? String ?: "localhost:9092"
                    topic = config["kafka.topic"] as? String ?: "spice.events"
                    clientId = config["kafka.clientId"] as? String ?: "spice-eventbus"
                    pollTimeout = (config["kafka.pollTimeout"] as? Long)?.seconds ?: 1.seconds
                    acks = config["kafka.acks"] as? String ?: "all"
                    securityProtocol = config["kafka.securityProtocol"] as? String
                    saslMechanism = config["kafka.saslMechanism"] as? String
                    saslJaasConfig = config["kafka.saslJaasConfig"] as? String
                }
            }

            else -> throw IllegalArgumentException("Unknown event bus type: $type. Use 'inmemory', 'redis', or 'kafka'")
        }
    }

    /**
     * Redis configuration
     */
    data class RedisConfig(
        var host: String = "localhost",
        var port: Int = 6379,
        var password: String? = null,
        var ssl: Boolean = false,
        var database: Int = 0,
        var streamKey: String = "spice:events",
        var consumerPrefix: String = "spice-events",
        var batchSize: Int = 100,
        var pollTimeout: Duration = 1.seconds
    )

    /**
     * Kafka configuration
     */
    data class KafkaConfig(
        var bootstrapServers: String = "localhost:9092",
        var topic: String = "spice.events",
        var clientId: String = "spice-eventbus",
        var pollTimeout: Duration = 1.seconds,
        var acks: String = "all",
        var securityProtocol: String? = null,
        var saslMechanism: String? = null,
        var saslJaasConfig: String? = null
    )

    /**
     * Create Jedis pool from Redis config
     */
    private fun createJedisPool(config: RedisConfig): JedisPool {
        val clientConfigBuilder = DefaultJedisClientConfig.builder()
            .database(config.database)
            .ssl(config.ssl)

        config.password?.takeIf { it.isNotBlank() }?.let { clientConfigBuilder.password(it) }

        val clientConfig = clientConfigBuilder.build()
        return JedisPool(HostAndPort(config.host, config.port), clientConfig)
    }
}
