package io.github.noailabs.spice.springboot.statemachine.persistence

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.StateMachineContext
import org.springframework.statemachine.persist.ReactiveStateMachinePersister

/**
 * Persists and restores StateMachineContext instances.
 */
class StateMachineCheckpointBridge(
    private val persister: ReactiveStateMachinePersister<ExecutionState, SpiceEvent, String>?
) {
    suspend fun persist(stateMachine: StateMachine<ExecutionState, SpiceEvent>, key: String) {
        persister?.persist(stateMachine, key)?.awaitFirstOrNull()
    }

    suspend fun restore(key: String): StateMachineContext<ExecutionState, SpiceEvent>? {
        return persister?.restore(null, key)?.awaitFirstOrNull()
    }
}
