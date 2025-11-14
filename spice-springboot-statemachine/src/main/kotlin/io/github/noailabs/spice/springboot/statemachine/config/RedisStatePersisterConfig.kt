package io.github.noailabs.spice.springboot.statemachine.config

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import io.github.noailabs.spice.springboot.statemachine.persistence.ReactiveRedisStatePersister
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.statemachine.StateMachinePersist
import java.time.Duration

@Configuration
@ConditionalOnProperty(name = ["spice.statemachine.persistence.type"], havingValue = "REDIS")
class RedisStatePersisterConfig(
    private val properties: StateMachineProperties
) {
    @Bean
    fun spiceStateMachineRedisConnectionFactory(): LettuceConnectionFactory {
        val redis = properties.persistence.redis
        val standalone = RedisStandaloneConfiguration(redis.host, redis.port).apply {
            redis.password?.takeIf { it.isNotBlank() }?.let { password = RedisPassword.of(it) }
            database = redis.database
        }
        val clientConfigBuilder = LettuceClientConfiguration.builder()
        if (redis.ssl) {
            clientConfigBuilder.useSsl()
        }
        clientConfigBuilder.commandTimeout(Duration.ofSeconds(5))
        return LettuceConnectionFactory(standalone, clientConfigBuilder.build()).apply { afterPropertiesSet() }
    }

    @Bean
    fun spiceStateMachineRedisTemplate(
        connectionFactory: LettuceConnectionFactory
    ): ReactiveStringRedisTemplate = ReactiveStringRedisTemplate(connectionFactory)

    @Bean
    fun spiceStateMachinePersister(
        redisTemplate: ReactiveStringRedisTemplate
    ): StateMachinePersist<ExecutionState, SpiceEvent, String> {
        return ReactiveRedisStatePersister(redisTemplate)
    }
}
