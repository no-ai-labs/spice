package io.github.noailabs.spice.springboot.statemachine.persistence

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.StateMachineContext
import org.springframework.statemachine.StateMachinePersist
import org.springframework.statemachine.support.DefaultStateMachineContext

/**
 * Persists and restores StateMachineContext instances using StateMachinePersist (4.0 API).
 */
class StateMachineCheckpointBridge(
    private val persister: StateMachinePersist<ExecutionState, SpiceEvent, String>?
) {
    suspend fun persist(stateMachine: StateMachine<ExecutionState, SpiceEvent>, key: String) {
        persister?.let {
            withContext(Dispatchers.IO) {
                // Manually construct context from state machine
                val context = DefaultStateMachineContext<ExecutionState, SpiceEvent>(
                    stateMachine.state.id,  // state
                    null,  // event
                    emptyMap(),  // event headers
                    stateMachine.extendedState,  // extended state
                    emptyMap(),  // state history
                    stateMachine.id  // id
                )
                it.write(context, key)
            }
        }
    }

    suspend fun restore(key: String): StateMachineContext<ExecutionState, SpiceEvent>? {
        return persister?.let {
            withContext(Dispatchers.IO) {
                it.read(key)
            }
        }
    }
}
