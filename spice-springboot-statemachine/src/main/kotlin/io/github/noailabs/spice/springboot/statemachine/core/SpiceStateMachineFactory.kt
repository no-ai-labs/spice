package io.github.noailabs.spice.springboot.statemachine.core

import io.github.noailabs.spice.ExecutionState
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.listener.StateMachineListener

/**
 * Factory responsible for decorating StateMachine instances with the listeners/actions used by Spice.
 */
class SpiceStateMachineFactory(
    private val factory: StateMachineFactory<ExecutionState, SpiceEvent>,
    private val listeners: List<StateMachineListener<ExecutionState, SpiceEvent>> = emptyList()
) {
    fun create(): SpiceStateMachine {
        val stateMachine = factory.stateMachine
        registerListeners(stateMachine)
        return SpiceStateMachine(stateMachine)
    }

    private fun registerListeners(stateMachine: org.springframework.statemachine.StateMachine<ExecutionState, SpiceEvent>) {
        listeners.forEach { stateMachine.addStateListener(it) }
    }
}
