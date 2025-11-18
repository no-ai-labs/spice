package io.github.noailabs.spice.springboot.statemachine.listeners

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import io.github.noailabs.spice.springboot.statemachine.events.NodeExecutionEvent
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.Message
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.state.State
import java.util.concurrent.atomic.AtomicReference

/**
 * Publishes NodeExecutionEvent for every transition and logs rejected events.
 */
class NodeExecutionLogger(
    private val publisher: ApplicationEventPublisher
) : StateMachineListenerAdapter<ExecutionState, SpiceEvent>() {

    private val logger = LoggerFactory.getLogger(NodeExecutionLogger::class.java)
    private val currentStateMachine = AtomicReference<StateMachine<ExecutionState, SpiceEvent>?>(null)

    override fun eventNotAccepted(event: Message<SpiceEvent>) {
        logger.warn("Spice state machine rejected event: {}", event.payload)
    }

    override fun stateMachineStarted(stateMachine: StateMachine<ExecutionState, SpiceEvent>) {
        currentStateMachine.set(stateMachine)
    }

    override fun stateMachineStopped(stateMachine: StateMachine<ExecutionState, SpiceEvent>) {
        currentStateMachine.set(null)
    }

    override fun stateChanged(from: State<ExecutionState, SpiceEvent>?, to: State<ExecutionState, SpiceEvent>?) {
        val toState = to?.id ?: return
        val machine = currentStateMachine.get() ?: return
        val rawMetadata = machine.extendedState.variables.toMap()
        val metadata = rawMetadata.mapNotNull { (k, v) ->
            k?.toString()?.let { key -> key to v }
        }.toMap()
        publisher.publishEvent(
            NodeExecutionEvent(
                graphId = metadata["graphId"]?.toString() ?: "unknown",
                nodeId = metadata["nodeId"]?.toString(),
                from = from?.id,
                to = toState,
                event = SpiceEvent.valueOf(metadata["lastEvent"]?.toString() ?: SpiceEvent.START.name),
                timestamp = Clock.System.now(),
                metadata = metadata
            )
        )
    }
}
