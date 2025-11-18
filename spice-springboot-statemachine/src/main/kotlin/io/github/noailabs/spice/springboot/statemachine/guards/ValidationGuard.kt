package io.github.noailabs.spice.springboot.statemachine.guards

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.guard.Guard

/**
 * Ensures a SpiceMessage is attached to the extended state before transitions fire.
 */
class ValidationGuard : Guard<ExecutionState, SpiceEvent> {
    override fun evaluate(context: StateContext<ExecutionState, SpiceEvent>): Boolean {
        val message = context.extendedState.variables["message"]
        return message is SpiceMessage && !message.state.isTerminal()
    }
}
