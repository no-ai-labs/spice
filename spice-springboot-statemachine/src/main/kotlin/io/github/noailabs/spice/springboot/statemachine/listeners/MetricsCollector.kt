package io.github.noailabs.spice.springboot.statemachine.listeners

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.springboot.statemachine.actuator.StateMachineMetrics
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.state.State

/**
 * Updates Micrometer metrics whenever the machine transitions.
 */
class MetricsCollector(
    private val metrics: StateMachineMetrics
) : StateMachineListenerAdapter<ExecutionState, SpiceEvent>() {

    override fun stateChanged(from: State<ExecutionState, SpiceEvent>?, to: State<ExecutionState, SpiceEvent>?) {
        if (to != null) {
            metrics.onStateChange(from?.id, to.id)
        }
    }
}
