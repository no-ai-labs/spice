package io.github.noailabs.spice.lifecycle

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.AgentRuntime
import io.github.noailabs.spice.BaseAgent
import kotlinx.coroutines.flow.StateFlow

/**
 * Extension functions for Agent lifecycle management
 */

/**
 * Create a lifecycle-managed agent
 */
fun Agent.withLifecycle(
    runtime: AgentRuntime,
    config: LifecycleConfig = LifecycleConfig()
): AgentLifecycleManager {
    return AgentLifecycleManager(this, runtime, config)
}

/**
 * Initialize and start agent with lifecycle
 */
suspend fun Agent.startWithLifecycle(
    runtime: AgentRuntime,
    config: LifecycleConfig = LifecycleConfig()
): AgentLifecycleManager {
    val manager = withLifecycle(runtime, config)
    manager.initialize().getOrThrow()
    manager.start().getOrThrow()
    return manager
}

/**
 * Extension property to get lifecycle state if available
 */
val Agent.lifecycleState: StateFlow<AgentState>?
    get() = (this as? LifecycleAwareAgent)?.lifecycleManager?.state

/**
 * Extension property to get health status if available
 */
val Agent.healthStatus: StateFlow<HealthStatus>?
    get() = (this as? LifecycleAwareAgent)?.lifecycleManager?.health

/**
 * Check if agent is in a healthy running state
 */
fun Agent.isHealthyAndRunning(): Boolean {
    return when (this) {
        is LifecycleAwareAgent -> {
            lifecycleManager?.let { manager ->
                manager.state.value == AgentState.RUNNING &&
                manager.health.value.status == HealthLevel.HEALTHY
            } ?: false
        }
        else -> isReady()
    }
}

/**
 * Gracefully stop agent if it has lifecycle support
 */
suspend fun Agent.stopGracefully(gracePeriodMs: Long = 30_000): Result<Unit> {
    return when (this) {
        is LifecycleAwareAgent -> {
            lifecycleManager?.stop(gracePeriodMs) 
                ?: Result.failure(IllegalStateException("No lifecycle manager"))
        }
        else -> {
            cleanup()
            Result.success(Unit)
        }
    }
}