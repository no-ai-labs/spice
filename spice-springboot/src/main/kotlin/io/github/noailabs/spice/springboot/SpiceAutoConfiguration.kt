package io.github.noailabs.spice.springboot

import io.github.noailabs.spice.arbiter.Arbiter
import io.github.noailabs.spice.arbiter.InMemoryMessageQueue
import io.github.noailabs.spice.arbiter.MessageQueue
import io.github.noailabs.spice.arbiter.RedisMessageQueue
import io.github.noailabs.spice.cache.CachePolicy
import io.github.noailabs.spice.cache.InMemoryVectorCache
import io.github.noailabs.spice.cache.RedisVectorCache
import io.github.noailabs.spice.cache.VectorCache
import io.github.noailabs.spice.event.EventBusConfig
import io.github.noailabs.spice.event.InMemoryToolCallEventBus
import io.github.noailabs.spice.event.KafkaToolCallEventBus
import io.github.noailabs.spice.event.MetadataFilterConfig
import io.github.noailabs.spice.event.RedisToolCallEventBus
import io.github.noailabs.spice.event.ToolCallEventBus
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
import io.github.noailabs.spice.tool.ToolLifecycleListener
import io.github.noailabs.spice.tool.ToolLifecycleListeners
import io.github.noailabs.spice.validation.DeadLetterHandler
import io.github.noailabs.spice.validation.SchemaValidationPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Bean
import java.util.concurrent.atomic.AtomicBoolean
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

    /**
     * Auto-wire all ToolLifecycleListener beans into a single ToolLifecycleListeners registry.
     *
     * This allows users to define individual listener beans and have them automatically
     * aggregated for use in graph execution.
     *
     * **Usage:**
     * ```kotlin
     * @Bean
     * fun metricsListener(): ToolLifecycleListener = MetricsListener()
     *
     * @Bean
     * fun slackAlertListener(): ToolLifecycleListener = SlackAlertListener()
     * ```
     *
     * These will be automatically combined into a ToolLifecycleListeners registry.
     */
    @Bean
    @ConditionalOnMissingBean
    fun toolLifecycleListeners(
        listenersProvider: ObjectProvider<List<ToolLifecycleListener>>
    ): ToolLifecycleListeners {
        val listeners = listenersProvider.getIfAvailable() ?: emptyList()
        return if (listeners.isEmpty()) {
            ToolLifecycleListeners.EMPTY
        } else {
            ToolLifecycleListeners.of(listeners)
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun graphRunner(
        properties: SpiceFrameworkProperties,
        schemaValidationPipeline: SchemaValidationPipeline,
        executionStateMachine: ExecutionStateMachine,
        cachePolicy: CachePolicy,
        vectorCacheProvider: ObjectProvider<VectorCache>,
        toolLifecycleListeners: ToolLifecycleListeners
    ): GraphRunner {
        val vectorCache = vectorCacheProvider.getIfAvailable()
        val graphRunnerProps = properties.graphRunner

        // Use ToolLifecycleListeners as fallback when graph doesn't define its own
        val fallbackListeners = if (toolLifecycleListeners == ToolLifecycleListeners.EMPTY) {
            null
        } else {
            toolLifecycleListeners
        }

        return DefaultGraphRunner(
            enableIdempotency = graphRunnerProps.enableIdempotency,
            enableEvents = graphRunnerProps.enableEvents,
            validationPipeline = schemaValidationPipeline,
            stateMachine = executionStateMachine,
            cachePolicy = cachePolicy,
            vectorCache = vectorCache,
            fallbackToolLifecycleListeners = fallbackListeners
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

    @Bean(destroyMethod = "")  // Disable auto-detection - eventBusLifecycle handles shutdown
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

    /**
     * Lifecycle bean for EventBus shutdown
     * Handles suspend fun close() properly using runBlocking
     */
    @Bean
    @ConditionalOnBean(EventBus::class)
    fun eventBusLifecycle(eventBus: EventBus): SmartLifecycle {
        return object : SmartLifecycle {
            private val running = AtomicBoolean(true)

            override fun start() {
                // EventBus starts automatically on creation
            }

            override fun stop() {
                if (running.compareAndSet(true, false)) {
                    runBlocking {
                        eventBus.close()
                    }
                }
            }

            override fun stop(callback: Runnable) {
                stop()
                callback.run()
            }

            override fun isRunning(): Boolean = running.get()

            override fun isAutoStartup(): Boolean = false

            override fun getPhase(): Int = Int.MAX_VALUE - 100 // Shutdown before other components
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "spice.tool-call-event-bus", name = ["enabled"], havingValue = "true")
    @ConditionalOnMissingBean
    fun toolCallEventBus(
        properties: SpiceFrameworkProperties,
        redisPoolProvider: ObjectProvider<JedisPool>
    ): ToolCallEventBus {
        val toolCallEventBus = properties.toolCallEventBus

        // Build metadata filter config from properties
        val filterConfig = MetadataFilterConfig(
            includeMetadataKeys = toolCallEventBus.filters.include.takeIf { it.isNotEmpty() },
            excludeMetadataKeys = toolCallEventBus.filters.exclude.takeIf { it.isNotEmpty() }
        )

        val eventBusConfig = EventBusConfig(
            enableHistory = toolCallEventBus.history.enabled,
            historySize = toolCallEventBus.history.size,
            enableMetrics = toolCallEventBus.history.enableMetrics,
            metadataFilter = filterConfig
        )

        return when (toolCallEventBus.backend) {
            SpiceFrameworkProperties.ToolCallEventBusProperties.ToolCallEventBackend.IN_MEMORY -> {
                InMemoryToolCallEventBus(config = eventBusConfig)
            }
            SpiceFrameworkProperties.ToolCallEventBusProperties.ToolCallEventBackend.REDIS_STREAMS -> {
                val redisPool = redisPoolProvider.getIfAvailable()
                    ?: throw IllegalStateException(
                        "ToolCallEventBus backend set to REDIS_STREAMS but spice.redis.enabled=false"
                    )
                val redisConfig = toolCallEventBus.redisStreams
                RedisToolCallEventBus(
                    jedisPool = redisPool,
                    streamKey = redisConfig.streamKey,
                    consumerGroup = redisConfig.consumerGroup,
                    consumerName = redisConfig.consumerName,
                    startFrom = redisConfig.startFrom,
                    config = eventBusConfig,
                    pollInterval = redisConfig.pollInterval.toKotlinDuration()
                )
            }
            SpiceFrameworkProperties.ToolCallEventBusProperties.ToolCallEventBackend.KAFKA -> {
                val kafkaConfig = toolCallEventBus.kafka
                KafkaToolCallEventBus(
                    bootstrapServers = kafkaConfig.bootstrapServers,
                    topic = kafkaConfig.topic,
                    clientId = kafkaConfig.clientId,
                    consumerGroup = kafkaConfig.consumerGroup,
                    autoOffsetReset = kafkaConfig.autoOffsetReset,
                    config = eventBusConfig,
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
    ): SmartLifecycle {
        val topic = properties.hitl.arbiter.topic
        return object : SmartLifecycle {
            private val running = AtomicBoolean(false)
            private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            private var job: Job? = null

            override fun start() {
                if (running.compareAndSet(false, true)) {
                    if (!scope.isActive) {
                        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                    }
                    job = scope.launch {
                        arbiter.start(topic, graphProvider::provide)
                    }
                }
            }

            override fun stop(callback: Runnable) {
                stop()
                callback.run()
            }

            override fun stop() {
                if (running.compareAndSet(true, false)) {
                    runBlocking {
                        job?.cancelAndJoin()
                        job = null
                    }
                    scope.cancel()
                }
            }

            override fun isRunning(): Boolean = running.get()

            override fun isAutoStartup(): Boolean = true

            override fun getPhase(): Int = Int.MAX_VALUE
        }
    }
}
