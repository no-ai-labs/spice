package io.github.noailabs.spice.event

import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 🏭 Tool Call Event Bus Factory
 *
 * Factory for creating ToolCallEventBus instances in non-Spring applications.
 * Provides a fluent configuration API for all event bus backends.
 *
 * **Usage:**
 * ```kotlin
 * // In-Memory (dev/testing)
 * val eventBus = ToolCallEventBusFactory.inMemory()
 *
 * // Redis Streams (production)
 * val eventBus = ToolCallEventBusFactory.redisStreams {
 *     host = "localhost"
 *     port = 6379
 *     streamKey = "spice:toolcall:events"
 * }
 *
 * // Kafka (high-throughput production)
 * val eventBus = ToolCallEventBusFactory.kafka {
 *     bootstrapServers = "localhost:9092"
 *     topic = "spice.toolcall.events"
 *     acks = "all"
 * }
 *
 * // From config (map/properties)
 * val eventBus = ToolCallEventBusFactory.fromConfig(configMap)
 * ```
 *
 * @since 1.0.0
 */
object ToolCallEventBusFactory {

    /**
     * Create in-memory event bus
     */
    fun inMemory(config: EventBusConfig = EventBusConfig.DEFAULT): ToolCallEventBus {
        return InMemoryToolCallEventBus(config = config)
    }

    /**
     * Create Redis Streams event bus with configuration
     */
    fun redisStreams(configure: RedisConfig.() -> Unit): ToolCallEventBus {
        val config = RedisConfig().apply(configure)

        val jedisPool = createJedisPool(config)

        val eventBusConfig = EventBusConfig(
            enableHistory = config.enableHistory,
            historySize = config.historySize,
            enableMetrics = config.enableMetrics
        )

        return RedisToolCallEventBus(
            jedisPool = jedisPool,
            streamKey = config.streamKey,
            consumerGroup = config.consumerGroup,
            consumerName = config.consumerName,
            startFrom = config.startFrom,
            config = eventBusConfig,
            pollInterval = config.pollInterval
        )
    }

    /**
     * Create Kafka event bus with configuration
     */
    fun kafka(configure: KafkaConfig.() -> Unit): ToolCallEventBus {
        val config = KafkaConfig().apply(configure)

        val eventBusConfig = EventBusConfig(
            enableHistory = config.enableHistory,
            historySize = config.historySize,
            enableMetrics = config.enableMetrics
        )

        return KafkaToolCallEventBus(
            bootstrapServers = config.bootstrapServers,
            topic = config.topic,
            clientId = config.clientId,
            consumerGroup = config.consumerGroup,
            autoOffsetReset = config.autoOffsetReset,
            config = eventBusConfig,
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
     * - `history.enabled`: true | false
     * - `history.size`: Int (max events to store)
     * - Redis: `redis.host`, `redis.port`, `redis.streamKey`, etc.
     * - Kafka: `kafka.bootstrapServers`, `kafka.topic`, etc.
     */
    fun fromConfig(config: Map<String, Any>): ToolCallEventBus {
        val type = config["type"] as? String ?: "inmemory"

        return when (type.lowercase()) {
            "inmemory", "in-memory", "memory" -> {
                val eventBusConfig = EventBusConfig(
                    enableHistory = config["history.enabled"] as? Boolean ?: true,
                    historySize = config["history.size"] as? Int ?: 1000,
                    enableMetrics = config["history.enableMetrics"] as? Boolean ?: true
                )
                inMemory(eventBusConfig)
            }

            "redis", "redis-streams" -> {
                redisStreams {
                    host = config["redis.host"] as? String ?: "localhost"
                    port = config["redis.port"] as? Int ?: 6379
                    password = config["redis.password"] as? String
                    ssl = config["redis.ssl"] as? Boolean ?: false
                    database = config["redis.database"] as? Int ?: 0
                    streamKey = config["redis.streamKey"] as? String ?: "spice:toolcall:events"
                    consumerGroup = config["redis.consumerGroup"] as? String
                    consumerName = config["redis.consumerName"] as? String ?: "consumer-${java.util.UUID.randomUUID()}"
                    startFrom = config["redis.startFrom"] as? String ?: "$"
                    pollInterval = (config["redis.pollInterval"] as? Long)?.seconds ?: 1.seconds
                    enableHistory = config["history.enabled"] as? Boolean ?: true
                    historySize = config["history.size"] as? Int ?: 1000
                    enableMetrics = config["history.enableMetrics"] as? Boolean ?: true
                }
            }

            "kafka" -> {
                kafka {
                    bootstrapServers = config["kafka.bootstrapServers"] as? String ?: "localhost:9092"
                    topic = config["kafka.topic"] as? String ?: "spice.toolcall.events"
                    clientId = config["kafka.clientId"] as? String ?: "spice-toolcall-eventbus"
                    consumerGroup = config["kafka.consumerGroup"] as? String ?: clientId
                    autoOffsetReset = config["kafka.autoOffsetReset"] as? String ?: "latest"
                    pollTimeout = (config["kafka.pollTimeout"] as? Long)?.seconds ?: 1.seconds
                    acks = config["kafka.acks"] as? String ?: "all"
                    securityProtocol = config["kafka.securityProtocol"] as? String
                    saslMechanism = config["kafka.saslMechanism"] as? String
                    saslJaasConfig = config["kafka.saslJaasConfig"] as? String
                    enableHistory = config["history.enabled"] as? Boolean ?: true
                    historySize = config["history.size"] as? Int ?: 1000
                    enableMetrics = config["history.enableMetrics"] as? Boolean ?: true
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
        var streamKey: String = "spice:toolcall:events",
        var consumerGroup: String? = null,
        var consumerName: String = "consumer-${java.util.UUID.randomUUID()}",
        var startFrom: String = "$",
        var pollInterval: Duration = 1.seconds,
        var enableHistory: Boolean = true,
        var historySize: Int = 1000,
        var enableMetrics: Boolean = true
    )

    /**
     * Kafka configuration
     */
    data class KafkaConfig(
        var bootstrapServers: String = "localhost:9092",
        var topic: String = "spice.toolcall.events",
        var clientId: String = "spice-toolcall-eventbus",
        var consumerGroup: String = "spice-toolcall-eventbus",
        var autoOffsetReset: String = "latest",
        var pollTimeout: Duration = 1.seconds,
        var acks: String = "all",
        var securityProtocol: String? = null,
        var saslMechanism: String? = null,
        var saslJaasConfig: String? = null,
        var enableHistory: Boolean = true,
        var historySize: Int = 1000,
        var enableMetrics: Boolean = true
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
