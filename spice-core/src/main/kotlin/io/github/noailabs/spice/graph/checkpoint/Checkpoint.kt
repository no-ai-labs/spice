package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.graph.nodes.GraphExecutionState
import io.github.noailabs.spice.graph.nodes.HumanInteraction
import io.github.noailabs.spice.graph.nodes.HumanResponse
import java.time.Instant

/**
 * Represents a saved state of graph execution.
 * Allows resuming long-running workflows from a specific point.
 */
data class Checkpoint(
    /**
     * Unique identifier for this checkpoint.
     */
    val id: String,

    /**
     * ID of the graph execution run.
     */
    val runId: String,

    /**
     * ID of the graph being executed.
     */
    val graphId: String,

    /**
     * Current node being executed when checkpoint was created.
     */
    val currentNodeId: String,

    /**
     * Execution state (node results and intermediate data).
     */
    val state: Map<String, Any?>,

    /**
     * AgentContext for multi-tenant support.
     */
    val agentContext: AgentContext? = null,

    /**
     * When this checkpoint was created.
     */
    val timestamp: Instant = Instant.now(),

    /**
     * Optional metadata about the checkpoint.
     */
    val metadata: Map<String, Any> = emptyMap(),

    /**
     * Execution state for HITL support.
     */
    val executionState: GraphExecutionState = GraphExecutionState.RUNNING,

    /**
     * Pending human interaction (if waiting for human input).
     */
    val pendingInteraction: HumanInteraction? = null,

    /**
     * Human response (if resuming from human input).
     */
    val humanResponse: HumanResponse? = null
)

/**
 * Configuration for checkpoint behavior.
 */
data class CheckpointConfig(
    /**
     * Save checkpoint after every N nodes.
     * If null, manual checkpointing only.
     */
    val saveEveryNNodes: Int? = null,

    /**
     * Save checkpoint after every N seconds.
     * If null, time-based checkpointing disabled.
     */
    val saveEveryNSeconds: Long? = null,

    /**
     * Maximum number of checkpoints to keep per run.
     * Older checkpoints will be deleted.
     */
    val maxCheckpointsPerRun: Int = 10,

    /**
     * Whether to save checkpoint on error.
     */
    val saveOnError: Boolean = true
)
