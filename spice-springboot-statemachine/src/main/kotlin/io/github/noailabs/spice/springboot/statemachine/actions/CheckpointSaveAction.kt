package io.github.noailabs.spice.springboot.statemachine.actions

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.checkpoint.Checkpoint
import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
import io.github.noailabs.spice.springboot.statemachine.events.HitlRequiredEvent
import io.github.noailabs.spice.springboot.statemachine.persistence.StateMachineCheckpointBridge
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.statemachine.StateMachine
import java.util.UUID

/**
 * Saves checkpoints when entering WAITING state and emits corresponding events.
 */
class CheckpointSaveAction(
    private val checkpointBridge: StateMachineCheckpointBridge,
    private val publisher: ApplicationEventPublisher,
    private val checkpointStore: CheckpointStore? = null
) {
    private val logger = LoggerFactory.getLogger(CheckpointSaveAction::class.java)

    suspend fun save(runId: String, graph: Graph, stateMachine: StateMachine<ExecutionState, io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent>) {
        val checkpointId = runId.ifBlank { UUID.randomUUID().toString() }
        checkpointBridge.persist(stateMachine, checkpointId)
        val message = stateMachine.extendedState.variables["message"] as? SpiceMessage
        if (message != null && message.state == ExecutionState.WAITING) {
            val checkpoint = Checkpoint.fromMessage(
                message = message,
                graphId = graph.id,
                runId = runId
            )
            (graph.checkpointStore ?: checkpointStore)?.save(checkpoint)
        }
        publisher.publishEvent(
            HitlRequiredEvent(
                checkpointId = checkpointId,
                graphId = graph.id,
                nodeId = stateMachine.extendedState.variables["nodeId"]?.toString(),
                options = mapOf("timestamp" to Clock.System.now().toString())
            )
        )
        logger.info("Saved checkpoint for graph {} with id {}", graph.id, checkpointId)
    }
}
