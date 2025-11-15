package io.github.noailabs.spice.springboot

import io.github.noailabs.spice.arbiter.Arbiter
import io.github.noailabs.spice.arbiter.InMemoryMessageQueue
import io.github.noailabs.spice.arbiter.MessageQueue
import io.github.noailabs.spice.arbiter.RedisMessageQueue
import io.github.noailabs.spice.cache.CachePolicy
import io.github.noailabs.spice.cache.InMemoryVectorCache
import io.github.noailabs.spice.cache.RedisVectorCache
import io.github.noailabs.spice.cache.VectorCache
import io.github.noailabs.spice.events.EventBus
import io.github.noailabs.spice.events.InMemoryEventBus
import io.github.noailabs.spice.events.KafkaEventBus
import io.github.noailabs.spice.events.RedisStreamsEventBus
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.GraphRunner
import io.github.noailabs.spice.idempotency.IdempotencyStore
import io.github.noailabs.spice.idempotency.InMemoryIdempotencyStore
import io.github.noailabs.spice.idempotency.RedisIdempotencyStore
import io.github.noailabs.spice.state.ExecutionStateMachine
import io.github.noailabs.spice.springboot.GraphProvider
import io.github.noailabs.spice.validation.DeadLetterHandler
import io.github.noailabs.spice.validation.SchemaValidationPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import kotlin.time.toKotlinDuration

/**
 * Auto-configuration aligning Spice Spring Boot starter with the 1.0 core runtime.
 */
@AutoConfiguration
@EnableConfigurationProperties(SpiceFrameworkProperties::class)
@ConditionalOnProperty(prefix = "spice", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SpiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun schemaValidationPipeline(): SchemaValidationPipeline = SchemaValidationPipeline()

    @Bean
    @ConditionalOnMissingBean
    fun executionStateMachine(): ExecutionStateMachine = ExecutionStateMachine()

    @Bean
    @ConditionalOnMissingBean
    fun cachePolicy(properties: SpiceFrameworkProperties): CachePolicy {
        val cache = properties.cache
        return CachePolicy(
            toolCallTtl = cache.toolCallTtl.toKotlinDuration(),
            stepTtl = cache.stepTtl.toKotlinDuration(),
            intentTtl = cache.intentTtl.toKotlinDuration()
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun graphRunner(
        properties: SpiceFrameworkProperties,
        schemaValidationPipeline: SchemaValidationPipeline,
        executionStateMachine: ExecutionStateMachine,
        cachePolicy: CachePolicy,
        vectorCacheProvider: ObjectProvider<VectorCache>
    ): GraphRunner {
        val vectorCache = vectorCacheProvider.getIfAvailable()
        val graphRunnerProps = properties.graphRunner
        return DefaultGraphRunner(
            enableIdempotency = graphRunnerProps.enableIdempotency,
            enableEvents = graphRunnerProps.enableEvents,
            validationPipeline = schemaValidationPipeline,
            stateMachine = executionStateMachine,
            cachePolicy = cachePolicy,
            vectorCache = vectorCache
        )
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "spice.redis", name = ["enabled"], havingValue = "true")
    fun spiceRedisPool(properties: SpiceFrameworkProperties): JedisPool {
        val redis = properties.redis
        val clientConfigBuilder = DefaultJedisClientConfig.builder()
            .database(redis.database)
            .ssl(redis.ssl)
        redis.password?.takeIf { it.isNotBlank() }?.let { clientConfigBuilder.password(it) }
        val clientConfig = clientConfigBuilder.build()
        return JedisPool(HostAndPort(redis.host, redis.port), clientConfig)
    }

    @Bean
    @ConditionalOnProperty(prefix = "spice.vector-cache", name = ["enabled"], havingValue = "true")
    fun vectorCache(
        properties: SpiceFrameworkProperties,
        redisPoolProvider: ObjectProvider<JedisPool>
    ): VectorCache {
        val config = properties.vectorCache
        return when (config.backend) {
            SpiceFrameworkProperties.VectorCacheProperties.Backend.IN_MEMORY -> InMemoryVectorCache()
            SpiceFrameworkProperties.VectorCacheProperties.Backend.REDIS -> {
                val redisPool = redisPoolProvider.getIfAvailable()
                    ?: throw IllegalStateException(
                        "Vector cache backend set to REDIS but spice.redis.enabled=false"
                    )
                RedisVectorCache(redisPool, namespace = config.namespace)
            }
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "spice.idempotency", name = ["enabled"], havingValue = "true")
    @ConditionalOnMissingBean
    fun idempotencyStore(
        properties: SpiceFrameworkProperties,
        redisPoolProvider: ObjectProvider<JedisPool>
    ): IdempotencyStore {
        val config = properties.idempotency
        return when (config.backend) {
            SpiceFrameworkProperties.IdempotencyProperties.Backend.IN_MEMORY ->
                InMemoryIdempotencyStore(
                    maxEntries = config.maxEntries,
                    enableStats = config.enableStats
                )
            SpiceFrameworkProperties.IdempotencyProperties.Backend.REDIS -> {
                val redisPool = redisPoolProvider.getIfAvailable()
                    ?: throw IllegalStateException(
                        "Idempotency backend set to REDIS but spice.redis.enabled=false"
                    )
                RedisIdempotencyStore(
                    jedisPool = redisPool,
                    namespace = config.namespace
                )
            }
        }
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "spice.events", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    fun eventBus(
        properties: SpiceFrameworkProperties,
        redisPoolProvider: ObjectProvider<JedisPool>
    ): EventBus {
        val events = properties.events
        return when (events.backend) {
            SpiceFrameworkProperties.EventProperties.EventBackend.IN_MEMORY -> InMemoryEventBus()
            SpiceFrameworkProperties.EventProperties.EventBackend.REDIS_STREAMS -> {
                val redisPool = redisPoolProvider.getIfAvailable()
                    ?: throw IllegalStateException(
                        "EventBus backend set to REDIS_STREAMS but spice.redis.enabled=false"
                    )
                val redisConfig = events.redisStreams
                RedisStreamsEventBus(
                    jedisPool = redisPool,
                    streamKey = redisConfig.streamKey,
                    consumerPrefix = redisConfig.consumerPrefix,
                    batchSize = redisConfig.batchSize,
                    blockTimeout = redisConfig.pollTimeout.toKotlinDuration()
                )
            }
            SpiceFrameworkProperties.EventProperties.EventBackend.KAFKA -> {
                val kafkaConfig = events.kafka
                KafkaEventBus(
                    bootstrapServers = kafkaConfig.bootstrapServers,
                    topic = kafkaConfig.topic,
                    clientId = kafkaConfig.clientId,
                    pollTimeout = kafkaConfig.pollTimeout.toKotlinDuration(),
                    acks = kafkaConfig.acks,
                    securityProtocol = kafkaConfig.securityProtocol,
                    saslMechanism = kafkaConfig.saslMechanism,
                    saslJaasConfig = kafkaConfig.saslJaasConfig
                )
            }
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "spice.hitl.queue", name = ["enabled"], havingValue = "true")
    @ConditionalOnMissingBean
    fun hitlMessageQueue(
        properties: SpiceFrameworkProperties,
        redisPoolProvider: ObjectProvider<JedisPool>
    ): MessageQueue {
        val queue = properties.hitl.queue
        return when (queue.backend) {
            SpiceFrameworkProperties.HitlProperties.QueueBackend.IN_MEMORY -> InMemoryMessageQueue()
            SpiceFrameworkProperties.HitlProperties.QueueBackend.REDIS -> {
                val redisPool = redisPoolProvider.getIfAvailable()
                    ?: throw IllegalStateException(
                        "HITL queue backend set to REDIS but spice.redis.enabled=false"
                    )
                RedisMessageQueue(
                    jedisPool = redisPool,
                    namespace = queue.namespace
                )
            }
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "spice.hitl.arbiter", name = ["enabled"], havingValue = "true")
    @ConditionalOnBean(GraphRunner::class)
    @ConditionalOnMissingBean
    fun hitlArbiter(
        graphRunner: GraphRunner,
        schemaValidationPipeline: SchemaValidationPipeline,
        executionStateMachine: ExecutionStateMachine,
        deadLetterHandlerProvider: ObjectProvider<DeadLetterHandler>,
        messageQueueProvider: ObjectProvider<MessageQueue>
    ): Arbiter {
        val queue = messageQueueProvider.getIfAvailable()
            ?: throw IllegalStateException(
                "HITL Arbiter requires a MessageQueue bean. " +
                    "Enable spice.hitl.queue.enabled or provide your own MessageQueue implementation."
            )

        return Arbiter(
            queue = queue,
            graphRunner = graphRunner,
            validationPipeline = schemaValidationPipeline,
            stateMachine = executionStateMachine,
            deadLetterHandler = deadLetterHandlerProvider.getIfAvailable()
        )
    }

    /**
     * Auto-start the Arbiter when enabled.
     *
     * Requires a GraphProvider bean to be configured:
     * ```kotlin
     * @Bean
     * fun graphProvider(): GraphProvider = GraphProvider { message ->
     *     // Return the appropriate graph based on message
     *     myGraphRegistry.get(message.data["graphId"] as String)
     * }
     * ```
     *
     * Uses SmartLifecycle to ensure proper startup/shutdown with Spring context.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "spice.hitl.arbiter",
        name = ["enabled", "auto-start"],
        havingValue = "true",
        matchIfMissing = false
    )
    @ConditionalOnBean(Arbiter::class, GraphProvider::class)
    fun arbiterLifecycle(
        arbiter: Arbiter,
        graphProvider: GraphProvider,
        properties: SpiceFrameworkProperties
    ): org.springframework.context.SmartLifecycle {
        return object : org.springframework.context.SmartLifecycle {
            private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            private var running = false

            override fun start() {
                if (!running) {
                    scope.launch {
                        val topic = properties.hitl.arbiter.topic
                        arbiter.start(topic, graphProvider::provide)
                    }
                    running = true
                }
            }

            override fun stop() {
                if (running) {
                    scope.cancel()
                    running = false
                }
            }

            override fun isRunning(): Boolean = running

            override fun getPhase(): Int = Int.MAX_VALUE  // Start last, stop first
        }
    }
}
