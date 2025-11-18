package io.github.noailabs.spice.graph.checkpoint

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * ⚙️ Checkpoint Configuration
 *
 * Controls when and how checkpoints are saved during graph execution.
 *
 * **Strategies:**
 * - **Save on HITL**: Always save when entering WAITING state
 * - **Save every N nodes**: Periodic checkpoints for crash recovery
 * - **Save on error**: Checkpoint before failure for debugging
 * - **No checkpoints**: Disable for stateless workflows
 *
 * **Usage:**
 * ```kotlin
 * // HITL only (default)
 * val config = CheckpointConfig()
 *
 * // Aggressive checkpointing (every node + errors)
 * val config = CheckpointConfig(
 *     saveEveryNNodes = 1,
 *     saveOnError = true,
 *     ttl = 24.hours
 * )
 *
 * // Minimal checkpointing (HITL only, short TTL)
 * val config = CheckpointConfig(
 *     saveOnHitl = true,
 *     saveEveryNNodes = null,
 *     ttl = 1.hours
 * )
 * ```
 *
 * @property saveOnHitl Save checkpoint when entering WAITING state (HITL)
 * @property saveEveryNNodes Save checkpoint every N nodes (null = disabled)
 * @property saveOnError Save checkpoint before failure
 * @property ttl Checkpoint time-to-live
 * @property autoCleanup Delete checkpoints after successful completion
 *
 * @since 1.0.0
 */
data class CheckpointConfig(
    val saveOnHitl: Boolean = true,
    val saveEveryNNodes: Int? = null,
    val saveOnError: Boolean = false,
    val ttl: Duration = 24.hours,
    val autoCleanup: Boolean = true
) {
    companion object {
        /**
         * Default configuration (HITL only)
         */
        val DEFAULT = CheckpointConfig()

        /**
         * Aggressive configuration (every node + errors)
         * Use for critical workflows
         */
        val AGGRESSIVE = CheckpointConfig(
            saveEveryNNodes = 1,
            saveOnError = true,
            ttl = 72.hours
        )

        /**
         * Minimal configuration (HITL only, short TTL)
         * Use for non-critical workflows
         */
        val MINIMAL = CheckpointConfig(
            ttl = 1.hours,
            autoCleanup = true
        )

        /**
         * Disabled configuration (no checkpoints)
         * Use for stateless, fast workflows
         */
        val DISABLED = CheckpointConfig(
            saveOnHitl = false,
            saveEveryNNodes = null,
            saveOnError = false,
            autoCleanup = false
        )
    }
}
