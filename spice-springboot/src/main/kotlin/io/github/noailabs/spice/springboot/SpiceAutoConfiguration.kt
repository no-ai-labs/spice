package io.github.noailabs.spice.springboot

import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.GraphRunner
import io.github.noailabs.spice.cache.CachePolicy
import io.github.noailabs.spice.cache.InMemoryVectorCache
import io.github.noailabs.spice.cache.VectorCache
import io.github.noailabs.spice.cache.RedisVectorCache
import io.github.noailabs.spice.events.EventBus
import io.github.noailabs.spice.events.InMemoryEventBus
import io.github.noailabs.spice.idempotency.IdempotencyStore
import io.github.noailabs.spice.idempotency.InMemoryIdempotencyStore
import io.github.noailabs.spice.idempotency.RedisIdempotencyStore
import io.github.noailabs.spice.state.ExecutionStateMachine
import io.github.noailabs.spice.validation.SchemaValidationPipeline
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
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

    @Bean
    @ConditionalOnProperty(prefix = "spice.events", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    fun eventBus(): EventBus = InMemoryEventBus()
}
