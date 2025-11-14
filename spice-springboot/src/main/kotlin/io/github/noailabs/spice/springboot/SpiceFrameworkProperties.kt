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
    val idempotency: IdempotencyProperties = IdempotencyProperties(),
    val vectorCache: VectorCacheProperties = VectorCacheProperties(),
    val redis: RedisProperties = RedisProperties()
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
        val enabled: Boolean = true
    )

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
}
