package io.github.noailabs.spice.springboot.statemachine.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Spring Boot configuration properties for the Spice state machine extension.
 */
@ConfigurationProperties("spice.statemachine")
data class StateMachineProperties(
    val enabled: Boolean = true,
    val persistence: Persistence = Persistence(),
    val retry: Retry = Retry(),
    val events: Events = Events(),
    val visualization: Visualization = Visualization(),
    val metrics: Metrics = Metrics()
) {
    data class Persistence(
        val type: PersistenceType = PersistenceType.IN_MEMORY,
        val redis: Redis = Redis()
    ) {
        data class Redis(
            val host: String = "localhost",
            val port: Int = 6379,
            val password: String? = null,
            val database: Int = 0,
            val ssl: Boolean = false,
            val keyPrefix: String = "spice:sm:",
            val ttlSeconds: Long = 86400
        )
    }

    enum class PersistenceType {
        IN_MEMORY,
        REDIS
    }

    data class Retry(
        val enabled: Boolean = true,
        val maxAttempts: Int = 3,
        val backoffStrategy: BackoffStrategy = BackoffStrategy.EXPONENTIAL,
        val initialBackoffMs: Long = 1000,
        val maxBackoffMs: Long = 10000
    ) {
        enum class BackoffStrategy {
            EXPONENTIAL,
            FIXED,
            LINEAR
        }
    }

    data class Events(
        val enabled: Boolean = true,
        val publishToKafka: Boolean = false,
        val kafkaTopic: String = "spice.workflow.events"
    )

    data class Visualization(
        val enabled: Boolean = true,
        val format: VisualizationFormat = VisualizationFormat.MERMAID
    ) {
        enum class VisualizationFormat {
            MERMAID,
            GRAPHVIZ
        }
    }

    data class Metrics(
        val enabled: Boolean = true
    )
}
