package io.github.noailabs.spice.springboot

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Spring Boot configuration model for the modern Spice core runtime.
 */
@ConfigurationProperties(prefix = "spice")
data class SpiceFrameworkProperties(
    val enabled: Boolean = true,
    val graphRunner: GraphRunnerProperties = GraphRunnerProperties(),
    val cache: CacheProperties = CacheProperties(),
    val events: EventProperties = EventProperties(),
    val toolCallEventBus: ToolCallEventBusProperties = ToolCallEventBusProperties(),
    val idempotency: IdempotencyProperties = IdempotencyProperties(),
    val vectorCache: VectorCacheProperties = VectorCacheProperties(),
    val redis: RedisProperties = RedisProperties(),
    val hitl: HitlProperties = HitlProperties()
) {
    data class GraphRunnerProperties(
        val enableIdempotency: Boolean = true,
        val enableEvents: Boolean = true
    )

    data class CacheProperties(
        val toolCallTtl: Duration = Duration.ofHours(1),
        val stepTtl: Duration = Duration.ofHours(6),
        val intentTtl: Duration = Duration.ofDays(1)
    )

    data class EventProperties(
        val enabled: Boolean = true,
        val backend: EventBackend = EventBackend.IN_MEMORY,
        val redisStreams: RedisStreamsProperties = RedisStreamsProperties(),
        val kafka: KafkaProperties = KafkaProperties()
    ) {
        enum class EventBackend { IN_MEMORY, REDIS_STREAMS, KAFKA }

        data class RedisStreamsProperties(
            val streamKey: String = "spice:events",
            val consumerPrefix: String = "spice-events",
            val pollTimeout: Duration = Duration.ofSeconds(1),
            val batchSize: Int = 100
        )

        data class KafkaProperties(
            val topic: String = "spice.events",
            val bootstrapServers: String = "localhost:9092",
            val clientId: String = "spice-eventbus",
            val pollTimeout: Duration = Duration.ofSeconds(1),
            val acks: String = "all",
            val securityProtocol: String? = null,
            val saslMechanism: String? = null,
            val saslJaasConfig: String? = null
        )
    }

    data class ToolCallEventBusProperties(
        val enabled: Boolean = false,
        val backend: ToolCallEventBackend = ToolCallEventBackend.IN_MEMORY,
        val redisStreams: RedisStreamsProperties = RedisStreamsProperties(),
        val kafka: KafkaProperties = KafkaProperties(),
        val history: HistoryProperties = HistoryProperties()
    ) {
        enum class ToolCallEventBackend { IN_MEMORY, REDIS_STREAMS, KAFKA }

        data class RedisStreamsProperties(
            val streamKey: String = "spice:toolcall:events",
            val consumerGroup: String? = null,
            val consumerName: String = "consumer-default",
            val startFrom: String = "$",
            val pollInterval: Duration = Duration.ofSeconds(1)
        )

        data class KafkaProperties(
            val topic: String = "spice.toolcall.events",
            val bootstrapServers: String = "localhost:9092",
            val clientId: String = "spice-toolcall-eventbus",
            val consumerGroup: String = "spice-toolcall-eventbus",
            val autoOffsetReset: String = "latest",
            val pollTimeout: Duration = Duration.ofSeconds(1),
            val acks: String = "all",
            val securityProtocol: String? = null,
            val saslMechanism: String? = null,
            val saslJaasConfig: String? = null
        )

        data class HistoryProperties(
            val enabled: Boolean = true,
            val size: Int = 1000,
            val enableMetrics: Boolean = true
        )
    }

    data class IdempotencyProperties(
        val enabled: Boolean = false,
        val backend: Backend = Backend.IN_MEMORY,
        val maxEntries: Int = 1000,
        val enableStats: Boolean = true,
        val namespace: String = "spice:idempotency"
    ) {
        enum class Backend { IN_MEMORY, REDIS }
    }

    data class VectorCacheProperties(
        val enabled: Boolean = false,
        val backend: Backend = Backend.IN_MEMORY,
        val namespace: String = "spice:vector"
    ) {
        enum class Backend { IN_MEMORY, REDIS }
    }

    data class RedisProperties(
        val enabled: Boolean = false,
        val host: String = "localhost",
        val port: Int = 6379,
        val password: String? = null,
        val ssl: Boolean = false,
        val database: Int = 0
    )

    data class HitlProperties(
        val queue: QueueProperties = QueueProperties(),
        val arbiter: ArbiterProperties = ArbiterProperties()
    ) {
        data class QueueProperties(
            val enabled: Boolean = false,
            val backend: QueueBackend = QueueBackend.IN_MEMORY,
            val namespace: String = "spice:mq",
            val topic: String = "spice.hitl.queue"
        )

        enum class QueueBackend {
            IN_MEMORY,
            REDIS
        }

        data class ArbiterProperties(
            val enabled: Boolean = false,
            val autoStart: Boolean = true,
            val topic: String = "spice.hitl.queue",
            val defaultGraphId: String? = null
        )
    }
}
