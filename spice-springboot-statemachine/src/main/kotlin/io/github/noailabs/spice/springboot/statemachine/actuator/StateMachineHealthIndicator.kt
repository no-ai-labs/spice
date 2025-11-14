package io.github.noailabs.spice.springboot.statemachine.actuator

import io.github.noailabs.spice.springboot.statemachine.config.StateMachineProperties
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator

/**
 * Simple health indicator showing whether the extension is enabled.
 */
class StateMachineHealthIndicator(
    private val properties: StateMachineProperties
) : HealthIndicator {
    override fun health(): Health {
        return if (properties.enabled) {
            Health.up()
                .withDetail("persistence", properties.persistence.type)
                .withDetail("retryEnabled", properties.retry.enabled)
                .build()
        } else {
            Health.unknown().build()
        }
    }
}
