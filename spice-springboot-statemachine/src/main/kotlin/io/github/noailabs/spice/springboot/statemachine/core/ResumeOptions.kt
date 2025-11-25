package io.github.noailabs.spice.springboot.statemachine.core

import io.github.noailabs.spice.graph.checkpoint.CheckpointConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Options for controlling HITL resume behavior in GraphToStateMachineAdapter.
 *
 * Provides fine-grained control over:
 * - State machine event publication after resume
 * - Error handling and exception propagation
 * - Checkpoint validation and cleanup
 *
 * **Usage:**
 * ```kotlin
 * // Default behavior (matches existing flow)
 * adapter.resume(runId)
 *
 * // Silent resume (no events)
 * adapter.resume(runId, ResumeOptions(publishEvents = false))
 *
 * // Strict mode (throw on error)
 * adapter.resume(runId, ResumeOptions(throwOnError = true))
 *
 * // Custom TTL validation
 * adapter.resume(runId, ResumeOptions(
 *     validateExpiration = true,
 *     maxCheckpointAge = 1.hours
 * ))
 * ```
 *
 * @property publishEvents Whether to publish state machine events after resume (default: true)
 * @property throwOnError If true, throws exceptions on resume failures instead of returning SpiceResult.Failure (default: false)
 * @property validateExpiration Validate checkpoint TTL before resuming (default: true)
 * @property maxCheckpointAge Maximum allowed age for checkpoint (default: 24 hours)
 * @property autoCleanup Delete checkpoint after successful completion (default: true)
 * @property checkpointConfig Full checkpoint configuration for advanced scenarios (overrides individual settings)
 * @property userResponseMetadata Additional metadata to merge into user response message
 *
 * @since 1.0.4
 */
data class ResumeOptions(
    val publishEvents: Boolean = true,
    val throwOnError: Boolean = false,
    val validateExpiration: Boolean = true,
    val maxCheckpointAge: Duration = 24.hours,
    val autoCleanup: Boolean = true,
    val checkpointConfig: CheckpointConfig? = null,
    val userResponseMetadata: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * Default options (matches existing behavior)
         */
        val DEFAULT = ResumeOptions()

        /**
         * Silent mode - no events published
         */
        val SILENT = ResumeOptions(publishEvents = false)

        /**
         * Strict mode - throws exceptions on errors
         */
        val STRICT = ResumeOptions(throwOnError = true)

        /**
         * Lenient mode - no TTL validation, no auto cleanup
         */
        val LENIENT = ResumeOptions(
            validateExpiration = false,
            autoCleanup = false
        )

        /**
         * Short-lived checkpoints (1 hour TTL)
         */
        val SHORT_LIVED = ResumeOptions(
            maxCheckpointAge = 1.hours
        )

        /**
         * Long-lived checkpoints (72 hours TTL)
         */
        val LONG_LIVED = ResumeOptions(
            maxCheckpointAge = 72.hours
        )
    }

    /**
     * Get effective checkpoint config, merging options if no explicit config provided
     */
    fun effectiveCheckpointConfig(): CheckpointConfig {
        return checkpointConfig ?: CheckpointConfig(
            ttl = maxCheckpointAge,
            autoCleanup = autoCleanup
        )
    }
}
